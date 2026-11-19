package xyz.etkn.mobile.ui.screens.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.etkn.mobile.data.downloads.DownloadEntry
import xyz.etkn.mobile.data.downloads.DownloadProgress
import xyz.etkn.mobile.data.update.formatBytes
import xyz.etkn.mobile.graph
import xyz.etkn.mobile.player.PlayerLauncher
import xyz.etkn.mobile.ui.common.WhileStarted
import xyz.etkn.mobile.ui.components.GlassBackBar
import xyz.etkn.mobile.ui.components.MessageState
import xyz.etkn.mobile.ui.components.glassBarClearance
import xyz.etkn.mobile.ui.components.glassSource
import xyz.etkn.mobile.ui.nav.LocalBottomInset
import xyz.etkn.mobile.ui.nav.LocalNav

/** This account's downloads: progress while running, tap a finished one to play it offline. */
@Composable
fun DownloadsScreen() {
    val context = LocalContext.current
    val graph = context.graph
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val haze = rememberHazeState()
    val all by graph.downloads.entries.collectAsStateWithLifecycle()
    val session = graph.sessions.current
    val entries = remember(all, session) { session?.let(graph.downloads::of).orEmpty().reversed() }
    var progress by remember { mutableStateOf<Map<Long, DownloadProgress>>(emptyMap()) }
    var deleting by remember { mutableStateOf<DownloadEntry?>(null) }
    WhileStarted(entries) {
        while (true) {
            progress = graph.downloads.progress(entries)
            delay(1_000)
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (entries.isEmpty()) {
            MessageState("还没有下载内容", icon = Icons.Rounded.Download)
        } else {
            LazyColumn(
                Modifier.fillMaxSize().glassSource(haze),
                contentPadding = PaddingValues(top = glassBarClearance(), bottom = LocalBottomInset.current + 24.dp),
            ) {
                items(entries, key = { it.downloadId }) { entry ->
                    DownloadRow(
                        entry, progress[entry.downloadId],
                        onPlay = { PlayerLauncher.playDownload(context, entry) },
                        onDelete = { deleting = entry },
                    )
                }
            }
        }
        GlassBackBar("下载", collapsed = true, haze = haze, onBack = nav::back)
    }

    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除下载") },
            text = { Text("「${listOf(entry.title, entry.subtitle).filter { it.isNotEmpty() }.joinToString(" ")}」的本地文件会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { graph.downloads.remove(entry) }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun DownloadRow(entry: DownloadEntry, progress: DownloadProgress?, onPlay: () -> Unit, onDelete: () -> Unit) {
    val done = progress?.status == DownloadProgress.Status.Done
    Row(
        Modifier.fillMaxWidth().clickable(enabled = done, onClick = onPlay).padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (entry.subtitle.isNotEmpty()) {
                Text(entry.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val status = when (progress?.status) {
                null, DownloadProgress.Status.Waiting -> "等待中"
                DownloadProgress.Status.Running -> "${(progress.fraction * 100).toInt()}% · ${formatBytes(progress.done)} / ${formatBytes(progress.total)}"
                DownloadProgress.Status.Paused -> "已暂停，等待网络"
                DownloadProgress.Status.Done -> formatBytes(progress.total)
                DownloadProgress.Status.Failed -> "下载失败"
            }
            Text(
                status, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 2.dp),
                color = if (progress?.status == DownloadProgress.Status.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (progress?.status == DownloadProgress.Status.Running) {
                LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), drawStopIndicator = {})
            }
        }
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
