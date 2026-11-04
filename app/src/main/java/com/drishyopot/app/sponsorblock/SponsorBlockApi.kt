package com.drishyopot.app.sponsorblock

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Talks to the public SponsorBlock API using the privacy-preserving hash-prefix endpoint:
 * only the first 4 hex characters of the video ID's SHA-256 are sent.
 */
object SponsorBlockApi {
    private const val BASE = "https://sponsor.ajay.app"
    private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L

    class FetchException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /** Blocking. Returns every known segment for [videoId]; empty when the video has none. */
    fun fetch(context: Context, videoId: String): List<SponsorSegment> {
        val cacheFile = File(File(context.cacheDir, "sponsorblock").also { it.mkdirs() }, "$videoId.json")
        if (cacheFile.exists() && System.currentTimeMillis() - cacheFile.lastModified() < CACHE_TTL_MS) {
            try { return parse(cacheFile.readText(), videoId) } catch (_: JSONException) { cacheFile.delete() }
        }
        val body = download(videoId)
        try {
            val parsed = parse(body, videoId)
            cacheFile.writeText(body)
            return parsed
        } catch (e: JSONException) {
            throw FetchException("Unexpected SponsorBlock response", e)
        }
    }

    private fun download(videoId: String): String {
        val prefix = sha256Hex(videoId).take(4)
        val categories = URLEncoder.encode(
            SbCategory.entries.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"${it.api}\"" }, "UTF-8",
        )
        val actions = URLEncoder.encode("[\"${SbAction.SKIP}\",\"${SbAction.MUTE}\",\"${SbAction.POI}\"]", "UTF-8")
        val url = URL("$BASE/api/skipSegments/$prefix?categories=$categories&actionTypes=$actions")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "Drishyopot/0.1")
            return when (val code = conn.responseCode) {
                200 -> conn.inputStream.bufferedReader().use { it.readText() }
                404 -> "[]"
                else -> throw FetchException("HTTP $code")
            }
        } catch (e: FileNotFoundException) {
            return "[]"
        } catch (e: java.io.IOException) {
            throw FetchException(e.message ?: "Network error", e)
        } finally {
            conn.disconnect()
        }
    }

    internal fun parse(json: String, videoId: String): List<SponsorSegment> {
        val videos = JSONArray(json)
        val out = ArrayList<SponsorSegment>()
        for (i in 0 until videos.length()) {
            val video = videos.getJSONObject(i)
            if (video.optString("videoID") != videoId) continue
            val segments = video.optJSONArray("segments") ?: continue
            for (j in 0 until segments.length()) {
                val s = segments.getJSONObject(j)
                val category = SbCategory.fromApi(s.optString("category")) ?: continue
                val range = s.optJSONArray("segment") ?: continue
                if (range.length() < 2) continue
                val start = (range.getDouble(0) * 1000).toLong()
                val end = (range.getDouble(1) * 1000).toLong()
                val action = s.optString("actionType", SbAction.SKIP)
                if (action != SbAction.SKIP && action != SbAction.MUTE && action != SbAction.POI) continue
                if (action != SbAction.POI && end <= start) continue
                out += SponsorSegment(
                    uuid = s.optString("UUID", "$category-$start"),
                    category = category,
                    actionType = action,
                    startMs = start,
                    endMs = end,
                    videoDurationMs = (s.optDouble("videoDuration", 0.0) * 1000).toLong(),
                )
            }
        }
        return out.sortedBy { it.startMs }
    }

    private fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
