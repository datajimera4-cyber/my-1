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
            Regex("(?:https?:\\/\\/)?(?:www\\.|m\\.)?youtube\\.com\\/watch\\?v=([a-zA-Z0-9_-]{11})"),
            Regex("(?:https?:\\/\\/)?(?:www\\.|m\\.)?youtu\\.be\\/([a-zA-Z0-9_-]{11})"),
            Regex("(?:https?:\\/\\/)?(?:www\\.|m\\.)?youtube\\.com\\/embed\\/([a-zA-Z0-9_-]{11})"),
            Regex("(?:https?:\\/\\/)?(?:www\\.|m\\.)?youtube\\.com\\/shorts\\/([a-zA-Z0-9_-]{11})"),
            Regex("(?:https?:\\/\\/)?(?:www\\.|m\\.)?youtube\\.com\\/live\\/([a-zA-Z0-9_-]{11})"),
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
     * Cleans raw pasted text (e.g. from YouTube share button containing title + link)
     * and extracts ONLY the clean canonical YouTube video URL.
     */
    fun extractCleanYouTubeUrl(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val trimmed = text.trim()
        val urlRegex = Regex("""https?://(?:www\.|m\.)?(?:youtube\.com|youtu\.be)[^\s]+""")
        val match = urlRegex.find(trimmed)
        val candidate = match?.value ?: trimmed
        val videoId = extractVideoId(candidate)
        return if (!videoId.isNullOrBlank()) {
            "https://www.youtube.com/watch?v=$videoId"
        } else {
            candidate
        }
    }

    /**
     * If user pasted shared text that contained a title before the link,
     * extract the title prefix so it can be auto-filled.
     */
    fun extractSharedTitle(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val trimmed = text.trim()
        val urlRegex = Regex("""https?://[^\s]+""")
        val match = urlRegex.find(trimmed) ?: return null
        val beforeUrl = trimmed.substring(0, match.range.first).trim()
        val cleaned = beforeUrl
            .removePrefix("Watch \"")
            .removeSuffix("\" on YouTube:")
            .removeSuffix("on YouTube:")
            .removeSuffix(":")
            .trim()
        return if (cleaned.length >= 3) cleaned else null
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
