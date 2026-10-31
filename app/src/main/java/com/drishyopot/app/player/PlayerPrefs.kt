package com.drishyopot.app.player

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

data class SubtitleStyle(
    val textSize: Float = 0.0533f,     // fraction of view height
    val background: Int = BG_SEMI,     // BG_NONE / BG_SEMI / BG_SOLID
    val color: Int = Color.WHITE,
    val bold: Boolean = false,
    val offsetY: Float = 0f,           // fraction of view height, + = lower
) {
    companion object {
        const val BG_NONE = 0
        const val BG_SEMI = 1
        const val BG_SOLID = 2
        const val MIN_SIZE = 0.03f
        const val MAX_SIZE = 0.12f
    }

    fun backgroundColor(): Int = when (background) {
        BG_NONE -> Color.TRANSPARENT
        BG_SOLID -> Color.BLACK
        else -> Color.argb(160, 0, 0, 0)
    }
}

enum class EndMode { NEXT, LOOP, PAUSE, SHUFFLE }

/** Per-video position plus remembered player choices. */
class PlayerPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("drishyopot_player", Context.MODE_PRIVATE)

    fun position(key: String): Long = prefs.getLong("pos_$key", 0L)
    fun savePosition(key: String, ms: Long) { prefs.edit().putLong("pos_$key", ms).apply() }
    fun clearPosition(key: String) { prefs.edit().remove("pos_$key").apply() }

    var speed: Float
        get() = prefs.getFloat("speed", 1f)
        set(v) { prefs.edit().putFloat("speed", v).apply() }

    /** PlayerView resize-mode constant. */
    var resizeMode: Int
        get() = prefs.getInt("resize", 0)
        set(v) { prefs.edit().putInt("resize", v).apply() }

    /** 0 = auto, otherwise the pinned video height. */
    var qualityHeight: Int
        get() = prefs.getInt("quality", 0)
        set(v) { prefs.edit().putInt("quality", v).apply() }

    var subtitleStyle: SubtitleStyle
        get() = SubtitleStyle(
            textSize = prefs.getFloat("sub_size", SubtitleStyle().textSize),
            background = prefs.getInt("sub_bg", SubtitleStyle.BG_SEMI),
            color = prefs.getInt("sub_color", Color.WHITE),
            bold = prefs.getBoolean("sub_bold", false),
            offsetY = prefs.getFloat("sub_offset", 0f),
        )
        set(v) {
            prefs.edit()
                .putFloat("sub_size", v.textSize).putInt("sub_bg", v.background)
                .putInt("sub_color", v.color).putBoolean("sub_bold", v.bold)
                .putFloat("sub_offset", v.offsetY).apply()
        }

    var endMode: EndMode
        get() = EndMode.entries.getOrElse(prefs.getInt("end_mode", 0)) { EndMode.NEXT }
        set(v) { prefs.edit().putInt("end_mode", v.ordinal).apply() }

    var autoplay: Boolean
        get() = prefs.getBoolean("autoplay", true)
        set(v) { prefs.edit().putBoolean("autoplay", v).apply() }

    var audioOnly: Boolean
        get() = prefs.getBoolean("audio_only", false)
        set(v) { prefs.edit().putBoolean("audio_only", v).apply() }

    /** Mini player window: position as 0..1 fractions of free space, size preset 0..2. */
    var miniX: Float
        get() = prefs.getFloat("mini_x", 1f)
        set(v) { prefs.edit().putFloat("mini_x", v).apply() }
    var miniY: Float
        get() = prefs.getFloat("mini_y", 0.85f)
        set(v) { prefs.edit().putFloat("mini_y", v).apply() }
    var miniSize: Int
        get() = prefs.getInt("mini_size", 1)
        set(v) { prefs.edit().putInt("mini_size", v.coerceIn(0, 2)).apply() }
}
