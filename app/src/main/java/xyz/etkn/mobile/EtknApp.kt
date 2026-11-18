package xyz.etkn.mobile

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowHardware
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath
import xyz.etkn.mobile.data.diagnostics.Diagnostics

class EtknApp : Application(), SingletonImageLoader.Factory {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        Diagnostics.start(this)
        graph = AppGraph(this)
        graph.sessions.restore()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { graph.http.images })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.22).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("artwork").toOkioPath())
                    .maxSizeBytes(384L * 1024 * 1024)
                    .build()
            }
            .crossfade(180)
            .allowHardware(true)
            .build()
}

val Context.graph: AppGraph get() = (applicationContext as EtknApp).graph
