package xyz.etkn.mobile.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize

/** Material window width classes: phone (< 600 dp), small tablet / unfolded (600–839), tablet (≥ 840). */
enum class WindowLayout {
    Compact, Medium, Expanded;

    val wide: Boolean get() = this != Compact
}

val LocalWindowLayout = staticCompositionLocalOf { WindowLayout.Compact }

/**
 * The window in dp at the density the app actually draws with. Derived from pixels rather than
 * `Configuration.screenWidthDp`, which ignores the in-app page size (an overridden densityDpi).
 */
@Composable
fun windowSizeDp(): DpSize {
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { DpSize(size.width.toDp(), size.height.toDp()) }
}

@Composable
fun currentWindowLayout(): WindowLayout {
    val width = windowSizeDp().width.value
    return when {
        width >= 840 -> WindowLayout.Expanded
        width >= 600 -> WindowLayout.Medium
        else -> WindowLayout.Compact
    }
}
