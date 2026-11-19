package xyz.etkn.mobile.player

import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.ForwardingTrackOutput
import androidx.media3.extractor.TrackOutput
import xyz.etkn.mobile.player.bluray.BlurayMediaSourceFactory

/**
 * Matroska / MP4 single-track Dolby Vision profile 7 (base + enhancement layer + RPU in one
 * stream) played as profile 8.1: each sample is collected whole, rewritten by
 * [DolbyVision.rewrite], then handed on. Other tracks and formats pass straight through.
 */
internal class DolbyVisionTrackOutput(output: TrackOutput, private val convert: Boolean) : ForwardingTrackOutput(output) {
    private var rewriting = false
    private var pending = ByteArray(0)
    private var size = 0
    private var out = ByteArray(0)

    override fun format(format: Format) {
        rewriting = convert && DolbyVision.isProfile7(format)
        super.format(if (rewriting) DolbyVision.profile81(format) else BlurayMediaSourceFactory.baseLayer(format))
    }

    override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
        if (!rewriting) return super.sampleData(input, length, allowEndOfInput, sampleDataPart)
        reserve(length)
        val read = input.read(pending, size, length)
        if (read == C.RESULT_END_OF_INPUT) {
            if (allowEndOfInput) return C.RESULT_END_OF_INPUT
            throw java.io.EOFException()
        }
        size += read
        return read
    }

    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
        if (!rewriting) return super.sampleData(data, length, sampleDataPart)
        reserve(length)
        data.readBytes(pending, size, length)
        size += length
    }

    override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
        if (!rewriting) return super.sampleMetadata(timeUs, flags, size, offset, cryptoData)
        // The sample is the `size` bytes ending `offset` bytes before what has been written.
        val start = this.size - offset - size
        if (start < 0) return super.sampleMetadata(timeUs, flags, size, offset, cryptoData)
        val written: Int
        val bytes: ByteArray
        if (cryptoData == null) {
            if (out.size < size + 8192) out = ByteArray(size + 8192)
            written = DolbyVision.rewrite(pending, start, size, out)
            bytes = out
        } else {
            written = size
            bytes = pending.copyOfRange(start, start + size)
        }
        super.sampleData(ParsableByteArray(bytes, written), written, C.SAMPLE_DATA_PART_MAIN)
        super.sampleMetadata(timeUs, flags, written, 0, cryptoData)
        // Bytes after the sample belong to the next one.
        pending.copyInto(pending, 0, this.size - offset, this.size)
        this.size = offset
    }

    private fun reserve(extra: Int) {
        if (size + extra > pending.size) pending = pending.copyOf(maxOf(pending.size * 2, size + extra, 1 shl 20))
    }
}
