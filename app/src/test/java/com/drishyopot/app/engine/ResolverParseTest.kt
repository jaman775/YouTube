package com.drishyopot.app.engine

import com.drishyopot.app.yt.AudioKind
import com.drishyopot.app.yt.YoutubeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolverParseTest {
    private val sample = """
    {"id":"dQw4w9WgXcQ","title":"Title","uploader":"Up","durationMs":212000,"live":false,
     "thumbnail":"https://i.ytimg.com/vi/x/hq.jpg","hlsUrl":null,
     "videoOnly":[{"url":"https://v/1","height":1080,"width":1920,"fps":30,"codec":"avc1.640028","bitrate":4000000,"mime":"video/mp4","ranges":[0,739,740,2123]}],
     "muxed":[{"url":"https://v/2","height":360,"width":640,"fps":30,"codec":null,"bitrate":500000,"mime":"video/mp4","ranges":[0,0,0,0]}],
     "audio":[{"url":"https://a/1","trackId":"en.4","label":"English · original","kind":"ORIGINAL","language":"en","bitrate":128000,"codec":"mp4a.40.2","mime":"audio/mp4","ranges":[0,631,632,1900]}],
     "subtitles":[{"url":"https://s/1","language":"en","label":"English","mime":"text/vtt","auto":false}]}
    """.trimIndent()

    @Test fun videoParses() {
        val v = YoutubeResolver.parseVideo(sample)
        assertEquals("dQw4w9WgXcQ", v.id)
        assertEquals(212_000L, v.durationMs)
        assertFalse(v.isLive)
        assertNull(v.hlsUrl)
        assertEquals(1080, v.videoOnly.single().height)
        assertEquals("avc", v.videoOnly.single().family)
        assertTrue(v.videoOnly.single().ranges.valid)
        assertFalse(v.muxed.single().ranges.valid)
        assertEquals(AudioKind.ORIGINAL, v.audio.single().kind)
        assertEquals("en.4", v.audio.single().trackId)
        assertEquals("text/vtt", v.subtitles.single().mimeType)
    }

    @Test fun playlistParses() {
        val p = YoutubeResolver.parsePlaylist("""{"title":"Mix","items":[{"id":"dQw4w9WgXcQ","title":"A","uploader":"U","durationSec":60}],"truncated":true}""")
        assertEquals("Mix", p.title)
        assertEquals(60L, p.items.single().durationSec)
        assertTrue(p.truncated)
    }
}
