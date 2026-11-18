package xyz.etkn.mobile.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.chrisbanes.haze.rememberHazeState
import xyz.etkn.mobile.data.session.ActiveSession
import xyz.etkn.mobile.graph
import xyz.etkn.mobile.ui.components.LocalHaze
import xyz.etkn.mobile.ui.components.glass
import xyz.etkn.mobile.ui.components.glassSource
import xyz.etkn.mobile.ui.common.LocalWindowLayout
import xyz.etkn.mobile.ui.common.WindowLayout
import xyz.etkn.mobile.ui.common.currentWindowLayout
import xyz.etkn.mobile.ui.screens.detail.DetailScreen
import xyz.etkn.mobile.ui.screens.discover.CloudScreen
import xyz.etkn.mobile.ui.screens.discover.DiscoverDetailScreen
import xyz.etkn.mobile.ui.screens.discover.DiscoverPersonScreen
import xyz.etkn.mobile.ui.screens.discover.DiscoverScreen
import xyz.etkn.mobile.ui.screens.home.HomeScreen
import xyz.etkn.mobile.ui.screens.library.FavoritesScreen
import xyz.etkn.mobile.ui.screens.library.LibrariesScreen
import xyz.etkn.mobile.ui.screens.library.LibraryScreen
import xyz.etkn.mobile.ui.screens.me.AccountsScreen
import xyz.etkn.mobile.ui.screens.me.MeScreen
import xyz.etkn.mobile.ui.screens.me.SettingsPage
import xyz.etkn.mobile.ui.screens.me.SettingsScreen
import xyz.etkn.mobile.ui.screens.music.MiniPlayer
import xyz.etkn.mobile.ui.screens.music.MusicHomeScreen
import xyz.etkn.mobile.ui.screens.music.MusicListScreen
import xyz.etkn.mobile.ui.screens.music.NowPlayingScreen
import xyz.etkn.mobile.ui.screens.person.PersonScreen
import xyz.etkn.mobile.ui.screens.search.SearchScreen
import xyz.etkn.mobile.ui.theme.Etkn

/** Space screens must leave at the bottom for the floating dock / mini player. */
val LocalBottomInset = staticCompositionLocalOf { 0.dp }

