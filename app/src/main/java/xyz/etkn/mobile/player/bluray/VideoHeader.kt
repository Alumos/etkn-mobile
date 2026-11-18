package xyz.etkn.mobile.player.bluray

import androidx.media3.common.ColorInfo
import androidx.media3.common.MimeTypes
import androidx.media3.container.NalUnitUtil

/**
 * Picture size and colour from the sequence parameter set in a disc track's Annex-B codec header
 * (VPS/SPS/PPS that FFmpeg's extract_extradata recovers), parsed the way Media3's own TS readers do.
 */
internal object VideoHeader {
    class Info(val width: Int, val height: Int, val color: ColorInfo)

    fun parse(mime: String, data: ByteArray): Info? {
        if (data.size < 4 || (mime != MimeTypes.VIDEO_H265 && mime != MimeTypes.VIDEO_H264)) return null
        val flags = BooleanArray(3)
        var start = NalUnitUtil.findNalUnit(data, 0, data.size, flags)
        while (start + 3 < data.size) {
            flags.fill(false)
            val next = NalUnitUtil.findNalUnit(data, start + 3, data.size, flags)
            val header = start + 3
            val info = runCatching {
                if (mime == MimeTypes.VIDEO_H265 && NalUnitUtil.getH265NalUnitType(data, start) == H265_SPS) {
                    val sps = NalUnitUtil.parseH265SpsNalUnit(data, header, next, null)
                    Info(sps.width, sps.height, color(sps.colorSpace, sps.colorRange, sps.colorTransfer))
                } else if (mime == MimeTypes.VIDEO_H264 && NalUnitUtil.getNalUnitType(data, start) == H264_SPS) {
                    val sps = NalUnitUtil.parseSpsNalUnit(data, header, next)
                    Info(sps.width, sps.height, color(sps.colorSpace, sps.colorRange, sps.colorTransfer))
                } else null
            }.getOrNull()
            if (info != null && info.width > 0 && info.height > 0) return info
            start = next
        }
        return null
    }

    private fun color(space: Int, range: Int, transfer: Int): ColorInfo =
        ColorInfo.Builder().setColorSpace(space).setColorRange(range).setColorTransfer(transfer).build()

    private const val H265_SPS = 33
    private const val H264_SPS = 7
}
