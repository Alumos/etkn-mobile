package xyz.etkn.mobile.data.discover

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import xyz.etkn.mobile.data.net.bool
import xyz.etkn.mobile.data.net.double
import xyz.etkn.mobile.data.net.has
import xyz.etkn.mobile.data.net.int
import xyz.etkn.mobile.data.net.objects
import xyz.etkn.mobile.data.net.str

/**
 * 影视探索 (TMDb-backed, served through ETKN `/api/tv/discover`). IDs here are TMDb ids and never
 * mix with library item ids. Wire values: query kind `series`, media type `tv`.
 */
enum class DiscoverKind(val wire: String, val mediaType: String, val label: String) {
    Movie("movie", "movie", "电影"),
    Series("series", "tv", "剧集");

    companion object {
        fun ofMediaType(value: String?): DiscoverKind = if (value == "tv" || value == "series") Series else Movie
    }
}

@Immutable
class DiscoverItem(val raw: JsonObject, fallbackKind: DiscoverKind = DiscoverKind.Movie) {
    val id: String = raw.str("id")
    val title: String = titleOf(raw)
    val kind: DiscoverKind = if (raw.has("media_type")) DiscoverKind.ofMediaType(raw.str("media_type")) else fallbackKind
    val overview: String = raw.str("overview").trim()
    val tagline: String = (raw.str("tagline").ifEmpty { raw.str("Tagline") }).trim()
    val posterPath: String = raw.str("poster_path")
    val backdropPath: String = raw.str("backdrop_path")
    val vote: Double = raw.double("vote_average") ?: 0.0
    val year: String = yearOf(raw)
    val genreIds: List<String> = (raw["genre_ids"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
    val inLibrary: Boolean = raw.bool("in_library")
    val subscription: String = raw.str("subscription_status", "NONE").ifEmpty { "NONE" }
    val mediaItemId: String = raw.str("media_item_id")
    val status: DiscoverStatus? = DiscoverStatus.of(raw)

    fun copyWith(vararg values: Pair<String, JsonElement>): DiscoverItem = DiscoverItem(raw.withValues(*values), kind)

    /** Fills overview / tagline / backdrop from a detail payload without overwriting non-empty values. */
    fun enrich(details: JsonObject): DiscoverItem {
        val extra = listOf("backdrop_path", "overview", "tagline")
            .filter { raw.str(it).isEmpty() && details.str(it).isNotEmpty() }
            .map { it to details[it]!! }
        return if (extra.isEmpty()) this else copyWith(*extra.toTypedArray())
    }

    override fun equals(other: Any?) = other is DiscoverItem && other.raw == raw
    override fun hashCode() = raw.hashCode()
}

/** Library / subscription ribbon. */
enum class DiscoverStatus(val label: String) {
    InLibrary("已入库"), Subscribed("已订阅"), Wanted("待订阅"), Paused("已暂停"),
    Requested("待审核"), PendingRelease("未发行"), Ignored("已忽略");

    companion object {
        fun of(raw: JsonObject): DiscoverStatus? {
            if (raw.bool("in_library")) return InLibrary
            return when (raw.str("subscription_status")) {
                "SUBSCRIBED" -> Subscribed
                "WANTED" -> Wanted
                "PAUSED" -> Paused
                "REQUESTED" -> Requested
                "PENDING_RELEASE" -> PendingRelease
                "IGNORED" -> Ignored
                else -> null
            }
        }
    }
}

@Immutable
class DiscoverSeason(val raw: JsonObject) {
    val number: Int = raw.int("season_number")
    val name: String = raw.str("name")
    val episodes: Int = raw.int("episode_count")
    val airDate: String = raw.str("air_date")
    val posterPath: String = raw.str("poster_path")
    val inLibrary: Boolean = raw.bool("in_library")
    val subscription: String = raw.str("subscription_status", "NONE")
    val status: DiscoverStatus? = DiscoverStatus.of(raw)
    val subscribable: Boolean get() = number > 0 && !inLibrary && subscription != "SUBSCRIBED" && subscription != "WANTED"
}

@Immutable
data class DiscoverOption(val label: String, val value: String)

@Immutable
data class DiscoverPage(val items: List<DiscoverItem>, val totalPages: Int)

@Immutable
data class CastMember(val id: String, val name: String, val character: String, val profilePath: String)

@Immutable
data class DiscoverPerson(val name: String, val biography: String, val profilePath: String, val works: List<DiscoverItem>)

enum class DiscoverSort(val label: String, val key: String) {
    PopularDesc("热度", "popularity.desc"),
    DateDesc("最新上映", "date.desc"),
    VoteDesc("评分最高", "vote_average.desc"),
    PopularAsc("热度升序", "popularity.asc"),
    DateAsc("最早上映", "date.asc");

    fun wire(kind: DiscoverKind): String = when (key) {
        "date.desc" -> if (kind == DiscoverKind.Movie) "primary_release_date.desc" else "first_air_date.desc"
        "date.asc" -> if (kind == DiscoverKind.Movie) "primary_release_date.asc" else "first_air_date.asc"
        else -> key
    }
}

enum class DiscoverEra(val label: String, val from: Int?, val to: Int?) {
    All("全部年代", null, null),
    Now("2020 年代", 2020, null),
    Tens("2010 年代", 2010, 2019),
    Noughts("2000 年代", 2000, 2009),
    Nineties("90 年代", 1990, 1999),
    Older("更早", null, 1989),
}

/** Browse filters → `POST D/query/{kind}` body (TV field names and encodings). */
@Immutable
data class DiscoverFilter(
    val query: String = "",
    val sort: DiscoverSort = DiscoverSort.PopularDesc,
    val genres: Set<String> = emptySet(),
    val era: DiscoverEra = DiscoverEra.All,
    val minVote: Int? = null,
    val hideInLibrary: Boolean = false,
) {
    val active: Int get() = listOf(sort != DiscoverSort.PopularDesc, genres.isNotEmpty(), era != DiscoverEra.All, minVote != null, hideInLibrary).count { it }

    fun body(kind: DiscoverKind, page: Int): JsonObject = buildJsonObject {
        put("page", page)
        put("query", query.trim())
        put("sort_by", sort.wire(kind))
        put("hide_in_library", hideInLibrary)
        if (genres.isNotEmpty()) put("with_genres", genres.joinToString(","))
        era.from?.let { put("release_date_gte", "$it-01-01") }
        era.to?.let { put("release_date_lte", "$it-12-31") }
        minVote?.let { put("vote_average_gte", it.toDouble()) }
        putJsonArray("keywords") {}
        putJsonArray("studios") {}
    }
}

fun titleOf(raw: JsonObject): String {
    for (key in arrayOf("Name", "title", "name")) if (raw.has(key)) return raw.str(key)
    return "未命名"
}

fun yearOf(raw: JsonObject): String {
    val date = raw.str("release_date").ifEmpty { raw.str("first_air_date") }
    if (date.length >= 4) return date.substring(0, 4)
    val year = raw.int("year").takeIf { it > 0 } ?: raw.int("release_year")
    return if (year > 0) year.toString() else ""
}

fun options(raw: JsonObject, key: String): List<DiscoverOption> =
    raw.objects(key).mapNotNull { o -> o.str("value").ifEmpty { null }?.let { DiscoverOption(o.str("label").ifEmpty { it }, it) } }

/** Immutable "put": a copy of this object with the given keys replaced. */
fun JsonObject.withValues(vararg values: Pair<String, JsonElement>): JsonObject = JsonObject(LinkedHashMap(this).apply { values.forEach { (k, v) -> put(k, v) } })

fun JsonObject.without(key: String): JsonObject = JsonObject(LinkedHashMap(this).apply { remove(key) })

internal fun JsonElement?.isBlankValue(): Boolean = this == null || this is JsonNull || (this is JsonPrimitive && content.isEmpty())

/**
 * Per-session hand-off between discover screens (routes only carry ids) plus small caches for
 * filter options. Cleared when the account changes.
 */
class DiscoverStore {
    private val items = HashMap<String, DiscoverItem>()
    val options = HashMap<DiscoverKind, JsonObject>()

    /** `can_access_discovery` of the signed-in account; false until checked. */
    val allowed = MutableStateFlow(false)

    fun put(item: DiscoverItem) {
        items[key(item.kind, item.id)] = item
    }

    fun get(kind: DiscoverKind, id: String): DiscoverItem? = items[key(kind, id)]

    fun clear() {
        items.clear()
        options.clear()
        allowed.value = false
    }

    private fun key(kind: DiscoverKind, id: String) = "${kind.wire}:$id"
}
