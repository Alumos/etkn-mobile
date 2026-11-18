package xyz.etkn.mobile.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import xyz.etkn.mobile.data.model.Item
import xyz.etkn.mobile.ui.theme.Etkn
import xyz.etkn.mobile.ui.theme.HeroTitleStyle

/**
 * Home hero: one focal point per screen. Full-bleed backdrop, logo (or title), a single meta
 * line and two actions. Parallax is a pure graphicsLayer transform so scrolling never relayouts.
 */
@Composable
fun HeroCarousel(
    items: List<Item>,
    height: Dp,
    scrollOffsetPx: () -> Int,
    onPlay: (Item) -> Unit,
    onOpen: (Item) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val pager = rememberPagerState { items.size }
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(items.size, dragged) {
        if (dragged || items.size < 2) return@LaunchedEffect
        while (true) {
            delay(7_000)
            pager.animateScrollToPage((pager.currentPage + 1) % items.size)
        }
    }
    val widthPx = LocalConfiguration.current.screenWidthDp.dp.px()
    Box(modifier.fillMaxWidth().height(height)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { items[it].id }) { page ->
            val item = items[page]
            HeroPage(item, widthPx, scrollOffsetPx, onPlay, onOpen)
        }
        if (items.size > 1) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(end = Etkn.space.screen, bottom = 34.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(items.size) { index ->
                    val selected = index == pager.currentPage
                    val w by animateDpAsState(if (selected) 18.dp else 6.dp, label = "dot")
                    Box(
                        Modifier.height(6.dp).width(w).background(
                            if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.45f),
                            CircleShape,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroPage(
    item: Item,
    widthPx: Int,
    scrollOffsetPx: () -> Int,
    onPlay: (Item) -> Unit,
    onOpen: (Item) -> Unit,
) {
    val urls = images()
    val tokens = Etkn.tokens
    Box(Modifier.fillMaxSize()) {
        Artwork(
            urls.backdrop(item, widthPx) ?: urls.poster(item, widthPx),
            Modifier.fillMaxSize().graphicsLayer {
                val offset = scrollOffsetPx().toFloat()
                translationY = offset * 0.45f
                val scale = 1f + (offset / size.height.coerceAtLeast(1f)) * 0.08f
                scaleX = scale
                scaleY = scale
            },
            item.name,
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxWidth().height(180.dp).background(tokens.heroTopScrim))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(300.dp).background(tokens.heroBottomScrim))
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .padding(horizontal = Etkn.space.screen).padding(bottom = 24.dp),
        ) {
            val logo = urls.logo(item, 720)
            if (logo != null) {
                Artwork(
                    logo,
                    Modifier.fillMaxWidth(0.62f).heightIn(max = 96.dp).height(84.dp),
                    item.name,
                    contentScale = ContentScale.Fit,
                    background = Color.Transparent,
                )
            } else {
                Text(
                    item.name,
                    style = HeroTitleStyle.copy(shadow = Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 16f)),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val meta = listOfNotNull(
                item.productionYear?.toString(),
                item.genres.take(2).joinToString(" / ").takeIf { it.isNotEmpty() },
                item.runtimeMs.takeIf { it > 0 }?.let(::formatDuration),
                formatRating(item.bestRating)?.let { "★ $it" },
            ).joinToString("  ·  ")
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    meta, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            item.overview?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(0.88f),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { onPlay(item) },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier.height(ButtonDefaults.MediumContainerHeight),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("播放")
                }
                FilledTonalButton(
                    onClick = { onOpen(item) },
                    modifier = Modifier.height(ButtonDefaults.MediumContainerHeight),
                ) {
                    Icon(Icons.Rounded.Info, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("详情")
                }
            }
        }
    }
}
