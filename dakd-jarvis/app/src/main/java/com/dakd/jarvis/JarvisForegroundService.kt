package com.dakd.jarvis

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat

class JarvisForegroundService : Service() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, JarvisForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, JarvisForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = NotificationManager(this)
        val notification = nm.buildForegroundNotification()

        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(
                this,
                NotificationManager.NOTIFICATION_ID_FOREGROUND,
                notification,
                foregroundServiceType
            )
        } catch (e: Exception) {
            // fallback
            try {
                startForeground(NotificationManager.NOTIFICATION_ID_FOREGROUND, notification)
            } catch (err: Exception) {
                // ignore
            }
        }

        return START_STICKY
    }
}
