package com.drishyopot.app.cast

import com.drishyopot.app.diagnostics.DiagnosticLog
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * Tiny HTTP server on the phone. A Cast device (or a browser opened from the shared link) fetches
 * media from here instead of from YouTube directly, so headers, ranges and CORS are handled for it.
 *
 * Routes: /stream/<token>, /manifest.mpd, /player, /ping.
 */
class LocalStreamProxy(private val userAgent: String) {
    /** A proxied upstream file. [refresh] returns a fresh URL when the old one expires. */
    class Source(
        @Volatile var url: String,
        val mimeType: String,
        val refresh: (() -> String?)? = null,
        @Volatile var length: Long = -1,
    )

    private val sources = ConcurrentHashMap<String, Source>()
    private val pool = Executors.newCachedThreadPool()
    private var server: ServerSocket? = null

    @Volatile var manifest: String? = null
    @Volatile var playerHtml: String? = null
    @Volatile private var lastPeerPing = 0L

    val port: Int get() = server?.localPort ?: 0
    val running: Boolean get() = server?.isClosed == false

    fun peerConnected(): Boolean = System.currentTimeMillis() - lastPeerPing < PEER_TIMEOUT_MS

    @Synchronized
    fun start(): Boolean {
        if (running) return true
        return try {
            val s = ServerSocket(0)
            server = s
            Thread({
                while (!s.isClosed) {
                    try {
                        val client = s.accept()
                        pool.execute { handle(client) }
                    } catch (_: IOException) { break }
                }
            }, "cast-proxy").start()
            true
        } catch (e: IOException) {
            DiagnosticLog.log("Cast", "Proxy could not start", e)
            false
        }
    }

    @Synchronized
    fun stop() {
        try { server?.close() } catch (_: IOException) { }
        server = null
        sources.clear()
        manifest = null
        playerHtml = null
        lastPeerPing = 0
    }

    fun register(source: Source): String {
        val token = UUID.randomUUID().toString().replace("-", "").take(16)
        sources[token] = source
        return token
    }

    fun clearSources() { sources.clear() }

    // ---- HTTP ------------------------------------------------------------------------------------

    private fun handle(client: Socket) {
        client.use { socket ->
            try {
                socket.soTimeout = 30_000
                val input = BufferedInputStream(socket.getInputStream())
                val request = readRequest(input) ?: return
                val out = socket.getOutputStream()
                val path = request.path.substringBefore('?')
                when {
                    request.method == "OPTIONS" -> respond(out, 204, "No Content", emptyMap(), ByteArray(0), head = false)
                    path == "/ping" -> { lastPeerPing = System.currentTimeMillis(); respondText(out, "ok", "text/plain") }
                    path == "/player" -> {
                        lastPeerPing = System.currentTimeMillis()
                        respondText(out, playerHtml ?: "Nothing to play", "text/html; charset=utf-8", request.method == "HEAD")
                    }
                    path == "/manifest.mpd" -> respondText(out, manifest ?: "", "application/dash+xml", request.method == "HEAD", status = if (manifest == null) 404 else 200)
                    path.startsWith("/stream/") -> serveStream(out, sources[path.removePrefix("/stream/")], request)
                    else -> respond(out, 404, "Not Found", emptyMap(), ByteArray(0), head = false)
                }
            } catch (_: IOException) {
                // The client went away; nothing to do.
            } catch (e: Exception) {
                DiagnosticLog.log("Cast", "Proxy request failed", e)
            }
        }
    }

    private class Request(val method: String, val path: String, val headers: Map<String, String>)

    private fun readRequest(input: InputStream): Request? {
        val head = StringBuilder()
        var last4 = 0
        while (head.length < MAX_HEADER_BYTES) {
            val b = input.read()
            if (b < 0) return null
            head.append(b.toChar())
            last4 = (last4 shl 8) or b
            if (last4 == 0x0D0A0D0A) break
        }
        val lines = head.toString().split("\r\n").filter { it.isNotEmpty() }
        val parts = lines.firstOrNull()?.split(' ') ?: return null
        if (parts.size < 2) return null
        val headers = lines.drop(1).mapNotNull { line ->
            val i = line.indexOf(':')
            if (i <= 0) null else line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim()
        }.toMap()
        return Request(parts[0].uppercase(), parts[1], headers)
    }

