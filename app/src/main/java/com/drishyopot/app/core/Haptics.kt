package com.drishyopot.app.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object Haptics {
    /** Short tick whose intensity follows [strength] (0 = off, 5 = max). */
    fun tick(context: Context, strength: Int) {
        if (strength <= 0) return
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude = (strength * 255 / 5).coerceIn(1, 255)
            vibrator.vibrate(VibrationEffect.createOneShot(25L, amplitude))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(25L)
        }
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
