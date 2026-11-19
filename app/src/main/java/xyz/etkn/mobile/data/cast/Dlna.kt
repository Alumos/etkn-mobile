package xyz.etkn.mobile.data.cast

import android.content.Context
import android.net.wifi.WifiManager
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** A UPnP media renderer on the local network (TV, box, speaker) with an AVTransport service. */
@Immutable
data class CastDevice(val name: String, val controlUrl: String)

/**
 * 投屏 (DLNA / UPnP AV): finds renderers with an SSDP search on the Wi-Fi, then hands them the
 * stream with `SetAVTransportURI` + `Play` (+ `Seek` to the current position) and can `Stop`.
 * The renderer fetches the stream itself, so it gets the self-contained URL (token in the query).
 */
class Dlna(private val context: Context, http: OkHttpClient) {
    private val http = http.newBuilder().callTimeout(6, TimeUnit.SECONDS).build()

    /** Renderers answering within [timeoutMs]. */
    suspend fun discover(timeoutMs: Int = 3_000): List<CastDevice> = withContext(Dispatchers.IO) {
        val locations = search(timeoutMs)
        coroutineScope { locations.map { async { runCatching { describe(it) }.getOrNull() } }.awaitAll() }
            .filterNotNull().distinctBy { it.controlUrl }.sortedBy { it.name }
    }

    suspend fun play(device: CastDevice, url: String, title: String, positionMs: Long) = withContext(Dispatchers.IO) {
        val meta = didl(title, url)
        soap(device, "SetAVTransportURI", "<CurrentURI>${xml(url)}</CurrentURI><CurrentURIMetaData>${xml(meta)}</CurrentURIMetaData>")
        soap(device, "Play", "<Speed>1</Speed>")
        if (positionMs > 5_000) {
            // Most renderers accept a seek only once the new media is loaded.
            delay(1_500)
            runCatching { soap(device, "Seek", "<Unit>REL_TIME</Unit><Target>${clock(positionMs)}</Target>") }
        }
    }

    suspend fun stop(device: CastDevice) = withContext(Dispatchers.IO) { runCatching { soap(device, "Stop", "") } }

    private fun search(timeoutMs: Int): Set<String> {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi?.createMulticastLock("etkn-cast")?.apply { setReferenceCounted(false); acquire() }
        val found = LinkedHashSet<String>()
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 400
                val message = (
                    "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\n" +
                        "ST: $AV_TRANSPORT\r\n\r\n"
                    ).toByteArray()
                val group = InetAddress.getByName("239.255.255.250")
                repeat(2) { socket.send(DatagramPacket(message, message.size, group, 1900)) }
                val buffer = ByteArray(2048)
                val end = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < end) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    String(packet.data, 0, packet.length).lineSequence()
                        .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
                        ?.substringAfter(':')?.trim()?.let(found::add)
                }
            }
        } finally {
            lock?.release()
        }
        return found
    }

    /** Reads the device description: friendly name and the AVTransport control URL. */
    private fun describe(location: String): CastDevice? {
        val body = http.newCall(Request.Builder().url(location).build()).execute().use { if (it.isSuccessful) it.body.string() else return null }
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
        parser.setInput(StringReader(body))
        var name = ""
        var base = ""
        var type = ""
        var control: String? = null
        var tag = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> tag = parser.name
                XmlPullParser.TEXT -> {
                    val text = parser.text.trim()
                    when (tag) {
                        "friendlyName" -> if (name.isEmpty()) name = text
                        "URLBase" -> base = text
                        "serviceType" -> type = text
                        "controlURL" -> if (type.startsWith("urn:schemas-upnp-org:service:AVTransport") && control == null) control = text
                    }
                }
                XmlPullParser.END_TAG -> tag = ""
            }
        }
        val relative = control ?: return null
        val root = (base.ifEmpty { location }).toHttpUrlOrNull() ?: return null
        val url = root.resolve(relative)?.toString() ?: return null
        return CastDevice(name.ifEmpty { root.host }, url)
    }

    private fun soap(device: CastDevice, action: String, arguments: String) {
        val envelope = """<?xml version="1.0" encoding="utf-8"?>""" +
            """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""" +
            """<s:Body><u:$action xmlns:u="$AV_TRANSPORT"><InstanceID>0</InstanceID>$arguments</u:$action></s:Body></s:Envelope>"""
        val request = Request.Builder().url(device.controlUrl)
            .header("SOAPACTION", "\"$AV_TRANSPORT#$action\"")
            .post(envelope.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
            .build()
        http.newCall(request).execute().use { if (!it.isSuccessful) throw IOException("投屏设备拒绝了 $action（HTTP ${it.code}）") }
    }

    private fun didl(title: String, url: String) =
        """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">""" +
            """<item id="0" parentID="-1" restricted="1"><dc:title>${xml(title)}</dc:title><upnp:class>object.item.videoItem</upnp:class>""" +
            """<res protocolInfo="http-get:*:video/*:*">${xml(url)}</res></item></DIDL-Lite>"""

    private fun xml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun clock(ms: Long): String {
        val s = ms / 1000
        return "%02d:%02d:%02d".format(Locale.ROOT, s / 3600, s % 3600 / 60, s % 60)
    }

    private companion object {
        const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
    }
}
