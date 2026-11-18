package xyz.etkn.mobile.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

/** Material window width classes: phone (< 600 dp), small tablet / unfolded (600–839), tablet (≥ 840). */
enum class WindowLayout {
    Compact, Medium, Expanded;

    val wide: Boolean get() = this != Compact
}

val LocalWindowLayout = staticCompositionLocalOf { WindowLayout.Compact }

@Composable
@ReadOnlyComposable
fun currentWindowLayout(): WindowLayout {
    val width = LocalConfiguration.current.screenWidthDp
    return when {
        width >= 840 -> WindowLayout.Expanded
        width >= 600 -> WindowLayout.Medium
        else -> WindowLayout.Compact
    }
}
