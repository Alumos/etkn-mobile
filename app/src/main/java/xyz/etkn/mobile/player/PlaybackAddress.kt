package xyz.etkn.mobile.player

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import xyz.etkn.mobile.data.model.MediaSource
import xyz.etkn.mobile.data.net.has
import xyz.etkn.mobile.data.net.objects
import xyz.etkn.mobile.data.net.sameOrigin

/**
 * Stream URL rules from the TV client: use the server-supplied `DirectStreamUrl` (relative paths
 * resolve against the server). Ordinary direct streams must be same-origin; ISO sources may use
 * `Path` and receive the token only when same-origin. No URL is ever derived from IDs.
 */
object PlaybackAddress {

    fun resolve(source: MediaSource, server: String, token: String, userId: String): String {
        val direct = source.directStreamUrl ?: throw IllegalStateException("服务器未提供播放地址")
        val iso = isDisc(source)
        // A disc whose Path names the image (local ISO / linked .iso) is read from Path like the TV
        // client; a 115 STRM disc's Path is the STRM target, so it plays from DirectStreamUrl.
        val fromPath = iso && source.path.lowercase().contains(".iso")
        var address = if (fromPath) source.path.trim() else direct
        address = when {
            address.startsWith("/") -> server + address
            address.matches(Regex("(?i)^https?://.*")) -> address
            else -> "$server/$address"
        }
        val resolved = address.toHttpUrlOrNull() ?: throw IllegalStateException("服务器返回了无效播放地址")
        val origin = server.toHttpUrlOrNull() ?: throw IllegalStateException("服务器地址无效")
        val same = sameOrigin(resolved, origin)
        if (!iso && !same) throw IllegalStateException("服务器返回了无效播放地址")
        if (iso && same) {
            val b = resolved.newBuilder()
            if (resolved.queryParameter("X-Emby-Token") == null && token.isNotBlank()) b.addQueryParameter("X-Emby-Token", token)
            if (resolved.queryParameter("UserId") == null && userId.isNotBlank()) b.addQueryParameter("UserId", userId)
            return b.build().toString()
        }
        return resolved.toString()
    }

    /**
     * Blu-ray image per the server's media source. A 115 STRM disc reports `Container: strm` and
     * a pick-code `Path`, so the disc probe's own markers (`BlurayPlaylist`, per-track
     * `TransportStreamId`) also count.
     */
    fun isDisc(source: MediaSource): Boolean =
        source.container.equals("iso", true) ||
            source.path.lowercase().contains(".iso") ||
            source.raw.has("BlurayPlaylist") ||
            source.raw.objects("MediaStreams").any { it.has("TransportStreamId") }

    /** Music streams additionally carry token / user / session as query parameters. */
    fun music(source: MediaSource, server: String, token: String, userId: String, playSession: String): String {
        val base: HttpUrl = resolve(source, server, token, userId).toHttpUrlOrNull()!!
        return base.newBuilder()
            .setQueryParameter("X-Emby-Token", token)
            .setQueryParameter("UserId", userId)
            .setQueryParameter("PlaySessionId", playSession)
            .setQueryParameter("MediaSourceId", source.id)
            .build().toString()
    }

    /** External subtitle: same-origin only; drop `UserId`, set `X-Emby-Token`. */
    fun subtitle(delivery: String, server: String, token: String): String? {
        val url = (if (delivery.startsWith("/")) server + delivery else delivery).toHttpUrlOrNull() ?: return null
        val origin = server.toHttpUrlOrNull() ?: return null
        if (!sameOrigin(url, origin)) return null
        return url.newBuilder().removeAllQueryParameters("UserId").setQueryParameter("X-Emby-Token", token).build().toString()
    }
}
