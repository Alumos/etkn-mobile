package xyz.etkn.mobile.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import xyz.etkn.mobile.ui.theme.Corner

/**
 * The app's one selectable pill: every choice row, filter, range and multi-pick uses it, so they
 * all share one height, shape and pair of colours. Fully round in every state (the Expressive
 * defaults morph a checked toggle squarer). Unchecked pills are tonal rather than transparent, so
 * a row's first pill visibly starts where its label does. Rows of these sit [PillGap] apart.
 */
@Composable
fun PillToggle(
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    ToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        modifier = modifier,
        enabled = enabled,
        shapes = PillShapes,
        colors = ToggleButtonDefaults.colors(
            containerColor = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
            checkedContainerColor = scheme.primary,
            checkedContentColor = scheme.onPrimary,
        ),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        }
        content()
    }
}

val PillGap = 6.dp

private val PillShapes = ToggleButtonShapes(shape = Corner.pill, pressedShape = Corner.pill, checkedShape = Corner.pill)
