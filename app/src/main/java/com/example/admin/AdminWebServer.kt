package com.example.admin

import android.content.Context
import android.util.Log
import com.example.data.DataStoreManager
import com.example.data.PayoutStatus
import com.example.data.VideoTaskItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

object AdminWebServer {

    private const val TAG = "AdminWebServer"
    const val PORT = 8888

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var appContext: Context? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    fun getLocalIpAddress(context: Context): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    fun startServer(context: Context, dataStoreManager: DataStoreManager, onStatusChanged: (Boolean, String) -> Unit) {
        appContext = context.applicationContext
        if (isRunning) {
            val ip = getLocalIpAddress(context)
            onStatusChanged(true, "http://$ip:$PORT")
            return
        }

        serverJob = scope.launch {
            try {
                val socket = ServerSocket(PORT)
                serverSocket = socket
                isRunning = true
                val ip = getLocalIpAddress(context)
                val url = "http://$ip:$PORT"
                Log.d(TAG, "Admin Web Server running at $url")
                onStatusChanged(true, url)

                while (isActive && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        scope.launch {
                            handleClient(client, dataStoreManager)
                        }
                    } catch (e: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server: ${e.message}")
                isRunning = false
                onStatusChanged(false, "")
            }
        }
    }

    fun stopServer(onStatusChanged: (Boolean, String) -> Unit) {
        isRunning = false
        try {
            serverSocket?.close()
            serverSocket = null
            serverJob?.cancel()
            serverJob = null
        } catch (_: Exception) {}
        onStatusChanged(false, "")
    }

    private suspend fun handleClient(client: Socket, dataStoreManager: DataStoreManager) {
        try {
            client.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val out = s.getOutputStream()

                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0]
                val pathWithQuery = parts[1]
                val path = pathWithQuery.substringBefore("?")

                // Read headers
                val headers = mutableMapOf<String, String>()
                var contentLength = 0
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line.isNullOrBlank()) break
                    val headerParts = line!!.split(":", limit = 2)
                    if (headerParts.size == 2) {
                        val key = headerParts[0].trim().lowercase()
                        val value = headerParts[1].trim()
                        headers[key] = value
                        if (key == "content-length") {
                            contentLength = value.toIntOrNull() ?: 0
                        }
                    }
                }

