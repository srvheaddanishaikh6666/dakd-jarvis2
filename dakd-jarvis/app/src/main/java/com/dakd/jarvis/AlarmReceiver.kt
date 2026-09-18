package com.dakd.jarvis

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val title = intent?.getStringExtra("title") ?: "Reminder"
        val message = intent?.getStringExtra("message") ?: "Aapka scheduled task ready hai Sir."
        val id = intent?.getIntExtra("id", (System.currentTimeMillis() % 10000).toInt()) ?: 101

        val notifManager = NotificationManager(context)
        notifManager.showReminderNotification(id, title, message)
    }
}
