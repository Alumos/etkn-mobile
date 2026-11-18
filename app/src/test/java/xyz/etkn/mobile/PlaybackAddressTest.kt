package xyz.etkn.mobile

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.etkn.mobile.data.model.MediaSource
import xyz.etkn.mobile.data.net.EtknJson
import xyz.etkn.mobile.player.PlaybackAddress
import xyz.etkn.mobile.player.bluray.BlurayMediaSourceFactory

class PlaybackAddressTest {
    private val server = "https://media.example"

    private fun source(json: String) = MediaSource.from(EtknJson.parseToJsonElement(json) as JsonObject)

    @Test
    fun localIsoPlaysFromItsPath() {
        val disc = source("""{"Id":"version-1","Container":"iso","Path":"/api/tv/Videos/item-1/stream.iso?X-Emby-Token=t",
            "DirectStreamUrl":"/api/tv/Videos/item-1/stream.iso?X-Emby-Token=t"}""")
        assertTrue(PlaybackAddress.isDisc(disc))
        assertTrue(PlaybackAddress.resolve(disc, server, "t", "u").startsWith("$server/api/tv/Videos/item-1/stream.iso"))
    }

    @Test
    fun strmDiscIsRecognisedFromProbeMarkersAndPlaysFromDirectStream() {
        // 115 STRM ISO: Container is "strm" and Path is the pick-code target on the LAN address.
        val disc = source("""{"Id":"version-2","Container":"strm","Path":"http://192.168.1.2:8097/api/p115/play/abc123",
            "DirectStreamUrl":"/api/tv/Videos/item-2/original.strm?X-Emby-Token=t&MediaSourceId=version-2",
            "BlurayPlaylist":800,"MediaStreams":[{"Index":0,"Type":"Video","TransportStreamId":4113}]}""")
        assertTrue(PlaybackAddress.isDisc(disc))
        assertEquals(
            "$server/api/tv/Videos/item-2/original.strm?X-Emby-Token=t&MediaSourceId=version-2&UserId=u",
            PlaybackAddress.resolve(disc, server, "t", "u"),
        )
        val tracksOnly = source("""{"Id":"version-3","Container":"strm","Path":"http://lan/api/p115/play/x",
            "DirectStreamUrl":"/api/tv/Videos/item-3/original.strm","MediaStreams":[{"Index":1,"Type":"Audio","TransportStreamId":4352}]}""")
        assertTrue(PlaybackAddress.isDisc(tracksOnly))
    }

    @Test
    fun ordinaryStrmIsNotADisc() {
        val movie = source("""{"Id":"version-4","Container":"mkv","Path":"http://lan/api/p115/play/y",
            "DirectStreamUrl":"/api/tv/Videos/item-4/original.strm","MediaStreams":[{"Index":0,"Type":"Video"}]}""")
        assertFalse(PlaybackAddress.isDisc(movie))
        assertEquals("$server/api/tv/Videos/item-4/original.strm", PlaybackAddress.resolve(movie, server, "t", "u"))
    }

    @Test
    fun hdrAndDolbyVisionAreRecognisedFromTheProbeLabels() {
        fun video(fields: String) = source("""{"Id":"v","MediaStreams":[{"Index":0,"Type":"Video",$fields}]}""")
        assertTrue(PlaybackAddress.isHdr(video(""""VideoRange":"HDR","ColorTransfer":"smpte2084"""")))
        assertTrue(PlaybackAddress.isHdr(video(""""VideoRange":"SDR","ExtendedVideoType":"DolbyVision","ExtendedVideoSubType":"DoviProfile81"""")))
        assertTrue(PlaybackAddress.isHdr(video(""""ColorTransfer":"arib-std-b67"""")))
        assertFalse(PlaybackAddress.isHdr(video(""""VideoRange":"SDR","ExtendedVideoType":"SDR","ColorTransfer":"bt709"""")))
        assertFalse(PlaybackAddress.isHdr(source("""{"Id":"a","MediaStreams":[{"Index":1,"Type":"Audio","Title":"HDR remaster commentary"}]}""")))
    }

    @Test
    fun discTrackPidSurvivesSourcePrefixes() {
        assertEquals(4113, BlurayMediaSourceFactory.discPid("etkn-ts-4113-video/hevc"))
        assertEquals(4352, BlurayMediaSourceFactory.discPid("0:etkn-ts-4352-audio/true-hd"))
        assertEquals(-1, BlurayMediaSourceFactory.discPid("ext-3"))
        assertEquals(-1, BlurayMediaSourceFactory.discPid(null))
        val stream = source("""{"Id":"v","MediaStreams":[{"Index":2,"Type":"Audio","TransportStreamId":4352}]}""").audio.single()
        assertEquals(4352, stream.transportStreamId)
    }
}
