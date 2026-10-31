@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.drishyopot.app.player.yt

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import kotlin.math.min

/**
 * googlevideo.com throttles long single-range downloads, so media is fetched as consecutive
 * ranged chunks while ExoPlayer sees one continuous stream. Other hosts pass straight through.
 */
class ChunkedHttpDataSource(private val upstream: DataSource) : DataSource {
    private var spec: DataSpec? = null
    private var chunked = false
    private var position = 0L
    private var requestedEnd = -1L        // exclusive end the caller asked for, or -1
    private var total = -1L               // from Content-Range, or -1
    private var chunkOpen = false
    private var chunkRemaining = 0L

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        val host = dataSpec.uri.host.orEmpty()
        chunked = host.endsWith("googlevideo.com") &&
            dataSpec.uri.getQueryParameter("range") == null &&
            !dataSpec.uri.path.orEmpty().contains("manifest")
        if (!chunked) return upstream.open(dataSpec)

        position = dataSpec.position
        requestedEnd = if (dataSpec.length != C.LENGTH_UNSET.toLong()) position + dataSpec.length else -1L
        total = -1L
        openChunk()
        return when {
            requestedEnd >= 0 -> requestedEnd - position
            total >= 0 -> total - position
            else -> C.LENGTH_UNSET.toLong()
        }
    }

    private fun openChunk() {
        val base = spec!!
        val last = position + CHUNK_SIZE - 1
        val chunkEnd = if (requestedEnd >= 0) min(last, requestedEnd - 1) else last
        val chunkSpec = base.buildUpon().setPosition(position).setLength(chunkEnd - position + 1).build()
        val length = upstream.open(chunkSpec)
        upstream.responseHeaders.entries
            .firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
            ?.value?.firstOrNull()?.substringAfter('/', "")?.toLongOrNull()
            ?.let { total = it }
        chunkRemaining = if (length != C.LENGTH_UNSET.toLong()) length else chunkEnd - position + 1
        chunkOpen = true
    }

    private fun streamEnd(): Long = when {
        requestedEnd >= 0 -> requestedEnd
        total >= 0 -> total
        else -> Long.MAX_VALUE
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!chunked) return upstream.read(buffer, offset, length)
        if (length == 0) return 0
        while (true) {
            if (!chunkOpen) {
                if (position >= streamEnd()) return C.RESULT_END_OF_INPUT
                openChunk()
            }
            val toRead = min(length.toLong(), chunkRemaining).toInt().coerceAtLeast(1)
            val n = upstream.read(buffer, offset, toRead)
            if (n == C.RESULT_END_OF_INPUT) {
                upstream.close()
                chunkOpen = false
                // The server ended early: if we do not know the total, that is the end of the stream.
                if (chunkRemaining > 0 && total < 0) return C.RESULT_END_OF_INPUT
                continue
            }
            position += n
            chunkRemaining -= n
            if (chunkRemaining <= 0) {
                upstream.close()
                chunkOpen = false
            }
            return n
        }
    }

    override fun getUri(): Uri? = if (chunkOpen) upstream.uri else spec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        chunkOpen = false
        spec = null
        upstream.close()
    }

    class Factory(private val upstream: DefaultHttpDataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedHttpDataSource(upstream.createDataSource())
    }

    companion object {
        private const val CHUNK_SIZE = 9L * 1024 * 1024
    }
}
