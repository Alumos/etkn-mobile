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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import xyz.etkn.mobile.data.session.ActiveSession
import xyz.etkn.mobile.graph
import xyz.etkn.mobile.ui.common.LocalWindowLayout
import xyz.etkn.mobile.ui.common.WindowLayout
import xyz.etkn.mobile.ui.common.currentWindowLayout
import xyz.etkn.mobile.ui.components.LocalHaze
import xyz.etkn.mobile.ui.components.glassSource
import xyz.etkn.mobile.ui.screens.music.MiniPlayer
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
    val canDiscover by context.graph.discover.allowed.collectAsStateWithLifecycle()
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
        val discover = context.graph.discover
        discover.clear()
        launch { discover.allowed.value = runCatching { context.graph.api.canDiscover() }.getOrDefault(false) }
        context.graph.syncServerAppearance()
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
                AppNavHost(controller, Modifier.fillMaxSize().glassSource(hazeState))
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
