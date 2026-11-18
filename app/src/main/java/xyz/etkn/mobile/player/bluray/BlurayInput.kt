package xyz.etkn.mobile.player.bluray

import android.net.Uri
import android.util.Log
import androidx.annotation.Keep
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException

/**
 * Bounded random access to the original ISO over HTTP Range requests, in 1 MiB chunks with a
 * small LRU cache (libbluray reads 2048-byte sectors, often revisiting the same areas).
 *
 * The URL already carries its own credentials when it is on the ETKN origin; the media client
 * adds no ETKN headers, so redirect targets (storage / CDN) never receive any.
 */
@Keep
internal class BlurayInput(private val http: OkHttpClient, private val uri: Uri, private val userAgent: String) : AutoCloseable {
    private val cache = object : LinkedHashMap<Long, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?) = size > CACHE_CHUNKS
    }
    private var response: Response? = null
    private var stream: InputStream? = null
    private var resolved: HttpUrl? = null
    private var position = -1L
    private var size = -1L

    /** Called from native code on the loader thread only (signature `(JI)[B`). */
    @Keep
    @Throws(IOException::class)
    fun read(at: Long, length: Int): ByteArray {
        if (at < 0 || length <= 0 || length > 8 * CHUNK) throw IOException("原盘读取范围无效")
        val result = ByteArray(length)
        var copied = 0
        while (copied < length) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
            val offset = at + copied
            val base = offset / CHUNK * CHUNK
            val block = cache[base] ?: load(base).also { cache[base] = it }
            val inBlock = (offset - base).toInt()
            val count = minOf(length - copied, block.size - inBlock)
            if (count <= 0) throw EOFException("原盘数据不完整")
            System.arraycopy(block, inBlock, result, copied, count)
            copied += count
        }
        return result
    }

    private fun load(at: Long): ByteArray {
        if (stream == null || position != at) {
            closeStream()
            try {
                open(at, resolved ?: uri.toString().toHttpUrl())
                // An expired signed redirect: start again from the original address.
                if (response!!.code in setOf(401, 403) && resolved != null) {
                    closeStream()
                    resolved = null
                    open(at, uri.toString().toHttpUrl())
                }
                val current = response!!
                // Host and status only: the address itself may carry signed credentials.
                Log.i("ETKN-Disc", "range $at -> HTTP ${current.code} ${current.request.url.host} ${current.header("Content-Range").orEmpty()}")
                val match = CONTENT_RANGE.matchEntire(current.header("Content-Range").orEmpty())
                if (current.code != 206 || match == null || match.groupValues[1].toLong() != at) {
                    throw IOException("原盘服务器不支持 Range 随机读取（HTTP ${current.code}）")
                }
                size = match.groupValues[3].toLong()
                resolved = current.request.url
                stream = current.body.byteStream()
                position = at
            } catch (error: IOException) {
                closeStream()
                throw error
            }
        }
        val wanted = (if (size < 0) CHUNK.toLong() else minOf(CHUNK.toLong(), size - at)).toInt()
        if (wanted <= 0) throw EOFException("原盘读取超出文件范围")
        val bytes = ByteArray(wanted)
        var got = 0
        val input = stream!!
        while (got < wanted) {
            val count = input.read(bytes, got, wanted - got)
            if (count < 0) throw EOFException("原盘数据不完整")
            got += count
            position += count
        }
        return bytes
    }

    private fun open(at: Long, address: HttpUrl) {
        response = http.newCall(
            Request.Builder().url(address).header("User-Agent", userAgent).header("Range", "bytes=$at-").build(),
        ).execute()
    }

    private fun closeStream() {
        stream = null
        position = -1
        response?.close()
        response = null
    }

    override fun close() {
        closeStream()
        cache.clear()
    }

    private companion object {
        const val CHUNK = 1024 * 1024
        const val CACHE_CHUNKS = 8
        val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
    }
}
