@file:OptIn(androidx.media3.common.util.UnstableApi::class, ExperimentalMaterial3Api::class)

package com.drishyopot.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drishyopot.app.R
import com.drishyopot.app.player.EndMode
import com.drishyopot.app.player.PlaybackHub

fun endModeLabel(mode: EndMode): Int = when (mode) {
    EndMode.NEXT -> R.string.playback_end_next
    EndMode.LOOP -> R.string.playback_end_loop
    EndMode.PAUSE -> R.string.playback_end_pause
    EndMode.SHUFFLE -> R.string.playback_end_shuffle
}

@Composable
fun QueueSheet(hub: PlaybackHub, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newUrl by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    // Reading queueVersion makes the list refresh when the queue changes.
    val version = hub.queueVersion
    val items = remember(version) { hub.queueItems() }
    val current = remember(version) { hub.currentIndex() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.queue), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.queue_autoplay), modifier = Modifier.padding(end = 8.dp))
                Switch(checked = hub.autoplay, onCheckedChange = { hub.updateAutoplay(it) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EndMode.entries.forEach { mode ->
                    FilterChip(
                        selected = hub.endMode == mode,
                        onClick = { hub.updateEndMode(mode) },
                        label = { Text(stringResource(endModeLabel(mode)), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newUrl, onValueChange = { newUrl = it },
                    label = { Text(stringResource(R.string.open_url_hint)) },
                    singleLine = true, modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { if (newUrl.isNotBlank()) { hub.enqueue(newUrl.trim()); newUrl = "" } },
                    enabled = newUrl.isNotBlank(),
                ) { Icon(Icons.Filled.Add, stringResource(R.string.add_to_queue)) }
            }

            if (items.isEmpty()) {
                Text(stringResource(R.string.queue_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    itemsIndexed(items, key = { i, e -> "$i-${e.url}" }) { index, entry ->
                        val isCurrent = index == current
                        ListItem(
                            modifier = Modifier.clickable { hub.jumpTo(index) },
                            leadingContent = {
                                if (isCurrent) Icon(Icons.Filled.PlayArrow, stringResource(R.string.queue_now_playing))
                                else Text("${index + 1}")
                            },
                            headlineContent = {
                                Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal)
                            },
                            supportingContent = {
                                Text(if (isCurrent) stringResource(R.string.queue_now_playing) else entry.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { hub.move(index, index - 1) }, enabled = index > 0) {
                                        Icon(Icons.Filled.KeyboardArrowUp, null)
                                    }
                                    IconButton(onClick = { hub.move(index, index + 1) }, enabled = index < items.lastIndex) {
                                        Icon(Icons.Filled.KeyboardArrowDown, null)
                                    }
                                    IconButton(onClick = { hub.removeAt(index) }) {
                                        Icon(Icons.Filled.Close, stringResource(R.string.queue_remove))
                                    }
                                }
                            },
                        )
                    }
                }
                TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.clear_queue)) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_queue)) },
            text = { Text(stringResource(R.string.clear_queue_confirm)) },
            confirmButton = { TextButton(onClick = { hub.clearQueue(); confirmClear = false }) { Text(stringResource(R.string.confirm)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
