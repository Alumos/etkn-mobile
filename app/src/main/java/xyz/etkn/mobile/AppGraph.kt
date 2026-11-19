package xyz.etkn.mobile

import xyz.etkn.mobile.data.session.SessionState
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import xyz.etkn.mobile.data.net.ResponseCache
import android.app.Application
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import xyz.etkn.mobile.data.api.EtknApi
import xyz.etkn.mobile.data.api.ImageUrls
import xyz.etkn.mobile.data.api.ServerProbe
import xyz.etkn.mobile.data.net.ApiClient
import xyz.etkn.mobile.data.net.ClientIdentity
import xyz.etkn.mobile.data.net.HttpClients
import xyz.etkn.mobile.data.net.str
import xyz.etkn.mobile.data.session.ActiveSession
import xyz.etkn.mobile.data.session.SessionManager
import xyz.etkn.mobile.data.session.SessionStore
import xyz.etkn.mobile.data.settings.AppSettings
import xyz.etkn.mobile.data.discover.DiscoverStore
import xyz.etkn.mobile.data.update.UpdateManager
import xyz.etkn.mobile.music.MusicConnection
import java.util.UUID

/** Manual dependency graph: a handful of long-lived singletons, no reflection, no DI startup cost. */
/** [deleted] = the item is gone (whole delete); otherwise its data changed and should be re-read. */
data class ItemChange(val id: String, val deleted: Boolean = false)

class AppGraph(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val identity = ClientIdentity(
        clientName = CLIENT_NAME,
        device = Build.MODEL ?: "Android",
        deviceId = deviceId(app),
        version = BuildConfig.VERSION_NAME,
    )

    val settings = AppSettings(app)
    private val sessionStore = SessionStore(app)

    val http = HttpClients(
        currentServer = { sessions.current?.server },
        currentToken = { sessions.current?.token },
        userAgent = identity.userAgent,
    )

    val sessions: SessionManager = SessionManager(sessionStore, ServerProbe(http.probe, identity.userAgent), scope)

    /** Last good responses for instant start-up screens ([xyz.etkn.mobile.data.net.ResponseCache]). */
    val responses = ResponseCache(java.io.File(app.cacheDir, "api"))

    val api = EtknApi(
        client = ApiClient(http.api, identity, responses),
        session = { sessions.require() },
        onExpired = { stale -> if (sessions.current?.generation == stale.generation) sessions.expire() },
    )

    val music by lazy { MusicConnection(app, this) }

    val updates by lazy { UpdateManager(app, http.external, scope) }

    val discover = DiscoverStore()

    /**
     * Item-level changes made from this device (favourite, metadata, images, delete …). Lists
     * patch or drop the row, detail pages reload; nothing polls.
     */
    val itemChanges = MutableSharedFlow<ItemChange>(extraBufferCapacity = 16)

    init {
        sessions.api = api
        scope.launch(Dispatchers.IO) { responses.prune() }
        // Nothing of a signed-out account stays on disk.
        scope.launch { sessions.state.collect { if (it is SessionState.SignedOut) withContext(Dispatchers.IO) { responses.clear() } } }
    }

    /**
     * Cache mode for start-up screens: stored only for accounts that may persist data
     * (private accounts live in memory only).
     */
    fun cacheUse(mode: ResponseCache.Mode): kotlin.coroutines.CoroutineContext =
        if (sessions.current?.privateAccount == false) ResponseCache.Use(mode) else kotlin.coroutines.EmptyCoroutineContext

    fun images(session: ActiveSession): ImageUrls = ImageUrls(session.server, session.userId)

    /** Pulls the account's shared appearance (theme colour / mode) used by the TV and Web clients. */
    suspend fun syncServerAppearance() {
        val prefs = runCatching { api.clientPreferences() }.getOrNull() ?: return
        val color = prefs.str("theme_color").ifEmpty { null }
        val mode = prefs.str("mode").ifEmpty { null }
        settings.setServerAppearance(color, mode?.let { it == "dark" })
    }

    companion object {
        const val CLIENT_NAME = "ETKN Mobile"

        private fun deviceId(context: Context): String {
            val prefs = context.getSharedPreferences("etkn_device", Context.MODE_PRIVATE)
            prefs.getString("id", null)?.let { return it }
            val created = UUID.randomUUID().toString()
            prefs.edit().putString("id", created).apply()
            return created
        }
    }
}
