package com.drishyopot.app.player.yt

import com.drishyopot.app.diagnostics.DiagnosticLog
import com.drishyopot.app.engine.EngineHost
import com.drishyopot.app.engine.HostHttpBridge
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** The identity a streaming request should have, decided by the engine. */
data class StreamProfile(val userAgent: String, val headers: Map<String, String>)

object StreamProfiles {
    private val cache = ConcurrentHashMap<String, StreamProfile>()
    private val default = StreamProfile(HostHttpBridge.USER_AGENT, emptyMap())
    private val clientParam = Regex("[?&]c=([A-Za-z0-9_]+)")

    fun forUrl(url: String): StreamProfile {
        val key = clientParam.find(url)?.groupValues?.get(1) ?: "-"
        cache[key]?.let { return it }
        return try {
            parse(EngineHost.call { it.streamProfile(url) }).also { cache[key] = it }
        } catch (e: Exception) {
            DiagnosticLog.log("StreamHttp", "No stream profile for client $key; using the default", e)
            default
        }
    }

    fun clear() = cache.clear()

    internal fun parse(json: String): StreamProfile {
        val o = JSONObject(json)
        val headers = LinkedHashMap<String, String>()
        o.optJSONObject("headers")?.let { h -> h.keys().forEach { headers[it] = h.getString(it) } }
        return StreamProfile(if (o.isNull("userAgent")) default.userAgent else o.getString("userAgent"), headers)
    }
}

/**
 * Requests to googlevideo.com `/videoplayback` URLs the way YouTube's own web player makes them:
 * `POST` with a two-byte body, the byte range in a `range` query parameter instead of a Range header,
 * and a running `rn` counter. Plain GETs with Range headers are throttled or refused.
 */
object YoutubeStreamHttp {
    private val body = byteArrayOf(0x78, 0)
    private val requestNumber = AtomicInteger()
    private val clen = Regex("[?&]clen=(\\d+)")

    fun isVideoPlayback(url: String): Boolean = try {
        URI(url).path.orEmpty().startsWith("/videoplayback")
    } catch (_: Exception) { false }

    /** The file size YouTube puts in the URL, when it does. */
    fun contentLengthParam(url: String): Long? = clen.find(url)?.groupValues?.get(1)?.toLongOrNull()

    /**
     * Opens bytes [start]..[endInclusive] (or to the end when [endInclusive] < 0).
     * The caller reads the body and must disconnect.
     */
    fun open(url: String, start: Long, endInclusive: Long): HttpURLConnection {
        var requestUrl = url
        if (!requestUrl.contains("&rn=")) requestUrl += "&rn=${requestNumber.getAndIncrement()}"
        if (!(start == 0L && endInclusive < 0)) {
            requestUrl += "&range=$start-" + (if (endInclusive >= 0) endInclusive.toString() else "")
        }
        val profile = StreamProfiles.forUrl(url)
        val c = URL(requestUrl).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", profile.userAgent)
        profile.headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.setRequestProperty("TE", "trailers")
        c.setRequestProperty("Accept-Encoding", "identity")
        c.requestMethod = "POST"
        c.doOutput = true
        c.setFixedLengthStreamingMode(body.size)
        c.connect()
        c.outputStream.use { it.write(body) }
        return c
    }

    /** Short description for logs; never includes the signed URL. */
    fun describe(url: String): String =
        "client=" + (Regex("[?&]c=([A-Za-z0-9_]+)").find(url)?.groupValues?.get(1) ?: "?") +
            " host=" + (try { URI(url).host } catch (_: Exception) { "?" })
}
