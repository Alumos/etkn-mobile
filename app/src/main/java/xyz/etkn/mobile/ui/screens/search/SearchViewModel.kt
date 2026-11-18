package xyz.etkn.mobile.ui.screens.search

import androidx.compose.foundation.lazy.items
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.etkn.mobile.AppGraph
import xyz.etkn.mobile.data.discover.DiscoverFilter
import xyz.etkn.mobile.data.discover.DiscoverItem
import xyz.etkn.mobile.data.discover.DiscoverKind
import xyz.etkn.mobile.data.model.ItemsPage
import xyz.etkn.mobile.ui.common.Load
import xyz.etkn.mobile.ui.common.Paged
import xyz.etkn.mobile.ui.common.userMessage
import xyz.etkn.mobile.ui.screens.library.PagedViewModel

/**
 * Library search first; when the library has nothing for the term and the account may discover,
 * TMDb is searched for movies and series at the same time (no type switch needed).
 */
class SearchViewModel(private val graph: AppGraph) : PagedViewModel() {
    private val api = graph.api
    private val term = MutableStateFlow("")
    var current = ""
        private set
    private val _discover = MutableStateFlow<Map<DiscoverKind, Load<List<DiscoverItem>>>?>(null)
    /** Null while the library answers (or has answers); otherwise one result per kind. */
    val discover: StateFlow<Map<DiscoverKind, Load<List<DiscoverItem>>>?> = _discover.asStateFlow()
    private var discoverJob: Job? = null
    private var discoverTerm = ""

    init {
        viewModelScope.launch {
            term.debounce(350).distinctUntilChanged().collect { value ->
                current = value.trim()
                discoverJob?.cancel()
                discoverTerm = ""
                _discover.value = null
                if (current.isNotEmpty()) reset() else _paged.value = Paged(endReached = true)
            }
        }
        viewModelScope.launch {
            combine(paged, graph.discover.allowed) { page, allowed -> page to allowed }.collect { (page, allowed) ->
                val empty = page.items.isEmpty() && page.endReached && page.error == null
                if (allowed && empty && current.isNotEmpty() && discoverTerm != current) searchDiscover(current)
            }
        }
    }

    private fun searchDiscover(query: String) {
        discoverTerm = query
        _discover.value = DiscoverKind.entries.associateWith { Load.Loading }
        discoverJob = viewModelScope.launch {
            DiscoverKind.entries.forEach { kind ->
                launch {
                    val result = runCatching { api.discoverQuery(kind, DiscoverFilter(query = query).body(kind, 1)).items }
                        .onSuccess { items -> items.forEach(graph.discover::put) }
                        .fold({ Load.Ready(it) }, { Load.Failed(it.userMessage()) })
                    _discover.update { it?.plus(kind to result) }
                }
            }
        }
    }

    fun retryDiscover() {
        discoverTerm = ""
        if (current.isNotEmpty()) searchDiscover(current)
    }

    fun onQuery(value: String) {
        term.value = value
    }

    override suspend fun fetch(start: Int, limit: Int): ItemsPage = api.search(current, start, limit)
}
