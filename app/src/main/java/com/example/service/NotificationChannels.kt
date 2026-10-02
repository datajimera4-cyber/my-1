package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.DataStoreManager
import com.example.data.PayoutStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object NotificationChannels {

    const val CHANNEL_TIMER_ID = "channel_watch_timer"
    const val CHANNEL_ALERT_ID = "channel_watch_alert"
    const val CHANNEL_COMPLETION_ID = "channel_watch_completion"
    const val CHANNEL_ADMIN_UPDATES_ID = "channel_kingo_admin_push_v2"

    const val NOTIFICATION_TIMER_ID = 1001
    const val NOTIFICATION_ALERT_ID = 1002
    const val NOTIFICATION_COMPLETION_ID = 1003

    private val notifyMutex = Mutex()
    private val inMemoryDispatchedKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

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

            // 4. Instant Admin Push Notifications Channel (High Importance Heads-Up + Sound + Vibration)
            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val adminUpdatesChannel = NotificationChannel(
                CHANNEL_ADMIN_UPDATES_ID,
                "Kingo King Live Push Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Instant push notifications for new tasks, posts, withdrawals, wallet updates & app updates"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
                lightColor = Color.parseColor("#F59E0B")
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                if (defaultSoundUri != null) {
                    setSound(defaultSoundUri, audioAttributes)
                }
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
        dedupKey: String? = null,
        allowOnAdminApp: Boolean = false,
        notificationId: Int = ((dedupKey?.hashCode()?.let { Math.abs(it) } ?: (System.currentTimeMillis() % 100000).toInt()) % 80000) + 2000
    ) {
        if (!allowOnAdminApp && com.example.BuildConfig.APP_ROLE == "ADMIN") return
        if (!dedupKey.isNullOrBlank()) {
            if (!inMemoryDispatchedKeys.add(dedupKey)) {
                return
            }
        }
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
                .setSmallIcon(R.drawable.ic_stat_kingo_notification)
                .setColor(Color.parseColor("#F59E0B"))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(notificationId, notification)
        } catch (_: Exception) {}
    }

    /**
     * Centralized real-time notification evaluator that checks DataStore for ANY new Admin action:
     * - New or Updated/Pinned Video Tasks
     * - New or Updated/Pinned Admin Posts / Banners / Urgent Alerts
     * - Withdrawal Request Submitted / Approved / Payment Done / Rejected & Refunded
     * - Admin Wallet Balance Adjustment & 10% Referral Bonuses
     * - Support Chat Replies
     * - Mandatory App Update Available
     * Runs both when the app is in the foreground (MainViewModel) and in the background (YouTubeLiveSearchService).
     */
    suspend fun checkAndDispatchAdminNotifications(
        context: Context,
        dataStoreManager: DataStoreManager
    ) {
        notifyMutex.withLock {
            try {
                val isAdminApp = com.example.BuildConfig.APP_ROLE == "ADMIN"
                val notified = dataStoreManager.notifiedItemIdsFlow.first()
                inMemoryDispatchedKeys.addAll(notified)
                val newlyNotifiedKeys = mutableSetOf<String>()

                if (!isAdminApp) {
                    val currentEmail = dataStoreManager.currentUserEmailFlow.first()?.trim()?.lowercase()
                    val currentUser = dataStoreManager.currentUserFlow.first()
                    // If user has not logged in yet (fresh install / on auth screen), DO NOT send push notifications!
                    // Baseline all current tasks & posts into notified set so they never fire later as pseudo-new items.
                    if (currentUser == null || currentEmail.isNullOrBlank() || currentEmail == "guest@watchearn.com") {
                        val tasks = dataStoreManager.videoTasksFlow.first()
                        val posts = dataStoreManager.adminPostsFlow.first()
                        val baselineKeys = tasks.map { it.id }.toSet() + posts.map { it.id }.toSet()
                        val missingBaseline = baselineKeys.filter { !notified.contains(it) }.toSet()
                        if (missingBaseline.isNotEmpty()) {
                            dataStoreManager.markItemsNotified(missingBaseline)
                            inMemoryDispatchedKeys.addAll(missingBaseline)
                        }
                        return
                    }

                    // 1. Check Video Tasks (Only 1 notification per newly added task)
                    val defaultTaskIds = setOf("default_rick", "default_android15", "default_kotlin_course", "default_lofi_live")
                    val tasks = dataStoreManager.videoTasksFlow.first()
                    for (t in tasks) {
                        if (defaultTaskIds.contains(t.id)) continue
                        val taskKey = "task_${t.id}"
                        if (!notified.contains(t.id) && !notified.contains(taskKey) &&
                            !inMemoryDispatchedKeys.contains(t.id) && !inMemoryDispatchedKeys.contains(taskKey)
                        ) {
                            newlyNotifiedKeys.add(t.id)
                            newlyNotifiedKeys.add(taskKey)
                            sendAdminUpdateNotification(
                                context = context,
                                title = "🎬 New Video Task (+${t.rewardCoins} Coins)",
                                body = t.title,
                                dedupKey = taskKey
                            )
                        }
                    }

                    // 2. Check Posts / Banners / Announcements (Only 1 notification per newly added post)
                    val defaultPostIds = setOf("default_welcome_banner")
                    val posts = dataStoreManager.adminPostsFlow.first()
                    for (p in posts) {
                        if (defaultPostIds.contains(p.id)) continue
                        val postKey = "post_${p.id}"
                        if (!notified.contains(p.id) && !notified.contains(postKey) &&
                            !inMemoryDispatchedKeys.contains(p.id) && !inMemoryDispatchedKeys.contains(postKey)
                        ) {
                            newlyNotifiedKeys.add(p.id)
                            newlyNotifiedKeys.add(postKey)
                            sendAdminUpdateNotification(
                                context = context,
                                title = p.title.ifBlank { "🔔 New Announcement" },
                                body = p.message.ifBlank { "Tap to open Kingo King." },
                                dedupKey = postKey
                            )
                        }
                    }

                    // 3. Check Payout / Withdrawal Requests (APPROVED / COMPLETED / REJECTED)
                    val payouts = dataStoreManager.payoutRequestsFlow.first()
                    for (req in payouts) {
                        if (!currentEmail.isNullOrBlank() && !req.userEmail.equals(currentEmail, ignoreCase = true)) {
                            continue
                        }
                        if (req.status != PayoutStatus.PENDING) {
                            val statusNotifyKey = "payout_${req.id}_${req.status.name}"
                            if (!notified.contains(statusNotifyKey) && !inMemoryDispatchedKeys.contains(statusNotifyKey)) {
                                newlyNotifiedKeys.add(statusNotifyKey)
                                val safeCoins = if (req.amountCoins > 0) req.amountCoins else (req.amountInr * 100).toInt()
                                val inrStr = String.format(Locale.US, "%.2f", req.amountInr)
                                when (req.status) {
                                    PayoutStatus.APPROVED -> {
                                        sendAdminUpdateNotification(
                                            context = context,
                                            title = "✅ Withdrawal Approved (₹$inrStr)",
                                            body = "Your ₹$inrStr payout via ${req.method} is approved and processing.",
                                            dedupKey = statusNotifyKey
                                        )
                                    }
                                    PayoutStatus.COMPLETED -> {
                                        sendAdminUpdateNotification(
                                            context = context,
                                            title = "🎉 Payment Sent! ₹$inrStr",
                                            body = "₹$inrStr ($safeCoins Coins) has been sent to ${req.method} (${req.destination}).",
                                            dedupKey = statusNotifyKey
                                        )
                                    }
                                    PayoutStatus.REJECTED -> {
                                        sendAdminUpdateNotification(
                                            context = context,
                                            title = "❌ Withdrawal Refunded (+$safeCoins Coins)",
                                            body = "$safeCoins Coins have been returned to your wallet.",
                                            dedupKey = statusNotifyKey
                                        )
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }

                    // 4. Check Wallet Transactions for Coin Updates & Referral Bonuses
                    val txList = dataStoreManager.transactionsFlow.first()
                    for (tx in txList) {
                        val isAdminCoin = tx.id.startsWith("admin_coin_") || tx.title.contains("Balance Updated") || tx.title.contains("Admin Balance Update")
                        val isRefBonus = tx.id.startsWith("ref_withdraw_bonus_") || tx.title.contains("Referral Withdraw Bonus")
                        if ((isAdminCoin || isRefBonus) && !notified.contains(tx.id) && !inMemoryDispatchedKeys.contains(tx.id)) {
                            newlyNotifiedKeys.add(tx.id)
                            val sign = if (tx.coins >= 0) "+${tx.coins}" else "${tx.coins}"
                            if (isRefBonus) {
                                sendAdminUpdateNotification(
                                    context = context,
                                    title = "🤝 Referral Bonus ($sign Coins)",
                                    body = tx.title,
                                    dedupKey = tx.id
                                )
                            } else {
                                sendAdminUpdateNotification(
                                    context = context,
                                    title = "🪙 Wallet Updated ($sign Coins)",
                                    body = "Your wallet balance has been updated.",
                                    dedupKey = tx.id
                                )
                            }
                        }
                    }

                    // 5. Check Mandatory App Update Availability
                    val appUpdate = dataStoreManager.remoteAppUpdateFlow.first()
                    val installedSig = dataStoreManager.installedUpdateSignatureFlow.first()
                    if (appUpdate != null && appUpdate.hasUpdate &&
                        (appUpdate.fileId.isNotBlank() || appUpdate.downloadUrl.isNotBlank()) &&
                        appUpdate.signature != installedSig
                    ) {
                        val updKey = "app_update_${appUpdate.signature}"
                        if (!notified.contains(updKey) && !inMemoryDispatchedKeys.contains(updKey)) {
                            newlyNotifiedKeys.add(updKey)
                            sendAdminUpdateNotification(
                                context = context,
                                title = "🚀 App Update Available",
                                body = "Tap to download the latest update.",
                                dedupKey = updKey
                            )
                        }
                    }
                }

                // 6. Check Support Chat Messages
                val msgs = dataStoreManager.supportMessagesFlow.first()
                if (isAdminApp) {
                    val newIncoming = msgs.filter { m ->
                        m.senderRole == "USER" && !notified.contains(m.id) && !inMemoryDispatchedKeys.contains(m.id)
                    }
                    if (newIncoming.isNotEmpty()) {
                        for (m in newIncoming) newlyNotifiedKeys.add(m.id)
                        val latest = newIncoming.last()
                        sendAdminUpdateNotification(
                            context = context,
                            title = "💬 Support Query from ${latest.userName} (${latest.userId})",
                            body = latest.message,
                            dedupKey = latest.id,
                            allowOnAdminApp = true
                        )
                    }
                } else {
                    val myEmail = dataStoreManager.currentUserEmailFlow.first()?.trim()?.lowercase() ?: "guest@watchearn.com"
                    val newReplies = msgs.filter { m ->
                        m.senderRole == "ADMIN" &&
                                m.userEmail.equals(myEmail, ignoreCase = true) &&
                                !notified.contains(m.id) &&
                                !inMemoryDispatchedKeys.contains(m.id)
                    }
                    if (newReplies.isNotEmpty()) {
                        for (m in newReplies) newlyNotifiedKeys.add(m.id)
                        val latest = newReplies.last()
                        sendAdminUpdateNotification(
                            context = context,
                            title = "💬 Support Reply",
                            body = latest.message,
                            dedupKey = latest.id
                        )
                    }
                }

                if (newlyNotifiedKeys.isNotEmpty()) {
                    dataStoreManager.markItemsNotified(newlyNotifiedKeys)
                }
            } catch (_: Exception) {}
        }
    }
}

