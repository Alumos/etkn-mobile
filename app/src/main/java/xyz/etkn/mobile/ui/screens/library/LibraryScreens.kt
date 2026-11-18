package xyz.etkn.mobile.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.etkn.mobile.data.api.SortKey
import xyz.etkn.mobile.data.model.Item
import xyz.etkn.mobile.ui.common.Load
import xyz.etkn.mobile.ui.common.Paged
import xyz.etkn.mobile.ui.common.graphViewModel
import xyz.etkn.mobile.ui.common.screenPadding
import xyz.etkn.mobile.ui.components.ErrorState
import xyz.etkn.mobile.ui.components.InlineLoading
import xyz.etkn.mobile.ui.components.LibraryCard
import xyz.etkn.mobile.ui.components.LoadingState
import xyz.etkn.mobile.ui.components.MessageState
import xyz.etkn.mobile.ui.components.PosterCard
import xyz.etkn.mobile.ui.nav.LibraryRoute
import xyz.etkn.mobile.ui.nav.LocalBottomInset
import xyz.etkn.mobile.ui.nav.LocalNav
import xyz.etkn.mobile.ui.theme.Etkn

@Composable
fun LibrariesScreen() {
    val vm = graphViewModel { LibrariesViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize()) {
        LargeTitle("媒体库")
        when (val s = state) {
            Load.Loading -> LoadingState()
            is Load.Failed -> ErrorState(s.message, vm::load)
            is Load.Ready -> LazyVerticalGrid(
                columns = GridCells.Adaptive(168.dp),
                contentPadding = PaddingValues(start = Etkn.space.screen, end = Etkn.space.screen, top = 8.dp, bottom = LocalBottomInset.current),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(s.value, key = { it.id }) { library ->
                    LibraryCard(library, onClick = { nav.openLibrary(library) }, modifier = Modifier.fillMaxWidth(), width = 400.dp)
                }
            }
        }
    }
}

@Composable
fun LargeTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.headlineLarge,
        modifier = Modifier.padding(screenPadding(top = 16.dp, horizontal = Etkn.space.screen)).padding(bottom = 0.dp),
    )
}

@Composable
fun LibraryScreen(route: LibraryRoute) {
    val vm = graphViewModel(key = route.id) { LibraryViewModel(it, route.id, route.collectionType) }
    val paged by vm.paged.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    PosterGridScaffold(
        title = route.name,
        paged = paged,
        onBack = nav::back,
        onLoadMore = vm::loadMore,
        onRetry = vm::retry,
        onOpen = nav::open,
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        header = if (vm.isFolderView) null else {
            {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Etkn.space.screen, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SortMenu(query.sort, query.descending, vm::setSort)
                    FilterChip(
                        selected = query.favoritesOnly,
                        onClick = vm::toggleFavorites,
                        label = { Text("收藏") },
                        leadingIcon = { Icon(if (query.favoritesOnly) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, Modifier.size(18.dp)) },
                        shape = RoundedCornerShape(12.dp),
                    )
                    if (paged.total >= 0) {
                        Text("${paged.total} 项", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
    )
}

@Composable
private fun SortMenu(sort: SortKey, descending: Boolean, onSelect: (SortKey) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 14.dp), modifier = Modifier.height(32.dp)) {
            Icon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(sort.label, style = MaterialTheme.typography.labelLarge)
            if (sort != SortKey.Random) {
                Icon(if (descending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward, null, Modifier.padding(start = 4.dp).size(16.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(16.dp)) {
            SortKey.entries.forEach { key ->
                DropdownMenuItem(
                    text = { Text(key.label, color = if (key == sort) MaterialTheme.colorScheme.primary else Color.Unspecified) },
                    trailingIcon = if (key == sort && key != SortKey.Random) {
                        { Icon(if (descending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward, null) }
                    } else null,
                    onClick = { open = false; onSelect(key) },
                )
            }
        }
    }
}

@Composable
fun FavoritesScreen() {
    val vm = graphViewModel { FavoritesViewModel(it) }
    val paged by vm.paged.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    PosterGridScaffold("我的收藏", paged, nav::back, vm::loadMore, vm::retry, nav::open)
}

/** Shared top bar + adaptive poster grid with infinite scroll. */
@Composable
fun PosterGridScaffold(
    title: String,
    paged: Paged<Item>,
    onBack: (() -> Unit)?,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (Item) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    grid: LazyGridState = rememberLazyGridState(),
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
        )
        header?.invoke()
        when {
            paged.items.isEmpty() && paged.loading -> LoadingState()
            paged.items.isEmpty() && paged.error != null -> ErrorState(paged.error, onRetry)
            paged.items.isEmpty() && paged.endReached -> MessageState("这里还没有内容")
            else -> PosterGrid(paged, grid, onLoadMore, onRetry, onOpen)
        }
    }
}

@Composable
fun PosterGrid(
    paged: Paged<Item>,
    grid: LazyGridState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (Item) -> Unit,
) {
    val shouldLoad by remember(paged) {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            paged.canLoadMore && last >= paged.items.size - 18
        }
    }
    LaunchedEffect(grid, paged) {
        snapshotFlow { shouldLoad }.collect { if (it) onLoadMore() }
    }
    LazyVerticalGrid(
        state = grid,
        columns = GridCells.Adaptive(108.dp),
        contentPadding = PaddingValues(start = Etkn.space.screen, end = Etkn.space.screen, top = 8.dp, bottom = LocalBottomInset.current),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(paged.items, key = { it.id }, contentType = { "poster" }) { item ->
            if (item.type == "CollectionFolder" || item.collectionType != null && item.type != "BoxSet") {
                LibraryCard(item, onClick = { onOpen(item) }, modifier = Modifier.fillMaxWidth(), width = 300.dp)
            } else {
                PosterCard(item, onClick = { onOpen(item) }, width = null)
            }
        }
        if (paged.loading) item(span = { GridItemSpan(maxLineSpan) }) { InlineLoading() }
        if (paged.error != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    FilledTonalButton(onClick = onRetry) { Text("加载失败，重试") }
                }
            }
        }
    }
}
