package com.drishyopot.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.drishyopot.app.R
import com.drishyopot.app.diagnostics.DiagnosticLog
import com.drishyopot.app.settings.SettingsBackup
import com.drishyopot.app.settings.SettingsStore
import com.drishyopot.app.ui.components.DrishyScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupScreen(store: SettingsStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pendingImport by remember { mutableStateOf<Map<String, Any>?>(null) }

    val msgExportDone = stringResource(R.string.settings_export_done)
    val msgExportFailed = stringResource(R.string.settings_export_failed)
    val msgImportDone = stringResource(R.string.settings_import_done)
    val msgImportFailed = stringResource(R.string.settings_import_failed)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(SettingsBackup.export(store).toByteArray(Charsets.UTF_8))
                    } != null
                } catch (e: Exception) {
                    DiagnosticLog.log("Backup", "Export failed", e)
                    false
                }
            }
            snackbar.showSnackbar(if (ok) msgExportDone else msgExportFailed)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                try {
                    val text = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }
                    text?.let { SettingsBackup.parse(it) }
                } catch (e: Exception) {
                    DiagnosticLog.log("Backup", "Import read failed", e)
                    null
                }
            }
            if (parsed == null) snackbar.showSnackbar(msgImportFailed) else pendingImport = parsed
        }
    }

    DrishyScaffold(
        title = stringResource(R.string.settings_backup_restore),
        onBack = onBack,
        snackbarHostState = snackbar,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                modifier = Modifier.clickable { exportLauncher.launch("drishyopot-settings.json") },
                headlineContent = { Text(stringResource(R.string.settings_export)) },
                supportingContent = { Text(stringResource(R.string.settings_export_summary)) },
                leadingContent = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
            )
            ListItem(
                modifier = Modifier.clickable { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                headlineContent = { Text(stringResource(R.string.settings_import)) },
                supportingContent = { Text(stringResource(R.string.settings_import_summary)) },
                leadingContent = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
            )
        }
    }

    pendingImport?.let { values ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.settings_import_confirm_title)) },
            text = { Text(stringResource(R.string.settings_import_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    store.restore(values)
                    pendingImport = null
                    scope.launch { snackbar.showSnackbar(msgImportDone) }
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
