package com.drishyopot.app.player

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player

/** Pauses playback after a delay, or when the current video ends. */
class SleepTimer(private val player: Player, private val onFired: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    /** Uptime (ms) when the timer fires, or 0 when no timed sleep is set. */
    var endsAtUptimeMs by mutableStateOf(0L)
        private set
    var atEndOfVideo by mutableStateOf(false)
        private set

    val active: Boolean get() = endsAtUptimeMs > 0L || atEndOfVideo

    private val fire = Runnable {
        clear()
        player.pause()
        onFired()
    }

    fun setMinutes(minutes: Int) {
        clear()
        if (minutes <= 0) return
        endsAtUptimeMs = SystemClock.uptimeMillis() + minutes * 60_000L
        main.postAtTime(fire, endsAtUptimeMs)
    }

    fun setEndOfVideo() {
        clear()
        atEndOfVideo = true
    }

    fun clear() {
        main.removeCallbacks(fire)
        endsAtUptimeMs = 0L
        atEndOfVideo = false
    }

    fun remainingMs(): Long = (endsAtUptimeMs - SystemClock.uptimeMillis()).coerceAtLeast(0L)

    /** Called by the hub when an item finished playing (including a loop restart). */
    fun onItemFinished() {
        if (!atEndOfVideo) return
        clear()
        player.pause()
        onFired()
    }
}
