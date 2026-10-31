package com.drishyopot.app.ui.about

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.drishyopot.app.R
import com.drishyopot.app.diagnostics.DiagnosticLog
import com.drishyopot.app.settings.SettingsBackup
import com.drishyopot.app.settings.SettingsStore
import com.drishyopot.app.update.UpdateChecker
import androidx.compose.material.icons.filled.SystemUpdate
import com.drishyopot.app.ui.components.DrishyScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AboutScreen(store: SettingsStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }

    val info = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    @Suppress("DEPRECATION")
    val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else info.versionCode
    val versionLine = stringResource(R.string.about_version, info.versionName ?: "?", versionCode)

    val msgCleared = stringResource(R.string.about_cache_cleared)
    val msgSaved = stringResource(R.string.about_save_done)
    val msgSaveFailed = stringResource(R.string.about_save_failed)
    val msgFailed = stringResource(R.string.operation_failed)
    val shareTitle = stringResource(R.string.about_share_chooser)
    val updateRepo = stringResource(R.string.update_repo)
    val msgUpdateFailed = stringResource(R.string.about_update_failed)
    val msgUpdateAvailable = stringResource(R.string.about_update_available)
    val msgUpToDate = stringResource(R.string.about_up_to_date)

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val ok = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use {
                        DiagnosticLog.writeArchive(context, SettingsBackup.export(store), it)
                    } != null
                } catch (e: Exception) {
                    DiagnosticLog.log("About", "Save diagnostics failed", e)
                    false
                }
            }
            busy = false
            snackbar.showSnackbar(if (ok) msgSaved else msgSaveFailed)
        }
    }

    DrishyScaffold(title = stringResource(R.string.about), onBack = onBack, snackbarHostState = snackbar) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text(versionLine, style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.about_license_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ListItem(
                modifier = Modifier.clickable(enabled = !busy) {
                    scope.launch {
                        busy = true
                        withContext(Dispatchers.IO) {
                            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                        }
                        busy = false
                        snackbar.showSnackbar(msgCleared)
                    }
                },
                headlineContent = { Text(stringResource(R.string.about_clear_cache)) },
                leadingContent = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
            )
            if (updateRepo.isNotBlank()) {
                ListItem(
                    modifier = Modifier.clickable(enabled = !busy) {
                        scope.launch {
                            busy = true
                            val latest = withContext(Dispatchers.IO) { UpdateChecker.latest(updateRepo) }
                            busy = false
                            when {
                                latest == null -> snackbar.showSnackbar(msgUpdateFailed)
                                UpdateChecker.isNewer(latest.tag, info.versionName.orEmpty()) -> {
                                    snackbar.showSnackbar(msgUpdateAvailable.format(latest.tag))
                                    if (latest.url.startsWith("https://")) {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latest.url)))
                                    }
                                }
                                else -> snackbar.showSnackbar(msgUpToDate)
                            }
                        }
                    },
                    headlineContent = { Text(stringResource(R.string.about_check_updates)) },
                    leadingContent = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
                )
            }
            ListItem(
                modifier = Modifier.clickable(enabled = !busy) {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    saveLauncher.launch("drishyopot-diagnostics-$stamp.zip")
                },
                headlineContent = { Text(stringResource(R.string.about_save_logs)) },
                leadingContent = { Icon(Icons.Filled.BugReport, contentDescription = null) },
            )
            ListItem(
                modifier = Modifier.clickable(enabled = !busy) {
                    scope.launch {
                        busy = true
                        val file = withContext(Dispatchers.IO) {
                            try {
                                val dir = File(context.cacheDir, "diagnostics").also { it.mkdirs() }
                                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                                File(dir, "drishyopot-diagnostics-$stamp.zip").also { f ->
                                    f.outputStream().use { DiagnosticLog.writeArchive(context, SettingsBackup.export(store), it) }
                                }
                            } catch (e: Exception) {
                                DiagnosticLog.log("About", "Share diagnostics failed", e)
                                null
                            }
                        }
                        busy = false
                        if (file == null) {
                            snackbar.showSnackbar(msgFailed)
                        } else {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, shareTitle))
                        }
                    }
                },
                headlineContent = { Text(stringResource(R.string.about_share_logs)) },
                leadingContent = { Icon(Icons.Filled.Share, contentDescription = null) },
            )
        }
    }
}
