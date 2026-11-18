package xyz.etkn.mobile.player.danmaku

import kotlinx.serialization.json.JsonObject
import xyz.etkn.mobile.data.net.int
import xyz.etkn.mobile.data.net.long
import xyz.etkn.mobile.data.net.str
import kotlin.math.ceil
import kotlin.math.floor

/** One comment: [timeMs] on the media clock, ARGB [color], [mode] 1 scroll ←, 4 bottom, 5 top, 6 scroll →. */
data class DanmakuCue(val timeMs: Long, val text: String, val color: Int, val mode: Int)

/** `GET /api/tv/Items/{id}/Danmaku` result. `status` is one of ok / stale / busy / unavailable / unmatched. */
data class DanmakuPage(val enabled: Boolean, val status: String, val cues: List<DanmakuCue>)

object DanmakuTimeline {
    private const val MAX_CUES = 3000
    private const val MAX_TIME_MS = 604_800_000L
    private const val MAX_CODE_POINTS = 200

    /** Tolerant parse of `comments[]` (same filters as ETKN TV), sorted by time. */
    fun parse(rows: List<JsonObject>): List<DanmakuCue> {
        val out = ArrayList<DanmakuCue>(minOf(rows.size, MAX_CUES))
        for (row in rows.asSequence().take(MAX_CUES)) {
            val time = row.long("time", -1)
            val text = row.str("text").replace('\n', ' ').trim()
            val mode = row.int("mode", 1)
            if (time < 0 || time > MAX_TIME_MS || text.isEmpty() || mode !in Modes) continue
            out += DanmakuCue(time, truncate(text), (0xFF000000.toInt()) or (row.int("color", 0xFFFFFF) and 0xFFFFFF), mode)
        }
        out.sortBy { it.timeMs }
        return out
    }

    /** Merges adjacent windows, dropping exact duplicates that both windows returned. */
    fun merge(pages: Collection<List<DanmakuCue>>): List<DanmakuCue> {
        val seen = HashSet<DanmakuCue>()
        val out = ArrayList<DanmakuCue>(pages.sumOf { it.size })
        pages.forEach { page -> page.forEach { if (seen.add(it)) out += it } }
        out.sortBy { it.timeMs }
        return out
    }

    /** Cuts to 200 code points without splitting a grapheme (ZWJ sequences, flags, modifiers). */
    fun truncate(text: String): String {
        if (text.codePointCount(0, text.length) <= MAX_CODE_POINTS) return text
        var end = text.offsetByCodePoints(0, MAX_CODE_POINTS)
        while (end > 0) {
            val point = text.codePointAt(end)
            val previous = text.codePointBefore(end)
            val type = Character.getType(point)
            if (point == 0x200D || previous == 0x200D ||
                type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt() || point in 0x1F3FB..0x1F3FF || point in 0xE0020..0xE007F
            ) {
                end -= Character.charCount(previous)
            } else if (point in RegionalIndicators && previous in RegionalIndicators) {
                var start = end
                while (start > 0) {
                    val prior = text.codePointBefore(start)
                    if (prior !in RegionalIndicators) break
                    start -= Character.charCount(prior)
                }
                if (text.codePointCount(start, end) % 2 != 0) end -= Character.charCount(previous)
                break
            } else {
                break
            }
        }
        return text.substring(0, end)
    }

    private val Modes = setOf(1, 4, 5, 6)
    private val RegionalIndicators = 0x1F1E6..0x1F1FF
}

/** A cue placed on a lane with its actual start time (possibly delayed to avoid overlap). */
class PlacedCue(val cue: DanmakuCue, val startMs: Long, val width: Float, val lane: Int) {
    fun x(nowMs: Long, screen: Int, lifeMs: Long): Float {
        val fraction = (nowMs - startMs) / lifeMs.toFloat()
        return when (cue.mode) {
            4, 5 -> (screen - width) / 2f
            6 -> -width + (screen + width) * fraction
            else -> screen - (screen + width) * fraction
        }
    }
}

/**
 * Lane assignment from ETKN TV: a cue may be delayed by at most 1.5 s to find a lane where it
 * neither overlaps nor catches up with the previous scroller; density sampling is keyed on the
 * cue itself so merging or evicting windows never reshuffles what is shown.
 */
object DanmakuLayout {
    fun place(
        cues: List<DanmakuCue>,
        lanes: Int,
        screen: Int,
        lifeMs: Long,
        gap: Float,
        density: Int,
        measure: (String) -> Float,
    ): List<PlacedCue> {
        val result = ArrayList<PlacedCue>()
        if (lanes <= 0 || screen <= 0) return result
        val previous = arrayOfNulls<PlacedCue>(lanes)
        var last = -1000L
        var next = 0
        val spacing = (900L / lanes).coerceIn(60L, 220L)
        for (cue in cues) {
            if (Math.floorMod(sampleHash(cue), 100) >= density) continue
            val width = measure(cue.text)
            val desired = maxOf(cue.timeMs, last + spacing)
            var best = Long.MAX_VALUE
            var chosen = 0
            for (offset in 0 until lanes) {
                val lane = (next + offset) % lanes
                val old = previous[lane]
                var start = desired
                if (old != null) {
                    start = if ((cue.mode == 1 || cue.mode == 6) && old.cue.mode == cue.mode) {
                        val priorSpeed = (screen + old.width) / lifeMs.toDouble()
                        val newSpeed = (screen + width) / lifeMs.toDouble()
                        val clear = old.startMs + ceil((old.width + gap) / priorSpeed).toLong()
                        val catchUp = old.startMs + lifeMs - floor((screen - gap) / newSpeed).toLong()
                        maxOf(start, clear, catchUp)
                    } else {
                        maxOf(start, old.startMs + lifeMs)
                    }
                }
                if (start < best) {
                    best = start
                    chosen = lane
                }
            }
            if (best - cue.timeMs > 1500) continue
            val entry = PlacedCue(cue, best, width, chosen)
            result += entry
            previous[chosen] = entry
            last = best
            next = (chosen + 1) % lanes
        }
        return result
    }

    /** Index of the first cue starting at or after [timeMs] (entries are in start order). */
    fun firstAt(entries: List<PlacedCue>, timeMs: Long): Int {
        var low = 0
        var high = entries.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (entries[mid].startMs < timeMs) low = mid + 1 else high = mid
        }
        return low
    }

    private fun sampleHash(cue: DanmakuCue): Int {
        var hash = java.util.Objects.hash(cue.timeMs, cue.text, cue.mode, cue.color)
        hash = hash xor (hash ushr 16)
        hash *= 0x45d9f3b
        hash = hash xor (hash ushr 16)
        return hash
    }
}
