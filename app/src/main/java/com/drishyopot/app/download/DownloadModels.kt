package com.drishyopot.app.download

import org.json.JSONArray
import org.json.JSONObject

enum class DlMode { VIDEO, AUDIO }

enum class DlStatus { QUEUED, RUNNING, WAITING_NETWORK, PAUSED, MERGING, COMPLETED, FAILED }

enum class DlError { LIVE, PREMIERE, HTTP_403, HTTP_429, STORAGE, MEMORY, NETWORK, NO_STREAM, MERGE_FAILED, VERIFY_FAILED, OTHER }

/** What the user asked for. [height] is the wanted resolution; the nearest available one is used. */
data class DownloadRequest(
    val videoId: String,
    val title: String,
    val uploader: String,
    val thumbnail: String?,
    val durationMs: Long,
    val mode: DlMode,
    val height: Int,
    val family: String,                 // "avc", "vp9", "av1" or "" for automatic
    val audioTrackId: String?,
    val subtitleLangs: List<String>,
    val includeCover: Boolean,
    val fileName: String,               // without extension
    val batchId: String? = null,
)

/** Progress of one downloaded stream: its size and which 4 MiB blocks are complete (hex BitSet). */
data class PartState(val length: Long = 0, val doneBlocks: String = "")

data class DownloadTask(
    val id: String,
    val request: DownloadRequest,
    val status: DlStatus,
    val error: DlError? = null,
    val errorDetail: String? = null,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val speedBps: Long = 0,
    val etaSec: Long = -1,
    val video: PartState = PartState(),
    val audio: PartState = PartState(),
    val outputUri: String? = null,
    val outputName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0,
) {
    val progress: Float get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    val isActive: Boolean get() = status == DlStatus.QUEUED || status == DlStatus.RUNNING ||
        status == DlStatus.WAITING_NETWORK || status == DlStatus.MERGING
    val isFinished: Boolean get() = status == DlStatus.COMPLETED
}

/** JSON (de)serialisation used by [DownloadStore]. */
object DownloadJson {
    fun toJson(t: DownloadTask): JSONObject {
        val r = t.request
        return JSONObject()
            .put("id", t.id).put("status", t.status.name)
            .put("error", t.error?.name ?: JSONObject.NULL).put("errorDetail", t.errorDetail ?: JSONObject.NULL)
            .put("total", t.totalBytes).put("done", t.downloadedBytes)
            .put("vLen", t.video.length).put("vBits", t.video.doneBlocks)
            .put("aLen", t.audio.length).put("aBits", t.audio.doneBlocks)
            .put("uri", t.outputUri ?: JSONObject.NULL).put("outName", t.outputName ?: JSONObject.NULL)
            .put("created", t.createdAt).put("completed", t.completedAt)
            .put(
                "req",
                JSONObject()
                    .put("videoId", r.videoId).put("title", r.title).put("uploader", r.uploader)
                    .put("thumb", r.thumbnail ?: JSONObject.NULL).put("duration", r.durationMs)
                    .put("mode", r.mode.name).put("height", r.height).put("family", r.family)
                    .put("audioTrack", r.audioTrackId ?: JSONObject.NULL)
                    .put("subs", JSONArray(r.subtitleLangs)).put("cover", r.includeCover)
                    .put("fileName", r.fileName).put("batch", r.batchId ?: JSONObject.NULL),
            )
    }

    fun fromJson(o: JSONObject): DownloadTask? = try {
        val r = o.getJSONObject("req")
        fun str(obj: JSONObject, key: String) = if (obj.isNull(key)) null else obj.getString(key)
        val subs = r.optJSONArray("subs")
        DownloadTask(
            id = o.getString("id"),
            status = DlStatus.valueOf(o.getString("status")),
            error = str(o, "error")?.let { runCatching { DlError.valueOf(it) }.getOrNull() },
            errorDetail = str(o, "errorDetail"),
            totalBytes = o.optLong("total"), downloadedBytes = o.optLong("done"),
            video = PartState(o.optLong("vLen"), o.optString("vBits")),
            audio = PartState(o.optLong("aLen"), o.optString("aBits")),
            outputUri = str(o, "uri"), outputName = str(o, "outName"),
            createdAt = o.optLong("created"), completedAt = o.optLong("completed"),
            request = DownloadRequest(
                videoId = r.getString("videoId"), title = r.getString("title"), uploader = r.optString("uploader"),
                thumbnail = str(r, "thumb"), durationMs = r.optLong("duration"),
                mode = DlMode.valueOf(r.getString("mode")), height = r.optInt("height"), family = r.optString("family"),
                audioTrackId = str(r, "audioTrack"),
                subtitleLangs = (0 until (subs?.length() ?: 0)).map { subs!!.getString(it) },
                includeCover = r.optBoolean("cover"), fileName = r.getString("fileName"), batchId = str(r, "batch"),
            ),
        )
    } catch (_: Exception) { null }
}
