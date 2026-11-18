package xyz.etkn.mobile.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
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
import xyz.etkn.mobile.ui.screens.detail.DetailScreen
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
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    LaunchedEffect(session.key) { context.graph.syncServerAppearance() }

    val dockVisible = current != null && !wide
    val bottomInset: Dp = (if (dockVisible) Etkn.space.dockBottomInset else 24.dp) +
        (if (showMini) Etkn.space.miniPlayer + 8.dp else 0.dp)

    CompositionLocalProvider(LocalNav provides nav, LocalHaze provides hazeState, LocalBottomInset provides bottomInset) {
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                WideNavigationRail(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                    TopLevel.entries.forEach { tab ->
                        WideNavigationRailItem(
                            railExpanded = false,
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
                    composable<SettingsRoute> { entry ->
                        val page = entry.toRoute<SettingsRoute>().page
                        SettingsScreen(SettingsPage.entries.firstOrNull { it.name == page } ?: SettingsPage.Appearance)
                    }
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 14.dp)
                        .padding(bottom = if (dockVisible) 10.dp else 12.dp),
                ) {
                    AnimatedVisibility(showMini, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
                        MiniPlayer(onOpen = { nav.to(NowPlayingRoute) }, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    AnimatedVisibility(dockVisible, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
                        Dock(current, nav)
                    }
                }
            }
        }
    }
}

@Composable
private fun Dock(current: TopLevel?, nav: AppNav) {
    val shape = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxWidth().glass(shape, elevation = 14.dp)) {
        ShortNavigationBar(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            windowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            TopLevel.entries.forEach { tab ->
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
