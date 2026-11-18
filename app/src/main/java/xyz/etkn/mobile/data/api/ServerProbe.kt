package xyz.etkn.mobile.data.api

import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.etkn.mobile.data.model.ServerIdentity
import xyz.etkn.mobile.data.net.EtknJson
import xyz.etkn.mobile.data.net.await
import xyz.etkn.mobile.data.net.int
import xyz.etkn.mobile.data.net.str
import java.net.URI

class ProbeException(message: String) : Exception(message)

/**
 * Strict ETKN identification, same contract as the TV client:
 * `/api/health` → `/api/tv/System/Info/Public` (ProductName=ETKN, TvApiVersion=1) →
 * `/api/system/tv-discovery` to find the media address. A candidate on another address must
 * report the same instance `Id`; plain Emby / Jellyfin servers are rejected.
 */
class ServerProbe(private val http: OkHttpClient, private val userAgent: String) {

    suspend fun resolve(input: String): ServerIdentity {
        val address = normalize(input)
        val original = identify(address)
        val connection = runCatching { read("$address/api/system/tv-discovery") }.getOrNull()
            ?: return original
        if (connection.str("ProductName") != "ETKN") throw ProbeException("请先更新 ETKN 服务端")
        val entered = URI(address)
        val advertisedRaw = connection.str("MediaServerAddress")
        val advertised = advertisedRaw.takeIf { it.isNotEmpty() }?.let { runCatching { URI(normalize(it)) }.getOrNull() }
        var candidate: String
        if (advertised == null || !entered.host.equals(advertised.host, ignoreCase = true)) {
            val enteredPort = if (entered.port >= 0) entered.port else if (entered.scheme == "https") 443 else 80
            if (enteredPort != connection.int("ApiPort", 5257)) return original
            val mediaPort = connection.int("MediaServerPort", -1)
            if (mediaPort <= 0) return original
            candidate = URI(entered.scheme, null, entered.host, mediaPort, entered.path, null, null).toString()
        } else {
            candidate = advertisedRaw
        }
        candidate = normalize(candidate)
        if (candidate == address) return original
        val media = try {
            identify(candidate)
        } catch (error: Exception) {
            throw ProbeException("无法连接媒体服务器，请填写 ETKN 媒体服务器地址")
        }
        if (original.id.isEmpty() || original.id != media.id) throw ProbeException("媒体服务器身份不一致，请核对地址")
        return media
    }

    private suspend fun identify(address: String): ServerIdentity {
        val health = try {
            read("$address/api/health")
        } catch (error: Exception) {
            throw ProbeException("无法连接该地址，请检查地址与网络")
        }
        if (health.str("status") != "ok") throw ProbeException("该地址不是可用的 ETKN")
        val info = read("$address/api/tv/System/Info/Public")
        if (info.str("ProductName") != "ETKN" || info.int("TvApiVersion") != 1) {
            throw ProbeException("请先更新 ETKN 服务端以支持独立接口")
        }
        return ServerIdentity(url = address, name = info.str("ServerName", "ETKN"), id = info.str("Id"))
    }

    private suspend fun read(url: String): JsonObject {
        val request = Request.Builder().url(url).header("Accept", "application/json").header("User-Agent", userAgent).build()
        return http.newCall(request).await { response ->
            if (response.code != 200) throw ProbeException("请检查媒体地址及 ETKN 服务端版本")
            EtknJson.parseToJsonElement(response.body.string()) as? JsonObject
                ?: throw ProbeException("服务响应异常")
        }
    }

    companion object {
        fun normalize(input: String): String {
            var value = input.trim()
            if (!value.contains("://")) value = "http://$value"
            val url = value.toHttpUrlOrNull() ?: throw ProbeException("请输入有效的 ETKN 地址")
            val uri = URI(value)
            if (uri.userInfo != null || uri.query != null || uri.fragment != null || url.host.isEmpty()) {
                throw ProbeException("请输入有效的 ETKN 地址")
            }
            while (value.endsWith("/")) value = value.dropLast(1)
            return value
        }
    }
}
