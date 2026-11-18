package xyz.etkn.mobile.ui.screens.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import xyz.etkn.mobile.AppGraph
import xyz.etkn.mobile.data.model.ItemsPage
import xyz.etkn.mobile.ui.common.graphViewModel
import xyz.etkn.mobile.ui.components.LoadingState
import xyz.etkn.mobile.ui.components.MessageState
import xyz.etkn.mobile.ui.nav.LocalNav
import xyz.etkn.mobile.ui.screens.library.PagedViewModel
import xyz.etkn.mobile.ui.screens.library.PosterGrid
import xyz.etkn.mobile.ui.theme.Etkn

class SearchViewModel(graph: AppGraph) : PagedViewModel() {
    private val api = graph.api
    private val term = MutableStateFlow("")
    var current = ""
        private set

    init {
        viewModelScope.launch {
            term.debounce(350).distinctUntilChanged().collect { value ->
                current = value.trim()
                if (current.isNotEmpty()) reset() else _paged.value = xyz.etkn.mobile.ui.common.Paged(endReached = true)
            }
        }
    }

    fun onQuery(value: String) {
        term.value = value
    }

    override suspend fun fetch(start: Int, limit: Int): ItemsPage = api.search(current, start, limit)
}

@Composable
fun SearchScreen() {
    val vm = graphViewModel { SearchViewModel(it) }
    val paged by vm.paged.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val text = rememberTextFieldState()
    val searchState = rememberSearchBarState()
    val keyboard = LocalSoftwareKeyboardController.current
    val grid = rememberLazyGridState()

    LaunchedEffect(text) {
        snapshotFlow { text.text.toString() }.collect(vm::onQuery)
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Etkn.space.screen, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            SearchBar(
                state = searchState,
                inputField = {
                    SearchBarDefaults.InputField(
                        textFieldState = text,
                        searchBarState = searchState,
                        onSearch = { keyboard?.hide() },
                        placeholder = { Text("搜索电影、剧集") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (text.text.isNotEmpty()) {
                                IconButton(onClick = { text.clearText() }) { Icon(Icons.Rounded.Close, "清除") }
                            }
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        when {
            vm.current.isEmpty() && text.text.isEmpty() -> MessageState("输入片名开始搜索", icon = Icons.Rounded.Search)
            paged.items.isEmpty() && paged.loading -> LoadingState()
            paged.items.isEmpty() && paged.endReached -> MessageState("没有找到相关影视")
            else -> PosterGrid(paged, grid, vm::loadMore, vm::retry) { keyboard?.hide(); nav.open(it) }
        }
    }
}
