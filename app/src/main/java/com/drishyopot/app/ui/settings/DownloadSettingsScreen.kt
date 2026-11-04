package com.drishyopot.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drishyopot.app.R
import com.drishyopot.app.settings.PrefKeys
import com.drishyopot.app.settings.SettingsStore
import com.drishyopot.app.ui.components.DrishyScaffold

/** Download preferences. The download manager itself is Phase 5. */
@Composable
fun DownloadSettingsScreen(store: SettingsStore, onBack: () -> Unit) {
    val revision by store.revision.collectAsStateWithLifecycle()
    val wifiOnly = remember(revision) { store.bool(PrefKeys.DOWNLOAD_WIFI_ONLY) }
    val spec = PrefKeys.INT_SPECS.getValue(PrefKeys.DOWNLOAD_CONNECTIONS)
    var connections by remember { mutableStateOf(store.int(PrefKeys.DOWNLOAD_CONNECTIONS).toFloat()) }

    DrishyScaffold(title = stringResource(R.string.downloads), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                modifier = Modifier.clickable { store.setBool(PrefKeys.DOWNLOAD_WIFI_ONLY, !wifiOnly) },
                headlineContent = { Text(stringResource(R.string.download_wifi_only)) },
                trailingContent = {
                    Switch(checked = wifiOnly, onCheckedChange = { store.setBool(PrefKeys.DOWNLOAD_WIFI_ONLY, it) })
                },
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("${stringResource(R.string.download_connections)}: ${connections.toInt()}")
                Slider(
                    value = connections,
                    onValueChange = { connections = it },
                    onValueChangeFinished = { store.setInt(PrefKeys.DOWNLOAD_CONNECTIONS, connections.toInt()) },
                    valueRange = spec.min.toFloat()..spec.max.toFloat(),
                    steps = spec.max - spec.min - 1,
                )
            }
            Text(
                stringResource(R.string.download_manager_soon),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
