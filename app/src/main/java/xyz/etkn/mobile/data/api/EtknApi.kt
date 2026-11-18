package xyz.etkn.mobile.data.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import xyz.etkn.mobile.data.model.EpisodePage
import xyz.etkn.mobile.data.model.Item
import xyz.etkn.mobile.data.model.ItemsPage
import xyz.etkn.mobile.data.model.MediaSource
import xyz.etkn.mobile.data.model.MusicHome
import xyz.etkn.mobile.data.model.MusicPage
import xyz.etkn.mobile.data.model.PlaybackInfo
import xyz.etkn.mobile.data.model.Playlist
import xyz.etkn.mobile.data.model.UserInfo
import xyz.etkn.mobile.data.model.itemList
import xyz.etkn.mobile.data.model.items
import xyz.etkn.mobile.data.net.ApiClient
import xyz.etkn.mobile.data.net.Credentials
import xyz.etkn.mobile.data.net.EtknException
import xyz.etkn.mobile.data.net.bool
import xyz.etkn.mobile.data.net.enc
import xyz.etkn.mobile.data.net.int
import xyz.etkn.mobile.data.net.obj
import xyz.etkn.mobile.data.net.objects
import xyz.etkn.mobile.data.net.query
import xyz.etkn.mobile.data.net.str
import xyz.etkn.mobile.data.net.strOrNull
import xyz.etkn.mobile.data.session.ActiveSession

/** Sort keys exposed by the TV poster wall; ratings are separate server fields. */
enum class SortKey(val wire: String, val label: String) {
    DateCreated("DateCreated", "最新入库"),
    PremiereDate("PremiereDate", "首映日期"),
    SortName("SortName", "名称"),
    Tmdb("TMDbRating", "TMDb 评分"),
    Douban("DoubanRating", "豆瓣评分"),
    Imdb("IMDbRating", "IMDb 评分"),
    Critic("CriticRating", "烂番茄"),
    Year("ProductionYear", "年份"),
    Played("DatePlayed", "最近播放"),
    Random("Random", "随机"),
}

data class LibraryQuery(
    val parentId: String,
    val includeTypes: String?,
    val sort: SortKey = SortKey.DateCreated,
    val descending: Boolean = true,
    val favoritesOnly: Boolean = false,
    val genre: String? = null,
)

/**
 * Typed wrapper over the ETKN TV-dedicated routes (`/api/tv`). Every call is bound to the
 * session it was issued with; callers compare [ActiveSession.generation] to drop stale results.
 */
