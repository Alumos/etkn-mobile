package xyz.etkn.mobile.ui.screens.detail

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.etkn.mobile.data.api.EtknApi
import xyz.etkn.mobile.data.model.Item
import xyz.etkn.mobile.ui.common.userMessage

@Immutable
data class EpisodesState(
    val seasonId: String? = null,
    val items: List<Item> = emptyList(),
    val next: Int = 0,
    val total: Int = 0,
    /** Episode the list opens at (the one to play next, or the page's own episode). */
    val anchorId: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
) {
    val hasMore get() = items.size < total && !loading && error == null
    val anchorIndex: Int get() = anchorId?.let { id -> items.indexOfFirst { it.id == id } } ?: -1
}

/**
 * One season's episodes, read page by page from `Shows/{id}/EpisodePage`. Shared by the detail
 * page (horizontal row with range chips) and the full episode grid. Requests are serialized:
 * [more] and [loadThrough] only raise the target and one loop fetches until it is reached.
 */
class EpisodeList(private val api: EtknApi, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(EpisodesState())
    val state: StateFlow<EpisodesState> = _state.asStateFlow()
    private var seriesId = ""
    private var wanted = 0
    private var job: Job? = null

    /** Opens [seasonId] of [seriesId]; pages on until [anchorId] is in the list. */
    fun select(seriesId: String, seasonId: String?, anchorId: String? = null) {
        job?.cancel()
        this.seriesId = seriesId
        wanted = 0
        _state.value = EpisodesState(seasonId = seasonId, anchorId = anchorId, loading = true)
        job = scope.launch {
            try {
                var state = append(_state.value, FIRST_PAGE)
                while (anchorId != null && state.anchorIndex < 0 && state.items.size < state.total && state.items.size < ANCHOR_LIMIT) {
                    state = append(state, ANCHOR_PAGE)
                }
                _state.value = state.copy(loading = false)
            } catch (error: Exception) {
                _state.value = _state.value.copy(loading = false, error = error.userMessage())
            }
        }
    }

    /** The next page, for a list scrolled near its end. */
    fun more() = loadThrough(_state.value.items.size)

    /** Loads until position [index] (0-based) is in the list: range chips, jump to the last episode. */
    fun loadThrough(index: Int) {
        wanted = maxOf(wanted, index)
        if (job?.isActive == true || seriesId.isEmpty()) return
        job = scope.launch {
            while (true) {
                val state = _state.value
                if (state.items.size > wanted || state.items.size >= state.total || state.error != null) break
                _state.value = state.copy(loading = true)
                try {
                    _state.value = append(state, (wanted + 1 - state.items.size).coerceIn(FIRST_PAGE, MAX_PAGE)).copy(loading = false)
                } catch (error: Exception) {
                    _state.value = state.copy(loading = false, error = error.userMessage())
                }
            }
        }
    }

    fun retry() {
        val state = _state.value
        if (state.items.isEmpty()) select(seriesId, state.seasonId, state.anchorId)
        else {
            _state.value = state.copy(error = null)
            more()
        }
    }

    /** Re-reads what is loaded (progress, played flags) in one request, keeping the list on screen. */
    fun refresh() {
        val state = _state.value
        if (seriesId.isEmpty() || state.items.isEmpty() || job?.isActive == true) return
        job = scope.launch {
            runCatching { api.episodePage(seriesId, state.seasonId, start = 0, limit = state.items.size.coerceIn(FIRST_PAGE, ANCHOR_LIMIT)) }
                .onSuccess { page ->
                    _state.update { it.copy(items = page.items, next = page.nextStartIndex, total = maxOf(page.total, page.items.size)) }
                }
        }
    }

    fun contains(id: String) = _state.value.items.any { it.id == id }

    private suspend fun append(state: EpisodesState, limit: Int): EpisodesState {
        val page = api.episodePage(seriesId, state.seasonId, start = state.next, limit = limit)
        val seen = state.items.mapTo(HashSet()) { it.id }
        val fresh = page.items.filter { seen.add(it.id) }
        return state.copy(
            items = state.items + fresh,
            next = page.nextStartIndex,
            // An empty page ends the list even when the server's total says otherwise.
            total = if (fresh.isEmpty()) state.items.size else page.total,
        )
    }

    private companion object {
        const val FIRST_PAGE = 60
        const val ANCHOR_PAGE = 120
        const val MAX_PAGE = 300
        const val ANCHOR_LIMIT = 2000
    }
}
