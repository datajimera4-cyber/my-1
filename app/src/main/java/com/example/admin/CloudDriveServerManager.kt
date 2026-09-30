package com.example.admin

import android.util.Log
import com.example.data.DataStoreManager
import com.example.data.PayoutRequest
import com.example.data.PayoutStatus
import com.example.data.UserProfile
import com.example.data.VideoTaskItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object CloudDriveServerManager {

    private const val TAG = "CloudDriveServer"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Tests connectivity to the Google Drive Apps Script Web App.
     */
    suspend fun testConnection(serverUrl: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanUrl = serverUrl.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            return@withContext Pair(false, "Invalid URL. Please enter a valid HTTP/HTTPS Web App URL.")
        }

        try {
            val request = Request.Builder()
                .url(cleanUrl)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val code = response.code
            val body = response.body?.string() ?: ""

            if (code in 200..299 || code == 302) {
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val status = json?.optString("status", "success") ?: "online"
                val folderName = json?.optString("folderName", "KingoKing_Server")
                Pair(true, "Connected successfully! Google Drive folder: '$folderName' active.")
            } else {
                Pair(false, "Server returned HTTP $code: ${body.take(120)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Test connection error: ${e.message}")
            Pair(false, "Connection error: ${e.message ?: "Failed to reach server"}")
        }
    }

    /**
     * Full two-way sync:
     * 1. Pulls tasks from Google Drive server into local DataStore
     * 2. Pushes local payout requests and tasks to Google Drive server
     */
    suspend fun syncData(serverUrl: String, dataStoreManager: DataStoreManager): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanUrl = serverUrl.trim()
        if (cleanUrl.isBlank()) {
            return@withContext Pair(false, "Server URL not configured.")
        }

        try {
            val localTasks = dataStoreManager.videoTasksFlow.first()
            val localPayouts = dataStoreManager.payoutRequestsFlow.first()
            val localUsers = dataStoreManager.usersFlow.first()

            val syncPayload = JSONObject().apply {
                put("action", "sync_all")

                val tasksArr = JSONArray()
                for (t in localTasks) {
                    tasksArr.put(JSONObject().apply {
                        put("id", t.id)
                        put("title", t.title)
                        put("channelName", t.channelName)
                        put("videoUrl", t.videoUrl)
                        put("thumbnailUrl", t.thumbnailUrl)
                        put("rewardCoins", t.rewardCoins)
                        put("durationSeconds", t.durationSeconds)
                        put("selectedDurationSeconds", t.selectedDurationSeconds)
                        put("isCompleted", t.isCompleted)
                        put("lockedUntilMillis", t.lockedUntilMillis)
                    })
                }
                put("tasks", tasksArr)

                val payoutsArr = JSONArray()
                for (p in localPayouts) {
                    payoutsArr.put(JSONObject().apply {
                        put("id", p.id)
                        put("userId", p.userId)
                        put("userEmail", p.userEmail)
                        put("amountCoins", p.amountCoins)
                        put("amountInr", p.amountInr)
                        put("method", p.method)
                        put("destination", p.destination)
                        put("status", p.status.name)
                        put("requestedAtMillis", p.requestedAtMillis)
                        put("adminNote", p.adminNote ?: "")
                    })
                }
                put("payouts", payoutsArr)
            }

            val requestBody = syncPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(cleanUrl)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                if (json != null && json.optBoolean("success", true)) {
                    // Pull remote tasks into local dataStore (skipping tasks deleted locally)
                    val deletedIds = dataStoreManager.deletedTaskIdsFlow.first()
                    val remoteTasksArr = json.optJSONArray("tasks")
                    if (remoteTasksArr != null && remoteTasksArr.length() > 0) {
                        for (i in 0 until remoteTasksArr.length()) {
                            val obj = remoteTasksArr.getJSONObject(i)
                            val taskId = obj.optString("id")
                            if (taskId.isNotBlank() && !deletedIds.contains(taskId) && localTasks.none { it.id == taskId }) {
                                val newTask = VideoTaskItem(
                                    id = taskId,
                                    title = obj.optString("title", "Remote Task"),
                                    channelName = obj.optString("channelName", "YouTube Creator"),
                                    videoUrl = obj.optString("videoUrl", "https://www.youtube.com"),
                                    thumbnailUrl = obj.optString("thumbnailUrl", ""),
                                    durationSeconds = obj.optInt("durationSeconds", 600),
                                    rewardCoins = obj.optInt("rewardCoins", 10),
                                    selectedDurationSeconds = obj.optInt("selectedDurationSeconds", 180),
                                    lockedUntilMillis = obj.optLong("lockedUntilMillis", 0L)
                                )
                                dataStoreManager.addVideoTask(newTask)
                            }
                        }
                    }

                    dataStoreManager.setCloudServerStatus("Synced with Google Drive (${localTasks.size} tasks, ${localPayouts.size} payouts)")
                    Pair(true, "Synced successfully with Google Drive! Everything is up to date.")
                } else {
                    val errMsg = json?.optString("error", "Unknown error from Drive server")
                    Pair(false, "Drive response: $errMsg")
                }
            } else {
                Pair(false, "Server HTTP error ${response.code}")
            }
        } catch (e: Exception) {
            Pair(false, "Sync failed: ${e.message}")
        }
    }

    /**
     * Complete, copy-paste ready Google Apps Script that turns Google Drive
     * into a free 24/7 online server with auto-folder creation.
     */
    fun getGoogleAppsScriptTemplate(): String {
        return """
// =========================================================================
// KINGO KING - GOOGLE DRIVE 24/7 FREE CLOUD SERVER SCRIPT
// =========================================================================
// INSTRUCTIONS:
// 1. Open https://script.google.com/ in your browser.
// 2. Click "New project".
// 3. Paste this entire code into the editor (replace everything).
// 4. Click "Deploy" > "New deployment".
// 5. Select type "Web app".
// 6. Set Description: "Kingo King Drive Server".
// 7. Execute as: "Me (your google account)".
// 8. Who has access: "Anyone".
// 9. Click "Deploy", authorize permissions, and copy the Web App URL!
// 10. Paste the Web App URL into the Kingo King Admin App -> "Google Drive Server".
// =========================================================================

var FOLDER_NAME = "KingoKing_Server";

function getOrCreateFolder() {
  var folders = DriveApp.getFoldersByName(FOLDER_NAME);
  if (folders.hasNext()) {
    return folders.next();
  }
  return DriveApp.createFolder(FOLDER_NAME);
}

function getFileContent(fileName, defaultContent) {
  var folder = getOrCreateFolder();
  var files = folder.getFilesByName(fileName);
  if (files.hasNext()) {
    return files.next().getBlob().getDataAsString();
  }
  folder.createFile(fileName, defaultContent);
  return defaultContent;
}

function saveFileContent(fileName, content) {
  var folder = getOrCreateFolder();
  var files = folder.getFilesByName(fileName);
  if (files.hasNext()) {
    var file = files.next();
    file.setContent(content);
    return file;
  }
  return folder.createFile(fileName, content);
}

function doGet(e) {
  try {
    var tasks = JSON.parse(getFileContent("tasks.json", "[]"));
    var users = JSON.parse(getFileContent("users.json", "[]"));
    var payouts = JSON.parse(getFileContent("payouts.json", "[]"));
    
    var response = {
      "status": "online",
      "server": "Google Drive Cloud Server",
      "folderName": FOLDER_NAME,
      "tasks": tasks,
      "users": users,
      "payouts": payouts,
      "timestamp": new Date().toISOString()
    };
    
    return ContentService.createTextOutput(JSON.stringify(response))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ "status": "error", "error": err.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

function doPost(e) {
  try {
    var postData = JSON.parse(e.postData.contents);
    var action = postData.action || "sync_all";
    
    if (action === "sync_all") {
      if (postData.tasks) saveFileContent("tasks.json", JSON.stringify(postData.tasks));
      if (postData.payouts) saveFileContent("payouts.json", JSON.stringify(postData.payouts));
      if (postData.users) saveFileContent("users.json", JSON.stringify(postData.users));
      
      var currentTasks = JSON.parse(getFileContent("tasks.json", "[]"));
      return ContentService.createTextOutput(JSON.stringify({
        "success": true,
        "message": "Data saved to Google Drive",
        "tasks": currentTasks
      })).setMimeType(ContentService.MimeType.JSON);
    }
    
    if (action === "add_task") {
      var tasks = JSON.parse(getFileContent("tasks.json", "[]"));
      tasks.unshift(postData.task);
      saveFileContent("tasks.json", JSON.stringify(tasks));
      return ContentService.createTextOutput(JSON.stringify({ "success": true, "task": postData.task }))
        .setMimeType(ContentService.MimeType.JSON);
    }
    
    if (action === "submit_payout") {
      var payouts = JSON.parse(getFileContent("payouts.json", "[]"));
      payouts.unshift(postData.payout);
      saveFileContent("payouts.json", JSON.stringify(payouts));
      return ContentService.createTextOutput(JSON.stringify({ "success": true, "payoutId": postData.payout.id }))
        .setMimeType(ContentService.MimeType.JSON);
    }
    
    return ContentService.createTextOutput(JSON.stringify({ "success": true }))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ "success": false, "error": err.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}
""".trimIndent()
    }
}
