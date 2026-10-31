package com.drishyopot.app.cast

import com.drishyopot.app.yt.MediaRanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastManifestTest {
    private val ranges = MediaRanges(initStart = 0, initEnd = 739, indexStart = 740, indexEnd = 2123)
    private val video = DashRep("v", "video/mp4", "avc1.640028", 4_000_000, "http://192.168.0.5:8080/stream/a&b", ranges, 1920, 1080, 30)
    private val audio = DashRep("a", "audio/mp4", "mp4a.40.2", 128_000, "http://192.168.0.5:8080/stream/c", ranges)

    @Test fun manifestContainsBothSetsAndRanges() {
        val mpd = CastManifest.build(video, audio, 125_500)
        assertTrue(mpd.contains("mediaPresentationDuration=\"PT125.500S\""))
        assertTrue(mpd.contains("contentType=\"video\""))
        assertTrue(mpd.contains("contentType=\"audio\""))
        assertTrue(mpd.contains("indexRange=\"740-2123\""))
        assertTrue(mpd.contains("<Initialization range=\"0-739\"/>"))
        assertTrue(mpd.contains("width=\"1920\""))
    }

    @Test fun urlsAreXmlEscaped() {
        assertTrue(CastManifest.build(video, audio, 1000).contains("/stream/a&amp;b"))
    }

    @Test fun audioHasNoVideoAttributes() {
        val audioSet = CastManifest.build(video, audio, 1000).substringAfter("contentType=\"audio\"")
        assertFalse(audioSet.contains("width="))
    }

    @Test fun rangesValidity() {
        assertTrue(ranges.valid)
        assertFalse(MediaRanges().valid)
    }

    @Test fun parsesNormalRange() {
        assertEquals(ByteRange(100, 199), ByteRange.parse("bytes=100-199", 1000))
        assertEquals(100L, ByteRange.parse("bytes=100-199", 1000)!!.length)
    }

    @Test fun openEndedAndSuffixRanges() {
        assertEquals(ByteRange(900, 999), ByteRange.parse("bytes=900-", 1000))
        assertEquals(ByteRange(950, 999), ByteRange.parse("bytes=-50", 1000))
    }

    @Test fun endIsClampedToTotal() = assertEquals(ByteRange(0, 999), ByteRange.parse("bytes=0-5000", 1000))

    @Test fun badRangesAreIgnored() {
        assertNull(ByteRange.parse(null, 1000))
        assertNull(ByteRange.parse("items=0-1", 1000))
        assertNull(ByteRange.parse("bytes=2000-3000", 1000))
        assertNull(ByteRange.parse("bytes=abc-def", 1000))
        assertNull(ByteRange.parse("bytes=0-10", 0))
    }
}
