package com.drishyopot.app.ui.settings

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drishyopot.app.R
import com.drishyopot.app.account.YoutubeAccount
import com.drishyopot.app.engine.EngineHost
import com.drishyopot.app.engine.EngineInfo
import com.drishyopot.app.engine.EngineSource
import com.drishyopot.app.engine.UpdateStatus
import com.drishyopot.app.settings.PrefKeys
import com.drishyopot.app.settings.SettingsStore
import com.drishyopot.app.ui.components.DrishyScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun SwitchRow(title: Int, summary: Int, key: String, store: SettingsStore) {
    val revision by store.revision.collectAsStateWithLifecycle()
    val checked = remember(revision) { store.bool(key) }
    ListItem(
        modifier = Modifier.clickable { store.setBool(key, !checked) },
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(summary)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = { store.setBool(key, it) }) },
    )
}

/** Sign in or out of YouTube and choose how the account is used. */
@Composable
fun AccountScreen(store: SettingsStore, onBack: () -> Unit, onSignIn: () -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    var confirmSignOut by remember { mutableStateOf(false) }
    val signedIn = remember(refresh) { YoutubeAccount.isSignedIn() }

    DrishyScaffold(title = stringResource(R.string.settings_account), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text(stringResource(if (signedIn) R.string.account_signed_in else R.string.account_signed_out)) },
                supportingContent = { Text(stringResource(R.string.account_note)) },
            )
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (signedIn) {
                    OutlinedButton(onClick = { confirmSignOut = true }) { Text(stringResource(R.string.account_sign_out)) }
                } else {
                    Button(onClick = onSignIn) { Text(stringResource(R.string.account_sign_in)) }
                }
            }
            SwitchRow(R.string.account_use, R.string.account_use_summary, PrefKeys.USE_ACCOUNT, store)
            SwitchRow(R.string.po_token_use, R.string.po_token_use_summary, PrefKeys.USE_PO_TOKEN, store)
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.account_sign_out)) },
            text = { Text(stringResource(R.string.account_sign_out_confirm)) },
            confirmButton = {
                TextButton(onClick = { YoutubeAccount.signOut(); refresh++; confirmSignOut = false }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Shows which engine is running and checks for a newer one. */
@Composable
fun EngineScreen(store: SettingsStore, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val updater = EngineHost.updater

    val info by produceState<EngineInfo?>(null, refresh) {
        value = withContext(Dispatchers.IO) { runCatching { EngineHost.info() }.getOrNull() }
    }
    val lastCheck = remember(refresh) { updater.lastCheckAt }
    val lastMessage = remember(refresh) { updater.lastMessage }

    val msgUpdated = stringResource(R.string.engine_result_updated)
    val msgCurrent = stringResource(R.string.engine_result_current)
    val msgNotConfigured = stringResource(R.string.engine_not_configured)
    val msgRejected = stringResource(R.string.engine_result_rejected)
    val msgIncompatible = stringResource(R.string.engine_result_incompatible)
    val msgUnsupported = stringResource(R.string.engine_unsupported)
    val msgFailed = stringResource(R.string.engine_result_failed)

    DrishyScaffold(title = stringResource(R.string.settings_engine), onBack = onBack, snackbarHostState = snackbar) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            val current = info
            ListItem(
                headlineContent = { Text(stringResource(R.string.engine_running)) },
                supportingContent = {
                    if (current == null) CircularProgressIndicator(Modifier.padding(4.dp))
                    else Text(
                        current.version + " · " + if (current.source == EngineSource.UPDATE) stringResource(R.string.engine_source_update, current.sequence)
                        else stringResource(R.string.engine_source_bundled),
                    )
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.engine_last_check)) },
                supportingContent = {
                    Text(
                        if (lastCheck == 0L) stringResource(R.string.engine_never_checked)
                        else DateUtils.getRelativeTimeSpanString(lastCheck).toString() + if (lastMessage.isNotEmpty()) " · $lastMessage" else "",
                    )
                },
            )
            if (!updater.configured) {
                Text(
                    stringResource(R.string.engine_not_configured_detail),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Button(
                    enabled = !busy && updater.configured,
                    onClick = {
                        scope.launch {
                            busy = true
                            val result = withContext(Dispatchers.IO) { updater.checkBlocking() }
                            busy = false
                            refresh++
                            snackbar.showSnackbar(
                                when (result.status) {
                                    UpdateStatus.UPDATED -> msgUpdated
                                    UpdateStatus.UP_TO_DATE -> msgCurrent
                                    UpdateStatus.NOT_CONFIGURED -> msgNotConfigured
                                    UpdateStatus.REJECTED -> msgRejected
                                    UpdateStatus.INCOMPATIBLE -> msgIncompatible
                                    UpdateStatus.UNSUPPORTED_DEVICE -> msgUnsupported
                                    UpdateStatus.FAILED -> msgFailed
                                },
                            )
                        }
                    },
                ) { Text(stringResource(if (busy) R.string.engine_checking else R.string.engine_check_now)) }
            }
            SwitchRow(R.string.engine_auto_update, R.string.engine_auto_update_summary, PrefKeys.AUTO_UPDATE_ENGINE, store)
        }
    }
}
