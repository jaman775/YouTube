package com.drishyopot.app.browser

import java.net.URI

/** Network-level ad blocking for the embedded browser. */
object AdBlockRules {
    private val blockedHosts = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "2mdn.net",
        "googletagservices.com", "imasdk.googleapis.com", "adservice.google.com",
    )
    private val blockedYoutubePaths = listOf("/pagead/", "/api/stats/ads", "/ptracking", "/get_midroll_")

    fun shouldBlock(url: String): Boolean {
        val uri = try { URI(url) } catch (_: Exception) { return false }
        val host = uri.host?.lowercase() ?: return false
        if (blockedHosts.any { host == it || host.endsWith(".$it") }) return true
        if (host == "youtube.com" || host.endsWith(".youtube.com")) {
            val path = uri.path.orEmpty()
            return blockedYoutubePaths.any { path.contains(it) }
        }
        return false
    }
}
