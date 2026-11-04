package com.drishyopot.app.account

import android.webkit.CookieManager
import android.webkit.WebStorage
import java.security.MessageDigest

/**
 * The signed-in YouTube account, as far as the app can see it: the cookies the browser tab stored.
 * They are only sent to youtube.com hosts, and only while the user keeps "use my account" on.
 */
object YoutubeAccount {
    private const val ORIGIN = "https://www.youtube.com"

    fun isYoutubeHost(url: String): Boolean {
        val host = try { java.net.URI(url).host?.lowercase() } catch (_: Exception) { null } ?: return false
        return host == "youtube.com" || host.endsWith(".youtube.com")
    }

    fun cookieHeader(): String? = try {
        CookieManager.getInstance().getCookie(ORIGIN)?.takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }

    fun isSignedIn(): Boolean = hasLoginCookies(cookieHeader())

    fun hasLoginCookies(cookies: String?): Boolean =
        cookies != null && (cookies.contains("SAPISID=") || cookies.contains("__Secure-3PAPISID=") || cookies.contains("SID="))

    /** The value used to sign requests: SAPISID, or its secure twin. */
    fun sapisid(cookies: String): String? {
        val map = cookies.split(';').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i).trim() to it.substring(i + 1).trim()
        }.toMap()
        return map["SAPISID"] ?: map["__Secure-3PAPISID"]
    }

    /** `SAPISIDHASH <time>_<sha1(time SAPISID origin)>`, the Authorization scheme InnerTube accepts. */
    fun sapisidHash(sapisid: String, origin: String, epochSeconds: Long): String {
        val digest = MessageDigest.getInstance("SHA-1").digest("$epochSeconds $sapisid $origin".toByteArray())
        return "SAPISIDHASH ${epochSeconds}_${digest.joinToString("") { "%02x".format(it) }}"
    }

    /** Extra headers for a request to [url], empty when signed out, disabled, or not a YouTube host. */
    fun authHeaders(url: String, enabled: Boolean, epochSeconds: Long = System.currentTimeMillis() / 1000): Map<String, String> {
        if (!enabled || !isYoutubeHost(url)) return emptyMap()
        val cookies = cookieHeader() ?: return emptyMap()
        if (!hasLoginCookies(cookies)) return emptyMap()
        val headers = linkedMapOf("Cookie" to cookies)
        sapisid(cookies)?.let {
            headers["Authorization"] = sapisidHash(it, ORIGIN, epochSeconds)
            headers["X-Origin"] = ORIGIN
            headers["X-Goog-AuthUser"] = "0"
        }
        return headers
    }

    fun signOut() {
        try {
            val manager = CookieManager.getInstance()
            manager.removeAllCookies(null)
            manager.flush()
            WebStorage.getInstance().deleteAllData()
        } catch (_: Exception) { }
    }
}
