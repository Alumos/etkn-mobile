package xyz.etkn.mobile.ui.nav

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavGraph.Companion.findStartDestination
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

    fun top(destination: TopLevel) {
        controller.navigate(destination.route) {
            popUpTo(controller.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun to(route: Any) = controller.navigate(route) { launchSingleTop = true }

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
