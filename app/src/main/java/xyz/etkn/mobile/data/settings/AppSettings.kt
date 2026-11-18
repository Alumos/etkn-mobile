package xyz.etkn.mobile.data.settings

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.etkn.mobile.data.update.UpdateSource

private val Context.settingsDataStore by preferencesDataStore(name = "etkn_settings")

enum class ThemeMode(val label: String) { System("跟随系统"), Server("跟随服务器"), Light("浅色"), Dark("深色") }

enum class ColorSource(val label: String) { Server("服务器主题色"), Wallpaper("系统壁纸取色") }

enum class DecoderPreference(val label: String) { Auto("自动"), Hardware("仅硬件"), Software("软件 (FFmpeg)") }

enum class SkipMode(val label: String) { Button("显示按钮"), Auto("自动跳过"), Off("关闭") }

/** Whole-app size: scales density (layout and text together) like the system display size. */
enum class UiScale(val label: String, val factor: Float) {
    Small("小", 0.9f), Default("标准", 1f), Large("大", 1.1f), Larger("特大", 1.2f);

    companion object {
        private const val FILE = "etkn_display"
        private const val KEY = "ui_scale"

        /**
         * Read synchronously in `attachBaseContext`, before any resource is touched, so it lives
         * in plain SharedPreferences rather than DataStore.
         */
        fun read(context: Context): UiScale = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, null)?.let { name -> entries.firstOrNull { it.name == name } } ?: Default

        fun write(context: Context, scale: UiScale) {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit(commit = true) { putString(KEY, scale.name) }
        }

        /**
         * Overrides only `densityDpi`: every window of the activity (dialogs, sheets, menus)
         * then measures with the scaled density, exactly like the system display-size setting.
         */
        fun applyTo(activity: android.view.ContextThemeWrapper, base: Context) {
            val factor = read(base).factor
            if (factor == 1f) return
            val dpi = base.resources.configuration.densityDpi
            activity.applyOverrideConfiguration(android.content.res.Configuration().apply { densityDpi = (dpi * factor).toInt() })
        }
    }
}

/** Home recommendation banner. */
enum class HeroMode(val label: String) { Auto("自动轮播"), Manual("手动切换"), Off("隐藏") }

/** Local danmaku look, same ranges as ETKN TV (font size defaults smaller for phones). */
@Immutable
data class DanmakuStyle(
    val enabled: Boolean = false,
    val rows: Int = 6,
    val density: Int = 100,
    val fontSize: Int = 18,
    val opacity: Int = 85,
    val speed: Float = 1f,
    val themeColor: Boolean = false,
) {
    companion object {
        val Rows = 1..16
        val Density = 10..100
        val FontSize = 12..40
        val Opacity = 10..100
        val Speed = 0.5f..2f
    }
}

@Immutable
data class Settings(
    val themeMode: ThemeMode = ThemeMode.System,
    val colorSource: ColorSource = ColorSource.Server,
    val amoled: Boolean = false,
    val decoder: DecoderPreference = DecoderPreference.Auto,
    val skipMode: SkipMode = SkipMode.Button,
    val autoPlayNext: Boolean = true,
    val defaultSpeed: Float = 1f,
    val serverThemeColor: String? = null,
    val serverDarkMode: Boolean? = null,
    val danmaku: DanmakuStyle = DanmakuStyle(),
    val autoUpdate: Boolean = true,
    val updateSource: UpdateSource = UpdateSource.Proxy,
    val heroMode: HeroMode = HeroMode.Auto,
)

