package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "watchearn_prefs")

class DataStoreManager(private val context: Context) {

    companion object {
        private val KEY_WALLET_BALANCE = intPreferencesKey("wallet_balance")
        private val KEY_TASK_COMPLETED = booleanPreferencesKey("task_completed")
        private val KEY_WATCHED_MILLIS = longPreferencesKey("watched_millis")
        private val KEY_TRANSACTIONS = stringPreferencesKey("transactions_json")
        private val KEY_ACTIVE_VIDEO_URL = stringPreferencesKey("active_video_url")
        private val KEY_LIVE_SEARCH_MODE = booleanPreferencesKey("live_search_mode")
        private val KEY_VIDEO_TASKS = stringPreferencesKey("video_tasks_json")
        private val KEY_SELECTED_TASK_ID = stringPreferencesKey("selected_task_id")
        private val KEY_USERS = stringPreferencesKey("users_json")
        private val KEY_CURRENT_USER_EMAIL = stringPreferencesKey("current_user_email")
        private val KEY_PAYOUT_REQUESTS = stringPreferencesKey("payout_requests_json")
        private val KEY_LIKED_TASKS = stringPreferencesKey("liked_tasks_json")
        private val KEY_COMMENT_COUNTS = stringPreferencesKey("comment_counts_json")
        private val KEY_CLOUD_SERVER_URL = stringPreferencesKey("cloud_server_url")
        private val KEY_CLOUD_SERVER_STATUS = stringPreferencesKey("cloud_server_status")
    }

