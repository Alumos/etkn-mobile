package xyz.etkn.mobile.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.etkn.mobile.AppGraph
import xyz.etkn.mobile.data.api.EtknApi
import xyz.etkn.mobile.data.api.LibraryQuery
import xyz.etkn.mobile.data.api.SortKey
import xyz.etkn.mobile.data.model.Item
import xyz.etkn.mobile.data.model.ItemsPage
import xyz.etkn.mobile.data.net.ResponseCache
import xyz.etkn.mobile.ui.common.Load
import xyz.etkn.mobile.ui.common.Paged
import xyz.etkn.mobile.ui.common.userMessage

class LibrariesViewModel(private val graph: AppGraph) : ViewModel() {
    private val api = graph.api
    private val _state = MutableStateFlow<Load<List<Item>>>(Load.Loading)
    val state: StateFlow<Load<List<Item>>> = _state.asStateFlow()

    init {
        // Last session's list first; the network answer replaces it quietly.
        viewModelScope.launch {
            val cached = runCatching { withContext(graph.cacheUse(ResponseCache.Mode.Read)) { api.views() } }.getOrNull()
            if (!cached.isNullOrEmpty() && _state.value !is Load.Ready) _state.value = Load.Ready(cached)
        }
        load()
    }

    fun load() {
        if (_state.value !is Load.Ready) _state.value = Load.Loading
        viewModelScope.launch {
            val result = runCatching { withContext(graph.cacheUse(ResponseCache.Mode.Store)) { api.views() } }
            result.onSuccess { _state.value = Load.Ready(it) }
                .onFailure { if (_state.value !is Load.Ready) _state.value = Load.Failed(it.userMessage()) }
        }
    }
}

/**
 * Generic paged grid. [fetch] receives the start index and page size; StartIndex advances by
 * rows actually returned, stopping at TotalRecordCount or an empty page.
 */
abstract class PagedViewModel : ViewModel() {
    protected val _paged = MutableStateFlow(Paged<Item>())
    val paged: StateFlow<Paged<Item>> = _paged.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    protected open val pageSize = 60

    protected abstract suspend fun fetch(start: Int, limit: Int): ItemsPage

    fun reset() {
        job?.cancel()
        generation++
        _paged.value = Paged()
        loadMore()
    }

    fun retry() {
        _paged.update { it.copy(error = null) }
        loadMore()
    }

    fun loadMore() {
        val current = _paged.value
        if (!current.canLoadMore) return
        val ticket = generation
        _paged.value = current.copy(loading = true)
        job = viewModelScope.launch {
            try {
                val start = current.items.size
                val page = fetch(start, pageSize)
                if (ticket != generation) return@launch
                val seen = current.items.mapTo(HashSet()) { it.id }
                val merged = current.items + page.items.filter { seen.add(it.id) }
                _paged.value = Paged(
                    items = merged,
                    total = page.total,
                    loading = false,
                    endReached = page.items.isEmpty() || merged.size >= page.total,
                )
            } catch (error: Exception) {
                if (ticket != generation) return@launch
                _paged.update { it.copy(loading = false, error = error.userMessage()) }
            }
        }
    }

    fun replace(item: Item) {
        _paged.update { p -> p.copy(items = p.items.map { if (it.id == item.id) item else it }) }
    }

    /** Drops deleted rows and re-reads changed ones in place, keeping the scroll position. */
    protected fun followChanges(graph: AppGraph) {
        viewModelScope.launch {
            graph.itemChanges.collect { change ->
                if (_paged.value.items.none { it.id == change.id }) return@collect
                if (change.deleted) {
                    _paged.update { p -> p.copy(items = p.items.filterNot { it.id == change.id }, total = if (p.total > 0) p.total - 1 else p.total) }
                } else {
                    runCatching { graph.api.item(change.id, EtknApi.LIST_FIELDS) }.onSuccess(::replace)
                }
            }
        }
    }
}

class LibraryViewModel(graph: AppGraph, private val libraryId: String, private val collectionType: String?) : PagedViewModel() {
    private val api = graph.api
    private val _query = MutableStateFlow(
        LibraryQuery(
            parentId = libraryId,
            includeTypes = EtknApi.includeTypesFor(collectionType),
            sort = if (collectionType == "boxsets") SortKey.SortName else SortKey.DateCreated,
            descending = collectionType != "boxsets",
        ),
    )
    val query: StateFlow<LibraryQuery> = _query.asStateFlow()

    /** Groups and plain folders list their direct children only (no recursion, no type filter). */
    val isFolderView = collectionType == "group" || collectionType == "folder"

    init {
        reset()
        followChanges(graph)
    }

    override suspend fun fetch(start: Int, limit: Int): ItemsPage {
        if (isFolderView) {
            if (start > 0) return ItemsPage(emptyList(), start)
            val children = api.children(libraryId)
            return ItemsPage(children, children.size)
        }
        return api.libraryItems(_query.value, start, limit)
    }

    fun setSort(sort: SortKey) {
        _query.update { if (it.sort == sort) it.copy(descending = !it.descending) else it.copy(sort = sort, descending = sort != SortKey.SortName) }
        reset()
    }

    fun toggleFavorites() {
        _query.update { it.copy(favoritesOnly = !it.favoritesOnly) }
        reset()
    }
}

class FavoritesViewModel(graph: AppGraph) : PagedViewModel() {
    private val api = graph.api
    init {
        reset()
        followChanges(graph)
    }
    override suspend fun fetch(start: Int, limit: Int): ItemsPage = api.favorites(start, limit)
}
