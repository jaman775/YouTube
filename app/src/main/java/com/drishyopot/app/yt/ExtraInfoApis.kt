package com.drishyopot.app.yt

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class RydVotes(val likes: Long, val dislikes: Long, val viewCount: Long)

/** Optional extras: exact like/dislike counts (Return YouTube Dislike) and the uploader's own title (oEmbed). */
object ExtraInfoApis {
    private val votesCache = ConcurrentHashMap<String, RydVotes>()
    private val titleCache = ConcurrentHashMap<String, String>()

    /** Blocking. Returns null when the service is unreachable or has no data. */
    fun votes(videoId: String): RydVotes? {
        votesCache[videoId]?.let { return it }
        val json = get("https://returnyoutubedislikeapi.com/votes?videoId=$videoId") ?: return null
        return try {
            val o = JSONObject(json)
            RydVotes(o.optLong("likes"), o.optLong("dislikes"), o.optLong("viewCount")).also { votesCache[videoId] = it }
        } catch (_: Exception) { null }
    }

    /** Blocking. The title as published, which may differ from the translated one YouTube shows. */
    fun originalTitle(videoId: String): String? {
        titleCache[videoId]?.let { return it }
        val watch = URLEncoder.encode(YoutubeUrls.watchUrl(videoId), "UTF-8")
        val json = get("https://www.youtube.com/oembed?url=$watch&format=json") ?: return null
        return try {
            JSONObject(json).optString("title").takeIf { it.isNotBlank() }?.also { titleCache[videoId] = it }
        } catch (_: Exception) { null }
    }

    fun compact(n: Long): String = when {
        n >= 1_000_000_000 -> "%.1fB".format(n / 1_000_000_000.0)
        n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
        n >= 1_000 -> "%.1fK".format(n / 1_000.0)
        else -> n.toString()
    }

    private fun get(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 6_000
            conn.readTimeout = 6_000
            conn.setRequestProperty("User-Agent", "Drishyopot/0.1")
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
        } catch (_: Exception) { null } finally { conn.disconnect() }
    }
}
