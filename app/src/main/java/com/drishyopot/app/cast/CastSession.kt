package com.drishyopot.app.cast

import android.annotation.SuppressLint
import com.drishyopot.app.diagnostics.DiagnosticLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/** What the receiver reports about the media it is playing. */
data class RemoteMediaStatus(
    val playerState: String,
    val positionMs: Long,
    val durationMs: Long,
    val idleReason: String?,
    val mediaSessionId: Int,
)

/**
 * One connection to a Cast device (Cast V2 over TLS, port 8009), driving the Default Media Receiver.
 *
 * Security note: Cast devices present certificates issued by Google's device CA, which this app does
 * not ship, so the TLS certificate is NOT verified. Use it only on networks you trust.
 */
class CastSession(
    private val device: CastDevice,
    private val listener: Listener,
) {
    interface Listener {
        fun onConnected()
        fun onMediaStatus(status: RemoteMediaStatus)
        fun onVolume(level: Float, muted: Boolean)
        fun onLoadFailed(reason: String)
        fun onClosed(error: String?)
    }

    private val senderId = "sender-drishyopot"
    private val requestId = AtomicInteger(1)
    private val writeLock = Any()
    private var socket: SSLSocket? = null
    private var out: OutputStream? = null
    private var scheduler: ScheduledExecutorService? = null
    @Volatile private var transportId: String? = null
    @Volatile private var sessionId: String? = null
    @Volatile private var mediaSessionId = 0
    @Volatile private var closed = false
    @Volatile private var lastPongAt = System.currentTimeMillis()
    private var connectedNotified = false

    // ---- Lifecycle -----------------------------------------------------------------------------

    fun connect() {
        Thread({
            try {
                val sock = openSocket()
                socket = sock
                out = sock.outputStream
                startReader(sock)
                send(NS_CONNECTION, PLATFORM_ID, JSONObject().put("type", "CONNECT"))
                startTimers()
                send(
                    NS_RECEIVER, PLATFORM_ID,
                    JSONObject().put("type", "LAUNCH").put("appId", APP_ID).put("requestId", requestId.getAndIncrement()),
                )
                trace("launch_sent")
            } catch (e: Exception) {
                fail("connect: ${e.message ?: e.javaClass.simpleName}", e)
            }
        }, "cast-connect").start()
    }

    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private fun openSocket(): SSLSocket {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
        val sock = context.socketFactory.createSocket() as SSLSocket
        sock.connect(InetSocketAddress(device.host, device.port), CONNECT_TIMEOUT_MS)
        sock.startHandshake()
        trace("tls_connected")
        return sock
    }

    private fun startReader(sock: SSLSocket) {
        Thread({
            try {
                val input = sock.inputStream
                while (!closed) {
                    val message = CastMessageCodec.readFrame(input) ?: break
                    handle(message)
                }
                if (!closed) fail("connection closed by device", null)
            } catch (e: Exception) {
                if (!closed) fail("read: ${e.message ?: e.javaClass.simpleName}", e)
            }
        }, "cast-reader").start()
    }

    private fun startTimers() {
        val s = Executors.newSingleThreadScheduledExecutor()
        scheduler = s
        s.scheduleWithFixedDelay({
            try {
                if (System.currentTimeMillis() - lastPongAt > HEARTBEAT_TIMEOUT_MS) fail("heartbeat timeout", null)
                else send(NS_HEARTBEAT, PLATFORM_ID, JSONObject().put("type", "PING"))
            } catch (_: Exception) { }
        }, 0, 5, TimeUnit.SECONDS)
        s.scheduleWithFixedDelay({
            try { if (mediaSessionId != 0) mediaCommand("GET_STATUS") } catch (_: Exception) { }
        }, 1, 1, TimeUnit.SECONDS)
    }

    fun close() {
        if (closed) return
        closed = true
        try {
            transportId?.let { send(NS_CONNECTION, it, JSONObject().put("type", "CLOSE")) }
            sessionId?.let {
                send(NS_RECEIVER, PLATFORM_ID, JSONObject().put("type", "STOP").put("sessionId", it).put("requestId", requestId.getAndIncrement()))
            }
        } catch (_: Exception) { }
        scheduler?.shutdownNow()
        try { socket?.close() } catch (_: IOException) { }
    }

    private fun fail(reason: String, error: Throwable?) {
        if (closed) return
        closed = true
        DiagnosticLog.log("Cast", "Session failed: $reason", error)
        scheduler?.shutdownNow()
        try { socket?.close() } catch (_: IOException) { }
        listener.onClosed(reason)
    }

    // ---- Incoming ------------------------------------------------------------------------------

    private fun handle(message: CastMessage) {
        val json = try { JSONObject(message.payload) } catch (_: Exception) { return }
        when (message.namespace) {
            NS_HEARTBEAT -> {
                if (json.optString("type") == "PING") send(NS_HEARTBEAT, message.sourceId, JSONObject().put("type", "PONG"))
                else lastPongAt = System.currentTimeMillis()
            }
            NS_CONNECTION -> if (json.optString("type") == "CLOSE") {
                fail("device closed the connection", null)
            }
            NS_RECEIVER -> handleReceiver(json)
            NS_MEDIA -> handleMedia(json)
        }
    }

    private fun handleReceiver(json: JSONObject) {
        when (json.optString("type")) {
            "RECEIVER_STATUS" -> {
                val status = json.optJSONObject("status") ?: return
                status.optJSONObject("volume")?.let {
                    listener.onVolume(it.optDouble("level", 1.0).toFloat(), it.optBoolean("muted", false))
                }
                val app = findApp(status.optJSONArray("applications"))
                if (app != null && transportId == null) {
                    transportId = app.optString("transportId")
                    sessionId = app.optString("sessionId")
                    trace("app_launched")
                    send(NS_CONNECTION, transportId!!, JSONObject().put("type", "CONNECT"))
                    if (!connectedNotified) { connectedNotified = true; listener.onConnected() }
                } else if (app == null && transportId != null) {
                    fail("receiver app stopped", null)
                }
            }
            "LAUNCH_ERROR" -> fail("launch error: ${json.optString("reason")}", null)
        }
    }

    private fun findApp(apps: JSONArray?): JSONObject? {
        if (apps == null) return null
        for (i in 0 until apps.length()) {
            val a = apps.optJSONObject(i) ?: continue
            if (a.optString("appId") == APP_ID) return a
        }
        return null
    }

    private fun handleMedia(json: JSONObject) {
        when (val type = json.optString("type")) {
            "MEDIA_STATUS" -> {
                val first = json.optJSONArray("status")?.optJSONObject(0) ?: return
                mediaSessionId = first.optInt("mediaSessionId", mediaSessionId)
                val duration = first.optJSONObject("media")?.optDouble("duration", 0.0) ?: 0.0
                listener.onMediaStatus(
                    RemoteMediaStatus(
                        playerState = first.optString("playerState", "IDLE"),
                        positionMs = (first.optDouble("currentTime", 0.0) * 1000).toLong(),
                        durationMs = (duration * 1000).toLong(),
                        idleReason = first.optString("idleReason").takeIf { it.isNotEmpty() },
                        mediaSessionId = mediaSessionId,
                    ),
                )
            }
            "LOAD_FAILED", "LOAD_CANCELLED", "INVALID_REQUEST", "INVALID_PLAYER_STATE" -> {
                trace("media_error_$type")
                listener.onLoadFailed(type)
            }
        }
    }

    // ---- Outgoing media commands ---------------------------------------------------------------

    fun load(
        contentUrl: String,
        contentType: String,
        title: String,
        imageUrl: String?,
        durationSec: Double?,
        live: Boolean,
        startSec: Double,
    ) {
        val metadata = JSONObject().put("metadataType", 0).put("title", title)
        if (imageUrl != null) metadata.put("images", JSONArray().put(JSONObject().put("url", imageUrl)))
        val media = JSONObject()
            .put("contentId", contentUrl)
            .put("contentType", contentType)
            .put("streamType", if (live) "LIVE" else "BUFFERED")
            .put("metadata", metadata)
        if (durationSec != null && durationSec > 0) media.put("duration", durationSec)
        mediaSessionId = 0
        trace("load_sent")
        send(
            NS_MEDIA, transportId ?: return,
            JSONObject()
                .put("type", "LOAD")
                .put("requestId", requestId.getAndIncrement())
                .put("sessionId", sessionId)
                .put("media", media)
                .put("autoplay", true)
                .put("currentTime", startSec),
        )
    }

    fun play() = mediaCommand("PLAY")
    fun pause() = mediaCommand("PAUSE")
    fun stopMedia() = mediaCommand("STOP")
    fun seek(positionMs: Long) = mediaCommand("SEEK") { it.put("currentTime", positionMs / 1000.0) }

    fun setVolume(level: Float) {
        send(
            NS_RECEIVER, PLATFORM_ID,
            JSONObject().put("type", "SET_VOLUME").put("requestId", requestId.getAndIncrement())
                .put("volume", JSONObject().put("level", level.coerceIn(0f, 1f).toDouble())),
        )
    }

    fun setMuted(muted: Boolean) {
        send(
            NS_RECEIVER, PLATFORM_ID,
            JSONObject().put("type", "SET_VOLUME").put("requestId", requestId.getAndIncrement())
                .put("volume", JSONObject().put("muted", muted)),
        )
    }

    private fun mediaCommand(type: String, extra: (JSONObject) -> Unit = {}) {
        val transport = transportId ?: return
        val id = mediaSessionId.takeIf { it != 0 } ?: return
        val payload = JSONObject().put("type", type).put("requestId", requestId.getAndIncrement()).put("mediaSessionId", id)
        extra(payload)
        send(NS_MEDIA, transport, payload)
    }

    private fun send(namespace: String, destination: String, payload: JSONObject) {
        val bytes = CastMessageCodec.frame(CastMessage(senderId, destination, namespace, payload.toString()))
        synchronized(writeLock) {
            val stream = out ?: return
            try {
                stream.write(bytes)
                stream.flush()
            } catch (e: IOException) {
                if (!closed) fail("write: ${e.message}", e)
            }
        }
    }

    private fun trace(step: String) = DiagnosticLog.log("Cast", "[$step] ${device.name}")

    companion object {
        const val APP_ID = "CC1AD845" // Default Media Receiver
        private const val PLATFORM_ID = "receiver-0"
        private const val NS_CONNECTION = "urn:x-cast:com.google.cast.tp.connection"
        private const val NS_HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat"
        private const val NS_RECEIVER = "urn:x-cast:com.google.cast.receiver"
        private const val NS_MEDIA = "urn:x-cast:com.google.cast.media"
        private const val CONNECT_TIMEOUT_MS = 6_000
        private const val HEARTBEAT_TIMEOUT_MS = 20_000L
    }
}
