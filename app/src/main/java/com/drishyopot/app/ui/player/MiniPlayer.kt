@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.drishyopot.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.drishyopot.app.R
import com.drishyopot.app.player.PlaybackHub
import kotlin.math.roundToInt

private val MINI_WIDTHS = listOf(150.dp, 220.dp, 300.dp)

/** Draggable in-app mini player. Position and size are remembered. */
@Composable
fun MiniPlayerHost(hub: PlaybackHub, onRestore: () -> Unit) {
    val item = hub.currentItem
    if (!hub.miniVisible || item == null) return
    val player = hub.player
    val prefs = hub.prefs
    val density = LocalDensity.current

    var sizeIndex by remember { mutableIntStateOf(prefs.miniSize) }
    var fx by remember { mutableFloatStateOf(prefs.miniX) }
    var fy by remember { mutableFloatStateOf(prefs.miniY) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }

    DisposableEffect(player) {
        val l = object : Player.Listener { override fun onIsPlayingChanged(p: Boolean) { isPlaying = p } }
        player.addListener(l)
        isPlaying = player.isPlaying
        onDispose { player.removeListener(l) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val parentW = with(density) { maxWidth.toPx() }
        val parentH = with(density) { maxHeight.toPx() }
        val winW = with(density) { MINI_WIDTHS[sizeIndex].toPx() }
        val winH = winW * 9f / 16f + with(density) { 44.dp.toPx() }
        val freeX = (parentW - winW).coerceAtLeast(0f)
        val freeY = (parentH - winH).coerceAtLeast(0f)

        Card(
            Modifier
                .width(MINI_WIDTHS[sizeIndex])
                .offset { IntOffset((fx * freeX).roundToInt(), (fy * freeY).roundToInt()) },
        ) {
            Column {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
                        .pointerInput(freeX, freeY) {
                            detectDragGestures(
                                onDragEnd = { prefs.miniX = fx; prefs.miniY = fy },
                            ) { change, drag ->
                                change.consume()
                                if (freeX > 0f) fx = (fx + drag.x / freeX).coerceIn(0f, 1f)
                                if (freeY > 0f) fy = (fy + drag.y / freeY).coerceIn(0f, 1f)
                            }
                        }
                        .pointerInput(Unit) { detectTapGestures(onTap = { onRestore() }) },
                ) {
                    AndroidView(
                        modifier = Modifier.matchParentSize(),
                        factory = { ctx -> PlayerView(ctx).apply { useController = false; this.player = player } },
                        update = { it.player = player },
                        onRelease = { it.player = null },
                    )
                }
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        item.mediaMetadata.title?.toString().orEmpty(),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                    IconButton(onClick = {
                        if (player.playbackState == Player.STATE_IDLE) player.prepare()
                        if (player.isPlaying) player.pause() else player.play()
                    }) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            stringResource(if (isPlaying) R.string.action_pause else R.string.action_play),
                        )
                    }
                    IconButton(onClick = { sizeIndex = (sizeIndex + 1) % MINI_WIDTHS.size; prefs.miniSize = sizeIndex }) {
                        Icon(Icons.Filled.ZoomOutMap, stringResource(R.string.resize_mode))
                    }
                    IconButton(onClick = onRestore) { Icon(Icons.Filled.Fullscreen, stringResource(R.string.action_restore_watch)) }
                    IconButton(onClick = { hub.closePlayer() }) { Icon(Icons.Filled.Close, stringResource(R.string.close_player)) }
                }
            }
        }
    }
}
