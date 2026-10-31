package com.drishyopot.app.download

import android.app.Application
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.drishyopot.app.R
import com.drishyopot.app.diagnostics.DiagnosticLog
import com.drishyopot.app.settings.PrefKeys
import com.drishyopot.app.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Owns the download list and schedules runners: two at a time, paused when the network is not
 * allowed (offline, or cellular with "Wi-Fi only") and resumed when it comes back.
 * All state changes happen on the main thread.
 */
class DownloadCenter(private val app: Application, private val settings: SettingsStore) : RunnerEvents {
    val tasks = mutableStateListOf<DownloadTask>()
    var online by mutableStateOf(true)
        private set
    var unmetered by mutableStateOf(true)
        private set

    val notifications = DownloadNotifications(app)

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val executor = Executors.newCachedThreadPool()
    private val store = DownloadStore(app)
    private val writer = OutputWriter(app)

    private enum class Stop { PAUSE, CANCEL, NETWORK }
    private class Handle { @Volatile var stop: Stop? = null }

    private val handles = HashMap<String, Handle>()
    private val resumeAfterStop = HashSet<String>()
    private var saveQueued = false

    init {
        // Anything that was in flight when the app died is paused, never silently restarted.
        store.load().forEach { t ->
            tasks += if (t.isActive) t.copy(status = DlStatus.PAUSED, speedBps = 0, etaSec = -1) else t
        }
        watchNetwork()
        scope.launch { settings.revision.collect { reevaluate() } }
    }

    // ---- Public API --------------------------------------------------------------------------------

    fun enqueue(requests: List<DownloadRequest>) {
        requests.forEach { r ->
            tasks += DownloadTask(id = UUID.randomUUID().toString(), request = r, status = DlStatus.QUEUED)
        }
        saveNow()
        pump()
        ensureService()
    }

    fun pause(id: String) {
        val handle = handles[id]
        if (handle != null) { handle.stop = Stop.PAUSE; resumeAfterStop.remove(id); return }
        update(id) { if (it.status == DlStatus.QUEUED || it.status == DlStatus.WAITING_NETWORK) it.copy(status = DlStatus.PAUSED, speedBps = 0) else it }
    }

    fun resume(id: String) {
        if (handles.containsKey(id)) { resumeAfterStop += id; return }
        update(id) {
            if (it.status == DlStatus.PAUSED || it.status == DlStatus.FAILED || it.status == DlStatus.WAITING_NETWORK)
                it.copy(status = DlStatus.QUEUED, error = null, errorDetail = null) else it
        }
        pump()
        ensureService()
    }

    fun cancel(id: String) {
        val handle = handles[id]
        if (handle != null) { handle.stop = Stop.CANCEL; return }
        removeTask(id, deleteOutput = false)
    }

    fun pauseAll() = tasks.filter { it.isActive }.forEach { pause(it.id) }

    fun resumeAll() = tasks.filter { it.status == DlStatus.PAUSED || it.status == DlStatus.FAILED }.forEach { resume(it.id) }

    fun cancelAll() = tasks.filter { it.isActive || it.status == DlStatus.PAUSED }.forEach { cancel(it.id) }

    /** Removes a finished or failed entry, optionally deleting the downloaded file. */
    fun remove(id: String, deleteFile: Boolean) = removeTask(id, deleteFile)

    fun clearCompleted() = tasks.filter { it.status == DlStatus.COMPLETED }.forEach { removeTask(it.id, false) }

    fun renameBeforeStart(id: String, newName: String) =
        update(id) { if (it.status == DlStatus.QUEUED || it.status == DlStatus.PAUSED) it.copy(request = it.request.copy(fileName = FileNames.sanitize(newName))) else it }

    fun outputExists(task: DownloadTask) = writer.exists(task.outputUri)

    fun writer(): OutputWriter = writer

    fun activeCount() = tasks.count { it.isActive }

    // ---- Scheduling ----------------------------------------------------------------------------------

    private fun allowed() = online && (!settings.bool(PrefKeys.DOWNLOAD_WIFI_ONLY) || unmetered)

    private fun pump() {
        if (!allowed()) {
            tasks.filter { it.status == DlStatus.QUEUED }.forEach { t -> update(t.id) { it.copy(status = DlStatus.WAITING_NETWORK) } }
            return
        }
        var slots = MAX_PARALLEL - handles.size
        for (t in tasks.toList()) {
            if (slots <= 0) break
            if (handles.containsKey(t.id)) continue
            if (t.status == DlStatus.QUEUED || t.status == DlStatus.WAITING_NETWORK) { start(t); slots-- }
        }
    }

    private fun start(task: DownloadTask) {
        val handle = Handle()
        handles[task.id] = handle
        update(task.id) { it.copy(status = DlStatus.RUNNING, error = null, errorDetail = null) }
        val current = tasks.first { it.id == task.id }
        val connections = settings.int(PrefKeys.DOWNLOAD_CONNECTIONS)
        executor.execute {
            DownloadRunner(app, current, connections, this, { online }, { handle.stop != null }).run()
        }
    }

