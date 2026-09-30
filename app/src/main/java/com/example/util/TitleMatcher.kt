package com.example.util

import com.example.data.MatchResult

object TitleMatcher {

    /**
     * Normalization rule:
     * lowercase, trim, remove punctuation/emoji, collapse spaces.
     */
    fun normalize(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text.lowercase()
            // Remove emojis, symbols, and punctuation, preserving letters, digits, and whitespace
            .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
            // Collapse multiple whitespace characters into a single space
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Matches YouTube MediaMetadata title & artist against oEmbed task title & channel.
     * MATCH if normalized playing title equals or contains task title (or vice versa).
     * If artist is present, it should loosely match author_name; if missing, title match is enough.
     */
    fun evaluateMatch(
        playingTitle: String?,
        taskTitle: String?,
        playingArtist: String?,
        taskAuthor: String?
    ): MatchResult {
        val normPlayingTitle = normalize(playingTitle)
        val normTaskTitle = normalize(taskTitle)

        if (normPlayingTitle.isEmpty() || normTaskTitle.isEmpty()) {
            return MatchResult.UNKNOWN
        }

        val titleMatches = normPlayingTitle == normTaskTitle ||
                normPlayingTitle.contains(normTaskTitle) ||
                normTaskTitle.contains(normPlayingTitle)

        if (!titleMatches) {
            return MatchResult.MISMATCH
        }

        // If artist is available and task author is available, verify loose match
        val normPlayingArtist = normalize(playingArtist)
        val normTaskAuthor = normalize(taskAuthor)

        if (normPlayingArtist.isNotEmpty() && normTaskAuthor.isNotEmpty()) {
            val compactArtist = normPlayingArtist.replace(" ", "")
            val compactAuthor = normTaskAuthor.replace(" ", "")

            val artistMatches = normPlayingArtist == normTaskAuthor ||
                    normPlayingArtist.contains(normTaskAuthor) ||
                    normTaskAuthor.contains(normPlayingArtist) ||
                    compactArtist.contains(compactAuthor) ||
                    compactAuthor.contains(compactArtist)
            if (!artistMatches) {
                // Channel clearly differs (e.g. reupload or completely different track)
                return MatchResult.MISMATCH
            }
        }

        return MatchResult.MATCH
    }

    /**
     * Extracts YouTube 11-character video ID from common YouTube URL formats.
     */
    fun extractVideoId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val patterns = listOf(
            Regex("(?:https?:\\/\\/)?(?:www\\.)?youtube\\.com\\/watch\\?v=([a-zA-Z0-9_-]{11})"),
            Regex("(?:https?:\\/\\/)?(?:www\\.)?youtu\\.be\\/([a-zA-Z0-9_-]{11})"),
            Regex("(?:https?:\\/\\/)?(?:www\\.)?youtube\\.com\\/embed\\/([a-zA-Z0-9_-]{11})"),
            Regex("(?:https?:\\/\\/)?(?:www\\.)?youtube\\.com\\/shorts\\/([a-zA-Z0-9_-]{11})"),
            Regex("^[a-zA-Z0-9_-]{11}$")
        )

        for (pattern in patterns) {
            val match = pattern.find(url.trim())
            if (match != null) {
                return match.groupValues.getOrNull(1) ?: match.value
            }
        }
        return null
    }

    /**
     * Returns high quality thumbnail URL from video ID.
     */
    fun getThumbnailUrl(videoUrl: String?): String? {
        val videoId = extractVideoId(videoUrl) ?: return null
        return "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
    }

    /**
     * Cleans YouTube URL to ensure it plays the full video from the beginning (00:00),
     * completely removing any chapter, timestamp (e.g. ?t=19s, &t=...), start parameters, or clip tags.
     */
    fun cleanYouTubeUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        val videoId = extractVideoId(url)
        return if (!videoId.isNullOrBlank()) {
            "https://www.youtube.com/watch?v=$videoId"
        } else {
            url.trim()
        }
    }
}
