package xyz.etkn.mobile.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.rememberHazeState
import xyz.etkn.mobile.data.discover.DiscoverItem
import xyz.etkn.mobile.data.discover.DiscoverKind
import xyz.etkn.mobile.ui.common.Load
import xyz.etkn.mobile.ui.common.LocalWindowLayout
import xyz.etkn.mobile.ui.common.graphViewModel
import xyz.etkn.mobile.ui.components.LoadingState
import xyz.etkn.mobile.ui.components.MessageState
import xyz.etkn.mobile.ui.components.SectionHeader
import xyz.etkn.mobile.ui.components.glass
import xyz.etkn.mobile.ui.components.glassSource
import xyz.etkn.mobile.ui.nav.DiscoverDetailRoute
import xyz.etkn.mobile.ui.nav.LocalBottomInset
import xyz.etkn.mobile.ui.nav.LocalNav
import xyz.etkn.mobile.ui.screens.discover.DiscoverCard
import xyz.etkn.mobile.ui.screens.library.PosterGrid
import xyz.etkn.mobile.ui.theme.Etkn

@Composable
fun SearchScreen() {
    val vm = graphViewModel { SearchViewModel(it) }
    val paged by vm.paged.collectAsStateWithLifecycle()
    val discover by vm.discover.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val text = rememberTextFieldState()
    // Standalone field: SearchBar(state, …) hands focus to an expanded overlay we don't use,
    // which dropped the keyboard the moment the field was tapped.
    val barState = rememberSearchBarState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val grid = rememberLazyGridState()
    val haze = rememberHazeState()
    val shape = RoundedCornerShape(28.dp)
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp

    LaunchedEffect(text) {
        snapshotFlow { text.text.toString() }.collect(vm::onQuery)
    }
    LaunchedEffect(Unit) { if (text.text.isEmpty()) focus.requestFocus() }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().glassSource(haze)) {
            when {
                vm.current.isEmpty() && text.text.isEmpty() -> MessageState("输入片名开始搜索", icon = Icons.Rounded.Search)
                paged.items.isEmpty() && paged.loading -> LoadingState()
                paged.items.isEmpty() && paged.endReached -> discover?.let { results ->
                    DiscoverFallback(results, top, vm::retryDiscover) { item ->
                        keyboard?.hide()
                        nav.to(DiscoverDetailRoute(item.id, item.kind.wire, item.title))
                    }
                } ?: MessageState("没有找到相关影视")
                else -> PosterGrid(paged, grid, vm::loadMore, vm::retry, top = top) { keyboard?.hide(); nav.open(it) }
            }
        }
        Box(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Etkn.space.screen, vertical = 8.dp)
                .glass(shape, elevation = 8.dp, state = haze),
        ) {
            SearchBarDefaults.InputField(
                textFieldState = text,
                searchBarState = barState,
                onSearch = { keyboard?.hide() },
                placeholder = { Text("搜索电影、剧集") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (text.text.isNotEmpty()) {
                        IconButton(onClick = { text.clearText(); focus.requestFocus() }) { Icon(Icons.Rounded.Close, "清除") }
                    }
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
    }
}

/** Library came back empty: TMDb movies and series side by side, each as its own shelf. */
@Composable
private fun DiscoverFallback(
    results: Map<DiscoverKind, Load<List<DiscoverItem>>>,
    top: Dp,
    onRetry: () -> Unit,
    onOpen: (DiscoverItem) -> Unit,
) {
    val allEmpty = results.values.all { it is Load.Ready && it.value.isEmpty() }
    if (allEmpty) {
        MessageState("没有找到相关影视")
        return
    }
    val width = if (LocalWindowLayout.current.wide) Etkn.space.posterWidthWide else Etkn.space.posterWidth
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top, bottom = LocalBottomInset.current + 16.dp)) {
        DiscoverKind.entries.forEach { kind ->
            val load = results[kind] ?: return@forEach
            if (load is Load.Ready && load.value.isEmpty()) return@forEach
            item(key = "head-${kind.wire}", contentType = "head") { SectionHeader("探索 · ${kind.label}") }
            item(key = "row-${kind.wire}", contentType = "row") {
                when (load) {
                    Load.Loading -> Box(Modifier.fillMaxWidth().height(width * 1.6f), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    is Load.Failed -> Row(
                        Modifier.fillMaxWidth().padding(horizontal = Etkn.space.screen, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(load.message, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        TextButton(onClick = onRetry) { Text("重试") }
                    }
                    is Load.Ready -> LazyRow(
                        contentPadding = PaddingValues(horizontal = Etkn.space.screen),
                        horizontalArrangement = Arrangement.spacedBy(Etkn.space.item),
                        modifier = Modifier.padding(top = 4.dp, bottom = Etkn.space.section),
                    ) {
                        items(load.value, key = { it.id }) { item -> DiscoverCard(item, onClick = { onOpen(item) }, width = width) }
                    }
                }
            }
        }
    }
}
