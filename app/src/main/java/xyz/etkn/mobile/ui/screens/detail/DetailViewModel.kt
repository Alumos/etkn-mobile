package xyz.etkn.mobile.ui.screens.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.etkn.mobile.AppGraph
import xyz.etkn.mobile.data.api.EtknApi
import xyz.etkn.mobile.data.events.ServerEvent
import xyz.etkn.mobile.data.model.Item
import xyz.etkn.mobile.ui.common.Load
import xyz.etkn.mobile.ui.common.userMessage

@Immutable
data class DetailData(
    val item: Item,
    val seasons: List<Item> = emptyList(),
    val target: Item? = null,
    val similar: List<Item> = emptyList(),
    /** TMDb collections a movie belongs to. */
    val collections: List<Item> = emptyList(),
    val selectedSource: Int = 0,
)

/**
 * One detail page. On an episode's page the shelf switches between the series' episodes in place
 * ([show]): the page keeps what it shows until the next episode is loaded instead of opening a
 * new page per episode, so back still leads straight out of the series.
 */
class DetailViewModel(private val graph: AppGraph, routeId: String) : ViewModel() {
    private val shown = MutableStateFlow(routeId)
    private val id get() = shown.value
    private val api = graph.api
    private val _state = MutableStateFlow<Load<DetailData>>(Load.Loading)
    val state: StateFlow<Load<DetailData>> = _state.asStateFlow()
    val episodes = EpisodeList(api, viewModelScope)
    private var busy = false
    private val _deleted = MutableStateFlow(false)
    /** The item itself was deleted from the item menu; the page leaves. */
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            graph.itemChanges.collect { change ->
                val data = (_state.value as? Load.Ready)?.value
                when {
                    change.id == id && change.deleted -> _deleted.value = true
                    change.id == id || data?.similar?.any { it.id == change.id } == true ||
                        episodes.contains(change.id) -> load()
                }
            }
        }
    }

    /** Shows another episode of the same series on this page. */
    fun show(episodeId: String) {
        if (episodeId == id) return
        shown.value = episodeId
        load()
    }

    fun load() {
        if (_state.value !is Load.Ready) _state.value = Load.Loading
        val requested = id
        viewModelScope.launch {
            try {
                val data = coroutineScope {
                    val base = api.item(requested)
                    // Everything below depends only on the base item, so it all runs at once.
                    val detailed = if (base.isPlayable && base.mediaSources.isEmpty()) {
                        async { runCatching { api.item(requested, EtknApi.DETAIL_FIELDS) }.getOrNull() }
                    } else null
                    val seriesId = when {
                        base.isSeries -> base.id
                        base.isSeason -> base.seriesId
                        else -> null
                    }
                    val item = base
                    val seasons = seriesId?.let { sid -> async { runCatching { api.seasons(sid) }.getOrDefault(emptyList()) } }
                    val target = seriesId?.let { sid ->
                        async { runCatching { api.playbackTarget(sid, if (item.isSeason) item.id else null) }.getOrNull() }
                    }
                    val similar = if (item.isMovie || item.isSeries) async { runCatching { api.similar(item.id) }.getOrDefault(emptyList()) } else null
                    val collections = if (item.isMovie) async { runCatching { api.itemCollections(item.id) }.getOrDefault(emptyList()) } else null
                    val full = detailed?.await()
                    DetailData(
                        item = if (full != null) base.copy(mediaSources = full.mediaSources, chapters = full.chapters.ifEmpty { base.chapters }) else base,
                        seasons = seasons?.await().orEmpty(),
                        target = target?.await(),
                        similar = similar?.await().orEmpty(),
                        collections = collections?.await().orEmpty(),
                    )
                }
                // A newer episode was picked while this one loaded.
                if (requested != id) return@launch
                _state.value = Load.Ready(data)
                val seasonForEpisodes = when {
                    data.item.isSeason -> data.item.id
                    data.item.isSeries -> data.target?.seasonId ?: data.seasons.firstOrNull()?.id
                    data.item.isEpisode -> data.item.seasonId
                    else -> null
                }
                val seriesForEpisodes = if (data.item.isSeries) data.item.id else data.item.seriesId
                if (seriesForEpisodes != null && (data.item.isSeries || data.item.isSeason || data.item.isEpisode)) {
                    val shown = episodes.state.value
                    val anchor = if (data.item.isEpisode) data.item.id else data.target?.id
                    if (shown.seasonId != seasonForEpisodes || shown.items.isEmpty()) episodes.select(seriesForEpisodes, seasonForEpisodes, anchor)
                    else episodes.refresh()
                }
            } catch (error: Exception) {
                if (_state.value !is Load.Ready) _state.value = Load.Failed(error.userMessage())
            }
        }
    }

    /** Re-reads the page when the server reports changes to its rows (metadata, images, versions). */
    suspend fun followServer() = shown.collectLatest { id ->
        graph.events.stream(id).collect { if (it is ServerEvent.Items && !it.initial) load() }
    }

    fun selectSeason(seriesId: String, seasonId: String) = episodes.select(seriesId, seasonId)

    fun selectSource(index: Int) {
        _state.update { s -> if (s is Load.Ready) Load.Ready(s.value.copy(selectedSource = index)) else s }
    }

    fun toggleFavorite() = mutate { item ->
        api.setFavorite(item.id, !item.userData.isFavorite).let(item::withUserData)
            .let { if (it.userData.isFavorite == item.userData.isFavorite) it.copy(userData = it.userData.copy(isFavorite = !item.userData.isFavorite)) else it }
    }

    fun togglePlayed() = mutate { item ->
        val target = !item.userData.played
        api.setPlayed(item.id, target).let(item::withUserData)
            .let { if (it.userData.played != target) it.copy(userData = it.userData.copy(played = target)) else it }
            .also { if (item.isSeries || item.isSeason) episodes.refresh() }
    }

    private fun mutate(block: suspend (Item) -> Item) {
        val data = (_state.value as? Load.Ready)?.value ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            runCatching { block(data.item) }.onSuccess { updated ->
                _state.update { s -> if (s is Load.Ready) Load.Ready(s.value.copy(item = updated)) else s }
            }
            busy = false
        }
    }
}
