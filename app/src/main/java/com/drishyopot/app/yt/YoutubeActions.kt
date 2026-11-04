package com.drishyopot.app.yt

import com.drishyopot.app.account.YoutubeAccount
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Like, dislike and subscribe on the signed-in YouTube account, using the cookies the browser tab holds
 * (the same requests the YouTube website sends). All calls block: run them off the main thread.
 */
object YoutubeActions {
    private const val KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8" // the public key youtube.com itself uses
    private const val CLIENT_VERSION = "2.20260901.00.00"
    private const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    data class State(val liked: Boolean?, val disliked: Boolean?, val subscribed: Boolean?)

    fun state(videoId: String): State {
        val body = post("next", JSONObject().put("videoId", videoId)) ?: return State(null, null, null)
        val like = Regex("\"likeStatus\":\"(LIKE|DISLIKE|INDIFFERENT)\"").find(body)?.groupValues?.get(1)
        val sub = Regex("\"subscribed\":(true|false)").find(body)?.groupValues?.get(1)
        return State(like?.let { it == "LIKE" }, like?.let { it == "DISLIKE" }, sub?.let { it == "true" })
    }

    fun like(videoId: String) = ok("like/like", JSONObject().put("target", JSONObject().put("videoId", videoId)))
    fun dislike(videoId: String) = ok("like/dislike", JSONObject().put("target", JSONObject().put("videoId", videoId)))
    fun removeLike(videoId: String) = ok("like/removelike", JSONObject().put("target", JSONObject().put("videoId", videoId)))

    fun subscribe(channelId: String) =
        ok("subscription/subscribe", JSONObject().put("channelIds", org.json.JSONArray().put(channelId)).put("params", "EgIIAhgA"))

    fun unsubscribe(channelId: String) =
        ok("subscription/unsubscribe", JSONObject().put("channelIds", org.json.JSONArray().put(channelId)))

    private fun ok(endpoint: String, body: JSONObject): Boolean {
        val response = post(endpoint, body) ?: return false
        return try { !JSONObject(response).has("error") } catch (_: Exception) { false }
    }

    private fun post(endpoint: String, payload: JSONObject): String? {
        val url = "https://www.youtube.com/youtubei/v1/$endpoint?key=$KEY&prettyPrint=false"
        val headers = YoutubeAccount.authHeaders(url, enabled = true)
        if (!headers.containsKey("Authorization")) return null // signed out
        payload.put("context", JSONObject().put("client", JSONObject()
            .put("clientName", "WEB").put("clientVersion", CLIENT_VERSION).put("hl", "en").put("gl", "US")))
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Origin", "https://www.youtube.com")
            conn.setRequestProperty("X-Youtube-Client-Name", "1")
            conn.setRequestProperty("X-Youtube-Client-Version", CLIENT_VERSION)
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(payload.toString().toByteArray()) }
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
        } catch (_: Exception) { null } finally { conn.disconnect() }
    }
}
