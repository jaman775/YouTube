@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.drishyopot.app.player.yt

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Timeline
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.CompositeMediaSource
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.upstream.Allocator
import com.drishyopot.app.yt.StreamChoice
import com.drishyopot.app.yt.YoutubeException
import com.drishyopot.app.yt.YoutubeResolver
import com.drishyopot.app.yt.YtVideo
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/** Shared dependencies for every YouTube source in the process. */
class YoutubeSourceEnv(
    val executor: ExecutorService,
    val choiceFor: (String) -> StreamChoice,
    val onResolved: (YtVideo) -> Unit,
) {
    val dataSourceFactory: DataSource.Factory = ChunkedHttpDataSource.Factory(
        DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true),
    )

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    }
}

/**
 * A media source that resolves a YouTube video on a background thread when prepared, then
 * plays the merged video-only + audio (+ subtitle) streams, or the HLS stream for live videos.
 * Keeping resolution lazy lets whole playlists sit in the queue without any network work.
 */
class YoutubeMediaSource(
    private val item: MediaItem,
    private val videoId: String,
    private val env: YoutubeSourceEnv,
) : CompositeMediaSource<Unit>() {

    @Volatile private var failure: IOException? = null
    private var future: Future<*>? = null
    private var playbackHandler: Handler? = null
    private var child: MediaSource? = null
    private var released = false

    override fun getMediaItem(): MediaItem = item

    override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
        super.prepareSourceInternal(mediaTransferListener)
        released = false
        failure = null
        val handler = Handler(Looper.myLooper() ?: Looper.getMainLooper())
        playbackHandler = handler
        future = env.executor.submit {
            try {
                val video = YoutubeResolver.resolve(videoId)
                val source = buildSource(video)
                env.onResolved(video)
                handler.post {
                    if (released) return@post
                    child = source
                    prepareChildSource(Unit, source)
                }
            } catch (e: Exception) {
                failure = IOException(if (e is YoutubeException) e.message else "YouTube: ${e.message}", e)
            }
        }
    }

    private fun buildSource(video: YtVideo): MediaSource {
        val extras = item.mediaMetadata.extras
        val metadata = MediaMetadata.Builder()
            .setTitle(video.title.ifBlank { item.mediaMetadata.title })
            .setArtist(video.uploader)
            .setArtworkUri(video.thumbnail?.let { Uri.parse(it) })
            .setExtras(extras)
            .build()
        val base = MediaItem.Builder().setMediaId(item.mediaId).setMediaMetadata(metadata)
        val dsf = env.dataSourceFactory

        if (video.isLive && video.hlsUrl != null) {
            return HlsMediaSource.Factory(dsf).createMediaSource(base.setUri(video.hlsUrl).build())
        }

        val choice = env.choiceFor(videoId)
        val videoStream = YoutubeResolver.pickVideo(video, choice.height)
        val audioStream = YoutubeResolver.pickAudio(video, choice.audioTrackId)

        if (videoStream == null || audioStream == null) {
            // No separate streams available: fall back to a combined stream, then to HLS.
            val muxed = YoutubeResolver.pickMuxed(video, choice.height)
            if (muxed != null) {
                return ProgressiveMediaSource.Factory(dsf).createMediaSource(base.setUri(muxed.url).build())
            }
            if (video.hlsUrl != null) {
                return HlsMediaSource.Factory(dsf).createMediaSource(base.setUri(video.hlsUrl).build())
            }
            throw YoutubeException("No playable streams found")
        }

        val sources = ArrayList<MediaSource>()
        sources += ProgressiveMediaSource.Factory(dsf).createMediaSource(base.setUri(videoStream.url).build())
        sources += ProgressiveMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(audioStream.url))
        video.subtitles.forEach { sub ->
            val config = MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                .setMimeType(sub.mimeType)
                .setLanguage(sub.language)
                .setLabel(sub.label)
                .setSelectionFlags(0)
                .build()
            sources += SingleSampleMediaSource.Factory(dsf).createMediaSource(config, C.TIME_UNSET)
        }
        return MergingMediaSource(false, true, *sources.toTypedArray())
    }

    override fun onChildSourceInfoRefreshed(childSourceId: Unit, mediaSource: MediaSource, newTimeline: Timeline) {
        refreshSourceInfo(newTimeline)
    }

    @Throws(IOException::class)
    override fun maybeThrowSourceInfoRefreshError() {
        failure?.let { throw it }
        super.maybeThrowSourceInfoRefreshError()
    }

    override fun createPeriod(id: MediaSource.MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod =
        checkNotNull(child) { "Source not resolved yet" }.createPeriod(id, allocator, startPositionUs)

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        child?.releasePeriod(mediaPeriod)
    }

    override fun releaseSourceInternal() {
        released = true
        future?.cancel(true)
        playbackHandler?.removeCallbacksAndMessages(null)
        playbackHandler = null
        child = null
        super.releaseSourceInternal()
    }
}
