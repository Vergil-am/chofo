package dev.dogetime.torrent

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopServiceInternal()
            return START_NOT_STICKY
        }

        val notification = DownloadNotifier.createForegroundNotification(this)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    FOREGROUND_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            // Fallback if foreground service type restriction or permission issues occur
            try {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification)
            } catch (_: Exception) {}
        }

        return START_NOT_STICKY
    }

    private fun stopServiceInternal() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 10001
        const val ACTION_START = "dev.dogetime.torrent.START_DOWNLOAD_SERVICE"
        const val ACTION_STOP = "dev.dogetime.torrent.STOP_DOWNLOAD_SERVICE"

        @Volatile
        var isRunning: Boolean = false
            internal set
    }
}
