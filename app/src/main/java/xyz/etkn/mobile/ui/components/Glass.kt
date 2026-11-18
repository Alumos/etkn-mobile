package xyz.etkn.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

/** Blur source of the current screen; null means "no blur available, paint flat". */
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
): Modifier {
    val tokens = Etkn.tokens
    val state = LocalHaze.current
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
