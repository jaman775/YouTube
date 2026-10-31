package com.drishyopot.app.download

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.BitSet

class DownloadLogicTest {
    @Test fun sanitizeRemovesIllegalCharacters() {
        assertEquals("a b c d", FileNames.sanitize("a/b:c*d"))
        assertEquals("video", FileNames.sanitize("???"))
        assertEquals("দৃশ্যপট", FileNames.sanitize("দৃশ্যপট"))
    }

    @Test fun sanitizeLimitsLength() = assertTrue(FileNames.sanitize("x".repeat(500)).length <= 120)

    @Test fun blockBitsRoundTrip() {
        val bits = BitSet().apply { set(0); set(3); set(70) }
        assertEquals(bits, BlockBits.decode(BlockBits.encode(bits)))
        assertEquals(BitSet(), BlockBits.decode(""))
        assertEquals(BitSet(), BlockBits.decode("***not base64***"))
    }

    @Test fun doneBytesHandlesShortLastBlock() {
        val block = 100L
        val bits = BitSet().apply { set(0); set(2) } // blocks 0 and 2; file is 250 bytes so block 2 has 50
        assertEquals(150L, BlockBits.doneBytes(bits, 250, block))
    }

    @Test fun taskSurvivesJson() {
        val request = DownloadRequest(
            "dQw4w9WgXcQ", "Title", "Uploader", "http://t/x.jpg", 212_000, DlMode.VIDEO, 1080, "avc", "en.1",
            listOf("en", "bn"), true, "Title",
        )
        val task = DownloadTask("id1", request, DlStatus.PAUSED, DlError.HTTP_403, "x", 100, 40, video = PartState(60, "AQ=="))
        val back = DownloadJson.fromJson(JSONObject(DownloadJson.toJson(task).toString()))
        assertNotNull(back)
        assertEquals(task.copy(createdAt = back!!.createdAt), back)
    }

    @Test fun progressAndActiveFlags() {
        val request = DownloadRequest("x".repeat(11), "t", "u", null, 0, DlMode.AUDIO, 0, "", null, emptyList(), false, "t")
        val running = DownloadTask("i", request, DlStatus.RUNNING, totalBytes = 200, downloadedBytes = 50)
        assertEquals(0.25f, running.progress, 0.0001f)
        assertTrue(running.isActive)
        assertTrue(!running.copy(status = DlStatus.PAUSED).isActive)
    }

    @Test fun containerChoice() {
        assertEquals(ContainerPlan.MP4, ContainerPlan.forStreams("avc", "mp4a.40.2"))
        assertEquals(ContainerPlan.WEBM, ContainerPlan.forStreams("vp9", "opus"))
        assertEquals(ContainerPlan.MP4, ContainerPlan.forStreams("vp9", "mp4a.40.2"))
    }
}
