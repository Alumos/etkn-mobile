package xyz.etkn.mobile.player

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers where a stream address redirects (ETKN `original.strm` → 115 / storage CDN), so the
 * player's later range requests — the MKV index at the end of the file, the resume seek, every
 * user seek — go straight to the target instead of making the server resolve the link again.
 * A target that stops answering (expired signature) is dropped and the original address is
 * asked once more. Only cross-host targets are kept; nothing ETKN-specific is ever sent there.
 */
class ResolvedRedirects(private val upstream: DataSource.Factory) : DataSource.Factory {
    private val targets = ConcurrentHashMap<Uri, Uri>()

    override fun createDataSource(): DataSource = Source(upstream.createDataSource())

    private inner class Source(private val inner: DataSource) : DataSource {
        override fun addTransferListener(transferListener: TransferListener) = inner.addTransferListener(transferListener)

        override fun open(dataSpec: DataSpec): Long {
            targets[dataSpec.uri]?.let { target ->
                try {
                    return inner.open(dataSpec.withUri(target))
                } catch (_: HttpDataSource.HttpDataSourceException) {
                    targets.remove(dataSpec.uri)
                    inner.close()
                }
            }
            val length = inner.open(dataSpec)
            val landed = inner.uri
            if (landed != null && landed.host != null && !landed.host.equals(dataSpec.uri.host, ignoreCase = true)) {
                targets[dataSpec.uri] = landed
            }
            return length
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = inner.read(buffer, offset, length)

        override fun getUri(): Uri? = inner.uri

        override fun getResponseHeaders(): Map<String, List<String>> = inner.responseHeaders

        override fun close() = inner.close()
    }
}
