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
    }

    val liveSearchModeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_LIVE_SEARCH_MODE] ?: false
    }

    val selectedTaskIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_TASK_ID]
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
            prefs[KEY_TASK_COMPLETED] = true

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
                list.add(
                    0,
                    WalletTransaction(
                        id = UUID.randomUUID().toString(),
                        title = "Withdrawal to $method ($destination) [₹$formattedInr]",
                        coins = -coins,
                        timestampMillis = System.currentTimeMillis()
                    )
                )
                prefs[KEY_TRANSACTIONS] = serializeTransactionsJson(list)
                success = true
            }
        }
        return success
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
                currentList[index] = t.copy(isCompleted = true, rewardCoins = rewardCoins)
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
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
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
}
