package xyz.etkn.mobile.ui.screens.me

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SwitchAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import xyz.etkn.mobile.BuildConfig
import xyz.etkn.mobile.data.session.SessionState
import xyz.etkn.mobile.data.session.StoredSessions
import xyz.etkn.mobile.data.settings.ColorSource
import xyz.etkn.mobile.data.settings.DecoderPreference
import xyz.etkn.mobile.data.settings.Settings
import xyz.etkn.mobile.data.settings.SkipMode
import xyz.etkn.mobile.data.settings.ThemeMode
import xyz.etkn.mobile.graph
import xyz.etkn.mobile.ui.components.Artwork
import xyz.etkn.mobile.ui.components.images
import xyz.etkn.mobile.ui.nav.AccountsRoute
import xyz.etkn.mobile.ui.nav.FavoritesRoute
import xyz.etkn.mobile.ui.nav.LocalBottomInset
import xyz.etkn.mobile.ui.nav.LocalNav
import xyz.etkn.mobile.ui.screens.connect.ConnectScreen
import xyz.etkn.mobile.ui.screens.library.LargeTitle
import xyz.etkn.mobile.ui.theme.Etkn

@Composable
fun MeScreen() {
    val graph = LocalContext.current.graph
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val settings by graph.settings.flow.collectAsStateWithLifecycle(initialValue = Settings())
    val session = (graph.sessions.state.collectAsStateWithLifecycle().value as? SessionState.SignedIn)?.session ?: return
    var confirmLogout by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomInset.current)) {
        item { LargeTitle("我的") }
        item {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Etkn.space.screen, vertical = 12.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(images().user(tag = session.imageTag, widthPx = 240), Modifier.size(64.dp).clip(CircleShape), session.userName, placeholder = Icons.Rounded.Person)
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text(session.userName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            session.serverName + if (session.isAdmin) " · 管理员" else "",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        )
                        Text(session.server, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item {
            Group {
                Entry(Icons.Rounded.Favorite, "我的收藏") { nav.to(FavoritesRoute) }
                Entry(Icons.Rounded.SwitchAccount, "切换账号") { nav.to(AccountsRoute) }
                Entry(Icons.AutoMirrored.Rounded.Logout, "退出登录", tint = MaterialTheme.colorScheme.error) { confirmLogout = true }
            }
        }
        item { Caption("外观") }
        item {
            ChoiceRow("主题", ThemeMode.entries, settings.themeMode, { it.label }) { scope.launch { graph.settings.setTheme(it) } }
            val sources = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ColorSource.entries else listOf(ColorSource.Server)
            if (sources.size > 1) ChoiceRow("配色", sources, settings.colorSource, { it.label }) { scope.launch { graph.settings.setColorSource(it) } }
            SwitchRow("纯黑深色", settings.amoled) { scope.launch { graph.settings.setAmoled(it) } }
        }
        item { Caption("播放") }
        item {
            ChoiceRow("解码", DecoderPreference.entries, settings.decoder, { it.label }) { scope.launch { graph.settings.setDecoder(it) } }
            ChoiceRow("片头片尾", SkipMode.entries, settings.skipMode, { it.label }) { scope.launch { graph.settings.setSkipMode(it) } }
            ChoiceRow("默认倍速", listOf(1f, 1.25f, 1.5f, 2f), settings.defaultSpeed, { "${it}×" }) { scope.launch { graph.settings.setDefaultSpeed(it) } }
            SwitchRow("自动播放下一集", settings.autoPlayNext) { scope.launch { graph.settings.setAutoPlayNext(it) } }
        }
        item {
            Text(
                "ETKN Mobile ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("退出登录？") },
            text = { Text("将退出 ${session.userName} 在 ${session.serverName} 上的登录。") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; graph.music.stop(); scope.launch { graph.sessions.logout() } }) { Text("退出") } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Etkn.space.screen + 4.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Etkn.space.screen, vertical = 4.dp),
    ) { Column { content() } }
}

@Composable
private fun Entry(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label, color = tint) },
        leadingContent = { Icon(icon, null, tint = tint) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Connected button group (M3E replacement for segmented buttons). */
@Composable
private fun <T> ChoiceRow(label: String, options: List<T>, selected: T, name: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Etkn.space.screen, vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
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
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = Etkn.space.screen + 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun AccountsScreen() {
    val graph = LocalContext.current.graph
    val nav = LocalNav.current
    val stored by graph.sessions.accounts.collectAsStateWithLifecycle(initialValue = StoredSessions())
    var adding by remember { mutableStateOf(false) }
    if (adding) {
        ConnectScreen(onDone = { adding = false; nav.back() }, onCancel = { adding = false })
        return
    }
    val current = graph.sessions.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("切换账号") },
            navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
        Group {
            stored.accounts.forEach { account ->
                val active = account.key == current?.key
                ListItem(
                    headlineContent = { Text(account.userName) },
                    supportingContent = { Text(account.serverName + if (account.sealedToken == null && !active) " · 需要密码" else "") },
                    leadingContent = { Icon(Icons.Rounded.Person, null) },
                    trailingContent = { if (active) Text("当前", color = MaterialTheme.colorScheme.primary) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(enabled = !active) {
                        scope.launch {
                            val ok = runCatching { graph.sessions.switchTo(account) }.getOrDefault(false)
                            if (!ok) { message = "该账号需要重新输入密码"; adding = true }
                        }
                    },
                )
            }
            ListItem(
                headlineContent = { Text("添加账号或服务器") },
                leadingContent = { Icon(Icons.Rounded.SwitchAccount, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { adding = true },
            )
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(Etkn.space.screen)) }
        Spacer(Modifier.height(LocalBottomInset.current))
    }
}
