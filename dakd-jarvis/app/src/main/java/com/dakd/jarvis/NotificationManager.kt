package com.dakd.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager as AndroidNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.R

class NotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_FOREGROUND = "dakd_jarvis_fg_channel"
        const val CHANNEL_REMINDERS = "dakd_jarvis_reminders_channel"
        const val NOTIFICATION_ID_FOREGROUND = 1001
    }

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val fgChannel = NotificationChannel(
                CHANNEL_FOREGROUND,
                "DAKD JARVIS Status",
                AndroidNotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground assistant status and quick access"
                setShowBadge(false)
            }

            val remChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                "DAKD JARVIS Reminders",
                AndroidNotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders and scheduled automations"
                enableVibration(true)
            }

            nm.createNotificationChannel(fgChannel)
            nm.createNotificationChannel(remChannel)
        }
    }

    fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action", "voice_listen")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_FOREGROUND)
            .setSmallIcon(R.drawable.dakd_jarvis_icon_1789661735066)
            .setContentTitle("DAKD JARVIS")
            .setContentText("JARVIS is ready. Tap to command.")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun showReminderNotification(id: Int, title: String, text: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.dakd_jarvis_icon_1789661735066)
            .setContentTitle("DAKD JARVIS: $title")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // notification permission missing
        }
    }
}
