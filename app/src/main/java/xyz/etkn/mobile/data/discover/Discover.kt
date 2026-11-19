package xyz.etkn.mobile.data.discover

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
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

/** One `GET D/people?q=` hit: TMDb person id, name, main department and best-known titles. */
@Immutable
data class PersonMatch(val id: String, val name: String, val department: String, val knownFor: String) {
    val option: DiscoverOption get() = DiscoverOption(name, id)
}

/**
 * The person a typed name most likely means: an exact name in [department] (TMDb's
 * `Acting` / `Directing`), else the most popular one in it, else the most popular overall
 * (the server keeps TMDb's popularity order).
 */
fun List<PersonMatch>.best(name: String, department: String): PersonMatch? {
    val wanted = filter { it.department == department }
    return wanted.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } ?: wanted.firstOrNull() ?: firstOrNull()
}

@Immutable
data class DiscoverPage(val items: List<DiscoverItem>, val totalPages: Int)

@Immutable
data class CastMember(val id: String, val name: String, val character: String, val profilePath: String)

@Immutable
data class DiscoverPerson(val name: String, val biography: String, val profilePath: String, val works: List<DiscoverItem>)

enum class DiscoverSort(val label: String, val key: String) {
    PopularDesc("热度降序", "popularity.desc"),
    PopularAsc("热度升序", "popularity.asc"),
    DateDesc("上映日期降序", "date.desc"),
    DateAsc("上映日期升序", "date.asc"),
    VoteDesc("评分降序", "vote_average.desc"),
    VoteAsc("评分升序", "vote_average.asc");

    fun wire(kind: DiscoverKind): String = when (key) {
        "date.desc" -> if (kind == DiscoverKind.Movie) "primary_release_date.desc" else "first_air_date.desc"
        "date.asc" -> if (kind == DiscoverKind.Movie) "primary_release_date.asc" else "first_air_date.asc"
        else -> key
    }
}

/** `GET D/options?media_type=` vocabularies: each a label/value list (ratings carry an index). */
@Immutable
data class DiscoverOptions(
    val genres: List<DiscoverOption> = emptyList(),
    val countries: List<DiscoverOption> = emptyList(),
    val languages: List<DiscoverOption> = emptyList(),
    val keywords: List<DiscoverOption> = emptyList(),
    val studios: List<DiscoverOption> = emptyList(),
    val ratings: List<DiscoverOption> = emptyList(),
) {
    companion object {
        fun from(raw: JsonObject) = DiscoverOptions(
            genres = options(raw, "genres"),
            countries = options(raw, "countries"),
            languages = options(raw, "languages"),
            keywords = options(raw, "keywords"),
            studios = options(raw, "studios"),
            ratings = options(raw, "ratings"),
        )
    }
}

/**
 * Browse filters → `POST D/query/{kind}` body, field for field what the Web 影视探索 panel sends:
 * genres included or excluded, countries joined by `|`, one original language, a free year
 * range, minimum score, keywords / studios by label, actors / directors by TMDb person id,
 * content rating by index, hide titles already in the library.
 */
@Immutable
data class DiscoverFilter(
    val query: String = "",
    val sort: DiscoverSort = DiscoverSort.PopularDesc,
    val genres: Set<String> = emptySet(),
    val excludeGenres: Boolean = false,
    val rating: String? = null,
    val countries: Set<String> = emptySet(),
    val language: String? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val studios: Set<String> = emptySet(),
    val keywords: Set<String> = emptySet(),
    /** label = name, value = TMDb person id. */
    val actors: List<DiscoverOption> = emptyList(),
    val directors: List<DiscoverOption> = emptyList(),
    val minVote: Float? = null,
    val hideInLibrary: Boolean = false,
) {
    val active: Int
        get() = listOf(
            sort != DiscoverSort.PopularDesc, genres.isNotEmpty(), rating != null, countries.isNotEmpty(), language != null,
            yearFrom != null || yearTo != null, studios.isNotEmpty(), keywords.isNotEmpty(), actors.isNotEmpty(),
            directors.isNotEmpty(), minVote != null, hideInLibrary,
        ).count { it }

    fun body(kind: DiscoverKind, page: Int): JsonObject = buildJsonObject {
        put("page", page)
        put("query", query.trim())
        put("sort_by", sort.wire(kind))
        put("hide_in_library", hideInLibrary)
        if (genres.isNotEmpty()) put(if (excludeGenres) "without_genres" else "with_genres", genres.joinToString(","))
        if (countries.isNotEmpty()) put("with_origin_country", countries.joinToString("|"))
        language?.let { put("with_original_language", it) }
        yearFrom?.let { put("release_date_gte", "$it-01-01") }
        yearTo?.let { put("release_date_lte", "$it-12-31") }
        minVote?.let { put("vote_average_gte", it.toDouble()) }
        rating?.toIntOrNull()?.let { put("rating", it) }
        putJsonArray("keywords") { keywords.forEach { add(it) } }
        putJsonArray("studios") { studios.forEach { add(it) } }
        putJsonArray("actors") { actors.forEach { add(it.value) } }
        putJsonArray("directors") { directors.forEach { add(it.value) } }
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
