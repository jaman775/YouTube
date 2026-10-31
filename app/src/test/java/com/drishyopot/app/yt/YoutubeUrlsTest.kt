package com.drishyopot.app.yt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeUrlsTest {
    private val id = "dQw4w9WgXcQ"

    @Test fun watchUrl() =
        assertEquals(YtLink.Video(id), YoutubeUrls.parse("https://www.youtube.com/watch?v=$id"))

    @Test fun mobileWatchWithListAndTime() =
        assertEquals(YtLink.Video(id, "PL123", 90), YoutubeUrls.parse("https://m.youtube.com/watch?v=$id&list=PL123&t=1m30s"))

    @Test fun shortLink() =
        assertEquals(YtLink.Video(id, null, 42), YoutubeUrls.parse("https://youtu.be/$id?t=42"))

    @Test fun shortsAndEmbedAndLive() {
        assertEquals(YtLink.Video(id), YoutubeUrls.parse("https://www.youtube.com/shorts/$id"))
        assertEquals(YtLink.Video(id), YoutubeUrls.parse("https://www.youtube.com/embed/$id"))
        assertEquals(YtLink.Video(id), YoutubeUrls.parse("https://www.youtube.com/live/$id"))
    }

    @Test fun musicHost() =
        assertEquals(YtLink.Video(id), YoutubeUrls.parse("https://music.youtube.com/watch?v=$id"))

    @Test fun playlist() =
        assertEquals(YtLink.Playlist("PL999"), YoutubeUrls.parse("https://www.youtube.com/playlist?list=PL999"))

    @Test fun channelAndSearchOpenInBrowser() {
        assertTrue(YoutubeUrls.parse("https://www.youtube.com/@someone") is YtLink.Page)
        assertTrue(YoutubeUrls.parse("https://m.youtube.com/results?search_query=cats") is YtLink.Page)
    }

    @Test fun foreignHostsAndGarbageAreRejected() {
        assertNull(YoutubeUrls.parse("https://example.com/watch?v=$id"))
        assertNull(YoutubeUrls.parse("https://notyoutube.com/watch?v=$id"))
        assertNull(YoutubeUrls.parse("not a url"))
        assertNull(YoutubeUrls.parse(""))
    }

    @Test fun invalidIdFallsBackToPage() =
        assertTrue(YoutubeUrls.parse("https://www.youtube.com/watch?v=short") is YtLink.Page)

    @Test fun startParsing() {
        assertEquals(90, YoutubeUrls.parseStart("90"))
        assertEquals(90, YoutubeUrls.parseStart("1m30s"))
        assertEquals(3723, YoutubeUrls.parseStart("1h2m3s"))
        assertEquals(0, YoutubeUrls.parseStart(null))
    }

    @Test fun firstUrlInSharedText() =
        assertEquals("https://youtu.be/$id", YoutubeUrls.firstUrl("Watch this: https://youtu.be/$id."))
}
