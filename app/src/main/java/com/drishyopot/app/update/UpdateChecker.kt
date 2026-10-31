package com.drishyopot.app.update

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class LatestRelease(val tag: String, val url: String)

/** Looks for a newer GitHub release of the repository named in the `update_repo` resource. */
object UpdateChecker {
    /** Blocking. Returns null when the repo has no release or the service cannot be reached. */
    fun latest(repo: String): LatestRelease? {
        if (!Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(repo)) return null
        val c = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 8_000
            c.readTimeout = 8_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "Drishyopot")
            if (c.responseCode != 200) return null
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            LatestRelease(o.getString("tag_name"), o.optString("html_url"))
        } catch (_: Exception) { null } finally { c.disconnect() }
    }

    /** True when [remote] (e.g. "v1.2.0") is a higher version than [local] (e.g. "1.1.9-debug"). */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
