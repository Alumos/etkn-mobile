package xyz.etkn.mobile.ui.screens.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSearchBarState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.rememberHazeState
import xyz.etkn.mobile.data.discover.DiscoverEra
import xyz.etkn.mobile.data.discover.DiscoverFilter
import xyz.etkn.mobile.data.discover.DiscoverItem
import xyz.etkn.mobile.data.discover.DiscoverKind
import xyz.etkn.mobile.data.discover.DiscoverOption
import xyz.etkn.mobile.data.discover.DiscoverSort
import xyz.etkn.mobile.data.discover.DiscoverStatus
import xyz.etkn.mobile.ui.common.LocalWindowLayout
import xyz.etkn.mobile.ui.common.Paged
import xyz.etkn.mobile.ui.common.graphViewModel
import xyz.etkn.mobile.ui.components.Artwork
import xyz.etkn.mobile.ui.components.CardShape
import xyz.etkn.mobile.ui.components.ErrorState
import xyz.etkn.mobile.ui.components.InlineLoading
import xyz.etkn.mobile.ui.components.LoadingState
import xyz.etkn.mobile.ui.components.MessageState
import xyz.etkn.mobile.ui.components.SmallCardShape
import xyz.etkn.mobile.ui.components.formatRating
import xyz.etkn.mobile.ui.components.glass
import xyz.etkn.mobile.ui.components.glassSource
import xyz.etkn.mobile.ui.components.images
import xyz.etkn.mobile.ui.nav.DiscoverDetailRoute
import xyz.etkn.mobile.ui.nav.LocalBottomInset
import xyz.etkn.mobile.ui.nav.LocalNav
import xyz.etkn.mobile.ui.theme.Etkn

@Composable
fun DiscoverScreen() {
    val vm = graphViewModel { DiscoverViewModel(it) }
    val kind by vm.kind.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val paged by vm.paged.collectAsStateWithLifecycle()
    val genres by vm.genres.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val haze = rememberHazeState()
    val grid = rememberLazyGridState()
    val density = LocalDensity.current
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var headerHeight by remember { mutableStateOf(64.dp) }
    var searching by remember { mutableStateOf(filter.query.isNotEmpty()) }
    var filtering by remember { mutableStateOf(false) }
    val text = rememberTextFieldState(filter.query)
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(kind) { grid.scrollToItem(0) }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().glassSource(haze)) {
            when {
                paged.items.isEmpty() && paged.loading -> LoadingState()
                paged.items.isEmpty() && paged.error != null -> ErrorState(paged.error!!, vm::retry)
                paged.items.isEmpty() && paged.endReached -> MessageState("暂无匹配结果，可调整筛选或搜索词", icon = Icons.Rounded.Search)
                else -> DiscoverGrid(paged, grid, top = status + headerHeight + 12.dp, onLoadMore = vm::loadMore, onRetry = vm::retry) { item ->
                    nav.to(DiscoverDetailRoute(item.id, item.kind.wire, item.title))
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .glass(RoundedCornerShape(26.dp), elevation = 8.dp, state = haze)
                .onSizeChanged { headerHeight = with(density) { it.height.toDp() } },
        ) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    DiscoverKind.entries.forEachIndexed { index, option ->
                        ToggleButton(
                            checked = option == kind,
                            onCheckedChange = { vm.setKind(option) },
                            shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes() else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                        ) { Text(option.label) }
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    searching = !searching
                    if (!searching && filter.query.isNotEmpty()) {
                        text.setTextAndPlaceCursorAtEnd("")
                        vm.setFilter(filter.copy(query = ""))
                    }
                }) { Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, if (searching) "关闭搜索" else "搜索") }
                IconButton(onClick = { filtering = true }) {
                    BadgedBox(badge = { if (filter.active > 0) Badge { Text(filter.active.toString()) } }) {
                        Icon(Icons.Rounded.Tune, "筛选")
                    }
                }
            }
            if (searching) {
                SearchBarDefaults.InputField(
                    textFieldState = text,
                    searchBarState = rememberSearchBarState(),
                    onSearch = {
                        keyboard?.hide()
                        vm.setFilter(filter.copy(query = it.trim()))
                    },
                    placeholder = { Text("搜索 TMDb 片名") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
            }
        }
    }

    if (filtering) {
        FilterSheet(filter, genres, onDismiss = { filtering = false }) { next ->
            filtering = false
            vm.setFilter(next.copy(query = filter.query))
        }
    }
}

