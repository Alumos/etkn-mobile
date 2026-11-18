package xyz.etkn.mobile.player.danmaku

import android.os.SystemClock
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import xyz.etkn.mobile.data.api.EtknApi
import java.util.TreeMap

/**
 * Loads danmaku for one play session in 120 s windows around the playhead (TV contract): the
 * current window first, the next one 40 s before the boundary, at most one request in flight,
 * nothing while paused. All state is touched on the main thread only.
 */
class DanmakuFeed(
    private val api: EtknApi,
    private val scope: CoroutineScope,
    private val player: Player,
    private val itemId: String,
    private val playSession: String,
    private val onCues: (List<DanmakuCue>) -> Unit,
    private val onNotice: (String) -> Unit,
    private val onServiceOff: () -> Unit,
) {
    private val pages = TreeMap<Long, List<DanmakuCue>>()
    private var request: Job? = null
    private var wanted = -1L
    private var base = -1L
    private var retryAt = 0L
    private var firstLoad = true
    private var closed = false
    private var lastNotice = ""
    private val ticker: Job = scope.launch {
        while (isActive) {
            tick()
            delay(1_000)
        }
    }

    fun tick() {
        if (closed) return
        if (!player.isPlaying) {
            request?.cancel()
            request = null
            return
        }
        val position = player.currentPosition.coerceAtLeast(0)
        val current = position / WINDOW * WINDOW
        if (current != base) {
            base = current
            retryAt = 0
            if (request != null && (wanted < base || wanted > base + WINDOW)) {
                request?.cancel()
                request = null
            }
            pages.keys.removeAll { it < base - WINDOW || it > base + WINDOW }
            if (!pages.containsKey(base)) onCues(DanmakuTimeline.merge(pages.values))
        }
        val target = when {
            !pages.containsKey(base) -> base
            position >= base + WINDOW - PREFETCH && !pages.containsKey(base + WINDOW) -> base + WINDOW
            else -> return
        }
        if (request != null || SystemClock.elapsedRealtime() < retryAt) return
        val primary = firstLoad && target == base
        if (primary && lastNotice.isEmpty()) notice("正在拉取弹幕…")
        wanted = target
        var job: Job? = null
        job = scope.launch {
            val page = runCatching { api.danmaku(itemId, playSession, target) }
            if (closed || request !== job) return@launch
            request = null
            val result = page.getOrElse {
                if (primary) notice("弹幕拉取失败，稍后自动重试")
                retryAt = SystemClock.elapsedRealtime() + RETRY_MS
                return@launch
            }
            if (!result.enabled) {
                onNotice("弹幕服务已关闭")
                onServiceOff()
                return@launch
            }
            when (result.status) {
                "stale" -> { retryAt = SystemClock.elapsedRealtime() + 1_000; return@launch }
                "busy", "unavailable" -> {
                    if (primary) notice(if (result.status == "busy") "弹幕服务繁忙，稍后自动重试" else "弹幕拉取失败，稍后自动重试")
                    retryAt = SystemClock.elapsedRealtime() + RETRY_MS
                    return@launch
                }
            }
            pages[target] = result.cues
            onCues(DanmakuTimeline.merge(pages.values))
            if (primary) {
                firstLoad = false
                notice(
                    when {
                        result.status == "unmatched" -> "未匹配到该影片的弹幕"
                        result.cues.isEmpty() -> "当前时段暂无弹幕"
                        else -> "弹幕已加载"
                    },
                )
            }
        }
        request = job
    }

    private fun notice(message: String) {
        if (message == lastNotice) return
        lastNotice = message
        onNotice(message)
    }

    fun close() {
        if (closed) return
        closed = true
        ticker.cancel()
        request?.cancel()
        request = null
        pages.clear()
        onCues(emptyList())
    }

    private companion object {
        const val WINDOW = 120_000L
        const val PREFETCH = 40_000L
        const val RETRY_MS = 15_000L
    }
}
