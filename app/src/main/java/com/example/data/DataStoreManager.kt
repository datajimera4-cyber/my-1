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
        // =========================================================================
        // DEFAULT GOOGLE DRIVE SERVER URL
        // Future mein agar aapko Google Apps Script ka link change karna ho,
        // to bas neeche wali line mein naya "/exec" URL daal dein:
        // =========================================================================
        const val DEFAULT_CLOUD_SERVER_URL =
            "https://script.google.com/macros/s/AKfycbzeuvE2McdDn-PAw9CNUHs_QkoIbk7R4oAFtrk8c-RKmJX9r0a8p_xEqaS6bLV6FuW3/exec"

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
        private val KEY_DELETED_TASK_IDS = stringPreferencesKey("deleted_task_ids_json")
        private val KEY_ADMIN_POSTS = stringPreferencesKey("admin_posts_json")
        private val KEY_NOTIFIED_ITEM_IDS = stringPreferencesKey("notified_item_ids_json")
        private val KEY_DISMISSED_POST_IDS = stringPreferencesKey("dismissed_post_ids_json")
    }

    val adminPostsFlow: Flow<List<AdminPostItem>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_ADMIN_POSTS]
        val list = if (json == null) {
            getDefaultAdminPosts()
        } else {
            parseAdminPostsJson(json)
        }
        list.sortedWith(
            compareByDescending<AdminPostItem> { it.isPinned }
                .thenByDescending { if (it.isPinned) it.pinnedAt else 0L }
        )
    }

    val notifiedItemIdsFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_NOTIFIED_ITEM_IDS] ?: "[]"
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

    val dismissedPostIdsFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_DISMISSED_POST_IDS] ?: "[]"
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

    val deletedTaskIdsFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_DELETED_TASK_IDS] ?: "[]"
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

    val cloudServerUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        val saved = prefs[KEY_CLOUD_SERVER_URL]?.trim()
        if (saved.isNullOrBlank()) DEFAULT_CLOUD_SERVER_URL else saved
    }

    val cloudServerStatusFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CLOUD_SERVER_STATUS] ?: "Connected to Default Drive Server"
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
        if (currentEmail.isNullOrBlank()) {
            null
        } else {
            val usersJson = prefs[KEY_USERS] ?: "[]"
            val users = parseUsersJson(usersJson)
            val walletBal = prefs[KEY_WALLET_BALANCE] ?: 0
            users.find { it.email.equals(currentEmail, ignoreCase = true) }?.copy(
                coinsBalance = walletBal
            ) ?: UserProfile(
                userId = "usr_${Math.abs(currentEmail.hashCode()) % 100000}",
                email = currentEmail,
                name = currentEmail.substringBefore("@"),
                coinsBalance = walletBal
            )
        }
    }

    val payoutRequestsFlow: Flow<List<PayoutRequest>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_PAYOUT_REQUESTS] ?: "[]"
        parsePayoutRequestsJson(json)
    }

    val videoTasksFlow: Flow<List<VideoTaskItem>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_VIDEO_TASKS]
        val list = if (json == null) {
            getDefaultTasks()
        } else {
            parseVideoTasksJson(json)
        }
        list.sortedWith(
            compareByDescending<VideoTaskItem> { it.isPinned }
                .thenByDescending { if (it.isPinned) it.pinnedAt else 0L }
        )
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
            val serializedTx = serializeTransactionsJson(list)
            prefs[KEY_TRANSACTIONS] = serializedTx
            syncActiveUserIntoUsersList(prefs, newBalanceOverride = newBalance, newTxJsonOverride = serializedTx)
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
                val serializedTx = serializeTransactionsJson(list)
                prefs[KEY_TRANSACTIONS] = serializedTx
                syncActiveUserIntoUsersList(
                    prefs,
                    newBalanceOverride = currentBalance + 5,
                    newTxJsonOverride = serializedTx,
                    newLikedJsonOverride = arr.toString()
                )
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
                currentList[index] = t.copy(
                    isCompleted = false,
                    watchedMillis = 0L,
                    lockedUntilMillis = lockTime
                )
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
                currentList[index] = t.copy(
                    isCompleted = false,
                    watchedMillis = 0L,
                    lockedUntilMillis = 0L
                )
                prefs[KEY_VIDEO_TASKS] = serializeVideoTasksJson(currentList)
            }
        }
    }

    suspend fun unlockExpiredTasks() {
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_VIDEO_TASKS] ?: return@edit
            val now = System.currentTimeMillis()
            val rawArray = try { JSONArray(json) } catch (_: Exception) { return@edit }
            var anyExpired = false
            for (i in 0 until rawArray.length()) {
                val obj = rawArray.optJSONObject(i) ?: continue
                val lockedUntil = obj.optLong("lockedUntilMillis", 0L)
                if (lockedUntil in 1..now) {
                    anyExpired = true
                    break
                }
            }
            if (anyExpired) {
                val currentList = parseVideoTasksJson(json)
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
                val serializedTx = serializeTransactionsJson(list)
                prefs[KEY_TRANSACTIONS] = serializedTx
                syncActiveUserIntoUsersList(
                    prefs,
                    newBalanceOverride = currentBalance + 5,
                    newTxJsonOverride = serializedTx,
                    newCommentsJsonOverride = obj.toString()
                )
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

                val inrAmount = coins.toDouble() / COINS_PER_INR.toDouble()
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
                syncActiveUserIntoUsersList(
                    prefs,
                    newBalanceOverride = newBalance,
                    newTxJsonOverride = prefs[KEY_TRANSACTIONS]
                )
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
                result = Pair(false, "Account with this email already exists. Please sign in.")
                return@edit
            }
            val now = System.currentTimeMillis()
            val newUser = UserProfile(
                userId = "usr_${Math.abs(cleanEmail.hashCode()) % 100000}",
                email = cleanEmail,
                name = name.ifBlank { cleanEmail.substringBefore("@") },
                passwordHash = password,
                coinsBalance = 0,
                completedTasksCount = 0,
                joinedAtMillis = now,
                transactionsJson = "[]",
                likedTasksJson = "[]",
                commentCountsJson = "{}",
                taskLocksJson = "{}",
                lastUpdatedMillis = now
            )
            users.add(newUser)
            prefs[KEY_USERS] = serializeUsersJson(users)
            prefs[KEY_CURRENT_USER_EMAIL] = cleanEmail
            applyUserProfileToSessionPrefs(prefs, newUser)
        }
        return result
    }

    suspend fun loginUser(email: String, password: String): Pair<Boolean, String> {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
            return Pair(false, "Please enter a valid email address.")
        }
        if (password.isBlank()) {
            return Pair(false, "Please enter your password.")
        }
        var result = Pair(false, "Invalid email or password.")
        context.dataStore.edit { prefs ->
            val users = parseUsersJson(prefs[KEY_USERS] ?: "[]").toMutableList()
            val existing = users.find { it.email.equals(cleanEmail, ignoreCase = true) }
            if (existing != null) {
                if (existing.passwordHash == password || existing.passwordHash.isEmpty()) {
                    val updatedUser = if (existing.passwordHash.isEmpty()) {
                        existing.copy(passwordHash = password, lastUpdatedMillis = System.currentTimeMillis())
                    } else {
                        existing
                    }
                    val idx = users.indexOfFirst { it.email.equals(cleanEmail, ignoreCase = true) }
                    if (idx != -1) {
                        users[idx] = updatedUser
                        prefs[KEY_USERS] = serializeUsersJson(users)
                    }
                    prefs[KEY_CURRENT_USER_EMAIL] = cleanEmail
                    applyUserProfileToSessionPrefs(prefs, updatedUser)
                    result = Pair(true, "Welcome back, ${updatedUser.name.ifBlank { cleanEmail.substringBefore("@") }}!")
                } else {
                    result = Pair(false, "Incorrect password. Please try again.")
                }
            } else {
                result = Pair(false, "No account found with this email. Please Sign Up first.")
            }
        }
        return result
    }

    suspend fun logoutUser() {
        context.dataStore.edit { prefs ->
            syncActiveUserIntoUsersList(prefs)
            prefs.remove(KEY_CURRENT_USER_EMAIL)
            prefs[KEY_WALLET_BALANCE] = 0
            prefs[KEY_TRANSACTIONS] = "[]"
            prefs[KEY_LIKED_TASKS] = "[]"
            prefs[KEY_COMMENT_COUNTS] = "{}"
            val currentList = parseVideoTasksJson(prefs[KEY_VIDEO_TASKS] ?: serializeVideoTasksJson(getDefaultTasks()))
            val unlocked = currentList.map { it.copy(isCompleted = false, watchedMillis = 0L, lockedUntilMillis = 0L) }
            prefs[KEY_VIDEO_TASKS] = serializeVideoTasksJson(unlocked)
        }
    }

    suspend fun adminDeleteVideoTask(taskId: String) {
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_VIDEO_TASKS]
            val list = if (json == null) getDefaultTasks().toMutableList() else parseVideoTasksJson(json).toMutableList()
            list.removeAll { it.id == taskId }
            prefs[KEY_VIDEO_TASKS] = serializeVideoTasksJson(list)

            val deletedJson = prefs[KEY_DELETED_TASK_IDS] ?: "[]"
            val deletedArr = try { JSONArray(deletedJson) } catch (_: Exception) { JSONArray() }
            deletedArr.put(taskId)
            prefs[KEY_DELETED_TASK_IDS] = deletedArr.toString()

            if (prefs[KEY_SELECTED_TASK_ID] == taskId) {
                val nextTask = list.firstOrNull()
                if (nextTask != null) {
                    prefs[KEY_SELECTED_TASK_ID] = nextTask.id
                    prefs[KEY_ACTIVE_VIDEO_URL] = nextTask.videoUrl
                } else {
                    prefs.remove(KEY_SELECTED_TASK_ID)
                }
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
                users[index] = users[index].copy(
                    coinsBalance = newCoins.coerceAtLeast(0),
                    lastUpdatedMillis = System.currentTimeMillis() + 5000L
                )
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
            currentList.removeAll { it.id == task.id }
            currentList.add(0, task)
            val sortedList = currentList.sortedWith(
                compareByDescending<VideoTaskItem> { it.isPinned }
                    .thenByDescending { if (it.isPinned) it.pinnedAt else 0L }
            )
            prefs[KEY_VIDEO_TASKS] = serializeVideoTasksJson(sortedList)
        }
    }

    suspend fun togglePinVideoTask(taskId: String): Boolean {
        var newPinState = false
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_VIDEO_TASKS]
            val currentList = if (json.isNullOrBlank()) getDefaultTasks().toMutableList() else parseVideoTasksJson(json).toMutableList()
            val index = currentList.indexOfFirst { it.id == taskId }
            if (index != -1) {
                val item = currentList[index]
                newPinState = !item.isPinned
                val now = System.currentTimeMillis()
                currentList[index] = item.copy(
                    isPinned = newPinState,
                    pinnedAt = if (newPinState) now else 0L
                )
                val sortedList = currentList.sortedWith(
                    compareByDescending<VideoTaskItem> { it.isPinned }
                        .thenByDescending { if (it.isPinned) it.pinnedAt else 0L }
                )
                prefs[KEY_VIDEO_TASKS] = serializeVideoTasksJson(sortedList)
            }
        }
        return newPinState
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
                val lockDuration = 8 * 60 * 60 * 1000L
                val lockUntil = System.currentTimeMillis() + lockDuration
                currentList[index] = t.copy(
                    isCompleted = true,
                    watchedMillis = 0L,
                    rewardCoins = rewardCoins,
                    lockedUntilMillis = lockUntil
                )
                val serializedTasks = serializeVideoTasksJson(currentList)
                prefs[KEY_VIDEO_TASKS] = serializedTasks
                syncActiveUserIntoUsersList(prefs, updatedTasks = currentList, incrementCompletedTasks = true)
            }
        }
    }

    fun getDefaultAdminPosts(): List<AdminPostItem> {
        return listOf(
            AdminPostItem(
                id = "default_welcome_banner",
                title = "🔥 Bonus Update: 1000 Coins = ₹10 INR!",
                message = "Watch tasks & earn: 3m=10c, 5m=17c, 10m=35c, 20m=72c, 30m=110c + Like (+5c) & Comment (+5c)!",
                targetTab = "HOME",
                postType = "BANNER",
                actionUrl = "",
                imageUrl = "",
                createdAt = 1700000000000L,
                isPinned = true,
                pinnedAt = 1700000000000L
            )
        )
    }

    suspend fun addAdminPost(post: AdminPostItem) {
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_ADMIN_POSTS]
            val currentList = if (json == null) getDefaultAdminPosts().toMutableList() else parseAdminPostsJson(json).toMutableList()
            currentList.removeAll { it.id == post.id }
            currentList.add(0, post)
            val sortedList = currentList.sortedWith(
                compareByDescending<AdminPostItem> { it.isPinned }
                    .thenByDescending { if (it.isPinned) it.pinnedAt else 0L }
            )
            prefs[KEY_ADMIN_POSTS] = serializeAdminPostsJson(sortedList)
        }
    }

    suspend fun togglePinAdminPost(postId: String): Boolean {
        var newPinState = false
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_ADMIN_POSTS]
            val currentList = if (json == null) getDefaultAdminPosts().toMutableList() else parseAdminPostsJson(json).toMutableList()
            val index = currentList.indexOfFirst { it.id == postId }
            if (index != -1) {
                val item = currentList[index]
                newPinState = !item.isPinned
                val now = System.currentTimeMillis()
                currentList[index] = item.copy(
                    isPinned = newPinState,
                    pinnedAt = if (newPinState) now else 0L
                )
                val sortedList = currentList.sortedWith(
                    compareByDescending<AdminPostItem> { it.isPinned }
                        .thenByDescending { if (it.isPinned) it.pinnedAt else 0L }
                )
                prefs[KEY_ADMIN_POSTS] = serializeAdminPostsJson(sortedList)
            }
        }
        return newPinState
    }

    suspend fun deleteAdminPost(postId: String) {
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_ADMIN_POSTS]
            val currentList = if (json == null) getDefaultAdminPosts().toMutableList() else parseAdminPostsJson(json).toMutableList()
            currentList.removeAll { it.id == postId }
            prefs[KEY_ADMIN_POSTS] = serializeAdminPostsJson(currentList)
        }
    }

    suspend fun syncAdminPosts(posts: List<AdminPostItem>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ADMIN_POSTS] = serializeAdminPostsJson(posts)
        }
    }

    suspend fun markItemsNotified(ids: Set<String>) {
        if (ids.isEmpty()) return
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_NOTIFIED_ITEM_IDS] ?: "[]"
            val arr = try { JSONArray(json) } catch (_: Exception) { JSONArray() }
            val existing = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                existing.add(arr.getString(i))
            }
            for (id in ids) {
                if (existing.add(id)) {
                    arr.put(id)
                }
            }
            prefs[KEY_NOTIFIED_ITEM_IDS] = arr.toString()
        }
    }

    suspend fun dismissAdminPost(postId: String) {
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_DISMISSED_POST_IDS] ?: "[]"
            val arr = try { JSONArray(json) } catch (_: Exception) { JSONArray() }
            var found = false
            for (i in 0 until arr.length()) {
                if (arr.getString(i) == postId) {
                    found = true
                    break
                }
            }
            if (!found) {
                arr.put(postId)
                prefs[KEY_DISMISSED_POST_IDS] = arr.toString()
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
                rewardCoins = 35,
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
                rewardCoins = 110,
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
                rewardCoins = 110,
                selectedDurationSeconds = 1800
            )
        )
    }

    private fun parseVideoTasksJson(json: String): List<VideoTaskItem> {
        val list = mutableListOf<VideoTaskItem>()
        val now = System.currentTimeMillis()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val rawLockedUntil = obj.optLong("lockedUntilMillis", 0L)
                val lockExpired = rawLockedUntil in 1..now
                val effectiveLockedUntil = if (lockExpired) 0L else rawLockedUntil
                val rawCompleted = obj.optBoolean("isCompleted", false)
                val effectiveCompleted = if (lockExpired || effectiveLockedUntil == 0L) false else rawCompleted
                val effectiveWatched = if (lockExpired) 0L else obj.optLong("watchedMillis", 0L)
                val selSec = obj.optInt("selectedDurationSeconds", 180)
                val rawCoins = obj.optInt("rewardCoins", 10)
                val tierCoins = WATCH_DURATION_TIERS.find { it.seconds == selSec }?.coins ?: when (rawCoins) {
                    5, 50 -> 10
                    10, 85 -> if (selSec == 300) 17 else 10
                    20, 175 -> 35
                    45, 360 -> 72
                    80, 550 -> 110
                    else -> rawCoins
                }

                list.add(
                    VideoTaskItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        title = obj.optString("title", "Video Task"),
                        channelName = obj.optString("channelName", "YouTube Creator"),
                        videoUrl = obj.optString("videoUrl", "https://www.youtube.com"),
                        thumbnailUrl = obj.optString("thumbnailUrl", ""),
                        durationSeconds = obj.optInt("durationSeconds", 600),
                        isLive = obj.optBoolean("isLive", false),
                        isCompleted = effectiveCompleted,
                        watchedMillis = effectiveWatched,
                        selectedDurationSeconds = selSec,
                        rewardCoins = tierCoins,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        lockedUntilMillis = effectiveLockedUntil,
                        isPinned = obj.optBoolean("isPinned", false),
                        pinnedAt = obj.optLong("pinnedAt", 0L)
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
                put("isPinned", item.isPinned)
                put("pinnedAt", item.pinnedAt)
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
                        joinedAtMillis = obj.optLong("joinedAtMillis", System.currentTimeMillis()),
                        transactionsJson = obj.optString("transactionsJson", "[]"),
                        likedTasksJson = obj.optString("likedTasksJson", "[]"),
                        commentCountsJson = obj.optString("commentCountsJson", "{}"),
                        taskLocksJson = obj.optString("taskLocksJson", "{}"),
                        lastUpdatedMillis = obj.optLong("lastUpdatedMillis", 0L)
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
                put("transactionsJson", u.transactionsJson)
                put("likedTasksJson", u.likedTasksJson)
                put("commentCountsJson", u.commentCountsJson)
                put("taskLocksJson", u.taskLocksJson)
                put("lastUpdatedMillis", u.lastUpdatedMillis)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun extractTaskLocksJson(tasks: List<VideoTaskItem>): String {
        val obj = JSONObject()
        val now = System.currentTimeMillis()
        for (t in tasks) {
            if (t.lockedUntilMillis > now) {
                obj.put(t.id, t.lockedUntilMillis)
            }
        }
        return obj.toString()
    }

    private fun applyUserProfileToSessionPrefs(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        user: UserProfile
    ) {
        prefs[KEY_WALLET_BALANCE] = user.coinsBalance
        prefs[KEY_TRANSACTIONS] = user.transactionsJson.ifBlank { "[]" }
        prefs[KEY_LIKED_TASKS] = user.likedTasksJson.ifBlank { "[]" }
        prefs[KEY_COMMENT_COUNTS] = user.commentCountsJson.ifBlank { "{}" }

        val locksObj = try { JSONObject(user.taskLocksJson.ifBlank { "{}" }) } catch (_: Exception) { JSONObject() }
        val now = System.currentTimeMillis()
        val currentTasks = parseVideoTasksJson(prefs[KEY_VIDEO_TASKS] ?: serializeVideoTasksJson(getDefaultTasks()))
        val updatedTasks = currentTasks.map { task ->
            val lockUntil = locksObj.optLong(task.id, 0L)
            val isLockedNow = lockUntil > now
            task.copy(
                isCompleted = isLockedNow,
                watchedMillis = 0L,
                lockedUntilMillis = if (isLockedNow) lockUntil else 0L
            )
        }
        prefs[KEY_VIDEO_TASKS] = serializeVideoTasksJson(updatedTasks)
    }

    private fun syncActiveUserIntoUsersList(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        newBalanceOverride: Int? = null,
        newTxJsonOverride: String? = null,
        newLikedJsonOverride: String? = null,
        newCommentsJsonOverride: String? = null,
        updatedTasks: List<VideoTaskItem>? = null,
        incrementCompletedTasks: Boolean = false
    ) {
        val currentEmail = prefs[KEY_CURRENT_USER_EMAIL] ?: return
        if (currentEmail.isBlank()) return
        val users = parseUsersJson(prefs[KEY_USERS] ?: "[]").toMutableList()
        val idx = users.indexOfFirst { it.email.equals(currentEmail, ignoreCase = true) }
        val balance = newBalanceOverride ?: (prefs[KEY_WALLET_BALANCE] ?: 0)
        val txJson = newTxJsonOverride ?: (prefs[KEY_TRANSACTIONS] ?: "[]")
        val likedJson = newLikedJsonOverride ?: (prefs[KEY_LIKED_TASKS] ?: "[]")
        val commentsJson = newCommentsJsonOverride ?: (prefs[KEY_COMMENT_COUNTS] ?: "{}")
        val tasksList = updatedTasks ?: parseVideoTasksJson(prefs[KEY_VIDEO_TASKS] ?: serializeVideoTasksJson(getDefaultTasks()))
        val locksJson = extractTaskLocksJson(tasksList)
        val now = System.currentTimeMillis()

        if (idx != -1) {
            val existing = users[idx]
            val newCompletedCount = if (incrementCompletedTasks) {
                existing.completedTasksCount + 1
            } else {
                maxOf(existing.completedTasksCount, tasksList.count { it.isCompleted })
            }
            users[idx] = existing.copy(
                coinsBalance = balance,
                completedTasksCount = newCompletedCount,
                transactionsJson = txJson,
                likedTasksJson = likedJson,
                commentCountsJson = commentsJson,
                taskLocksJson = locksJson,
                lastUpdatedMillis = now
            )
        } else {
            users.add(
                UserProfile(
                    userId = "usr_${Math.abs(currentEmail.hashCode()) % 100000}",
                    email = currentEmail,
                    name = currentEmail.substringBefore("@"),
                    coinsBalance = balance,
                    completedTasksCount = if (incrementCompletedTasks) 1 else tasksList.count { it.isCompleted },
                    joinedAtMillis = now,
                    transactionsJson = txJson,
                    likedTasksJson = likedJson,
                    commentCountsJson = commentsJson,
                    taskLocksJson = locksJson,
                    lastUpdatedMillis = now
                )
            )
        }
        prefs[KEY_USERS] = serializeUsersJson(users)
    }

    suspend fun syncRemoteTasksFromServer(remoteTasks: List<VideoTaskItem>) {
        context.dataStore.edit { prefs ->
            val currentJson = prefs[KEY_VIDEO_TASKS]
            val localList = if (currentJson.isNullOrBlank()) getDefaultTasks() else parseVideoTasksJson(currentJson)
            val localMap = localList.associateBy { it.id }

            // Merge remote authoritative task metadata with local user's personal lock/completion state
            val merged = remoteTasks.map { remote ->
                val local = localMap[remote.id]
                if (local != null) {
                    remote.copy(
                        isCompleted = local.isCompleted,
                        watchedMillis = local.watchedMillis,
                        lockedUntilMillis = local.lockedUntilMillis,
                        selectedDurationSeconds = local.selectedDurationSeconds
                    )
                } else {
                    remote
                }
            }.sortedWith(
                compareByDescending<VideoTaskItem> { it.isPinned }
                    .thenByDescending { if (it.isPinned) it.pinnedAt else 0L }
            )
            prefs[KEY_VIDEO_TASKS] = serializeVideoTasksJson(merged)
        }
    }

    suspend fun syncRemoteUsersFromServer(remoteUsers: List<UserProfile>, isAdmin: Boolean) {
        context.dataStore.edit { prefs ->
            val localUsers = parseUsersJson(prefs[KEY_USERS] ?: "[]").toMutableList()
            val currentEmail = prefs[KEY_CURRENT_USER_EMAIL]

            for (remote in remoteUsers) {
                if (remote.email.isBlank()) continue
                val idx = localUsers.indexOfFirst { it.email.equals(remote.email, ignoreCase = true) }
                if (idx == -1) {
                    localUsers.add(remote)
                    if (!isAdmin && currentEmail != null && currentEmail.equals(remote.email, ignoreCase = true)) {
                        applyUserProfileToSessionPrefs(prefs, remote)
                    }
                } else {
                    val local = localUsers[idx]
                    // If remote is newer or if we are pulling another user's stats into Admin
                    val isCurrentLoggedUser = !isAdmin && currentEmail != null && currentEmail.equals(remote.email, ignoreCase = true)
                    if (!isCurrentLoggedUser || remote.lastUpdatedMillis > local.lastUpdatedMillis) {
                        val mergedUser = remote.copy(
                            passwordHash = remote.passwordHash.ifBlank { local.passwordHash },
                            transactionsJson = if (remote.transactionsJson != "[]" || local.transactionsJson == "[]") remote.transactionsJson else local.transactionsJson,
                            likedTasksJson = if (remote.likedTasksJson != "[]" || local.likedTasksJson == "[]") remote.likedTasksJson else local.likedTasksJson,
                            commentCountsJson = if (remote.commentCountsJson != "{}" || local.commentCountsJson == "{}") remote.commentCountsJson else local.commentCountsJson,
                            taskLocksJson = if (remote.taskLocksJson != "{}" || local.taskLocksJson == "{}") remote.taskLocksJson else local.taskLocksJson
                        )
                        localUsers[idx] = mergedUser
                        if (isCurrentLoggedUser) {
                            applyUserProfileToSessionPrefs(prefs, mergedUser)
                        }
                    }
                }
            }
            prefs[KEY_USERS] = serializeUsersJson(localUsers)
        }
    }

    suspend fun syncRemotePayoutsFromServer(remotePayouts: List<PayoutRequest>) {
        context.dataStore.edit { prefs ->
            val localPayouts = parsePayoutRequestsJson(prefs[KEY_PAYOUT_REQUESTS] ?: "[]").toMutableList()
            val currentEmail = prefs[KEY_CURRENT_USER_EMAIL]
            var balanceChanged = false

            for (remote in remotePayouts) {
                val idx = localPayouts.indexOfFirst { it.id == remote.id }
                if (idx == -1) {
                    localPayouts.add(remote)
                } else {
                    val local = localPayouts[idx]
                    if (local.status == PayoutStatus.PENDING && remote.status != PayoutStatus.PENDING) {
                        localPayouts[idx] = remote
                        // If this payout belongs to the active user, record approval or refund!
                        if (currentEmail != null && currentEmail.equals(remote.userEmail, ignoreCase = true)) {
                            val txList = parseTransactionsJson(prefs[KEY_TRANSACTIONS] ?: "[]").toMutableList()
                            if (remote.status == PayoutStatus.APPROVED) {
                                txList.add(
                                    0,
                                    WalletTransaction(
                                        id = UUID.randomUUID().toString(),
                                        title = "✅ Payout APPROVED: ₹${String.format(java.util.Locale.US, "%.2f", remote.amountInr)} sent via ${remote.method} (${remote.adminNote ?: "Dispatched"})",
                                        coins = 0,
                                        timestampMillis = System.currentTimeMillis()
                                    )
                                )
                                prefs[KEY_TRANSACTIONS] = serializeTransactionsJson(txList)
                                balanceChanged = true
                            } else if (remote.status == PayoutStatus.REJECTED) {
                                val curBal = prefs[KEY_WALLET_BALANCE] ?: 0
                                prefs[KEY_WALLET_BALANCE] = curBal + remote.amountCoins
                                txList.add(
                                    0,
                                    WalletTransaction(
                                        id = UUID.randomUUID().toString(),
                                        title = "❌ Payout REJECTED (Refunded +${remote.amountCoins} coins): ${remote.adminNote ?: "Declined"}",
                                        coins = remote.amountCoins,
                                        timestampMillis = System.currentTimeMillis()
                                    )
                                )
                                prefs[KEY_TRANSACTIONS] = serializeTransactionsJson(txList)
                                balanceChanged = true
                            }
                        }
                    }
                }
            }
            localPayouts.sortByDescending { it.requestedAtMillis }
            prefs[KEY_PAYOUT_REQUESTS] = serializePayoutRequestsJson(localPayouts)
            if (balanceChanged) {
                syncActiveUserIntoUsersList(prefs)
            }
        }
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

    private fun parseAdminPostsJson(json: String): List<AdminPostItem> {
        val list = mutableListOf<AdminPostItem>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val rawId = obj.optString("id", UUID.randomUUID().toString())
                val rawTitle = obj.optString("title", "Announcement")
                    .replace("200 Coins = ₹10 INR", "1000 Coins = ₹10 INR")
                    .replace("5000 Coins = ₹10 INR", "1000 Coins = ₹10 INR")
                val rawMessage = obj.optString("message", "")
                    .replace("200 Coins = ₹10 INR", "1000 Coins = ₹10 INR")
                    .replace("5000 Coins = ₹10 INR", "1000 Coins = ₹10 INR")
                    .replace("3m=5c, 5m=10c, 10m=20c, 20m=45c, 30m=80c", "3m=10c, 5m=17c, 10m=35c, 20m=72c, 30m=110c")
                val rawTab = obj.optString("targetTab", "HOME").uppercase()
                val effectiveTab = if (rawTab == "ALL" || rawTab.isBlank()) "HOME" else rawTab
                list.add(
                    AdminPostItem(
                        id = rawId,
                        title = rawTitle,
                        message = rawMessage,
                        targetTab = effectiveTab,
                        postType = obj.optString("postType", "BANNER"),
                        actionUrl = obj.optString("actionUrl", ""),
                        imageUrl = obj.optString("imageUrl", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        isPinned = obj.optBoolean("isPinned", rawId == "default_welcome_banner"),
                        pinnedAt = obj.optLong("pinnedAt", if (rawId == "default_welcome_banner") 1700000000000L else 0L)
                    )
                )
            }
        } catch (_: Exception) {
            // fallback
        }
        return list
    }

    private fun serializeAdminPostsJson(posts: List<AdminPostItem>): String {
        val array = JSONArray()
        for (p in posts) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("title", p.title)
                put("message", p.message)
                put("targetTab", p.targetTab)
                put("postType", p.postType)
                put("actionUrl", p.actionUrl)
                put("imageUrl", p.imageUrl)
                put("createdAt", p.createdAt)
                put("isPinned", p.isPinned)
                put("pinnedAt", p.pinnedAt)
            }
            array.put(obj)
        }
        return array.toString()
    }
}
