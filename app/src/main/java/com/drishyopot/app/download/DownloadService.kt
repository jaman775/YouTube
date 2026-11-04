package com.drishyopot.app.download

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.drishyopot.app.DrishyopotApp

/** Foreground service that keeps the process alive and shows progress while downloads run. */
class DownloadService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val center get() = (application as DrishyopotApp).downloads

    private val tick = object : Runnable {
        override fun run() {
            val active = center.tasks.filter { it.isActive }
            if (active.isEmpty()) {
                stopSelfCleanly()
                return
            }
            center.notifications.let {
                (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
                    .notify(DownloadNotifications.NOTIFICATION_ID, it.progress(active))
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_ALL -> center.pauseAll()
            ACTION_CANCEL_ALL -> center.cancelAll()
        }
        val active = center.tasks.filter { it.isActive }
        val notification = center.notifications.progress(active)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(DownloadNotifications.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(DownloadNotifications.NOTIFICATION_ID, notification)
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_NOT_STICKY
    }

    /** Android 15 limits data-sync services; pause so progress is kept and the user can resume. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        center.pauseAll()
        stopSelfCleanly()
    }

    @Suppress("DEPRECATION")
    private fun stopSelfCleanly() {
        handler.removeCallbacks(tick)
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_PAUSE_ALL = "com.drishyopot.app.DL_PAUSE_ALL"
        const val ACTION_CANCEL_ALL = "com.drishyopot.app.DL_CANCEL_ALL"
    }
}
