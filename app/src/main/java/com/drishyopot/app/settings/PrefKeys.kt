package com.drishyopot.app.settings

/** Every persisted preference key and its default value. */
object PrefKeys {
    // Interface
    const val HAPTIC_STRENGTH = "haptic_strength"
    const val ENABLE_DISPLAY_DISLIKES = "enable_display_dislikes"
    const val ENABLE_SHOW_LIKES = "enable_show_likes"
    const val ENABLE_HIDE_SHORTS = "enable_hide_shorts"

    // Playback
    const val REMEMBER_LAST_POSITION = "remember_last_position"
    const val REMEMBER_QUALITY = "remember_quality"
    const val REMEMBER_PLAYBACK_SPEED = "remember_playback_speed"
    const val REMEMBER_RESIZE_MODE = "remember_resize_mode"
    const val USE_ORIGINAL_TITLE = "use_original_title"

    // Gestures (windowed = embedded player)
    const val GESTURE_TAP_WINDOWED = "gesture_tap_windowed"
    const val GESTURE_TAP_FULLSCREEN = "gesture_tap_fullscreen"
    const val GESTURE_DOUBLE_TAP_WINDOWED = "gesture_double_tap_windowed"
    const val GESTURE_DOUBLE_TAP_FULLSCREEN = "gesture_double_tap_fullscreen"
    const val GESTURE_LONG_PRESS_WINDOWED = "gesture_long_press_windowed"
    const val GESTURE_LONG_PRESS_FULLSCREEN = "gesture_long_press_fullscreen"
    const val GESTURE_BRIGHTNESS_WINDOWED = "gesture_brightness_windowed"
    const val GESTURE_BRIGHTNESS_FULLSCREEN = "gesture_brightness_fullscreen"
    const val GESTURE_VOLUME_WINDOWED = "gesture_volume_windowed"
    const val GESTURE_VOLUME_FULLSCREEN = "gesture_volume_fullscreen"
    const val GESTURE_SEEK_WINDOWED = "gesture_seek_windowed"
    const val GESTURE_SEEK_FULLSCREEN = "gesture_seek_fullscreen"
    const val GESTURE_FULLSCREEN_WINDOWED = "gesture_fullscreen_windowed"
    const val GESTURE_FULLSCREEN_FULLSCREEN = "gesture_fullscreen_fullscreen"

    // Background / mini player
    const val ENABLE_BACKGROUND_PLAY = "enable_background_play"
    const val ENABLE_PIP = "enable_pip"
    const val ENABLE_IN_APP_MINI_PLAYER = "enable_in_app_mini_player"

    // SponsorBlock
    const val SKIP_SPONSORS = "skip_sponsors"
    const val SKIP_SELF_PROMO = "skip_self_promo"
    const val SKIP_INTERACTION = "skip_interaction"
    const val SKIP_INTRO = "skip_intro"
    const val SKIP_OUTRO = "skip_outro"
    const val SKIP_PREVIEW = "skip_preview"
    const val SKIP_MUSIC_OFFTOPIC = "skip_music_offtopic"
    const val SKIP_FILLER = "skip_filler"
    const val SKIP_POI_HIGHLIGHT = "skip_poi_highlight"
    const val SPONSOR_COUNTDOWN = "sponsor_countdown"

    // Downloads
    const val DOWNLOAD_WIFI_ONLY = "download_wifi_only"
    const val DOWNLOAD_CONNECTIONS = "download_connections"

    val BOOLEAN_DEFAULTS: Map<String, Boolean> = linkedMapOf(
        ENABLE_DISPLAY_DISLIKES to true,
        ENABLE_SHOW_LIKES to true,
        ENABLE_HIDE_SHORTS to false,
        REMEMBER_LAST_POSITION to true,
        REMEMBER_QUALITY to true,
        REMEMBER_PLAYBACK_SPEED to false,
        REMEMBER_RESIZE_MODE to false,
        USE_ORIGINAL_TITLE to false,
        GESTURE_TAP_WINDOWED to true,
        GESTURE_TAP_FULLSCREEN to true,
        GESTURE_DOUBLE_TAP_WINDOWED to true,
        GESTURE_DOUBLE_TAP_FULLSCREEN to true,
        GESTURE_LONG_PRESS_WINDOWED to true,
        GESTURE_LONG_PRESS_FULLSCREEN to true,
        GESTURE_BRIGHTNESS_WINDOWED to true,
        GESTURE_BRIGHTNESS_FULLSCREEN to true,
        GESTURE_VOLUME_WINDOWED to true,
        GESTURE_VOLUME_FULLSCREEN to true,
        GESTURE_SEEK_WINDOWED to true,
        GESTURE_SEEK_FULLSCREEN to true,
        GESTURE_FULLSCREEN_WINDOWED to true,
        GESTURE_FULLSCREEN_FULLSCREEN to true,
        ENABLE_BACKGROUND_PLAY to true,
        ENABLE_PIP to true,
        ENABLE_IN_APP_MINI_PLAYER to true,
        SKIP_SPONSORS to true,
        SKIP_SELF_PROMO to true,
        SKIP_INTERACTION to true,
        SKIP_INTRO to true,
        SKIP_OUTRO to true,
        SKIP_PREVIEW to true,
        SKIP_MUSIC_OFFTOPIC to true,
        SKIP_FILLER to true,
        SKIP_POI_HIGHLIGHT to true,
        SPONSOR_COUNTDOWN to true,
        DOWNLOAD_WIFI_ONLY to false,
    )

    data class IntSpec(val min: Int, val max: Int, val default: Int)

    val INT_SPECS: Map<String, IntSpec> = linkedMapOf(
        HAPTIC_STRENGTH to IntSpec(0, 5, 3),
        DOWNLOAD_CONNECTIONS to IntSpec(1, 8, 4),
    )
}
