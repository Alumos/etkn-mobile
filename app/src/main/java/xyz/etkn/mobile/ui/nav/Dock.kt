package xyz.etkn.mobile.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import xyz.etkn.mobile.ui.components.glass
import androidx.compose.runtime.getValue

/**
 * Floating glass dock. The selected tab sits in one tonal pill that wraps both icon and label and
 * slides between tabs with the expressive spatial spring (the stock bar only rings the icon).
 */
@Composable
internal fun Dock(tabs: List<TopLevel>, current: TopLevel?, nav: AppNav) {
    val colors = MaterialTheme.colorScheme
    val selected = tabs.indexOf(current)
    val slide by animateFloatAsState(selected.coerceAtLeast(0).toFloat(), MaterialTheme.motionScheme.fastSpatialSpec(), label = "dockPill")
    val pillShape = RoundedCornerShape(20.dp)
    BoxWithConstraints(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp), elevation = 14.dp)
            .height(68.dp).padding(6.dp).selectableGroup(),
    ) {
        val slot = maxWidth / tabs.size
        if (selected >= 0) {
            Box(
                Modifier.offset { IntOffset((slot.toPx() * slide).roundToInt(), 0) }
                    .width(slot).fillMaxHeight().padding(horizontal = 3.dp)
                    .background(colors.secondaryContainer, pillShape),
            )
        }
        Row(Modifier.fillMaxSize()) {
            tabs.forEach { tab ->
                val active = tab == current
                val tint by animateColorAsState(if (active) colors.onSecondaryContainer else colors.onSurfaceVariant, label = "dockTint")
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(pillShape)
                        .selectable(selected = active, role = Role.Tab, onClick = { nav.top(tab) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(if (active) tab.selectedIcon else tab.icon, null, tint = tint)
                    Text(
                        tab.label, color = tint, maxLines = 1,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}
