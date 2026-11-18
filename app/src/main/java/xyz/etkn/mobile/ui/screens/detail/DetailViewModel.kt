package xyz.etkn.mobile.ui.screens.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.etkn.mobile.AppGraph
import xyz.etkn.mobile.data.api.EtknApi
import xyz.etkn.mobile.data.model.Item
import xyz.etkn.mobile.ui.common.Load
import xyz.etkn.mobile.ui.common.userMessage

@Immutable
data class EpisodesState(
    val seasonId: String? = null,
    val items: List<Item> = emptyList(),
    val next: Int = 0,
    val total: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
) {
    val hasMore get() = items.size < total && !loading && error == null
}

@Immutable
data class DetailData(
    val item: Item,
    val seasons: List<Item> = emptyList(),
    val target: Item? = null,
    val similar: List<Item> = emptyList(),
    val selectedSource: Int = 0,
)

class DetailViewModel(graph: AppGraph, private val id: String) : ViewModel() {
    private val api = graph.api
    private val _state = MutableStateFlow<Load<DetailData>>(Load.Loading)
    val state: StateFlow<Load<DetailData>> = _state.asStateFlow()
    private val _episodes = MutableStateFlow(EpisodesState())
    val episodes: StateFlow<EpisodesState> = _episodes.asStateFlow()
    private var episodesJob: Job? = null
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
                        _episodes.value.items.any { it.id == change.id } -> load()
                }
            }
        }
    }

    fun load() {
        if (_state.value !is Load.Ready) _state.value = Load.Loading
        viewModelScope.launch {
            try {
                val data = coroutineScope {
                    val base = api.item(id)
                    // Everything below depends only on the base item, so it all runs at once.
                    val detailed = if (base.isPlayable && base.mediaSources.isEmpty()) {
                        async { runCatching { api.item(id, EtknApi.DETAIL_FIELDS) }.getOrNull() }
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
                    val full = detailed?.await()
                    DetailData(
                        item = if (full != null) base.copy(mediaSources = full.mediaSources, chapters = full.chapters.ifEmpty { base.chapters }) else base,
                        seasons = seasons?.await().orEmpty(),
                        target = target?.await(),
                        similar = similar?.await().orEmpty(),
                    )
                }
                _state.value = Load.Ready(data)
                val seasonForEpisodes = when {
                    data.item.isSeason -> data.item.id
                    data.item.isSeries -> data.target?.seasonId ?: data.seasons.firstOrNull()?.id
                    data.item.isEpisode -> data.item.seasonId
                    else -> null
                }
                val seriesForEpisodes = if (data.item.isSeries) data.item.id else data.item.seriesId
                if (seriesForEpisodes != null && (data.item.isSeries || data.item.isSeason || data.item.isEpisode)) {
                    if (_episodes.value.seasonId != seasonForEpisodes || _episodes.value.items.isEmpty()) {
                        selectSeason(seriesForEpisodes, seasonForEpisodes, anchor = data.target?.id ?: data.item.id.takeIf { data.item.isEpisode })
                    }
                }
            } catch (error: Exception) {
                if (_state.value !is Load.Ready) _state.value = Load.Failed(error.userMessage())
            }
        }
    }

    fun selectSeason(seriesId: String, seasonId: String?, anchor: String? = null) {
        episodesJob?.cancel()
        _episodes.value = EpisodesState(seasonId = seasonId, loading = true)
        episodesJob = viewModelScope.launch {
            try {
                val page = api.episodePage(seriesId, seasonId, start = 0, limit = 60)
                _episodes.value = EpisodesState(seasonId, page.items, page.nextStartIndex, page.total)
            } catch (error: Exception) {
                _episodes.value = EpisodesState(seasonId, error = error.userMessage())
            }
        }
    }

    fun moreEpisodes(seriesId: String) {
        val current = _episodes.value
        if (!current.hasMore) return
        _episodes.value = current.copy(loading = true)
        episodesJob = viewModelScope.launch {
            try {
                val page = api.episodePage(seriesId, current.seasonId, start = current.next, limit = 60)
                val seen = current.items.mapTo(HashSet()) { it.id }
                _episodes.value = current.copy(
                    items = current.items + page.items.filter { seen.add(it.id) },
                    next = page.nextStartIndex,
                    total = if (page.items.isEmpty()) current.items.size else page.total,
                    loading = false,
                )
            } catch (error: Exception) {
                _episodes.value = current.copy(loading = false, error = error.userMessage())
            }
        }
    }

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
            .also { if (item.isSeries || item.isSeason) refreshEpisodes() }
    }

    fun toggleEpisodePlayed(episode: Item) {
        viewModelScope.launch {
            val target = !episode.userData.played
            runCatching { api.setPlayed(episode.id, target) }.onSuccess {
                _episodes.update { s ->
                    s.copy(items = s.items.map { if (it.id == episode.id) it.copy(userData = it.userData.copy(played = target, playbackPositionTicks = 0)) else it })
                }
            }
        }
    }

    private fun refreshEpisodes() {
        val data = (_state.value as? Load.Ready)?.value ?: return
        val series = if (data.item.isSeries) data.item.id else data.item.seriesId ?: return
        selectSeason(series, _episodes.value.seasonId)
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
