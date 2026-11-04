package com.drishyopot.app.yt

import java.net.URI
import java.net.URLDecoder

/** What a pasted or shared YouTube link points to. */
sealed interface YtLink {
    data class Video(val id: String, val listId: String? = null, val startSeconds: Int = 0) : YtLink
    data class Playlist(val id: String) : YtLink
    /** Anything else on a YouTube host (home, search, channel, ...): open in the browser. */
    data class Page(val url: String) : YtLink
}

object YoutubeUrls {
    private val idRegex = Regex("^[A-Za-z0-9_-]{11}$")
    private val urlInText = Regex("""https?://[^\s<>"']+""")
    private val timeToken = Regex("""(\d+)([hms])""")

    fun isValidId(id: String?): Boolean = id != null && idRegex.matches(id)

    fun isYoutubeHost(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return h == "youtu.be" || h == "youtube.com" || h.endsWith(".youtube.com") ||
            h == "youtube-nocookie.com" || h.endsWith(".youtube-nocookie.com")
    }

    fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

    fun playlistUrl(listId: String) = "https://www.youtube.com/playlist?list=$listId"

    /** First http(s) link inside a block of shared text, if any. */
    fun firstUrl(text: String?): String? = text?.let { urlInText.find(it)?.value?.trimEnd('.', ',', ')') }

    fun parse(raw: String?): YtLink? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val uri = try { URI(text) } catch (_: Exception) { return null }
        val host = uri.host?.lowercase() ?: return null
        if (!isYoutubeHost(host)) return null
        val query = parseQuery(uri.rawQuery)
        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val start = parseStart(query["t"] ?: query["start"])

        if (host == "youtu.be") {
            val id = segments.firstOrNull()
            return if (isValidId(id)) YtLink.Video(id!!, query["list"], start) else YtLink.Page(text)
        }
        val first = segments.firstOrNull()
        return when {
            first == "watch" -> {
                val id = query["v"]
                if (isValidId(id)) YtLink.Video(id!!, query["list"], start) else YtLink.Page(text)
            }
            first in setOf("shorts", "embed", "live", "v") && segments.size >= 2 && isValidId(segments[1]) ->
                YtLink.Video(segments[1], query["list"], start)
            first == "playlist" && !query["list"].isNullOrBlank() -> YtLink.Playlist(query["list"]!!)
            else -> YtLink.Page(text)
        }
    }

    /** Accepts "90", "90s", "1m30s", "1h2m3s". */
    fun parseStart(value: String?): Int {
        if (value.isNullOrBlank()) return 0
        value.toIntOrNull()?.let { return it.coerceAtLeast(0) }
        var total = 0
        timeToken.findAll(value.lowercase()).forEach { m ->
            val n = m.groupValues[1].toIntOrNull() ?: 0
            total += when (m.groupValues[2]) { "h" -> n * 3600; "m" -> n * 60; else -> n }
        }
        return total
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        raw.split('&').forEach { pair ->
            if (pair.isEmpty()) return@forEach
            val k = pair.substringBefore('=')
            val v = pair.substringAfter('=', "")
            out.putIfAbsent(URLDecoder.decode(k, "UTF-8"), URLDecoder.decode(v, "UTF-8"))
        }
        return out
    }
}
