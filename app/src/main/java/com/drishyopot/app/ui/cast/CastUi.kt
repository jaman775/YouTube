package com.drishyopot.app.ui.cast

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drishyopot.app.R
import com.drishyopot.app.cast.CastController
import com.drishyopot.app.ui.player.SeekBar
import com.drishyopot.app.ui.player.formatTime

private const val HELP_URL = "https://support.google.com/chromecast"

/** Device picker plus the "open on any device" link. */
@Composable
fun CastDialog(cast: CastController, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val discovery = cast.discovery
    val state = cast.state

    DisposableEffect(Unit) {
        discovery.start()
        onDispose { discovery.stop() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cast)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.cast_section_devices), style = MaterialTheme.typography.titleSmall)
                if (discovery.devices.isEmpty()) {
                    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (discovery.searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            stringResource(if (discovery.searching) R.string.cast_searching else R.string.cast_no_devices),
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    Text(stringResource(R.string.cast_no_devices_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    discovery.devices.forEach { device ->
                        ListItem(
                            modifier = Modifier.clickable { cast.castTo(device); onDismiss() },
                            leadingContent = { Icon(Icons.Filled.Cast, null) },
                            headlineContent = { Text(device.name) },
                            supportingContent = if (device.model.isNotBlank()) { { Text(device.model) } } else null,
                        )
                    }
                }
                Text(
                    stringResource(R.string.cast_security_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )

                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(stringResource(R.string.cast_section_link), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.cast_link_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                val link = state.linkUrl
                if (link == null) {
                    OutlinedButton(onClick = { cast.startLink() }) { Text(stringResource(R.string.cast_link_create)) }
                } else {
                    SelectionContainer { Text(link, style = MaterialTheme.typography.bodyMedium) }
                    Text(
                        stringResource(if (state.linkConnected) R.string.cast_link_connected else R.string.cast_link_waiting),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.linkConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("link", link))
                        }) { Text(stringResource(R.string.browser_copy_link)) }
                        TextButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, link) }
                            context.startActivity(Intent.createChooser(send, null))
                        }) { Text(stringResource(R.string.share)) }
                        TextButton(onClick = { cast.stopLink() }) { Text(stringResource(R.string.cast_link_close)) }
                    }
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = {
            TextButton(onClick = {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(HELP_URL))) } catch (_: Exception) { }
            }) { Text(stringResource(R.string.cast_learn)) }
        },
    )
}

/** Full-area remote control shown instead of the video while a TV is playing. */
@Composable
fun CastPanel(cast: CastController, title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state = cast.state
    Box(modifier.fillMaxSize().background(Color(0xFF101820))) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back), tint = Color.White)
        }
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.Cast, null, tint = Color.White, modifier = Modifier.size(32.dp))
            Text(
                stringResource(if (state.connecting) R.string.cast_connecting else R.string.casting_to, state.deviceName.orEmpty()),
                color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
            )
            Text(title, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            state.error?.let { Text(it, color = Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }

            if (state.casting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(state.positionMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    SeekBar(
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        bufferedMs = 0,
                        segments = emptyList(),
                        onScrub = { },
                        onCommit = { cast.seekTo(it) },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text(formatTime(state.durationMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { cast.toggleMute() }) {
                        Icon(
                            if (state.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            stringResource(R.string.cast_volume), tint = Color.White,
                        )
                    }
                    var volume by remember(state.volume) { mutableStateOf(state.volume) }
                    Slider(
                        value = volume,
                        onValueChange = { volume = it },
                        onValueChangeFinished = { cast.setVolume(volume) },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { cast.togglePlay() }, modifier = Modifier.size(56.dp)) {
                        val playing = state.playerState == "PLAYING" || state.playerState == "BUFFERING"
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            stringResource(if (playing) R.string.action_pause else R.string.action_play),
                            tint = Color.White, modifier = Modifier.size(40.dp),
                        )
                    }
                }
            } else if (state.connecting) {
                CircularProgressIndicator(color = Color.White)
            }
            Button(onClick = { cast.stopCasting() }) { Text(stringResource(R.string.cast_stop)) }
        }
    }
}
