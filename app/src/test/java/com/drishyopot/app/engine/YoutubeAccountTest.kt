package com.drishyopot.app.engine

import com.drishyopot.app.account.YoutubeAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeAccountTest {
    @Test fun sapisidHashMatchesReferenceValue() {
        // sha1("1700000000 ABC123 https://www.youtube.com"), computed independently.
        assertEquals(
            "SAPISIDHASH 1700000000_127b58fad191974bd15010621820454a8b3e6ebd",
            YoutubeAccount.sapisidHash("ABC123", "https://www.youtube.com", 1_700_000_000),
        )
    }

    @Test fun sapisidIsFoundInCookieString() {
        assertEquals("ABC", YoutubeAccount.sapisid("A=1; SAPISID=ABC; B=2"))
        assertEquals("SEC", YoutubeAccount.sapisid("A=1; __Secure-3PAPISID=SEC"))
        assertNull(YoutubeAccount.sapisid("A=1; B=2"))
    }

    @Test fun loginCookieDetection() {
        assertTrue(YoutubeAccount.hasLoginCookies("x=1; SAPISID=abc"))
        assertFalse(YoutubeAccount.hasLoginCookies("x=1; VISITOR_INFO1_LIVE=abc"))
        assertFalse(YoutubeAccount.hasLoginCookies(null))
    }

    @Test fun cookiesGoOnlyToYoutubeHosts() {
        assertTrue(YoutubeAccount.isYoutubeHost("https://www.youtube.com/youtubei/v1/player"))
        assertTrue(YoutubeAccount.isYoutubeHost("https://m.youtube.com/"))
        assertFalse(YoutubeAccount.isYoutubeHost("https://rr1---sn.googlevideo.com/videoplayback"))
        assertFalse(YoutubeAccount.isYoutubeHost("https://evil-youtube.com/"))
        assertFalse(YoutubeAccount.isYoutubeHost("https://youtube.com.evil.com/"))
        assertFalse(YoutubeAccount.isYoutubeHost("not a url"))
    }
}
