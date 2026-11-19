package xyz.etkn.mobile.data.events

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.etkn.mobile.data.net.EtknJson
import xyz.etkn.mobile.data.net.bool
import xyz.etkn.mobile.data.net.str
import xyz.etkn.mobile.data.session.ActiveSession
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** What `U/Home/Events` reports: the home revision moved, or rows of one detail changed. */
sealed interface ServerEvent {
    data class Home(val revision: String) : ServerEvent

    /** [initial] is the snapshot sent on connect, not a change. */
    data class Items(val initial: Boolean) : ServerEvent
}

/**
 * The server's change feed (SSE): no polling while the server is quiet. The stream carries no
 * media data, only that something changed; screens re-read through the normal routes. Connects
 * while collected, reconnects 3 s after a drop, and ends (expiring the session) on
 * `auth-expired` or HTTP 401.
 */
class ServerEvents(
    private val http: OkHttpClient,
    private val session: () -> ActiveSession?,
    private val onExpired: (ActiveSession) -> Unit,
) {
    fun stream(itemId: String? = null): Flow<ServerEvent> = channelFlow {
        while (isActive) {
            val active = session() ?: break
            val request = request(active, itemId) ?: break
            val call = http.newCall(request)
            val end = try {
                withContext(Dispatchers.IO) {
                    val cancel = coroutineContext.job.invokeOnCompletion { call.cancel() }
                    try {
                        read(call.execute(), active) { send(it) }
                    } finally {
                        cancel.dispose()
                    }
                }
            } catch (_: IOException) {
                false
            }
            if (end) break
            delay(RETRY_MS)
        }
    }

    private fun request(active: ActiveSession, itemId: String?): Request? {
        val url = "${active.server.trimEnd('/')}/api/tv/Users/${active.userId}/Home/Events".toHttpUrlOrNull()?.newBuilder()
            ?.apply { if (itemId != null) addQueryParameter("ItemId", itemId) }
            ?.build() ?: return null
        return Request.Builder().url(url)
            .header("Accept", "text/event-stream")
            .header("X-Emby-Token", active.token)
            .build()
    }

    /** Reads one connection to its end; true when the session is over and nothing should retry. */
    private suspend fun read(response: okhttp3.Response, active: ActiveSession, emit: suspend (ServerEvent) -> Unit): Boolean = response.use {
        if (it.code == 401) {
            onExpired(active)
            return true
        }
        if (!it.isSuccessful || it.header("Content-Type")?.startsWith("text/event-stream") != true) return false
        val source = it.body.source()
        var event = ""
        val data = StringBuilder()
        while (coroutineContext.isActive) {
            val line = source.readUtf8Line() ?: return false
            when {
                line.isEmpty() -> {
                    if (event == "auth-expired") {
                        onExpired(active)
                        return true
                    }
                    parse(event, data.toString())?.let { e -> emit(e) }
                    event = ""
                    data.clear()
                }
                line.startsWith(":") -> Unit
                line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                line.startsWith("data:") -> data.append(line.removePrefix("data:").trim())
            }
        }
        false
    }

    private fun parse(event: String, data: String): ServerEvent? {
        val json = runCatching { EtknJson.parseToJsonElement(data) as JsonObject }.getOrNull() ?: return null
        return when (event) {
            "home" -> ServerEvent.Home(json.str("revision"))
            "items" -> ServerEvent.Items(initial = json.bool("Initial") && !json.changed())
            else -> null
        }
    }

    private fun JsonObject.changed() = listOf("Updated", "Added", "Removed").any { key -> runCatching { this[key]!!.jsonArray.isNotEmpty() }.getOrDefault(false) }

    private companion object {
        const val RETRY_MS = 3_000L
    }
}
