package xyz.etkn.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.etkn.mobile.music.Lyrics

class LyricsTest {
    @Test
    fun parsesSyncedLinesAndDropsEmptyTimestamps() {
        val lyrics = Lyrics.parse(
            """
            [ti:Song]
            [00:01.50]第一句
            [00:03.00]
            [00:05.2][00:20.25]重复句
            """.trimIndent(),
        )
        assertTrue(lyrics.synced)
        assertEquals(listOf(1_500L, 5_200L, 20_250L), lyrics.lines.map { it.timeMs })
        assertEquals("重复句", lyrics.lines[1].text)
        assertEquals(-1, lyrics.indexAt(1_000))
        assertEquals(0, lyrics.indexAt(4_000))
        assertEquals(2, lyrics.indexAt(60_000))
    }

    @Test
    fun plainTextIsNotSynced() {
        val lyrics = Lyrics.parse("第一行\n\n第二行")
        assertFalse(lyrics.synced)
        assertEquals(2, lyrics.lines.size)
        assertEquals(-1, lyrics.indexAt(10_000))
    }
}
