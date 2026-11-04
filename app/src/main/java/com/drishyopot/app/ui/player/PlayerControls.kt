package com.drishyopot.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
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
import androidx.compose.material.icons.outlined.Settings
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
    CAST, SETTINGS, DOWNLOAD,
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
    val autoplay: Boolean = true,
    val hasChapters: Boolean = false,
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
    onToggleAutoplay: () -> Unit = {},
    onChapters: () -> Unit = {},
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

    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableStateOf(0f) }
    val youtubeRed = Color(0xFFFF0000)

    Box(Modifier.fillMaxSize().background(scrim)) {
        // Top: minimise on the left; autoplay, cast, captions and settings on the right (as in the YouTube app).
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.navigate_back), tint = Color.White, modifier = Modifier.size(32.dp))
            }
            if (model.fullscreen) {
                Text(model.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            } else Spacer(Modifier.weight(1f))
            AutoplayPill(model.autoplay) { onToggleAutoplay(); onInteract() }
            IconButton(onClick = { onOpenDialog(PlayerDialog.CAST); onInteract() }) {
                Icon(
                    if (model.castActive) Icons.Filled.CastConnected else Icons.Filled.Cast,
                    stringResource(R.string.cast), tint = Color.White,
                )
            }
            IconButton(onClick = { onToggleSubtitles(); onInteract() }, enabled = model.hasSubtitles) {
                Icon(
                    if (model.subtitlesOn) Icons.Filled.ClosedCaption else Icons.Filled.ClosedCaptionOff,
                    stringResource(R.string.subtitles),
                    tint = if (model.hasSubtitles) Color.White else Color.Gray,
                )
            }
            IconButton(onClick = { onOpenDialog(PlayerDialog.SETTINGS); onInteract() }) {
                Icon(Icons.Outlined.Settings, stringResource(R.string.player_settings), tint = Color.White)
            }
        }

        // Centre: previous, pause/play, next, each in a soft round button.
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            RoundButton(48.dp, model.hasPrevious, { onPrevious(); onInteract() }) {
                Icon(Icons.Filled.SkipPrevious, stringResource(R.string.action_previous), tint = Color.White, modifier = Modifier.size(28.dp))
            }
            RoundButton(68.dp, true, onPlayPause) {
                Icon(
                    when {
                        model.ended -> Icons.Filled.Replay
                        model.isPlaying -> Icons.Filled.Pause
                        else -> Icons.Filled.PlayArrow
                    },
                    contentDescription = stringResource(if (model.isPlaying) R.string.action_pause else R.string.action_play),
                    tint = Color.White,
                    modifier = Modifier.size(44.dp),
                )
            }
            RoundButton(48.dp, model.hasNext, { onNext(); onInteract() }) {
                Icon(Icons.Filled.SkipNext, stringResource(R.string.action_next), tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }

        // Bottom: time pill on the left, fullscreen on the right, thin red progress line along the very bottom.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val shownPos = if (scrubbing) scrubValue.toLong() else model.positionMs
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    if (model.isLive) {
                        Text(stringResource(R.string.player_live), color = youtubeRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("${formatTime(shownPos)} / ${formatTime(model.durationMs)}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
                if (model.hasChapters) {
                    Row(
                        Modifier.padding(start = 8.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.5f))
                            .clickable { onChapters(); onInteract() }.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.yt_in_this_video), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                RoundButton(40.dp, true, onToggleFullscreen) {
                    Icon(
                        if (model.fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        stringResource(R.string.action_fullscreen),
                        tint = Color.White,
                    )
                }
            }
            if (!model.isLive) {
                val shownPos = if (scrubbing) scrubValue.toLong() else model.positionMs
                SeekBar(
                    positionMs = shownPos,
                    durationMs = model.durationMs,
                    bufferedMs = model.bufferedMs,
                    segments = model.segments,
                    onScrub = { scrubbing = true; scrubValue = it.toFloat(); onSeekPreview(it); onInteract() },
                    onCommit = { scrubbing = false; onSeekCommit(it) },
                    modifier = Modifier.fillMaxWidth(),
                    accent = youtubeRed, trackDp = 3f, heightDp = 24f, thumbDp = 6f,
                )
            } else Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RoundButton(size: Dp, enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size).alpha(if (enabled) 1f else 0.4f).clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** YouTube's autoplay switch: a grey pill with a round knob holding a play arrow. */
@Composable
private fun AutoplayPill(on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 8.dp).width(40.dp).height(22.dp).clip(CircleShape)
            .background(Color.Gray.copy(alpha = if (on) 0.85f else 0.45f)).clickable(onClick = onClick),
    ) {
        Box(
            Modifier.align(if (on) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 2.dp)
                .size(18.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(13.dp)) }
    }
}
