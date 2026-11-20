package xyz.etkn.mobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeTonalSpot
import xyz.etkn.mobile.data.settings.ColorSource
import xyz.etkn.mobile.data.settings.Settings
import xyz.etkn.mobile.data.settings.ThemeMode

/** TV defaults when the server has no theme colour configured. */
private const val DEFAULT_DARK_SEED = 0xFF55C7B0.toInt()
private const val DEFAULT_LIGHT_SEED = 0xFF087F70.toInt()

fun parseSeed(value: String?): Int? {
    val text = value?.trim()?.removePrefix("#") ?: return null
    return when (text.length) {
        6 -> text.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
        8 -> text.toLongOrNull(16)?.toInt()
        else -> null
    }
}

/** HCT tonal-spot scheme (2025 spec) from a single seed colour. */
fun seedColorScheme(seed: Int, dark: Boolean, amoled: Boolean): ColorScheme {
    val s: DynamicScheme = SchemeTonalSpot(Hct.fromInt(seed), dark, 0.0, ColorSpec.SpecVersion.SPEC_2025)
    fun c(argb: Int) = Color(argb)
    val scheme = if (dark) darkColorScheme(
            primary = c(s.primary), onPrimary = c(s.onPrimary),
            primaryContainer = c(s.primaryContainer), onPrimaryContainer = c(s.onPrimaryContainer),
            inversePrimary = c(s.inversePrimary),
            secondary = c(s.secondary), onSecondary = c(s.onSecondary),
            secondaryContainer = c(s.secondaryContainer), onSecondaryContainer = c(s.onSecondaryContainer),
            tertiary = c(s.tertiary), onTertiary = c(s.onTertiary),
            tertiaryContainer = c(s.tertiaryContainer), onTertiaryContainer = c(s.onTertiaryContainer),
            background = c(s.background), onBackground = c(s.onBackground),
            surface = c(s.surface), onSurface = c(s.onSurface),
            surfaceVariant = c(s.surfaceVariant), onSurfaceVariant = c(s.onSurfaceVariant),
            surfaceTint = c(s.surfaceTint),
            inverseSurface = c(s.inverseSurface), inverseOnSurface = c(s.inverseOnSurface),
            error = c(s.error), onError = c(s.onError),
            errorContainer = c(s.errorContainer), onErrorContainer = c(s.onErrorContainer),
            outline = c(s.outline), outlineVariant = c(s.outlineVariant), scrim = c(s.scrim),
            surfaceBright = c(s.surfaceBright), surfaceContainer = c(s.surfaceContainer),
            surfaceContainerHigh = c(s.surfaceContainerHigh), surfaceContainerHighest = c(s.surfaceContainerHighest),
            surfaceContainerLow = c(s.surfaceContainerLow), surfaceContainerLowest = c(s.surfaceContainerLowest),
            surfaceDim = c(s.surfaceDim),
        ) else lightColorScheme(
            primary = c(s.primary), onPrimary = c(s.onPrimary),
            primaryContainer = c(s.primaryContainer), onPrimaryContainer = c(s.onPrimaryContainer),
            inversePrimary = c(s.inversePrimary),
            secondary = c(s.secondary), onSecondary = c(s.onSecondary),
            secondaryContainer = c(s.secondaryContainer), onSecondaryContainer = c(s.onSecondaryContainer),
            tertiary = c(s.tertiary), onTertiary = c(s.onTertiary),
            tertiaryContainer = c(s.tertiaryContainer), onTertiaryContainer = c(s.onTertiaryContainer),
            background = c(s.background), onBackground = c(s.onBackground),
            surface = c(s.surface), onSurface = c(s.onSurface),
            surfaceVariant = c(s.surfaceVariant), onSurfaceVariant = c(s.onSurfaceVariant),
            surfaceTint = c(s.surfaceTint),
            inverseSurface = c(s.inverseSurface), inverseOnSurface = c(s.inverseOnSurface),
            error = c(s.error), onError = c(s.onError),
            errorContainer = c(s.errorContainer), onErrorContainer = c(s.onErrorContainer),
            outline = c(s.outline), outlineVariant = c(s.outlineVariant), scrim = c(s.scrim),
            surfaceBright = c(s.surfaceBright), surfaceContainer = c(s.surfaceContainer),
            surfaceContainerHigh = c(s.surfaceContainerHigh), surfaceContainerHighest = c(s.surfaceContainerHighest),
            surfaceContainerLow = c(s.surfaceContainerLow), surfaceContainerLowest = c(s.surfaceContainerLowest),
            surfaceDim = c(s.surfaceDim),
        )
    return if (dark && amoled) scheme.toAmoled() else scheme
}

