@file:OptIn(ExperimentalLayoutApi::class)

package com.drishyopot.app.ui.download

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.drishyopot.app.R
import com.drishyopot.app.download.DlMode
import com.drishyopot.app.download.DownloadCenter
import com.drishyopot.app.download.DownloadOptions
import com.drishyopot.app.download.DownloadRequest
import com.drishyopot.app.download.FileNames
import com.drishyopot.app.yt.YoutubeResolver
import com.drishyopot.app.yt.YtLink
import com.drishyopot.app.yt.YtPlaylist
import com.drishyopot.app.yt.YtVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Entry point: shows the right dialog for a video or a playlist link. */
@Composable
fun DownloadFlow(link: YtLink, center: DownloadCenter, onDismiss: () -> Unit) {
    when (link) {
        is YtLink.Video -> SingleDownload(link.id, center, onDismiss)
        is YtLink.Playlist -> PlaylistDownload(link.id, center, onDismiss)
        is YtLink.Page -> LaunchedEffect(Unit) { onDismiss() }
    }
}

@Composable
private fun rememberEnqueueWithPermission(center: DownloadCenter): (List<DownloadRequest>) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val added = stringResource(R.string.dl_added)
    return { requests ->
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        center.enqueue(requests)
        Toast.makeText(context, added, Toast.LENGTH_SHORT).show()
    }
}

// ---- One video ---------------------------------------------------------------------------------------

@Composable
private fun SingleDownload(videoId: String, center: DownloadCenter, onDismiss: () -> Unit) {
    val result by produceState<Result<YtVideo>?>(null, videoId) {
        value = withContext(Dispatchers.IO) { runCatching { YoutubeResolver.resolve(videoId) } }
    }
    val loaded = result
    when {
        loaded == null -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.dl_title)) },
            text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.padding(end = 16.dp)); Text(stringResource(R.string.dl_loading)) } },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
        loaded.isFailure -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.dl_title)) },
            text = { Text(stringResource(R.string.dl_err_resolve)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        )
        loaded.getOrNull()?.isLive == true -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.dl_title)) },
            text = { Text(stringResource(R.string.dl_err_live)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        )
        else -> SingleOptions(loaded.getOrThrow(), center, onDismiss)
    }
}

@Composable
private fun SingleOptions(video: YtVideo, center: DownloadCenter, onDismiss: () -> Unit) {
    val enqueue = rememberEnqueueWithPermission(center)
    val heights = remember(video) { DownloadOptions.heights(video) }
    val audioTracks = remember(video) { DownloadOptions.audioTracks(video) }
    val subtitleLangs = remember(video) { DownloadOptions.subtitleLanguages(video) }

    var mode by remember { mutableStateOf(DlMode.VIDEO) }
    var height by remember { mutableStateOf(heights.firstOrNull { it <= 1080 } ?: heights.firstOrNull() ?: 0) }
    var family by remember { mutableStateOf(DownloadOptions.families(video, height).firstOrNull().orEmpty()) }
    var audioTrack by remember { mutableStateOf<String?>(null) }
    val subs = remember { mutableStateListOf<String>() }
    var cover by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(FileNames.sanitize(video.title)) }

    val estimate = DownloadOptions.estimateBytes(video, mode, height, family, audioTrack)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dl_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == DlMode.VIDEO, onClick = { mode = DlMode.VIDEO }, label = { Text(stringResource(R.string.dl_mode_video)) })
                    FilterChip(selected = mode == DlMode.AUDIO, onClick = { mode = DlMode.AUDIO }, label = { Text(stringResource(R.string.dl_mode_audio)) })
                }

                if (mode == DlMode.VIDEO && heights.isNotEmpty()) {
                    Text(stringResource(R.string.quality), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        heights.forEach { h ->
                            FilterChip(
                                selected = h == height,
                                onClick = { height = h; family = DownloadOptions.families(video, h).firstOrNull().orEmpty() },
                                label = { Text("${h}p") },
                            )
                        }
                    }
                    val families = DownloadOptions.families(video, height)
                    if (families.size > 1) {
                        Text(stringResource(R.string.dl_codec), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            families.forEach { f ->
                                FilterChip(selected = f == family, onClick = { family = f }, label = { Text(DownloadOptions.familyLabel(f)) })
                            }
                        }
                    }
                }

                if (audioTracks.size > 1) {
                    Text(stringResource(R.string.audio_track), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        audioTracks.forEachIndexed { index, a ->
                            val selected = audioTrack == a.trackId || (audioTrack == null && index == 0)
                            FilterChip(selected = selected, onClick = { audioTrack = a.trackId }, label = { Text(a.label) })
                        }
                    }
                }

                if (subtitleLangs.isNotEmpty()) {
                    Text(stringResource(R.string.dl_subtitles), style = MaterialTheme.typography.labelLarge)
                    subtitleLangs.forEach { (lang, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { if (lang in subs) subs.remove(lang) else subs.add(lang) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = lang in subs, onCheckedChange = { if (it) subs.add(lang) else subs.remove(lang) })
                            Text(label)
                        }
                    }
                }

                Row(Modifier.fillMaxWidth().clickable { cover = !cover }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = cover, onCheckedChange = { cover = it })
                    Text(stringResource(R.string.dl_include_cover))
                }

                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.dl_file_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.dl_estimated_size, DownloadOptions.formatSize(estimate)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                enqueue(
                    listOf(
                        DownloadRequest(
                            videoId = video.id, title = video.title, uploader = video.uploader, thumbnail = video.thumbnail,
                            durationMs = video.durationMs, mode = mode, height = height, family = family,
                            audioTrackId = audioTrack, subtitleLangs = subs.toList(), includeCover = cover,
                            fileName = FileNames.sanitize(name, FileNames.sanitize(video.title)),
                        ),
                    ),
                )
                onDismiss()
            }) { Text(stringResource(R.string.dl_start)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ---- Playlist ----------------------------------------------------------------------------------------

private const val MAX_BATCH = 500
private val PLAYLIST_HEIGHTS = listOf(2160, 1440, 1080, 720, 480, 360, 240, 144)

@Composable
private fun PlaylistDownload(listId: String, center: DownloadCenter, onDismiss: () -> Unit) {
    val result by produceState<Result<YtPlaylist>?>(null, listId) {
        value = withContext(Dispatchers.IO) { runCatching { YoutubeResolver.playlist("https://www.youtube.com/playlist?list=$listId", MAX_BATCH) } }
    }
    val loaded = result
    if (loaded == null || loaded.isFailure || loaded.getOrThrow().items.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.dl_title)) },
            text = {
                if (loaded == null) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.padding(end = 16.dp)); Text(stringResource(R.string.dl_loading)) }
                else Text(stringResource(R.string.dl_err_resolve))
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(if (loaded == null) R.string.cancel else R.string.close)) } },
        )
        return
    }
    PlaylistOptions(loaded.getOrThrow(), center, onDismiss)
}

