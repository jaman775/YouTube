package com.drishyopot.app.player

import kotlin.math.abs

/** Pure gesture helpers so they can be unit-tested. */
object GestureMath {
    enum class Mode { NONE, SEEK, BRIGHTNESS, VOLUME, FULLSCREEN }

    const val SEEK_SPAN_MS = 90_000L
    const val FULLSCREEN_THRESHOLD_FRACTION = 0.12f
    private const val SLOP = 12f

    /**
     * Horizontal drags seek. Vertical drags depend on where they started:
     * left 35% brightness, right 35% volume, middle 30% fullscreen toggle.
     */
    fun classify(dx: Float, dy: Float, startX: Float, width: Float): Mode {
        if (abs(dx) < SLOP && abs(dy) < SLOP) return Mode.NONE
        if (abs(dx) > abs(dy)) return Mode.SEEK
        val f = if (width > 0f) startX / width else 0.5f
        return when {
            f < 0.35f -> Mode.BRIGHTNESS
            f > 0.65f -> Mode.VOLUME
            else -> Mode.FULLSCREEN
        }
    }

    fun seekTarget(startMs: Long, dx: Float, width: Float, durationMs: Long): Long {
        if (width <= 0f) return startMs
        val delta = (dx / width * SEEK_SPAN_MS).toLong()
        val upper = if (durationMs > 0) durationMs else Long.MAX_VALUE
        return (startMs + delta).coerceIn(0L, upper)
    }

    /** Up = negative dy. Returns the new 0..1 level after a drag step. */
    fun adjustLevel(current: Float, dy: Float, height: Float): Float {
        if (height <= 0f) return current
        return (current - dy / height * 1.2f).coerceIn(0f, 1f)
    }

    /** +1 enter fullscreen, -1 exit, 0 nothing. */
    fun fullscreenIntent(totalDy: Float, height: Float): Int = when {
        height <= 0f -> 0
        totalDy < -height * FULLSCREEN_THRESHOLD_FRACTION -> 1
        totalDy > height * FULLSCREEN_THRESHOLD_FRACTION -> -1
        else -> 0
    }

    fun doubleTapZone(x: Float, width: Float): Int = when {
        x < width / 3f -> -1
        x > width * 2f / 3f -> 1
        else -> 0
    }
}
