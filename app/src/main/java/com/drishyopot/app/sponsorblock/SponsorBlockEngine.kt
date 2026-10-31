@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.drishyopot.app.sponsorblock

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.drishyopot.app.diagnostics.DiagnosticLog
import com.drishyopot.app.settings.PrefKeys
import com.drishyopot.app.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Skips SponsorBlock segments for the item currently loaded in [player].
 * Runs inside the process-wide hub, so skipping also works in the background and in PiP.
 */
class SponsorBlockEngine(
    private val app: Application,
    private val player: ExoPlayer,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    var allSegments by mutableStateOf<List<SponsorSegment>>(emptyList())
        private set
    var status by mutableStateOf(SbStatus.IDLE)
        private set
    var pending by mutableStateOf<PendingSkip?>(null)
        private set
    var skipEvent by mutableStateOf<SkipEvent?>(null)
        private set

    private val main = Handler(Looper.getMainLooper())
    private val handled = HashSet<String>()
    private var currentItem: MediaItem? = null
    private var fetchJob: Job? = null
    private var durationChecked = false
    private var muted = false
    private var eventCounter = 0L

    private val tickTask = object : Runnable {
        override fun run() {
            try { tick() } catch (e: Exception) { DiagnosticLog.log("SponsorBlock", "tick failed", e) }
            main.postDelayed(this, TICK_MS)
        }
    }

    fun start() {
        main.removeCallbacks(tickTask)
        main.post(tickTask)
        // If the user enables a category after the video loaded, fetch then.
        scope.launch {
            settings.revision.collect {
                if ((status == SbStatus.OFF || status == SbStatus.IDLE) && enabledCategories().isNotEmpty()) {
                    onItemChanged(currentItem)
                }
            }
        }
    }

    fun enabledCategories(): Set<SbCategory> = SbCategory.entries.filter { settings.bool(it.prefKey) }.toSet()

    /** Segments the user wants acted on, used for the timeline and the segment list. */
    fun visibleSegments(): List<SponsorSegment> {
        val enabled = enabledCategories()
        return allSegments.filter { it.category in enabled }
    }

    fun onItemChanged(item: MediaItem?) {
        currentItem = item
        fetchJob?.cancel()
        allSegments = emptyList()
        pending = null
        handled.clear()
        durationChecked = false
        restoreVolume()

        val id = VideoIds.extract(item)
        when {
            item == null -> { status = SbStatus.IDLE; return }
            id == null -> { status = SbStatus.NO_ID; return }
            enabledCategories().isEmpty() -> { status = SbStatus.OFF; return }
        }
        status = SbStatus.LOADING
        fetchJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { SponsorBlockApi.fetch(app, id!!) }
                allSegments = result
                status = SbStatus.READY
            } catch (e: SponsorBlockApi.FetchException) {
                DiagnosticLog.log("SponsorBlock", "Fetch failed for $id: ${e.message}")
                status = SbStatus.ERROR
            }
        }
    }

    fun retry() = onItemChanged(currentItem)

    // ---- Skipping -----------------------------------------------------------------

    private fun tick() {
        if (allSegments.isEmpty()) return

        val duration = player.duration
        if (!durationChecked && duration != C.TIME_UNSET && duration > 0) {
            durationChecked = true
            // Segments submitted for a different cut of the video would land at wrong times.
            allSegments = allSegments.filter { it.videoDurationMs <= 0 || abs(it.videoDurationMs - duration) <= DURATION_TOLERANCE_MS }
        }

        val pos = player.currentPosition
        // Seeking back before a handled segment re-arms it.
        handled.removeAll { uuid ->
            val seg = allSegments.firstOrNull { it.uuid == uuid }
            seg == null || pos < seg.startMs - REARM_MS
        }
        if (!player.isPlaying) return

        val enabled = enabledCategories()
        val active = allSegments.filter { it.category in enabled && it.uuid !in handled }

        // Mute segments
        val muteSeg = active.firstOrNull { it.actionType == SbAction.MUTE && pos in it.startMs until it.endMs }
        if (muteSeg != null) { if (!muted) { player.volume = 0f; muted = true } } else restoreVolume()

        // Skip segments
        val skipSeg = active.firstOrNull { it.actionType == SbAction.SKIP && pos >= it.startMs && pos < it.endMs - END_GUARD_MS }
        // Highlight: jump forward to the highlight point, only near the beginning of the video.
        val poiSeg = if (skipSeg == null) {
            active.firstOrNull { it.isPoi && pos < POI_WINDOW_MS && pos < it.startMs - 1_000 }
        } else null

        val target = skipSeg ?: poiSeg
        if (target == null) { pending = null; return }

        if (!settings.bool(PrefKeys.SPONSOR_COUNTDOWN)) {
            perform(target)
            return
        }
        val now = SystemClock.elapsedRealtime()
        val current = pending?.takeIf { it.segment.uuid == target.uuid } ?: PendingSkip(target, COUNTDOWN_MS, now)
        val remaining = COUNTDOWN_MS - (now - current.startedAt)
        if (remaining <= 0) perform(target) else pending = current.copy(remainingMs = remaining)
    }

    private fun perform(seg: SponsorSegment) {
        handled += seg.uuid
        pending = null
        val destination = if (seg.isPoi) seg.startMs else seg.endMs
        val duration = player.duration
        if (duration != C.TIME_UNSET && destination >= duration - END_GUARD_MS) {
            player.seekTo((duration - 1).coerceAtLeast(0))
        } else {
            player.seekTo(destination)
        }
        skipEvent = SkipEvent(++eventCounter, seg.category)
    }

    /** "Skip now" on the countdown chip. */
    fun skipPendingNow() { pending?.let { perform(it.segment) } }

    /** "Cancel" on the countdown chip: leave this segment alone until the user seeks back. */
    fun cancelPending() {
        pending?.let { handled += it.segment.uuid }
        pending = null
    }

    /** Manual jump from the segment list. */
    fun skipSegment(seg: SponsorSegment) { handled += seg.uuid; perform(seg) }

    private fun restoreVolume() {
        if (muted) { player.volume = 1f; muted = false }
    }

    companion object {
        private const val TICK_MS = 200L
        private const val COUNTDOWN_MS = 3_000L
        private const val END_GUARD_MS = 300L
        private const val REARM_MS = 1_500L
        private const val POI_WINDOW_MS = 5_000L
        private const val DURATION_TOLERANCE_MS = 5_000L
    }
}
