package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object OEmbedFetcher {

    /**
     * Fetches YouTube video metadata (title, author_name, author_url, thumbnail_url)
     * using the public no-key oEmbed endpoint on Dispatchers.IO.
     */
    suspend fun fetchOEmbed(rawVideoUrl: String): OEmbedResult = withContext(Dispatchers.IO) {
        val trimmedUrl = rawVideoUrl.trim()

        if (trimmedUrl.isEmpty() || trimmedUrl == "PASTE_MY_YOUTUBE_LINK_HERE") {
            return@withContext OEmbedResult.Error(
                "Task URL is currently 'PASTE_MY_YOUTUBE_LINK_HERE'. Please paste a valid YouTube video link in SampleTask.kt or tap 'Use Demo Video'."
            )
        }

        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            return@withContext OEmbedResult.Error(
                "Invalid video URL format. Expected an HTTP/HTTPS YouTube link."
            )
        }

        var connection: HttpURLConnection? = null
        try {
            val encodedUrl = URLEncoder.encode(trimmedUrl, "UTF-8")
            val endpoint = "https://www.youtube.com/oembed?url=$encodedUrl&format=json"
            val url = URL(endpoint)

            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "WatchEarn-Android/1.0")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val jsonText = reader.use { it.readText() }
                val jsonObject = JSONObject(jsonText)

                val title = jsonObject.optString("title", "").trim()
                val authorName = jsonObject.optString("author_name", "").trim()
                val authorUrl = jsonObject.optString("author_url", "")
                val thumbnailUrl = jsonObject.optString("thumbnail_url", "")

                if (title.isNotEmpty()) {
                    OEmbedResult.Success(
                        title = title,
                        authorName = authorName,
                        authorUrl = authorUrl,
                        thumbnailUrl = thumbnailUrl
                    )
                } else {
                    OEmbedResult.Error("Video title not found in oEmbed response.")
                }
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                OEmbedResult.Error("Video not found or is private (HTTP 404).")
            } else if (responseCode == HttpURLConnection.HTTP_BAD_REQUEST) {
                OEmbedResult.Error("Invalid YouTube URL requested by oEmbed (HTTP 400).")
            } else {
                OEmbedResult.Error("Failed to fetch video details (HTTP $responseCode).")
            }
        } catch (e: Exception) {
            OEmbedResult.Error("Network error: ${e.localizedMessage ?: "Unable to connect to YouTube"}")
        } finally {
            connection?.disconnect()
        }
    }
}
