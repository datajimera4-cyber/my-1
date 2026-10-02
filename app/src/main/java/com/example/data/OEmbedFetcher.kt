package com.example.data

import com.example.util.TitleMatcher
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
     * using the public no-key oEmbed endpoint on Dispatchers.IO, with automatic URL normalization
     * and fallback for shorts/live/restricted links.
     */
    suspend fun fetchOEmbed(rawVideoUrl: String): OEmbedResult = withContext(Dispatchers.IO) {
        val trimmedUrl = rawVideoUrl.trim()

        if (trimmedUrl.isEmpty() || trimmedUrl == "PASTE_MY_YOUTUBE_LINK_HERE") {
            return@withContext OEmbedResult.Error(
                "Task URL is currently 'PASTE_MY_YOUTUBE_LINK_HERE'. Please paste a valid YouTube video link in SampleTask.kt or tap 'Use Demo Video'."
            )
        }

        val normalizedUrl = TitleMatcher.extractCleanYouTubeUrl(trimmedUrl)
        val videoId = TitleMatcher.extractVideoId(normalizedUrl)
        val canonicalUrl = if (!videoId.isNullOrBlank()) {
            "https://www.youtube.com/watch?v=$videoId"
        } else if (normalizedUrl.startsWith("http://") || normalizedUrl.startsWith("https://")) {
            normalizedUrl
        } else {
            "https://$normalizedUrl"
        }

        val fallbackThumb = if (!videoId.isNullOrBlank()) {
            "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
        } else ""

        // 1. Try YouTube official oEmbed
        val primaryResult = tryOEmbedEndpoint(
            "https://www.youtube.com/oembed?url=${URLEncoder.encode(canonicalUrl, "UTF-8")}&format=json",
            fallbackThumb
        )
        if (primaryResult is OEmbedResult.Success) {
            return@withContext primaryResult
        }

        // 2. Fallback to noembed.com (handles some links where YouTube oEmbed returns 401/403)
        val noembedResult = tryOEmbedEndpoint(
            "https://noembed.com/embed?url=${URLEncoder.encode(canonicalUrl, "UTF-8")}",
            fallbackThumb
        )
        if (noembedResult is OEmbedResult.Success) {
            return@withContext noembedResult
        }

        // 3. Fallback to parsing YouTube watch page HTML <title> if videoId is valid
        if (!videoId.isNullOrBlank()) {
            val htmlResult = tryFetchWatchPageMetadata(canonicalUrl, fallbackThumb)
            if (htmlResult is OEmbedResult.Success) {
                return@withContext htmlResult
            }
        }

        return@withContext primaryResult
    }

    private fun tryOEmbedEndpoint(endpointUrl: String, fallbackThumb: String): OEmbedResult {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(endpointUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3_200
                readTimeout = 3_200
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val jsonText = reader.use { it.readText() }
                val jsonObject = JSONObject(jsonText)

                val title = jsonObject.optString("title", "").trim()
                val authorName = jsonObject.optString("author_name", "").trim()
                val authorUrl = jsonObject.optString("author_url", "")
                val thumbnailUrl = jsonObject.optString("thumbnail_url", "").ifEmpty { fallbackThumb }

                if (title.isNotEmpty()) {
                    OEmbedResult.Success(
                        title = title,
                        authorName = authorName.ifEmpty { "YouTube Creator" },
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

    /**
     * Fetches the exact video duration in seconds ("lengthSeconds") for a specific 11-char YouTube videoId
     * so that multiple videos with the same title on the same channel can be strictly distinguished.
     */
    suspend fun fetchVideoExactDurationSeconds(videoId: String?): Int = withContext(Dispatchers.IO) {
        val cleanId = videoId?.trim().orEmpty()
        if (cleanId.length != 11) return@withContext 0
        var connection: HttpURLConnection? = null
        return@withContext try {
            val watchUrl = URL("https://www.youtube.com/watch?v=$cleanId")
            connection = (watchUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2_500
                readTimeout = 2_500
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
                )
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val sb = StringBuilder()
                val buf = CharArray(8192)
                var totalRead = 0
                val lengthRegex = Regex("\"lengthSeconds\"\\s*:\\s*\"(\\d+)\"")
                val approxMsRegex = Regex("\"approxDurationMs\"\\s*:\\s*\"(\\d+)\"")
                while (totalRead < 350_000) {
                    val n = reader.read(buf)
                    if (n <= 0) break
                    sb.append(buf, 0, n)
                    totalRead += n
                    val m = lengthRegex.find(sb)
                    if (m != null) {
                        val secs = m.groupValues[1].toIntOrNull() ?: 0
                        if (secs > 0) return@withContext secs
                    }
                }
                val approxMatch = approxMsRegex.find(sb)
                val approxMs = approxMatch?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                if (approxMs > 0L) return@withContext ((approxMs + 500L) / 1000L).toInt()
            }
            0
        } catch (_: Exception) {
            0
        } finally {
            connection?.disconnect()
        }
    }

    private fun tryFetchWatchPageMetadata(watchUrl: String, fallbackThumb: String): OEmbedResult {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(watchUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val html = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                val titleMatch = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE).find(html)
                val rawTitle = titleMatch?.groupValues?.getOrNull(1)
                    ?.replace("- YouTube", "")
                    ?.replace("&#39;", "'")
                    ?.replace("&quot;", "\"")
                    ?.replace("&amp;", "&")
                    ?.trim() ?: ""
                val authorMatch = Regex("\"ownerChannelName\":\"(.*?)\"").find(html)
                val authorName = authorMatch?.groupValues?.getOrNull(1)?.trim() ?: "YouTube Creator"
                val handleMatch = Regex("\"(?:canonicalBaseUrl|vanityChannelUrl|ownerProfileUrl)\":\"(?:https?://(?:www\\.)?youtube\\.com)?(/+@[^\"/]+)\"").find(html)
                val extractedAuthorUrl = handleMatch?.groupValues?.getOrNull(1)?.let { "https://www.youtube.com$it" } ?: ""

                if (rawTitle.isNotBlank() && !rawTitle.equals("YouTube", ignoreCase = true)) {
                    return OEmbedResult.Success(
                        title = rawTitle,
                        authorName = authorName,
                        authorUrl = extractedAuthorUrl,
                        thumbnailUrl = fallbackThumb
                    )
                }
            }
            OEmbedResult.Error("Could not parse watch page metadata.")
        } catch (e: Exception) {
            OEmbedResult.Error("Metadata fallback failed: ${e.localizedMessage}")
        } finally {
            connection?.disconnect()
        }
    }
}
