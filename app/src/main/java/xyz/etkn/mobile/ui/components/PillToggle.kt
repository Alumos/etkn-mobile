package xyz.etkn.mobile.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * One option of a choice row: a fully rounded pill in every state. The Expressive defaults morph a
 * checked toggle into a squarer shape and connected groups square off the inner corners, which
 * reads as uneven next to the app's other pills. Rows of these sit [PillGap] apart.
 */
@Composable
fun PillToggle(checked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    ToggleButton(checked = checked, onCheckedChange = { onClick() }, modifier = modifier, shapes = PillShapes) { content() }
}

val PillGap = 6.dp

private val PillShapes = ToggleButtonShapes(shape = CircleShape, pressedShape = CircleShape, checkedShape = CircleShape)
