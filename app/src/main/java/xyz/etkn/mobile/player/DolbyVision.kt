package xyz.etkn.mobile.player

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import android.view.Display
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import xyz.etkn.mobile.player.bluray.BlurayNative
import java.util.Locale

/**
 * Dolby Vision profile 7 (dual layer: UHD Blu-ray and its remuxes) → profile 8.1 while playing.
 *
 * No phone decodes profile 7, but many decode profile 8: the HDR10 base layer plus the same
 * dynamic metadata (RPU). The enhancement layer is dropped and every RPU is rewritten as 8.1
 * (dovi_tool's conversion, in libetkn_bluray.so), so a Dolby Vision display gets real Dolby
 * Vision. Without a profile 8 decoder or a Dolby Vision display the HDR10 base layer plays as
 * before. All NAL handling here is on Annex-B access units (start codes), which is what both the
 * disc demuxer and Media3's Matroska / MP4 extractors emit.
 */
internal object DolbyVision {
    private const val TAG = "ETKN-Player"
    private const val NAL_RPU = 62
    private const val NAL_ENHANCEMENT = 63

    /** Decided once per process from the device's decoders and display. */
    @Volatile var convertProfile7 = false
        private set
    @Volatile private var checked = false

    fun init(context: Context) {
        if (checked) return
        checked = true
        val decoder = profile8Decoder()
        val display = displaySupportsDolbyVision(context)
        convertProfile7 = decoder != null && display && BlurayNative.loaded
        Log.i(TAG, "dolby vision p7->p8.1 ${if (convertProfile7) "on" else "off"} decoder=${decoder ?: "none"} display=$display")
    }

    private fun profile8Decoder(): String? = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull { info ->
            !info.isEncoder && info.supportedTypes.any { type ->
                type.equals(MimeTypes.VIDEO_DOLBY_VISION, ignoreCase = true) && runCatching {
                    info.getCapabilitiesForType(type).profileLevels.any { it.profile == CodecProfileLevel.DolbyVisionProfileDvheSt }
                }.getOrDefault(false)
            }
        }?.name
    }.getOrNull()

    private fun displaySupportsDolbyVision(context: Context): Boolean = runCatching {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val types = if (Build.VERSION.SDK_INT >= 34) display.mode.supportedHdrTypes
        else @Suppress("DEPRECATION") display.hdrCapabilities?.supportedHdrTypes ?: intArrayOf()
        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in types
    }.getOrDefault(false)

    private val PROFILE_7 = Regex("(dv(?:he|h1))\\.0?7\\.(\\d+)")

    fun isProfile7(format: Format): Boolean =
        format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION && format.codecs?.lowercase(Locale.ROOT)?.let(PROFILE_7::matches) == true

    /** The same stream announced as profile 8.1 (base layer + converted RPUs). */
    fun profile81(format: Format, level: String? = null): Format {
        val match = format.codecs?.lowercase(Locale.ROOT)?.let(PROFILE_7::matchEntire)
        val codecs = "${match?.groupValues?.get(1) ?: "dvhe"}.08.${level ?: match?.groupValues?.get(2)?.padStart(2, '0') ?: "06"}"
        return format.buildUpon().setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION).setCodecs(codecs).build()
    }

    /** The first RPU NAL of an Annex-B access unit, converted to profile 8.1; null when none converts. */
    fun convertedRpu(data: ByteArray, offset: Int, length: Int): ByteArray? {
        var result: ByteArray? = null
        forEachNal(data, offset, offset + length) { start, end ->
            if (result == null && type(data, start) == NAL_RPU) result = convert(data, start, end - start)
        }
        return result
    }

    /**
     * Single-track profile 7 sample → profile 8.1: enhancement-layer NALs are dropped, the RPU
     * is converted, everything else is copied. Returns the new length written to [out].
     */
    fun rewrite(data: ByteArray, offset: Int, length: Int, out: ByteArray): Int {
        var at = 0
        forEachNal(data, offset, offset + length) { start, end ->
            when (type(data, start)) {
                NAL_ENHANCEMENT -> Unit
                NAL_RPU -> convert(data, start, end - start)?.let { at = put(out, at, it, 0, it.size) }
                else -> at = put(out, at, data, start, end - start)
            }
        }
        return at
    }

    /** [sample] with [rpu] appended as its last NAL unit. */
    fun append(sample: ByteArray, length: Int, rpu: ByteArray): ByteArray {
        val out = sample.copyOf(length + START.size + rpu.size)
        START.copyInto(out, length)
        rpu.copyInto(out, length + START.size)
        return out
    }

    private val START = byteArrayOf(0, 0, 0, 1)
    private val scratch = ThreadLocal.withInitial { ByteArray(64 * 1024) }

    private fun convert(data: ByteArray, start: Int, length: Int): ByteArray? {
        val out = scratch.get()!!
        val written = runCatching { BlurayNative.dolbyVisionP81(data, start, length, out) }.getOrDefault(-1)
        return if (written > 0) out.copyOf(written) else null
    }

    private fun put(out: ByteArray, at: Int, source: ByteArray, start: Int, length: Int): Int {
        START.copyInto(out, at)
        source.copyInto(out, at + START.size, start, start + length)
        return at + START.size + length
    }

    private fun type(data: ByteArray, start: Int) = (data[start].toInt() shr 1) and 0x3F

    /** NAL payloads (header included, start codes and trailing zero bytes excluded). */
    private inline fun forEachNal(data: ByteArray, from: Int, to: Int, block: (start: Int, end: Int) -> Unit) {
        var i = from
        var nal = -1
        while (i + 2 < to) {
            if (data[i].toInt() == 0 && data[i + 1].toInt() == 0 && data[i + 2].toInt() == 1) {
                if (nal >= 0) block(nal, trimZeros(data, nal, i))
                i += 3
                nal = i
            } else {
                i++
            }
        }
        if (nal in 0 until to) block(nal, trimZeros(data, nal, to))
    }

    private fun trimZeros(data: ByteArray, start: Int, end: Int): Int {
        var e = end
        while (e > start + 1 && data[e - 1].toInt() == 0) e--
        return e
    }
}
