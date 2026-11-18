package xyz.etkn.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.compose.material3.MaterialTheme
import xyz.etkn.mobile.ui.theme.Etkn

/**
 * Blur source behind the app chrome (dock, mini player); null means "no blur, paint flat".
 * Bars that float *inside* a screen cannot sample it — Haze skips sources that are ancestors of
 * the effect — so such screens wrap their scrolling content in [glassSource] with their own
 * [rememberHazeState] and pass it to [glass] explicitly.
 */
val LocalHaze = staticCompositionLocalOf<HazeState?> { null }

fun Modifier.glassSource(state: HazeState?): Modifier = if (state == null) this else hazeSource(state)

/**
 * Glass surface: real backdrop blur (Android 12+ via Haze, translucent fallback below), a soft
 * drop shadow and a faint top highlight.
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    elevation: Dp = 12.dp,
    blur: Dp = 24.dp,
    border: Boolean = true,
    tint: Color? = null,
    state: HazeState? = LocalHaze.current,
): Modifier {
    val tokens = Etkn.tokens
    val surface = MaterialTheme.colorScheme.surface
    var m = this
    if (elevation > 0.dp) {
        m = m.shadow(elevation, shape, clip = false, ambientColor = tokens.glassShadow, spotColor = tokens.glassShadow)
    }
    m = m.clip(shape)
    m = if (state == null) {
        m.background(tint ?: tokens.glassFlat, shape)
    } else {
        m.hazeEffect(
            state = state,
            style = HazeStyle(
                backgroundColor = surface,
                tints = listOf(
                    HazeTint(tint ?: tokens.glassTint),
                    HazeTint(Brush.verticalGradient(0f to tokens.glassHighlight, 0.2f to Color.Transparent)),
                ),
                blurRadius = blur,
                noiseFactor = 0.03f,
                fallbackTint = HazeTint(tint ?: tokens.glassFlat),
            ),
        )
    }
    if (border) m = m.border(1.dp, tokens.glassBorder, shape)
    return m
}

/**
 * Back button over artwork that turns into a glass pill with the title once the page scrolls.
 * [haze] must be the screen-local source wrapping that page's scrolling content.
 */
@Composable
fun GlassBackBar(title: String, collapsed: Boolean, haze: HazeState, onBack: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 12.dp, vertical = 6.dp)
            .then(if (collapsed) Modifier.glass(shape, elevation = 8.dp, state = haze) else Modifier)
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkBackButton(onBack, Modifier.padding(start = 2.dp), onGlass = collapsed)
        if (collapsed) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}

@Composable
fun ArtworkBackButton(onBack: () -> Unit, modifier: Modifier = Modifier, onGlass: Boolean = false) {
    FilledTonalIconButton(
        onClick = onBack,
        modifier = modifier,
        colors = if (onGlass) IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Transparent)
        else IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.35f), contentColor = Color.White),
    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
}

