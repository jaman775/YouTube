package com.drishyopot.app.player

import com.drishyopot.app.player.yt.StreamProfiles
import com.drishyopot.app.player.yt.YoutubeStreamHttp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamHttpTest {
    private val stream = "https://rr1---sn-abc.googlevideo.com/videoplayback?expire=1&c=WEB&clen=12345678&itag=137"

    @Test fun videoplaybackUrlsAreRecognised() {
        assertTrue(YoutubeStreamHttp.isVideoPlayback(stream))
        assertFalse(YoutubeStreamHttp.isVideoPlayback("https://www.youtube.com/api/timedtext?v=x"))
        assertFalse(YoutubeStreamHttp.isVideoPlayback("https://i.ytimg.com/vi/x/hq.jpg"))
        assertFalse(YoutubeStreamHttp.isVideoPlayback("not a url"))
    }

    @Test fun sizeComesFromTheUrl() {
        assertEquals(12_345_678L, YoutubeStreamHttp.contentLengthParam(stream))
        assertNull(YoutubeStreamHttp.contentLengthParam("https://x.googlevideo.com/videoplayback?itag=18"))
    }

    @Test fun profileParsesUserAgentAndHeaders() {
        val p = StreamProfiles.parse("""{"userAgent":"UA/1","headers":{"Origin":"https://www.youtube.com","Sec-Fetch-Mode":"cors"}}""")
        assertEquals("UA/1", p.userAgent)
        assertEquals("cors", p.headers["Sec-Fetch-Mode"])
        assertEquals(2, p.headers.size)
    }

    @Test fun missingUserAgentFallsBackToTheDefault() {
        val p = StreamProfiles.parse("""{"userAgent":null,"headers":{}}""")
        assertTrue(p.userAgent.isNotBlank())
        assertTrue(p.headers.isEmpty())
    }
}