    val cloudServerUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CLOUD_SERVER_URL] ?: ""
    }

    val cloudServerStatusFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CLOUD_SERVER_STATUS] ?: "Not Connected (Local Mode)"
    }

    val likedTasksFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_LIKED_TASKS] ?: "[]"
        try {
            val arr = JSONArray(json)
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                set.add(arr.getString(i))
            }
            set
        } catch (_: Exception) {
            emptySet()
        }
    }

    val commentCountsFlow: Flow<Map<String, Int>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_COMMENT_COUNTS] ?: "{}"
        try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, Int>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.optInt(k, 0)
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    val liveSearchModeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_LIVE_SEARCH_MODE] ?: false
    }

    val selectedTaskIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_TASK_ID]
    }

    val currentUserEmailFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_CURRENT_USER_EMAIL]
    }

    val usersFlow: Flow<List<UserProfile>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_USERS] ?: "[]"
        parseUsersJson(json)
    }

    val currentUserFlow: Flow<UserProfile?> = context.dataStore.data.map { prefs ->
        val currentEmail = prefs[KEY_CURRENT_USER_EMAIL]
        val usersJson = prefs[KEY_USERS] ?: "[]"
        val users = parseUsersJson(usersJson)
        if (currentEmail.isNullOrBlank()) {
            // Default guest profile if not logged in
            UserProfile(
                userId = "user_guest",
                email = "guest@watchearn.com",
                name = "VIP Watcher",
                coinsBalance = prefs[KEY_WALLET_BALANCE] ?: 0
            )
        } else {
            users.find { it.email.equals(currentEmail, ignoreCase = true) } ?: UserProfile(
                userId = "user_${currentEmail.hashCode()}",
                email = currentEmail,
                name = currentEmail.substringBefore("@"),
                coinsBalance = prefs[KEY_WALLET_BALANCE] ?: 0
            )
        }
    }

    val payoutRequestsFlow: Flow<List<PayoutRequest>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_PAYOUT_REQUESTS] ?: "[]"
        parsePayoutRequestsJson(json)
    }

    val videoTasksFlow: Flow<List<VideoTaskItem>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_VIDEO_TASKS]
        if (json.isNullOrBlank()) {
            getDefaultTasks()
        } else {
            val list = parseVideoTasksJson(json)
            if (list.isEmpty()) getDefaultTasks() else list
        }
    }

    val walletBalanceFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_WALLET_BALANCE] ?: 0
    }

    val isTaskCompletedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_TASK_COMPLETED] ?: false
    }

    val watchedMillisFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_WATCHED_MILLIS] ?: 0L
    }

    val activeVideoUrlFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_VIDEO_URL]
    }

    val transactionsFlow: Flow<List<WalletTransaction>> = context.dataStore.data.map { prefs ->
        val jsonStr = prefs[KEY_TRANSACTIONS] ?: "[]"
        parseTransactionsJson(jsonStr)
    }

    suspend fun saveWatchedMillis(millis: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WATCHED_MILLIS] = millis
        }
    }

    suspend fun setWatchedMillis(millis: Long) {
        saveWatchedMillis(millis)
    }

    suspend fun setTaskCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TASK_COMPLETED] = completed
        }
    }

    suspend fun setActiveVideoUrl(url: String?) {
        context.dataStore.edit { prefs ->
            if (url.isNullOrBlank()) {
                prefs.remove(KEY_ACTIVE_VIDEO_URL)
            } else {
                prefs[KEY_ACTIVE_VIDEO_URL] = url
            }
        }
    }

    suspend fun setLiveSearchMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LIVE_SEARCH_MODE] = enabled
        }
    }

    suspend fun addRewardTransaction(taskTitle: String, coins: Int) {
        context.dataStore.edit { prefs ->
            val currentBalance = prefs[KEY_WALLET_BALANCE] ?: 0
            val newBalance = currentBalance + coins
            prefs[KEY_WALLET_BALANCE] = newBalance

            val currentJson = prefs[KEY_TRANSACTIONS] ?: "[]"
            val list = parseTransactionsJson(currentJson).toMutableList()
            list.add(
                0,
                WalletTransaction(
                    id = UUID.randomUUID().toString(),
                    title = taskTitle,
                    coins = coins,
                    timestampMillis = System.currentTimeMillis()
                )
            )
            prefs[KEY_TRANSACTIONS] = serializeTransactionsJson(list)
        }
    }

    suspend fun clearContinuousWatchSession() {
        context.dataStore.edit { prefs ->
            prefs[KEY_WATCHED_MILLIS] = 0L
        }
    }

    suspend fun recordTaskLike(taskId: String, taskTitle: String = "YouTube Video"): Pair<Boolean, String> {
        var added = false
        var message = ""
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_LIKED_TASKS] ?: "[]"
            val arr = try { JSONArray(json) } catch (_: Exception) { JSONArray() }
            var alreadyLiked = false
            for (i in 0 until arr.length()) {
                if (arr.getString(i) == taskId) {
                    alreadyLiked = true
                    break
                }
            }
            if (alreadyLiked) {
                message = "Already earned like reward for this video."
                added = false
            } else {
                arr.put(taskId)
                prefs[KEY_LIKED_TASKS] = arr.toString()

                val currentBalance = prefs[KEY_WALLET_BALANCE] ?: 0
                prefs[KEY_WALLET_BALANCE] = currentBalance + 5

                val currentTxJson = prefs[KEY_TRANSACTIONS] ?: "[]"
                val list = parseTransactionsJson(currentTxJson).toMutableList()
                list.add(
                    0,
                    WalletTransaction(
                        id = UUID.randomUUID().toString(),
                        title = "👍 Video Like Bonus: $taskTitle",
                        coins = 5,
                        timestampMillis = System.currentTimeMillis()
                    )
                )
                prefs[KEY_TRANSACTIONS] = serializeTransactionsJson(list)
                added = true
                message = "🎉 +5 Coins added for Liking the video!"
            }
        }
        return Pair(added, message)
    }

    suspend fun markTaskAlreadyLiked(taskId: String) {
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_LIKED_TASKS] ?: "[]"
            val arr = try { JSONArray(json) } catch (_: Exception) { JSONArray() }
            var alreadyLiked = false
            for (i in 0 until arr.length()) {
                if (arr.getString(i) == taskId) {
                    alreadyLiked = true
                    break
                }
            }
            if (!alreadyLiked) {
                arr.put(taskId)
                prefs[KEY_LIKED_TASKS] = arr.toString()
            }
        }
    }

    suspend fun setCloudServerUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CLOUD_SERVER_URL] = url.trim()
        }
    }

    suspend fun setCloudServerStatus(status: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CLOUD_SERVER_STATUS] = status
        }
    }

    suspend fun lockTask(taskId: String, durationMillis: Long = 12 * 60 * 60 * 1000L) {
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_VIDEO_TASKS]
            val currentList = if (json.isNullOrBlank()) getDefaultTasks().toMutableList() else parseVideoTasksJson(json).toMutableList()
            val index = currentList.indexOfFirst { it.id == taskId }
            val lockTime = System.currentTimeMillis() + durationMillis
            if (index != -1) {
                val t = currentList[index]
                currentList[index] = t.copy(lockedUntilMillis = lockTime)
                prefs[KEY_VIDEO_TASKS] = serializeVideoTasksJson(currentList)
            }
        }
    }

    suspend fun unlockTask(taskId: String) {
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_VIDEO_TASKS]
            val currentList = if (json.isNullOrBlank()) getDefaultTasks().toMutableList() else parseVideoTasksJson(json).toMutableList()
            val index = currentList.indexOfFirst { it.id == taskId }
            if (index != -1) {
                val t = currentList[index]
                currentList[index] = t.copy(lockedUntilMillis = 0L)
                prefs[KEY_VIDEO_TASKS] = serializeVideoTasksJson(currentList)
            }
        }
    }

    suspend fun unlockAllTasks() {
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_VIDEO_TASKS]
            val currentList = if (json.isNullOrBlank()) getDefaultTasks().toMutableList() else parseVideoTasksJson(json).toMutableList()
            val updated = currentList.map { it.copy(lockedUntilMillis = 0L) }
            prefs[KEY_VIDEO_TASKS] = serializeVideoTasksJson(updated)
        }
    }

    suspend fun recordTaskComment(taskId: String, taskTitle: String = "YouTube Video"): Pair<Boolean, String> {
        var added = false
        var message = ""
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_COMMENT_COUNTS] ?: "{}"
            val obj = try { JSONObject(json) } catch (_: Exception) { JSONObject() }
            val currentCount = obj.optInt(taskId, 0)
            if (currentCount >= 2) {
                message = "Maximum 2 comments reached for this task (+10 coins limit)."
                added = false
            } else {
                val newCount = currentCount + 1
                obj.put(taskId, newCount)
                prefs[KEY_COMMENT_COUNTS] = obj.toString()

                val currentBalance = prefs[KEY_WALLET_BALANCE] ?: 0
                prefs[KEY_WALLET_BALANCE] = currentBalance + 5

                val currentTxJson = prefs[KEY_TRANSACTIONS] ?: "[]"
                val list = parseTransactionsJson(currentTxJson).toMutableList()
                list.add(
                    0,
                    WalletTransaction(
                        id = UUID.randomUUID().toString(),
                        title = "💬 Video Comment #$newCount Bonus: $taskTitle",
                        coins = 5,
                        timestampMillis = System.currentTimeMillis()
                    )
                )
                prefs[KEY_TRANSACTIONS] = serializeTransactionsJson(list)
                added = true
                message = "🎉 +5 Coins added for Comment #$newCount on video!"
            }
        }
        return Pair(added, message)
    }

    suspend fun withdrawCoins(coins: Int, method: String, destination: String): Boolean {
        var success = false
        context.dataStore.edit { prefs ->
            val currentBalance = prefs[KEY_WALLET_BALANCE] ?: 0
            if (currentBalance >= coins && coins > 0) {
                val newBalance = currentBalance - coins
                prefs[KEY_WALLET_BALANCE] = newBalance

                val inrAmount = coins / 10.0
                val formattedInr = String.format(java.util.Locale.US, "%.2f", inrAmount)

                val currentJson = prefs[KEY_TRANSACTIONS] ?: "[]"
                val list = parseTransactionsJson(currentJson).toMutableList()
                val reqId = UUID.randomUUID().toString()
                list.add(
                    0,
                    WalletTransaction(
                        id = reqId,
                        title = "Withdrawal PENDING to $method ($destination) [₹$formattedInr]",
                        coins = -coins,
                        timestampMillis = System.currentTimeMillis()
                    )
                )
                prefs[KEY_TRANSACTIONS] = serializeTransactionsJson(list)

                // Add to payout requests queue for instant Admin Panel review!
                val currentEmail = prefs[KEY_CURRENT_USER_EMAIL] ?: "guest@watchearn.com"
                val payoutList = parsePayoutRequestsJson(prefs[KEY_PAYOUT_REQUESTS] ?: "[]").toMutableList()
                payoutList.add(
                    0,
                    PayoutRequest(
                        id = reqId,
                        userId = "usr_${Math.abs(currentEmail.hashCode()) % 100000}",
                        userEmail = currentEmail,
                        amountCoins = coins,
                        amountInr = inrAmount,
                        method = method,
                        destination = destination,
                        status = PayoutStatus.PENDING,
                        requestedAtMillis = System.currentTimeMillis()
                    )
                )
                prefs[KEY_PAYOUT_REQUESTS] = serializePayoutRequestsJson(payoutList)
                success = true
            }
        }
        return success
    }

    suspend fun approvePayout(requestId: String, note: String = "Payment Dispatched"): Boolean {
        var found = false
        context.dataStore.edit { prefs ->
            val payoutList = parsePayoutRequestsJson(prefs[KEY_PAYOUT_REQUESTS] ?: "[]").toMutableList()
            val index = payoutList.indexOfFirst { it.id == requestId }
            if (index != -1) {
                val req = payoutList[index]
                payoutList[index] = req.copy(
                    status = PayoutStatus.APPROVED,
                    processedAtMillis = System.currentTimeMillis(),
                    adminNote = note
                )
                prefs[KEY_PAYOUT_REQUESTS] = serializePayoutRequestsJson(payoutList)

                // Add approved transaction entry
                val txList = parseTransactionsJson(prefs[KEY_TRANSACTIONS] ?: "[]").toMutableList()
                txList.add(
                    0,
                    WalletTransaction(
                        id = UUID.randomUUID().toString(),
                        title = "✅ Payout APPROVED: ₹${String.format(java.util.Locale.US, "%.2f", req.amountInr)} sent via ${req.method} ($note)",
                        coins = 0,
                        timestampMillis = System.currentTimeMillis()
                    )
                )
                prefs[KEY_TRANSACTIONS] = serializeTransactionsJson(txList)
                found = true
            }
        }
        return found
    }

    suspend fun rejectPayout(requestId: String, reason: String = "Declined by Admin"): Boolean {
        var found = false
        context.dataStore.edit { prefs ->
            val payoutList = parsePayoutRequestsJson(prefs[KEY_PAYOUT_REQUESTS] ?: "[]").toMutableList()
            val index = payoutList.indexOfFirst { it.id == requestId }
            if (index != -1) {
                val req = payoutList[index]
                payoutList[index] = req.copy(
                    status = PayoutStatus.REJECTED,
                    processedAtMillis = System.currentTimeMillis(),
                    adminNote = reason
                )
                prefs[KEY_PAYOUT_REQUESTS] = serializePayoutRequestsJson(payoutList)

                // Refund the coins back to the user's wallet!
                val currentBalance = prefs[KEY_WALLET_BALANCE] ?: 0
                prefs[KEY_WALLET_BALANCE] = currentBalance + req.amountCoins

                val txList = parseTransactionsJson(prefs[KEY_TRANSACTIONS] ?: "[]").toMutableList()
                txList.add(
                    0,
                    WalletTransaction(
                        id = UUID.randomUUID().toString(),
                        title = "❌ Payout REJECTED (Refunded +${req.amountCoins} coins): $reason",
                        coins = req.amountCoins,
                        timestampMillis = System.currentTimeMillis()
                    )
                )
                prefs[KEY_TRANSACTIONS] = serializeTransactionsJson(txList)
                found = true
            }
        }
        return found
    }

    suspend fun signUpUser(email: String, password: String, name: String): Pair<Boolean, String> {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
            return Pair(false, "Please enter a valid email address.")
        }
        if (password.length < 4) {
            return Pair(false, "Password must be at least 4 characters.")
        }

        var result = Pair(true, "Account created successfully!")
        context.dataStore.edit { prefs ->
            val users = parseUsersJson(prefs[KEY_USERS] ?: "[]").toMutableList()
            if (users.any { it.email.equals(cleanEmail, ignoreCase = true) }) {
                result = Pair(false, "Account with this email already exists.")
                return@edit
            }
            val newUser = UserProfile(
                userId = "usr_${Math.abs(cleanEmail.hashCode()) % 100000}",
                email = cleanEmail,
                name = name.ifBlank { cleanEmail.substringBefore("@") },
                passwordHash = password,
                coinsBalance = prefs[KEY_WALLET_BALANCE] ?: 0,
                joinedAtMillis = System.currentTimeMillis()
            )
            users.add(newUser)
            prefs[KEY_USERS] = serializeUsersJson(users)
            prefs[KEY_CURRENT_USER_EMAIL] = cleanEmail
        }
        return result
    }

    suspend fun loginUser(email: String, password: String): Pair<Boolean, String> {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
            return Pair(false, "Please enter a valid email address.")
        }
        var result = Pair(false, "Invalid email or password.")
        context.dataStore.edit { prefs ->
            val users = parseUsersJson(prefs[KEY_USERS] ?: "[]").toMutableList()
            val existing = users.find { it.email.equals(cleanEmail, ignoreCase = true) }
            if (existing != null) {
                if (existing.passwordHash == password || existing.passwordHash.isEmpty()) {
                    prefs[KEY_CURRENT_USER_EMAIL] = cleanEmail
                    result = Pair(true, "Logged in successfully!")
                } else {
                    result = Pair(false, "Incorrect password.")
                }
            } else {
                // Auto create account on first login
                val newUser = UserProfile(
                    userId = "usr_${Math.abs(cleanEmail.hashCode()) % 100000}",
                    email = cleanEmail,
                    name = cleanEmail.substringBefore("@"),
                    passwordHash = password,
                    coinsBalance = prefs[KEY_WALLET_BALANCE] ?: 0,
                    joinedAtMillis = System.currentTimeMillis()
                )
                users.add(newUser)
                prefs[KEY_USERS] = serializeUsersJson(users)
                prefs[KEY_CURRENT_USER_EMAIL] = cleanEmail
                result = Pair(true, "Account created and logged in!")
            }
        }
        return result
    }

    suspend fun logoutUser() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_CURRENT_USER_EMAIL)
        }
    }

    suspend fun adminDeleteVideoTask(taskId: String) {
        context.dataStore.edit { prefs ->
            val list = parseVideoTasksJson(prefs[KEY_VIDEO_TASKS] ?: "").toMutableList()
            list.removeAll { it.id == taskId }
            prefs[KEY_VIDEO_TASKS] = serializeVideoTasksJson(list)
            if (prefs[KEY_SELECTED_TASK_ID] == taskId) {
                prefs.remove(KEY_SELECTED_TASK_ID)
            }
        }
    }

    suspend fun adminUpdateUserCoins(userEmail: String, newCoins: Int) {
        context.dataStore.edit { prefs ->
            val currentEmail = prefs[KEY_CURRENT_USER_EMAIL]
            if (currentEmail.equals(userEmail, ignoreCase = true) || userEmail.isBlank() || userEmail == "current") {
                prefs[KEY_WALLET_BALANCE] = newCoins.coerceAtLeast(0)
            }
            val users = parseUsersJson(prefs[KEY_USERS] ?: "[]").toMutableList()
            val index = users.indexOfFirst { it.email.equals(userEmail, ignoreCase = true) }
            if (index != -1) {
                users[index] = users[index].copy(coinsBalance = newCoins.coerceAtLeast(0))
                prefs[KEY_USERS] = serializeUsersJson(users)
            }
        }
    }

    suspend fun resetAll() {
        context.dataStore.edit { prefs ->
            prefs[KEY_WALLET_BALANCE] = 0
            prefs[KEY_TASK_COMPLETED] = false
            prefs[KEY_WATCHED_MILLIS] = 0L
            prefs[KEY_TRANSACTIONS] = "[]"
            prefs[KEY_VIDEO_TASKS] = serializeVideoTasksJson(getDefaultTasks())
            prefs.remove(KEY_SELECTED_TASK_ID)
        }
    }

    suspend fun setSelectedTaskId(id: String?) {
        context.dataStore.edit { prefs ->
            if (id != null) {
                prefs[KEY_SELECTED_TASK_ID] = id
            } else {
                prefs.remove(KEY_SELECTED_TASK_ID)
            }
        }
    }

    suspend fun addVideoTask(task: VideoTaskItem) {
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_VIDEO_TASKS]
            val currentList = if (json.isNullOrBlank()) getDefaultTasks().toMutableList() else parseVideoTasksJson(json).toMutableList()
            // Add new task at the top
            currentList.add(0, task)
            prefs[KEY_VIDEO_TASKS] = serializeVideoTasksJson(currentList)
        }
    }

    suspend fun updateVideoTask(updatedTask: VideoTaskItem) {
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_VIDEO_TASKS]
            val currentList = if (json.isNullOrBlank()) getDefaultTasks().toMutableList() else parseVideoTasksJson(json).toMutableList()
            val index = currentList.indexOfFirst { it.id == updatedTask.id }
            if (index != -1) {
                currentList[index] = updatedTask
            } else {
                currentList.add(0, updatedTask)
            }
            prefs[KEY_VIDEO_TASKS] = serializeVideoTasksJson(currentList)
        }
    }

    suspend fun markTaskCompleted(taskId: String, rewardCoins: Int) {
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_VIDEO_TASKS]
            val currentList = if (json.isNullOrBlank()) getDefaultTasks().toMutableList() else parseVideoTasksJson(json).toMutableList()
            val index = currentList.indexOfFirst { it.id == taskId }
            if (index != -1) {
                val t = currentList[index]
                val lockDuration = 12 * 60 * 60 * 1000L
                val lockUntil = System.currentTimeMillis() + lockDuration
                currentList[index] = t.copy(
                    isCompleted = true,
                    rewardCoins = rewardCoins,
                    lockedUntilMillis = lockUntil
                )
                prefs[KEY_VIDEO_TASKS] = serializeVideoTasksJson(currentList)
            }
        }
    }

    fun getDefaultTasks(): List<VideoTaskItem> {
        return listOf(
            VideoTaskItem(
                id = "default_rick",
                title = "Rick Astley - Never Gonna Give You Up (Official Music Video)",
                channelName = "Rick Astley",
                videoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                thumbnailUrl = "https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                durationSeconds = 213, // 3 min 33 sec
                isLive = false,
                isCompleted = false,
                rewardCoins = 10,
                selectedDurationSeconds = 180
            ),
            VideoTaskItem(
                id = "default_android15",
                title = "Android 15 Developer Deep Dive: Private Spaces & System APIs",
                channelName = "Android Developers",
                videoUrl = "https://www.youtube.com/watch?v=jNQXAC9IVRw",
                thumbnailUrl = "https://img.youtube.com/vi/jNQXAC9IVRw/hqdefault.jpg",
                durationSeconds = 600, // 10 min 00 sec
                isLive = false,
                isCompleted = false,
                rewardCoins = 40,
                selectedDurationSeconds = 600
            ),
            VideoTaskItem(
                id = "default_kotlin_course",
                title = "Complete Kotlin Programming Course 2026 for Android",
                channelName = "freeCodeCamp.org",
                videoUrl = "https://www.youtube.com/watch?v=F9UC9DY-vIU",
                thumbnailUrl = "https://img.youtube.com/vi/F9UC9DY-vIU/hqdefault.jpg",
                durationSeconds = 1980, // 33 min
                isLive = false,
                isCompleted = false,
                rewardCoins = 160,
                selectedDurationSeconds = 1800
            ),
            VideoTaskItem(
                id = "default_lofi_live",
                title = "lofi hip hop radio - beats to relax/study to [LIVE 24/7]",
                channelName = "Lofi Girl",
                videoUrl = "https://www.youtube.com/watch?v=jfKfPfyJRdk",
                thumbnailUrl = "https://img.youtube.com/vi/jfKfPfyJRdk/hqdefault.jpg",
                durationSeconds = 0, // 0 = LIVE STREAM!
                isLive = true,
                isCompleted = false,
                rewardCoins = 160,
                selectedDurationSeconds = 1800
            )
        )
    }

    private fun parseVideoTasksJson(json: String): List<VideoTaskItem> {
        val list = mutableListOf<VideoTaskItem>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    VideoTaskItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        title = obj.optString("title", "Video Task"),
                        channelName = obj.optString("channelName", "YouTube Creator"),
                        videoUrl = obj.optString("videoUrl", "https://www.youtube.com"),
                        thumbnailUrl = obj.optString("thumbnailUrl", ""),
                        durationSeconds = obj.optInt("durationSeconds", 600),
                        isLive = obj.optBoolean("isLive", false),
                        isCompleted = obj.optBoolean("isCompleted", false),
                        watchedMillis = obj.optLong("watchedMillis", 0L),
                        selectedDurationSeconds = obj.optInt("selectedDurationSeconds", 180),
                        rewardCoins = obj.optInt("rewardCoins", 10),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        lockedUntilMillis = obj.optLong("lockedUntilMillis", 0L)
                    )
                )
            }
        } catch (_: Exception) {
            // fallback
        }
        return list
    }

    private fun serializeVideoTasksJson(tasks: List<VideoTaskItem>): String {
        val array = JSONArray()
        for (item in tasks) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("channelName", item.channelName)
                put("videoUrl", item.videoUrl)
                put("thumbnailUrl", item.thumbnailUrl)
                put("durationSeconds", item.durationSeconds)
                put("isLive", item.isLive)
                put("isCompleted", item.isCompleted)
                put("watchedMillis", item.watchedMillis)
                put("selectedDurationSeconds", item.selectedDurationSeconds)
                put("rewardCoins", item.rewardCoins)
                put("createdAt", item.createdAt)
                put("lockedUntilMillis", item.lockedUntilMillis)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseTransactionsJson(json: String): List<WalletTransaction> {
        val results = mutableListOf<WalletTransaction>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                results.add(
                    WalletTransaction(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        title = obj.optString("title", "Task Reward"),
                        coins = obj.optInt("coins", 0),
                        timestampMillis = obj.optLong("timestampMillis", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {
            // fallback to empty list
        }
        return results
    }

    private fun serializeTransactionsJson(transactions: List<WalletTransaction>): String {
        val array = JSONArray()
        for (item in transactions) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("coins", item.coins)
                put("timestampMillis", item.timestampMillis)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseUsersJson(json: String): List<UserProfile> {
        val list = mutableListOf<UserProfile>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    UserProfile(
                        userId = obj.optString("userId", UUID.randomUUID().toString()),
                        email = obj.optString("email", ""),
                        name = obj.optString("name", ""),
                        passwordHash = obj.optString("passwordHash", ""),
                        coinsBalance = obj.optInt("coinsBalance", 0),
                        completedTasksCount = obj.optInt("completedTasksCount", 0),
                        joinedAtMillis = obj.optLong("joinedAtMillis", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {
            // fallback
        }
        return list
    }

    private fun serializeUsersJson(users: List<UserProfile>): String {
        val array = JSONArray()
        for (u in users) {
            val obj = JSONObject().apply {
                put("userId", u.userId)
                put("email", u.email)
                put("name", u.name)
                put("passwordHash", u.passwordHash)
                put("coinsBalance", u.coinsBalance)
                put("completedTasksCount", u.completedTasksCount)
                put("joinedAtMillis", u.joinedAtMillis)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun parsePayoutRequestsJson(json: String): List<PayoutRequest> {
        val list = mutableListOf<PayoutRequest>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val statusStr = obj.optString("status", PayoutStatus.PENDING.name)
                val status = try {
                    PayoutStatus.valueOf(statusStr)
                } catch (_: Exception) {
                    PayoutStatus.PENDING
                }
                list.add(
                    PayoutRequest(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        userId = obj.optString("userId", ""),
                        userEmail = obj.optString("userEmail", ""),
                        amountCoins = obj.optInt("amountCoins", 0),
                        amountInr = obj.optDouble("amountInr", 0.0),
                        method = obj.optString("method", "UPI"),
                        destination = obj.optString("destination", ""),
                        status = status,
                        requestedAtMillis = obj.optLong("requestedAtMillis", System.currentTimeMillis()),
                        processedAtMillis = if (obj.has("processedAtMillis")) obj.optLong("processedAtMillis") else null,
                        adminNote = if (obj.has("adminNote")) obj.optString("adminNote") else null
                    )
                )
            }
        } catch (_: Exception) {
            // fallback
        }
        return list
    }

    private fun serializePayoutRequestsJson(requests: List<PayoutRequest>): String {
        val array = JSONArray()
        for (r in requests) {
            val obj = JSONObject().apply {
                put("id", r.id)
                put("userId", r.userId)
                put("userEmail", r.userEmail)
                put("amountCoins", r.amountCoins)
                put("amountInr", r.amountInr)
                put("method", r.method)
                put("destination", r.destination)
                put("status", r.status.name)
                put("requestedAtMillis", r.requestedAtMillis)
                r.processedAtMillis?.let { put("processedAtMillis", it) }
                r.adminNote?.let { put("adminNote", it) }
            }
            array.put(obj)
        }
        return array.toString()
    }
}
