package xyz.etkn.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.etkn.mobile.data.session.SessionState
import xyz.etkn.mobile.data.settings.Settings
import xyz.etkn.mobile.ui.components.LoadingState
import xyz.etkn.mobile.ui.components.LocalImages
import xyz.etkn.mobile.ui.nav.MainShell
import xyz.etkn.mobile.ui.screens.connect.ConnectScreen
import xyz.etkn.mobile.ui.theme.EtknTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { EtknRoot(graph) }
    }
}

@Composable
fun EtknRoot(graph: AppGraph) {
    val settings by graph.settings.flow.collectAsStateWithLifecycle(initialValue = Settings())
    val state by graph.sessions.state.collectAsStateWithLifecycle()
    EtknTheme(settings) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            AnimatedContent(
                targetState = when (val s = state) {
                    is SessionState.SignedIn -> s.session.key
                    SessionState.SignedOut -> "signed-out"
                    SessionState.Restoring -> "restoring"
                },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "session",
            ) { target ->
                when {
                    target == "restoring" -> LoadingState()
                    target == "signed-out" -> ConnectScreen()
                    else -> {
                        val session = (state as? SessionState.SignedIn)?.session
                        if (session != null && session.key == target) {
                            val urls = remember(session.server, session.userId) { graph.images(session) }
                            key(session.key) {
                                CompositionLocalProvider(LocalImages provides urls) { MainShell(session) }
                            }
                        } else {
                            LoadingState()
                        }
                    }
                }
            }
        }
    }
}
