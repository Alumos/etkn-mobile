package xyz.etkn.mobile.ui.screens.home

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import xyz.etkn.mobile.ui.components.glassSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import xyz.etkn.mobile.ui.common.windowSizeDp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.etkn.mobile.data.session.SessionState
import xyz.etkn.mobile.graph
import xyz.etkn.mobile.data.settings.HeroMode
import xyz.etkn.mobile.ui.theme.LocalSettings
import xyz.etkn.mobile.ui.common.Load
import xyz.etkn.mobile.ui.common.LocalWindowLayout
import xyz.etkn.mobile.ui.common.graphViewModel
import xyz.etkn.mobile.ui.components.Artwork
import xyz.etkn.mobile.ui.components.ErrorState
import xyz.etkn.mobile.ui.components.HeroCarousel
import xyz.etkn.mobile.ui.components.LibraryCard
import xyz.etkn.mobile.ui.components.LoadingState
import xyz.etkn.mobile.ui.components.PosterCard
import xyz.etkn.mobile.ui.components.SquareCard
import xyz.etkn.mobile.ui.components.WideCard
import xyz.etkn.mobile.ui.components.glass
import xyz.etkn.mobile.ui.components.images
import xyz.etkn.mobile.ui.components.shelf
import xyz.etkn.mobile.ui.nav.FavoritesRoute
import xyz.etkn.mobile.ui.nav.LibraryRoute
import xyz.etkn.mobile.ui.nav.LocalBottomInset
import xyz.etkn.mobile.ui.nav.LocalNav
import xyz.etkn.mobile.ui.nav.TopLevel
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

@Composable
fun HomeScreen() {
    val vm = graphViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }

    when (val s = state) {
        Load.Loading -> LoadingState()
        is Load.Failed -> ErrorState(s.message, vm::refresh)
        is Load.Ready -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            HomeContent(s.value, vm)
        }
    }
}

@Composable
private fun HomeContent(data: HomeData, vm: HomeViewModel) {
    val nav = LocalNav.current
    val list = rememberLazyListState()
    val screenHeight = windowSizeDp().height
    val wide = LocalWindowLayout.current.wide
    // Tablets: a cinematic band instead of a near-full-screen poster.
    val heroHeight = if (wide) (screenHeight * 0.58f).coerceIn(380.dp, 560.dp) else (screenHeight * 0.64f).coerceIn(420.dp, 620.dp)
    val revealPx = with(LocalDensity.current) { 96.dp.toPx() }
    val heroMode = LocalSettings.current.heroMode
    val hasHero = data.hero.isNotEmpty() && heroMode != HeroMode.Off
    // 0 over the untouched hero (bar invisible, poster unobstructed) → 1 after ~96 dp of scrolling.
    val reveal by remember(hasHero) {
        derivedStateOf {
            if (!hasHero || list.firstVisibleItemIndex > 0) 1f else (list.firstVisibleItemScrollOffset / revealPx).coerceIn(0f, 1f)
        }
    }
    val haze = rememberHazeState()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().glassSource(haze)) {
            if (hasHero) {
                item(key = "hero", contentType = "hero") {
                    HeroCarousel(
                        items = data.hero,
                        height = heroHeight,
                        autoAdvance = heroMode == HeroMode.Auto,
                        scrollOffsetPx = { if (list.firstVisibleItemIndex == 0) list.firstVisibleItemScrollOffset else 0 },
                        onPlay = { nav.play(it) },
                        onOpen = { nav.open(it) },
                    )
                }
            } else {
                item(key = "top-space") { Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars).height(64.dp)) }
            }
            shelf("resume", "继续观看", data.resume) { item ->
                WideCard(item, onClick = { nav.play(item) }, resume = true)
            }
            shelf("libraries", "媒体库", data.libraries, onMore = { nav.top(TopLevel.Libraries) }) { library ->
                LibraryCard(library, onClick = { nav.openLibrary(library) })
            }
            data.latest.forEach { shelfData ->
                val library = shelfData.library
                shelf("latest-${library.id}", "最新 · ${library.name}", shelfData.items, onMore = {
                    nav.to(LibraryRoute(library.id, library.name, library.collectionType))
                }) { item -> PosterCard(item, onClick = { nav.open(item) }) }
            }
            data.musicLatest?.let { (library, albums) ->
                shelf("music-${library.id}", "新专辑", albums, onMore = { nav.openLibrary(library) }) { album ->
                    SquareCard(album, onClick = { nav.open(album) })
                }
            }
            item(key = "bottom") { Spacer(Modifier.height(LocalBottomInset.current + 16.dp)) }
        }
        HomeTopBar(reveal = { reveal }, haze = haze, onSearch = { nav.top(TopLevel.Search) }, onFavorites = { nav.to(FavoritesRoute) }, onMe = { nav.top(TopLevel.Me) })
    }
}

/**
 * Over the untouched hero only the title and icons float on the poster; the glass pill fades in
 * as soon as the page scrolls ([reveal] 0 → 1) and stays once the hero is gone.
 */
@Composable
private fun HomeTopBar(reveal: () -> Float, haze: HazeState, onSearch: () -> Unit, onFavorites: () -> Unit, onMe: () -> Unit) {
    val graph = LocalContext.current.graph
    val session = (graph.sessions.state.collectAsStateWithLifecycle().value as? SessionState.SignedIn)?.session ?: return
    val urls = images()
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(26.dp)
    val fraction = reveal()
    val content = lerp(Color.White, MaterialTheme.colorScheme.onSurface, fraction)
    Box(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        if (fraction > 0f) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = fraction }.glass(shape, elevation = 8.dp, state = haze))
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp).height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                session.serverName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "搜索", tint = content) }
            Box {
                IconButton(onClick = { menu = true }) {
                    Artwork(
                        urls.user(tag = session.imageTag),
                        Modifier.size(32.dp).clip(CircleShape),
                        session.userName,
                        placeholder = Icons.Rounded.Person,
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(16.dp)) {
                    DropdownMenuItem(text = { Text("我的收藏") }, onClick = { menu = false; onFavorites() })
                    DropdownMenuItem(text = { Text("账号与设置") }, onClick = { menu = false; onMe() })
                }
            }
        }
    }
}
