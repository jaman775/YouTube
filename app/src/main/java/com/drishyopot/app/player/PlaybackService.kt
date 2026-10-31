package com.drishyopot.app.player

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.drishyopot.app.DrishyopotApp

/**
 * Keeps playback alive in the background and shows the media notification.
 * The player and session are owned by [PlaybackHub]; this service only hosts them.
 */
class PlaybackService : MediaSessionService() {
    private val hub: PlaybackHub get() = (application as DrishyopotApp).hub

    override fun onCreate() {
        super.onCreate()
        addSession(hub.ensureSession())
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = hub.session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away stops playback unless it is actively playing in the background.
        if (!hub.player.playWhenReady) stopSelf()
    }
}
