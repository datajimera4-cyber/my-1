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

    // Listener for service notification triggers
    var onRedAlertTriggered: ((title: String, message: String) -> Unit)? = null
    var onCompletionTriggered: ((coins: Int, title: String) -> Unit)? = null
    var onSaveProgressNeeded: ((millis: Long) -> Unit)? = null

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
        initialWatchedMillis: Long,
        rewardCoins: Int = 10,
        taskId: String? = null
    ) {
        _targetTaskTitle.value = taskTitle
        _targetTaskAuthor.value = taskAuthor
        _requiredMillis.value = requiredSeconds * 1000L
        _watchedMillis.value = initialWatchedMillis
        _rewardCoins.value = rewardCoins
        _activeTaskId.value = taskId
        _redAlertMessage.value = null
        _isGracePeriodActive.value = false
        _currentMediaTitle.value = taskTitle
        _currentMediaArtist.value = taskAuthor
        _matchResult.value = MatchResult.MATCH
        _playbackState.value = VideoPlaybackState.PLAYING
        _mediaSessionDetected.value = true
        _sessionState.value = SessionState.ACTIVE

        addLog("Task started. Target: \"$taskTitle\" ($requiredSeconds s for $rewardCoins coins). Watching session ACTIVE!", LogType.SUCCESS)

        waitingTimeoutJob?.cancel()
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
                if (isPlaying) {
                    // Start grace period if not already running
                    if (!_isGracePeriodActive.value && currentState == SessionState.ACTIVE) {
                        startGracePeriod()
                    }
                }
            }

            MatchResult.UNKNOWN -> {
                // Keep waiting or paused
            }
        }
    }

    private fun startGracePeriod() {
        _isGracePeriodActive.value = true
        _graceSecondsRemaining.value = 10
        val detected = _currentMediaTitle.value ?: ""
        val isLikelyAd = detected.contains("Ad", ignoreCase = true) || 
                         detected.contains("Sponsored", ignoreCase = true) || 
                         detected.length < 4
        if (isLikelyAd) {
            addLog("Pre-roll ad or sponsor detected (\"$detected\"). Human tolerance grace active.", LogType.INFO)
        } else {
            addLog("Title mismatch detected (\"$detected\"). Human 10s grace period active.", LogType.WARNING)
        }

        graceJob?.cancel()
        graceJob = repositoryScope.launch {
            for (sec in 9 downTo 0) {
                delay(1000L)
                if (!isActive || !_isGracePeriodActive.value) return@launch
                _graceSecondsRemaining.value = sec
            }

            // Grace period expired and still mismatched!
            if (_matchResult.value == MatchResult.MISMATCH && _isGracePeriodActive.value) {
                _isGracePeriodActive.value = false
                val wrongTitle = _currentMediaTitle.value ?: "Different video"
                invalidateSession("Wrong video detected: \"$wrongTitle\".")
                onRedAlertTriggered?.invoke(
                    "Red Alert: Wrong video detected",
                    "Task cancelled because \"$wrongTitle\" was played."
                )
            }
        }
    }

    private fun cancelGracePeriod(reason: String) {
        graceJob?.cancel()
        _isGracePeriodActive.value = false
        _graceSecondsRemaining.value = 10
        addLog(reason, LogType.INFO)
    }

    /**
     * Timer tick loop called from foreground service or ViewModel every ~500ms
     */
    fun processTimerTick() {
        val state = _sessionState.value
        if (state != SessionState.ACTIVE) {
            lastTickRealtime = 0L
            return
        }

        val isPlaying = _playbackState.value == VideoPlaybackState.PLAYING
        val isMatched = _matchResult.value == MatchResult.MATCH

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
        _sessionState.value = SessionState.COMPLETED
        _isGracePeriodActive.value = false
        lastTickRealtime = 0L
        waitingTimeoutJob?.cancel()
        graceJob?.cancel()

        val earned = _currentMilestoneTier.value?.coins ?: _rewardCoins.value
        addLog("Task complete! Continuous watch goal reached. +$earned coins rewarded.", LogType.SUCCESS)
        onCompletionTriggered?.invoke(earned, _targetTaskTitle.value ?: "Video Task")
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
