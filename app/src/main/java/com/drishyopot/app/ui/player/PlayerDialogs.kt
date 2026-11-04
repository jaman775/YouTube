@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.drishyopot.app.ui.player

import android.graphics.Color as AColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drishyopot.app.R
import com.drishyopot.app.player.SubtitleStyle

data class ChoiceItem(val label: String, val selected: Boolean, val onClick: () -> Unit)

@Composable
fun ChoiceDialog(title: String, items: List<ChoiceItem>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                items.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().clickable { item.onClick(); onDismiss() }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = item.selected, onClick = { item.onClick(); onDismiss() })
                        Text(item.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/** Key/value rows with Copy / Copy all. */
@Composable
fun InfoDialog(rows: List<Pair<String, String>>, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.info)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                rows.forEach { (k, v) ->
                    Column(
                        Modifier.fillMaxWidth().clickable { clipboard.setText(AnnotatedString(v)) }.padding(vertical = 4.dp),
                    ) {
                        Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(rows.joinToString("\n") { "${it.first}: ${it.second}" }))
            }) { Text(stringResource(R.string.copy_all)) }
        },
    )
}

private val swatches = listOf(
    AColor.WHITE, AColor.YELLOW, AColor.CYAN, AColor.GREEN, AColor.rgb(255, 165, 0), AColor.rgb(255, 105, 180),
)

@Composable
fun SubtitleStyleDialog(initial: SubtitleStyle, onChange: (SubtitleStyle) -> Unit, onDismiss: () -> Unit) {
    var style by remember { mutableStateOf(initial) }
    var hex by remember { mutableStateOf(String.format("%06X", initial.color and 0xFFFFFF)) }
    var boxHeight by remember { mutableStateOf(1f) }
    fun update(s: SubtitleStyle) { style = s; onChange(s) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subtitle_style)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Preview: drag to reposition.
                Box(
                    Modifier.fillMaxWidth().height(110.dp).background(Color(0xFF334155))
                        .onSizeChanged { boxHeight = it.height.toFloat().coerceAtLeast(1f) }
                        .pointerInput(Unit) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                update(style.copy(offsetY = (style.offsetY + drag.y / boxHeight).coerceIn(-0.4f, 0.4f)))
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.subtitle_style_preview),
                        color = Color(style.color),
                        fontSize = (style.textSize * boxHeight * 3.2f).coerceIn(10f, 40f).sp,
                        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .padding(top = (style.offsetY * boxHeight).coerceAtLeast(0f).dp, bottom = (-style.offsetY * boxHeight).coerceAtLeast(0f).dp)
                            .background(Color(style.backgroundColor()))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(stringResource(R.string.subtitle_style_drag_hint), style = MaterialTheme.typography.bodySmall)

                Text(stringResource(R.string.subtitle_style_text_size))
                Slider(
                    value = style.textSize,
                    onValueChange = { update(style.copy(textSize = it)) },
                    valueRange = SubtitleStyle.MIN_SIZE..SubtitleStyle.MAX_SIZE,
                )

                Text(stringResource(R.string.subtitle_style_background))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        SubtitleStyle.BG_NONE to R.string.subtitle_background_none,
                        SubtitleStyle.BG_SEMI to R.string.subtitle_background_semi,
                        SubtitleStyle.BG_SOLID to R.string.subtitle_background_opaque,
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = style.background == mode,
                            onClick = { update(style.copy(background = mode)) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }

                Text(stringResource(R.string.subtitle_style_color))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    swatches.forEach { c ->
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                                .clickable {
                                    hex = String.format("%06X", c and 0xFFFFFF)
                                    update(style.copy(color = c))
                                },
                        )
                    }
                }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { raw ->
                        val clean = raw.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                        hex = clean
                        if (clean.length == 6) update(style.copy(color = AColor.parseColor("#$clean")))
                    },
                    label = { Text(stringResource(R.string.subtitle_style_hex)) },
                    singleLine = true,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.subtitle_style_bold), Modifier.weight(1f))
                    Switch(checked = style.bold, onCheckedChange = { update(style.copy(bold = it)) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        dismissButton = {
            TextButton(onClick = {
                val d = SubtitleStyle()
                hex = String.format("%06X", d.color and 0xFFFFFF)
                update(d)
            }) { Text(stringResource(R.string.reset)) }
        },
    )
}