    private fun serveStream(out: OutputStream, source: Source?, request: Request) {
        if (source == null) { respond(out, 404, "Not Found", emptyMap(), ByteArray(0), head = false); return }
        val total = ensureLength(source)
        if (total <= 0) { respond(out, 502, "Bad Gateway", emptyMap(), ByteArray(0), head = false); return }

        val range = ByteRange.parse(request.headers["range"], total)
        val start = range?.start ?: 0L
        val end = range?.endInclusive ?: (total - 1)
        val headers = linkedMapOf(
            "Content-Type" to source.mimeType,
            "Accept-Ranges" to "bytes",
            "Content-Length" to (end - start + 1).toString(),
        )
        if (range != null) headers["Content-Range"] = "bytes $start-$end/$total"
        writeHead(out, if (range != null) 206 else 200, if (range != null) "Partial Content" else "OK", headers)
        if (request.method == "HEAD") return

        var position = start
        var retried = false
        while (position <= end) {
            val chunkEnd = min(end, position + CHUNK - 1)
            val connection = openUpstream(source, position, chunkEnd)
            try {
                val code = connection.responseCode
                if ((code == 403 || code == 410) && !retried && source.refresh != null) {
                    retried = true
                    source.refresh.invoke()?.let { source.url = it }
                    continue
                }
                if (code != 206 && code != 200) throw IOException("Upstream HTTP $code")
                val copied = copy(connection.inputStream, out, chunkEnd - position + 1)
                if (copied <= 0) throw IOException("Upstream returned no data")
                position += copied
            } finally {
                connection.disconnect()
            }
        }
        out.flush()
    }

    private fun openUpstream(source: Source, from: Long, to: Long): HttpURLConnection {
        val c = URL(source.url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", userAgent)
        c.setRequestProperty("Range", "bytes=$from-$to")
        return c
    }

    /** Learns the file size once with a one-byte ranged request. */
    private fun ensureLength(source: Source): Long {
        if (source.length > 0) return source.length
        repeat(2) { attempt ->
            val c = openUpstream(source, 0, 0)
            try {
                val code = c.responseCode
                if (code == 403 && attempt == 0 && source.refresh != null) {
                    source.refresh.invoke()?.let { source.url = it }
                    return@repeat
                }
                val total = c.getHeaderField("Content-Range")?.substringAfter('/', "")?.toLongOrNull()
                    ?: c.getHeaderField("Content-Length")?.toLongOrNull()
                if (total != null && total > 0) { source.length = total; return total }
            } catch (_: IOException) {
            } finally { c.disconnect() }
        }
        return -1
    }

    private fun copy(input: InputStream, out: OutputStream, limit: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        input.use {
            while (copied < limit) {
                val n = it.read(buffer, 0, min(buffer.size.toLong(), limit - copied).toInt())
                if (n < 0) break
                out.write(buffer, 0, n)
                copied += n
            }
        }
        return copied
    }

    private fun respondText(out: OutputStream, body: String, type: String, head: Boolean = false, status: Int = 200) {
        respond(out, status, if (status == 200) "OK" else "Not Found", mapOf("Content-Type" to type), body.toByteArray(Charsets.UTF_8), head)
    }

    private fun respond(out: OutputStream, status: Int, reason: String, extra: Map<String, String>, body: ByteArray, head: Boolean) {
        val headers = LinkedHashMap(extra)
        headers["Content-Length"] = body.size.toString()
        writeHead(out, status, reason, headers)
        if (!head) out.write(body)
        out.flush()
    }

    private fun writeHead(out: OutputStream, status: Int, reason: String, headers: Map<String, String>) {
        val text = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            headers.forEach { (k, v) -> append("$k: $v\r\n") }
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Range, Content-Type\r\n")
            append("Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(text.toByteArray(Charsets.ISO_8859_1))
    }

    companion object {
        private const val CHUNK = 8L * 1024 * 1024
        private const val MAX_HEADER_BYTES = 16 * 1024
        private const val PEER_TIMEOUT_MS = 12_000L
    }
}
