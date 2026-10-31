package com.drishyopot.app.browser

import com.drishyopot.app.yt.YtLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserLogicTest {
    @Test fun blocksKnownAdHosts() {
        assertTrue(AdBlockRules.shouldBlock("https://googleads.g.doubleclick.net/pagead/id"))
        assertTrue(AdBlockRules.shouldBlock("https://pagead2.googlesyndication.com/x.js"))
    }

    @Test fun blocksYoutubeAdPaths() {
        assertTrue(AdBlockRules.shouldBlock("https://m.youtube.com/api/stats/ads?x=1"))
        assertTrue(AdBlockRules.shouldBlock("https://www.youtube.com/pagead/viewthroughconversion/1"))
    }

    @Test fun leavesNormalTrafficAlone() {
        assertFalse(AdBlockRules.shouldBlock("https://m.youtube.com/results?search_query=cats"))
        assertFalse(AdBlockRules.shouldBlock("https://i.ytimg.com/vi/abc/hq.jpg"))
        assertFalse(AdBlockRules.shouldBlock("not a url"))
    }

    @Test fun videoLinksGoNative() {
        val d = UrlPolicy.decide("https://m.youtube.com/watch?v=dQw4w9WgXcQ")
        assertTrue(d is NavDecision.Native && d.link is YtLink.Video)
        assertTrue(UrlPolicy.decide("https://m.youtube.com/shorts/dQw4w9WgXcQ") is NavDecision.Native)
        assertTrue(UrlPolicy.decide("https://youtu.be/dQw4w9WgXcQ") is NavDecision.Native)
    }

    @Test fun youtubeAndGoogleSignInStayInside() {
        assertEquals(NavDecision.Allow, UrlPolicy.decide("https://m.youtube.com/feed/subscriptions"))
        assertEquals(NavDecision.Allow, UrlPolicy.decide("https://accounts.google.com/signin"))
        assertEquals(NavDecision.Allow, UrlPolicy.decide("https://consent.youtube.com/m?continue=x"))
    }

    @Test fun otherSitesAndSchemesLeaveTheApp() {
        assertEquals(NavDecision.External, UrlPolicy.decide("https://example.com/"))
        assertEquals(NavDecision.External, UrlPolicy.decide("intent://scan/#Intent;end"))
        assertEquals(NavDecision.External, UrlPolicy.decide("market://details?id=x"))
    }

    @Test fun inputResolution() {
        assertNull(BrowserInput.resolve("   "))
        assertEquals("https://example.com/a", BrowserInput.resolve("https://example.com/a"))
        assertEquals("https://example.com", BrowserInput.resolve("example.com"))
        assertEquals("https://m.youtube.com/results?search_query=lo+fi+beats", BrowserInput.resolve("lo fi beats"))
    }
}
