package com.drishyopot.app.download

import java.util.BitSet

object FileNames {
    private val illegal = Regex("""[\\/:*?"<>|\u0000-\u001F]""")

    /** A safe file name (no extension) that keeps readable text in any script. */
    fun sanitize(raw: String, fallback: String = "video"): String {
        val cleaned = raw.replace(illegal, " ").replace(Regex("\\s+"), " ").trim().trim('.')
        val cut = if (cleaned.length > 120) cleaned.substring(0, 120).trimEnd() else cleaned
        return cut.ifBlank { fallback }
    }
}

/** Compact persistence of the set of finished blocks (hex of the BitSet bytes). */
object BlockBits {
    fun encode(bits: BitSet): String = bits.toByteArray().joinToString("") { "%02x".format(it) }

    fun decode(text: String): BitSet {
        if (text.isBlank() || text.length % 2 != 0 || !text.all { it in "0123456789abcdef" }) return BitSet()
        return BitSet.valueOf(ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() })
    }

    fun doneBytes(bits: BitSet, length: Long, blockSize: Long): Long {
        var total = 0L
        var i = bits.nextSetBit(0)
        while (i >= 0) {
            val start = i * blockSize
            if (start < length) total += minOf(blockSize, length - start)
            i = bits.nextSetBit(i + 1)
        }
        return total
    }
}
