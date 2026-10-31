package com.drishyopot.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import android.widget.Toast
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import com.drishyopot.app.player.PlaybackHub
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drishyopot.app.R
import com.drishyopot.app.Routes
import com.drishyopot.app.ui.components.DrishyScaffold

private data class Phase(val title: Int, val done: Boolean)

private val phases = listOf(
    Phase(R.string.roadmap_phase_1, true),
    Phase(R.string.roadmap_phase_2, true),
    Phase(R.string.roadmap_phase_3, true),
    Phase(R.string.roadmap_phase_4, true),
    Phase(R.string.roadmap_phase_5, true),
    Phase(R.string.roadmap_phase_6, true),
    Phase(R.string.roadmap_phase_7, true),
)

@Composable
fun HomeScreen(hub: PlaybackHub, onNavigate: (String) -> Unit, onPlay: (String, String?) -> Unit, onDownload: (String) -> Unit) {
    val context = LocalContext.current
    var url by rememberSaveable { mutableStateOf("") }
    var videoId by rememberSaveable { mutableStateOf("") }
    DrishyScaffold(title = stringResource(R.string.app_name), onBack = null) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.open_url_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = videoId,
                        onValueChange = { videoId = it.trim() },
                        label = { Text(stringResource(R.string.sb_video_id_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Button(
                        onClick = { onPlay(url.trim(), videoId.ifBlank { null }) },
                        enabled = url.isNotBlank(),
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text(stringResource(R.string.open_url_play)) }
                    OutlinedButton(
                        onClick = { onDownload(url.trim()) },
                        enabled = url.isNotBlank(),
                        modifier = Modifier.padding(top = 4.dp),
                    ) { Text(stringResource(R.string.dl_download)) }
                    OutlinedButton(
                        onClick = {
                            val added = hub.enqueue(url.trim(), null, videoId.ifBlank { null })
                            Toast.makeText(
                                context,
                                if (added) R.string.queue_added else R.string.queue_already_added,
                                Toast.LENGTH_SHORT,
                            ).show()
                            if (added) { url = ""; videoId = "" }
                        },
                        enabled = url.isNotBlank(),
                        modifier = Modifier.padding(top = 4.dp),
                    ) { Text(stringResource(R.string.add_to_queue)) }
                }
            }
            item { NavRow(Icons.Filled.Public, R.string.home_browse, R.string.home_browse_summary) { onNavigate(Routes.BROWSER) } }
            item { NavRow(Icons.Filled.Settings, R.string.home_settings, R.string.home_settings_summary) { onNavigate(Routes.SETTINGS) } }
            item { NavRow(Icons.Filled.Download, R.string.home_downloads, R.string.home_downloads_summary) { onNavigate(Routes.DOWNLOADS) } }
            item { NavRow(Icons.Filled.Info, R.string.home_about, R.string.home_about_summary) { onNavigate(Routes.ABOUT) } }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item {
                Card(Modifier.padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(
                            stringResource(R.string.roadmap_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        phases.forEach { phase ->
                            ListItem(
                                headlineContent = { Text(stringResource(phase.title)) },
                                supportingContent = {
                                    Text(stringResource(if (phase.done) R.string.roadmap_done else R.string.roadmap_pending))
                                },
                                leadingContent = {
                                    Icon(
                                        if (phase.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: Int, summary: Int, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(summary)) },
        leadingContent = { Icon(icon, contentDescription = null) },
    )
}