                // Read body if POST
                val body = if (contentLength > 0) {
                    val buffer = CharArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val r = reader.read(buffer, readTotal, contentLength - readTotal)
                        if (r <= 0) break
                        readTotal += r
                    }
                    String(buffer, 0, readTotal)
                } else ""

                when {
                    method == "GET" && (path == "/" || path == "/admin") -> {
                        serveHtml(out)
                    }
                    method == "GET" && path == "/api/data" -> {
                        serveApiData(out, dataStoreManager)
                    }
                    method == "POST" && path == "/api/tasks/add" -> {
                        handleAddTask(body, out, dataStoreManager)
                    }
                    method == "POST" && path == "/api/tasks/delete" -> {
                        handleDeleteTask(body, out, dataStoreManager)
                    }
                    method == "POST" && path == "/api/payouts/approve" -> {
                        handleApprovePayout(body, out, dataStoreManager)
                    }
                    method == "POST" && path == "/api/payouts/reject" -> {
                        handleRejectPayout(body, out, dataStoreManager)
                    }
                    method == "POST" && path == "/api/users/update_coins" -> {
                        handleUpdateCoins(body, out, dataStoreManager)
                    }
                    else -> {
                        val response = "HTTP/1.1 404 Not Found\r\nContent-Length: 9\r\n\r\nNot Found"
                        out.write(response.toByteArray())
                        out.flush()
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun serveApiData(out: OutputStream, dataStoreManager: DataStoreManager) {
        val tasks = dataStoreManager.videoTasksFlow.first()
        val users = dataStoreManager.usersFlow.first()
        val payouts = dataStoreManager.payoutRequestsFlow.first()
        val balance = dataStoreManager.walletBalanceFlow.first()

        val json = JSONObject().apply {
            val tasksArr = JSONArray()
            for (t in tasks) {
                tasksArr.put(JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("channelName", t.channelName)
                    put("videoUrl", t.videoUrl)
                    put("thumbnailUrl", t.thumbnailUrl)
                    put("rewardCoins", t.rewardCoins)
                    put("selectedDurationSeconds", t.selectedDurationSeconds)
                    put("isCompleted", t.isCompleted)
                })
            }
            put("tasks", tasksArr)

            val usersArr = JSONArray()
            for (u in users) {
                usersArr.put(JSONObject().apply {
                    put("userId", u.userId)
                    put("email", u.email)
                    put("name", u.name)
                    put("coinsBalance", u.coinsBalance)
                })
            }
            put("users", usersArr)

            val payoutsArr = JSONArray()
            for (p in payouts) {
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

            put("currentWalletBalance", balance)
            put("pendingPayoutsCount", payouts.count { it.status == PayoutStatus.PENDING })
        }

        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nAccess-Control-Allow-Origin: *\r\nContent-Length: ${bytes.size}\r\n\r\n"
        out.write(header.toByteArray())
        out.write(bytes)
        out.flush()
    }

    private suspend fun handleAddTask(body: String, out: OutputStream, dataStoreManager: DataStoreManager) {
        try {
            val json = JSONObject(body)
            val title = json.optString("title", "New YouTube Task")
            val channel = json.optString("channelName", "YouTube Creator")
            val url = json.optString("videoUrl", "https://www.youtube.com")
            val coins = json.optInt("rewardCoins", 10)
            val duration = json.optInt("durationSeconds", 180)

            val videoId = com.example.util.TitleMatcher.extractVideoId(url) ?: ""
            val thumb = if (videoId.isNotEmpty()) "https://img.youtube.com/vi/$videoId/hqdefault.jpg" else ""

            val newTask = VideoTaskItem(
                id = "task_${System.currentTimeMillis()}",
                title = title,
                channelName = channel,
                videoUrl = url,
                thumbnailUrl = thumb,
                durationSeconds = duration,
                rewardCoins = coins,
                selectedDurationSeconds = duration
            )
            dataStoreManager.addVideoTask(newTask)

            val resBytes = "{\"success\":true}".toByteArray()
            out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${resBytes.size}\r\n\r\n".toByteArray())
            out.write(resBytes)
            out.flush()
        } catch (e: Exception) {
            sendError(out, e.message ?: "Invalid body")
        }
    }

    private suspend fun handleDeleteTask(body: String, out: OutputStream, dataStoreManager: DataStoreManager) {
        try {
            val json = JSONObject(body)
            val taskId = json.getString("id")
            dataStoreManager.adminDeleteVideoTask(taskId)
            val resBytes = "{\"success\":true}".toByteArray()
            out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${resBytes.size}\r\n\r\n".toByteArray())
            out.write(resBytes)
            out.flush()
        } catch (e: Exception) {
            sendError(out, e.message ?: "Invalid body")
        }
    }

    private suspend fun handleApprovePayout(body: String, out: OutputStream, dataStoreManager: DataStoreManager) {
        try {
            val json = JSONObject(body)
            val reqId = json.getString("id")
            val note = json.optString("note", "Approved & Dispatched")
            dataStoreManager.approvePayout(reqId, note)
            val resBytes = "{\"success\":true}".toByteArray()
            out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${resBytes.size}\r\n\r\n".toByteArray())
            out.write(resBytes)
            out.flush()
        } catch (e: Exception) {
            sendError(out, e.message ?: "Invalid body")
        }
    }

    private suspend fun handleRejectPayout(body: String, out: OutputStream, dataStoreManager: DataStoreManager) {
        try {
            val json = JSONObject(body)
            val reqId = json.getString("id")
            val reason = json.optString("reason", "Declined by Admin")
            dataStoreManager.rejectPayout(reqId, reason)
            val resBytes = "{\"success\":true}".toByteArray()
            out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${resBytes.size}\r\n\r\n".toByteArray())
            out.write(resBytes)
            out.flush()
        } catch (e: Exception) {
            sendError(out, e.message ?: "Invalid body")
        }
    }

    private suspend fun handleUpdateCoins(body: String, out: OutputStream, dataStoreManager: DataStoreManager) {
        try {
            val json = JSONObject(body)
            val email = json.getString("email")
            val coins = json.getInt("coins")
            dataStoreManager.adminUpdateUserCoins(email, coins)
            val resBytes = "{\"success\":true}".toByteArray()
            out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${resBytes.size}\r\n\r\n".toByteArray())
            out.write(resBytes)
            out.flush()
        } catch (e: Exception) {
            sendError(out, e.message ?: "Invalid body")
        }
    }

    private fun sendError(out: OutputStream, msg: String) {
        val bytes = "{\"success\":false,\"error\":\"$msg\"}".toByteArray()
        out.write("HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\n\r\n".toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun serveHtml(out: OutputStream) {
        val htmlBytes = try {
            appContext?.assets?.open("admin_dashboard.html")?.use { it.readBytes() }
        } catch (_: Exception) { null } ?: "<html><body><h1>Kingo King Admin</h1><p>Dashboard ready.</p></body></html>".toByteArray(Charsets.UTF_8)

        val header = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${htmlBytes.size}\r\n\r\n"
        out.write(header.toByteArray())
        out.write(htmlBytes)
        out.flush()
    }
}
