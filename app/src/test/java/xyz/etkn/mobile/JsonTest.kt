package xyz.etkn.mobile

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.etkn.mobile.data.model.Item
import xyz.etkn.mobile.data.net.EtknJson
import xyz.etkn.mobile.data.net.rows

class JsonTest {
    @Test
    fun toleratesUnexpectedTypes() {
        val raw = EtknJson.parseToJsonElement(
            """{"Items":[{"Id":"item-1","Name":"片名","Type":"Episode","IndexNumber":"3","ParentIndexNumber":1,
               "RunTimeTicks":36000000000,"ImageTags":{"Primary":"abc","Bad":5},"UserData":{"PlaybackPositionTicks":18000000000},
               "TMDbRating":"8.4","Genres":["剧情",null]}]}""",
        )
        val item = Item.from(raw.rows().first())
        assertEquals("S1E3", item.episodeCode)
        assertEquals(8.4, item.tmdbRating!!, 0.0001)
        assertEquals(0.5f, item.progress, 0.0001f)
        assertEquals(listOf("剧情"), item.genres)
        assertEquals("abc", item.imageTags["Primary"])
    }

    @Test
    fun arrayBodiesAndNestedRowKeys() {
        val nested = EtknJson.parseToJsonElement("""{"data":{"items":[{"Id":"a"},{"Id":"b"}]}}""") as JsonObject
        assertEquals(2, nested.rows().size)
        assertTrue(EtknJson.parseToJsonElement("{}").rows().isEmpty())
    }
}
