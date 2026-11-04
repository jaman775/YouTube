@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.drishyopot.app.player.yt

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import com.drishyopot.app.diagnostics.DiagnosticLog
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import kotlin.math.min

/**
 * Plays YouTube's `/videoplayback` URLs as a series of ranged requests made the way YouTube's web
 * player makes them (see [YoutubeStreamHttp]); ExoPlayer sees one continuous stream. Any other URL
 * (subtitles, HLS playlists) goes to [fallback] untouched.
 */
class ChunkedHttpDataSource(private val fallback: DataSource) : BaseDataSource(/* isNetwork = */ true) {
    private var passthrough = false
    private var spec: DataSpec? = null
    private var url = ""
    private var position = 0L
    private var requestedEnd = -1L      // exclusive end ExoPlayer asked for, or -1
    private var connection: HttpURLConnection? = null
    private var input: InputStream? = null
    private var chunkRemaining = 0L
    private var lastChunkWasShort = false
    private var started = false

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        val target = dataSpec.uri.toString()
        passthrough = !YoutubeStreamHttp.isVideoPlayback(target)
        if (passthrough) return fallback.open(dataSpec)

        transferInitializing(dataSpec)
        url = target
        position = dataSpec.position
        val total = YoutubeStreamHttp.contentLengthParam(target) ?: -1L
        requestedEnd = if (dataSpec.length != C.LENGTH_UNSET.toLong()) position + dataSpec.length else total
        lastChunkWasShort = false
        if (requestedEnd < 0 || position < requestedEnd) openChunk()
        started = true
        transferStarted(dataSpec)
        return if (requestedEnd >= 0) (requestedEnd - position).coerceAtLeast(0) else C.LENGTH_UNSET.toLong()
    }

    private fun openChunk() {
        val last = position + CHUNK_SIZE - 1
        val end = if (requestedEnd >= 0) min(last, requestedEnd - 1) else last
        val expected = end - position + 1
        val c = try {
            YoutubeStreamHttp.open(url, position, end)
        } catch (e: IOException) {
            DiagnosticLog.log("StreamHttp", "Connection failed (${YoutubeStreamHttp.describe(url)}): ${e.message}")
            throw e
        }
        val code = c.responseCode
        if (code !in 200..299) {
            c.disconnect()
            DiagnosticLog.log("StreamHttp", "HTTP $code from ${YoutubeStreamHttp.describe(url)}")
            throw IOException("YouTube refused the video stream (HTTP $code)")
        }
        connection = c
        input = c.inputStream
        val length = c.contentLengthLong
        chunkRemaining = if (length >= 0) length else expected
        lastChunkWasShort = length in 0 until expected
    }

    private fun closeChunk() {
        try { input?.close() } catch (_: IOException) { }
        connection?.disconnect()
        input = null
        connection = null
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (passthrough) return fallback.read(buffer, offset, length)
        if (length == 0) return 0
        while (true) {
            val stream = input
            if (stream == null) {
                if (lastChunkWasShort || (requestedEnd >= 0 && position >= requestedEnd)) return C.RESULT_END_OF_INPUT
                openChunk()
                continue
            }
            val toRead = min(length.toLong(), chunkRemaining).toInt()
            if (toRead <= 0) { closeChunk(); continue }
            val n = stream.read(buffer, offset, toRead)
            if (n < 0) {
                closeChunk()
                if (lastChunkWasShort) return C.RESULT_END_OF_INPUT
                throw IOException("The video stream ended early")
            }
            position += n
            chunkRemaining -= n
            bytesTransferred(n)
            if (chunkRemaining <= 0L) closeChunk()
            return n
        }
    }

    override fun getUri(): Uri? = if (passthrough) fallback.uri else spec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = if (passthrough) fallback.responseHeaders else emptyMap()

    override fun close() {
        try {
            if (passthrough) fallback.close() else closeChunk()
        } finally {
            if (started) { started = false; transferEnded() }
            spec = null
        }
    }

    class Factory(private val upstream: DefaultHttpDataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedHttpDataSource(upstream.createDataSource())
    }

    companion object {
        private const val CHUNK_SIZE = 9L * 1024 * 1024
    }
}