class AppSettings(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val color = stringPreferencesKey("color_source")
        val amoled = booleanPreferencesKey("amoled")
        val decoder = stringPreferencesKey("decoder")
        val skip = stringPreferencesKey("skip_mode")
        val autoNext = booleanPreferencesKey("auto_next")
        val speed = floatPreferencesKey("default_speed")
        val serverColor = stringPreferencesKey("server_theme_color")
        val serverDark = stringPreferencesKey("server_dark")
        val dmEnabled = booleanPreferencesKey("danmaku_enabled")
        val dmRows = intPreferencesKey("danmaku_rows")
        val dmDensity = intPreferencesKey("danmaku_density")
        val dmFont = intPreferencesKey("danmaku_font_size")
        val dmOpacity = intPreferencesKey("danmaku_opacity")
        val dmSpeed = floatPreferencesKey("danmaku_speed")
        val dmTheme = booleanPreferencesKey("danmaku_theme_color")
        val autoUpdate = booleanPreferencesKey("auto_update")
        val updateSource = stringPreferencesKey("update_source")
        val heroMode = stringPreferencesKey("hero_mode")
    }

    val flow: Flow<Settings> = context.settingsDataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = Settings(
        themeMode = enumOr(this[Keys.theme], ThemeMode.System),
        colorSource = enumOr(this[Keys.color], ColorSource.Server),
        amoled = this[Keys.amoled] ?: false,
        decoder = enumOr(this[Keys.decoder], DecoderPreference.Auto),
        skipMode = enumOr(this[Keys.skip], SkipMode.Button),
        autoPlayNext = this[Keys.autoNext] ?: true,
        defaultSpeed = this[Keys.speed] ?: 1f,
        serverThemeColor = this[Keys.serverColor],
        serverDarkMode = this[Keys.serverDark]?.toBooleanStrictOrNull(),
        danmaku = DanmakuStyle().let { d ->
            DanmakuStyle(
                enabled = this[Keys.dmEnabled] ?: d.enabled,
                rows = (this[Keys.dmRows] ?: d.rows).coerceIn(DanmakuStyle.Rows),
                density = (this[Keys.dmDensity] ?: d.density).coerceIn(DanmakuStyle.Density),
                fontSize = (this[Keys.dmFont] ?: d.fontSize).coerceIn(DanmakuStyle.FontSize),
                opacity = (this[Keys.dmOpacity] ?: d.opacity).coerceIn(DanmakuStyle.Opacity),
                speed = (this[Keys.dmSpeed] ?: d.speed).coerceIn(DanmakuStyle.Speed),
                themeColor = this[Keys.dmTheme] ?: d.themeColor,
            )
        },
        autoUpdate = this[Keys.autoUpdate] ?: true,
        updateSource = enumOr(this[Keys.updateSource], UpdateSource.Proxy),
        heroMode = enumOr(this[Keys.heroMode], HeroMode.Auto),
    )

    suspend fun setTheme(mode: ThemeMode) = context.settingsDataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setColorSource(source: ColorSource) = context.settingsDataStore.edit { it[Keys.color] = source.name }
    suspend fun setAmoled(value: Boolean) = context.settingsDataStore.edit { it[Keys.amoled] = value }
    suspend fun setDecoder(value: DecoderPreference) = context.settingsDataStore.edit { it[Keys.decoder] = value.name }
    suspend fun setSkipMode(value: SkipMode) = context.settingsDataStore.edit { it[Keys.skip] = value.name }
    suspend fun setAutoPlayNext(value: Boolean) = context.settingsDataStore.edit { it[Keys.autoNext] = value }
    suspend fun setDefaultSpeed(value: Float) = context.settingsDataStore.edit { it[Keys.speed] = value }

    suspend fun setHeroMode(value: HeroMode) = context.settingsDataStore.edit { it[Keys.heroMode] = value.name }

    suspend fun setAutoUpdate(value: Boolean) = context.settingsDataStore.edit { it[Keys.autoUpdate] = value }
    suspend fun setUpdateSource(value: UpdateSource) = context.settingsDataStore.edit { it[Keys.updateSource] = value.name }

    suspend fun setDanmaku(style: DanmakuStyle) = context.settingsDataStore.edit {
        it[Keys.dmEnabled] = style.enabled
        it[Keys.dmRows] = style.rows
        it[Keys.dmDensity] = style.density
        it[Keys.dmFont] = style.fontSize
        it[Keys.dmOpacity] = style.opacity
        it[Keys.dmSpeed] = style.speed
        it[Keys.dmTheme] = style.themeColor
    }

    /** Cache of the account's server-side appearance so the next cold start paints the right colors. */
    suspend fun setServerAppearance(color: String?, dark: Boolean?) = context.settingsDataStore.edit {
        if (color.isNullOrBlank()) it.remove(Keys.serverColor) else it[Keys.serverColor] = color
        if (dark == null) it.remove(Keys.serverDark) else it[Keys.serverDark] = dark.toString()
    }

    private inline fun <reified T : Enum<T>> enumOr(value: String?, fallback: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
}
