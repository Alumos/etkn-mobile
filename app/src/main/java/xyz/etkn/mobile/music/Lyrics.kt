package xyz.etkn.mobile.music

import androidx.compose.runtime.Immutable

@Immutable
data class LyricLine(val timeMs: Long, val text: String)

@Immutable
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean) {
    /** Index of the line active at [positionMs] (binary search; lines are sorted). */
    fun indexAt(positionMs: Long): Int {
        if (!synced || lines.isEmpty()) return -1
        var lo = 0
        var hi = lines.lastIndex
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        return found
    }

    companion object {
        private val TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
        private val META = Regex("""^\[(ar|ti|al|by|offset|re|ve|length|au):.*]$""", RegexOption.IGNORE_CASE)

        /**
         * Parses LRC (multiple time tags per line supported) or plain text. Blank lines and
         * lines with only time tags are dropped, as on TV.
         */
        fun parse(text: String): Lyrics {
            val out = ArrayList<LyricLine>()
            var sawTag = false
            text.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || META.matches(line)) return@forEach
                val tags = TAG.findAll(line).toList()
                if (tags.isEmpty()) {
                    out += LyricLine(-1, line)
                    return@forEach
                }
                sawTag = true
                val content = line.substring(tags.last().range.last + 1).trim()
                if (content.isEmpty()) return@forEach
                tags.forEach { m ->
                    val min = m.groupValues[1].toLong()
                    val sec = m.groupValues[2].toLong()
                    val frac = m.groupValues[3]
                    val ms = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
                    out += LyricLine(min * 60_000 + sec * 1_000 + ms, content)
                }
            }
            return if (sawTag) Lyrics(out.filter { it.timeMs >= 0 }.sortedBy { it.timeMs }, true) else Lyrics(out, false)
        }
    }
}
