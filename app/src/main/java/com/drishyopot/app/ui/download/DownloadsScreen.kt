package com.drishyopot.app.ui.download

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drishyopot.app.R
import com.drishyopot.app.download.DlStatus
import com.drishyopot.app.download.DownloadCenter
import com.drishyopot.app.download.DownloadOptions
import com.drishyopot.app.download.DownloadTask
import com.drishyopot.app.settings.PrefKeys
import com.drishyopot.app.settings.SettingsStore

private enum class Filter { ALL, IN_PROGRESS, COMPLETED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    center: DownloadCenter,
    store: SettingsStore,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlayFile: (url: String, title: String) -> Unit,
) {
    var filter by remember { mutableStateOf(Filter.ALL) }
    val all = center.tasks.toList().sortedByDescending { it.createdAt }
    val shown = when (filter) {
        Filter.ALL -> all
        Filter.IN_PROGRESS -> all.filter { it.status != DlStatus.COMPLETED }
        Filter.COMPLETED -> all.filter { it.status == DlStatus.COMPLETED }
    }
    val hasPausable = all.any { it.isActive }
    val waitingForWifi = store.bool(PrefKeys.DOWNLOAD_WIFI_ONLY) && center.online && !center.unmetered

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back)) } },
                actions = {
                    IconButton(onClick = { if (hasPausable) center.pauseAll() else center.resumeAll() }, enabled = all.any { it.status != DlStatus.COMPLETED }) {
                        Icon(
                            if (hasPausable) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            stringResource(if (hasPausable) R.string.dl_pause_all else R.string.dl_resume_all),
                        )
                    }
                    IconButton(onClick = { center.clearCompleted() }, enabled = all.any { it.status == DlStatus.COMPLETED }) {
                        Icon(Icons.Filled.DeleteSweep, stringResource(R.string.dl_clear_completed))
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.home_settings)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == Filter.ALL, onClick = { filter = Filter.ALL }, label = { Text(stringResource(R.string.dl_filter_all)) })
                FilterChip(selected = filter == Filter.IN_PROGRESS, onClick = { filter = Filter.IN_PROGRESS }, label = { Text(stringResource(R.string.dl_filter_progress)) })
                FilterChip(selected = filter == Filter.COMPLETED, onClick = { filter = Filter.COMPLETED }, label = { Text(stringResource(R.string.dl_filter_done)) })
            }
            if (!center.online) Banner(stringResource(R.string.dl_banner_offline))
            else if (waitingForWifi) Banner(stringResource(R.string.dl_banner_wifi))

            if (shown.isEmpty()) {
                Text(stringResource(R.string.dl_empty), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                    items(shown, key = { it.id }) { task -> DownloadRow(task, center, onPlayFile) }
                }
            }
        }
    }
}

@Composable
private fun Banner(text: String) {
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun DownloadRow(task: DownloadTask, center: DownloadCenter, onPlayFile: (String, String) -> Unit) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val done = task.status == DlStatus.COMPLETED
    val fileMissing = done && remember(task.id, task.outputUri) { !center.outputExists(task) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(task.outputName ?: task.request.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                statusLine(task, fileMissing, center),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!done) {
                if (task.status == DlStatus.MERGING || (task.totalBytes <= 0 && task.status == DlStatus.RUNNING)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { task.progress }, modifier = Modifier.fillMaxWidth())
                }
                if (task.totalBytes > 0) {
                    Text(
                        "${DownloadOptions.formatSize(task.downloadedBytes)} / ${DownloadOptions.formatSize(task.totalBytes)}" +
                            if (task.status == DlStatus.RUNNING) " · ${DownloadOptions.formatSpeed(task.speedBps)} · ${stringResource(R.string.dl_eta, DownloadOptions.formatEta(task.etaSec))}" else "",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (task.status == DlStatus.FAILED) {
                Text(center.errorMessage(task.error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
                when (task.status) {
                    DlStatus.QUEUED, DlStatus.RUNNING, DlStatus.WAITING_NETWORK -> {
                        IconButton(onClick = { center.pause(task.id) }) { Icon(Icons.Filled.Pause, stringResource(R.string.dl_pause)) }
                        IconButton(onClick = { center.cancel(task.id) }) { Icon(Icons.Filled.Close, stringResource(R.string.cancel)) }
                    }
                    DlStatus.MERGING -> Unit
                    DlStatus.PAUSED -> {
                        IconButton(onClick = { center.resume(task.id) }) { Icon(Icons.Filled.PlayArrow, stringResource(R.string.dl_resume)) }
                        IconButton(onClick = { center.cancel(task.id) }) { Icon(Icons.Filled.Close, stringResource(R.string.cancel)) }
                    }
                    DlStatus.FAILED -> {
                        IconButton(onClick = { center.resume(task.id) }) { Icon(Icons.Filled.Refresh, stringResource(R.string.retry)) }
                        IconButton(onClick = { center.remove(task.id, false) }) { Icon(Icons.Filled.Close, stringResource(R.string.dl_remove)) }
                    }
                    DlStatus.COMPLETED -> {
                        IconButton(enabled = !fileMissing, onClick = {
                            task.outputUri?.let { onPlayFile(center.writer().playUri(it), task.outputName ?: task.request.title) }
                        }) { Icon(Icons.Filled.PlayArrow, stringResource(R.string.action_play)) }
                        IconButton(enabled = !fileMissing, onClick = { shareFile(context, center, task) }) { Icon(Icons.Filled.Share, stringResource(R.string.share)) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.dl_delete)) }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.dl_delete)) },
            text = { Text(task.outputName ?: task.request.title) },
            confirmButton = {
                TextButton(onClick = { center.remove(task.id, true); confirmDelete = false }) { Text(stringResource(R.string.dl_delete_file)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { center.remove(task.id, false); confirmDelete = false }) { Text(stringResource(R.string.dl_remove)) }
                    TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
}

@Composable
private fun statusLine(task: DownloadTask, fileMissing: Boolean, center: DownloadCenter): String {
    if (fileMissing) return stringResource(R.string.dl_file_missing)
    return stringResource(
        when (task.status) {
            DlStatus.QUEUED -> R.string.dl_status_queued
            DlStatus.RUNNING -> R.string.dl_status_running
            DlStatus.WAITING_NETWORK -> R.string.dl_status_waiting
            DlStatus.PAUSED -> R.string.dl_status_paused
            DlStatus.MERGING -> R.string.dl_status_merging
            DlStatus.COMPLETED -> R.string.dl_status_done
            DlStatus.FAILED -> R.string.dl_status_failed
        },
    )
}

private fun shareFile(context: Context, center: DownloadCenter, task: DownloadTask) {
    val path = task.outputUri ?: return
    val name = task.outputName.orEmpty().lowercase()
    val mime = when {
        name.endsWith(".mp4") -> "video/mp4"
        name.endsWith(".webm") -> if (task.request.mode == com.drishyopot.app.download.DlMode.AUDIO) "audio/webm" else "video/webm"
        name.endsWith(".m4a") -> "audio/mp4"
        else -> "*/*"
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, center.writer().shareUri(path))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try { context.startActivity(Intent.createChooser(send, null)) } catch (_: Exception) { }
}
