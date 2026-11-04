@file:OptIn(ExperimentalMaterial3Api::class)

package com.drishyopot.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drishyopot.app.R
import kotlinx.coroutines.delay

data class SheetOption(val label: String, val selected: Boolean, val onSelect: () -> Unit)

/** Everything the YouTube-style settings sheet shows. Built by the player screen. */
class PlayerSettingsModel(
    val qualityValue: String, val qualityOptions: List<SheetOption>,
    val speedValue: String, val speedOptions: List<SheetOption>,
    val captionsValue: String, val captionsOptions: List<SheetOption>,
    val audioValue: String, val audioOptions: List<SheetOption>,
    val loop: Boolean, val ambient: Boolean, val stableVolume: Boolean,
    val endModeValue: String, val endModeOptions: List<SheetOption>,
    val sleepAtEnd: Boolean, val sleepRemainingMs: () -> Long,
)

private enum class Page { MAIN, QUALITY, SPEED, CAPTIONS, AUDIO, MORE, SLEEP, END_MODE, HELP }

private val SheetBg = Color(0xFF212121)
private val TextMain = Color.White
private val TextDim = Color(0xFFAAAAAA)

/**
 * The player's settings sheet, laid out like YouTube's: Quality, Playback speed, Captions, Audio track,
 * Lock screen and "More" (Loop video, Ambient mode, Stable volume, Sleep timer, Help & feedback).
 * Each entry opens a sub-page inside the same sheet with a back arrow.
 */
