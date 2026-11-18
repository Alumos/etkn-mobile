package xyz.etkn.mobile.player.bluray

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.CodecSpecificDataUtil
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.container.NalUnitUtil
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ForwardingExtractor
import androidx.media3.extractor.ForwardingExtractorOutput
import androidx.media3.extractor.ForwardingTrackOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.util.Locale

/**
 * MediaItem tag from the server's media source: [disc] when it is a Blu-ray image (`Container`
 * iso or an `.iso` path — the stream URL itself may lack the suffix, e.g. a cloud STRM target),
 * and the probed main [playlist] or -1 for libbluray's own pick.
 */
data class BlurayTag(val disc: Boolean, val playlist: Int)

/**
 * Full-disc ISO playback, ported from ETKN TV. A `.iso` URI goes through libbluray: the ISO is
 * read by HTTP Range, the main title's MPEG-TS is demuxed natively and fed to the ordinary
 * ProgressiveMediaSource, so decoding, seeking, track selection and the player lifecycle stay
 * Media3's (hardware MediaCodec first). Everything else uses [ordinary].
 */
class BlurayMediaSourceFactory(
    private val ordinary: MediaSource.Factory,
    private val http: OkHttpClient,
    private val userAgent: String,
    private val upstream: DataSource.Factory,
) : MediaSource.Factory {

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val config = requireNotNull(mediaItem.localConfiguration)
        val uri = config.uri
        val tag = config.tag as? BlurayTag
        if (tag?.disc != true && !isIso(uri)) return ordinary.createMediaSource(mediaItem)
        val playlist = tag?.playlist ?: -1
        val session = Session(BlurayInput(http, uri, userAgent), playlist)
        val data = DataSource.Factory { DiscDataSource(session, uri, upstream.createDataSource()) }
        val fallback = baseLayerExtractors()
        val extractors = object : ExtractorsFactory {
            override fun createExtractors(): Array<Extractor> = arrayOf(DiscExtractor(session))
            override fun createExtractors(address: Uri, headers: Map<String, List<String>>): Array<Extractor> =
                if (address == uri) createExtractors() else fallback.createExtractors(address, headers)
        }
        return DefaultMediaSourceFactory(data, extractors).createMediaSource(mediaItem)
    }

    override fun getSupportedTypes(): IntArray = ordinary.supportedTypes

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
        ordinary.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
        ordinary.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    companion object {
        fun isIso(uri: Uri): Boolean = uri.path?.lowercase(Locale.ROOT)?.endsWith(".iso") == true

        /**
         * Dolby Vision profile 7 (dual-layer, Blu-ray) has no phone decoder; its base layer is
         * plain HEVC HDR10, so present it as such instead of failing the DV decoder lookup.
         */
        fun baseLayer(format: Format): Format {
            val codecs = format.codecs?.lowercase(Locale.ROOT) ?: return format
            if (format.sampleMimeType != MimeTypes.VIDEO_DOLBY_VISION || !DV_PROFILE_7.matches(codecs)) return format
            return format.buildUpon()
                .setSampleMimeType(MimeTypes.VIDEO_H265)
                .setCodecs(NalUnitUtil.getH265BaseLayerCodecsString(format.initializationData))
                .build()
        }

        /** Default extractors whose track formats go through [baseLayer]. */
        fun baseLayerExtractors(delegate: ExtractorsFactory = DefaultExtractorsFactory()): ExtractorsFactory = object : ExtractorsFactory {
            override fun createExtractors(): Array<Extractor> = wrap(delegate.createExtractors())
            override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
                wrap(delegate.createExtractors(uri, responseHeaders))

            private fun wrap(extractors: Array<Extractor>): Array<Extractor> = Array(extractors.size) { i ->
                object : ForwardingExtractor(extractors[i]) {
                    override fun init(output: ExtractorOutput) {
                        super.init(object : ForwardingExtractorOutput(output) {
                            private val tracks = HashMap<Int, TrackOutput>()
                            override fun track(id: Int, type: Int): TrackOutput = tracks.getOrPut(id) {
                                object : ForwardingTrackOutput(super.track(id, type)) {
                                    override fun format(format: Format) = super.format(baseLayer(format))
                                }
                            }
                        })
                    }
                }
            }
        }

        private val DV_PROFILE_7 = Regex("dv(?:he|h1)\\.0?7(?:\\..*)?")
    }

    private class Session(val input: BlurayInput, val playlist: Int) {
        @Volatile var handle = 0L

        @Synchronized
        fun open(): Long {
            if (handle == 0L) {
                if (!BlurayNative.loaded) throw IOException("原盘组件未能加载")
                handle = BlurayNative.open(input, playlist)
            }
            return handle
        }

        @Synchronized
        fun close() {
            if (handle != 0L) {
                BlurayNative.close(handle)
                handle = 0
            }
            runCatching { input.close() }
        }
    }

    /** The ISO URI reads the selected title's transport stream; any other URI is passed through. */
    private class DiscDataSource(val session: Session, val iso: Uri, val fallback: DataSource) : BaseDataSource(true) {
        private var disc = false
        private var started = false
        private var remaining = 0L

        override fun open(dataSpec: DataSpec): Long {
            disc = dataSpec.uri == iso
            if (!disc) return fallback.open(dataSpec)
            transferInitializing(dataSpec)
            try {
                val handle = session.open()
                val size = BlurayNative.size(handle)
                if (dataSpec.position < 0 || dataSpec.position > size) throw IOException("原盘跳转超出正片范围")
                if (BlurayNative.seek(handle, dataSpec.position) != dataSpec.position) throw IOException("原盘无法跳转到指定位置")
                remaining = size - dataSpec.position
                if (dataSpec.length != C.LENGTH_UNSET.toLong()) remaining = minOf(remaining, dataSpec.length)
                transferStarted(dataSpec)
                started = true
                return remaining
            } catch (error: Exception) {
                session.close()
                throw error
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (!disc) return fallback.read(buffer, offset, length)
            if (length == 0) return 0
            if (remaining == 0L) return C.RESULT_END_OF_INPUT
            val count = BlurayNative.read(session.handle, buffer, offset, minOf(remaining, length.toLong()).toInt())
            if (count <= 0) throw EOFException("原盘正片数据不完整")
            remaining -= count
            bytesTransferred(count)
            return count
        }

        override fun getUri(): Uri? = if (disc) iso else fallback.uri

        override fun close() {
            if (!disc) {
                fallback.close()
                return
            }
            if (started) {
                started = false
                transferEnded()
            }
        }
    }

    /** Native packets → Media3 samples; track identity is PID + codec (TrueHD and its AC3 core share a PID). */
    private class DiscExtractor(val session: Session) : Extractor {
        private lateinit var output: ExtractorOutput
        private val known = LinkedHashMap<String, TrackOutput>()
        private val tracks = HashMap<Int, TrackOutput>()
        private val pgs = HashMap<Int, PgsAssembler>()
        private var buffer = ByteArray(2 * 1024 * 1024)
        private val metadata = LongArray(3)
        private var initialized = false

        override fun sniff(input: ExtractorInput): Boolean = true

        override fun init(output: ExtractorOutput) {
            this.output = output
        }

        override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
            if (!initialized) {
                val descriptions = BlurayNative.tracks(session.open(), input, input.position)
                val first = known.isEmpty()
                tracks.clear()
                pgs.clear()
                for (track in descriptions) {
                    val identity = "etkn-ts-${track.pid}-${track.mime}"
                    var target = known[identity]
                    if (first) {
                        val format = Format.Builder().setId(identity).setSampleMimeType(track.mime)
                            .setContainerMimeType(MimeTypes.VIDEO_MP2T).setLanguage(track.language.ifEmpty { null })
                        if (track.type == C.TRACK_TYPE_VIDEO) format.setWidth(track.width).setHeight(track.height).setMaxInputSize(buffer.size)
                        if (track.type == C.TRACK_TYPE_AUDIO) format.setChannelCount(track.channels).setSampleRate(track.sampleRate).setPcmEncoding(track.pcmEncoding)
                        if (track.initialization.isNotEmpty()) {
                            val nals = if (track.mime == MimeTypes.VIDEO_H264) CodecSpecificDataUtil.splitNalUnits(track.initialization) else null
                            format.setInitializationData(nals?.toList() ?: listOf(track.initialization))
                        }
                        target = output.track(known.size, track.type)
                        target.format(baseLayer(format.build()))
                        known[identity] = target
                    }
                    if (target != null) {
                        tracks[track.index] = target
                        if (track.mime == MimeTypes.APPLICATION_PGS) pgs[track.index] = PgsAssembler(target)
                    }
                }
                if (first) {
                    output.endTracks()
                    output.seekMap(object : SeekMap {
                        override fun isSeekable() = true
                        override fun getDurationUs() = BlurayNative.duration(session.handle)
                        override fun getSeekPoints(timeUs: Long) = SeekMap.SeekPoints(SeekPoint(timeUs, BlurayNative.seekPoint(session.handle, timeUs)))
                    })
                }
                initialized = true
            }
            val count = BlurayNative.packet(session.handle, input, buffer, metadata)
            if (count < -1) {
                if (-count > 16 * 1024 * 1024) throw IOException("原盘视频帧过大")
                buffer = ByteArray(-count)
                return Extractor.RESULT_CONTINUE
            }
            if (count == -1) return Extractor.RESULT_END_OF_INPUT
            if (count > 0) {
                val index = metadata[0].toInt()
                val subtitles = pgs[index]
                val track = tracks[index]
                if (subtitles != null) {
                    subtitles.sample(buffer, count, metadata[1], metadata[2].toInt())
                } else if (track != null) {
                    track.sampleData(ParsableByteArray(buffer, count), count)
                    track.sampleMetadata(metadata[1], metadata[2].toInt(), count, 0, null)
                }
            }
            return Extractor.RESULT_CONTINUE
        }

        override fun seek(position: Long, timeUs: Long) {
            pgs.values.forEach(PgsAssembler::reset)
            if (initialized) {
                BlurayNative.reset(session.handle)
                initialized = false
            }
        }

        override fun release() {
            pgs.clear()
            session.close()
        }
    }

    /** Disc PGS: publish a display set only at its END segment, stamped with the presentation time. */
    private class PgsAssembler(private val output: TrackOutput) {
        private var pending: ByteArrayOutputStream? = null
        private var timeUs = 0L
        private var flags = 0

        fun reset() {
            pending = null
        }

        fun sample(packet: ByteArray, length: Int, time: Long, sampleFlags: Int) {
            var position = 0
            while (length - position >= 3) {
                val type = packet[position].toInt() and 255
                val size = 3 + ((packet[position + 1].toInt() and 255) shl 8) + (packet[position + 2].toInt() and 255)
                if (size > length - position) {
                    reset()
                    return
                }
                if (type == 0x16) {
                    reset()
                    if (size < 14) return
                    pending = ByteArrayOutputStream()
                    timeUs = time
                    flags = sampleFlags
                }
                pending?.let { set ->
                    if (size > MAX_BYTES - set.size()) {
                        reset()
                        return
                    }
                    set.write(packet, position, size)
                    if (type == 0x80) {
                        val displaySet = set.toByteArray()
                        output.sampleData(ParsableByteArray(displaySet), displaySet.size)
                        output.sampleMetadata(timeUs, flags, displaySet.size, 0, null)
                        reset()
                    }
                }
                position += size
            }
            if (position != length) reset()
        }

        private companion object {
            const val MAX_BYTES = 16 * 1024 * 1024
        }
    }
}
