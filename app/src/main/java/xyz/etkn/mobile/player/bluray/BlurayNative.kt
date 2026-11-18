package xyz.etkn.mobile.player.bluray

import androidx.annotation.Keep
import androidx.media3.extractor.ExtractorInput
import java.io.IOException

/**
 * JNI surface of `libetkn_bluray.so` (third_party/bluray, built in CI by
 * scripts/build_bluray_native.py): libbluray + libudfread read the ISO's UDF image and pick the
 * main playlist; a demux-only FFmpeg splits its MPEG-TS packets for Media3's renderers.
 * Names and signatures are bound from native code — keep them in sync with bluray_jni.cpp.
 */
@Keep
internal object BlurayNative {
    /** Loaded lazily; a missing library surfaces as a playback error instead of a crash. */
    val loaded: Boolean by lazy { runCatching { System.loadLibrary("etkn_bluray") }.isSuccess }

    @Keep
    class Track(
        @JvmField val index: Int,
        @JvmField val pid: Int,
        @JvmField val type: Int,
        @JvmField val width: Int,
        @JvmField val height: Int,
        @JvmField val sampleRate: Int,
        @JvmField val channels: Int,
        @JvmField val pcmEncoding: Int,
        @JvmField val mime: String,
        @JvmField val language: String,
        @JvmField val initialization: ByteArray,
    )

    @JvmStatic @Throws(IOException::class) external fun open(input: BlurayInput, playlist: Int): Long
    @JvmStatic external fun size(handle: Long): Long
    @JvmStatic external fun duration(handle: Long): Long
    @JvmStatic external fun seekPoint(handle: Long, timeUs: Long): Long
    @JvmStatic @Throws(IOException::class) external fun seek(handle: Long, position: Long): Long
    @JvmStatic @Throws(IOException::class) external fun read(handle: Long, bytes: ByteArray, offset: Int, length: Int): Int
    @JvmStatic @Throws(IOException::class) external fun tracks(handle: Long, input: ExtractorInput, position: Long): Array<Track>
    @JvmStatic @Throws(IOException::class) external fun packet(handle: Long, input: ExtractorInput, bytes: ByteArray, metadata: LongArray): Int
    @JvmStatic external fun reset(handle: Long)
    @JvmStatic external fun close(handle: Long)
}
