package com.drishyopot.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.drishyopot.app.MainActivity
import com.drishyopot.app.R

class DownloadNotifications(private val context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_PROGRESS, context.getString(R.string.dl_channel_progress), NotificationManager.IMPORTANCE_LOW),
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, context.getString(R.string.dl_channel_done), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    private fun openDownloads(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).putExtra(EXTRA_ROUTE, "downloads").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun serviceAction(action: String, code: Int): PendingIntent = PendingIntent.getService(
        context, code, Intent(context, DownloadService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The ongoing notification summarising every active download. */
    fun progress(active: List<DownloadTask>): Notification {
        val total = active.sumOf { it.totalBytes }
        val done = active.sumOf { it.downloadedBytes }
        val percent = if (total > 0) (done * 100 / total).toInt() else 0
        val waiting = active.count { it.status == DlStatus.WAITING_NETWORK }
        val text = when {
            active.size == waiting -> context.getString(R.string.dl_notif_waiting)
            active.any { it.status == DlStatus.MERGING } -> context.getString(R.string.dl_notif_merging)
            else -> "$percent% · ${DownloadOptions.formatSpeed(active.sumOf { it.speedBps })}"
        }
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.resources.getQuantityString(R.plurals.dl_notif_title, active.size, active.size))
            .setContentText(text)
            .setProgress(100, percent, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openDownloads())
            .addAction(0, context.getString(R.string.dl_pause_all), serviceAction(DownloadService.ACTION_PAUSE_ALL, 1))
            .addAction(0, context.getString(R.string.dl_cancel_all), serviceAction(DownloadService.ACTION_CANCEL_ALL, 2))
            .build()
    }

    fun completed(task: DownloadTask) {
        notify(task, android.R.drawable.stat_sys_download_done, context.getString(R.string.dl_notif_done), task.outputName ?: task.request.title)
    }

    fun failed(task: DownloadTask, reason: String) {
        notify(task, android.R.drawable.stat_notify_error, context.getString(R.string.dl_notif_failed, task.request.title), reason)
    }

    private fun notify(task: DownloadTask, icon: Int, title: String, text: String) {
        val n = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(icon).setContentTitle(title).setContentText(text)
            .setAutoCancel(true).setContentIntent(openDownloads()).build()
        try { manager.notify(task.id.hashCode(), n) } catch (_: SecurityException) { }
    }

    fun cancel(task: DownloadTask) = manager.cancel(task.id.hashCode())

    companion object {
        const val CHANNEL_PROGRESS = "downloads_progress"
        const val CHANNEL_DONE = "downloads_done"
        const val EXTRA_ROUTE = "route"
        const val NOTIFICATION_ID = 4101
    }
}
