@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.drishyopot.app.player

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.drishyopot.app.MainActivity
import com.drishyopot.app.diagnostics.DiagnosticLog
import com.drishyopot.app.player.yt.YoutubeMediaSource
import com.drishyopot.app.player.yt.YoutubeSourceEnv
import com.drishyopot.app.settings.PrefKeys
import com.drishyopot.app.yt.StreamChoice
import com.drishyopot.app.yt.YoutubeException
import com.drishyopot.app.yt.YoutubeResolver
import com.drishyopot.app.yt.YoutubeUrls
import com.drishyopot.app.yt.YtLink
import com.drishyopot.app.yt.YtVideo
import androidx.compose.runtime.mutableStateMapOf
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import com.drishyopot.app.settings.SettingsStore
import com.drishyopot.app.sponsorblock.SponsorBlockEngine
import com.drishyopot.app.sponsorblock.VideoIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide owner of the single ExoPlayer, its MediaSession and the queue.
 * The Activity UI, the mini player, PiP and the notification service all share it.
 */
class PlaybackHub(private val app: Application, private val settings: SettingsStore) {
    val prefs = PlayerPrefs(app)
    private val queueStore = QueueStore(app)
    private val main = Handler(Looper.getMainLooper())

    val player: ExoPlayer = ExoPlayer.Builder(app)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    val stats = PlaybackStats(player)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val sponsor = SponsorBlockEngine(app, player, settings, scope)

    // ---- YouTube ---------------------------------------------------------------------
    private val choices = ConcurrentHashMap<String, StreamChoice>()
    /** Videos the resolver has finished with; the UI reads quality/audio options from here. */
    val resolvedVideos = mutableStateMapOf<String, YtVideo>()
    private val defaultFactory = DefaultMediaSourceFactory(app)
    private val ytEnv = YoutubeSourceEnv(
        executor = Executors.newCachedThreadPool(),
        choiceFor = { id -> choiceFor(id) },
        onResolved = { video -> main.post { resolvedVideos[video.id] = video } },
    )
    @Volatile private var internalEdit = false
    var message by mutableStateOf<String?>(null)
    /** Set by the cast controller so closing the player also ends a cast session. */
    var onClosePlayer: (() -> Unit)? = null
    var session: MediaSession? = null
        private set

    // Compose-observable state
    var queueVersion by mutableIntStateOf(0)
        private set
    var currentItem by mutableStateOf<MediaItem?>(null)
        private set
    var miniVisible by mutableStateOf(false)
    var inPip by mutableStateOf(false)
    var audioOnly by mutableStateOf(prefs.audioOnly)
        private set
    var endMode by mutableStateOf(prefs.endMode)
        private set
    var autoplay by mutableStateOf(prefs.autoplay)
        private set

    /** Called when something PiP-related changed (playing state, video size, screen visibility). */
    var onPipStateChanged: (() -> Unit)? = null
    var playerScreenVisible: Boolean = false
        set(value) { field = value; onPipStateChanged?.invoke() }