class EtknApi(
    private val client: ApiClient,
    private val session: () -> ActiveSession,
    private val onExpired: (ActiveSession) -> Unit,
) {

    private val s get() = session()
    private fun cred(a: ActiveSession = s) = Credentials(a.server, a.token)
    private fun u(a: ActiveSession = s) = "/api/tv/Users/${enc(a.userId)}"

    private suspend fun send(method: String, path: String, body: JsonElement? = null): JsonObject {
        val active = s
        try {
            return client.call(cred(active), method, path, body)
        } catch (expired: EtknException.AuthExpired) {
            onExpired(active)
            throw expired
        }
    }

    suspend fun get(path: String): JsonObject = send("GET", path)
    suspend fun post(path: String, body: JsonElement = EMPTY): JsonObject = send("POST", path, body)
    suspend fun put(path: String, body: JsonElement): JsonObject = send("PUT", path, body)
    suspend fun delete(path: String): JsonObject = send("DELETE", path)

    // ---------- Account ----------

    suspend fun authenticate(server: String, username: String, password: String): Pair<String, UserInfo> {
        val body = buildJsonObject { put("Username", username); put("Pw", password) }
        val result = client.call(Credentials(server, ""), "POST", "/api/tv/Users/AuthenticateByName", body)
        val token = result.str("AccessToken")
        val user = result.obj("User") ?: throw EtknException.BadResponse(200)
        if (token.isEmpty()) throw EtknException.BadResponse(200)
        return token to UserInfo.from(user, topLevelPrivate = result.bool("PrivateAccount"))
    }

    suspend fun me(server: String, token: String): UserInfo =
        UserInfo.from(client.call(Credentials(server, token), "GET", "/api/tv/Users/Me"))

    suspend fun rememberAdmin(server: String, token: String): Boolean {
        val value = client.call(Credentials(server, token), "GET", "/api/tv/Users/Me/AccountPreferences")
        return value.bool("remember_admin_account")
    }

    suspend fun logout(a: ActiveSession) {
        runCatching { client.call(cred(a), "POST", "/api/tv/Sessions/Logout", EMPTY) }
    }

    suspend fun clientPreferences(): JsonObject = get("/api/tv/Users/Me/ClientPreferences")

    suspend fun canAccessDiscovery(): Boolean =
        runCatching { get("/api/tv/auth/check_status").obj("user")?.bool("can_access_discovery") == true }.getOrDefault(false)

    // ---------- Home ----------

    suspend fun views(): List<Item> = get("${u()}/Views").items()

    suspend fun resume(limit: Int = 24): List<Item> =
        get(query("${u()}/Items/Resume", "Limit" to limit, "Fields" to "Overview")).items()

    suspend fun latest(parentId: String, includeTypes: String?, limit: Int = 24): List<Item> =
        get(query("${u()}/Items/Latest", "ParentId" to parentId, "IncludeItemTypes" to includeTypes, "Limit" to limit)).items()

    suspend fun recommendations(seed: String, cursor: String?, limit: Int = 1): ItemsPage {
        val path = "${u()}/Items/PersonalRecommendations?Stream=true&Limit=$limit&Fields=Overview" +
            "&ShuffleSeed=${enc(seed)}&Cursor=${enc(cursor ?: "")}"
        val result = get(path)
        val next = result["NextCursor"]?.let { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        return ItemsPage(result.items(), 0, next)
    }

    // ---------- Library ----------

    suspend fun libraryItems(q: LibraryQuery, start: Int, limit: Int): ItemsPage {
        val path = query(
            "${u()}/Items",
            "Recursive" to true,
            "ParentId" to q.parentId,
            "IncludeItemTypes" to q.includeTypes,
            "Limit" to limit,
            "StartIndex" to start,
            "SortBy" to q.sort.wire,
            "SortOrder" to if (q.sort == SortKey.Random) "Ascending" else if (q.descending) "Descending" else "Ascending",
            "Fields" to LIST_FIELDS,
            "Filters" to if (q.favoritesOnly) "IsFavorite" else null,
            "Genres" to q.genre,
        )
        val result = get(path)
        val items = result.items()
        return ItemsPage(items, result.int("TotalRecordCount", start + items.size))
    }

    suspend fun children(parentId: String): List<Item> = get(query("${u()}/Items", "ParentId" to parentId)).items()

    suspend fun favorites(start: Int, limit: Int): ItemsPage {
        val path = query(
            "${u()}/Browse",
            "Limit" to limit, "StartIndex" to start, "SortBy" to "DateCreated", "SortOrder" to "Descending",
            "Filters" to "IsFavorite",
        )
        val result = get(path)
        val items = result.items()
        return ItemsPage(items, result.int("TotalRecordCount", start + items.size))
    }

    suspend fun search(term: String, start: Int, limit: Int): ItemsPage {
        val path = query(
            "${u()}/Items",
            "Recursive" to true, "IncludeItemTypes" to "Movie,Series", "Limit" to limit, "StartIndex" to start,
            "SortBy" to "SortName", "Fields" to "PrimaryImageAspectRatio,Overview,ProductionYear,CommunityRating,TMDbRating,UserData",
            "SearchTerm" to term,
        )
        val result = get(path)
        val items = result.items()
        return ItemsPage(items, result.int("TotalRecordCount", start + items.size))
    }

    suspend fun collections(libraryId: String): List<Item> =
        get(query("${u()}/Libraries/${enc(libraryId)}/Collections", "Limit" to 200)).items()

    suspend fun itemCollections(itemId: String): List<Item> =
        get(query("${u()}/Items/${enc(itemId)}/Collections", "Limit" to 60)).items()

    // ---------- Detail ----------

    suspend fun item(id: String, fields: String? = null): Item =
        Item.from(get(query("${u()}/Items/${enc(id)}", "Fields" to fields)))

    suspend fun similar(id: String): List<Item> =
        get(query("/api/tv/Items/${enc(id)}/Similar", "UserId" to s.userId, "Limit" to 20)).items()

    suspend fun person(id: String): Item = Item.from(get(query("/api/tv/Persons/${enc(id)}", "UserId" to s.userId)))

    suspend fun personItems(id: String, start: Int, limit: Int = 40): ItemsPage {
        val result = get(
            query(
                "/api/tv/Persons/${enc(id)}/Items",
                "UserId" to s.userId, "IncludeItemTypes" to "Movie,Series", "StartIndex" to start, "Limit" to limit,
            ),
        )
        val items = result.items()
        return ItemsPage(items, result.int("TotalRecordCount", start + items.size))
    }

    suspend fun seasons(seriesId: String): List<Item> =
        get(query("/api/tv/Shows/${enc(seriesId)}/Seasons", "UserId" to s.userId)).items()

    suspend fun playbackTarget(seriesId: String, seasonId: String? = null): Item? =
        get(
            query(
                "/api/tv/Shows/${enc(seriesId)}/PlaybackTarget",
                "UserId" to s.userId, "Fields" to "MediaSources,MediaStreams,Overview", "SeasonId" to seasonId,
            ),
        ).items().firstOrNull()

    suspend fun episodePage(
        seriesId: String,
        seasonId: String?,
        start: Int? = null,
        anchorId: String? = null,
        limit: Int = 40,
    ): EpisodePage {
        val result = get(
            query(
                "/api/tv/Shows/${enc(seriesId)}/EpisodePage",
                "UserId" to s.userId, "Limit" to limit, "SeasonId" to seasonId,
                "AnchorId" to anchorId, "StartIndex" to start,
            ),
        )
        val items = result.items()
        val startIndex = result.int("StartIndex", start ?: 0)
        return EpisodePage(
            items = items,
            startIndex = startIndex,
            nextStartIndex = result.int("NextStartIndex", startIndex + items.size),
            total = result.int("TotalRecordCount", startIndex + items.size),
        )
    }

    suspend fun nextEpisode(itemId: String): Item? =
        get(query("/api/tv/Items/${enc(itemId)}/NextEpisode", "UserId" to s.userId)).items().firstOrNull()

    suspend fun setFavorite(id: String, favorite: Boolean): JsonObject {
        val path = "${u()}/FavoriteItems/${enc(id)}"
        return if (favorite) post(path) else delete(path)
    }

    suspend fun setPlayed(id: String, played: Boolean): JsonObject {
        val path = "${u()}/PlayedItems/${enc(id)}"
        return if (played) post(path) else delete(path)
    }

    suspend fun hideFromResume(id: String) {
        post("${u()}/Items/${enc(id)}/HideFromResume")
    }

    // ---------- Playback ----------

    suspend fun playbackInfo(itemId: String, audio: Boolean = false): PlaybackInfo {
        val path = if (audio) "/api/tv/Items/${enc(itemId)}/PlaybackInfo"
        else query("/api/tv/Items/${enc(itemId)}/PlaybackInfo", "UserId" to s.userId)
        val result = post(path)
        return PlaybackInfo(result.str("PlaySessionId"), result.objects("MediaSources").map(MediaSource::from))
    }

    /** `event` is "" (started), "Progress" or "Stopped". */
    suspend fun report(event: String, payload: JsonObject, a: ActiveSession = s) {
        val path = "/api/tv/Sessions/Playing" + if (event.isEmpty()) "" else "/$event"
        client.call(cred(a), "POST", path, payload)
    }

    // ---------- Music ----------

    suspend fun musicHome(parentId: String, frequent: Int = 12): MusicHome {
        val result = get(
            query(
                "${u()}/Music",
                "ParentId" to parentId, "Key" to "", "Tab" to "recommended", "SortBy" to "Name",
                "SortOrder" to "Ascending", "FrequentLimit" to frequent,
            ),
        )
        return MusicHome(result.itemList("Latest"), result.itemList("Recent"), result.itemList("Frequent"))
    }

    suspend fun musicList(
        parentId: String,
        tab: String,
        key: String,
        start: Int,
        limit: Int = 60,
        tracksOnly: Boolean = false,
        newest: Boolean = key.isEmpty() && tab in setOf("albums", "artists", "songs"),
    ): MusicPage {
        val result = get(
            query(
                "${u()}/Music",
                "ParentId" to parentId, "Tab" to tab, "Key" to key,
                "SortBy" to if (newest) "DateCreated" else "Name",
                "SortOrder" to if (newest) "Descending" else "Ascending",
                "Tracks" to if (tracksOnly) 1 else null,
                "StartIndex" to start, "Limit" to limit,
            ),
        )
        val items = result.items()
        return MusicPage(
            items = items,
            folders = result.itemList("Folders"),
            total = result.int("TotalRecordCount", start + items.size),
            title = result.str("Title"),
            artistOverview = result.obj("Artist")?.strOrNull("Overview"),
        )
    }

    suspend fun playlists(parentId: String): List<Playlist> {
        val result = get(
            query("${u()}/Music", "ParentId" to parentId, "Tab" to "playlists", "Key" to "", "SortBy" to "Name", "SortOrder" to "Ascending"),
        )
        return result.objects("Playlists").map { Playlist(it.str("Key"), it.str("Name"), it.int("Count")) }
    }

    suspend fun createPlaylist(name: String): String =
        post("${u()}/Music/Playlists", buildJsonObject { put("Name", name.trim().take(80)) }).str("Key")

    suspend fun deletePlaylist(key: String) {
        delete("${u()}/Music/Playlists/${enc(key)}")
    }

    suspend fun addToPlaylist(key: String, itemId: String) {
        post("${u()}/Music/Playlists/${enc(key)}/Items/${enc(itemId)}")
    }

    suspend fun removeFromPlaylist(key: String, itemId: String) {
        delete("${u()}/Music/Playlists/${enc(key)}/Items/${enc(itemId)}")
    }

    /** GET first; only an empty (successful) text triggers the online lookup POST. */
    suspend fun lyrics(itemId: String, sourceId: String): String {
        val path = query("/api/tv/Music/${enc(itemId)}/Lyrics", "MediaSourceId" to sourceId)
        val text = get(path).str("text")
        if (text.isNotBlank()) return text
        return runCatching { post(path).str("text") }.getOrDefault("")
    }

    companion object {
        val EMPTY = JsonObject(emptyMap())
        const val LIST_FIELDS =
            "PrimaryImageAspectRatio,Overview,CommunityRating,TMDbRating,DoubanRating,IMDbRating,CriticRating,ProductionYear,RecursiveItemCount,UserData"
        const val DETAIL_FIELDS = "MediaSources,MediaStreams,Overview"

        fun includeTypesFor(collectionType: String?): String? = when (collectionType) {
            "movies" -> "Movie"
            "tvshows" -> "Series"
            "homevideos" -> "Video"
            "boxsets" -> "BoxSet"
            "musicvideos" -> "MusicVideo"
            else -> "Movie,Series,Video"
        }

        fun stringArray(values: List<String>?): JsonElement =
            values?.let { JsonArray(it.map(::JsonPrimitive)) } ?: kotlinx.serialization.json.JsonNull
    }
}
