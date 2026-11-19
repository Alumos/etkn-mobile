package xyz.etkn.mobile.ui.nav

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import xyz.etkn.mobile.data.model.Item
import xyz.etkn.mobile.graph
import xyz.etkn.mobile.player.PlayerLauncher

/** Thin navigation facade so screens never touch the NavController directly. */
@Stable
class AppNav(private val controller: NavHostController, private val context: Context) {

    fun back() {
        controller.popBackStack()
    }

    /** Tabs in the pager and how to move it; set by MainShell. */
    internal var tabs: List<TopLevel> = emptyList()
    internal var selectTab: (TopLevel) -> Unit = {}

    /** A tab in the pager is shown there (over pages are closed); any other opens as a page. */
    fun top(destination: TopLevel) {
        if (destination !in tabs) {
            to(destination.route)
            return
        }
        if (controller.currentDestination?.hasRoute<TabsRoute>() != true) controller.popBackStack<TabsRoute>(inclusive = false)
        selectTab(destination)
    }

    fun to(route: Any) = controller.navigate(route) { launchSingleTop = true }

    /** The title an item belongs to: an episode opens its series, anything else itself. */
    fun openTitle(item: Item) {
        val series = item.seriesId
        if (item.isEpisode && series != null) to(DetailRoute(series)) else open(item)
    }

    fun open(item: Item) {
        when {
            item.isMusicGroup -> to(MusicListRoute(parentId = musicParent(), tab = item.kind ?: "albums", key = item.key ?: "", title = item.name))
            item.isAudio -> context.graph.music.play(listOf(item), 0, null)
            item.type == "Person" -> to(PersonRoute(item.id, item.name))
            item.type == "CollectionFolder" || item.type == "UserView" -> openLibrary(item)
            item.type == "Folder" || item.type == "BoxSet" -> to(LibraryRoute(item.id, item.name, "folder"))
            else -> to(DetailRoute(item.id))
        }
    }

    fun openLibrary(library: Item) {
        if (library.collectionType == "music") {
            context.graph.music.selectLibrary(library.id)
            top(TopLevel.Music)
        } else {
            to(LibraryRoute(library.id, library.name, library.collectionType ?: if (library.sourceType == "group") "group" else null))
        }
    }

    fun play(item: Item, resume: Boolean = true, sourceId: String? = null) {
        PlayerLauncher.play(context, item, resume, sourceId)
    }

    private fun musicParent(): String = context.graph.music.libraryId.value ?: ""
}

val LocalNav = staticCompositionLocalOf<AppNav> { error("AppNav not provided") }
