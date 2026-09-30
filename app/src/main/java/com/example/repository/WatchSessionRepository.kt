package com.example.repository

import android.media.session.PlaybackState
import android.os.SystemClock
import com.example.data.LogEvent
import com.example.data.LogType
import com.example.data.MatchResult
import com.example.data.SessionState
import com.example.data.VideoPlaybackState
import com.example.util.TimeFormatter
import com.example.util.TitleMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

object WatchSessionRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // State flows
    private val _sessionState = MutableStateFlow(SessionState.IDLE)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    @Volatile
    var isAppInForeground: Boolean = false
        private set

    private val _matchResult = MutableStateFlow(MatchResult.UNKNOWN)
    val matchResult: StateFlow<MatchResult> = _matchResult.asStateFlow()

    private val _playbackState = MutableStateFlow(VideoPlaybackState.NONE)
    val playbackState: StateFlow<VideoPlaybackState> = _playbackState.asStateFlow()

    private val _currentMediaTitle = MutableStateFlow<String?>(null)
    val currentMediaTitle: StateFlow<String?> = _currentMediaTitle.asStateFlow()

    private val _currentMediaArtist = MutableStateFlow<String?>(null)
    val currentMediaArtist: StateFlow<String?> = _currentMediaArtist.asStateFlow()

    private val _targetTaskTitle = MutableStateFlow<String?>(null)
    val targetTaskTitle: StateFlow<String?> = _targetTaskTitle.asStateFlow()

    private val _targetTaskAuthor = MutableStateFlow<String?>(null)
    val targetTaskAuthor: StateFlow<String?> = _targetTaskAuthor.asStateFlow()

    private val _rewardCoins = MutableStateFlow(10)
    val rewardCoins: StateFlow<Int> = _rewardCoins.asStateFlow()

    private val _activeTaskId = MutableStateFlow<String?>(null)
    val activeTaskId: StateFlow<String?> = _activeTaskId.asStateFlow()

    private val _currentMilestoneTier = MutableStateFlow<com.example.data.WatchDurationTier?>(null)
    val currentMilestoneTier: StateFlow<com.example.data.WatchDurationTier?> = _currentMilestoneTier.asStateFlow()

    private val _watchedMillis = MutableStateFlow(0L)
    val watchedMillis: StateFlow<Long> = _watchedMillis.asStateFlow()

    private val _requiredMillis = MutableStateFlow(180_000L)
    val requiredMillis: StateFlow<Long> = _requiredMillis.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _isGracePeriodActive = MutableStateFlow(false)
    val isGracePeriodActive: StateFlow<Boolean> = _isGracePeriodActive.asStateFlow()

    private val _searchProgress = MutableStateFlow(com.example.data.SearchProgressState())
    val searchProgress: StateFlow<com.example.data.SearchProgressState> = _searchProgress.asStateFlow()

    private val _graceSecondsRemaining = MutableStateFlow(10)
    val graceSecondsRemaining: StateFlow<Int> = _graceSecondsRemaining.asStateFlow()

    private val _mediaSessionDetected = MutableStateFlow(false)
    val mediaSessionDetected: StateFlow<Boolean> = _mediaSessionDetected.asStateFlow()

    private val _redAlertMessage = MutableStateFlow<String?>(null)
    val redAlertMessage: StateFlow<String?> = _redAlertMessage.asStateFlow()

    private val _eventLogs = MutableStateFlow<List<LogEvent>>(emptyList())
    val eventLogs: StateFlow<List<LogEvent>> = _eventLogs.asStateFlow()

    private val logIdGenerator = AtomicLong(0)

    // Internal timing
    private var lastTickRealtime: Long = 0L
    private var graceJob: Job? = null
    private var waitingTimeoutJob: Job? = null
    private var isMilestoneAwarded: Boolean = false

    // Listener for service notification triggers
    var onRedAlertTriggered: ((title: String, message: String) -> Unit)? = null
    var onCompletionTriggered: ((coins: Int, title: String) -> Unit)? = null
    var onSaveProgressNeeded: ((millis: Long) -> Unit)? = null
    var onTaskLikeDetected: (() -> Unit)? = null
    var onVideoAlreadyLikedDetected: ((taskId: String) -> Unit)? = null
    var onTaskCommentDetected: (() -> Unit)? = null
    var onSessionInterrupted: ((reason: String) -> Unit)? = null
    var onTaskIncompleteAndLocked: ((taskId: String, reason: String, lockDurationMillis: Long) -> Unit)? = null
    var onMilestoneCoinsAwarded: ((coins: Int, title: String) -> Unit)? = null

    fun triggerTaskLike() {
        onTaskLikeDetected?.invoke()
    }

    fun triggerTaskComment() {
        onTaskCommentDetected?.invoke()
    }

    init {
        addLog("WatchEarn engine initialized", LogType.INFO)
    }

    fun addLog(message: String, type: LogType = LogType.INFO) {
        val event = LogEvent(
            id = logIdGenerator.incrementAndGet(),
            timestamp = TimeFormatter.formatTimestamp(System.currentTimeMillis()),
            message = message,
            type = type
        )
        val current = _eventLogs.value.toMutableList()
        current.add(0, event)
        // Keep last 30 log events
        if (current.size > 30) {
            _eventLogs.value = current.take(30)
        } else {
            _eventLogs.value = current
        }
    }

    fun updateSearchProgress(state: com.example.data.SearchProgressState) {
        _searchProgress.value = state
        if (state.isSearching && state.stepText.isNotBlank()) {
            addLog(state.stepText, LogType.INFO)
        }
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
        if (running) {
            addLog("Foreground timer service started", LogType.INFO)
        } else {
            addLog("Foreground timer service stopped", LogType.INFO)
        }
    }

    /**
     * Called when the user presses "Start Task"
     */
    fun startTask(
        taskTitle: String,
        taskAuthor: String,
        requiredSeconds: Int,
        initialWatchedMillis: Long = 0L,
        rewardCoins: Int = 10,
        taskId: String? = null
    ) {
        _targetTaskTitle.value = taskTitle
        _targetTaskAuthor.value = taskAuthor
        _requiredMillis.value = requiredSeconds * 1000L
        // Strict Continuous Watch Rule: Every session starts strictly from 0!
        _watchedMillis.value = 0L
        _currentMilestoneTier.value = null
        isMilestoneAwarded = false
        _rewardCoins.value = rewardCoins
        _activeTaskId.value = taskId
        _redAlertMessage.value = null
        _isGracePeriodActive.value = false
        _currentMediaTitle.value = taskTitle
        _currentMediaArtist.value = taskAuthor
        _matchResult.value = MatchResult.MATCH
        _playbackState.value = VideoPlaybackState.PAUSED
        _mediaSessionDetected.value = true
        _sessionState.value = SessionState.ACTIVE
        com.example.service.YouTubeLiveSearchService.isYouTubeInForeground = true

        addLog("Task started. Target: \"$taskTitle\". Continuous watch session initialized from 00:00!", LogType.SUCCESS)

        waitingTimeoutJob?.cancel()
    }

    /**
     * Resets any stale completed/error session state when switching to a different task
     */
    fun resetSessionForNewTask(taskId: String? = null) {
        _sessionState.value = SessionState.IDLE
        _watchedMillis.value = 0L
        _currentMilestoneTier.value = null
        isMilestoneAwarded = false
        _activeTaskId.value = taskId
        _redAlertMessage.value = null
        _isGracePeriodActive.value = false
        lastTickRealtime = 0L
        waitingTimeoutJob?.cancel()
        graceJob?.cancel()
    }

    /**
     * Called by WatchListenerService when YouTube media metadata changes
     */
    fun onMediaMetadataChanged(title: String?, artist: String?) {
        _mediaSessionDetected.value = true
        _currentMediaTitle.value = title
        _currentMediaArtist.value = artist

        addLog("YouTube metadata: \"$title\" by \"$artist\"", LogType.INFO)
        recomputeMatchAndState()
    }

    /**
     * Called by WatchListenerService when YouTube playback state changes
     */
    fun onPlaybackStateChanged(playbackStateInt: Int?) {
        _mediaSessionDetected.value = true
        val state = when (playbackStateInt) {
            PlaybackState.STATE_PLAYING -> VideoPlaybackState.PLAYING
            PlaybackState.STATE_PAUSED -> VideoPlaybackState.PAUSED
            PlaybackState.STATE_BUFFERING -> VideoPlaybackState.BUFFERING
            PlaybackState.STATE_STOPPED -> VideoPlaybackState.STOPPED
            else -> VideoPlaybackState.NONE
        }

        _playbackState.value = state
        addLog("YouTube playback state: $state", LogType.INFO)

        if (state != VideoPlaybackState.PLAYING) {
            // Paused/Buffering/Stopped: reset last tick so paused time is never counted
            lastTickRealtime = 0L
        }

        recomputeMatchAndState()
    }

    /**
     * Fallback signal from notification posted
     */
    fun onYouTubeNotificationPosted(title: String?, text: String?) {
        _mediaSessionDetected.value = true
        if (_currentMediaTitle.value.isNullOrBlank() && !title.isNullOrBlank()) {
            _currentMediaTitle.value = title
            if (!text.isNullOrBlank()) {
                _currentMediaArtist.value = text
            }
            addLog("YouTube notification detected: \"$title\"", LogType.INFO)
            recomputeMatchAndState()
        }
    }

    fun onSessionDestroyed() {
        addLog("YouTube media session destroyed", LogType.WARNING)
        _mediaSessionDetected.value = false
        _playbackState.value = VideoPlaybackState.NONE
        lastTickRealtime = 0L
        recomputeMatchAndState()
    }

    /**
     * Evaluates match between current playing video and task target
     */
    private fun recomputeMatchAndState() {
        val target = _targetTaskTitle.value
        val author = _targetTaskAuthor.value
        val playingTitle = _currentMediaTitle.value
        val playingArtist = _currentMediaArtist.value

        if (target.isNullOrBlank()) {
            _matchResult.value = MatchResult.UNKNOWN
            return
        }

        val match = TitleMatcher.evaluateMatch(
            playingTitle = playingTitle,
            taskTitle = target,
            playingArtist = playingArtist,
            taskAuthor = author
        )
        _matchResult.value = match

        val currentState = _sessionState.value
        if (currentState == SessionState.COMPLETED || currentState == SessionState.INVALID) {
            return
        }

        val isPlaying = _playbackState.value == VideoPlaybackState.PLAYING

        when (match) {
            MatchResult.MATCH -> {
                // Cancel any pending grace period because title matches!
                if (_isGracePeriodActive.value) {
                    cancelGracePeriod("Matching video restored. Grace period cancelled.")
                }

                if (currentState == SessionState.WAITING && isPlaying) {
                    waitingTimeoutJob?.cancel()
                    _sessionState.value = SessionState.ACTIVE
                    addLog("Target video verified! Watching session ACTIVE.", LogType.SUCCESS)
                }
            }

            MatchResult.MISMATCH -> {
                val detected = _currentMediaTitle.value ?: ""
                val isLikelyAd = detected.contains("Ad", ignoreCase = true) || 
                                 detected.contains("Sponsored", ignoreCase = true) || 
                                 detected.length < 4

                if (isLikelyAd) {
                    // Pre-roll ad or sponsor: pause timer progress so ad time is not counted
                    lastTickRealtime = 0L
                    addLog("Pre-roll ad or sponsor detected (\"$detected\"). Timer paused until target video plays.", LogType.INFO)
                } else if (isPlaying && currentState == SessionState.ACTIVE) {
                    // User played a DIFFERENT video!
                    // Strictly cancel session, reset time to 0, and lock task for 12 hours!
                    val wrongTitle = detected.ifBlank { "Doosra video" }
                    val message = "⚠️ Wrong Video Detected: Aapne doosra video open kiya (\"$wrongTitle\"). Sirf task targeted video dekhne par hi coins milte hain. Continuous watch cancel ho gayi, time reset ho gaya aur task 12 ghante ke liye lock ho gaya."
                    addLog(message, LogType.ERROR)

                    _watchedMillis.value = 0L
                    _currentMilestoneTier.value = null
                    lastTickRealtime = 0L
                    onSaveProgressNeeded?.invoke(0L)
                    _sessionState.value = SessionState.INVALID

                    val activeId = _activeTaskId.value
                    if (activeId != null) {
                        onTaskIncompleteAndLocked?.invoke(activeId, message, 12 * 60 * 60 * 1000L)
                    }
                    onRedAlertTriggered?.invoke("Wrong Video Played", message)
                }
            }

            MatchResult.UNKNOWN -> {
                // Keep waiting or paused
            }
        }
    }

    /**
     * Triggered immediately when user switches to another app or minimizes YouTube
     */
    fun onAppSwitchedOrMinimized() {
        if (_sessionState.value == SessionState.ACTIVE) {
            val watchedSecs = (_watchedMillis.value / 1000).toInt()
            val requiredSecs = (_requiredMillis.value / 1000).toInt()
            val activeId = _activeTaskId.value
            val message = "⚠️ Task Incomplete: Aapne YouTube minimize ya doosri app mein switch kar diya (${watchedSecs}s / ${requiredSecs}s). Continuous watch break ho gayi aur yeh task 12 ghante ke liye lock ho gaya hai."
            addLog(message, LogType.WARNING)

            _sessionState.value = SessionState.INVALID
            _watchedMillis.value = 0L
            _currentMilestoneTier.value = null
            lastTickRealtime = 0L
            onSaveProgressNeeded?.invoke(0L)

            if (activeId != null) {
                onTaskIncompleteAndLocked?.invoke(activeId, message, 12 * 60 * 60 * 1000L)
            }
            onSessionInterrupted?.invoke(message)
        }
    }

    private fun cancelGracePeriod(reason: String) {
        graceJob?.cancel()
        _isGracePeriodActive.value = false
        _graceSecondsRemaining.value = 10
        addLog(reason, LogType.INFO)
    }

    fun setAppInForeground(inForeground: Boolean) {
        if (isAppInForeground != inForeground) {
            isAppInForeground = inForeground
            if (inForeground) {
                com.example.service.YouTubeLiveSearchService.isYouTubeInForeground = false
                _playbackState.value = VideoPlaybackState.PAUSED
                lastTickRealtime = 0L

                val watchedSecs = (_watchedMillis.value / 1000).toInt()
                val requiredSecs = (_requiredMillis.value / 1000).toInt()
                val activeId = _activeTaskId.value
                if (_sessionState.value == SessionState.ACTIVE) {
                    if (watchedSecs < 180 || _watchedMillis.value < _requiredMillis.value) {
                        val milestone = _currentMilestoneTier.value
                        val message = if (milestone != null) {
                            "⚠️ Milestone of ${milestone.minutes}m was reached (+${milestone.coins}c), but you returned before completing the full goal (${watchedSecs}s / ${requiredSecs}s). This task is now locked for 12 hours."
                        } else {
                            "⚠️ Task Incomplete: You watched only ${watchedSecs}s out of ${requiredSecs}s (minimum 3 minutes required). Continuous watch was broken. This task is now locked for 12 hours."
                        }
                        addLog(message, LogType.WARNING)
                        _sessionState.value = SessionState.IDLE
                        _watchedMillis.value = 0L
                        _currentMilestoneTier.value = null
                        onSaveProgressNeeded?.invoke(0L)
                        if (activeId != null) {
                            onTaskIncompleteAndLocked?.invoke(activeId, message, 12 * 60 * 60 * 1000L)
                        }
                        onSessionInterrupted?.invoke(message)
                    }
                } else {
                    addLog("App opened in foreground", LogType.INFO)
                }
            } else {
                addLog("Switched out of app: Ready for video watch tracking", LogType.INFO)
            }
        }
    }

    fun setPlaybackPlaying(isPlaying: Boolean) {
        val newState = if (isPlaying) VideoPlaybackState.PLAYING else VideoPlaybackState.PAUSED
        if (_playbackState.value != newState) {
            _playbackState.value = newState
            if (!isPlaying) {
                lastTickRealtime = 0L
            }
        }
    }

    /**
     * Timer tick loop called from foreground service or ViewModel every ~500ms
     */
    fun processTimerTick() {
        val state = _sessionState.value
        if (state != SessionState.ACTIVE || isAppInForeground) {
            lastTickRealtime = 0L
            if (isAppInForeground && _playbackState.value == VideoPlaybackState.PLAYING) {
                _playbackState.value = VideoPlaybackState.PAUSED
            }
            return
        }

        val isPlaying = _playbackState.value == VideoPlaybackState.PLAYING
        val isMatched = _matchResult.value != MatchResult.MISMATCH

        if (isPlaying && isMatched && !_isGracePeriodActive.value) {
            val now = SystemClock.elapsedRealtime()
            if (lastTickRealtime > 0L) {
                val delta = now - lastTickRealtime
                // Guard against sudden clock jumps
                if (delta in 1..2000) {
                    val newWatched = _watchedMillis.value + delta
                    _watchedMillis.value = newWatched
                    onSaveProgressNeeded?.invoke(newWatched)

                    // Track continuous milestones live
                    val watchedSecs = (newWatched / 1000).toInt()
                    val goalSecs = (_requiredMillis.value / 1000).toInt()
                    val achieved = com.example.data.calculateContinuousWatchMilestone(watchedSecs, goalSecs)
                    if (achieved != _currentMilestoneTier.value) {
                        _currentMilestoneTier.value = achieved
                        if (achieved != null) {
                            addLog("🎉 Milestone Reached: ${achieved.minutes}m continuous watch (+${achieved.coins} coins unlocked)!", LogType.SUCCESS)
                            onMilestoneCoinsAwarded?.invoke(achieved.coins, "${achieved.minutes}m Milestone (+${achieved.coins}c)")
                        }
                    }

                    // Check completion
                    if (newWatched >= _requiredMillis.value) {
                        completeTask()
                    }
                }
            }
            lastTickRealtime = now
        } else {
            // Not playing or not matched -> pause timer tick delta
            lastTickRealtime = 0L
        }
    }

    private fun completeTask() {
        val watchedSecs = (_watchedMillis.value / 1000).toInt()
        val goalSecs = (_requiredMillis.value / 1000).toInt()
        val milestone = _currentMilestoneTier.value ?: com.example.data.calculateContinuousWatchMilestone(watchedSecs, goalSecs)

        // Strict milestone validation: must have watched for at least 180 continuous seconds
        if (watchedSecs < 180 || milestone == null) {
            addLog("Task stopped: Watched $watchedSecs s (less than 3 continuous minutes required). No coins rewarded.", LogType.WARNING)
            _sessionState.value = SessionState.IDLE
            _watchedMillis.value = 0L
            _currentMilestoneTier.value = null
            lastTickRealtime = 0L
            onSaveProgressNeeded?.invoke(0L)
            return
        }

        if (isMilestoneAwarded) {
            return // Prevent duplicate coin addition
        }
        isMilestoneAwarded = true

        _sessionState.value = SessionState.COMPLETED
        _isGracePeriodActive.value = false
        lastTickRealtime = 0L
        waitingTimeoutJob?.cancel()
        graceJob?.cancel()

        val earned = milestone.coins
        addLog("🎉 Task complete! Milestone reached: ${milestone.minutes}m continuous watch. +$earned coins rewarded.", LogType.SUCCESS)
        onMilestoneCoinsAwarded?.invoke(earned, "Task Completed (+${earned}c)")
        onCompletionTriggered?.invoke(earned, _targetTaskTitle.value ?: "Video Task")
    }

    fun abortOrStopSession() {
        val watchedSecs = (_watchedMillis.value / 1000).toInt()
        if (watchedSecs < 180) {
            addLog("Session aborted: Watched $watchedSecs s (did not reach 3 min continuous milestone). Reset to 00:00.", LogType.WARNING)
            _watchedMillis.value = 0L
            _currentMilestoneTier.value = null
            onSaveProgressNeeded?.invoke(0L)
        }
        _sessionState.value = SessionState.IDLE
        lastTickRealtime = 0L
        isMilestoneAwarded = false
        waitingTimeoutJob?.cancel()
        graceJob?.cancel()
    }

    fun invalidateSession(reason: String) {
        _sessionState.value = SessionState.INVALID
        _isGracePeriodActive.value = false
        _redAlertMessage.value = reason
        lastTickRealtime = 0L
        waitingTimeoutJob?.cancel()
        graceJob?.cancel()

        addLog("Session INVALID: $reason", LogType.ERROR)
    }

    fun resetSession() {
        waitingTimeoutJob?.cancel()
        graceJob?.cancel()
        lastTickRealtime = 0L
        _sessionState.value = SessionState.IDLE
        _matchResult.value = MatchResult.UNKNOWN
        _isGracePeriodActive.value = false
        _graceSecondsRemaining.value = 10
        _redAlertMessage.value = null
        _watchedMillis.value = 0L
        _currentMilestoneTier.value = null
        addLog("Session reset to IDLE", LogType.INFO)
    }

    fun setWatchedMillis(millis: Long) {
        _watchedMillis.value = millis
    }

    fun setCompletedState() {
        _sessionState.value = SessionState.COMPLETED
    }
}
