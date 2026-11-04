@file:OptIn(ExperimentalMaterial3Api::class)

package com.drishyopot.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drishyopot.app.R
import com.drishyopot.app.ui.components.RemoteImage
import com.drishyopot.app.yt.YtChapter

/** "In this video": the video's own chapters. Tapping one jumps there. */
@Composable
fun ChaptersSheet(chapters: List<YtChapter>, positionMs: Long, onSeek: (Long) -> Unit, onDismiss: () -> Unit) {
    val current = chapters.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 440.dp,
        containerColor = Color(0xFF212121),
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF606060)) },
    ) {
        Text(stringResource(R.string.yt_in_this_video), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.75f).dp)) {
            itemsIndexed(chapters) { i, c ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (i == current) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onSeek(c.startMs); onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RemoteImage(c.preview, Modifier.width(96.dp).size(width = 96.dp, height = 54.dp).clip(RoundedCornerShape(6.dp)))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(c.title, fontSize = 15.sp, maxLines = 2)
                        Text(formatTime(c.startMs), fontSize = 13.sp, color = Color(0xFFAAAAAA), modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}
