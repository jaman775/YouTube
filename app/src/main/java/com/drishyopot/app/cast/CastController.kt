@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.drishyopot.app.cast

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.drishyopot.app.R
import com.drishyopot.app.diagnostics.DiagnosticLog
import com.drishyopot.app.player.EndMode
import com.drishyopot.app.player.PlaybackHub
import com.drishyopot.app.player.yt.YoutubeSourceEnv
import com.drishyopot.app.yt.YoutubeResolver
import com.drishyopot.app.yt.YtVideo
import com.drishyopot.app.yt.YtVideoStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface

data class CastUi(
    val connecting: Boolean = false,
    val casting: Boolean = false,
    val deviceName: String? = null,
    val playerState: String = "IDLE",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val error: String? = null,
    val linkUrl: String? = null,
    val linkConnected: Boolean = false,
)

/** What to hand the receiver (or a browser) for the current item. */
private class Plan(
    val contentUrl: String,
    val contentType: String,
    val title: String,
    val imageUrl: String?,
    val durationMs: Long,
    val live: Boolean,
    val usingDash: Boolean,
)

/**
 * Coordinates discovery, the Cast session, the local proxy and the local player.
 * While casting the phone's player stays paused and the receiver is the source of truth.
 */
class CastController(private val app: Application, private val hub: PlaybackHub) {
    val discovery = CastDiscovery(app)
    var state by mutableStateOf(CastUi())
        private set

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: CastSession? = null
    private var proxy: LocalStreamProxy? = null
    private var generation = 0
    private var triedMuxed = false
    private var lastStatusAt = 0L
    private var basePosition = 0L
    private var linkActive = false

    private val ticker = object : Runnable {
        override fun run() {
            if (state.casting && state.playerState == "PLAYING") {
                val estimated = basePosition + (SystemClock.elapsedRealtime() - lastStatusAt)
                state = state.copy(positionMs = if (state.durationMs > 0) estimated.coerceAtMost(state.durationMs) else estimated)
            }
            if (linkActive) state = state.copy(linkConnected = proxy?.peerConnected() == true)
            if (state.casting || linkActive) main.postDelayed(this, 500)
        }
    }

