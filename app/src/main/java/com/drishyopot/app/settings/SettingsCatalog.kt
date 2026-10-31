package com.drishyopot.app.settings

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.drishyopot.app.R

/** Tree describing the settings screens. */
sealed class SettingNode {
    abstract val id: String
    abstract val title: Int

    data class Group(
        override val id: String,
        @StringRes override val title: Int,
        val icon: ImageVector? = null,
        val children: List<SettingNode>,
    ) : SettingNode()

    data class Toggle(
        val key: String,
        @StringRes override val title: Int,
        @StringRes val summary: Int = 0,
    ) : SettingNode() {
        override val id: String get() = key
    }

    data class Slider(
        val key: String,
        @StringRes override val title: Int,
        val zeroLabel: Int = 0,
    ) : SettingNode() {
        override val id: String get() = key
    }

    data class Nav(
        override val id: String,
        @StringRes override val title: Int,
        val icon: ImageVector? = null,
    ) : SettingNode()
}

object SettingsCatalog {
    const val NAV_DATA = "data"
    const val NAV_DOWNLOADS = "downloads"

    fun build(pipSupported: Boolean): List<SettingNode> = listOf(
        SettingNode.Group(
            id = "interface", title = R.string.interface_category, icon = Icons.Filled.Tune,
            children = listOf(
                SettingNode.Slider(PrefKeys.HAPTIC_STRENGTH, R.string.haptic_strength, R.string.haptics_off),
                SettingNode.Toggle(PrefKeys.ENABLE_DISPLAY_DISLIKES, R.string.display_dislikes, R.string.display_dislikes_summary),
                SettingNode.Toggle(PrefKeys.ENABLE_SHOW_LIKES, R.string.show_likes, R.string.display_dislikes_summary),
                SettingNode.Toggle(PrefKeys.ENABLE_HIDE_SHORTS, R.string.hide_shorts),
            ),
        ),
        SettingNode.Group(
            id = "player", title = R.string.player, icon = Icons.Filled.PlayArrow,
            children = listOf(
                SettingNode.Toggle(PrefKeys.REMEMBER_LAST_POSITION, R.string.remember_last_position),
                SettingNode.Toggle(PrefKeys.REMEMBER_QUALITY, R.string.remember_quality),
                SettingNode.Toggle(PrefKeys.REMEMBER_PLAYBACK_SPEED, R.string.remember_playback_speed),
                SettingNode.Toggle(PrefKeys.REMEMBER_RESIZE_MODE, R.string.remember_resize_mode),
                SettingNode.Toggle(PrefKeys.USE_ORIGINAL_TITLE, R.string.use_original_title),
            ),
        ),
        SettingNode.Group(
            id = "gesture", title = R.string.gesture, icon = Icons.Filled.Gesture,
            children = listOf(
                SettingNode.Group(
                    id = "gesture_windowed", title = R.string.enable_in_embedded,
                    children = listOf(
                        SettingNode.Toggle(PrefKeys.GESTURE_TAP_WINDOWED, R.string.gesture_single_tap),
                        SettingNode.Toggle(PrefKeys.GESTURE_DOUBLE_TAP_WINDOWED, R.string.gesture_double_tap),
                        SettingNode.Toggle(PrefKeys.GESTURE_LONG_PRESS_WINDOWED, R.string.gesture_long_press_speed),
                        SettingNode.Toggle(PrefKeys.GESTURE_BRIGHTNESS_WINDOWED, R.string.brightness),
                        SettingNode.Toggle(PrefKeys.GESTURE_VOLUME_WINDOWED, R.string.volume),
                        SettingNode.Toggle(PrefKeys.GESTURE_SEEK_WINDOWED, R.string.gesture_seek),
                        SettingNode.Toggle(PrefKeys.GESTURE_FULLSCREEN_WINDOWED, R.string.gesture_fullscreen_swipe),
                    ),
                ),
                SettingNode.Group(
                    id = "gesture_fullscreen", title = R.string.enable_in_fullscreen,
                    children = listOf(
                        SettingNode.Toggle(PrefKeys.GESTURE_TAP_FULLSCREEN, R.string.gesture_single_tap),
                        SettingNode.Toggle(PrefKeys.GESTURE_DOUBLE_TAP_FULLSCREEN, R.string.gesture_double_tap),
                        SettingNode.Toggle(PrefKeys.GESTURE_LONG_PRESS_FULLSCREEN, R.string.gesture_long_press_speed),
                        SettingNode.Toggle(PrefKeys.GESTURE_BRIGHTNESS_FULLSCREEN, R.string.brightness),
                        SettingNode.Toggle(PrefKeys.GESTURE_VOLUME_FULLSCREEN, R.string.volume),
                        SettingNode.Toggle(PrefKeys.GESTURE_SEEK_FULLSCREEN, R.string.gesture_seek),
                        SettingNode.Toggle(PrefKeys.GESTURE_FULLSCREEN_FULLSCREEN, R.string.gesture_fullscreen_swipe),
                    ),
                ),
            ),
        ),
        SettingNode.Group(
            id = "background", title = R.string.background_mini_player, icon = Icons.Filled.PictureInPictureAlt,
            children = listOfNotNull(
                if (pipSupported) SettingNode.Toggle(PrefKeys.ENABLE_PIP, R.string.pip) else null,
                SettingNode.Toggle(PrefKeys.ENABLE_IN_APP_MINI_PLAYER, R.string.in_app_mini_player),
                SettingNode.Toggle(PrefKeys.ENABLE_BACKGROUND_PLAY, R.string.background_play),
            ),
        ),
        SettingNode.Group(
            id = "sponsorblock", title = R.string.sponsorblock, icon = Icons.Filled.Block,
            children = listOf(
                SettingNode.Toggle(PrefKeys.SKIP_SPONSORS, R.string.skip_sponsors),
                SettingNode.Toggle(PrefKeys.SKIP_SELF_PROMO, R.string.skip_selfpromo),
                SettingNode.Toggle(PrefKeys.SKIP_INTERACTION, R.string.skip_interaction),
                SettingNode.Toggle(PrefKeys.SKIP_INTRO, R.string.skip_intro),
                SettingNode.Toggle(PrefKeys.SKIP_OUTRO, R.string.skip_outro),
                SettingNode.Toggle(PrefKeys.SKIP_PREVIEW, R.string.skip_preview),
                SettingNode.Toggle(PrefKeys.SKIP_MUSIC_OFFTOPIC, R.string.skip_music_offtopic),
                SettingNode.Toggle(PrefKeys.SKIP_FILLER, R.string.skip_filler),
                SettingNode.Toggle(PrefKeys.SKIP_POI_HIGHLIGHT, R.string.skip_highlight),
                SettingNode.Toggle(PrefKeys.SPONSOR_COUNTDOWN, R.string.sponsor_countdown),
            ),
        ),
        SettingNode.Nav(NAV_DATA, R.string.settings_backup_restore, Icons.Filled.SettingsBackupRestore),
        SettingNode.Nav(NAV_DOWNLOADS, R.string.downloads, Icons.Filled.Download),
    )

    /** Walks [ids] down the tree; returns the group they name, or null if invalid. */
    fun resolve(ids: List<String>, catalog: List<SettingNode>): SettingNode.Group? {
        var children = catalog
        var current: SettingNode.Group? = null
        for (id in ids) {
            val group = children.firstOrNull { it.id == id } as? SettingNode.Group ?: return null
            current = group
            children = group.children
        }
        return current
    }
}
