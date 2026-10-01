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
import com.example.data.UserProfile
import com.example.data.PayoutStatus
import com.example.data.PayoutRequest
import com.example.data.WatchDurationTier
import com.example.data.WATCH_DURATION_TIERS
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
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
    DIAGNOSTICS,
    ADMIN
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

    val currentUser: StateFlow<UserProfile?> = dataStoreManager.currentUserFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val allUsers: StateFlow<List<UserProfile>> = dataStoreManager.usersFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val payoutRequests: StateFlow<List<PayoutRequest>> = dataStoreManager.payoutRequestsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val cloudServerUrl: StateFlow<String> = dataStoreManager.cloudServerUrlFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val cloudServerStatus: StateFlow<String> = dataStoreManager.cloudServerStatusFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Not Connected (Local Mode)")

    val adminPosts: StateFlow<List< com.example.data.AdminPostItem >> = dataStoreManager.adminPostsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, dataStoreManager.getDefaultAdminPosts())

    val dismissedPostIds: StateFlow<Set<String>> = dataStoreManager.dismissedPostIdsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val taskIncompleteMessage: StateFlow<String?> = WatchSessionRepository.taskIncompleteMessage

    fun dismissTaskIncompleteMessage() {
        WatchSessionRepository.dismissTaskIncompleteMessage()
    }

    private val _adminServerRunning = MutableStateFlow(false)
    val adminServerRunning: StateFlow<Boolean> = _adminServerRunning.asStateFlow()

    private val _adminServerUrl = MutableStateFlow("")
    val adminServerUrl: StateFlow<String> = _adminServerUrl.asStateFlow()

    fun getDataStoreManager(): DataStoreManager = dataStoreManager

    fun updateAdminServerState(running: Boolean, url: String) {
        _adminServerRunning.value = running
        _adminServerUrl.value = url
    }

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

    val likedTasks: StateFlow<Set<String>> = dataStoreManager.likedTasksFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val commentCounts: StateFlow<Map<String, Int>> = dataStoreManager.commentCountsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _sessionInterruptedMessage = MutableStateFlow<String?>(null)
    val sessionInterruptedMessage: StateFlow<String?> = _sessionInterruptedMessage.asStateFlow()

    fun dismissInterruptedMessage() {
        _sessionInterruptedMessage.value = null
    }

    init {
        // Enforce Strict Continuous Watch: Every session must start at 00:00!
        // No incomplete session is accumulated across multiple days or returns.
        WatchSessionRepository.setWatchedMillis(0L)

        // Listen for session interruption when user returns to app before milestone
        WatchSessionRepository.onSessionInterrupted = { message ->
            _sessionInterruptedMessage.value = message
        }

        WatchSessionRepository.onTaskIncompleteAndLocked = { taskId, _, lockDuration ->
            viewModelScope.launch {
                dataStoreManager.lockTask(taskId, lockDuration)
            }
        }

        WatchSessionRepository.onVideoAlreadyLikedDetected = { taskId ->
            viewModelScope.launch {
                dataStoreManager.markTaskAlreadyLiked(taskId)
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

        // Repository completion callback - ONLY shown on genuine completion
        WatchSessionRepository.onCompletionTriggered = { coins, title ->
            viewModelScope.launch {
                dataStoreManager.addRewardTransaction(title, coins)
                val activeId = WatchSessionRepository.activeTaskId.value
                if (activeId != null) {
                    dataStoreManager.markTaskCompleted(activeId, coins)
                }
                _activeRewardCoins.value = coins
                _showSuccessDialog.value = true
            }
        }

        WatchSessionRepository.onSaveProgressNeeded = { millis ->
            viewModelScope.launch {
                dataStoreManager.setWatchedMillis(millis)
            }
        }

        viewModelScope.launch {
            while (true) {
                dataStoreManager.unlockExpiredTasks()
                val url = cloudServerUrl.value
                if (url.isNotBlank()) {
                    try {
                        com.example.admin.CloudDriveServerManager.syncData(url, dataStoreManager)
                    } catch (_: Exception) {}
                }
                delay(15_000L)
            }
        }

        // Real-time watcher for newly added Admin Tasks -> trigger instant User App notification
        viewModelScope.launch {
            val defaultTaskIds = setOf("default_rick", "default_android15", "default_kotlin_course", "default_lofi_live")
            dataStoreManager.videoTasksFlow.collectLatest { tasks ->
                val notified = dataStoreManager.notifiedItemIdsFlow.first()
                val newlyAdded = tasks.filter { t ->
                    !defaultTaskIds.contains(t.id) && !notified.contains(t.id) &&
                            (System.currentTimeMillis() - t.createdAt) < 10 * 60 * 1000L
                }
                if (newlyAdded.isNotEmpty()) {
                    dataStoreManager.markItemsNotified(newlyAdded.map { it.id }.toSet())
                    val newest = newlyAdded.first()
                    com.example.service.NotificationChannels.sendAdminUpdateNotification(
                        context = getApplication(),
                        title = "🎬 New Watch Task Added! (+${newest.rewardCoins} Coins)",
                        body = "\"${newest.title}\" by ${newest.channelName} is now live. Watch & earn coins now!"
                    )
                }
            }
        }

        // Real-time watcher for newly added Admin Posts / Banners / Alerts -> trigger instant User App notification
        viewModelScope.launch {
            val defaultPostIds = setOf("default_welcome_banner")
            dataStoreManager.adminPostsFlow.collectLatest { posts ->
                val notified = dataStoreManager.notifiedItemIdsFlow.first()
                val newlyAdded = posts.filter { p ->
                    !defaultPostIds.contains(p.id) && !notified.contains(p.id) &&
                            (System.currentTimeMillis() - p.createdAt) < 10 * 60 * 1000L
                }
                if (newlyAdded.isNotEmpty()) {
                    dataStoreManager.markItemsNotified(newlyAdded.map { it.id }.toSet())
                    val newest = newlyAdded.first()
                    val prefix = when (newest.postType.uppercase()) {
                        "ALERT" -> "🚨 Urgent Admin Alert"
                        "BANNER" -> "📢 New Offer Banner"
                        else -> "📌 New Admin Post"
                    }
                    com.example.service.NotificationChannels.sendAdminUpdateNotification(
                        context = getApplication(),
                        title = "$prefix: ${newest.title}",
                        body = newest.message.ifBlank { "Tap to view the latest update in ${newest.targetTab} tab!" }
                    )
                }
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
        if (!task.isCompleted) {
            WatchSessionRepository.resetSessionForNewTask(task.id)
        }
        viewModelScope.launch {
            dataStoreManager.setSelectedTaskId(task.id)
            dataStoreManager.setActiveVideoUrl(task.videoUrl)
            fetchOEmbed(task.videoUrl)
        }
    }

    fun addVideoTask(task: VideoTaskItem) {
        val rawClean = TitleMatcher.extractCleanYouTubeUrl(task.videoUrl)
        val cleanUrl = TitleMatcher.cleanYouTubeUrl(rawClean).ifEmpty { rawClean }
        val thumb = task.thumbnailUrl.ifBlank { TitleMatcher.getThumbnailUrl(cleanUrl) ?: "" }
        val cleanedTask = task.copy(videoUrl = cleanUrl, thumbnailUrl = thumb)
        _currentVideoUrl.value = cleanUrl
        _selectedTierSeconds.value = cleanedTask.selectedDurationSeconds
        _selectedTierCoins.value = cleanedTask.rewardCoins

        viewModelScope.launch {
            dataStoreManager.markItemsNotified(setOf(cleanedTask.id))
            dataStoreManager.addVideoTask(cleanedTask)
            dataStoreManager.setSelectedTaskId(cleanedTask.id)
            dataStoreManager.setActiveVideoUrl(cleanUrl)

            var finalTitle = cleanedTask.title
            var finalChannel = cleanedTask.channelName

            if (cleanedTask.title.isBlank() || cleanedTask.title == "YouTube Video" || cleanedTask.title.startsWith("YouTube Video (")) {
                val result = OEmbedFetcher.fetchOEmbed(cleanUrl)
                if (result is OEmbedResult.Success) {
                    _oEmbedState.value = result
                    finalTitle = result.title
                    finalChannel = result.authorName.ifBlank { cleanedTask.channelName }
                    val updated = cleanedTask.copy(
                        title = finalTitle,
                        channelName = finalChannel,
                        thumbnailUrl = result.thumbnailUrl.ifBlank { cleanedTask.thumbnailUrl }
                    )
                    dataStoreManager.updateVideoTask(updated)
                }
            } else {
                _oEmbedState.value = OEmbedResult.Success(
                    title = cleanedTask.title,
                    authorName = cleanedTask.channelName,
                    authorUrl = "",
                    thumbnailUrl = cleanedTask.thumbnailUrl
                )
            }
            com.example.service.NotificationChannels.sendAdminUpdateNotification(
                context = getApplication(),
                title = "🎬 New Video Task Live! (+${cleanedTask.rewardCoins} Coins)",
                body = "Watch \"$finalTitle\" ($finalChannel) & earn up to 110 coins + Like/Comment bonus!"
            )
            val url = cloudServerUrl.value
            if (url.isNotBlank()) {
                try {
                    com.example.admin.CloudDriveServerManager.syncData(url, dataStoreManager)
                } catch (_: Exception) {}
            }
            WatchSessionRepository.addLog("Created new video task: \"$finalTitle\"", LogType.SUCCESS)
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
        val rawClean = TitleMatcher.extractCleanYouTubeUrl(url)
        val cleanUrl = TitleMatcher.cleanYouTubeUrl(rawClean).ifEmpty { rawClean }
        _currentVideoUrl.value = cleanUrl
        _oEmbedState.value = OEmbedResult.Loading
        viewModelScope.launch {
            dataStoreManager.setActiveVideoUrl(cleanUrl)
            val result = OEmbedFetcher.fetchOEmbed(cleanUrl)
            _oEmbedState.value = result
            val thumb = (result as? OEmbedResult.Success)?.thumbnailUrl?.ifBlank { null }
                ?: TitleMatcher.getThumbnailUrl(cleanUrl)
                ?: ""
            val vid = TitleMatcher.extractVideoId(cleanUrl)
            val resolvedTitle = (result as? OEmbedResult.Success)?.title
                ?: if (!vid.isNullOrBlank()) "YouTube Video ($vid)" else "YouTube Video Task"
            val resolvedAuthor = (result as? OEmbedResult.Success)?.authorName?.ifBlank { null }
                ?: "YouTube Creator"

            val activeId = selectedTaskId.value
            val existingTask = videoTasks.value.find { it.id == activeId }
            if (existingTask != null) {
                val updatedTask = existingTask.copy(
                    title = resolvedTitle,
                    channelName = resolvedAuthor,
                    videoUrl = cleanUrl,
                    thumbnailUrl = thumb,
                    isCompleted = false,
                    lockedUntilMillis = 0L
                )
                dataStoreManager.updateVideoTask(updatedTask)
            } else {
                val newTask = VideoTaskItem(
                    id = "task_${System.currentTimeMillis()}",
                    title = resolvedTitle,
                    channelName = resolvedAuthor,
                    videoUrl = cleanUrl,
                    thumbnailUrl = thumb,
                    durationSeconds = 600,
                    rewardCoins = _selectedTierCoins.value,
                    selectedDurationSeconds = _selectedTierSeconds.value
                )
                dataStoreManager.addVideoTask(newTask)
                dataStoreManager.setSelectedTaskId(newTask.id)
            }
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

    fun signUp(email: String, password: String, name: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = dataStoreManager.signUpUser(email, password, name)
            onResult(res.first, res.second)
            if (res.first) {
                WatchSessionRepository.addLog("User signed up: $email", LogType.SUCCESS)
            }
        }
    }

    fun login(email: String, password: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = dataStoreManager.loginUser(email, password)
            onResult(res.first, res.second)
            if (res.first) {
                WatchSessionRepository.addLog("User logged in: $email", LogType.SUCCESS)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            dataStoreManager.logoutUser()
            WatchSessionRepository.addLog("User logged out", LogType.INFO)
        }
    }

    fun requestWithdrawal(coins: Int, method: String, destination: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val success = dataStoreManager.withdrawCoins(coins, method, destination)
            if (success) {
                onResult(true, "Payout request submitted! Admin will verify and process.")
                WatchSessionRepository.addLog("Payout request created: $coins coins to $method ($destination)", LogType.INFO)
            } else {
                onResult(false, "Insufficient balance or invalid coins amount.")
            }
        }
    }

    fun approvePayout(requestId: String, note: String = "Approved & Dispatched") {
        viewModelScope.launch {
            dataStoreManager.approvePayout(requestId, note)
            WatchSessionRepository.addLog("Admin: Payout approved for request #$requestId", LogType.SUCCESS)
        }
    }

    fun rejectPayout(requestId: String, reason: String = "Declined by Admin") {
        viewModelScope.launch {
            dataStoreManager.rejectPayout(requestId, reason)
            WatchSessionRepository.addLog("Admin: Payout rejected ($reason). Coins refunded.", LogType.WARNING)
        }
    }

    fun adminDeleteTask(taskId: String) {
        viewModelScope.launch {
            dataStoreManager.adminDeleteVideoTask(taskId)
            val url = cloudServerUrl.value
            if (url.isNotBlank()) {
                try {
                    com.example.admin.CloudDriveServerManager.syncData(url, dataStoreManager)
                } catch (_: Exception) {}
            }
            WatchSessionRepository.addLog("Admin: Deleted task #$taskId", LogType.INFO)
        }
    }

    fun togglePinVideoTask(taskId: String) {
        viewModelScope.launch {
            val isPinned = dataStoreManager.togglePinVideoTask(taskId)
            val url = cloudServerUrl.value
            if (url.isNotBlank()) {
                try {
                    com.example.admin.CloudDriveServerManager.syncData(url, dataStoreManager)
                } catch (_: Exception) {}
            }
            WatchSessionRepository.addLog(
                if (isPinned) "Admin: Pinned task #$taskId to top" else "Admin: Unpinned task #$taskId",
                LogType.INFO
            )
        }
    }

    fun addAdminPost(
        title: String,
        message: String,
        targetTab: String,
        postType: String,
        actionUrl: String = "",
        imageUrl: String = "",
        isPinned: Boolean = false
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val post = com.example.data.AdminPostItem(
                id = "post_${now}",
                title = title.trim(),
                message = message.trim(),
                targetTab = targetTab.uppercase(),
                postType = postType.uppercase(),
                actionUrl = actionUrl.trim(),
                imageUrl = imageUrl.trim(),
                createdAt = now,
                isPinned = isPinned,
                pinnedAt = if (isPinned) now else 0L
            )
            dataStoreManager.markItemsNotified(setOf(post.id))
            dataStoreManager.addAdminPost(post)

            val prefix = when (post.postType) {
                "ALERT" -> "🚨 Urgent Admin Alert"
                "BANNER" -> "📢 New Offer Banner"
                else -> "📌 New Admin Post"
            }
            com.example.service.NotificationChannels.sendAdminUpdateNotification(
                context = getApplication(),
                title = "$prefix: ${post.title}",
                body = post.message.ifBlank { "New update posted in ${post.targetTab} tab!" }
            )

            val url = cloudServerUrl.value
            if (url.isNotBlank()) {
                try {
                    com.example.admin.CloudDriveServerManager.syncData(url, dataStoreManager)
                } catch (_: Exception) {}
            }
            WatchSessionRepository.addLog("Admin published ${post.postType} to ${post.targetTab}: \"${post.title}\"", LogType.SUCCESS)
        }
    }

    fun togglePinAdminPost(postId: String) {
        viewModelScope.launch {
            val isPinned = dataStoreManager.togglePinAdminPost(postId)
            val url = cloudServerUrl.value
            if (url.isNotBlank()) {
                try {
                    com.example.admin.CloudDriveServerManager.syncData(url, dataStoreManager)
                } catch (_: Exception) {}
            }
            WatchSessionRepository.addLog(
                if (isPinned) "Admin: Pinned post #$postId to top" else "Admin: Unpinned post #$postId",
                LogType.INFO
            )
        }
    }

    fun deleteAdminPost(postId: String) {
        viewModelScope.launch {
            dataStoreManager.deleteAdminPost(postId)
            val url = cloudServerUrl.value
            if (url.isNotBlank()) {
                try {
                    com.example.admin.CloudDriveServerManager.syncData(url, dataStoreManager)
                } catch (_: Exception) {}
            }
            WatchSessionRepository.addLog("Admin deleted post #$postId", LogType.INFO)
        }
    }

    fun dismissAdminPost(postId: String) {
        viewModelScope.launch {
            dataStoreManager.dismissAdminPost(postId)
        }
    }

    fun adminUpdateUserCoins(userEmail: String, newCoins: Int) {
        viewModelScope.launch {
            dataStoreManager.adminUpdateUserCoins(userEmail, newCoins)
            WatchSessionRepository.addLog("Admin: Updated coins to $newCoins for $userEmail", LogType.SUCCESS)
        }
    }

    fun likeTask(taskId: String, taskTitle: String, onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            val res = dataStoreManager.recordTaskLike(taskId, taskTitle)
            onResult?.invoke(res.first, res.second)
            if (res.first) {
                WatchSessionRepository.addLog("Liked video \"$taskTitle\": +5 coins rewarded!", LogType.SUCCESS)
            }
        }
    }

    fun commentTask(taskId: String, taskTitle: String, onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            val res = dataStoreManager.recordTaskComment(taskId, taskTitle)
            onResult?.invoke(res.first, res.second)
            if (res.first) {
                WatchSessionRepository.addLog("Comment on \"$taskTitle\": +5 coins rewarded!", LogType.SUCCESS)
            }
        }
    }

    fun lockTask(taskId: String, durationMillis: Long = 12 * 60 * 60 * 1000L) {
        viewModelScope.launch {
            dataStoreManager.lockTask(taskId, durationMillis)
            WatchSessionRepository.addLog("Task #$taskId locked for 12 hours due to incomplete watch.", LogType.WARNING)
        }
    }

    fun unlockTask(taskId: String) {
        viewModelScope.launch {
            dataStoreManager.unlockTask(taskId)
            WatchSessionRepository.addLog("Task #$taskId unlocked.", LogType.SUCCESS)
        }
    }

    fun saveCloudServerUrl(url: String) {
        viewModelScope.launch {
            dataStoreManager.setCloudServerUrl(url)
            WatchSessionRepository.addLog("Saved Google Drive Server URL: $url", LogType.INFO)
        }
    }

    fun syncWithGoogleDriveServer(onResult: (Boolean, String) -> Unit) {
        val url = cloudServerUrl.value
        if (url.isBlank()) {
            onResult(false, "Please enter your Google Drive Web App URL first.")
            return
        }
        viewModelScope.launch {
            val res = com.example.admin.CloudDriveServerManager.syncData(url, dataStoreManager)
            onResult(res.first, res.second)
            if (res.first) {
                WatchSessionRepository.addLog("Sync with Google Drive successful!", LogType.SUCCESS)
            } else {
                WatchSessionRepository.addLog("Drive sync error: ${res.second}", LogType.ERROR)
            }
        }
    }

    fun testGoogleDriveConnection(onResult: (Boolean, String) -> Unit) {
        val url = cloudServerUrl.value
        if (url.isBlank()) {
            onResult(false, "Please enter your Google Drive Web App URL first.")
            return
        }
        viewModelScope.launch {
            val res = com.example.admin.CloudDriveServerManager.testConnection(url)
            onResult(res.first, res.second)
            if (res.first) {
                dataStoreManager.setCloudServerStatus("Connected to Google Drive")
                WatchSessionRepository.addLog("Google Drive connection verified!", LogType.SUCCESS)
            } else {
                dataStoreManager.setCloudServerStatus("Connection Failed")
                WatchSessionRepository.addLog("Google Drive connection failed: ${res.second}", LogType.WARNING)
            }
        }
    }

    private fun startTaskInternal(
        context: Context,
        videoUrl: String,
        requiredSeconds: Int,
        rewardCoins: Int,
        taskId: String?
    ) {
        if (searchProgress.value.isSearching) {
            return // Avoid duplicate search clicks
        }

        val resolvedTaskId = taskId
            ?: selectedTaskId.value
            ?: videoTasks.value.find { it.videoUrl == videoUrl }?.id
            ?: videoTasks.value.firstOrNull()?.id
            ?: "default_task"

        // Check if task is currently locked (8h for completed, 12h for incomplete)
        val currentTask = videoTasks.value.find { it.id == resolvedTaskId }
        if (currentTask != null && currentTask.isLocked) {
            val remainStr = currentTask.getLockRemainingFormatted()
            if (currentTask.isCompleted) {
                WatchSessionRepository.showTaskIncompleteMessage(
                    "This task is completed and locked for 8 hours ($remainStr remaining)."
                )
            } else {
                WatchSessionRepository.showTaskIncompleteMessage(
                    "This task is locked for 12 hours ($remainStr remaining) due to an incomplete session."
                )
            }
            return
        }

        viewModelScope.launch {
            // Strict Continuous Watch Rule: Every session starts strictly at 00:00!
            dataStoreManager.setWatchedMillis(0L)
            WatchSessionRepository.setWatchedMillis(0L)

            val effectiveUrl = if (videoUrl == "PASTE_MY_YOUTUBE_LINK_HERE" || !videoUrl.startsWith("http")) {
                SampleTask.fallbackDemoUrl
            } else {
                videoUrl
            }

            // Ensure oEmbed metadata is loaded for the exact target URL
            val fetchedOEmbed = OEmbedFetcher.fetchOEmbed(effectiveUrl)
            if (fetchedOEmbed is OEmbedResult.Success) {
                _oEmbedState.value = fetchedOEmbed
            }

            val taskTitleCandidate = currentTask?.title?.takeIf {
                it.isNotBlank() && it != "YouTube Video" && !it.startsWith("YouTube Video (") && it != "YouTube Video Task"
            }
            val taskChannelCandidate = currentTask?.channelName?.takeIf {
                it.isNotBlank() && it != "YouTube Creator" && it != "YouTube Channel"
            }

            val oEmbedSuccess = (fetchedOEmbed as? OEmbedResult.Success) ?: (_oEmbedState.value as? OEmbedResult.Success)
            val oEmbedTitleCandidate = oEmbedSuccess?.title?.takeIf {
                it.isNotBlank() && it != "YouTube Video" && !it.startsWith("YouTube Video (")
            }
            val oEmbedAuthorCandidate = oEmbedSuccess?.authorName?.takeIf {
                it.isNotBlank() && it != "YouTube Creator" && it != "YouTube Channel"
            }

            val title: String = oEmbedTitleCandidate ?: taskTitleCandidate ?: currentTask?.title ?: "YouTube Video Task"
            val author: String = oEmbedAuthorCandidate ?: taskChannelCandidate ?: currentTask?.channelName ?: ""

            if (currentTask != null && oEmbedTitleCandidate != null && taskTitleCandidate == null) {
                dataStoreManager.updateVideoTask(
                    currentTask.copy(
                        title = oEmbedTitleCandidate,
                        channelName = oEmbedAuthorCandidate ?: currentTask.channelName
                    )
                )
            }

            val targetVideoId = TitleMatcher.extractVideoId(effectiveUrl)

            if (liveSearchMode.value) {
                // LIVE MODE: Directly opens YouTube with the target title searched!
                WatchSessionRepository.updateSearchProgress(com.example.data.SearchProgressState(isSearching = false))
                WatchSessionRepository.addLog("Live Mode: Opening YouTube search for \"$title\"", LogType.INFO)

                // Arm the accessibility trigger to auto-type in search bar and click target video card
                YouTubeLiveSearchService.armSearchTrigger(title, author, effectiveUrl, targetVideoId)

                WatchSessionRepository.startTask(
                    taskTitle = title,
                    taskAuthor = author,
                    requiredSeconds = requiredSeconds,
                    initialWatchedMillis = 0L,
                    rewardCoins = rewardCoins,
                    taskId = resolvedTaskId
                )
                WatchTimerService.start(context)

                // Open YouTube with the exact search query intent
                val ytSearchIntent = PermissionHelper.openYouTubeSearchIntent(context, title)
                try {
                    context.startActivity(ytSearchIntent)
                } catch (_: Exception) {
                    val fallbackIntent = PermissionHelper.openVideoIntent(context, effectiveUrl, title)
                    context.startActivity(fallbackIntent)
                }

                WatchSessionRepository.addLog(
                    "YouTube opened for \"$title\"! Auto-searching and locating target video card...",
                    LogType.SUCCESS
                )
            } else {
                // DEFAULT SIMULATION MODE: Shows in-app typewriter/radar loading screen
                val foundItem = YouTubeSearchEngine.searchAndLocateVideo(
                    targetTitle = title,
                    targetChannel = author,
                    targetVideoId = targetVideoId
                ) { progressState ->
                    WatchSessionRepository.updateSearchProgress(progressState)
                }

                val targetUrlToLaunch = if (foundItem != null && foundItem.videoId.isNotEmpty()) {
                    "https://www.youtube.com/watch?v=${foundItem.videoId}"
                } else {
                    effectiveUrl
                }

                YouTubeLiveSearchService.prepareForDirectWatch(
                    title = title,
                    channel = author,
                    videoUrl = targetUrlToLaunch,
                    videoId = targetVideoId
                )

                WatchSessionRepository.startTask(
                    taskTitle = title,
                    taskAuthor = author,
                    requiredSeconds = requiredSeconds,
                    initialWatchedMillis = 0L,
                    rewardCoins = rewardCoins,
                    taskId = resolvedTaskId
                )

                WatchTimerService.start(context)

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
        if (coins < 1000) {
            onComplete(false, "Minimum payout is 1000 Coins (₹10.00 INR).")
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
                val inr = coins.toDouble() / com.example.data.COINS_PER_INR.toDouble()
                val formatted = String.format(java.util.Locale.US, "%.2f", inr)
                WatchSessionRepository.addLog(
                    "Withdrawal request submitted: $coins coins (₹$formatted INR) to $method: $destination",
                    LogType.SUCCESS
                )
                onComplete(true, "Payout request for ₹$formatted INR via $method submitted! It has been sent to the Admin Panel for approval.")
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
