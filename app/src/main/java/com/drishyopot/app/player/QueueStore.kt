package com.drishyopot.app.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class QueueEntry(val url: String, val title: String, val videoId: String? = null)

/** Persists the queue so it survives process death. */
class QueueStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("drishyopot_queue", Context.MODE_PRIVATE)

    fun save(items: List<QueueEntry>, index: Int) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("u", it.url).put("t", it.title).put("v", it.videoId ?: "")) }
        prefs.edit().putString("items", arr.toString()).putInt("index", index).apply()
    }

    fun load(): Pair<List<QueueEntry>, Int> {
        val raw = prefs.getString("items", null) ?: return emptyList<QueueEntry>() to 0
        return try {
            val arr = JSONArray(raw)
            val list = (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                QueueEntry(o.getString("u"), o.optString("t"), o.optString("v").takeIf { v -> v.isNotBlank() })
            }
            list to prefs.getInt("index", 0).coerceIn(0, (list.size - 1).coerceAtLeast(0))
        } catch (e: Exception) {
            emptyList<QueueEntry>() to 0
        }
    }
}
