package xyz.etkn.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.etkn.mobile.data.diagnostics.Tombstone
import xyz.etkn.mobile.data.net.Credentials
import xyz.etkn.mobile.data.net.ResponseCache
import java.io.ByteArrayOutputStream
import java.nio.file.Files

class DiagnosticsCacheTest {
    private fun varint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (true) {
            if (v and 0x7F.inv().toLong() == 0L) { out.write(v.toInt()); return }
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
    }

    private fun msg(block: ByteArrayOutputStream.() -> Unit) = ByteArrayOutputStream().apply(block).toByteArray()
    private fun ByteArrayOutputStream.num(field: Int, value: Long) { varint(this, (field shl 3).toLong()); varint(this, value) }
    private fun ByteArrayOutputStream.bytes(field: Int, value: ByteArray) { varint(this, ((field shl 3) or 2).toLong()); varint(this, value.size.toLong()); write(value) }
    private fun ByteArrayOutputStream.text(field: Int, value: String) = bytes(field, value.toByteArray())

    @Test
    fun tombstoneGivesSignalAbortAndTheCrashingThreadsFrames() {
        val frame = msg { num(1, 0x1234); text(4, "mp_decode"); num(5, 16); text(6, "/data/app/x/lib/arm64/libmpv.so"); text(8, "abcdef") }
        val crashing = msg { num(1, 77); text(2, "mpv/demux"); bytes(4, frame) }
        val other = msg { num(1, 5); text(2, "main") }
        val tombstone = msg {
            num(5, 70); num(6, 77)
            bytes(10, msg { num(1, 11); text(2, "SIGSEGV"); text(4, "SEGV_MAPERR"); num(8, 1); num(9, 0x10) })
            text(14, "")
            bytes(16, msg { num(1, 5); bytes(2, other) })
            bytes(16, msg { num(1, 77); bytes(2, crashing) })
            bytes(17, msg { text(2, "/system/lib64/libc.so") })
        }
        val lines = Tombstone.describe(tombstone, 10)
        assertEquals("signal SIGSEGV SEGV_MAPERR fault addr 0x10", lines[0])
        assertEquals("thread mpv/demux tid=77", lines[1])
        assertEquals("#00 pc 00001234 libmpv.so (mp_decode+16)", lines[2])
        assertEquals(3, lines.size)
        assertTrue(Tombstone.describe(byteArrayOf(0x0A, 0x7F), 10).isEmpty())
    }

    @Test
    fun cacheIsPerServerTokenAndPath() {
        val dir = Files.createTempDirectory("api").toFile()
        val cache = ResponseCache(dir)
        val account = Credentials("https://media.example", "token-a")
        cache.write(account, "/api/tv/UserViews", "{\"Items\":[]}")
        assertEquals("{\"Items\":[]}", cache.read(account, "/api/tv/UserViews"))
        assertNull(cache.read(account.copy(token = "token-b"), "/api/tv/UserViews"))
        assertNull(cache.read(account, "/api/tv/Items/Latest"))
        // No credential text in file names.
        assertTrue(dir.listFiles()!!.none { it.name.contains("token") })
        cache.clear()
        assertNull(cache.read(account, "/api/tv/UserViews"))
    }
}
