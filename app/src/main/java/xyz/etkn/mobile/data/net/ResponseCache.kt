package xyz.etkn.mobile.data.net

import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Last good JSON of selected GET requests, so a screen can paint the previous state at once and
 * refresh behind it. Opt-in per coroutine ([Use]): [Mode.Store] keeps successful responses,
 * [Mode.Read] answers only from disk (a miss is a network error). Files are named by a hash of
 * server, token and path, live in the app's cache directory and are pruned by age and size;
 * private accounts never opt in, and signing out clears everything.
 */
class ResponseCache(private val dir: File) {
    enum class Mode { Store, Read }

    class Use(val mode: Mode) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Use>
    }

    fun read(credentials: Credentials, path: String): String? =
        runCatching { file(credentials, path).takeIf { it.isFile }?.readText() }.getOrNull()

    fun write(credentials: Credentials, path: String, text: String) {
        runCatching {
            dir.mkdirs()
            val target = file(credentials, path)
            val temp = File(dir, target.name + ".tmp")
            temp.writeText(text)
            if (!temp.renameTo(target)) temp.delete()
        }
    }

    fun clear() {
        runCatching { dir.deleteRecursively() }
    }

    /** Drops entries older than [maxAgeDays] and the oldest beyond [maxBytes]. */
    fun prune(maxAgeDays: Int = 14, maxBytes: Long = 16L * 1024 * 1024) {
        runCatching {
            val cutoff = System.currentTimeMillis() - maxAgeDays * 86_400_000L
            val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
            var total = 0L
            for (file in files) {
                total += file.length()
                if (file.lastModified() < cutoff || total > maxBytes) file.delete()
            }
        }
    }

    private fun file(credentials: Credentials, path: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${credentials.origin}\u0000${credentials.token}\u0000$path".toByteArray())
        return File(dir, digest.joinToString("") { "%02x".format(it) } + ".json")
    }
}
