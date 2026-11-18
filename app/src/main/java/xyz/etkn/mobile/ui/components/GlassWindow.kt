package xyz.etkn.mobile.ui.components

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider

/*
 * Dialogs and sheets live in their own windows, which an in-app blur (Haze) cannot sample. On
 * Android 12+ the compositor blurs everything behind such a window instead, and the container
 * turns translucent so it reads as glass; elsewhere both stay as they were.
 */

private fun View.dialogWindow(): Window? {
    var current: Any? = this
    while (current is View) {
        if (current is DialogWindowProvider) return current.window
        current = current.parent
    }
    return null
}

/** True when this device composites cross-window blur right now (off in battery saver on some builds). */
@Composable
fun rememberWindowBlur(): Boolean {
    val view = LocalView.current
    return remember(view) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && view.context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
    }
}

/** Call from inside a dialog / sheet: blurs the app behind its window. */
@Composable
fun BlurBehindWindow(radius: Dp = 28.dp) {
    val view = LocalView.current
    val px = with(LocalDensity.current) { radius.roundToPx() }
    DisposableEffect(view, px) {
        val window = view.dialogWindow()
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && window.windowManager.isCrossWindowBlurEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                blurBehindRadius = px
                // The blur already separates the layers; a lighter dim keeps the backdrop readable.
                dimAmount = minOf(dimAmount, 0.32f)
            }
        }
        onDispose { }
    }
}

/** Container colour for windows that may sit on a blurred backdrop. */
@Composable
fun glassContainer(base: Color = MaterialTheme.colorScheme.surfaceContainerHigh): Color =
    if (rememberWindowBlur()) base.copy(alpha = 0.82f) else base

/** [ModalBottomSheet] with the backdrop blurred and a translucent sheet. */
@Composable
fun GlassSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = glassContainer(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        BlurBehindWindow()
        content()
    }
}

/** [AlertDialog] with the backdrop blurred and a translucent card. */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            BlurBehindWindow()
            confirmButton()
        },
        modifier = modifier,
        dismissButton = dismissButton,
        title = title,
        text = text,
        containerColor = glassContainer(),
    )
}
