package xyz.etkn.mobile.data.net

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * One connection pool / dispatcher shared by every transport so the JSON API, images and media
 * reuse sockets to the same ETKN host.
 */
class HttpClients(currentServer: () -> String?, currentToken: () -> String?, userAgent: String) {
    private val pool = ConnectionPool(10, 5, TimeUnit.MINUTES)
    private val dispatcher = Dispatcher().apply {
        maxRequests = 48
        maxRequestsPerHost = 12
    }

    private val base: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(pool)
        .dispatcher(dispatcher)
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .connectTimeout(7, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** JSON API: no redirects (see ApiClient). */
    val api: OkHttpClient = base.newBuilder()
        .readTimeout(35, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /** Short-timeout probe for connection discovery. */
    val probe: OkHttpClient = base.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /**
     * Image transport: follows redirects, but the token is attached per hop and only to the
     * current ETKN origin, never to a CDN. Cross-origin hops must be HTTPS.
     */
    val images: OkHttpClient = base.newBuilder()
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addNetworkInterceptor(SameOriginToken(currentServer, currentToken, userAgent, requireHttpsElsewhere = true))
        .build()

    /** Media (ExoPlayer): stream URLs already carry their own credentials. */
    val media: OkHttpClient = base.newBuilder()
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request()
            val timeout = if (request.url.encodedPath.contains("/api/tv/Transcodes/")) 65 else 30
            chain.withReadTimeout(timeout, TimeUnit.SECONDS).proceed(request)
        }
        .build()

    /**
     * Third-party HTTPS (GitHub releases and their download mirrors). No ETKN credentials are
     * ever attached here; redirects are followed because release assets live on a CDN.
     */
    val external: OkHttpClient = base.newBuilder()
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(false)
        .build()

    /** Streaming (SSE) client: long read timeout, no redirects. */
    val events: OkHttpClient = base.newBuilder()
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
}

private class SameOriginToken(
    private val server: () -> String?,
    private val token: () -> String?,
    private val userAgent: String,
    private val requireHttpsElsewhere: Boolean,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val origin = server()?.toHttpUrlOrNull()
        val same = origin != null && sameOrigin(request.url, origin)
        if (!same && requireHttpsElsewhere && !request.url.isHttps && origin != null) {
            throw IOException("Refusing insecure cross-origin image hop")
        }
        val builder = request.newBuilder().header("User-Agent", userAgent)
        val access = token()
        if (same && !access.isNullOrEmpty()) builder.header("X-Emby-Token", access) else builder.removeHeader("X-Emby-Token")
        return chain.proceed(builder.build())
    }
}

fun sameOrigin(a: HttpUrl, b: HttpUrl): Boolean =
    a.scheme.equals(b.scheme, true) && a.host.equals(b.host, true) && a.port == b.port

fun sameOrigin(a: String, b: String): Boolean {
    val x = a.toHttpUrlOrNull() ?: return false
    val y = b.toHttpUrlOrNull() ?: return false
    return sameOrigin(x, y)
}
