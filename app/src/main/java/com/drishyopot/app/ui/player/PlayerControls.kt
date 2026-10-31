package com.drishyopot.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drishyopot.app.R
import com.drishyopot.app.player.EndMode
import com.drishyopot.app.sponsorblock.SponsorSegment
import java.util.Locale

enum class PlayerDialog {
    QUALITY, SPEED, AUDIO, SUBTITLES, RESIZE, SUBTITLE_STYLE, INFO, SPONSOR_SEGMENTS,
    CAST,
    // Actions handled directly by the screen
    AUDIO_ONLY, PIP, SHARE, CLOSE_PLAYER, QUEUE,
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}

class ControlsModel(
    val title: String,
    val isPlaying: Boolean,
    val ended: Boolean,
    val isLive: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val bufferedMs: Long,
    val fullscreen: Boolean,
    val locked: Boolean,
    val subtitlesOn: Boolean,
    val hasSubtitles: Boolean,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val endMode: EndMode,
    val audioOnly: Boolean,
    val pipAvailable: Boolean,
    val segments: List<SponsorSegment> = emptyList(),
    val castActive: Boolean = false,
)

@Composable
fun PlayerControls(
    model: ControlsModel,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekPreview: (Long) -> Unit,
    onSeekCommit: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCycleEndMode: () -> Unit,
    onOpenDialog: (PlayerDialog) -> Unit,
    onInteract: () -> Unit,
) {
    val scrim = Color.Black.copy(alpha = 0.45f)
    if (model.locked) {
        Box(Modifier.fillMaxSize()) {
            IconButton(
                onClick = onToggleLock,
                modifier = Modifier.align(Alignment.CenterStart).padding(16.dp).background(scrim, MaterialTheme.shapes.large),
            ) { Icon(Icons.Filled.Lock, stringResource(R.string.unlock_screen), tint = Color.White) }
        }
        return
    }

    var menuOpen by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableStateOf(0f) }

    Box(Modifier.fillMaxSize().background(scrim)) {
        // Top bar
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back), tint = Color.White) }
            Text(model.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = { onOpenDialog(PlayerDialog.CAST); onInteract() }) {
                Icon(
                    if (model.castActive) Icons.Filled.CastConnected else Icons.Filled.Cast,
                    stringResource(R.string.cast), tint = Color.White,
                )
            }
            IconButton(onClick = { onOpenDialog(PlayerDialog.QUEUE); onInteract() }) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.queue), tint = Color.White)
            }
            IconButton(onClick = { onCycleEndMode(); onInteract() }) {
                Icon(
                    when (model.endMode) {
                        EndMode.NEXT -> Icons.AutoMirrored.Filled.PlaylistPlay
                        EndMode.LOOP -> Icons.Filled.RepeatOne
                        EndMode.PAUSE -> Icons.Filled.PauseCircle
                        EndMode.SHUFFLE -> Icons.Filled.Shuffle
                    },
                    stringResource(R.string.playback_end_mode), tint = Color.White,
                )
            }
            IconButton(onClick = { onToggleSubtitles(); onInteract() }, enabled = model.hasSubtitles) {
                Icon(
                    if (model.subtitlesOn) Icons.Filled.ClosedCaption else Icons.Filled.ClosedCaptionOff,
                    stringResource(R.string.subtitles),
                    tint = if (model.hasSubtitles) Color.White else Color.Gray,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true; onInteract() }) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options), tint = Color.White)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    val entries = listOf(
                        PlayerDialog.QUALITY to R.string.quality,
                        PlayerDialog.SPEED to R.string.playback_speed,
                        PlayerDialog.AUDIO to R.string.audio_track,
                        PlayerDialog.SUBTITLES to R.string.subtitles,
                        PlayerDialog.SUBTITLE_STYLE to R.string.subtitle_style,
                        PlayerDialog.SPONSOR_SEGMENTS to R.string.sb_segments,
                        PlayerDialog.RESIZE to R.string.resize_mode,
                        PlayerDialog.AUDIO_ONLY to (if (model.audioOnly) R.string.audio_only_off else R.string.audio_only_on),
                        PlayerDialog.SHARE to R.string.share,
                        PlayerDialog.DOWNLOAD to R.string.dl_download,
                        PlayerDialog.INFO to R.string.info,
                        PlayerDialog.CLOSE_PLAYER to R.string.close_player,
                    ).let { base ->
                        if (model.pipAvailable) base.take(base.size - 1) + (PlayerDialog.PIP to R.string.pip_enter) + base.last() else base
                    }
                    entries.forEach { (dialog, label) ->
                        DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { menuOpen = false; onOpenDialog(dialog) })
                    }
                }
            }
        }

        // Center previous / play-pause / next
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton(onClick = { onPrevious(); onInteract() }, enabled = model.hasPrevious, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.SkipPrevious, stringResource(R.string.action_previous),
                    tint = if (model.hasPrevious) Color.White else Color.Gray, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(72.dp)) {
                Icon(
                    when {
                        model.ended -> Icons.Filled.Replay
                        model.isPlaying -> Icons.Filled.Pause
                        else -> Icons.Filled.PlayArrow
                    },
                    contentDescription = stringResource(if (model.isPlaying) R.string.action_pause else R.string.action_play),
                    tint = Color.White,
                    modifier = Modifier.size(56.dp),
                )
            }
            IconButton(onClick = { onNext(); onInteract() }, enabled = model.hasNext, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.SkipNext, stringResource(R.string.action_next),
                    tint = if (model.hasNext) Color.White else Color.Gray, modifier = Modifier.size(36.dp))
            }
        }

        // Bottom bar
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val shownPos = if (scrubbing) scrubValue.toLong() else model.positionMs
                Text(formatTime(shownPos), color = Color.White, style = MaterialTheme.typography.labelMedium)
                if (model.isLive) {
                    Box(Modifier.weight(1f))
                    Text(stringResource(R.string.player_live), color = Color.Red, style = MaterialTheme.typography.labelLarge)
                } else {
                    SeekBar(
                        positionMs = shownPos,
                        durationMs = model.durationMs,
                        bufferedMs = model.bufferedMs,
                        segments = model.segments,
                        onScrub = { scrubbing = true; scrubValue = it.toFloat(); onSeekPreview(it); onInteract() },
                        onCommit = { scrubbing = false; onSeekCommit(it) },
                        modifier = Modifier.weight(1f),
                    )
                    Text(formatTime(model.durationMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = onToggleLock) { Icon(Icons.Filled.LockOpen, stringResource(R.string.lock_screen), tint = Color.White) }
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        if (model.fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        stringResource(R.string.action_fullscreen),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}