@Composable
fun MainShell(session: ActiveSession) {
    val context = LocalContext.current
    val controller = rememberNavController()
    val nav = remember(controller) { AppNav(controller, context) }
    val hazeState = rememberHazeState()
    val backStack by controller.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val current = TopLevel.entries.firstOrNull { tab -> destination?.hierarchy?.any { tab.matches(it) } == true }
    val isNowPlaying = destination?.hasRoute<NowPlayingRoute>() == true
    val music = context.graph.music
    val nowPlaying by music.state.collectAsStateWithLifecycle()
    val showMini = nowPlaying.current != null && !isNowPlaying
    val layout = currentWindowLayout()
    val wide = layout.wide
    var canDiscover by remember { mutableStateOf(false) }
    // Phone dock keeps five destinations: 探索 takes 搜索's slot when the account may discover
    // (search stays one tap away in the home top bar); the tablet rail shows everything.
    val tabs = TopLevel.entries.filter { tab ->
        when (tab) {
            TopLevel.Discover -> canDiscover
            TopLevel.Search -> wide || !canDiscover
            else -> true
        }
    }

    LaunchedEffect(session.key) {
        context.graph.discover.clear()
        context.graph.syncServerAppearance()
    }
    LaunchedEffect(session.key) {
        canDiscover = runCatching { context.graph.api.canDiscover() }.getOrDefault(false)
    }

    val dockVisible = current != null && !wide
    val bottomInset: Dp = (if (dockVisible) Etkn.space.dockBottomInset else 24.dp) +
        (if (showMini) Etkn.space.miniPlayer + 8.dp else 0.dp)

    val rail = rememberWideNavigationRailState()
    val railExpanded = layout == WindowLayout.Expanded
    LaunchedEffect(railExpanded) { if (railExpanded) rail.expand() else rail.collapse() }

    CompositionLocalProvider(
        LocalNav provides nav,
        LocalHaze provides hazeState,
        LocalBottomInset provides bottomInset,
        LocalWindowLayout provides layout,
    ) {
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                WideNavigationRail(state = rail, modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                    tabs.forEach { tab ->
                        WideNavigationRailItem(
                            railExpanded = railExpanded,
                            selected = current == tab,
                            onClick = { nav.top(tab) },
                            icon = { Icon(if (current == tab) tab.selectedIcon else tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxSize()) {
                NavHost(
                    navController = controller,
                    startDestination = HomeRoute,
                    modifier = Modifier.fillMaxSize().glassSource(hazeState),
                    // Pop transitions are what the predictive back gesture scrubs: the page being
                    // left shrinks and slides with the finger while the previous page fades in.
                    enterTransition = { fadeIn(tween(220, delayMillis = 60)) + slideInHorizontally(tween(260)) { it / 12 } },
                    exitTransition = { fadeOut(tween(120)) },
                    popEnterTransition = { fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.96f) },
                    popExitTransition = {
                        scaleOut(tween(260), targetScale = 0.9f) + slideOutHorizontally(tween(260)) { it / 10 } + fadeOut(tween(200))
                    },
                ) {
                    composable<HomeRoute> { HomeScreen() }
                    composable<LibrariesRoute> { LibrariesScreen() }
                    composable<MusicRoute> { MusicHomeScreen() }
                    composable<SearchRoute> { SearchScreen() }
                    composable<MeRoute> { MeScreen() }
                    composable<LibraryRoute> { LibraryScreen(it.toRoute()) }
                    composable<DetailRoute> { DetailScreen(it.toRoute<DetailRoute>().id) }
                    composable<PersonRoute> { PersonScreen(it.toRoute()) }
                    composable<FavoritesRoute> { FavoritesScreen() }
                    composable<MusicListRoute> { MusicListScreen(it.toRoute()) }
                    composable<NowPlayingRoute> { NowPlayingScreen() }
                    composable<AccountsRoute> { AccountsScreen() }
                    composable<DiscoverRoute> { DiscoverScreen() }
                    composable<DiscoverDetailRoute> { DiscoverDetailScreen(it.toRoute()) }
                    composable<DiscoverPersonRoute> { DiscoverPersonScreen(it.toRoute()) }
                    composable<CloudRoute> { CloudScreen(it.toRoute()) }
                    composable<SettingsRoute> { entry ->
                        val page = entry.toRoute<SettingsRoute>().page
                        SettingsScreen(SettingsPage.entries.firstOrNull { it.name == page } ?: SettingsPage.Appearance)
                    }
                }
                Column(
                    Modifier.align(if (wide) Alignment.BottomEnd else Alignment.BottomCenter)
                        .then(if (wide) Modifier.widthIn(max = 520.dp) else Modifier).fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 14.dp)
                        .padding(bottom = if (dockVisible) 10.dp else 12.dp),
                ) {
                    AnimatedVisibility(showMini, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
                        MiniPlayer(onOpen = { nav.to(NowPlayingRoute) }, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    AnimatedVisibility(dockVisible, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
                        Dock(tabs, current, nav)
                    }
                }
            }
        }
    }
}

@Composable
private fun Dock(tabs: List<TopLevel>, current: TopLevel?, nav: AppNav) {
    val shape = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxWidth().glass(shape, elevation = 14.dp)) {
        ShortNavigationBar(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            windowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            tabs.forEach { tab ->
                ShortNavigationBarItem(
                    selected = current == tab,
                    onClick = { nav.top(tab) },
                    icon = { Icon(if (current == tab) tab.selectedIcon else tab.icon, contentDescription = null) },
                    label = { Text(tab.label) },
                )
            }
        }
    }
}
