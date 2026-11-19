package xyz.etkn.mobile.ui.common

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.materialkolor.hct.Hct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val seeds = LruCache<String, Int>(64)

/**
 * The colour an artwork reads as, for theming its page: a 32 px copy of the image (from Coil's
 * caches when the page already shows it) bucketed by hue, weighted by chroma, so a large calm
 * area of colour wins over a small loud one. Null until known, or for grey images.
 */
@Composable
fun rememberArtworkSeed(url: String?): Int? {
    val context = LocalContext.current
    var seed by remember(url) { mutableStateOf(url?.let(seeds::get)) }
    LaunchedEffect(url) {
        if (url == null || seed != null) return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(url).size(32).allowHardware(false).build()
        val bitmap = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap() ?: return@LaunchedEffect
        withContext(Dispatchers.Default) { dominantSeed(bitmap) }?.let {
            seeds.put(url, it)
            seed = it
        }
    }
    return seed
}

private fun dominantSeed(bitmap: Bitmap): Int? {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val weight = DoubleArray(BUCKETS)
    val chroma = DoubleArray(BUCKETS)
    for (argb in pixels) {
        val hct = Hct.fromInt(argb)
        // Near-black and near-white pixels carry no usable hue.
        if (hct.tone < 12 || hct.tone > 92 || hct.chroma < 8) continue
        val bucket = (hct.hue / (360.0 / BUCKETS)).toInt().coerceIn(0, BUCKETS - 1)
        weight[bucket] += hct.chroma
        chroma[bucket] += hct.chroma * hct.chroma
    }
    val best = weight.indices.maxByOrNull { weight[it] } ?: return null
    if (weight[best] <= 0.0) return null
    val hue = (best + 0.5) * (360.0 / BUCKETS)
    return Hct.from(hue, (chroma[best] / weight[best]).coerceIn(24.0, 60.0), 50.0).toInt()
}

private const val BUCKETS = 24
