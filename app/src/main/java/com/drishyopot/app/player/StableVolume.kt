package com.drishyopot.app.player

import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import com.drishyopot.app.diagnostics.DiagnosticLog

/**
 * "Stable volume": evens out loud and quiet parts by running the player's audio session through a
 * limiter (Android 9+) or, on older versions, a small loudness boost. Needs no permission.
 */
class StableVolume {
    private var effect: AudioEffect? = null
    private var sessionId = 0

    /** True when the effect could be created on this device. */
    fun apply(enabled: Boolean, audioSessionId: Int): Boolean {
        if (!enabled || audioSessionId == 0) { release(); return true }
        if (effect != null && sessionId == audioSessionId) return true
        release()
        return try {
            effect = if (Build.VERSION.SDK_INT >= 28) createLimiter(audioSessionId) else createBoost(audioSessionId)
            sessionId = audioSessionId
            effect?.setEnabled(true)
            true
        } catch (e: Throwable) {
            DiagnosticLog.log("StableVolume", "Could not create audio effect", e)
            release()
            false
        }
    }

    private fun createLimiter(session: Int): AudioEffect {
        val channels = 2
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION, channels,
            /* preEqInUse = */ false, 0,
            /* mbcInUse = */ false, 0,
            /* postEqInUse = */ false, 0,
            /* limiterInUse = */ true,
        ).build()
        // Everything above -14 dB is squashed, then the whole signal is raised 5 dB: loud scenes stop
        // jumping out and quiet dialogue becomes easier to hear.
        val limiter = DynamicsProcessing.Limiter(
            /* inUse = */ true, /* enabled = */ true, /* linkGroup = */ 0,
            /* attackTime = */ 2f, /* releaseTime = */ 120f,
            /* ratio = */ 8f, /* threshold = */ -14f, /* postGain = */ 5f,
        )
        for (ch in 0 until channels) config.setLimiterByChannelIndex(ch, limiter)
        return DynamicsProcessing(0, session, config)
    }

    private fun createBoost(session: Int): AudioEffect =
        LoudnessEnhancer(session).also { it.setTargetGain(600) } // +6 dB, in millibels

    fun release() {
        try { effect?.release() } catch (_: Throwable) { }
        effect = null
        sessionId = 0
    }
}
