package com.example.admin

import android.util.Log
import com.example.BuildConfig
import com.example.data.AdminPostItem
import com.example.data.DataStoreManager
import com.example.data.PayoutRequest
import com.example.data.PayoutStatus
import com.example.data.UserProfile
import com.example.data.VideoTaskItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    private val syncMutex = Mutex()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val noRedirectClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /**
     * Tests connectivity to the Google Drive Apps Script Web App.
     */
    suspend fun testConnection(serverUrl: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanUrl = serverUrl.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            return@withContext Pair(false, "Invalid URL. Please enter a valid HTTPS Google Script Web App URL.")
        }

        try {
            val request = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val code = response.code
            val body = response.body?.string() ?: ""
            val finalUrl = response.request.url.toString()

            if (finalUrl.contains("accounts.google.com")) {
                return@withContext Pair(
                    false,
                    "Google Script Access Locked (403): Please open script.google.com -> Deploy -> Manage deployments -> Edit (✏️) -> Set 'Who has access' to 'Anyone' and click Deploy."
                )
            }

            if (code in 200..299 || code == 302) {
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val folderName = json?.optString("folderName", "KingoKing_Server") ?: "KingoKing_Server"
                Pair(true, "Connected! Google Drive folder '$folderName' is live.")
            } else if (code == 403 || code == 401) {
                Pair(
                    false,
                    "HTTP 403 Permission Denied: Google Script mein 'Deploy -> Manage deployments -> Edit' par jaakar 'Who has access' ko 'Anyone' set karein!"
                )
            } else {
                Pair(false, "Server returned HTTP $code: ${body.take(120)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Test connection error: ${e.message}")
            Pair(false, "Connection error: ${e.message ?: "Failed to reach server"}")
        }
    }

    /**
     * Real-time two-way sync with Google Drive Server:
     * - Pulls latest tasks, posts, users, and payouts via GET first so no data is ever overwritten.
     * - In USER role: pulls Admin's tasks & posts in real time, and pushes only the user's own profile & payouts.
     * - In ADMIN role: pulls all users' live stats & payout requests, and pushes Admin's tasks, posts, and approvals.
     */
    suspend fun syncData(
        serverUrl: String,
        dataStoreManager: DataStoreManager,
        pushAdminContent: Boolean = (BuildConfig.APP_ROLE == "ADMIN")
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanUrl = serverUrl.trim()
        if (cleanUrl.isBlank()) {
            return@withContext Pair(false, "Server URL not configured.")
        }

        syncMutex.withLock {
            try {
                val isAdminRole = BuildConfig.APP_ROLE == "ADMIN"

                // STEP 1: Fetch current authoritative server state via GET
                val getReq = Request.Builder()
                    .url(cleanUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json, text/plain, */*")
                    .get()
                    .build()
                val getRes = httpClient.newCall(getReq).execute()
                val getCode = getRes.code
                val getBody = getRes.body?.string() ?: ""
                val getFinalUrl = getRes.request.url.toString()
                val getSucceeded = getRes.isSuccessful && !getFinalUrl.contains("accounts.google.com")
                val remoteJson = if (getSucceeded) {
                    try { JSONObject(getBody) } catch (_: Exception) { null }
                } else null

                val deletedTaskIds = dataStoreManager.deletedTaskIdsFlow.first()

                if (remoteJson != null) {
                    // 1A. Parse Remote Tasks
                    val remoteTasksArr = remoteJson.optJSONArray("tasks")
                    if (remoteTasksArr != null) {
                        val parsedTasks = mutableListOf<VideoTaskItem>()
                        for (i in 0 until remoteTasksArr.length()) {
                            val obj = remoteTasksArr.optJSONObject(i) ?: continue
                            val taskId = obj.optString("id")
                            if (taskId.isNotBlank() && (!isAdminRole || !deletedTaskIds.contains(taskId))) {
                                parsedTasks.add(
                                    VideoTaskItem(
                                        id = taskId,
                                        title = obj.optString("title", "YouTube Video Task"),
                                        channelName = obj.optString("channelName", "YouTube Creator"),
                                        videoUrl = obj.optString("videoUrl", "https://www.youtube.com"),
                                        thumbnailUrl = obj.optString("thumbnailUrl", ""),
                                        durationSeconds = obj.optInt("durationSeconds", 600),
                                        isLive = obj.optBoolean("isLive", false),
                                        rewardCoins = obj.optInt("rewardCoins", 10),
                                        selectedDurationSeconds = obj.optInt("selectedDurationSeconds", 180),
                                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                                        lockedUntilMillis = 0L,
                                        isPinned = obj.optBoolean("isPinned", false),
                                        pinnedAt = obj.optLong("pinnedAt", 0L)
                                    )
                                )
                            }
                        }
                        if (!isAdminRole && parsedTasks.isNotEmpty()) {
                            dataStoreManager.syncRemoteTasksFromServer(parsedTasks)
                        } else if (isAdminRole && !pushAdminContent && parsedTasks.isNotEmpty()) {
                            dataStoreManager.syncRemoteTasksFromServer(parsedTasks)
                        }
                    }

                    // 1B. Parse Remote Admin Posts / Banners
                    val remotePostsArr = remoteJson.optJSONArray("posts")
                    if (remotePostsArr != null) {
                        val parsedPosts = mutableListOf<AdminPostItem>()
                        for (i in 0 until remotePostsArr.length()) {
                            val obj = remotePostsArr.optJSONObject(i) ?: continue
                            val postId = obj.optString("id")
                            if (postId.isNotBlank()) {
                                val rawTab = obj.optString("targetTab", "HOME").uppercase()
                                val remoteTab = if (rawTab == "ALL" || rawTab.isBlank()) "HOME" else rawTab
                                parsedPosts.add(
                                    AdminPostItem(
                                        id = postId,
                                        title = obj.optString("title", "Announcement"),
                                        message = obj.optString("message", ""),
                                        targetTab = remoteTab,
                                        postType = obj.optString("postType", "BANNER"),
                                        actionUrl = obj.optString("actionUrl", ""),
                                        imageUrl = obj.optString("imageUrl", ""),
                                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                                        isPinned = obj.optBoolean("isPinned", false),
                                        pinnedAt = obj.optLong("pinnedAt", 0L)
                                    )
                                )
                            }
                        }
                        if (!isAdminRole && parsedPosts.isNotEmpty()) {
                            dataStoreManager.syncAdminPosts(parsedPosts)
                        } else if (isAdminRole && !pushAdminContent && parsedPosts.isNotEmpty()) {
                            dataStoreManager.syncAdminPosts(parsedPosts)
                        }
                    }

                    // 1C. Parse Remote Users (Per-User Statistics, Coins, Task Locks, Transactions)
                    val remoteUsersArr = remoteJson.optJSONArray("users")
                    if (remoteUsersArr != null) {
                        val parsedUsers = mutableListOf<UserProfile>()
                        for (i in 0 until remoteUsersArr.length()) {
                            val obj = remoteUsersArr.optJSONObject(i) ?: continue
                            val email = obj.optString("email", "").trim().lowercase()
                            if (email.isNotBlank()) {
                                parsedUsers.add(
                                    UserProfile(
                                        userId = obj.optString("userId", "usr_${Math.abs(email.hashCode()) % 100000}"),
                                        email = email,
                                        name = obj.optString("name", email.substringBefore("@")),
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
                        }
                        if (parsedUsers.isNotEmpty()) {
                            dataStoreManager.syncRemoteUsersFromServer(parsedUsers, isAdmin = isAdminRole)
                        }
                    }

                    // 1D. Parse Remote Payout Requests
                    val remotePayoutsArr = remoteJson.optJSONArray("payouts")
                    if (remotePayoutsArr != null) {
                        val parsedPayouts = mutableListOf<PayoutRequest>()
                        for (i in 0 until remotePayoutsArr.length()) {
                            val obj = remotePayoutsArr.optJSONObject(i) ?: continue
                            val id = obj.optString("id")
                            if (id.isNotBlank()) {
                                val statusStr = obj.optString("status", PayoutStatus.PENDING.name)
                                val status = try { PayoutStatus.valueOf(statusStr) } catch (_: Exception) { PayoutStatus.PENDING }
                                parsedPayouts.add(
                                    PayoutRequest(
                                        id = id,
                                        userId = obj.optString("userId", ""),
                                        userEmail = obj.optString("userEmail", ""),
                                        amountCoins = obj.optInt("amountCoins", 0),
                                        amountInr = obj.optDouble("amountInr", 0.0),
                                        method = obj.optString("method", "UPI"),
                                        destination = obj.optString("destination", ""),
                                        status = status,
                                        requestedAtMillis = obj.optLong("requestedAtMillis", System.currentTimeMillis()),
                                        processedAtMillis = if (obj.has("processedAtMillis")) obj.optLong("processedAtMillis") else null,
                                        adminNote = obj.optString("adminNote", "")
                                    )
                                )
                            }
                        }
                        if (parsedPayouts.isNotEmpty()) {
                            dataStoreManager.syncRemotePayoutsFromServer(parsedPayouts)
                        }
                    }
                }

                // STEP 2: Push merged state back to Google Drive
                val updatedTasks = dataStoreManager.videoTasksFlow.first()
                val updatedPosts = dataStoreManager.adminPostsFlow.first()
                val updatedUsers = dataStoreManager.usersFlow.first()
                val updatedPayouts = dataStoreManager.payoutRequestsFlow.first()

                val syncPayload = JSONObject().apply {
                    put("action", "sync_all")
                    put("role", BuildConfig.APP_ROLE)

                    // Only ADMIN pushes tasks and posts so User App never overwrites Admin updates
                    if (isAdminRole && pushAdminContent) {
                        val tasksArr = JSONArray()
                        for (t in updatedTasks) {
                            tasksArr.put(JSONObject().apply {
                                put("id", t.id)
                                put("title", t.title)
                                put("channelName", t.channelName)
                                put("videoUrl", t.videoUrl)
                                put("thumbnailUrl", t.thumbnailUrl)
                                put("rewardCoins", t.rewardCoins)
                                put("durationSeconds", t.durationSeconds)
                                put("isLive", t.isLive)
                                put("selectedDurationSeconds", t.selectedDurationSeconds)
                                put("isCompleted", false)
                                put("lockedUntilMillis", 0L)
                                put("createdAt", t.createdAt)
                                put("isPinned", t.isPinned)
                                put("pinnedAt", t.pinnedAt)
                            })
                        }
                        put("tasks", tasksArr)

                        val postsArr = JSONArray()
                        for (post in updatedPosts) {
                            postsArr.put(JSONObject().apply {
                                put("id", post.id)
                                put("title", post.title)
                                put("message", post.message)
                                put("targetTab", post.targetTab)
                                put("postType", post.postType)
                                put("actionUrl", post.actionUrl)
                                put("imageUrl", post.imageUrl)
                                put("createdAt", post.createdAt)
                                put("isPinned", post.isPinned)
                                put("pinnedAt", post.pinnedAt)
                            })
                        }
                        put("posts", postsArr)
                    }

                    val usersArr = JSONArray()
                    for (u in updatedUsers) {
                        usersArr.put(JSONObject().apply {
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
                        })
                    }
                    put("users", usersArr)

                    val payoutsArr = JSONArray()
                    for (p in updatedPayouts) {
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
                            p.processedAtMillis?.let { put("processedAtMillis", it) }
                            put("adminNote", p.adminNote ?: "")
                        })
                    }
                    put("payouts", payoutsArr)
                }

                // Use text/plain; charset=utf-8 as recommended by Google Apps Script Web Apps
                // and handle 302 redirect manually so OkHttp does not fail on script.googleusercontent.com
                val requestBody = syncPayload.toString().toRequestBody("text/plain; charset=utf-8".toMediaType())
                val postReq = Request.Builder()
                    .url(cleanUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json, text/plain, */*")
                    .post(requestBody)
                    .build()

                val initialPostRes = noRedirectClient.newCall(postReq).execute()
                val initialCode = initialPostRes.code
                val redirectLocation = initialPostRes.header("Location")

                var postSucceeded = false
                var postBody = ""
                var finalPostCode = initialCode

                if (initialCode in 301..308 && !redirectLocation.isNullOrBlank()) {
                    initialPostRes.close()
                    if (redirectLocation.contains("script.googleusercontent.com")) {
                        // Google Apps Script executes doPost(e) BEFORE returning 302 to script.googleusercontent.com!
                        postSucceeded = true
                        try {
                            val echoReq = Request.Builder()
                                .url(redirectLocation)
                                .header("User-Agent", USER_AGENT)
                                .get()
                                .build()
                            val echoRes = httpClient.newCall(echoReq).execute()
                            if (echoRes.isSuccessful) {
                                postBody = echoRes.body?.string() ?: ""
                            }
                            echoRes.close()
                        } catch (_: Exception) {}
                    } else if (redirectLocation.contains("accounts.google.com")) {
                        finalPostCode = 403
                    } else {
                        val followReq = Request.Builder()
                            .url(redirectLocation)
                            .header("User-Agent", USER_AGENT)
                            .get()
                            .build()
                        val followRes = httpClient.newCall(followReq).execute()
                        finalPostCode = followRes.code
                        postBody = followRes.body?.string() ?: ""
                        postSucceeded = followRes.isSuccessful && !followRes.request.url.toString().contains("accounts.google.com")
                    }
                } else {
                    postBody = initialPostRes.body?.string() ?: ""
                    postSucceeded = initialPostRes.isSuccessful
                }

                if (postSucceeded || getSucceeded) {
                    val json = try { JSONObject(postBody) } catch (_: Exception) { null }
                    if (json == null || json.optBoolean("success", true)) {
                        dataStoreManager.setCloudServerStatus(
                            "Live Connected (${updatedTasks.size} tasks, ${updatedUsers.size} users)"
                        )
                        Pair(true, "Synced with Google Drive Server!")
                    } else {
                        val errMsg = json.optString("error", "Unknown error from Drive server")
                        Pair(false, "Drive response: $errMsg")
                    }
                } else if (finalPostCode == 403 || getCode == 403 || finalPostCode == 401 || getCode == 401) {
                    dataStoreManager.setCloudServerStatus("Permission Needed (Set 'Anyone' in Script)")
                    Pair(
                        false,
                        "HTTP 403: Google Script mein 'Deploy -> Manage deployments -> Edit (✏️)' par jaakar 'Who has access' ko 'Anyone' karein!"
                    )
                } else {
                    Pair(false, "Server HTTP error $finalPostCode")
                }
            } catch (e: Exception) {
                Pair(false, "Sync failed: ${e.message}")
            }
        }
    }

    /**
     * Complete, copy-paste ready Google Apps Script that turns Google Drive
     * into a free 24/7 real-time cloud server with per-user data & task sync.
     */
    fun getGoogleAppsScriptTemplate(): String {
        return """
// =========================================================================
// KINGO KING - GOOGLE DRIVE 24/7 REAL-TIME CLOUD SERVER SCRIPT
// =========================================================================
// STEP-BY-STEP SETUP INSTRUCTIONS:
// 1. Open https://script.google.com/ in Chrome/Browser and sign in.
// 2. Click "New project" (top-left).
// 3. Delete existing code in Code.gs and PASTE this entire script.
// 4. Click "Deploy" (top-right blue button) -> "New deployment".
// 5. Click the Gear icon ⚙️ next to "Select type" -> choose "Web app".
// 6. Set Description: "Kingo King Live Server".
// 7. Set "Execute as": "Me (your email)".
// 8. Set "Who has access": "Anyone" (IMPORTANT!).
// 9. Click "Deploy" -> "Authorize access" -> Select your Google Account
//    -> Click "Advanced" -> "Go to Untitled project (unsafe)" -> "Allow".
// 10. Copy the generated "Web app URL" (ends with /exec).
// 11. Paste that URL into Kingo Admin App AND Kingo King User App!
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
    var posts = JSON.parse(getFileContent("posts.json", "[]"));
    var users = JSON.parse(getFileContent("users.json", "[]"));
    var payouts = JSON.parse(getFileContent("payouts.json", "[]"));
    
    var response = {
      "status": "online",
      "server": "Kingo King Google Drive Server",
      "folderName": FOLDER_NAME,
      "tasks": tasks,
      "posts": posts,
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
  var lock = LockService.getScriptLock();
  try {
    lock.waitLock(10000);
    var postData = JSON.parse(e.postData.contents);
    var action = postData.action || "sync_all";
    
    if (action === "sync_all") {
      if (postData.tasks) {
        saveFileContent("tasks.json", JSON.stringify(postData.tasks));
      }
      if (postData.posts) {
        saveFileContent("posts.json", JSON.stringify(postData.posts));
      }
      if (postData.users) {
        var existingUsers = JSON.parse(getFileContent("users.json", "[]"));
        var userMap = {};
        for (var i = 0; i < existingUsers.length; i++) {
          var u = existingUsers[i];
          if (u.email) userMap[u.email.toLowerCase()] = u;
        }
        for (var j = 0; j < postData.users.length; j++) {
          var incoming = postData.users[j];
          if (!incoming.email) continue;
          var key = incoming.email.toLowerCase();
          var prev = userMap[key];
          if (!prev || (incoming.lastUpdatedMillis || 0) >= (prev.lastUpdatedMillis || 0)) {
            userMap[key] = incoming;
          }
        }
        var mergedUsers = [];
        for (var k in userMap) {
          mergedUsers.push(userMap[k]);
        }
        saveFileContent("users.json", JSON.stringify(mergedUsers));
      }
      if (postData.payouts) {
        var existingPayouts = JSON.parse(getFileContent("payouts.json", "[]"));
        var payoutMap = {};
        for (var pIdx = 0; pIdx < existingPayouts.length; pIdx++) {
          var ep = existingPayouts[pIdx];
          if (ep.id) payoutMap[ep.id] = ep;
        }
        for (var qIdx = 0; qIdx < postData.payouts.length; qIdx++) {
          var ip = postData.payouts[qIdx];
          if (!ip.id) continue;
          var oldP = payoutMap[ip.id];
          if (!oldP || oldP.status === "PENDING" || postData.role === "ADMIN") {
            payoutMap[ip.id] = ip;
          }
        }
        var mergedPayouts = [];
        for (var pk in payoutMap) {
          mergedPayouts.push(payoutMap[pk]);
        }
        saveFileContent("payouts.json", JSON.stringify(mergedPayouts));
      }
      
      var currentTasks = JSON.parse(getFileContent("tasks.json", "[]"));
      var currentPosts = JSON.parse(getFileContent("posts.json", "[]"));
      var currentUsers = JSON.parse(getFileContent("users.json", "[]"));
      var currentPayouts = JSON.parse(getFileContent("payouts.json", "[]"));
      return ContentService.createTextOutput(JSON.stringify({
        "success": true,
        "message": "Synced with Google Drive",
        "tasks": currentTasks,
        "posts": currentPosts,
        "users": currentUsers,
        "payouts": currentPayouts
      })).setMimeType(ContentService.MimeType.JSON);
    }
    
    return ContentService.createTextOutput(JSON.stringify({ "success": true }))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ "success": false, "error": err.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  } finally {
    try { lock.releaseLock(); } catch (ignore) {}
  }
}
""".trimIndent()
    }
}
