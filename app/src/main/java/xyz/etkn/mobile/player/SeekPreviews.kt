package xyz.etkn.mobile.player

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.JsonObject
import xyz.etkn.mobile.data.net.double
import xyz.etkn.mobile.data.net.enc
import xyz.etkn.mobile.data.net.long
import xyz.etkn.mobile.data.net.objects
import xyz.etkn.mobile.data.net.str

/**
 * The server's seek thumbnails for one media version (`I/PreviewThumbnails`): frame times and
 * tags; each frame is a small JPEG at `I/PreviewThumbnails/{index}`. Only versions the server
 * has already extracted have them (提取缩略图 in the item menu).
 */
@Immutable
class SeekPreviews private constructor(
    private val base: String,
    private val sourceId: String,
    val aspectRatio: Float,
    private val positionsMs: LongArray,
    private val tags: List<String>,
) {
    /** Frame shown for [positionMs]: the nearest one. */
    fun urlAt(positionMs: Long): String {
        var index = positionsMs.binarySearch(positionMs).let { if (it >= 0) it else -it - 1 }.coerceIn(0, positionsMs.lastIndex)
        if (index > 0 && positionMs - positionsMs[index - 1] < positionsMs[index] - positionMs) index--
        return "$base/$index?MediaSourceId=${enc(sourceId)}&tag=${enc(tags[index])}"
    }

    companion object {
        fun from(server: String, itemId: String, manifest: JsonObject): SeekPreviews? {
            val frames = manifest.objects("Thumbnails").sortedBy { it.long("PositionTicks") }
            if (frames.isEmpty()) return null
            return SeekPreviews(
                base = "$server/api/tv/Items/${enc(itemId)}/PreviewThumbnails",
                sourceId = manifest.str("MediaSourceId"),
                aspectRatio = (manifest.double("AspectRatio") ?: 16.0 / 9.0).toFloat().coerceIn(0.5f, 3f),
                positionsMs = LongArray(frames.size) { frames[it].long("PositionTicks") / 10_000 },
                tags = frames.map { it.str("ImageTag") },
            )
        }
    }
}
