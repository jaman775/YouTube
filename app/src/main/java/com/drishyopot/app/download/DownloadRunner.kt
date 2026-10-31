package com.drishyopot.app.download

import android.content.Context
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import com.drishyopot.app.R
import com.drishyopot.app.diagnostics.DiagnosticLog
import com.drishyopot.app.player.yt.YoutubeSourceEnv
import com.drishyopot.app.yt.YoutubeException
import com.drishyopot.app.yt.YoutubeResolver
import com.drishyopot.app.yt.YtAudioStream
import com.drishyopot.app.yt.YtSubtitle
import com.drishyopot.app.yt.YtVideo
import com.drishyopot.app.yt.YtVideoStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.BitSet
import java.util.concurrent.atomic.AtomicLong

class DlException(val error: DlError, detail: String? = null) : Exception(detail ?: error.name)

/** What the runner reports back; the center applies it on the main thread. */
interface RunnerEvents {
    fun progress(id: String, downloaded: Long, total: Long, speedBps: Long, etaSec: Long, video: PartState, audio: PartState)
    fun merging(id: String)
    fun completed(id: String, uri: String, name: String)
    fun failed(id: String, error: DlError, detail: String?)
    /** The run ended because [shouldStop] became true (pause, cancel or network loss). */
    fun stopped(id: String)
}

