package com.drishyopot.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drishyopot.app.R
import com.drishyopot.app.core.Haptics
import com.drishyopot.app.settings.PrefKeys
import com.drishyopot.app.settings.SettingNode
import com.drishyopot.app.settings.SettingsCatalog
import com.drishyopot.app.settings.SettingsStore
import com.drishyopot.app.ui.components.DrishyScaffold
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(
    route: String,
    store: SettingsStore,
    pipSupported: Boolean,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
) {
    // Re-read values whenever any preference changes.
    val revision by store.revision.collectAsStateWithLifecycle()
    val catalog = remember(pipSupported) { SettingsCatalog.build(pipSupported) }
    val ids = route.removePrefix("settings").split('/').filter { it.isNotEmpty() }
    val group = SettingsCatalog.resolve(ids, catalog)
    val isRoot = ids.isEmpty()
    val nodes = if (isRoot) catalog else group?.children.orEmpty()
    val title = if (isRoot) stringResource(R.string.settings_title) else group?.let { stringResource(it.title) } ?: ""
    var confirmReset by remember { mutableStateOf(false) }

    DrishyScaffold(title = title, onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(nodes, key = { it.id }) { node ->
                // `revision` is read so this item recomposes after a change.
                key(revision) { NodeRow(node, store, route, onNavigate) }
            }
            if (isRoot) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    ListItem(
                        modifier = Modifier.clickable { confirmReset = true },
                        headlineContent = { Text(stringResource(R.string.reset_all_settings)) },
                        leadingContent = { Icon(Icons.Filled.RestartAlt, contentDescription = null) },
                    )
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_all_settings)) },
            text = { Text(stringResource(R.string.reset_all_message)) },
            confirmButton = {
                TextButton(onClick = { store.resetAll(); confirmReset = false }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun NodeRow(node: SettingNode, store: SettingsStore, route: String, onNavigate: (String) -> Unit) {
    when (node) {
        is SettingNode.Group -> ListItem(
            modifier = Modifier.clickable { onNavigate("$route/${node.id}") },
            headlineContent = { Text(stringResource(node.title)) },
            leadingContent = node.icon?.let { icon -> { Icon(icon, contentDescription = null) } },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        )

        is SettingNode.Nav -> ListItem(
            modifier = Modifier.clickable { onNavigate("$route/${node.id}") },
            headlineContent = { Text(stringResource(node.title)) },
            leadingContent = node.icon?.let { icon -> { Icon(icon, contentDescription = null) } },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        )

        is SettingNode.Toggle -> {
            val checked = store.bool(node.key)
            ListItem(
                modifier = Modifier.clickable { store.setBool(node.key, !checked) },
                headlineContent = { Text(stringResource(node.title)) },
                supportingContent = if (node.summary != 0) {
                    { Text(stringResource(node.summary)) }
                } else null,
                trailingContent = { Switch(checked = checked, onCheckedChange = { store.setBool(node.key, it) }) },
            )
        }

        is SettingNode.Slider -> HapticSlider(node, store)
    }
}

@Composable
private fun HapticSlider(node: SettingNode.Slider, store: SettingsStore) {
    val spec = PrefKeys.INT_SPECS.getValue(node.key)
    val context = LocalContext.current
    var value by remember { mutableStateOf(store.int(node.key).toFloat()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val shown = value.toInt()
        val label = if (shown == 0 && node.zeroLabel != 0) stringResource(node.zeroLabel) else shown.toString()
        Text("${stringResource(node.title)}: $label")
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = {
                val v = value.toInt()
                store.setInt(node.key, v)
                if (node.key == PrefKeys.HAPTIC_STRENGTH) Haptics.tick(context, v)
            },
            valueRange = spec.min.toFloat()..spec.max.toFloat(),
            steps = spec.max - spec.min - 1,
        )
    }
}
