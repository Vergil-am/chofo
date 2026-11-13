package dev.dogetime.torrent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.dogetime.AndroidApp
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal actual object DownloadNotifier {
    private const val CHANNEL_ID_DOWNLOADS = "chofo_downloads"
    private const val CHANNEL_ID_COMPLETED = "chofo_downloads_completed"

    private data class ActiveDownload(
        val id: String,
        val title: String,
        val progress: Float,
        val downloadRate: Long,
        val peers: Int,
    )

    private val activeDownloads = ConcurrentHashMap<String, ActiveDownload>()

    @Volatile
    private var channelsCreated = false

    private fun ensureChannels(context: Context) {
        if (channelsCreated) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val downloadChannel = NotificationChannel(
                CHANNEL_ID_DOWNLOADS,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Active torrent downloads"
                setShowBadge(false)
            }
            val completedChannel = NotificationChannel(
                CHANNEL_ID_COMPLETED,
                "Completed Downloads",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Finished downloads"
                setShowBadge(true)
            }
            nm.createNotificationChannel(downloadChannel)
            nm.createNotificationChannel(completedChannel)
        }
        channelsCreated = true
    }

    private fun getLaunchPendingIntent(context: Context): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        } ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @Suppress("DEPRECATION")
    private fun newNotificationBuilder(context: Context, channelId: String): Notification.Builder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channelId)
        } else {
            Notification.Builder(context)
        }
    }

    fun createForegroundNotification(context: Context): Notification {
        ensureChannels(context)

        val downloads = activeDownloads.values.toList()
        val title: String
        val content: String
        val progressPercent: Int

        if (downloads.isEmpty()) {
            title = "Torrent Engine"
            content = "Running in background"
            progressPercent = 0
        } else if (downloads.size == 1) {
            val d = downloads.first()
            val percent = (d.progress * 100).toInt().coerceIn(0, 100)
            title = d.title
            content = "$percent% • ${formatSpeed(d.downloadRate)} • ${d.peers} peers"
            progressPercent = percent
        } else {
            val avgProgress = (downloads.map { it.progress }.average() * 100).toInt().coerceIn(0, 100)
            val totalSpeed = downloads.sumOf { it.downloadRate }
            title = "${downloads.size} downloads in progress"
            content = "$avgProgress% • ${formatSpeed(totalSpeed)}"
            progressPercent = avgProgress
        }

        val builder = newNotificationBuilder(context, CHANNEL_ID_DOWNLOADS)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (downloads.isNotEmpty()) {
            builder.setProgress(100, progressPercent, progressPercent <= 0 && downloads.firstOrNull()?.downloadRate == 0L)
        }

        getLaunchPendingIntent(context)?.let {
            builder.setContentIntent(it)
        }

        return builder.build()
    }

    private fun startServiceIfNeeded(context: Context) {
        if (!DownloadService.isRunning) {
            try {
                val intent = Intent(context, DownloadService::class.java).apply {
                    action = DownloadService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }
    }

    private fun stopServiceIfNoActiveDownloads(context: Context) {
        if (activeDownloads.isEmpty()) {
            try {
                val intent = Intent(context, DownloadService::class.java).apply {
                    action = DownloadService.ACTION_STOP
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }

    actual fun notifyProgress(
        id: String,
        title: String,
        progress: Float,
        downloadRate: Long,
        peers: Int,
    ) {
        val context = runCatching { AndroidApp.context }.getOrNull() ?: return
        ensureChannels(context)

        activeDownloads[id] = ActiveDownload(
            id = id,
            title = title,
            progress = progress,
            downloadRate = downloadRate,
            peers = peers,
        )

        startServiceIfNeeded(context)

        // Update ongoing foreground notification
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        try {
            val notification = createForegroundNotification(context)
            nm.notify(DownloadService.FOREGROUND_NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    actual fun notifyCompleted(id: String, title: String) {
        val context = runCatching { AndroidApp.context }.getOrNull() ?: return
        ensureChannels(context)

        activeDownloads.remove(id)
        stopServiceIfNoActiveDownloads(context)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        try {
            val builder = newNotificationBuilder(context, CHANNEL_ID_COMPLETED)
                .setContentTitle("Download Complete")
                .setContentText(title)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)

            getLaunchPendingIntent(context)?.let {
                builder.setContentIntent(it)
            }

            // Post completion notification with unique hash
            nm.notify(id.hashCode(), builder.build())
        } catch (_: Exception) {}

        // If there are other downloads still running, update the foreground notification
        if (activeDownloads.isNotEmpty()) {
            try {
                val notification = createForegroundNotification(context)
                nm.notify(DownloadService.FOREGROUND_NOTIFICATION_ID, notification)
            } catch (_: Exception) {}
        }
    }

    actual fun notifyFailed(id: String, title: String, error: String?) {
        val context = runCatching { AndroidApp.context }.getOrNull() ?: return
        ensureChannels(context)

        activeDownloads.remove(id)
        stopServiceIfNoActiveDownloads(context)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        try {
            val builder = newNotificationBuilder(context, CHANNEL_ID_COMPLETED)
                .setContentTitle("Download Failed")
                .setContentText("$title: ${error ?: "Unknown error"}")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setAutoCancel(true)

            getLaunchPendingIntent(context)?.let {
                builder.setContentIntent(it)
            }

            nm.notify(id.hashCode(), builder.build())
        } catch (_: Exception) {}

        if (activeDownloads.isNotEmpty()) {
            try {
                val notification = createForegroundNotification(context)
                nm.notify(DownloadService.FOREGROUND_NOTIFICATION_ID, notification)
            } catch (_: Exception) {}
        }
    }

    actual fun cancel(id: String) {
        val context = runCatching { AndroidApp.context }.getOrNull() ?: return
        activeDownloads.remove(id)
        stopServiceIfNoActiveDownloads(context)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        try {
            nm.cancel(id.hashCode())
        } catch (_: Exception) {}

        if (activeDownloads.isNotEmpty()) {
            try {
                val notification = createForegroundNotification(context)
                nm.notify(DownloadService.FOREGROUND_NOTIFICATION_ID, notification)
            } catch (_: Exception) {}
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / (1024f * 1024f))
            bytesPerSec >= 1024 -> String.format(Locale.US, "%.0f KB/s", bytesPerSec / 1024f)
            else -> "$bytesPerSec B/s"
        }
    }
}
