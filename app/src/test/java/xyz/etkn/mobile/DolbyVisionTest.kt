package xyz.etkn.mobile

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.etkn.mobile.player.DolbyVision

class DolbyVisionTest {
    /** Annex-B HEVC NAL unit: 4-byte start code, 2-byte header of [type], payload. */
    private fun nal(type: Int, vararg payload: Int) =
        byteArrayOf(0, 0, 0, 1, (type shl 1).toByte(), 1) + payload.map { it.toByte() }.toByteArray()

    @Test
    fun enhancementLayerIsDroppedAndPictureNalsAreKept() {
        val picture = nal(1, 0xAF, 0x10) + nal(34, 0x42)
        // An RPU that cannot be converted (no native library on the JVM) is left out as well.
        val sample = nal(1, 0xAF, 0x10) + nal(63, 0x55, 0x66) + nal(34, 0x42) + nal(62, 0x01, 0x02)
        val out = ByteArray(sample.size + 64)
        val written = DolbyVision.rewrite(sample, 0, sample.size, out)
        assertArrayEquals(picture, out.copyOf(written))
        assertNull(DolbyVision.convertedRpu(sample, 0, sample.size))
    }

    @Test
    fun threeByteStartCodesAndTrailingZerosAreNormalised() {
        val sample = byteArrayOf(0, 0, 1, 2, 1, 0x7F, 0, 0, 0, 1, 0x7E, 1, 0x09, 0, 0)
        val out = ByteArray(64)
        val written = DolbyVision.rewrite(sample, 0, sample.size, out)
        assertArrayEquals(nal(1, 0x7F), out.copyOf(written))
    }

    @Test
    fun profile7IsAnnouncedAsProfile81() {
        val p7 = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION).setCodecs("dvhe.07.06").build()
        assertTrue(DolbyVision.isProfile7(p7))
        assertEquals("dvhe.08.06", DolbyVision.profile81(p7).codecs)
        assertEquals(MimeTypes.VIDEO_DOLBY_VISION, DolbyVision.profile81(p7).sampleMimeType)
        assertFalse(DolbyVision.isProfile7(p7.buildUpon().setCodecs("dvhe.08.06").build()))
        assertFalse(DolbyVision.isProfile7(p7.buildUpon().setSampleMimeType(MimeTypes.VIDEO_H265).build()))
        // Disc base layer (plain HEVC, no codecs string): default UHD level.
        assertEquals("dvhe.08.06", DolbyVision.profile81(Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265).build()).codecs)
    }

    @Test
    fun rpuIsAppendedAsTheLastNalUnit() {
        val frame = nal(1, 0x11)
        val rpu = byteArrayOf(0x7C, 0x01, 0x22)
        assertArrayEquals(frame + byteArrayOf(0, 0, 0, 1) + rpu, DolbyVision.append(frame + ByteArray(8), frame.size, rpu))
    }
}
