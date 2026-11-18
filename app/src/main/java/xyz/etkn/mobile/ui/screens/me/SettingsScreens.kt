package xyz.etkn.mobile.ui.screens.me

import android.app.Activity
import xyz.etkn.mobile.ui.theme.LocalSettings
import xyz.etkn.mobile.ui.components.glassBarClearance
import xyz.etkn.mobile.ui.components.glassSource
import xyz.etkn.mobile.ui.components.GlassBackBar
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.FlowRow
import xyz.etkn.mobile.ui.common.LocalWindowLayout
import xyz.etkn.mobile.ui.nav.DOCK_MAX
import xyz.etkn.mobile.ui.nav.visibleTabs
import xyz.etkn.mobile.ui.nav.TopLevel
import xyz.etkn.mobile.data.settings.NavPosition
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import xyz.etkn.mobile.data.settings.ColorSource
import xyz.etkn.mobile.data.settings.DanmakuStyle
import xyz.etkn.mobile.data.settings.DecoderPreference
import xyz.etkn.mobile.data.settings.Settings
import xyz.etkn.mobile.data.settings.SkipMode
import xyz.etkn.mobile.data.settings.UiScale
import xyz.etkn.mobile.data.settings.HeroMode
import xyz.etkn.mobile.data.settings.ThemeMode
import xyz.etkn.mobile.graph
import xyz.etkn.mobile.ui.nav.LocalBottomInset
import xyz.etkn.mobile.ui.nav.LocalNav
import xyz.etkn.mobile.ui.theme.Etkn
import kotlin.math.roundToInt
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

enum class SettingsPage(val title: String) { Appearance("外观"), Playback("播放"), Danmaku("弹幕"), Update("检查更新") }

@Composable
fun SettingsScreen(page: SettingsPage) {
    val graph = LocalContext.current.graph
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val settings = LocalSettings.current
    val haze = rememberHazeState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
        Column(Modifier.fillMaxSize().glassSource(haze).verticalScroll(rememberScrollState()).padding(top = glassBarClearance())) {
            SettingsGroup {
                when (page) {
                    SettingsPage.Appearance -> {
                        ChoiceRow("主题", ThemeMode.entries, settings.themeMode, { it.label }) { scope.launch { graph.settings.setTheme(it) } }
                        val sources = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ColorSource.entries else listOf(ColorSource.Server)
                        if (sources.size > 1) {
                            ChoiceRow("配色", sources, settings.colorSource, { it.label }) { scope.launch { graph.settings.setColorSource(it) } }
                        }
                        SwitchRow("纯黑深色", settings.amoled) { scope.launch { graph.settings.setAmoled(it) } }
                        val context = LocalContext.current
                        val scale = remember { UiScale.read(context) }
                        ChoiceRow("页面大小", UiScale.entries, scale, { it.label }) {
                            if (it != scale) {
                                UiScale.write(context, it)
                                (context as? Activity)?.recreate()
                            }
                        }
                        ChoiceRow("首页推荐", HeroMode.entries, settings.heroMode, { it.label }) { scope.launch { graph.settings.setHeroMode(it) } }
                        val wide = LocalWindowLayout.current.wide
                        if (wide) {
                            ChoiceRow("导航位置", NavPosition.entries, settings.navPosition, { it.label }) { scope.launch { graph.settings.setNavPosition(it) } }
                        }
                        TabPicker(settings, side = wide && settings.navPosition == NavPosition.Side) { scope.launch { graph.settings.setTabs(it) } }
                    }
                    SettingsPage.Playback -> {
                        ChoiceRow("解码", DecoderPreference.entries, settings.decoder, { it.label }) { scope.launch { graph.settings.setDecoder(it) } }
                        ChoiceRow("片头片尾", SkipMode.entries, settings.skipMode, { it.label }) { scope.launch { graph.settings.setSkipMode(it) } }
                        ChoiceRow("默认倍速", listOf(1f, 1.25f, 1.5f, 2f), settings.defaultSpeed, { "${it}×" }) { scope.launch { graph.settings.setDefaultSpeed(it) } }
                        SwitchRow("自动播放下一集", settings.autoPlayNext) { scope.launch { graph.settings.setAutoPlayNext(it) } }
                    }
                    SettingsPage.Danmaku -> DanmakuControls(settings.danmaku) { scope.launch { graph.settings.setDanmaku(it) } }
                    SettingsPage.Update -> UpdateSettings(settings)
                }
            }
            Spacer(Modifier.height(LocalBottomInset.current + 16.dp))
        }
    }
    Box(Modifier.widthIn(max = 720.dp)) { GlassBackBar(page.title, collapsed = true, haze = haze, onBack = nav::back) }
    }
}

