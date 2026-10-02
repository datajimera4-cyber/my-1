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
import com.example.data.VideoPlaybackState
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
    private var completionJob: Job? = null
    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var floatingOverlayManager: FloatingTimerOverlayManager

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createChannels(this)
        dataStoreManager = DataStoreManager(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        floatingOverlayManager = FloatingTimerOverlayManager(this)
        registerRepositoryCallbacks()
    }

    private fun registerRepositoryCallbacks() {
        floatingOverlayManager.registerOverlayCallbacks()
        // Setup repository callbacks
        WatchSessionRepository.onRedAlertTriggered = { title, message ->
            completionJob?.cancel()
            postRedAlertNotification(title, message)
        }

        WatchSessionRepository.onServiceCompletionTriggered = { coins, title ->
            completionJob?.cancel()
            completionJob = serviceScope.launch {
                val activeId = WatchSessionRepository.activeTaskId.value
                dataStoreManager.addRewardTransaction("Watched: $title", coins)
                if (activeId != null) {
                    dataStoreManager.markTaskCompleted(activeId, coins)
                }
                floatingOverlayManager.showCoinAddedCelebration(coins, "+$coins COINS ADDED!")
                postCompletionNotification(coins, title)

                // Keep celebratory overlay on screen for ~3.5 seconds so user sees the reward animation
                delay(3500L)
                floatingOverlayManager.hideOverlay()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        WatchSessionRepository.onMilestoneCoinsAwarded = { coins, _ ->
            floatingOverlayManager.showCoinAddedCelebration(coins, "+$coins COINS ADDED!")
        }

        WatchSessionRepository.onPlaybackStateUpdated = { isPlaying ->
            if (floatingOverlayManager.isOverlayAttached()) {
                floatingOverlayManager.updateProgress(
                    watchedMillis = WatchSessionRepository.watchedMillis.value,
                    requiredMillis = WatchSessionRepository.requiredMillis.value,
                    milestone = WatchSessionRepository.currentMilestoneTier.value,
                    isPaused = !isPlaying
                )
            }
        }

        WatchSessionRepository.onServiceTaskIncomplete = { taskId, reason, lockDuration ->
            completionJob?.cancel()
            timerLoopJob?.cancel()
            serviceScope.launch {
                if (taskId.isNotBlank()) {
                    dataStoreManager.lockTask(taskId, lockDuration)
                }
            }
            postRedAlertNotification("Task Incomplete!", reason)
            floatingOverlayManager.showTaskIncompletePopup(reason) {
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
                completionJob?.cancel()
                floatingOverlayManager.hideOverlay()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                registerRepositoryCallbacks()
                completionJob?.cancel()
                startForegroundWithNotification()
                if (YouTubeLiveSearchService.isSearchOverlayActive) {
                    floatingOverlayManager.showOrUpdateSearchLoadingOverlay(
                        title = WatchSessionRepository.targetTaskTitle.value ?: "Video Task",
                        channel = WatchSessionRepository.targetTaskAuthor.value ?: "YouTube",
                        statusText = YouTubeLiveSearchService.searchOverlayStatusText
                    )
                }
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

    private fun pollActiveYouTubeMediaSession() {
        try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as? android.media.session.MediaSessionManager ?: return
            val componentName = android.content.ComponentName(this, WatchListenerService::class.java)
            val controllers = msm.getActiveSessions(componentName)
            val ytController = controllers.firstOrNull { it.packageName == "com.google.android.youtube" }
            if (ytController != null) {
                val metadata = ytController.metadata
                val title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
                val artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ?: metadata?.getString(android.media.MediaMetadata.METADATA_KEY_AUTHOR)
                if (!title.isNullOrBlank()) {
                    WatchSessionRepository.onMediaMetadataChanged(title, artist)
                }
                val playbackState = ytController.playbackState?.state
                if (playbackState != null) {
                    WatchSessionRepository.onPlaybackStateChanged(playbackState)
                }
            }
        } catch (_: SecurityException) {
            // NotificationListener not enabled; Accessibility inspection handles detection
        } catch (_: Exception) {}
    }

    private fun startTimerLoop() {
        timerLoopJob?.cancel()
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        timerLoopJob = serviceScope.launch {
            var lastNotificationUpdateSec = -1
            var hasSeenAudioPlaying = false
            var silentTicksCount = 0
            var wasAudioSilentWhileExplicitlyPaused = false
            while (isActive) {
                pollActiveYouTubeMediaSession()
                YouTubeLiveSearchService.instance?.inspectCurrentYouTubeState()

                val isAppForeground = WatchSessionRepository.isAppInForeground
                val isAudioPlaying = audioManager?.isMusicActive == true
                val isYtForeground = if (YouTubeLiveSearchService.isServiceConnected) {
                    YouTubeLiveSearchService.isYouTubeInForeground
                } else {
                    !isAppForeground
                }

                val sessionActive = WatchSessionRepository.sessionState.value == SessionState.ACTIVE
                if (!sessionActive) {
                    hasSeenAudioPlaying = false
                    silentTicksCount = 0
                    wasAudioSilentWhileExplicitlyPaused = false
                    if (floatingOverlayManager.isOverlayAttached()) {
                        floatingOverlayManager.hideOverlay()
                    }
                    delay(500L)
                    continue
                }

                if (!YouTubeLiveSearchService.isVideoExplicitlyPaused) {
                    wasAudioSilentWhileExplicitlyPaused = false
                }

                if (isAudioPlaying) {
                    hasSeenAudioPlaying = true
                    silentTicksCount = 0
                    // Only allow audio resumption to clear explicit pause if audio had actually stopped while paused
                    // AND the "Play video" button is not currently visible on screen
                    if (YouTubeLiveSearchService.isVideoExplicitlyPaused &&
                        wasAudioSilentWhileExplicitlyPaused &&
                        !YouTubeLiveSearchService.isPlayButtonCurrentlyVisible &&
                        !WatchSessionRepository.isMediaSessionExplicitlyPaused &&
                        System.currentTimeMillis() - YouTubeLiveSearchService.lastExplicitPauseTime > 1500L
                    ) {
                        YouTubeLiveSearchService.isVideoExplicitlyPaused = false
                        wasAudioSilentWhileExplicitlyPaused = false
                    }
                } else {
                    silentTicksCount++
                    if (YouTubeLiveSearchService.isVideoExplicitlyPaused) {
                        wasAudioSilentWhileExplicitlyPaused = true
                    }
                }

                val elapsedSinceLaunch = System.currentTimeMillis() - WatchSessionRepository.taskLaunchTimestampMillis
                val isPausedByAudioStop = hasSeenAudioPlaying && !isAudioPlaying && silentTicksCount >= 2

                // Video is paused if user paused in YouTube player, media session signaled paused, or active audio stream stopped
                val isExplicitlyPaused = elapsedSinceLaunch > 3000L && (
                        isPausedByAudioStop ||
                        YouTubeLiveSearchService.isVideoExplicitlyPaused ||
                        WatchSessionRepository.isMediaSessionExplicitlyPaused
                )

                val isMatched = WatchSessionRepository.matchResult.value != com.example.data.MatchResult.MISMATCH
                val isSearchStillInProgress = YouTubeLiveSearchService.isServiceConnected && (
                        !YouTubeLiveSearchService.isWatchPlayerConfirmedOpen ||
                        (YouTubeLiveSearchService.currentPhase != YouTubeLiveSearchService.LiveSearchPhase.IDLE &&
                         YouTubeLiveSearchService.currentPhase != YouTubeLiveSearchService.LiveSearchPhase.COMPLETED)
                )

                // Timer ticks ONLY when YouTube is active in foreground, target video is confirmed open in Watch Player, matches, and is playing (not paused)
                val isPlaying = !isAppForeground && isYtForeground && sessionActive && isMatched && !isExplicitlyPaused && !isSearchStillInProgress

                WatchSessionRepository.setPlaybackPlaying(isPlaying)
                WatchSessionRepository.processTimerTick()

                val watchedMillis = WatchSessionRepository.watchedMillis.value
                val requiredMillis = WatchSessionRepository.requiredMillis.value
                val currentSec = (watchedMillis / 1000).toInt()

                // Overlay shows ONLY when task is active and YouTube is in foreground
                val shouldShowOverlay = sessionActive && isYtForeground && !isAppForeground

                if (shouldShowOverlay) {
                    val searchOverlayStillActive = (YouTubeLiveSearchService.isSearchOverlayActive ||
                            (YouTubeLiveSearchService.isServiceConnected && !YouTubeLiveSearchService.isWatchPlayerConfirmedOpen)) &&
                            elapsedSinceLaunch < 35_000L
                    if (searchOverlayStillActive) {
                        floatingOverlayManager.showOrUpdateSearchLoadingOverlay(
                            title = WatchSessionRepository.targetTaskTitle.value ?: "Video Task",
                            channel = WatchSessionRepository.targetTaskAuthor.value ?: "YouTube",
                            statusText = "Opening..."
                        )
                    } else {
                        YouTubeLiveSearchService.dismissSearchOverlay()
                        floatingOverlayManager.hideSearchLoadingOverlay()
                        if (!floatingOverlayManager.isOverlayAttached()) {
                            floatingOverlayManager.showOverlay()
                        }
                        floatingOverlayManager.updateProgress(
                            watchedMillis = watchedMillis,
                            requiredMillis = requiredMillis,
                            milestone = WatchSessionRepository.currentMilestoneTier.value,
                            isPaused = !isPlaying
                        )
                    }
                } else {
                    // When user returns to our app, minimizes YouTube, or closes YouTube:
                    // Floating overlay disappears immediately!
                    if (elapsedSinceLaunch > 2000L) {
                        floatingOverlayManager.hideSearchLoadingOverlay()
                    }
                    if (floatingOverlayManager.isOverlayAttached()) {
                        floatingOverlayManager.hideOverlay()
                    }
                }

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
                    SessionState.INVALID, SessionState.IDLE -> {
                        floatingOverlayManager.hideOverlay()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    SessionState.COMPLETED -> {
                        // Will be stopped by onServiceCompletionTriggered
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
        WatchSessionRepository.onRequestShowOverlay = null
        WatchSessionRepository.onRequestHideOverlay = null
        WatchSessionRepository.onServiceTaskIncomplete = null
        WatchSessionRepository.onServiceCompletionTriggered = null
        WatchSessionRepository.setServiceRunning(false)
        timerLoopJob?.cancel()
        stateObserverJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
