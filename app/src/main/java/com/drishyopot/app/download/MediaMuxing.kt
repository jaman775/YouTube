package com.drishyopot.app.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.abs

/** Container choice for a video + audio pair. */
data class ContainerPlan(val extension: String, val mimeType: String, val muxerFormat: Int) {
    companion object {
        val MP4 = ContainerPlan("mp4", "video/mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val WEBM = ContainerPlan("webm", "video/webm", MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM)

        /** H.264/HEVC + AAC go to MP4, VP9/AV1 + Opus go to WebM. */
        fun forStreams(videoFamily: String, audioCodec: String?): ContainerPlan {
            val opus = audioCodec?.contains("opus", true) == true
            return if ((videoFamily == "vp9" || videoFamily == "av1") && opus) WEBM else MP4
        }
    }
}

/** Joins a video-only and an audio-only file into one container without re-encoding. */
object MediaMuxing {
    fun merge(video: File, audio: File, out: File, plan: ContainerPlan, isStopped: () -> Boolean) {
        val videoEx = MediaExtractor()
        val audioEx = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            videoEx.setDataSource(video.absolutePath)
            audioEx.setDataSource(audio.absolutePath)
            val vTrack = findTrack(videoEx, "video/")
            val aTrack = findTrack(audioEx, "audio/")
            muxer = MediaMuxer(out.absolutePath, plan.muxerFormat)
            val vOut = muxer.addTrack(videoEx.getTrackFormat(vTrack))
            val aOut = muxer.addTrack(audioEx.getTrackFormat(aTrack))
            videoEx.selectTrack(vTrack)
            audioEx.selectTrack(aTrack)
            muxer.start()
            started = true

            val bufferSize = maxOf(bufferFor(videoEx.getTrackFormat(vTrack)), bufferFor(audioEx.getTrackFormat(aTrack)))
            val buffer = ByteBuffer.allocate(bufferSize)
            val info = MediaCodec.BufferInfo()
            // Interleave by timestamp so the result also plays well while streaming.
            while (true) {
                if (isStopped()) throw StoppedException()
                val vTime = videoEx.sampleTime
                val aTime = audioEx.sampleTime
                if (vTime < 0 && aTime < 0) break
                val useVideo = aTime < 0 || (vTime in 0..aTime)
                val extractor = if (useVideo) videoEx else audioEx
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) { extractor.advance(); continue }
                info.set(0, size, extractor.sampleTime, if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                muxer.writeSampleData(if (useVideo) vOut else aOut, buffer, info)
                extractor.advance()
            }
        } finally {
            try { if (started) muxer?.stop() } catch (_: Exception) { }
            try { muxer?.release() } catch (_: Exception) { }
            videoEx.release()
            audioEx.release()
        }
    }

    private fun findTrack(extractor: MediaExtractor, prefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true) return i
        }
        throw IOException("No $prefix track")
    }

    private fun bufferFor(format: MediaFormat): Int =
        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(1 shl 20)
        else 2 shl 20

    /** Re-opens the output and checks it has the expected tracks and roughly the expected length. */
    fun verify(file: File, expectVideo: Boolean, expectedDurationMs: Long): Boolean {
        if (!file.exists() || file.length() <= 0) return false
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var video = false
            var audio = false
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) video = true
                if (mime.startsWith("audio/")) audio = true
            }
            if (!audio || (expectVideo && !video)) return false
        } catch (_: Exception) {
            return false
        } finally { extractor.release() }

        if (expectedDurationMs <= 0) return true
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val actual = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return true
            abs(actual - expectedDurationMs) <= maxOf(5_000L, expectedDurationMs / 10)
        } catch (_: Exception) {
            true // duration unreadable, but the tracks were fine
        } finally { retriever.release() }
    }
}
