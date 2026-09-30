package com.example.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.DataStoreManager
import com.example.data.SessionState
import com.example.repository.WatchSessionRepository
import com.example.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WatchTimerService : Service() {

    companion object {
        const val ACTION_START = "com.example.action.START_TIMER_SERVICE"
        const val ACTION_STOP = "com.example.action.STOP_TIMER_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, WatchTimerService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WatchTimerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timerLoopJob: Job? = null
    private var stateObserverJob: Job? = null
    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var floatingOverlayManager: FloatingTimerOverlayManager

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createChannels(this)
        dataStoreManager = DataStoreManager(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        floatingOverlayManager = FloatingTimerOverlayManager(this)

        // Setup repository callbacks
        WatchSessionRepository.onRedAlertTriggered = { title, message ->
            floatingOverlayManager.hideOverlay()
            postRedAlertNotification(title, message)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        WatchSessionRepository.onCompletionTriggered = { coins, title ->
            serviceScope.launch {
                dataStoreManager.addRewardTransaction(title, coins)
                floatingOverlayManager.hideOverlay()
                postCompletionNotification(coins, title)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        WatchSessionRepository.onSaveProgressNeeded = { millis ->
            serviceScope.launch {
                dataStoreManager.saveWatchedMillis(millis)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                floatingOverlayManager.hideOverlay()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startForegroundWithNotification()
                floatingOverlayManager.showOverlay()
                startTimerLoop()
                observeSessionState()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val initialNotification = buildTimerNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationChannels.NOTIFICATION_TIMER_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NotificationChannels.NOTIFICATION_TIMER_ID, initialNotification)
        }
        WatchSessionRepository.setServiceRunning(true)
    }

    private fun startTimerLoop() {
        timerLoopJob?.cancel()
        timerLoopJob = serviceScope.launch {
            var lastNotificationUpdateSec = -1
            while (isActive) {
                WatchSessionRepository.processTimerTick()

                val watchedMillis = WatchSessionRepository.watchedMillis.value
                val requiredMillis = WatchSessionRepository.requiredMillis.value
                val currentSec = (watchedMillis / 1000).toInt()

                // Update floating WindowManager overlay over YouTube
                floatingOverlayManager.updateProgress(
                    watchedMillis = watchedMillis,
                    requiredMillis = requiredMillis,
                    milestone = WatchSessionRepository.currentMilestoneTier.value
                )

                // Update notification text every second
                if (currentSec != lastNotificationUpdateSec) {
                    lastNotificationUpdateSec = currentSec
                    notificationManager.notify(
                        NotificationChannels.NOTIFICATION_TIMER_ID,
                        buildTimerNotification()
                    )
                }

                delay(500L)
            }
        }
    }

    private fun observeSessionState() {
        stateObserverJob?.cancel()
        stateObserverJob = serviceScope.launch {
            WatchSessionRepository.sessionState.collectLatest { state ->
                when (state) {
                    SessionState.INVALID -> {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    SessionState.COMPLETED -> {
                        // Will be stopped by onCompletionTriggered
                    }
                    else -> {
                        // Keep running
                    }
                }
            }
        }
    }

    private fun buildTimerNotification(): Notification {
        val watchedMillis = WatchSessionRepository.watchedMillis.value
        val requiredMillis = WatchSessionRepository.requiredMillis.value

        val watchedStr = TimeFormatter.formatMillisToMmSs(watchedMillis)
        val requiredStr = TimeFormatter.formatMillisToMmSs(requiredMillis)
        val status = when (WatchSessionRepository.sessionState.value) {
            SessionState.WAITING -> "Waiting for video to play..."
            SessionState.ACTIVE -> "Watching verified video"
            SessionState.INVALID -> "Invalid video"
            SessionState.COMPLETED -> "Completed!"
            SessionState.IDLE -> "Idle"
        }

        val milestone = WatchSessionRepository.currentMilestoneTier.value
        val milestoneText = if (milestone != null) " • ${milestone.minutes}m Done (+${milestone.coins}c)" else " • Min 3m required"
        val titleText = "WatchEarn: ⏱️ $watchedStr / $requiredStr$milestoneText"
        val contentText = "$status • Target: ${WatchSessionRepository.targetTaskTitle.value ?: "YouTube Video"}"

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationChannels.CHANNEL_TIMER_ID)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(
                (requiredMillis / 1000).toInt(),
                (watchedMillis / 1000).toInt(),
                false
            )
            .build()
    }

    private fun postRedAlertNotification(title: String, message: String) {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NotificationChannels.CHANNEL_ALERT_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setColor(Color.RED)
            .setColorized(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NotificationChannels.NOTIFICATION_ALERT_ID, notification)
    }

    private fun postCompletionNotification(coins: Int, taskTitle: String) {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            2,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NotificationChannels.CHANNEL_COMPLETION_ID)
            .setContentTitle("Task complete! +$coins coins added")
            .setContentText("Congratulations! You earned $coins coins for watching \"$taskTitle\".")
            .setSmallIcon(android.R.drawable.star_on)
            .setColor(Color.parseColor("#F59E0B"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NotificationChannels.NOTIFICATION_COMPLETION_ID, notification)
    }

    override fun onDestroy() {
        floatingOverlayManager.hideOverlay()
        WatchSessionRepository.setServiceRunning(false)
        timerLoopJob?.cancel()
        stateObserverJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
