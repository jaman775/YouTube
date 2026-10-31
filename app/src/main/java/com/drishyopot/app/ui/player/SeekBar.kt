package com.drishyopot.app.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.drishyopot.app.sponsorblock.SponsorSegment

/**
 * Seek bar that also paints SponsorBlock segments on the timeline.
 * Tap or drag to seek; [onScrub] fires while dragging and [onCommit] on release.
 */
@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    segments: List<SponsorSegment>,
    onScrub: (Long) -> Unit,
    onCommit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var dragMs by remember { mutableLongStateOf(0L) }
    val primary = MaterialTheme.colorScheme.primary
    val safeDuration = durationMs.coerceAtLeast(1L)
    val shown = if (dragging) dragMs else positionMs

    fun toMs(x: Float): Long = ((x / widthPx) * safeDuration).toLong().coerceIn(0L, safeDuration)

    Canvas(
        modifier
            .height(32.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(safeDuration) {
                detectTapGestures { offset ->
                    val t = toMs(offset.x)
                    onScrub(t)
                    onCommit(t)
                }
            }
            .pointerInput(safeDuration) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragMs = toMs(offset.x)
                        onScrub(dragMs)
                    },
                    onDragEnd = { dragging = false; onCommit(dragMs) },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    dragMs = toMs(change.position.x)
                    onScrub(dragMs)
                }
            },
    ) {
        val track = 4.dp.toPx()
        val top = (size.height - track) / 2f
        fun x(ms: Long) = (ms.toFloat() / safeDuration) * size.width

        drawRect(Color.White.copy(alpha = 0.3f), Offset(0f, top), Size(size.width, track))
        drawRect(Color.White.copy(alpha = 0.5f), Offset(0f, top), Size(x(bufferedMs.coerceIn(0L, safeDuration)), track))
        drawRect(primary, Offset(0f, top), Size(x(shown.coerceIn(0L, safeDuration)), track))

        // SponsorBlock segments sit on top of the track in their category colour.
        segments.forEach { seg ->
            val startX = x(seg.startMs.coerceIn(0L, safeDuration))
            val endX = x(seg.endMs.coerceIn(0L, safeDuration))
            val color = Color(seg.category.color)
            if (seg.isPoi) {
                drawRect(color, Offset(startX - 1.5.dp.toPx(), top - 3.dp.toPx()), Size(3.dp.toPx(), track + 6.dp.toPx()))
            } else {
                drawRect(color, Offset(startX, top), Size((endX - startX).coerceAtLeast(2.dp.toPx()), track))
            }
        }

        drawCircle(primary, radius = if (dragging) 9.dp.toPx() else 7.dp.toPx(), center = Offset(x(shown.coerceIn(0L, safeDuration)), size.height / 2f))
    }
}
