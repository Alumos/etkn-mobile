package xyz.etkn.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import xyz.etkn.mobile.data.api.ProbeException
import xyz.etkn.mobile.data.api.ServerProbe

class ServerProbeTest {
    @Test
    fun addsSchemeAndStripsTrailingSlash() {
        assertEquals("http://192.168.1.10:8097", ServerProbe.normalize(" 192.168.1.10:8097/ "))
        assertEquals("https://media.example.com/etkn", ServerProbe.normalize("https://media.example.com/etkn//"))
    }

    @Test
    fun rejectsCredentialsQueryAndFragment() {
        assertThrows(ProbeException::class.java) { ServerProbe.normalize("http://user:pw@host:8097") }
        assertThrows(ProbeException::class.java) { ServerProbe.normalize("http://host:8097/?a=1") }
        assertThrows(ProbeException::class.java) { ServerProbe.normalize("ftp://host") }
    }
}