@Composable
private fun DiscoverGrid(
    paged: Paged<DiscoverItem>,
    grid: LazyGridState,
    top: Dp,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (DiscoverItem) -> Unit,
) {
    val shouldLoad by remember(paged) {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            paged.canLoadMore && last >= paged.items.size - 18
        }
    }
    LaunchedEffect(grid, paged) { snapshotFlow { shouldLoad }.collect { if (it) onLoadMore() } }
    LazyVerticalGrid(
        state = grid,
        columns = GridCells.Adaptive(if (LocalWindowLayout.current.wide) 140.dp else 108.dp),
        contentPadding = PaddingValues(start = Etkn.space.screen, end = Etkn.space.screen, top = top, bottom = LocalBottomInset.current),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(paged.items, key = { it.kind.wire + it.id }, contentType = { "discover" }) { item ->
            DiscoverCard(item, onClick = { onOpen(item) })
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

/** 2:3 TMDb poster with status ribbon and rating, title and year below (mirrors PosterCard). */
@Composable
fun DiscoverCard(item: DiscoverItem, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp? = null) {
    val url = images().tmdb(item.posterPath, "w342")
    Column(
        modifier.then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()).clip(SmallCardShape).clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(CardShape)) {
            Artwork(url, Modifier.fillMaxSize(), item.title, placeholder = Icons.Rounded.Movie)
            item.status?.let { StatusPill(it, Modifier.align(Alignment.TopStart).padding(6.dp)) }
            formatRating(item.vote)?.let { rating ->
                Row(
                    Modifier.align(Alignment.TopEnd).padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Star, null, Modifier.size(12.dp), tint = Etkn.tokens.rating)
                    Spacer(Modifier.width(2.dp))
                    Text(rating, style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }
        Text(
            item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
        )
        if (item.year.isNotEmpty()) {
            Text(item.year, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 2.dp))
        }
    }
}

@Composable
fun StatusPill(status: DiscoverStatus, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (status) {
        DiscoverStatus.InLibrary -> scheme.primary to scheme.onPrimary
        DiscoverStatus.Subscribed, DiscoverStatus.Wanted -> scheme.tertiary to scheme.onTertiary
        else -> scheme.surfaceContainerHighest to scheme.onSurface
    }
    Surface(modifier, shape = RoundedCornerShape(8.dp), color = container, contentColor = content) {
        Text(status.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun FilterSheet(initial: DiscoverFilter, genres: List<DiscoverOption>, onDismiss: () -> Unit, onApply: (DiscoverFilter) -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            FilterGroup("排序") {
                DiscoverSort.entries.forEach { sort ->
                    FilterChip(selected = draft.sort == sort, onClick = { draft = draft.copy(sort = sort) }, label = { Text(sort.label) })
                }
            }
            if (genres.isNotEmpty()) {
                FilterGroup("类型") {
                    genres.forEach { genre ->
                        val selected = genre.value in draft.genres
                        FilterChip(
                            selected = selected,
                            onClick = { draft = draft.copy(genres = if (selected) draft.genres - genre.value else draft.genres + genre.value) },
                            label = { Text(genre.label) },
                        )
                    }
                }
            }
            FilterGroup("年代") {
                DiscoverEra.entries.forEach { era ->
                    FilterChip(selected = draft.era == era, onClick = { draft = draft.copy(era = era) }, label = { Text(era.label) })
                }
            }
            FilterGroup("评分") {
                listOf(null, 6, 7, 8).forEach { vote ->
                    FilterChip(
                        selected = draft.minVote == vote,
                        onClick = { draft = draft.copy(minVote = vote) },
                        label = { Text(vote?.let { "$it 分以上" } ?: "不限") },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().clickable { draft = draft.copy(hideInLibrary = !draft.hideInLibrary) }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("隐藏已入库", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = draft.hideInLibrary, onCheckedChange = { draft = draft.copy(hideInLibrary = it) })
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { draft = DiscoverFilter(query = initial.query) }, modifier = Modifier.weight(1f)) { Text("重置") }
                Button(onClick = { onApply(draft) }, modifier = Modifier.weight(1f)) { Text("应用") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