/**
 * Which top-level pages the dock / rail shows. 首页 is fixed; 探索 only appears for accounts with
 * the right; the phone dock stops at [DOCK_MAX].
 */
@Composable
private fun TabPicker(settings: Settings, side: Boolean, onChange: (Set<String>) -> Unit) {
    val canDiscover by LocalContext.current.graph.discover.allowed.collectAsStateWithLifecycle()
    val shown = visibleTabs(settings.tabs, canDiscover, side)
    val limit = if (side) Int.MAX_VALUE else DOCK_MAX
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(if (side) "侧栏" else "底栏", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TopLevel.entries.filter { it != TopLevel.Discover || canDiscover }.forEach { tab ->
                val on = tab in shown
                FilterChip(
                    selected = on,
                    enabled = tab != TopLevel.Home && (on || shown.size < limit),
                    onClick = {
                        val next = if (on) shown - tab else shown + tab
                        onChange(next.filter { it != TopLevel.Home }.mapTo(HashSet()) { it.name })
                    },
                    label = { Text(tab.label) },
                    leadingIcon = { Icon(if (on) tab.selectedIcon else tab.icon, null, Modifier.size(18.dp)) },
                )
            }
        }
    }
}

/** Danmaku switches and sliders; shared by the settings page and the player sheet. */
@Composable
fun DanmakuControls(style: DanmakuStyle, onChange: (DanmakuStyle) -> Unit) {
    SwitchRow("显示弹幕", style.enabled) { onChange(style.copy(enabled = it)) }
    SwitchRow("跟随主题色", style.themeColor) { onChange(style.copy(themeColor = it)) }
    SliderRow("行数", style.rows.toFloat(), DanmakuStyle.Rows.first.toFloat()..DanmakuStyle.Rows.last.toFloat(), 1f, { it.roundToInt().toString() }) {
        onChange(style.copy(rows = it.roundToInt()))
    }
    SliderRow("密度", style.density.toFloat(), DanmakuStyle.Density.first.toFloat()..DanmakuStyle.Density.last.toFloat(), 10f, { "${it.roundToInt()}%" }) {
        onChange(style.copy(density = it.roundToInt()))
    }
    SliderRow("字号", style.fontSize.toFloat(), DanmakuStyle.FontSize.first.toFloat()..DanmakuStyle.FontSize.last.toFloat(), 1f, { it.roundToInt().toString() }) {
        onChange(style.copy(fontSize = it.roundToInt()))
    }
    SliderRow("不透明度", style.opacity.toFloat(), DanmakuStyle.Opacity.first.toFloat()..DanmakuStyle.Opacity.last.toFloat(), 5f, { "${it.roundToInt()}%" }) {
        onChange(style.copy(opacity = it.roundToInt()))
    }
    SliderRow("速度", style.speed, DanmakuStyle.Speed, 0.25f, { "${(it * 100).roundToInt() / 100f}×" }) {
        onChange(style.copy(speed = it))
    }
}

@Composable
internal fun SettingsGroup(horizontal: Dp = Etkn.space.screen, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontal, vertical = 4.dp),
    ) { Column(Modifier.padding(vertical = 8.dp)) { content() } }
}

/** Connected button group (M3E replacement for segmented buttons). */
@Composable
internal fun <T> ChoiceRow(label: String, options: List<T>, selected: T, name: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            options.forEachIndexed { index, option ->
                ToggleButton(
                    checked = option == selected,
                    onCheckedChange = { onSelect(option) },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    modifier = Modifier.semantics { role = Role.RadioButton },
                ) { Text(name(option), maxLines = 1) }
            }
        }
    }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Slider that previews locally while dragging and commits once on release. */
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    onCommit: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    val steps = (((range.endInclusive - range.start) / step).roundToInt() - 1).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(format(local), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local) },
            valueRange = range,
            steps = steps,
        )
    }
}