    /** Called when connectivity or the Wi-Fi-only setting changes. */
    private fun reevaluate() {
        if (!allowed()) {
            handles.values.forEach { if (it.stop == null) it.stop = Stop.NETWORK }
            tasks.filter { it.status == DlStatus.QUEUED }.forEach { t -> update(t.id) { it.copy(status = DlStatus.WAITING_NETWORK) } }
        } else {
            pump()
        }
    }

    @Suppress("DEPRECATION")
    private fun watchNetwork() {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        fun applyCaps(caps: NetworkCapabilities?) {
            online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            unmetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
            reevaluate()
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { main.post { applyCaps(cm.getNetworkCapabilities(network)) } }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { main.post { applyCaps(caps) } }
            override fun onLost(network: Network) { main.post { online = false; unmetered = false; reevaluate() } }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) cm.registerDefaultNetworkCallback(callback)
            else cm.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), callback,
            )
            applyCaps(cm.getNetworkCapabilities(cm.activeNetwork))
        } catch (e: Exception) {
            DiagnosticLog.log("Downloads", "Network watcher unavailable", e)
        }
    }

    private fun ensureService() {
        if (activeCount() == 0) return
        try {
            ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        } catch (e: Exception) {
            // Starting a foreground service from the background can be refused; downloads still run.
            DiagnosticLog.log("Downloads", "Could not start download service", e)
        }
    }

    // ---- State helpers ------------------------------------------------------------------------------

    private fun update(id: String, change: (DownloadTask) -> DownloadTask) {
        val i = tasks.indexOfFirst { it.id == id }
        if (i < 0) return
        val old = tasks[i]
        val new = change(old)
        if (new != old) { tasks[i] = new; scheduleSave() }
    }

    private fun removeTask(id: String, deleteOutput: Boolean) {
        val task = tasks.firstOrNull { it.id == id } ?: return
        if (deleteOutput) writer.delete(task.outputUri)
        File(File(app.filesDir, "downloads"), id).deleteRecursively()
        notifications.cancel(task)
        tasks.removeAll { it.id == id }
        saveNow()
    }

    private fun scheduleSave() {
        if (saveQueued) return
        saveQueued = true
        main.postDelayed({ saveQueued = false; saveNow() }, 1500)
    }

    private fun saveNow() = store.save(tasks.toList())

    // ---- Runner callbacks (worker threads → main) -----------------------------------------------------

    override fun progress(id: String, downloaded: Long, total: Long, speedBps: Long, etaSec: Long, video: PartState, audio: PartState) {
        main.post {
            update(id) {
                if (it.status != DlStatus.RUNNING) it
                else it.copy(totalBytes = total, downloadedBytes = downloaded, speedBps = speedBps, etaSec = etaSec, video = video, audio = audio)
            }
        }
    }

    override fun merging(id: String) {
        main.post { update(id) { it.copy(status = DlStatus.MERGING, speedBps = 0, etaSec = -1) } }
    }

    override fun completed(id: String, uri: String, name: String) {
        main.post {
            handles.remove(id)
            update(id) {
                it.copy(
                    status = DlStatus.COMPLETED, downloadedBytes = it.totalBytes, speedBps = 0, etaSec = 0,
                    outputUri = uri, outputName = name, completedAt = System.currentTimeMillis(),
                    video = PartState(), audio = PartState(),
                )
            }
            saveNow()
            tasks.firstOrNull { it.id == id }?.let { notifications.completed(it) }
            pump()
        }
    }

    override fun failed(id: String, error: DlError, detail: String?) {
        main.post {
            handles.remove(id)
            update(id) { it.copy(status = DlStatus.FAILED, error = error, errorDetail = detail, speedBps = 0, etaSec = -1) }
            saveNow()
            tasks.firstOrNull { it.id == id }?.let { notifications.failed(it, errorMessage(error)) }
            pump()
        }
    }

    override fun stopped(id: String) {
        main.post {
            val handle = handles.remove(id)
            when {
                handle?.stop == Stop.CANCEL -> removeTask(id, false)
                handle?.stop == Stop.PAUSE -> {
                    val again = resumeAfterStop.remove(id)
                    update(id) { it.copy(status = if (again) DlStatus.QUEUED else DlStatus.PAUSED, speedBps = 0, etaSec = -1) }
                }
                // Network loss, or the runner stopped by itself because it went offline.
                else -> update(id) { it.copy(status = DlStatus.WAITING_NETWORK, speedBps = 0, etaSec = -1) }
            }
            saveNow()
            pump()
        }
    }

    fun errorMessage(error: DlError?): String = app.getString(
        when (error) {
            DlError.LIVE -> R.string.dl_err_live
            DlError.PREMIERE -> R.string.dl_err_premiere
            DlError.HTTP_403 -> R.string.dl_err_403
            DlError.HTTP_429 -> R.string.dl_err_429
            DlError.STORAGE -> R.string.dl_err_storage
            DlError.MEMORY -> R.string.dl_err_memory
            DlError.NETWORK -> R.string.dl_err_network
            DlError.NO_STREAM -> R.string.dl_err_no_stream
            DlError.MERGE_FAILED -> R.string.dl_err_merge
            DlError.VERIFY_FAILED -> R.string.dl_err_verify
            else -> R.string.dl_err_other
        },
    )

    companion object {
        private const val MAX_PARALLEL = 2
    }
}
