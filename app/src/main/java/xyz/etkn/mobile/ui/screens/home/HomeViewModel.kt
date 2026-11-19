package xyz.etkn.mobile.ui.screens.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.etkn.mobile.AppGraph
import xyz.etkn.mobile.data.model.Item
import xyz.etkn.mobile.data.net.ResponseCache
import xyz.etkn.mobile.ui.common.Load
import xyz.etkn.mobile.ui.common.userMessage
import java.util.UUID

@Immutable
data class LatestShelf(val library: Item, val items: List<Item>)

@Immutable
data class HomeData(
    val hero: List<Item> = emptyList(),
    val resume: List<Item> = emptyList(),
    val libraries: List<Item> = emptyList(),
    val latest: List<LatestShelf> = emptyList(),
    val musicLatest: Pair<Item, List<Item>>? = null,
)

class HomeViewModel(private val graph: AppGraph) : ViewModel() {
    private val api = graph.api
    private val _state = MutableStateFlow<Load<HomeData>>(Load.Loading)
    val state: StateFlow<Load<HomeData>> = _state.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private var heroJob: Job? = null
    private var lastLoad = 0L

    init {
        showCached()
        load()
        // Changes from the item menu: reload the shelves quietly (the hero is kept).
        viewModelScope.launch { graph.itemChanges.collect { load() } }
    }

    fun refresh() = load(manual = true)

    /** Called when the screen resumes; reloads only the volatile shelves after a minute. */
    fun onResume() {
        if (System.currentTimeMillis() - lastLoad > 60_000) refreshResume()
    }

    /**
     * Last session's shelves from disk, painted while the network load runs; the hero starts
     * from cached titles with artwork until the recommendation stream answers.
     */
    private fun showCached() {
        viewModelScope.launch {
            val cached = runCatching { withContext(graph.cacheUse(ResponseCache.Mode.Read)) { fetch() } }.getOrNull() ?: return@launch
            if (_state.value is Load.Ready || cached.libraries.isEmpty()) return@launch
            _state.value = Load.Ready(cached.copy(hero = fallbackHero(cached)))
        }
    }

    private fun load(manual: Boolean = false) {
        if (manual) _refreshing.value = true
        viewModelScope.launch {
            try {
                val data = withContext(graph.cacheUse(ResponseCache.Mode.Store)) { fetch() }
                    .let { fresh -> fresh.copy(hero = (_state.value as? Load.Ready)?.value?.hero.orEmpty()) }
                val fromCache = (_state.value as? Load.Ready) != null && lastLoad == 0L
                lastLoad = System.currentTimeMillis()
                _state.value = Load.Ready(data)
                // A shown hero (cached or previous) is swapped in one step, never shrunk to one card.
                if (data.hero.isEmpty()) loadHero(data) else if (manual || fromCache) loadHero(data, quiet = true)
            } catch (error: Exception) {
                if (_state.value !is Load.Ready) _state.value = Load.Failed(error.userMessage())
            } finally {
                _refreshing.value = false
            }
        }
    }

    private suspend fun fetch(): HomeData = coroutineScope {
        val viewsJob = async { api.views() }
        val resumeJob = async { runCatching { api.resume(20) }.getOrDefault(emptyList()) }
        val views = viewsJob.await()
        val videoLibraries = views.filter { it.collectionType !in NON_LATEST }
        val shelves = videoLibraries.map { library ->
            async {
                val types = when (library.collectionType) {
                    "movies" -> "Movie"
                    "tvshows" -> "Series"
                    else -> null
                }
                LatestShelf(library, runCatching { api.latest(library.id, types, 20) }.getOrDefault(emptyList()))
            }
        }
        val music = views.firstOrNull { it.collectionType == "music" }?.let { library ->
            async {
                runCatching {
                    library to api.musicList(library.id, "albums", "", 0, 20, newest = true).items
                }.getOrNull()
            }
        }
        HomeData(
            resume = resumeJob.await(),
            libraries = views,
            latest = shelves.awaitAll().filter { it.items.isNotEmpty() },
            musicLatest = music?.await()?.takeIf { it.second.isNotEmpty() },
        )
    }

    private fun refreshResume() {
        viewModelScope.launch {
            val resume = runCatching { api.resume(20) }.getOrNull() ?: return@launch
            lastLoad = System.currentTimeMillis()
            _state.update { current -> if (current is Load.Ready) Load.Ready(current.value.copy(resume = resume)) else current }
        }
    }

    /**
     * The personal-recommendation stream is paged one item at a time (as on TV). Items are
     * appended as they arrive so the hero paints after the first response; entries without
     * backdrop artwork are skipped.
     */
    private fun loadHero(data: HomeData, quiet: Boolean = false) {
        heroJob?.cancel()
        heroJob = viewModelScope.launch {
            val seed = UUID.randomUUID().toString()
            var cursor: String? = null
            val collected = ArrayList<Item>()
            var requests = 0
            try {
                while (collected.size < HERO_SIZE && requests < HERO_SIZE * 2) {
                    requests++
                    val page = api.recommendations(seed, cursor)
                    page.items.firstOrNull()?.let { item ->
                        if (hasBackdrop(item) && collected.none { it.id == item.id }) {
                            collected += item
                            if (!quiet) publishHero(collected.toList())
                        }
                    }
                    cursor = page.nextCursor ?: break
                }
            } catch (_: Exception) {
                // Recommendation stream unavailable: fall back to latest titles with backdrops.
            }
            if (quiet && collected.isNotEmpty()) publishHero(collected.toList())
            if (collected.isEmpty() && !quiet) {
                val fallback = fallbackHero(data)
                if (fallback.isNotEmpty()) {
                    val detailed = runCatching { coroutineScope { fallback.map { async { api.item(it.id, "Overview") } }.awaitAll() } }.getOrDefault(fallback)
                    publishHero(detailed)
                }
            }
        }
    }

    private fun publishHero(items: List<Item>) {
        _state.update { current -> if (current is Load.Ready) Load.Ready(current.value.copy(hero = items)) else current }
    }

    private fun fallbackHero(data: HomeData) =
        data.latest.flatMap { it.items }.filter(::hasBackdrop).distinctBy { it.id }.take(HERO_SIZE)

    private fun hasBackdrop(item: Item) = item.backdropTags.isNotEmpty() || item.parentBackdropTags.isNotEmpty()

    companion object {
        private const val HERO_SIZE = 6
        private val NON_LATEST = setOf("music", "boxsets", "playlists", "livetv", "folders")
    }
}
