package xyz.etkn.mobile.ui.nav

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
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
import xyz.etkn.mobile.ui.screens.music.MusicHomeScreen
import xyz.etkn.mobile.ui.screens.music.MusicListScreen
import xyz.etkn.mobile.ui.screens.music.NowPlayingScreen
import xyz.etkn.mobile.ui.screens.person.PersonScreen
import xyz.etkn.mobile.ui.screens.search.SearchScreen

/** Every destination inside the shell. */
@Composable
internal fun AppNavHost(controller: NavHostController, modifier: Modifier = Modifier) {
    // Theme motion (Material 3 Expressive springs) instead of fixed tweens: interrupted or
    // scrubbed transitions keep their velocity and settle naturally.
    val motion = MaterialTheme.motionScheme
    val move = motion.defaultSpatialSpec<IntOffset>()
    val zoom = motion.defaultSpatialSpec<Float>()
    val fade = motion.defaultEffectsSpec<Float>()
    NavHost(
        navController = controller,
        startDestination = HomeRoute,
        modifier = modifier,
        // Pop transitions are what the predictive back gesture scrubs: the page being
        // left shrinks into a rounded card and drifts with the finger while the page
        // underneath (already laid out, opaque) settles back from a slight zoom.
        enterTransition = { fadeIn(fade) + slideInHorizontally(move) { it / 12 } },
        exitTransition = { fadeOut(fade) + scaleOut(zoom, targetScale = 0.96f) },
        popEnterTransition = { fadeIn(fade) + scaleIn(zoom, initialScale = 0.96f) },
        popExitTransition = { scaleOut(zoom, targetScale = 0.88f) + slideOutHorizontally(move) { it / 8 } },
    ) {
        page<HomeRoute> { HomeScreen() }
        page<LibrariesRoute> { LibrariesScreen() }
        page<MusicRoute> { MusicHomeScreen() }
        page<SearchRoute> { SearchScreen() }
        page<MeRoute> { MeScreen() }
        page<LibraryRoute> { LibraryScreen(it.toRoute()) }
        page<DetailRoute> { DetailScreen(it.toRoute<DetailRoute>().id) }
        page<PersonRoute> { PersonScreen(it.toRoute()) }
        page<FavoritesRoute> { FavoritesScreen() }
        page<MusicListRoute> { MusicListScreen(it.toRoute()) }
        page<NowPlayingRoute> { NowPlayingScreen() }
        page<AccountsRoute> { AccountsScreen() }
        page<DiscoverRoute> { DiscoverScreen() }
        page<DiscoverDetailRoute> { DiscoverDetailScreen(it.toRoute()) }
        page<DiscoverPersonRoute> { DiscoverPersonScreen(it.toRoute()) }
        page<CloudRoute> { CloudScreen(it.toRoute()) }
        page<SettingsRoute> { entry ->
            val page = entry.toRoute<SettingsRoute>().page
            SettingsScreen(SettingsPage.entries.firstOrNull { it.name == page } ?: SettingsPage.Appearance)
        }
    }
}

/**
 * Every destination draws its own opaque, clipped page. Without it the predictive back gesture
 * shrinks a transparent page over the one underneath and both bleed into each other; the corner
 * radius follows the gesture so the page reads as a card while it is being dragged away.
 */
private inline fun <reified T : Any> NavGraphBuilder.page(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable<T> { entry -> PageFrame { content(entry) } }

@Composable
private fun AnimatedContentScope.PageFrame(content: @Composable () -> Unit) {
    val corner = transition.animateDp(label = "pageCorner") { if (it == EnterExitState.Visible) 0.dp else 32.dp }
    val background = MaterialTheme.colorScheme.surface
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer {
                val radius = corner.value.toPx()
                clip = radius > 0f
                shape = RoundedCornerShape(radius)
            }
            .background(background),
    ) { content() }
}