/**
 * A page themed by its artwork (detail pages): accents from the image's colour, and surfaces
 * tinted with its hue a little more than the tonal-spot neutrals so the page reads as that
 * title, not as plain black or white. Light / dark follows the app.
 */
@Composable
fun ArtworkTheme(seed: Int?, content: @Composable () -> Unit) {
    if (seed == null) {
        content()
        return
    }
    val dark = Etkn.tokens.isDark
    val scheme = remember(seed, dark) { seedColorScheme(seed, dark, amoled = false).tintedSurfaces(Hct.fromInt(seed).hue, dark) }
    val tokens = remember(scheme, dark) { etknTokens(scheme, dark) }
    CompositionLocalProvider(LocalEtknTokens provides tokens) {
        MaterialExpressiveTheme(colorScheme = scheme, content = content)
    }
}

private fun ColorScheme.tintedSurfaces(hue: Double, dark: Boolean): ColorScheme {
    fun tone(t: Double) = Color(Hct.from(hue, if (dark) 14.0 else 8.0, t).toInt())
    val steps = if (dark) listOf(10.0, 8.0, 12.0, 15.0, 18.0, 22.0) else listOf(96.0, 98.0, 95.0, 93.0, 91.0, 89.0)
    return copy(
        background = tone(steps[0]),
        surface = tone(steps[0]),
        surfaceContainerLowest = tone(steps[1]),
        surfaceContainerLow = tone(steps[2]),
        surfaceContainer = tone(steps[3]),
        surfaceContainerHigh = tone(steps[4]),
        surfaceContainerHighest = tone(steps[5]),
    )
}

private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0D),
    surfaceContainer = Color(0xFF121215),
    surfaceContainerHigh = Color(0xFF1A1A1E),
    surfaceContainerHighest = Color(0xFF232328),
)

/** Material's shape roles folded onto [Corner]'s scale, so stock components round like the rest. */
val EtknShapes = Shapes(
    extraSmall = Corner.small,
    small = Corner.small,
    medium = Corner.card,
    large = Corner.card,
    largeIncreased = Corner.card,
    extraLarge = Corner.panel,
    extraLargeIncreased = Corner.panel,
    extraExtraLarge = Corner.panel,
)

private val Base = Typography()

/** Editorial hierarchy: heavier display/headline weights, slightly tighter tracking for CJK titles. */
val EtknTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontWeight = FontWeight.Bold),
    displayMedium = Base.displayMedium.copy(fontWeight = FontWeight.Bold),
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall,
    bodyLarge = Base.bodyLarge,
    bodyMedium = Base.bodyMedium,
    bodySmall = Base.bodySmall,
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium,
    labelSmall = Base.labelSmall,
)

@Composable
fun isAppDark(settings: Settings): Boolean = when (settings.themeMode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Server -> settings.serverDarkMode ?: isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/** The app settings the theme was built from, for screens that only read a preference. */
val LocalSettings = compositionLocalOf { Settings() }

@Composable
fun EtknTheme(
    settings: Settings,
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = forceDark || isAppDark(settings)
    val context = LocalContext.current
    val wallpaper = settings.colorSource == ColorSource.Wallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val seed = parseSeed(settings.serverThemeColor) ?: if (dark) DEFAULT_DARK_SEED else DEFAULT_LIGHT_SEED
    val scheme = remember(dark, wallpaper, seed, settings.amoled) {
        if (wallpaper) {
            val base = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (dark && settings.amoled) base.toAmoled() else base
        } else {
            seedColorScheme(seed, dark, settings.amoled)
        }
    }
    val tokens = remember(scheme, dark) { etknTokens(scheme, dark) }
    CompositionLocalProvider(
        LocalEtknTokens provides tokens,
        LocalSettings provides settings,
    ) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            shapes = EtknShapes,
            typography = EtknTypography,
            content = content,
        )
    }
}

/** Shortcut for the hero / title text drawn over artwork. */
val HeroTitleStyle: TextStyle
    @Composable get() = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black)