    init {
        // Media-notification or headset buttons must not start the phone while the TV is playing.
        hub.player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && state.casting) hub.player.pause()
            }
        })
        hub.onClosePlayer = { endSession() }
    }

    // ---- Casting ---------------------------------------------------------------------------------

    fun castTo(device: CastDevice) {
        if (hub.player.currentMediaItem == null) {
            state = state.copy(error = app.getString(R.string.cast_no_stream))
            return
        }
        endSession()
        val gen = ++generation
        triedMuxed = false
        state = state.copy(connecting = true, casting = false, deviceName = device.name, error = null)
        val s = CastSession(device, object : CastSession.Listener {
            override fun onConnected() {
                main.post {
                    if (gen != generation) return@post
                    state = state.copy(connecting = false, casting = true)
                    val position = hub.player.currentPosition
                    hub.player.pause()
                    loadCurrent(position)
                    startTicker()
                }
            }
            override fun onMediaStatus(status: RemoteMediaStatus) { main.post { if (gen == generation) handleStatus(status) } }
            override fun onVolume(level: Float, muted: Boolean) { main.post { if (gen == generation) state = state.copy(volume = level, muted = muted) } }
            override fun onLoadFailed(reason: String) { main.post { if (gen == generation) handleLoadFailed(reason) } }
            override fun onClosed(error: String?) { main.post { if (gen == generation) handleClosed(error) } }
        })
        session = s
        s.connect()
    }

    private fun loadCurrent(positionMs: Long) {
        val item = hub.player.currentMediaItem ?: return
        val gen = generation
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { buildPlan(item, preferDash = !triedMuxed) } }
            if (gen != generation) return@launch
            result.onSuccess { plan ->
                state = state.copy(durationMs = plan.durationMs, positionMs = positionMs)
                basePosition = positionMs
                lastStatusAt = SystemClock.elapsedRealtime()
                session?.load(
                    contentUrl = plan.contentUrl,
                    contentType = plan.contentType,
                    title = plan.title,
                    imageUrl = plan.imageUrl,
                    durationSec = plan.durationMs / 1000.0,
                    live = plan.live,
                    startSec = positionMs / 1000.0,
                )
                usingDash = plan.usingDash
            }.onFailure {
                DiagnosticLog.log("Cast", "Could not prepare stream", it)
                state = state.copy(error = it.message ?: app.getString(R.string.cast_no_stream))
            }
        }
    }

    private var usingDash = false

    private fun handleStatus(status: RemoteMediaStatus) {
        lastStatusAt = SystemClock.elapsedRealtime()
        basePosition = status.positionMs
        state = state.copy(
            playerState = status.playerState,
            positionMs = status.positionMs,
            durationMs = status.durationMs.takeIf { it > 0 } ?: state.durationMs,
        )
        if (status.playerState == "IDLE") {
            when (status.idleReason) {
                "FINISHED" -> onRemoteFinished()
                "ERROR" -> handleLoadFailed("ERROR")
            }
        }
    }

    private fun onRemoteFinished() {
        when {
            hub.endMode == EndMode.LOOP -> loadCurrent(0)
            hub.player.hasNextMediaItem() && hub.autoplay && hub.endMode != EndMode.PAUSE -> {
                hub.player.seekToNextMediaItem()
                hub.player.pause()
                triedMuxed = false
                loadCurrent(0)
            }
        }
    }

    /** DASH is the riskiest path; if the receiver rejects it, retry once with a single combined file. */
    private fun handleLoadFailed(reason: String) {
        DiagnosticLog.log("Cast", "Receiver reported $reason (dash=$usingDash)")
        if (usingDash && !triedMuxed) {
            triedMuxed = true
            loadCurrent(basePosition)
        } else {
            state = state.copy(error = app.getString(R.string.cast_failed))
        }
    }

    private fun handleClosed(error: String?) {
        val wasCasting = state.casting
        val position = state.positionMs
        session = null
        state = state.copy(connecting = false, casting = false, error = if (error != null) app.getString(R.string.cast_failed) else null)
        stopProxyIfUnused()
        if (wasCasting) hub.player.seekTo(position)
    }

    fun togglePlay() {
        val s = session ?: return
        if (state.playerState == "PLAYING" || state.playerState == "BUFFERING") s.pause() else s.play()
    }

    fun seekTo(positionMs: Long) {
        basePosition = positionMs
        lastStatusAt = SystemClock.elapsedRealtime()
        state = state.copy(positionMs = positionMs)
        session?.seek(positionMs)
    }

    fun setVolume(level: Float) { state = state.copy(volume = level); session?.setVolume(level) }

    fun toggleMute() { session?.setMuted(!state.muted) }

    /** Stop casting and carry on playing on the phone from where the TV got to. */
    fun stopCasting() {
        val position = state.positionMs
        val resume = state.playerState == "PLAYING" || state.playerState == "BUFFERING"
        endSession()
        hub.player.seekTo(position)
        if (resume) hub.player.play()
    }

    /** Drops the connection without touching the local player. */
    fun endSession() {
        generation++
        session?.let { it.stopMedia(); it.close() }
        session = null
        state = state.copy(connecting = false, casting = false, playerState = "IDLE", error = null)
        stopProxyIfUnused()
    }

    // ---- Open on any device (link) ---------------------------------------------------------------

    fun startLink() {
        val item = hub.player.currentMediaItem
        if (item == null) { state = state.copy(error = app.getString(R.string.cast_no_stream)); return }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val plan = buildPlan(item, preferDash = false)
                    val p = proxy ?: throw IOException("proxy")
                    p.playerHtml = playerPage(plan)
                    "http://${localIp() ?: throw IOException(app.getString(R.string.cast_no_wifi))}:${p.port}/player"
                }
            }
            result.onSuccess {
                linkActive = true
                state = state.copy(linkUrl = it, linkConnected = false, error = null)
                startTicker()
            }.onFailure { state = state.copy(error = it.message ?: app.getString(R.string.cast_failed)) }
        }
    }

    fun stopLink() {
        linkActive = false
        state = state.copy(linkUrl = null, linkConnected = false)
        stopProxyIfUnused()
    }

    private fun playerPage(plan: Plan): String {
        val dash = plan.usingDash
        val video = if (dash) {
            """<video id="v" controls autoplay playsinline></video>
<script src="https://cdnjs.cloudflare.com/ajax/libs/dashjs/4.7.4/dash.all.min.js"></script>
<script>dashjs.MediaPlayer().create().initialize(document.getElementById('v'), ${jsString(plan.contentUrl)}, true);</script>"""
        } else {
            """<video id="v" controls autoplay playsinline src="${plan.contentUrl}"></video>"""
        }
        return """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${plan.title.replace("<", "&lt;")}</title>
<style>html,body{margin:0;height:100%;background:#000}video{width:100%;height:100%}</style></head>
<body>$video<script>setInterval(function(){fetch('/ping').catch(function(){})},4000)</script></body></html>"""
    }

    private fun jsString(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    // ---- Stream plans ------------------------------------------------------------------------------

    private fun ensureProxy(): LocalStreamProxy? {
        proxy?.let { if (it.running) return it }
        val p = LocalStreamProxy(YoutubeSourceEnv.USER_AGENT)
        if (!p.start()) return null
        proxy = p
        return p
    }

    private fun stopProxyIfUnused() {
        if (!state.casting && !state.connecting && !linkActive) {
            proxy?.stop()
            proxy = null
        }
    }

    private fun startTicker() {
        main.removeCallbacks(ticker)
        main.post(ticker)
    }

    /** Blocking: resolves streams and registers them with the proxy. */
    private fun buildPlan(item: MediaItem, preferDash: Boolean): Plan {
        val p = ensureProxy() ?: throw IOException(app.getString(R.string.cast_failed))
        val ip = localIp() ?: throw IOException(app.getString(R.string.cast_no_wifi))
        val base = "http://$ip:${p.port}"
        val title = item.mediaMetadata.title?.toString().orEmpty()
        val videoId = hub.youtubeIdOf(item)

        if (videoId == null) {
            val uri = item.localConfiguration?.uri?.toString() ?: item.mediaId
            if (uri.contains(".m3u8")) return Plan(uri, "application/x-mpegURL", title, null, 0, false, false)
            val mime = java.net.URLConnection.guessContentTypeFromName(uri.substringBefore('?')) ?: "video/mp4"
            val token = p.register(LocalStreamProxy.Source(uri, mime))
            return Plan("$base/stream/$token", mime, title, null, 0, false, false)
        }

        val video = YoutubeResolver.resolve(videoId)
        val image = video.thumbnail
        if (video.isLive && video.hlsUrl != null) return Plan(video.hlsUrl, "application/x-mpegURL", video.title, image, 0, true, false)

        val order = if (preferDash) listOf(true, false) else listOf(false, true)
        for (dash in order) {
            val plan = if (dash) dashPlan(p, base, videoId, video) else muxedPlan(p, base, videoId, video)
            if (plan != null) return plan
        }
        video.hlsUrl?.let { return Plan(it, "application/x-mpegURL", video.title, image, video.durationMs, false, false) }
        throw IOException(app.getString(R.string.cast_no_stream))
    }

    private fun muxedPlan(p: LocalStreamProxy, base: String, videoId: String, video: YtVideo): Plan? {
        val m = YoutubeResolver.pickMuxed(video, 0) ?: return null
        val source = LocalStreamProxy.Source(m.url, m.mimeType, refresh = {
            YoutubeResolver.resolve(videoId, true).muxed.firstOrNull { it.height == m.height && it.mimeType == m.mimeType }?.url
        })
        val token = p.register(source)
        return Plan("$base/stream/$token", m.mimeType, video.title, video.thumbnail, video.durationMs, false, false)
    }

    private fun dashPlan(p: LocalStreamProxy, base: String, videoId: String, video: YtVideo): Plan? {
        val v: YtVideoStream = video.videoOnly
            .filter { it.family == "avc" && it.ranges.valid && it.height in 1..1080 && it.codec != null }
            .maxWithOrNull(compareBy<YtVideoStream>({ it.height }, { it.fps }, { it.bitrate })) ?: return null
        val audioPool = video.audio.filter { it.codec?.startsWith("mp4a") == true && it.ranges.valid }
        val a = YoutubeResolver.pickAudio(video.copy(audio = audioPool), hub.choiceFor(videoId).audioTrackId) ?: return null

        val vToken = p.register(LocalStreamProxy.Source(v.url, v.mimeType, refresh = {
            YoutubeResolver.resolve(videoId, true).videoOnly.firstOrNull { it.height == v.height && it.family == v.family && it.fps == v.fps }?.url
        }))
        val aToken = p.register(LocalStreamProxy.Source(a.url, a.mimeType, refresh = {
            YoutubeResolver.resolve(videoId, true).audio.firstOrNull { it.trackId == a.trackId && it.bitrate == a.bitrate }?.url
        }))
        p.manifest = CastManifest.build(
            video = DashRep("v", v.mimeType, v.codec!!, v.bitrate.takeIf { it > 0 } ?: 4_000_000, "$base/stream/$vToken", v.ranges, v.width, v.height, v.fps),
            audio = DashRep("a", a.mimeType, a.codec ?: "mp4a.40.2", a.bitrate.takeIf { it > 0 } ?: 128_000, "$base/stream/$aToken", a.ranges),
            durationMs = video.durationMs,
        )
        return Plan("$base/manifest.mpd", "application/dash+xml", video.title, video.thumbnail, video.durationMs, false, true)
    }

    /** The phone's address on the local network, preferring Wi-Fi / hotspot interfaces. */
    private fun localIp(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("ap") || it.name.startsWith("swlan")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress
    } catch (_: Exception) { null }
}
