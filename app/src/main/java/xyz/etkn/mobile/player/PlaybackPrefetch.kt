package xyz.etkn.mobile.player

import android.os.SystemClock
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary
import xyz.etkn.mobile.player.mpv.MpvAssets
import xyz.etkn.mobile.AppGraph
import xyz.etkn.mobile.data.model.PlaybackInfo
import xyz.etkn.mobile.data.model.Item

/**
 * Starts PlaybackInfo the moment Play is tapped, so the request overlaps the player activity's
 * start-up instead of following it. The tapped item travels along so the title shows at once
 * and playback never waits for the full detail request. Main thread only.
 */
object PlaybackPrefetch {
    private const val FRESH_MS = 30_000L

    class Handoff(val item: Item, val info: Deferred<PlaybackInfo>)

    private var pending: Pair<Long, Handoff>? = null

    /** Series / seasons resolve their episode in the player first, so only playable items prefetch. */
    fun start(graph: AppGraph, item: Item) {
        warmUp(graph)
        if (item.isSeries || item.isSeason) {
            pending = null
            return
        }
        val api = graph.api
        val info = graph.scope.async(start = CoroutineStart.UNDISPATCHED) { api.playbackInfo(item.id) }
        pending = SystemClock.elapsedRealtime() to Handoff(item, info)
    }

    /** Loads both cores' native libraries off the main thread while the player activity starts. */
    private fun warmUp(graph: AppGraph) {
        graph.scope.launch(Dispatchers.IO) {
            runCatching {
                MpvAssets.prepare(graph.app)
                Class.forName("is.xyz.mpv.MPVLib")
                FfmpegLibrary.isAvailable()
            }
        }
    }

    fun take(itemId: String): Handoff? {
        val (at, handoff) = pending ?: return null
        pending = null
        if (handoff.item.id != itemId || SystemClock.elapsedRealtime() - at > FRESH_MS) {
            handoff.info.cancel()
            return null
        }
        return handoff
    }
}
