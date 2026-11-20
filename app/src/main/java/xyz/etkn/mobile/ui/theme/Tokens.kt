package xyz.etkn.mobile.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Semantic colours that Material roles do not cover: scrims over artwork, glass layers,
 * badges. Every derivation lives here; components only read tokens.
 *
 * Scrims over images stay dark in both themes (white text over a bright poster must stay
 * readable); glass layers flip with the theme.
 */
@Immutable
data class EtknTokens(
    val isDark: Boolean,
    val onImage: Color,
    val onImageMuted: Color,
    val onImageTrack: Color,
    val glassTint: Color,
    val glassFlat: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val glassShadow: Color,
    val heroTopScrim: Brush,
    val heroBottomScrim: Brush,
    val heroSideScrim: Brush,
    val posterScrim: Brush,
    val wideScrim: Brush,
    val detailScrim: Brush,
    val skeleton: Color,
    val rating: Color,
)

fun etknTokens(scheme: ColorScheme, dark: Boolean): EtknTokens = EtknTokens(
    isDark = dark,
    onImage = Color.White,
    onImageMuted = Color.White.copy(alpha = 0.78f),
    onImageTrack = Color.White.copy(alpha = 0.26f),
    glassTint = scheme.surfaceContainer.copy(alpha = if (dark) 0.58f else 0.66f),
    glassFlat = scheme.surfaceContainer.copy(alpha = if (dark) 0.94f else 0.96f),
    glassBorder = scheme.outlineVariant.copy(alpha = if (dark) 0.42f else 0.55f),
    glassHighlight = Color.White.copy(alpha = if (dark) 0.07f else 0.5f),
    glassShadow = Color.Black.copy(alpha = if (dark) 0.55f else 0.18f),
    heroTopScrim = Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.62f),
        0.5f to Color.Black.copy(alpha = 0.22f),
        1f to Color.Transparent,
    ),
    heroBottomScrim = Brush.verticalGradient(
        0f to Color.Transparent,
        0.55f to scheme.surface.copy(alpha = 0.78f),
        1f to scheme.surface,
    ),
    heroSideScrim = Brush.horizontalGradient(
        0f to scheme.surface.copy(alpha = 0.86f),
        0.45f to scheme.surface.copy(alpha = 0.35f),
        0.75f to Color.Transparent,
    ),
    posterScrim = Brush.verticalGradient(
        0f to Color.Transparent,
        0.45f to Color.Transparent,
        0.7f to Color.Black.copy(alpha = 0.55f),
        1f to Color.Black.copy(alpha = 0.86f),
    ),
    wideScrim = Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.28f),
        0.35f to Color.Transparent,
        0.6f to Color.Transparent,
        1f to Color.Black.copy(alpha = 0.66f),
    ),
    detailScrim = Brush.verticalGradient(
        0f to Color.Transparent,
        0.45f to scheme.surface.copy(alpha = 0.5f),
        1f to scheme.surface,
    ),
    skeleton = scheme.surfaceContainerHigh,
    rating = if (dark) Color(0xFFFFC857) else Color(0xFFB07800),
)

val LocalEtknTokens = staticCompositionLocalOf<EtknTokens> { error("EtknTokens not provided") }

object Etkn {
    val tokens: EtknTokens
        @Composable @ReadOnlyComposable get() = LocalEtknTokens.current

    val space = Spacing
}

object Spacing {
    val screen: Dp = 20.dp
    val section: Dp = 28.dp
    val item: Dp = 12.dp
    val posterWidth: Dp = 118.dp
    val posterWidthWide: Dp = 150.dp
    val wideCardWide: Dp = 300.dp
    val wideCard: Dp = 236.dp
    val dockBottomInset: Dp = 104.dp
    /** Tablet side dock: its width, and the space tab pages leave for it (margin + dock + gap). */
    val sideDock: Dp = 76.dp
    val sideDockInset: Dp = 92.dp
    val miniPlayer: Dp = 64.dp
}