/** Runs one download from resolving to the finished file. Blocking; call from a worker thread. */
class DownloadRunner(
    private val context: Context,
    private val task: DownloadTask,
    private val connections: Int,
    private val events: RunnerEvents,
    private val isOnline: () -> Boolean,
    private val shouldStop: () -> Boolean,
) {
    private val request = task.request
    private val ua = YoutubeSourceEnv.USER_AGENT
    private val workDir = File(File(context.filesDir, "downloads"), task.id).also { it.mkdirs() }
    private val writer = OutputWriter(context)

    fun run() {
        try {
            execute()
        } catch (_: StoppedException) {
            events.stopped(task.id)
        } catch (e: DlException) {
            events.failed(task.id, e.error, e.message)
        } catch (e: StorageFullException) {
            events.failed(task.id, DlError.STORAGE, null)
        } catch (e: HttpStatusException) {
            events.failed(task.id, when (e.code) { 403, 410 -> DlError.HTTP_403; 429 -> DlError.HTTP_429; else -> DlError.OTHER }, e.message)
        } catch (e: IOException) {
            // Offline: wait for the network instead of failing.
            if (!isOnline()) events.stopped(task.id) else events.failed(task.id, DlError.NETWORK, e.message)
        } catch (_: OutOfMemoryError) {
            events.failed(task.id, DlError.MEMORY, null)
        } catch (e: Exception) {
            DiagnosticLog.log("Downloads", "Unexpected failure for ${request.videoId}", e)
            events.failed(task.id, DlError.OTHER, e.message)
        }
    }

    private fun execute() {
        val video = resolve()
        if (video.isLive) throw DlException(DlError.LIVE)

        val pickedVideo = if (request.mode == DlMode.VIDEO) pickVideo(video) else null
        val pickedAudio = pickAudio(video, pickedVideo) ?: throw DlException(DlError.NO_STREAM)
        if (request.mode == DlMode.VIDEO && pickedVideo == null) throw DlException(DlError.NO_STREAM)

        val audioUrls = refreshingUrls(pickedAudio.url) { fresh -> fresh.audio.firstOrNull { it.trackId == pickedAudio.trackId && it.bitrate == pickedAudio.bitrate }?.url }
        val videoUrls = pickedVideo?.let { v ->
            refreshingUrls(v.url) { fresh -> fresh.videoOnly.firstOrNull { it.height == v.height && it.family == v.family && it.fps == v.fps }?.url }
        }

        // Sizes (re-used when resuming)
        val videoLength = if (pickedVideo != null) task.video.length.takeIf { it > 0 } ?: probe(videoUrls!!) else 0L
        val audioLength = task.audio.length.takeIf { it > 0 } ?: probe(audioUrls)
        val total = videoLength + audioLength
        checkStorage(total)

        val videoBits = BlockBits.decode(task.video.doneBlocks)
        val audioBits = BlockBits.decode(task.audio.doneBlocks)
        val counter = AtomicLong(
            BlockBits.doneBytes(videoBits, videoLength, PartDownloader.BLOCK) + BlockBits.doneBytes(audioBits, audioLength, PartDownloader.BLOCK),
        )
        val reporter = Reporter(total, counter, videoLength, audioLength, videoBits, audioBits)
        reporter.report(force = true)

        if (pickedVideo != null) {
            downloadPart(File(workDir, "video.part"), videoLength, videoBits, videoUrls!!, counter, reporter)
        }
        downloadPart(File(workDir, "audio.part"), audioLength, audioBits, audioUrls, counter, reporter)
        reporter.report(force = true)

        verifyPart(File(workDir, "audio.part"), audioLength, audioBits)
        if (pickedVideo != null) verifyPart(File(workDir, "video.part"), videoLength, videoBits)

        val baseName = FileNames.sanitize(request.fileName.ifBlank { video.title })
        saveAttachments(video, baseName)

        val plan = if (pickedVideo != null) ContainerPlan.forStreams(pickedVideo.family, pickedAudio.codec) else null
        val finalName: String
        val published: OutputWriter.Published
        if (pickedVideo == null) {
            val opus = pickedAudio.codec?.contains("opus", true) == true
            val ext = if (opus) "webm" else "m4a"
            finalName = "$baseName.$ext"
            published = writer.publishMedia(File(workDir, "audio.part"), finalName, if (opus) "audio/webm" else "audio/mp4", audio = true)
        } else {
            events.merging(task.id)
            val merged = File(workDir, "merged.${plan!!.extension}")
            try {
                MediaMuxing.merge(File(workDir, "video.part"), File(workDir, "audio.part"), merged, plan, shouldStop)
            } catch (e: StoppedException) {
                throw e
            } catch (e: Exception) {
                DiagnosticLog.log("Downloads", "Merge failed for ${request.videoId}", e)
                throw DlException(DlError.MERGE_FAILED, e.message)
            }
            if (!MediaMuxing.verify(merged, expectVideo = true, expectedDurationMs = video.durationMs)) {
                throw DlException(DlError.VERIFY_FAILED)
            }
            finalName = "$baseName.${plan.extension}"
            published = writer.publishMedia(merged, finalName, plan.mimeType, audio = false)
        }
        workDir.deleteRecursively()
        events.completed(task.id, published.uri, published.displayName)
    }

    // ---- Steps -----------------------------------------------------------------------------------

    private fun resolve(): YtVideo = try {
        YoutubeResolver.resolve(request.videoId)
    } catch (e: YoutubeException) {
        val message = e.message.orEmpty()
        throw when {
            message.contains("premiere", true) -> DlException(DlError.PREMIERE, message)
            message.contains("live", true) -> DlException(DlError.LIVE, message)
            !isOnline() -> StoppedException()
            else -> DlException(DlError.OTHER, message)
        }
    }

    /** Nearest available height at or below the request, otherwise the lowest above it. */
    private fun pickVideo(video: YtVideo): YtVideoStream? {
        val pool = video.videoOnly.filter { it.height > 0 }
        if (pool.isEmpty()) return null
        val heights = pool.map { it.height }.distinct().sorted()
        val target = heights.lastOrNull { it <= request.height } ?: heights.first()
        val atHeight = pool.filter { it.height == target }
        val wanted = request.family.takeIf { it.isNotBlank() }
        val order = listOfNotNull(wanted) + listOf("avc", "vp9", "av1", "hevc")
        return atHeight.sortedWith(
            compareBy<YtVideoStream> { order.indexOf(it.family).let { i -> if (i < 0) 99 else i } }
                .thenByDescending { it.fps }.thenByDescending { it.bitrate },
        ).first()
    }

    private fun pickAudio(video: YtVideo, picked: YtVideoStream?): YtAudioStream? {
        val wantOpus = picked != null && (picked.family == "vp9" || picked.family == "av1")
        val compatible = video.audio.filter {
            if (picked == null) it.codec?.startsWith("mp4a") == true
            else if (wantOpus) it.codec?.contains("opus", true) == true
            else it.codec?.startsWith("mp4a") == true
        }
        val pool = compatible.ifEmpty { video.audio }
        return YoutubeResolver.pickAudio(video.copy(audio = pool), request.audioTrackId)
    }

    private fun refreshingUrls(initial: String, find: (YtVideo) -> String?) = object : UrlSource {
        @Volatile private var url = initial
        override fun current() = url
        override fun refresh(): Boolean {
            val fresh = try { YoutubeResolver.resolve(request.videoId, forceRefresh = true) } catch (_: Exception) { return false }
            val newUrl = find(fresh) ?: return false
            url = newUrl
            return true
        }
    }

    private fun probe(urls: UrlSource): Long = try {
        PartDownloader.probeLength(urls.current(), ua)
    } catch (e: HttpStatusException) {
        if ((e.code == 403 || e.code == 410) && urls.refresh()) PartDownloader.probeLength(urls.current(), ua) else throw e
    }

    private fun checkStorage(total: Long) {
        val needed = (total * 2.2).toLong()
        val free = try { StatFs(workDir.absolutePath).availableBytes } catch (_: Exception) { Long.MAX_VALUE }
        if (free < needed) throw DlException(DlError.STORAGE, "Needs ${needed / 1_000_000} MB, free ${free / 1_000_000} MB")
    }

    private fun downloadPart(file: File, length: Long, bits: BitSet, urls: UrlSource, counter: AtomicLong, reporter: Reporter) {
        PartDownloader(
            file = file, length = length, done = bits, connections = connections, urls = urls, userAgent = ua,
            onBytes = { counter.addAndGet(it); reporter.report(force = false) },
            onBlockDone = { reporter.report(force = true) },
            isStopped = shouldStop,
        ).run()
    }

    private fun verifyPart(file: File, length: Long, bits: BitSet) {
        val blocks = ((length + PartDownloader.BLOCK - 1) / PartDownloader.BLOCK).toInt()
        if (!file.exists() || file.length() != length || bits.cardinality() < blocks) throw DlException(DlError.VERIFY_FAILED)
    }

    private fun saveAttachments(video: YtVideo, baseName: String) {
        for (lang in request.subtitleLangs) {
            val sub = matchSubtitle(video.subtitles, lang) ?: continue
            try {
                val bytes = fetchBytes(sub.url) ?: continue
                val ext = when {
                    sub.mimeType.contains("vtt") -> "vtt"
                    sub.mimeType.contains("ttml") -> "ttml"
                    else -> "srt"
                }
                writer.publishAttachment(bytes, "$baseName.${sub.language}.$ext", sub.mimeType)
            } catch (e: Exception) {
                DiagnosticLog.log("Downloads", "Subtitle $lang failed", e)
            }
        }
        if (request.includeCover) {
            request.thumbnail?.let { url ->
                fetchBytes(url)?.let { writer.publishAttachment(it, "$baseName.jpg", "image/jpeg") }
            }
        }
    }

    private fun matchSubtitle(subs: List<YtSubtitle>, lang: String): YtSubtitle? {
        val l = lang.lowercase()
        val matches = subs.filter { it.language.lowercase() == l || it.language.lowercase().startsWith("$l-") }
        return matches.firstOrNull { !it.autoGenerated } ?: matches.firstOrNull()
    }

    private fun fetchBytes(url: String): ByteArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", ua)
            if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null
        } finally { c.disconnect() }
    }

    /** Throttled progress and speed/ETA reporting. */
    private inner class Reporter(
        private val total: Long,
        private val counter: AtomicLong,
        private val videoLength: Long,
        private val audioLength: Long,
        private val videoBits: BitSet,
        private val audioBits: BitSet,
    ) {
        private var lastAt = SystemClock.elapsedRealtime()
        private var lastBytes = counter.get()
        private var speed = 0.0

        @Synchronized
        fun report(force: Boolean) {
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - lastAt
            if (!force && elapsed < 500) return
            val bytes = counter.get().coerceIn(0, total)
            if (elapsed > 0) {
                val instant = (bytes - lastBytes) * 1000.0 / elapsed
                speed = if (speed == 0.0) instant else speed * 0.7 + instant * 0.3
                lastAt = now
                lastBytes = bytes
            }
            val eta = if (speed > 1) ((total - bytes) / speed).toLong() else -1L
            events.progress(
                task.id, bytes, total, speed.toLong().coerceAtLeast(0), eta,
                synchronized(videoBits) { PartState(videoLength, BlockBits.encode(videoBits)) },
                synchronized(audioBits) { PartState(audioLength, BlockBits.encode(audioBits)) },
            )
        }
    }
}
