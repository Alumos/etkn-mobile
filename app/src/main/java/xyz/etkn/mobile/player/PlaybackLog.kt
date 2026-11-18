package xyz.etkn.mobile.player

import android.util.Log
import androidx.media3.common.Format
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener

/**
 * The few player facts worth keeping in the exported log: which decoder handles which format,
 * and anything that goes wrong (codec errors, sink errors, underruns, bursts of dropped frames).
 * Playback errors themselves are logged with their stack by [VideoSession].
 */
internal class PlaybackLog : AnalyticsListener {
    override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) =
        info("video ${Format.toLogString(format)}")

    override fun onAudioInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) =
        info("audio ${Format.toLogString(format)}")

    override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) =
        info("video decoder $decoderName ${initializationDurationMs}ms")

    override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) =
        info("audio decoder $decoderName ${initializationDurationMs}ms")

    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
        if (droppedFrames >= DROPPED_BURST) info("dropped $droppedFrames frames in ${elapsedMs}ms")
    }

    override fun onAudioUnderrun(eventTime: AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) =
        info("audio underrun buffer=${bufferSizeMs}ms")

    override fun onVideoCodecError(eventTime: AnalyticsListener.EventTime, videoCodecError: Exception) {
        Log.e(TAG, "video codec error", videoCodecError)
    }

    override fun onAudioCodecError(eventTime: AnalyticsListener.EventTime, audioCodecError: Exception) {
        Log.e(TAG, "audio codec error", audioCodecError)
    }

    override fun onAudioSinkError(eventTime: AnalyticsListener.EventTime, audioSinkError: Exception) {
        Log.e(TAG, "audio sink error", audioSinkError)
    }

    private fun info(message: String) {
        Log.i(TAG, message)
    }

    private companion object {
        const val TAG = "ETKN-Player"
        const val DROPPED_BURST = 24
    }
}
