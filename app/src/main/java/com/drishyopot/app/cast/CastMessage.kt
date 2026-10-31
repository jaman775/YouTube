package com.drishyopot.app.cast

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/** One Cast V2 message carrying a JSON string payload. */
data class CastMessage(
    val sourceId: String,
    val destinationId: String,
    val namespace: String,
    val payload: String,
)

/**
 * Minimal protobuf codec for the Cast V2 `CastMessage` (fields: 1 version, 2 source, 3 destination,
 * 4 namespace, 5 payload type, 6 utf8 payload). Messages are framed with a 4-byte big-endian length.
 */
object CastMessageCodec {
    private const val MAX_FRAME = 1 shl 20

    fun encode(m: CastMessage): ByteArray {
        val out = ByteArrayOutputStream()
        writeTag(out, 1, 0); writeVarint(out, 0)            // protocol_version = CASTV2_1_0
        writeString(out, 2, m.sourceId)
        writeString(out, 3, m.destinationId)
        writeString(out, 4, m.namespace)
        writeTag(out, 5, 0); writeVarint(out, 0)            // payload_type = STRING
        writeString(out, 6, m.payload)
        return out.toByteArray()
    }

    fun frame(m: CastMessage): ByteArray {
        val body = encode(m)
        val out = ByteArray(4 + body.size)
        out[0] = (body.size ushr 24).toByte()
        out[1] = (body.size ushr 16).toByte()
        out[2] = (body.size ushr 8).toByte()
        out[3] = body.size.toByte()
        System.arraycopy(body, 0, out, 4, body.size)
        return out
    }

    /** Returns null for a malformed message. */
    fun decode(bytes: ByteArray): CastMessage? {
        var pos = 0
        var source = ""
        var dest = ""
        var ns = ""
        var payload = ""
        var payloadType = 0
        while (pos < bytes.size) {
            val (tag, afterTag) = readVarint(bytes, pos) ?: return null
            pos = afterTag
            val field = (tag ushr 3).toInt()
            when ((tag and 7).toInt()) {
                0 -> {
                    val (value, next) = readVarint(bytes, pos) ?: return null
                    pos = next
                    if (field == 5) payloadType = value.toInt()
                }
                1 -> pos += 8
                5 -> pos += 4
                2 -> {
                    val (len, start) = readVarint(bytes, pos) ?: return null
                    val end = start + len.toInt()
                    if (len < 0 || end > bytes.size) return null
                    val text = String(bytes, start, len.toInt(), Charsets.UTF_8)
                    when (field) {
                        2 -> source = text
                        3 -> dest = text
                        4 -> ns = text
                        6 -> payload = text
                    }
                    pos = end
                }
                else -> return null
            }
        }
        return if (payloadType == 0) CastMessage(source, dest, ns, payload) else null
    }

    /** Reads one framed message; null on clean end of stream. */
    @Throws(IOException::class)
    fun readFrame(input: InputStream): CastMessage? {
        val header = ByteArray(4)
        if (!readFully(input, header, allowEofAtStart = true)) return null
        val length = ((header[0].toInt() and 0xFF) shl 24) or ((header[1].toInt() and 0xFF) shl 16) or
            ((header[2].toInt() and 0xFF) shl 8) or (header[3].toInt() and 0xFF)
        if (length <= 0 || length > MAX_FRAME) throw IOException("Bad Cast frame length $length")
        val body = ByteArray(length)
        readFully(input, body, allowEofAtStart = false)
        return decode(body)
    }

    private fun readFully(input: InputStream, buffer: ByteArray, allowEofAtStart: Boolean): Boolean {
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n < 0) {
                if (read == 0 && allowEofAtStart) return false
                throw EOFException()
            }
            read += n
        }
        return true
    }

    private fun writeTag(out: ByteArrayOutputStream, field: Int, wireType: Int) = writeVarint(out, ((field shl 3) or wireType).toLong())

    private fun writeString(out: ByteArrayOutputStream, field: Int, value: String) {
        val data = value.toByteArray(Charsets.UTF_8)
        writeTag(out, field, 2)
        writeVarint(out, data.size.toLong())
        out.write(data)
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (v and 0x7F.inv().toLong() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }

    private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var pos = start
        while (pos < bytes.size && shift < 64) {
            val b = bytes[pos++].toInt()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result to pos
            shift += 7
        }
        return null
    }
}
