package com.drishyopot.app.browser

import com.drishyopot.app.yt.YoutubeUrls
import com.drishyopot.app.yt.YtLink
import java.net.URI
import java.net.URLEncoder

sealed interface NavDecision {
    /** Stay in the WebView. */
    data object Allow : NavDecision
    /** Hand a video or playlist to the native player. */
    data class Native(val link: YtLink) : NavDecision
    /** Leave the app (default browser or other app). */
    data object External : NavDecision
}

object UrlPolicy {
    private val internalSuffixes = listOf(
        "google.com", "gstatic.com", "googleusercontent.com", "googleapis.com", "ggpht.com", "ytimg.com",
    )

    fun decide(url: String): NavDecision {
        if (url == "about:blank") return NavDecision.Allow
        val uri = try { URI(url) } catch (_: Exception) { return NavDecision.External }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return NavDecision.External
        when (val link = YoutubeUrls.parse(url)) {
            is YtLink.Video, is YtLink.Playlist -> return NavDecision.Native(link)
            else -> Unit
        }
        val host = uri.host?.lowercase() ?: return NavDecision.External
        if (YoutubeUrls.isYoutubeHost(host)) return NavDecision.Allow
        if (internalSuffixes.any { host == it || host.endsWith(".$it") }) return NavDecision.Allow
        return NavDecision.External
    }
}

/** Turns what the user typed into a URL: a link as-is, a bare domain, or a YouTube search. */
object BrowserInput {
    const val HOME = "https://m.youtube.com/"

    fun searchUrl(query: String) = "https://m.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8")

    fun resolve(text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return null
        if (t.startsWith("http://", true) || t.startsWith("https://", true)) return t
        val looksLikeDomain = !t.contains(' ') && t.contains('.') && !t.endsWith(".")
        return if (looksLikeDomain) "https://$t" else searchUrl(t)
    }
}
