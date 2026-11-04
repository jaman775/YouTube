package com.drishyopot.app.core

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import androidx.media3.common.Player
import com.drishyopot.app.player.PlaybackHub

object PipSupport {
    const val ACTION_PREVIOUS = "com.drishyopot.app.PIP_PREVIOUS"
    const val ACTION_PLAY_PAUSE = "com.drishyopot.app.PIP_PLAY_PAUSE"
    const val ACTION_NEXT = "com.drishyopot.app.PIP_NEXT"

    fun isSupported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    @RequiresApi(Build.VERSION_CODES.O)
    fun params(context: Context, hub: PlaybackHub, autoEnter: Boolean): PictureInPictureParams {
        val size = hub.player.videoSize
        val ratio = if (size.width > 0 && size.height > 0) {
            Rational(size.width, size.height)
        } else Rational(16, 9)
        // PiP only accepts ratios between ~0.418 and 2.39.
        val value = ratio.toDouble()
        val safe = when {
            value > 2.39 -> Rational(239, 100)
            value < 0.4184 -> Rational(100, 239)
            else -> ratio
        }
        val playing = hub.player.isPlaying
        val actions = listOf(
            action(context, ACTION_PREVIOUS, android.R.drawable.ic_media_previous, "Previous", 1, hub.player.hasPreviousMediaItem()),
            action(
                context, ACTION_PLAY_PAUSE,
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pause" else "Play", 2, true,
            ),
            action(context, ACTION_NEXT, android.R.drawable.ic_media_next, "Next", 3, hub.player.hasNextMediaItem()),
        )
        val builder = PictureInPictureParams.Builder().setAspectRatio(safe).setActions(actions)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter).setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun action(context: Context, name: String, icon: Int, label: String, code: Int, enabled: Boolean): RemoteAction {
        val intent = PendingIntent.getBroadcast(
            context, code, Intent(name).setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE,
        )
        return RemoteAction(Icon.createWithResource(context, icon), label, label, intent).also { it.isEnabled = enabled }
    }

    fun handleAction(hub: PlaybackHub, action: String?) {
        val p = hub.player
        when (action) {
            ACTION_PREVIOUS -> p.seekToPreviousMediaItem()
            ACTION_NEXT -> p.seekToNextMediaItem()
            ACTION_PLAY_PAUSE -> {
                if (p.playbackState == Player.STATE_IDLE) p.prepare()
                if (p.isPlaying) p.pause() else p.play()
            }
        }
    }
}
