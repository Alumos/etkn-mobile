package xyz.etkn.mobile.data.net

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class EtknException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class AuthExpired : EtknException("登录已过期，请重新登录")
    class Redirected : EtknException("服务器地址发生跳转，请使用最终地址登录")
    class Http(val status: Int, detail: String) : EtknException(detail)
    class BadResponse(status: Int) : EtknException("服务响应异常（HTTP $status）")
    class Network(cause: Throwable) : EtknException("无法连接服务器，请检查网络", cause)
    class Unsupported : EtknException("请先更新 ETKN 服务端以支持独立接口")
}

/** Credentials for a single request; JSON calls carry both header and cookie forms like the TV client. */
data class Credentials(val origin: String, val token: String)

/**
 * JSON transport for ETKN `/api/tv` routes.
 *
 * - never follows redirects (a redirect means the stored address is wrong, not something to chase);
 * - an empty body becomes `{}`, a top-level array becomes `{"Items": [...]}`;
 * - 401 maps to [EtknException.AuthExpired] so the session layer can drop the account.
 */
class ApiClient(
    private val http: OkHttpClient,
    private val identity: ClientIdentity,
) {
    suspend fun call(
        credentials: Credentials,
        method: String,
        path: String,
        body: JsonElement? = null,
        raw: RequestBody? = null,
    ): JsonObject {
        val builder = Request.Builder()
            .url(credentials.origin + path)
            .header("Accept", "application/json")
            .header("User-Agent", identity.userAgent)
            .header("X-Emby-Authorization", identity.authorization)
        if (credentials.token.isNotEmpty()) {
            builder.header("X-Emby-Token", credentials.token)
            builder.header("Cookie", "etkn_session=${credentials.token}")
        }
        val payload = raw ?: body?.let { EtknJson.encodeToString(JsonElement.serializer(), it).toRequestBody(JSON_TYPE) }
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> if (payload != null) builder.delete(payload) else builder.delete()
            else -> builder.method(method, payload ?: EMPTY_OBJECT.toRequestBody(JSON_TYPE))
        }
        val call = http.newCall(builder.build())
        return call.await { response -> parse(response, path) }
    }

    private fun parse(response: Response, path: String): JsonObject {
        val status = response.code
        if (status in 300..399) throw EtknException.Redirected()
        val text = response.body.use { body ->
            val source = body.source()
            source.request(MAX_BYTES + 1)
            if (source.buffer.size > MAX_BYTES) throw EtknException.BadResponse(status)
            source.readUtf8()
        }
        val json: JsonObject = try {
            val trimmed = text.trim()
            when {
                trimmed.isEmpty() -> JsonObject(emptyMap())
                trimmed.startsWith("[") -> JsonObject(mapOf("Items" to EtknJson.parseToJsonElement(trimmed) as JsonArray))
                else -> EtknJson.parseToJsonElement(trimmed) as JsonObject
            }
        } catch (error: Exception) {
            if (status == 401) throw EtknException.AuthExpired()
            throw EtknException.BadResponse(status)
        }
        if (status == 404 && path.startsWith("/api/tv/")) throw EtknException.Unsupported()
        if (status == 401) throw EtknException.AuthExpired()
        if (status >= 400) throw EtknException.Http(status, json.str("detail").ifEmpty { "请求失败（HTTP $status）" })
        return json
    }

    companion object {
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val EMPTY_OBJECT = "{}"
        private const val MAX_BYTES = 8L * 1024 * 1024
    }
}

data class ClientIdentity(
    val clientName: String,
    val device: String,
    val deviceId: String,
    val version: String,
) {
    val userAgent: String = "$clientName/$version (Android)"
    val authorization: String =
        "MediaBrowser Client=\"$clientName\", Device=\"${device.replace("\"", "")}\", DeviceId=\"$deviceId\", Version=\"$version\""
}

suspend fun <T> Call.await(block: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isActive) return
            continuation.resumeWithException(EtknException.Network(e))
        }

        override fun onResponse(call: Call, response: Response) {
            val result = runCatching { response.use(block) }
            if (!continuation.isActive) return
            result.fold(continuation::resume) { error ->
                continuation.resumeWithException(
                    if (error is EtknException) error else if (error is IOException) EtknException.Network(error) else error,
                )
            }
        }
    })
}

/** Query-value encoding identical to the TV client (Java URLEncoder, UTF-8). */
fun enc(value: Any?): String = URLEncoder.encode(value?.toString() ?: "", "UTF-8")

/** Builds `path?k=v&…`, skipping null values. */
fun query(path: String, vararg params: Pair<String, Any?>): String {
    val parts = params.mapNotNull { (key, value) -> value?.let { "$key=${enc(it)}" } }
    return if (parts.isEmpty()) path else path + (if ('?' in path) "&" else "?") + parts.joinToString("&")
}