@Composable
private fun PlaylistOptions(playlist: YtPlaylist, center: DownloadCenter, onDismiss: () -> Unit) {
    val enqueue = rememberEnqueueWithPermission(center)
    val selected = remember { mutableStateListOf<String>().also { list -> list.addAll(playlist.items.map { it.id }) } }
    var mode by remember { mutableStateOf(DlMode.VIDEO) }
    var height by remember { mutableStateOf(1080) }
    var family by remember { mutableStateOf("") }
    var langs by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(playlist.title.ifBlank { stringResource(R.string.dl_title) }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.dl_selected_count, selected.size, playlist.items.size), Modifier.weight(1f))
                    TextButton(onClick = { selected.clear(); selected.addAll(playlist.items.map { it.id }) }) { Text(stringResource(R.string.dl_select_all)) }
                    TextButton(onClick = { selected.clear() }) { Text(stringResource(R.string.dl_select_none)) }
                }
                LazyColumn(Modifier.heightIn(max = 220.dp)) {
                    items(playlist.items, key = { it.id }) { item ->
                        Row(
                            Modifier.fillMaxWidth().clickable { if (item.id in selected) selected.remove(item.id) else selected.add(item.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = item.id in selected, onCheckedChange = { if (it) selected.add(item.id) else selected.remove(item.id) })
                            Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (playlist.truncated) Text(stringResource(R.string.dl_playlist_truncated, MAX_BATCH), style = MaterialTheme.typography.bodySmall)

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == DlMode.VIDEO, onClick = { mode = DlMode.VIDEO }, label = { Text(stringResource(R.string.dl_mode_video)) })
                    FilterChip(selected = mode == DlMode.AUDIO, onClick = { mode = DlMode.AUDIO }, label = { Text(stringResource(R.string.dl_mode_audio)) })
                }
                if (mode == DlMode.VIDEO) {
                    Text(stringResource(R.string.dl_max_quality), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PLAYLIST_HEIGHTS.forEach { h -> FilterChip(selected = h == height, onClick = { height = h }, label = { Text("${h}p") }) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("" to stringResource(R.string.dl_codec_auto), "avc" to "H.264", "vp9" to "VP9", "av1" to "AV1").forEach { (f, label) ->
                            FilterChip(selected = family == f, onClick = { family = f }, label = { Text(label) })
                        }
                    }
                }
                OutlinedTextField(
                    value = langs, onValueChange = { langs = it },
                    label = { Text(stringResource(R.string.dl_subtitle_codes)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth().clickable { cover = !cover }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = cover, onCheckedChange = { cover = it })
                    Text(stringResource(R.string.dl_include_cover))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty(),
                onClick = {
                    val batch = UUID.randomUUID().toString()
                    val subtitleCodes = langs.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
                    val requests = playlist.items.filter { it.id in selected }.take(MAX_BATCH).map { item ->
                        DownloadRequest(
                            videoId = item.id, title = item.title, uploader = item.uploader,
                            thumbnail = "https://i.ytimg.com/vi/${item.id}/hqdefault.jpg",
                            durationMs = item.durationSec * 1000, mode = mode, height = height, family = family,
                            audioTrackId = null, subtitleLangs = subtitleCodes, includeCover = cover,
                            fileName = FileNames.sanitize(item.title), batchId = batch,
                        )
                    }
                    enqueue(requests)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.dl_start)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
