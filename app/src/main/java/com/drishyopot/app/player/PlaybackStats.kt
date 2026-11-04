@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.drishyopot.app.player

import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener

/** Collects the numbers shown in the Info dialog. */
class PlaybackStats(private val player: ExoPlayer) {
    @Volatile var videoDecoder: String? = null
    @Volatile var audioDecoder: String? = null
    @Volatile var droppedFrames: Int = 0
    @Volatile var firstFrameMs: Long = -1
    @Volatile var bufferEvents: Int = 0
    private var prepareAt = SystemClock.elapsedRealtime()
    private val bufferTimes = ArrayDeque<Long>()
    private val frameSamples = ArrayDeque<Pair<Long, Int>>()

    private val analytics = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
            videoDecoder = decoderName
        }
        override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
            audioDecoder = decoderName
        }
        override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
            this@PlaybackStats.droppedFrames += droppedFrames
        }
    }

    private val listener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            if (firstFrameMs < 0) firstFrameMs = SystemClock.elapsedRealtime() - prepareAt
        }
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_BUFFERING) {
                val now = SystemClock.elapsedRealtime()
                bufferTimes.addLast(now)
                bufferEvents++
                while (bufferTimes.isNotEmpty() && now - bufferTimes.first() > 60_000) bufferTimes.removeFirst()
            }
        }
    }

    init {
        player.addAnalyticsListener(analytics)
        player.addListener(listener)
    }

    fun markPrepare() {
        prepareAt = SystemClock.elapsedRealtime()
        firstFrameMs = -1
    }

    fun buffersPerMinute(): Int {
        val now = SystemClock.elapsedRealtime()
        return bufferTimes.count { now - it <= 60_000 }
    }

    /** Frames rendered in roughly the last 5 s, from the decoder counters. */
    fun renderedFps(): Float {
        val counters = player.videoDecoderCounters ?: return 0f
        counters.ensureUpdated()
        val now = SystemClock.elapsedRealtime()
        frameSamples.addLast(now to counters.renderedOutputBufferCount)
        while (frameSamples.size > 1 && now - frameSamples.first().first > 5_000) frameSamples.removeFirst()
        val first = frameSamples.first()
        val dt = (now - first.first) / 1000f
        return if (dt <= 0.5f) 0f else (counters.renderedOutputBufferCount - first.second) / dt
    }

    fun totalFrames(): Int = player.videoDecoderCounters?.let {
        it.ensureUpdated(); it.renderedOutputBufferCount + it.skippedOutputBufferCount + it.droppedBufferCount
    } ?: 0

    fun release() {
        player.removeAnalyticsListener(analytics)
        player.removeListener(listener)
    }
}
