package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DataStoreManager
import com.example.data.LogType
import com.example.data.MatchResult
import com.example.data.OEmbedFetcher
import com.example.data.OEmbedResult
import com.example.data.SampleTask
import com.example.data.SessionState
import com.example.data.VideoPlaybackState
import com.example.data.WalletTransaction
import com.example.repository.WatchSessionRepository
import com.example.service.WatchTimerService
import com.example.util.PermissionHelper
import com.example.util.TitleMatcher
import com.example.data.YouTubeSearchEngine
import com.example.service.YouTubeLiveSearchService
import com.example.data.VideoTaskItem
import com.example.data.WatchDurationTier
import com.example.data.WATCH_DURATION_TIERS
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppScreen {
    HOME,
    TASKS,
    TASK,
    TASK_DETAIL,
    WALLET,
    ME,
    SETUP,
    DIAGNOSTICS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStoreManager = DataStoreManager(application)

    // Navigation
    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val screenBackStack = mutableListOf<AppScreen>()

    // Local DataStore states
    val walletBalance: StateFlow<Int> = dataStoreManager.walletBalanceFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val isTaskCompleted: StateFlow<Boolean> = dataStoreManager.isTaskCompletedFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val transactions: StateFlow<List<WalletTransaction>> = dataStoreManager.transactionsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val liveSearchMode: StateFlow<Boolean> = dataStoreManager.liveSearchModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val videoTasks: StateFlow<List<VideoTaskItem>> = dataStoreManager.videoTasksFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, dataStoreManager.getDefaultTasks())

    val selectedTaskId: StateFlow<String?> = dataStoreManager.selectedTaskIdFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _activeRewardCoins = MutableStateFlow(10)
    val activeRewardCoins: StateFlow<Int> = _activeRewardCoins.asStateFlow()

    val targetTaskTitle: StateFlow<String?> = WatchSessionRepository.targetTaskTitle

    fun toggleLiveSearchMode(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.setLiveSearchMode(enabled)
            WatchSessionRepository.addLog(
                if (enabled) "Live YouTube Search Mode enabled" else "In-app animated search simulation enabled",
                LogType.INFO
            )
        }
    }

    // Active video URL to track
    private val _currentVideoUrl = MutableStateFlow(SampleTask.videoUrl)
    val currentVideoUrl: StateFlow<String> = _currentVideoUrl.asStateFlow()

    // Active selected duration tier
    private val _selectedTierSeconds = MutableStateFlow(SampleTask.requiredSeconds)
    val selectedTierSeconds: StateFlow<Int> = _selectedTierSeconds.asStateFlow()

    private val _selectedTierCoins = MutableStateFlow(SampleTask.rewardCoins)
    val selectedTierCoins: StateFlow<Int> = _selectedTierCoins.asStateFlow()

    // oEmbed metadata state
    private val _oEmbedState = MutableStateFlow<OEmbedResult>(OEmbedResult.Idle)
    val oEmbedState: StateFlow<OEmbedResult> = _oEmbedState.asStateFlow()

    // Success dialog
    private val _showSuccessDialog = MutableStateFlow(false)
    val showSuccessDialog: StateFlow<Boolean> = _showSuccessDialog.asStateFlow()

    // Delegated from repository
    val sessionState: StateFlow<SessionState> = WatchSessionRepository.sessionState
    val matchResult: StateFlow<MatchResult> = WatchSessionRepository.matchResult
    val playbackState: StateFlow<VideoPlaybackState> = WatchSessionRepository.playbackState
    val currentMediaTitle: StateFlow<String?> = WatchSessionRepository.currentMediaTitle
    val currentMediaArtist: StateFlow<String?> = WatchSessionRepository.currentMediaArtist
    val watchedMillis: StateFlow<Long> = WatchSessionRepository.watchedMillis
    val requiredMillis: StateFlow<Long> = WatchSessionRepository.requiredMillis
    val isServiceRunning: StateFlow<Boolean> = WatchSessionRepository.isServiceRunning
    val isGracePeriodActive: StateFlow<Boolean> = WatchSessionRepository.isGracePeriodActive
    val graceSecondsRemaining: StateFlow<Int> = WatchSessionRepository.graceSecondsRemaining
    val mediaSessionDetected: StateFlow<Boolean> = WatchSessionRepository.mediaSessionDetected
    val redAlertMessage: StateFlow<String?> = WatchSessionRepository.redAlertMessage
    val eventLogs = WatchSessionRepository.eventLogs
    val searchProgress = WatchSessionRepository.searchProgress

    init {
        // Observe persisted watchedMillis on launch to restore progress
        viewModelScope.launch {
            dataStoreManager.watchedMillisFlow.collectLatest { persistedMillis ->
                if (WatchSessionRepository.sessionState.value == SessionState.IDLE) {
                    WatchSessionRepository.setWatchedMillis(persistedMillis)
                }
            }
        }

        // Observe completion flag from DataStore
        viewModelScope.launch {
            dataStoreManager.isTaskCompletedFlow.collectLatest { completed ->
                if (completed) {
                    WatchSessionRepository.setCompletedState()
                }
            }
        }

        // Observe session state changes to show dialog upon completion
        viewModelScope.launch {
            WatchSessionRepository.sessionState.collectLatest { state ->
                if (state == SessionState.COMPLETED) {
                    _showSuccessDialog.value = true
                }
            }
        }

        viewModelScope.launch {
            WatchSessionRepository.rewardCoins.collectLatest { coins ->
                _activeRewardCoins.value = coins
            }
        }

        // Check if custom URL was saved
        viewModelScope.launch {
            dataStoreManager.activeVideoUrlFlow.collectLatest { savedUrl ->
                if (!savedUrl.isNullOrBlank()) {
                    _currentVideoUrl.value = savedUrl
                }
                fetchOEmbed()
            }
        }

        // Repository completion callback
        WatchSessionRepository.onCompletionTriggered = { coins, title ->
            viewModelScope.launch {
                dataStoreManager.addRewardTransaction(title, coins)
                dataStoreManager.setTaskCompleted(true)
                val activeId = WatchSessionRepository.activeTaskId.value
                if (activeId != null) {
                    dataStoreManager.markTaskCompleted(activeId, coins)
                }
            }
        }

        WatchSessionRepository.onSaveProgressNeeded = { millis ->
            viewModelScope.launch {
                dataStoreManager.setWatchedMillis(millis)
            }
        }
    }

    fun switchTab(screen: AppScreen) {
        screenBackStack.clear()
        _currentScreen.value = screen
    }

    fun selectTask(task: VideoTaskItem) {
        _currentVideoUrl.value = task.videoUrl
        _selectedTierSeconds.value = task.selectedDurationSeconds
        _selectedTierCoins.value = task.rewardCoins
        viewModelScope.launch {
            dataStoreManager.setSelectedTaskId(task.id)
            dataStoreManager.setActiveVideoUrl(task.videoUrl)
            fetchOEmbed(task.videoUrl)
        }
    }

    fun addVideoTask(task: VideoTaskItem) {
        val cleanUrl = TitleMatcher.cleanYouTubeUrl(task.videoUrl)
        val cleanedTask = task.copy(videoUrl = cleanUrl)
        viewModelScope.launch {
            dataStoreManager.addVideoTask(cleanedTask)
            WatchSessionRepository.addLog("Created new video task: \"${cleanedTask.title}\"", LogType.SUCCESS)
        }
    }

    fun startTaskWithTier(task: VideoTaskItem, tier: WatchDurationTier, context: Context) {
        _selectedTierSeconds.value = tier.seconds
        _selectedTierCoins.value = tier.coins
        selectTask(task)
        startTaskInternal(
            context = context,
            videoUrl = task.videoUrl,
            requiredSeconds = tier.seconds,
            rewardCoins = tier.coins,
            taskId = task.id
        )
    }

    fun navigateTo(screen: AppScreen) {
        if (_currentScreen.value != screen) {
            screenBackStack.add(_currentScreen.value)
            _currentScreen.value = screen
        }
    }

    fun navigateBack(): Boolean {
        if (screenBackStack.isNotEmpty()) {
            _currentScreen.value = screenBackStack.removeAt(screenBackStack.lastIndex)
            return true
        }
        return false
    }

    fun fetchOEmbed(targetUrl: String? = null) {
        val url = targetUrl ?: _currentVideoUrl.value
        _oEmbedState.value = OEmbedResult.Loading
        WatchSessionRepository.addLog("Fetching oEmbed for: $url", LogType.INFO)

        viewModelScope.launch {
            val result = OEmbedFetcher.fetchOEmbed(url)
            _oEmbedState.value = result
            when (result) {
                is OEmbedResult.Success -> {
                    WatchSessionRepository.addLog(
                        "oEmbed title fetched: \"${result.title}\" (${result.authorName})",
                        LogType.SUCCESS
                    )
                }
                is OEmbedResult.Error -> {
                    WatchSessionRepository.addLog("oEmbed fetch failed: ${result.message}", LogType.ERROR)
                }
                else -> {}
            }
        }
    }

    fun useDemoVideo() {
        setVideoUrl(SampleTask.fallbackDemoUrl)
    }

    fun setVideoUrl(url: String) {
        val cleanUrl = TitleMatcher.cleanYouTubeUrl(url).ifEmpty { url }
        _currentVideoUrl.value = cleanUrl
        viewModelScope.launch {
            dataStoreManager.setActiveVideoUrl(cleanUrl)
            fetchOEmbed(cleanUrl)
        }
    }

    fun startTask(context: Context) {
        startTaskInternal(
            context = context,
            videoUrl = _currentVideoUrl.value,
            requiredSeconds = _selectedTierSeconds.value,
            rewardCoins = _selectedTierCoins.value,
            taskId = selectedTaskId.value
        )
    }

    private fun startTaskInternal(
        context: Context,
        videoUrl: String,
        requiredSeconds: Int,
        rewardCoins: Int,
        taskId: String?
    ) {
        // Permission check: if missing essential permissions, route to Setup
        if (!PermissionHelper.areEssentialPermissionsGranted(context)) {
            WatchSessionRepository.addLog("Permissions missing. Redirecting to Setup screen.", LogType.WARNING)
            navigateTo(AppScreen.SETUP)
            return
        }

        if (searchProgress.value.isSearching) {
            return // Avoid duplicate search clicks
        }

        viewModelScope.launch {
            val effectiveUrl = if (videoUrl == "PASTE_MY_YOUTUBE_LINK_HERE" || !videoUrl.startsWith("http")) {
                SampleTask.fallbackDemoUrl
            } else {
                videoUrl
            }

            // Ensure oEmbed metadata is loaded
            var currentOEmbed = _oEmbedState.value
            if (currentOEmbed !is OEmbedResult.Success) {
                currentOEmbed = OEmbedFetcher.fetchOEmbed(effectiveUrl)
                _oEmbedState.value = currentOEmbed
            }

            val title: String
            val author: String
            if (currentOEmbed is OEmbedResult.Success) {
                title = currentOEmbed.title
                author = currentOEmbed.authorName
            } else {
                title = "YouTube Video Task"
                author = ""
            }

            val targetVideoId = TitleMatcher.extractVideoId(effectiveUrl)
            val currentWatched = watchedMillis.value

            if (liveSearchMode.value) {
                // LIVE MODE: Human-like YouTube automation. No pre-pasted title or direct video link!
                WatchSessionRepository.updateSearchProgress(com.example.data.SearchProgressState(isSearching = false))
                WatchSessionRepository.addLog("Live Mode: Opening YouTube to search & locate video like a human", LogType.INFO)

                // Arm the accessibility trigger to open search, clear old text, type title & click video
                YouTubeLiveSearchService.armSearchTrigger(title, author)

                WatchSessionRepository.startTask(
                    taskTitle = title,
                    taskAuthor = author,
                    requiredSeconds = requiredSeconds,
                    initialWatchedMillis = currentWatched,
                    rewardCoins = rewardCoins,
                    taskId = taskId
                )
                WatchTimerService.start(context)

                // Step 1: Open YouTube app normally (clean launch)
                val ytLaunchIntent = context.packageManager.getLaunchIntentForPackage(PermissionHelper.YOUTUBE_PACKAGE)
                    ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
                        setPackage(PermissionHelper.YOUTUBE_PACKAGE)
                    }
                ytLaunchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(ytLaunchIntent)

                WatchSessionRepository.addLog(
                    "YouTube opened! Kingo King Accessibility engine typing query and finding video...",
                    LogType.INFO
                )

                if (!PermissionHelper.isAccessibilityServiceEnabled(context)) {
                    WatchSessionRepository.addLog(
                        "⚠️ Tip: Enable Kingo King in Accessibility Settings for automatic typing and video clicking.",
                        LogType.WARNING
                    )
                }
            } else {
                // DEFAULT SIMULATION MODE: Shows in-app typewriter/radar loading screen
                val foundItem = YouTubeSearchEngine.searchAndLocateVideo(
                    targetTitle = title,
                    targetChannel = author,
                    targetVideoId = targetVideoId
                ) { progressState ->
                    WatchSessionRepository.updateSearchProgress(progressState)
                }

                WatchSessionRepository.startTask(
                    taskTitle = title,
                    taskAuthor = author,
                    requiredSeconds = requiredSeconds,
                    initialWatchedMillis = currentWatched,
                    rewardCoins = rewardCoins,
                    taskId = taskId
                )

                WatchTimerService.start(context)

                val targetUrlToLaunch = if (foundItem != null && foundItem.videoId.isNotEmpty()) {
                    "https://www.youtube.com/watch?v=${foundItem.videoId}"
                } else {
                    effectiveUrl
                }

                val openIntent = PermissionHelper.openVideoIntent(context, targetUrlToLaunch, title)
                context.startActivity(openIntent)
            }
        }
    }

    val currentMilestoneTier: StateFlow<WatchDurationTier?> = WatchSessionRepository.currentMilestoneTier

    fun resumeVideoInYouTube(context: Context) {
        val targetUrl = _currentVideoUrl.value
        val openIntent = PermissionHelper.openVideoIntent(context, targetUrl, targetTaskTitle.value)
        context.startActivity(openIntent)
    }

    fun claimMilestoneReward(context: Context) {
        val watchedSecs = (watchedMillis.value / 1000).toInt()
        val requiredSecs = _selectedTierSeconds.value
        val milestone = currentMilestoneTier.value ?: com.example.data.calculateContinuousWatchMilestone(watchedSecs, requiredSecs)

        if (watchedSecs < 180 || milestone == null) {
            val remaining = (180 - watchedSecs).coerceAtLeast(0)
            WatchSessionRepository.addLog(
                "Watched $watchedSecs sec (less than 3 continuous minutes). Need $remaining more seconds to earn coins!",
                LogType.WARNING
            )
            return
        }

        viewModelScope.launch {
            val title = targetTaskTitle.value ?: "YouTube Video Task"
            dataStoreManager.addRewardTransaction("${milestone.minutes}m Continuous Watch: $title", milestone.coins)
            dataStoreManager.setTaskCompleted(true)
            val activeId = selectedTaskId.value ?: WatchSessionRepository.activeTaskId.value
            if (activeId != null) {
                dataStoreManager.markTaskCompleted(activeId, milestone.coins)
            }
            _activeRewardCoins.value = milestone.coins
            _showSuccessDialog.value = true
            WatchSessionRepository.setCompletedState()
            WatchTimerService.stop(context)
            WatchSessionRepository.addLog(
                "🎉 Milestone reward claimed: ${milestone.minutes}m watch time = +${milestone.coins} coins!",
                LogType.SUCCESS
            )
        }
    }

    fun stopTask(context: Context) {
        val milestone = WatchSessionRepository.currentMilestoneTier.value
        val title = WatchSessionRepository.targetTaskTitle.value ?: "YouTube Video Task"
        val activeId = WatchSessionRepository.activeTaskId.value

        if (milestone != null) {
            viewModelScope.launch {
                dataStoreManager.addRewardTransaction("$title (${milestone.minutes}m continuous watch)", milestone.coins)
                dataStoreManager.setTaskCompleted(true)
                if (activeId != null) {
                    dataStoreManager.markTaskCompleted(activeId, milestone.coins)
                }
                _activeRewardCoins.value = milestone.coins
                _showSuccessDialog.value = true
                WatchSessionRepository.addLog(
                    "Watch session ended: Reached ${milestone.minutes}m milestone! Awarded +${milestone.coins} coins.",
                    LogType.SUCCESS
                )
            }
        } else {
            val watchedSec = (WatchSessionRepository.watchedMillis.value / 1000).toInt()
            WatchSessionRepository.addLog(
                "Watch session stopped at ${watchedSec}s. Watched less than 3 minutes minimum continuous requirement. 0 coins awarded.",
                LogType.WARNING
            )
        }

        WatchTimerService.stop(context)
        WatchSessionRepository.resetSession()
    }

    fun withdrawCoins(
        coins: Int,
        method: String,
        destination: String,
        onComplete: (success: Boolean, message: String) -> Unit
    ) {
        if (coins < 50) {
            onComplete(false, "Minimum payout is 50 Coins (₹5.00 INR).")
            return
        }
        if (coins > walletBalance.value) {
            onComplete(false, "Insufficient balance! You have ${walletBalance.value} Coins.")
            return
        }
        if (destination.isBlank()) {
            onComplete(false, "Please enter a valid payout ID or number.")
            return
        }

        viewModelScope.launch {
            val success = dataStoreManager.withdrawCoins(coins, method, destination.trim())
            if (success) {
                val inr = coins / 10.0
                val formatted = String.format(java.util.Locale.US, "%.2f", inr)
                WatchSessionRepository.addLog(
                    "Withdrawal processed: $coins coins (₹$formatted INR) to $method: $destination",
                    LogType.SUCCESS
                )
                onComplete(true, "Successfully withdrawn ₹$formatted INR via $method!")
            } else {
                onComplete(false, "Payout processing failed. Check wallet balance.")
            }
        }
    }

    fun resetAll(context: Context) {
        WatchTimerService.stop(context)
        WatchSessionRepository.resetSession()
        viewModelScope.launch {
            dataStoreManager.resetAll()
            WatchSessionRepository.addLog("Wallet, transactions, and task progress reset to zero.", LogType.INFO)
        }
    }

    fun dismissSuccessDialog() {
        _showSuccessDialog.value = false
    }
}
