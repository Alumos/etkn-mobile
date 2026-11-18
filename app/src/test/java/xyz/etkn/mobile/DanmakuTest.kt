package xyz.etkn.mobile

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.etkn.mobile.data.net.EtknJson
import xyz.etkn.mobile.data.net.objects
import xyz.etkn.mobile.player.danmaku.DanmakuCue
import xyz.etkn.mobile.player.danmaku.DanmakuLayout
import xyz.etkn.mobile.player.danmaku.DanmakuTimeline

class DanmakuTest {
    private fun rows(json: String): List<JsonObject> = (EtknJson.parseToJsonElement(json) as JsonObject).objects("comments")

    @Test
    fun parseFiltersAndSorts() {
        val cues = DanmakuTimeline.parse(
            rows(
                """{"comments":[
                {"time":5000,"text":"后","mode":1,"color":16711680},
                {"time":1000,"text":" 前\n行 ","mode":5},
                {"time":-1,"text":"负时间"},
                {"time":2000,"text":"","mode":1},
                {"time":3000,"text":"高级弹幕","mode":7}
                ]}""",
            ),
        )
        assertEquals(listOf("前 行", "后"), cues.map { it.text })
        assertEquals(0xFFFFFFFF.toInt(), cues[0].color)
        assertEquals(0xFFFF0000.toInt(), cues[1].color)
    }

    @Test
    fun truncateKeepsEmojiSequencesWhole() {
        val family = "👨‍👩‍👧"
        val text = "a".repeat(198) + family
        val cut = DanmakuTimeline.truncate(text)
        assertEquals("a".repeat(198), cut)
        assertEquals("短文本", DanmakuTimeline.truncate("短文本"))
    }

    @Test
    fun mergeDropsDuplicates() {
        val a = DanmakuCue(1000, "x", -1, 1)
        val b = DanmakuCue(2000, "y", -1, 1)
        assertEquals(listOf(a, b), DanmakuTimeline.merge(listOf(listOf(b, a), listOf(a))))
    }

    @Test
    fun scrollingCuesOnOneLaneNeverOverlap() {
        val cues = (0 until 40).map { DanmakuCue(it * 100L, "弹幕$it", -1, 1) }
        val screen = 1000
        val life = 8000L
        val placed = DanmakuLayout.place(cues, lanes = 3, screen = screen, lifeMs = life, gap = 16f, density = 100) { 120f }
        assertTrue(placed.isNotEmpty())
        placed.groupBy { it.lane }.values.forEach { lane ->
            lane.zipWithNext().forEach { (first, second) ->
                // When the follower enters, the leader's tail has cleared the right edge by the gap.
                val leaderX = first.x(second.startMs, screen, life)
                assertTrue(leaderX + first.width + 16f <= screen + 0.5f)
            }
        }
        placed.forEach { assertTrue(it.startMs - it.cue.timeMs <= 1500) }
        assertEquals(placed.sortedBy { it.startMs }, placed)
    }

    @Test
    fun densitySamplingIsStable() {
        val cues = (0 until 400).map { DanmakuCue(it * 500L, "c$it", -1, 1) }
        val half = DanmakuLayout.place(cues, 16, 2000, 8000, 16f, 50) { 40f }.map { it.cue }
        val again = DanmakuLayout.place(cues.reversed().sortedBy { it.timeMs }, 16, 2000, 8000, 16f, 50) { 40f }.map { it.cue }
        assertEquals(half, again)
        assertTrue(half.size in 120..280)
    }
}