@Composable
fun PlayerSettingsSheet(
    model: PlayerSettingsModel,
    onLoop: (Boolean) -> Unit,
    onAmbient: (Boolean) -> Unit,
    onStableVolume: (Boolean) -> Unit,
    onSleepMinutes: (Int) -> Unit,
    onSleepEnd: () -> Unit,
    onSleepOff: () -> Unit,
    onLock: () -> Unit,
    onCaptionStyle: () -> Unit,
    onHelpCenter: () -> Unit,
    onPlaybackInfo: () -> Unit,
    onQueue: () -> Unit,
    onResize: () -> Unit,
    onSegments: () -> Unit,
    onClosePlayer: () -> Unit,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf(Page.MAIN) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp

    val context = LocalContext.current
    fun sleepValue(): String {
        tick // re-read every second
        return when {
            model.sleepAtEnd -> context.getString(R.string.sleep_end_of_video)
            model.sleepRemainingMs() > 0 ->
                context.getString(R.string.sleep_minutes_left, ((model.sleepRemainingMs() + 59_999) / 60_000).toInt())
            else -> context.getString(R.string.sleep_off)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 440.dp,
        containerColor = SheetBg,
        contentColor = TextMain,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF606060)) },
    ) {
        Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
            when (page) {
                Page.MAIN -> {
                    SheetRow(R.string.quality, Icons.Outlined.Tune, model.qualityValue) { page = Page.QUALITY }
                    SheetRow(R.string.playback_speed, Icons.Outlined.Speed, model.speedValue) { page = Page.SPEED }
                    SheetRow(R.string.yt_captions, Icons.Outlined.ClosedCaption, model.captionsValue) { page = Page.CAPTIONS }
                    SheetRow(R.string.audio_track, Icons.Outlined.Audiotrack, model.audioValue) { page = Page.AUDIO }
                    SheetRow(R.string.lock_screen, Icons.Outlined.Lock, null) { onDismiss(); onLock() }
                    SheetRow(R.string.more_options, Icons.Outlined.MoreHoriz, null) { page = Page.MORE }
                }
                Page.QUALITY -> Options(R.string.quality, model.qualityOptions, { page = Page.MAIN }, onDismiss)
                Page.SPEED -> Options(R.string.playback_speed, model.speedOptions, { page = Page.MAIN }, onDismiss)
                Page.AUDIO -> Options(R.string.audio_track, model.audioOptions, { page = Page.MAIN }, onDismiss)
                Page.CAPTIONS -> Options(
                    R.string.yt_captions, model.captionsOptions, { page = Page.MAIN }, onDismiss,
                    footer = { SheetRow(R.string.subtitle_style, Icons.Outlined.ClosedCaption, null) { onDismiss(); onCaptionStyle() } },
                )
                Page.END_MODE -> Options(R.string.yt_when_video_ends, model.endModeOptions, { page = Page.MORE }, onDismiss)
                Page.MORE -> {
                    Header(R.string.more_options) { page = Page.MAIN }
                    SwitchRow(R.string.yt_loop_video, Icons.Outlined.Repeat, model.loop, onLoop)
                    SwitchRow(R.string.yt_ambient_mode, Icons.Outlined.BlurOn, model.ambient, onAmbient)
                    SwitchRow(R.string.yt_stable_volume, Icons.Outlined.GraphicEq, model.stableVolume, onStableVolume)
                    SheetRow(R.string.yt_sleep_timer, Icons.Outlined.Bedtime, sleepValue()) { page = Page.SLEEP }
                    SheetRow(R.string.yt_when_video_ends, Icons.AutoMirrored.Outlined.PlaylistPlay, model.endModeValue) { page = Page.END_MODE }
                    SheetRow(R.string.queue, Icons.AutoMirrored.Outlined.QueueMusic, null) { onDismiss(); onQueue() }
                    SheetRow(R.string.resize_mode, Icons.Outlined.AspectRatio, null) { onDismiss(); onResize() }
                    SheetRow(R.string.sb_segments, Icons.Outlined.Block, null) { onDismiss(); onSegments() }
                    SheetRow(R.string.yt_help_feedback, Icons.AutoMirrored.Outlined.HelpOutline, null) { page = Page.HELP }
                    SheetRow(R.string.close_player, Icons.Outlined.Close, null) { onDismiss(); onClosePlayer() }
                }
                Page.SLEEP -> {
                    val off = !model.sleepAtEnd && model.sleepRemainingMs() <= 0
                    val options = buildList {
                        add(SheetOption(stringResource(R.string.sleep_off), off) { onSleepOff() })
                        listOf(5, 10, 15, 30, 45, 60).forEach { m ->
                            add(SheetOption(stringResource(R.string.sleep_minutes, m), false) { onSleepMinutes(m) })
                        }
                        add(SheetOption(stringResource(R.string.sleep_end_of_video), model.sleepAtEnd) { onSleepEnd() })
                    }
                    Options(R.string.yt_sleep_timer, options, { page = Page.MORE }, onDismiss)
                }
                Page.HELP -> {
                    Header(R.string.yt_help_feedback) { page = Page.MORE }
                    SheetRow(R.string.yt_help_center, Icons.AutoMirrored.Outlined.HelpOutline, null) { onDismiss(); onHelpCenter() }
                    SheetRow(R.string.info, Icons.Outlined.Info, null) { onDismiss(); onPlaybackInfo() }
                }
            }
        }
    }
}

@Composable
private fun Header(title: Int, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back), tint = TextMain) }
        Text(stringResource(title), color = TextMain, fontSize = 16.sp)
    }
    HorizontalDivider(color = Color(0xFF3F3F3F))
}

@Composable
private fun Options(title: Int, options: List<SheetOption>, onBack: () -> Unit, onDismiss: () -> Unit, footer: @Composable () -> Unit = {}) {
    Header(title, onBack)
    options.forEach { o ->
        Row(
            Modifier.fillMaxWidth().height(48.dp).clickable { o.onSelect(); onDismiss() }.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(40.dp)) { if (o.selected) Icon(Icons.Filled.Check, null, tint = TextMain, modifier = Modifier.size(22.dp)) }
            Text(o.label, color = TextMain, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    footer()
}

@Composable
private fun SheetRow(title: Int, icon: ImageVector, value: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = TextMain, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Text(stringResource(title), color = TextMain, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = TextDim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
    }
}

@Composable
private fun SwitchRow(title: Int, icon: ImageVector, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clickable { onChange(!checked) }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = TextMain, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Text(stringResource(title), color = TextMain, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF212121), checkedTrackColor = Color.White,
                uncheckedThumbColor = TextDim, uncheckedTrackColor = Color(0xFF3F3F3F), uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}
