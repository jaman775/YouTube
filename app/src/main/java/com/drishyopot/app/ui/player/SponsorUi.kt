package com.drishyopot.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drishyopot.app.R
import com.drishyopot.app.sponsorblock.PendingSkip
import com.drishyopot.app.sponsorblock.SbStatus
import com.drishyopot.app.sponsorblock.SponsorSegment

/** Chip shown while a skip is counting down. */
@Composable
fun SkipChip(pending: PendingSkip, onSkip: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val seconds = ((pending.remainingMs + 999) / 1000).toInt().coerceAtLeast(1)
    val category = stringResource(pending.segment.category.label)
    Row(
        modifier.background(Color.Black.copy(alpha = 0.78f), MaterialTheme.shapes.large).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(pending.segment.category.color)))
        Text(
            stringResource(if (pending.segment.isPoi) R.string.sb_jumping_in else R.string.sb_skipping_in, category, seconds),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        TextButton(onClick = onSkip) { Text(stringResource(R.string.sb_skip_now)) }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.sb_cancel)) }
    }
}

@Composable
fun SegmentsDialog(
    segments: List<SponsorSegment>,
    status: SbStatus,
    onJumpToStart: (SponsorSegment) -> Unit,
    onSkip: (SponsorSegment) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sb_segments)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()) {
                if (segments.isEmpty()) {
                    Text(
                        stringResource(
                            when (status) {
                                SbStatus.NO_ID -> R.string.sb_status_no_id
                                SbStatus.OFF -> R.string.sb_status_off
                                SbStatus.LOADING -> R.string.sb_status_loading
                                SbStatus.ERROR -> R.string.sb_status_error
                                else -> R.string.sb_no_segments
                            },
                        ),
                    )
                    if (status == SbStatus.ERROR) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                segments.forEach { seg ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onJumpToStart(seg); onDismiss() }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(seg.category.color)))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(stringResource(seg.category.label), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (seg.isPoi) formatTime(seg.startMs) else "${formatTime(seg.startMs)} – ${formatTime(seg.endMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onSkip(seg); onDismiss() }) {
                            Icon(Icons.Filled.SkipNext, stringResource(R.string.sb_skip_segment))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
