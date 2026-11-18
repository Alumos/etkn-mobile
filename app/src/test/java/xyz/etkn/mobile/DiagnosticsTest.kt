package xyz.etkn.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import xyz.etkn.mobile.data.diagnostics.Diagnostics

class DiagnosticsTest {
    @Test
    fun exportStripsCredentials() {
        val line = "load https://cdn.example/a/b.iso?t=1&sign=abc and /api/tv/Videos/item-1/original.strm?X-Emby-Token=secret&UserId=u " +
            "header Authorization: MediaBrowser Client=\"x\", Token=\"secret2\" api_key=k3"
        val clean = Diagnostics.redact(line)
        listOf("abc", "secret", "secret2", "k3", "UserId=u").forEach { assertFalse(it, clean.contains(it)) }
        assertEquals(true, clean.contains("https://cdn.example/a/b.iso?…"))
        assertEquals(true, clean.contains("/api/tv/Videos/item-1/original.strm?…"))
    }
}
