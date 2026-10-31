package com.drishyopot.app.download

import android.content.Context
import com.drishyopot.app.diagnostics.DiagnosticLog
import org.json.JSONArray
import java.io.File

/** Download list on disk (JSON, written atomically). */
class DownloadStore(context: Context) {
    private val file = File(context.filesDir, "downloads.json")
    private val tmp = File(context.filesDir, "downloads.json.tmp")

    @Synchronized
    fun load(): List<DownloadTask> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { DownloadJson.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            DiagnosticLog.log("Downloads", "Could not read download list", e)
            emptyList()
        }
    }

    @Synchronized
    fun save(tasks: List<DownloadTask>) {
        try {
            val arr = JSONArray()
            tasks.forEach { arr.put(DownloadJson.toJson(it)) }
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        } catch (e: Exception) {
            DiagnosticLog.log("Downloads", "Could not save download list", e)
        }
    }
}
