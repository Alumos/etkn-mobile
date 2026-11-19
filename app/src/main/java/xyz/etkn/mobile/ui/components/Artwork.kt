package xyz.etkn.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import xyz.etkn.mobile.data.api.ImageUrls
import xyz.etkn.mobile.ui.theme.Etkn

/** Artwork URL builder for the signed-in account (null while signed out). */
val LocalImages = staticCompositionLocalOf<ImageUrls?> { null }

@Composable
fun images(): ImageUrls = LocalImages.current ?: error("ImageUrls not provided")

/** Converts a layout width to physical pixels for server-side resizing. */
@Composable
fun Dp.px(): Int = with(LocalDensity.current) { this@px.roundToPx() }

/**
 * Remote artwork. The placeholder (skeleton tint + optional glyph) sits *under* the image, so
 * there is no extra composition for loading / error states: a failed load simply keeps it.
 */
@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: ImageVector? = null,
    placeholderTint: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
    background: Color = Etkn.tokens.skeleton,
    alignment: Alignment = Alignment.Center,
) {
    Box(modifier.background(background), contentAlignment = Alignment.Center) {
        if (placeholder != null) {
            Icon(placeholder, contentDescription = null, tint = placeholderTint, modifier = Modifier.size(28.dp))
        }
        if (url != null) {
            val context = LocalContext.current
            val request = remember(url) { ImageRequest.Builder(context).data(url).build() }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = contentScale,
                alignment = alignment,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