    private val savePositionTask = object : Runnable {
        override fun run() {
            savePosition()
            if (player.isPlaying) main.postDelayed(this, 5_000)
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentItem = mediaItem
            queueChanged()
            sponsor.onItemChanged(mediaItem)
            if (internalEdit || mediaItem == null || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) return
            if (settings.bool(PrefKeys.REMEMBER_LAST_POSITION)) {
                val pos = prefs.position(mediaItem.mediaId)
                if (pos > 5_000) player.seekTo(pos)
            }
        }
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            currentItem = player.currentMediaItem
            queueChanged()
        }
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) player.currentMediaItem?.let { prefs.clearPosition(it.mediaId) }
            onPipStateChanged?.invoke()
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            main.removeCallbacks(savePositionTask)
            if (isPlaying) {
                main.postDelayed(savePositionTask, 5_000)
                startService()
            } else {
                savePosition()
            }
            onPipStateChanged?.invoke()
        }
        override fun onVideoSizeChanged(videoSize: VideoSize) { onPipStateChanged?.invoke() }
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            DiagnosticLog.log("Hub", "Player error ${error.errorCodeName}", error)
        }
    }

    init {
        player.addListener(listener)
        restoreQueue()
        sponsor.start()
        sponsor.onItemChanged(player.currentMediaItem)
        if (settings.bool(PrefKeys.REMEMBER_PLAYBACK_SPEED)) player.setPlaybackSpeed(prefs.speed)
        if (settings.bool(PrefKeys.REMEMBER_QUALITY) && prefs.qualityHeight > 0) TrackOptions.applyQuality(player, prefs.qualityHeight)
        applyAudioOnly()
        applyEndMode()
    }

    // ---- Session / service -------------------------------------------------

    fun ensureSession(): MediaSession {
        session?.let { return it }
        val open = PendingIntent.getActivity(
            app, 0, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return MediaSession.Builder(app, player).setId("drishyopot").setSessionActivity(open).build().also { session = it }
    }

    fun startService() {
        try {
            ensureSession()
            app.startService(Intent(app, PlaybackService::class.java))
        } catch (e: Exception) {
            DiagnosticLog.log("Hub", "Could not start playback service", e)
        }
    }

    /** "Close player": stop playback, drop the mini player and the notification. */
    fun closePlayer() {
        onClosePlayer?.invoke()
        player.pause()
        player.stop()
        miniVisible = false
        app.stopService(Intent(app, PlaybackService::class.java))
    }

    // ---- Sources -------------------------------------------------------------------

    fun choiceFor(videoId: String): StreamChoice =
        choices[videoId] ?: StreamChoice(
            height = if (settings.bool(PrefKeys.REMEMBER_QUALITY)) prefs.qualityHeight else 0,
        )

    fun youtubeIdOf(item: MediaItem?): String? =
        (item?.mediaId?.let { YoutubeUrls.parse(it) } as? YtLink.Video)?.id

    private fun sourceFor(item: MediaItem): MediaSource {
        val id = youtubeIdOf(item)
        return if (id != null) YoutubeMediaSource(item, id, ytEnv) else defaultFactory.createMediaSource(item)
    }

    /** Re-creates the current source so a new quality/audio choice takes effect, keeping the position. */
    private fun reloadCurrent() {
        val item = player.currentMediaItem ?: return
        val index = player.currentMediaItemIndex
        val position = player.currentPosition
        val resume = player.playWhenReady
        internalEdit = true
        try {
            player.addMediaSource(index + 1, sourceFor(item))
            player.removeMediaItem(index)
            player.seekTo(index, position)
            player.playWhenReady = resume
        } finally {
            internalEdit = false
        }
    }

    fun setYoutubeQuality(videoId: String, height: Int) {
        choices[videoId] = choiceFor(videoId).copy(height = height)
        prefs.qualityHeight = height
        if (youtubeIdOf(player.currentMediaItem) == videoId) reloadCurrent()
    }

    fun setYoutubeAudio(videoId: String, trackId: String?) {
        choices[videoId] = choiceFor(videoId).copy(audioTrackId = trackId)
        if (youtubeIdOf(player.currentMediaItem) == videoId) reloadCurrent()
    }

    // ---- Queue ---------------------------------------------------------------

    private fun mediaItem(url: String, title: String?, videoId: String? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(url)
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title?.takeIf { it.isNotBlank() } ?: defaultTitle(url))
                    .setExtras(if (VideoIds.isValid(videoId)) Bundle().apply { putString(VideoIds.EXTRA_KEY, videoId) } else null)
                    .build(),
            )
            .build()

    private fun defaultTitle(url: String) = url.substringBefore('?').substringAfterLast('/').ifBlank { url }

    fun queueItems(): List<QueueEntry> = (0 until player.mediaItemCount).map {
        val item = player.getMediaItemAt(it)
        QueueEntry(
            item.mediaId,
            item.mediaMetadata.title?.toString() ?: defaultTitle(item.mediaId),
            item.mediaMetadata.extras?.getString(VideoIds.EXTRA_KEY),
        )
    }

    fun currentIndex(): Int = player.currentMediaItemIndex

    private fun indexOf(url: String): Int = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == url } ?: -1

    /** Plays [url], adding it to the queue if needed. */
    fun play(rawUrl: String, title: String? = null, videoId: String? = null, startSeconds: Int = 0) {
        val (url, id) = normalise(rawUrl, videoId)
        var idx = indexOf(url)
        if (idx < 0) {
            player.addMediaSource(sourceFor(mediaItem(url, title, id)))
            idx = player.mediaItemCount - 1
        }
        miniVisible = false
        if (player.currentMediaItemIndex == idx && player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED) {
            player.play()
        } else {
            player.seekTo(idx, if (startSeconds > 0) startSeconds * 1000L else C.TIME_UNSET)
            player.prepare()
            player.play()
        }
        startService()
    }

    /** Canonical queue key for a URL, plus the video ID SponsorBlock should use. */
    private fun normalise(url: String, explicitId: String?): Pair<String, String?> {
        val link = YoutubeUrls.parse(url)
        return if (link is YtLink.Video) YoutubeUrls.watchUrl(link.id) to link.id else url to explicitId
    }

    /** Opens any YouTube link: video, playlist, or null when it is a plain page. */
    fun openLink(link: YtLink): Boolean = when (link) {
        is YtLink.Video -> {
            if (link.listId != null && !link.listId.startsWith("RD")) playPlaylist(YoutubeUrls.playlistUrl(link.listId), link.id)
            else play(YoutubeUrls.watchUrl(link.id), startSeconds = link.startSeconds)
            true
        }
        is YtLink.Playlist -> { playPlaylist(YoutubeUrls.playlistUrl(link.id), null); true }
        is YtLink.Page -> false
    }

    /** Loads a playlist into the queue (lazy: nothing is resolved until each item plays). */
    fun playPlaylist(url: String, startVideoId: String?) {
        scope.launch {
            try {
                message = null
                val playlist = withContext(Dispatchers.IO) { YoutubeResolver.playlist(url) }
                if (playlist.items.isEmpty()) { message = "Empty playlist"; return@launch }
                playlist.items.forEach { enqueue(YoutubeUrls.watchUrl(it.id), it.title, it.id) }
                val first = startVideoId ?: playlist.items.first().id
                play(YoutubeUrls.watchUrl(first))
            } catch (e: YoutubeException) {
                message = e.message
            }
        }
    }

    /** Returns false when the item was already queued. */
    fun enqueue(rawUrl: String, title: String? = null, videoId: String? = null): Boolean {
        val (url, id) = normalise(rawUrl, videoId)
        if (indexOf(url) >= 0) return false
        player.addMediaSource(sourceFor(mediaItem(url, title, id)))
        return true
    }

    fun removeAt(index: Int) { if (index in 0 until player.mediaItemCount) player.removeMediaItem(index) }

    fun move(from: Int, to: Int) {
        if (from in 0 until player.mediaItemCount && to in 0 until player.mediaItemCount) player.moveMediaItem(from, to)
    }

    fun jumpTo(index: Int) {
        if (index !in 0 until player.mediaItemCount) return
        player.seekTo(index, C.TIME_UNSET)
        player.prepare()
        player.play()
    }

    fun clearQueue() {
        player.stop()
        player.clearMediaItems()
        miniVisible = false
    }

    private fun queueChanged() {
        queueVersion++
        queueStore.save(queueItems(), player.currentMediaItemIndex.coerceAtLeast(0))
    }

    private fun restoreQueue() {
        val (items, index) = queueStore.load()
        if (items.isEmpty()) return
        player.setMediaSources(items.map { sourceFor(mediaItem(it.url, it.title, it.videoId)) }, index, C.TIME_UNSET)
        currentItem = player.currentMediaItem
        queueVersion++
    }

    private fun savePosition() {
        val item = player.currentMediaItem ?: return
        if (settings.bool(PrefKeys.REMEMBER_LAST_POSITION) && player.playbackState == Player.STATE_READY) {
            prefs.savePosition(item.mediaId, player.currentPosition)
        }
    }

    // ---- Modes ---------------------------------------------------------------

    fun setEndMode(mode: EndMode) { endMode = mode; prefs.endMode = mode; applyEndMode() }
    fun setAutoplay(on: Boolean) { autoplay = on; prefs.autoplay = on; applyEndMode() }

    fun cycleEndMode(): EndMode {
        val next = EndMode.entries[(endMode.ordinal + 1) % EndMode.entries.size]
        setEndMode(next)
        return next
    }

    private fun applyEndMode() {
        player.repeatMode = when (endMode) {
            EndMode.LOOP -> Player.REPEAT_MODE_ONE
            EndMode.SHUFFLE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        player.shuffleModeEnabled = endMode == EndMode.SHUFFLE
        player.pauseAtEndOfMediaItems = endMode == EndMode.PAUSE || !autoplay
    }

    fun setAudioOnly(on: Boolean) { audioOnly = on; prefs.audioOnly = on; applyAudioOnly() }

    private fun applyAudioOnly() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, audioOnly).build()
    }
}
