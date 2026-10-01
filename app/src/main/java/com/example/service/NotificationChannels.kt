package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

object NotificationChannels {

    const val CHANNEL_TIMER_ID = "channel_watch_timer"
    const val CHANNEL_ALERT_ID = "channel_watch_alert"
    const val CHANNEL_COMPLETION_ID = "channel_watch_completion"
    const val CHANNEL_ADMIN_UPDATES_ID = "channel_admin_updates"

    const val NOTIFICATION_TIMER_ID = 1001
    const val NOTIFICATION_ALERT_ID = 1002
    const val NOTIFICATION_COMPLETION_ID = 1003

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
                ?: return

            // 1. Silent Ongoing Timer Channel (updates every second)
            val timerChannel = NotificationChannel(
                CHANNEL_TIMER_ID,
                "Watch Progress Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live watch progress while watching YouTube video"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }

            // 2. High-Importance Red Alert Channel (vibration + heads-up)
            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "Watch Red Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts when wrong video is detected or task cancelled"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                lightColor = Color.RED
                enableLights(true)
            }

            // 3. Task Completion Channel
            val completionChannel = NotificationChannel(
                CHANNEL_COMPLETION_ID,
                "Task Rewards & Completion",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when task completes and coins are credited"
                enableVibration(true)
            }

            // 4. Instant Admin New Task & Post Alerts Channel (High Importance Heads-Up)
            val adminUpdatesChannel = NotificationChannel(
                CHANNEL_ADMIN_UPDATES_ID,
                "New Tasks & Admin Announcements",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Instant notifications when Admin posts a new video task, banner, or alert"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                lightColor = Color.parseColor("#F59E0B")
                enableLights(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(
                listOf(timerChannel, alertChannel, completionChannel, adminUpdatesChannel)
            )
        }
    }

    fun sendAdminUpdateNotification(
        context: Context,
        title: String,
        body: String,
        notificationId: Int = (System.currentTimeMillis() % 100000).toInt() + 2000
    ) {
        try {
            createChannels(context)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ADMIN_UPDATES_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_PROMO)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(notificationId, notification)
        } catch (_: Exception) {}
    }
}
