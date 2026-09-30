package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build

object NotificationChannels {

    const val CHANNEL_TIMER_ID = "channel_watch_timer"
    const val CHANNEL_ALERT_ID = "channel_watch_alert"
    const val CHANNEL_COMPLETION_ID = "channel_watch_completion"

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

            notificationManager.createNotificationChannels(
                listOf(timerChannel, alertChannel, completionChannel)
            )
        }
    }
}
