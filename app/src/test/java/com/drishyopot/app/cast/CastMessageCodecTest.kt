package com.drishyopot.app.cast

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class CastMessageCodecTest {
    private val msg = CastMessage("sender-0", "receiver-0", "urn:x-cast:com.google.cast.tp.connection", """{"type":"CONNECT"}""")

    @Test fun roundTrip() = assertEquals(msg, CastMessageCodec.decode(CastMessageCodec.encode(msg)))

    @Test fun unicodePayloadSurvives() {
        val m = msg.copy(payload = """{"title":"দৃশ্যপট ✓"}""")
        assertEquals(m, CastMessageCodec.decode(CastMessageCodec.encode(m)))
    }

    @Test fun knownBytesPrefix() {
        // field 1 = 0, field 2 = "ab"
        val encoded = CastMessageCodec.encode(CastMessage("ab", "", "", ""))
        assertArrayEquals(byteArrayOf(0x08, 0x00, 0x12, 0x02, 0x61, 0x62), encoded.copyOfRange(0, 6))
    }

    @Test fun framingReadsBackOneMessage() {
        val stream = ByteArrayInputStream(CastMessageCodec.frame(msg) + CastMessageCodec.frame(msg.copy(payload = "{}")))
        assertEquals(msg, CastMessageCodec.readFrame(stream))
        assertEquals("{}", CastMessageCodec.readFrame(stream)?.payload)
        assertNull(CastMessageCodec.readFrame(stream))
    }

    @Test fun longPayloadUsesMultiByteVarint() {
        val big = "x".repeat(5000)
        assertEquals(big, CastMessageCodec.decode(CastMessageCodec.encode(msg.copy(payload = big)))?.payload)
    }

    @Test fun truncatedInputIsRejected() {
        val bytes = CastMessageCodec.encode(msg)
        assertNull(CastMessageCodec.decode(bytes.copyOfRange(0, bytes.size - 5)))
    }
}
