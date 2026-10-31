package com.drishyopot.app.download

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.BitSet
import kotlin.concurrent.thread
import kotlin.math.min

class StoppedException : Exception("stopped")
class HttpStatusException(val code: Int) : IOException("HTTP $code")
class StorageFullException : IOException("No space left on device")

/** Supplies the current URL of a stream and can fetch a fresh one when it expires. */
interface UrlSource {
    fun current(): String
    /** Returns true when a new URL is available. */
    fun refresh(): Boolean
}

/**
 * Downloads one file as fixed-size blocks over several connections. Finished blocks are tracked in
 * [done], so a stopped download resumes by skipping them.
 */
class PartDownloader(
    private val file: File,
    private val length: Long,
    private val done: BitSet,
    private val connections: Int,
    private val urls: UrlSource,
    private val userAgent: String,
    private val onBytes: (Long) -> Unit,
    private val onBlockDone: () -> Unit,
    private val isStopped: () -> Boolean,
) {
    private val blockCount = ((length + BLOCK - 1) / BLOCK).toInt()
    private val claimed = BitSet()
    // The caller's reporter reads `done` under the same monitor.
    private val lock: Any = done
    @Volatile private var failure: Throwable? = null

    fun run() {
        if (blockCount == 0) return
        RandomAccessFile(file, "rw").use { raf ->
            try {
                if (raf.length() != length) raf.setLength(length)
            } catch (e: IOException) {
                throw if (isNoSpace(e)) StorageFullException() else e
            }
            val channel = raf.channel
            val workers = (1..min(connections.coerceAtLeast(1), blockCount)).map {
                thread(name = "dl-worker") {
                    try { work(channel) } catch (t: Throwable) { if (failure == null) failure = t }
                }
            }
            workers.forEach { it.join() }
        }
        failure?.let { throw it }
        if (isStopped()) throw StoppedException()
    }

    private fun work(channel: java.nio.channels.FileChannel) {
        while (failure == null && !isStopped()) {
            val index = claimNext() ?: return
            try {
                fetchBlock(channel, index)
                synchronized(lock) { done.set(index) }
                onBlockDone()
            } finally {
                synchronized(lock) { claimed.clear(index) }
            }
        }
    }

    private fun claimNext(): Int? = synchronized(lock) {
        var i = done.nextClearBit(0)
        while (i < blockCount) {
            if (!claimed.get(i)) { claimed.set(i); return@synchronized i }
            i = done.nextClearBit(i + 1)
        }
        null
    }

    private fun fetchBlock(channel: java.nio.channels.FileChannel, index: Int) {
        val start = index * BLOCK
        val end = min(length - 1, start + BLOCK - 1)
        var attempt = 0
        var refreshed = false
        while (true) {
            if (isStopped()) throw StoppedException()
            var counted = 0L
            try {
                counted = readRange(channel, start, end)
                return
            } catch (e: StoppedException) {
                throw e
            } catch (e: StorageFullException) {
                throw e
            } catch (e: HttpStatusException) {
                if ((e.code == 403 || e.code == 410) && !refreshed) {
                    refreshed = true
                    if (urls.refresh()) continue
                }
                if (!(e.code == 429 || e.code in 500..599) || ++attempt >= MAX_ATTEMPTS) throw e
                sleepBackoff(attempt)
            } catch (e: IOException) {
                if (isNoSpace(e)) throw StorageFullException()
                if (++attempt >= MAX_ATTEMPTS) throw e
                sleepBackoff(attempt)
            }
        }
    }

    /** Copies bytes [start, end] into the file; on failure rolls back the progress it reported. */
    private fun readRange(channel: java.nio.channels.FileChannel, start: Long, end: Long): Long {
        val connection = URL(urls.current()).openConnection() as HttpURLConnection
        var written = 0L
        try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Range", "bytes=$start-$end")
            val code = connection.responseCode
            if (code != 206) throw HttpStatusException(if (code == 200) 416 else code)
            val buffer = ByteArray(32 * 1024)
            var position = start
            connection.inputStream.use { input ->
                while (position <= end) {
                    if (isStopped()) throw StoppedException()
                    val n = input.read(buffer, 0, min(buffer.size.toLong(), end - position + 1).toInt())
                    if (n < 0) throw IOException("Connection ended early")
                    try {
                        channel.write(ByteBuffer.wrap(buffer, 0, n), position)
                    } catch (e: IOException) {
                        throw if (isNoSpace(e)) StorageFullException() else e
                    }
                    position += n
                    written += n
                    onBytes(n.toLong())
                }
            }
            return written
        } catch (t: Throwable) {
            if (written > 0) onBytes(-written)
            throw t
        } finally {
            connection.disconnect()
        }
    }

    private fun sleepBackoff(attempt: Int) {
        try { Thread.sleep(600L * attempt) } catch (_: InterruptedException) { throw StoppedException() }
    }

    private fun isNoSpace(e: IOException) = e.message?.contains("ENOSPC", true) == true ||
        e.message?.contains("No space left", true) == true

    companion object {
        const val BLOCK = 4L * 1024 * 1024
        private const val MAX_ATTEMPTS = 4

        /** One-byte ranged request that reveals the file size. */
        fun probeLength(url: String, userAgent: String): Long {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 12_000
                c.readTimeout = 15_000
                c.setRequestProperty("User-Agent", userAgent)
                c.setRequestProperty("Range", "bytes=0-0")
                val code = c.responseCode
                if (code != 206 && code != 200) throw HttpStatusException(code)
                return c.getHeaderField("Content-Range")?.substringAfter('/', "")?.toLongOrNull()
                    ?: c.getHeaderField("Content-Length")?.toLongOrNull()
                    ?: throw IOException("Unknown file size")
            } finally { c.disconnect() }
        }
    }
}
