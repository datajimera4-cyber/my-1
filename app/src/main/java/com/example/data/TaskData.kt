package com.example.data

/**
 * State representing the lifecycle of a watch session.
 */
enum class SessionState {
    IDLE,       // No task currently active
    WAITING,    // Task started, waiting for YouTube playback to begin (up to 60s)
    ACTIVE,     // Matching YouTube video actively tracking
    INVALID,    // Session invalidated (e.g. wrong video detected after grace period or timeout)
    COMPLETED   // Task requirement met, coins awarded
}

/**
 * Result of comparing YouTube playing title against task title.
 */
enum class MatchResult {
    UNKNOWN,
    MATCH,
    MISMATCH
}

/**
 * Playback state derived from Android MediaController.
 */
enum class VideoPlaybackState {
    NONE,
    PLAYING,
    PAUSED,
    BUFFERING,
    STOPPED
}

/**
 * Result of oEmbed metadata fetch.
 */
sealed interface OEmbedResult {
    data object Idle : OEmbedResult
    data object Loading : OEmbedResult
    data class Success(
        val title: String,
        val authorName: String,
        val authorUrl: String = "",
        val thumbnailUrl: String = ""
    ) : OEmbedResult
    data class Error(val message: String) : OEmbedResult
}

/**
 * Transaction record for rewards stored locally.
 */
data class WalletTransaction(
    val id: String,
    val title: String,
    val coins: Int,
    val timestampMillis: Long
)

/**
 * Diagnostic log event with human-readable timestamp.
 */
data class LogEvent(
    val id: Long,
    val timestamp: String,
    val message: String,
    val type: LogType = LogType.INFO
)

enum class LogType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

/**
 * Live state of the YouTube title search & discovery process.
 */
data class SearchProgressState(
    val isSearching: Boolean = false,
    val stepText: String = "",
    val typedQuery: String = "",
    val isTyping: Boolean = false,
    val progress: Float = 0f,
    val foundRank: Int? = null,
    val totalFound: Int = 0,
    val channelFilterApplied: Boolean = false,
    val thumbnailVerified: Boolean = false,
    val candidateVideos: List<SearchResultItem> = emptyList()
)

/**
 * Item discovered during YouTube search.
 */
data class SearchResultItem(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String
)

/**
 * Watch duration tier with coin reward definition.
 */
data class WatchDurationTier(
    val minutes: Int,
    val seconds: Int,
    val coins: Int,
    val label: String
)

const val COINS_PER_INR = 20 // 200 Coins = ₹10 INR

val WATCH_DURATION_TIERS = listOf(
    WatchDurationTier(minutes = 3, seconds = 180, coins = 5, label = "3 Min"),
    WatchDurationTier(minutes = 5, seconds = 300, coins = 10, label = "5 Min"),
    WatchDurationTier(minutes = 10, seconds = 600, coins = 20, label = "10 Min"),
    WatchDurationTier(minutes = 20, seconds = 1200, coins = 45, label = "20 Min"),
    WatchDurationTier(minutes = 30, seconds = 1800, coins = 80, label = "30 Min")
)

/**
 * Calculates highest reached milestone during continuous watch.
 * If watched less than 3 minutes (180s), returns null (0 coins).
 */
fun calculateContinuousWatchMilestone(watchedSeconds: Int, selectedGoalSeconds: Int = Int.MAX_VALUE): WatchDurationTier? {
    if (watchedSeconds < 180) return null
    return WATCH_DURATION_TIERS
        .filter { it.seconds <= watchedSeconds && it.seconds <= selectedGoalSeconds }
        .maxByOrNull { it.seconds }
}

/**
 * Multi-video Task Model representing a YouTube video task.
 */
data class VideoTaskItem(
    val id: String,
    val title: String,
    val channelName: String,
    val videoUrl: String,
    val thumbnailUrl: String,
    val durationSeconds: Int = 600, // <=0 means Live stream or unknown
    val isLive: Boolean = false,
    val isCompleted: Boolean = false,
    val watchedMillis: Long = 0L,
    val selectedDurationSeconds: Int = 180,
    val rewardCoins: Int = 5,
    val createdAt: Long = System.currentTimeMillis(),
    val lockedUntilMillis: Long = 0L
) {
    val isLocked: Boolean
        get() = System.currentTimeMillis() < lockedUntilMillis

    val remainingLockMillis: Long
        get() = (lockedUntilMillis - System.currentTimeMillis()).coerceAtLeast(0L)

    fun getLockRemainingFormatted(): String {
        val totalSecs = remainingLockMillis / 1000
        val hours = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return when {
            hours > 0 -> "${hours}h ${mins}m"
            mins > 0 -> "${mins}m ${secs}s"
            else -> "${secs}s"
        }
    }
}

/**
 * User Account Profile for Email Authentication.
 */
data class UserProfile(
    val userId: String,
    val email: String,
    val name: String = "",
    val passwordHash: String = "",
    val coinsBalance: Int = 0,
    val completedTasksCount: Int = 0,
    val joinedAtMillis: Long = System.currentTimeMillis()
)

/**
 * Payout/Withdrawal Request Status for Admin review.
 */
enum class PayoutStatus {
    PENDING,
    APPROVED,
    REJECTED
}

/**
 * Payout Request Model managed by Admin Dashboard.
 */
data class PayoutRequest(
    val id: String,
    val userId: String,
    val userEmail: String,
    val amountCoins: Int,
    val amountInr: Double,
    val method: String,
    val destination: String,
    val status: PayoutStatus = PayoutStatus.PENDING,
    val requestedAtMillis: Long = System.currentTimeMillis(),
    val processedAtMillis: Long? = null,
    val adminNote: String? = null
)

/**
 * Admin Banner / Post / Alert Notification item targeted at specific tabs in the User App.
 * targetTab: "ALL", "HOME", "TASKS", "WALLET", "ME"
 * postType: "BANNER", "ALERT", "POST"
 */
data class AdminPostItem(
    val id: String,
    val title: String,
    val message: String,
    val targetTab: String = "ALL",
    val postType: String = "BANNER",
    val actionUrl: String = "",
    val imageUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)


