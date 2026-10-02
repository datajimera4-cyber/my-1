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
            // Remove emojis, symbols, and punctuation, preserving letters, combining marks (Hindi matras), digits, and whitespace
            .replace(Regex("[^\\p{L}\\p{M}\\p{Nd}\\s]"), " ")
            // Collapse multiple whitespace characters into a single space
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Matches YouTube MediaMetadata / Watch Header title & artist against task title & channel.
     * Handles truncated 1-2 line watch page titles and multilingual (Hindi/English) titles.
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

        val normPlayingArtist = normalize(playingArtist)
        val normTaskAuthor = normalize(taskAuthor)

        val isJustAuthorName = normTaskAuthor.isNotEmpty() && (
                normPlayingTitle == normTaskAuthor ||
                normTaskAuthor.contains(normPlayingTitle) ||
                normPlayingTitle.replace(" ", "") == normTaskAuthor.replace(" ", "")
        )

        val compactPlaying = normPlayingTitle.replace(" ", "")
        val compactTask = normTaskTitle.replace(" ", "")

        val titleMatches = !isJustAuthorName && (
                normPlayingTitle == normTaskTitle ||
                compactPlaying == compactTask ||
                (normTaskTitle.length >= 4 && normPlayingTitle.contains(normTaskTitle)) ||
                (normPlayingTitle.length >= 6 && normTaskTitle.contains(normPlayingTitle)) ||
                (normPlayingTitle.length >= 8 && normTaskTitle.length >= 8 && (
                        normPlayingTitle.contains(normTaskTitle.take(8)) ||
                        normTaskTitle.contains(normPlayingTitle.take(8))
                )) ||
                (compactPlaying.length >= 8 && compactTask.length >= 8 && (
                        compactPlaying.contains(compactTask.take(8)) ||
                        compactTask.contains(compactPlaying.take(8))
                ))
        )

        val stopWords = setOf(
            "the", "and", "official", "video", "music", "audio", "with",
            "from", "feat", "song", "lyrics", "full", "remaster", "remastered",
            "hd", "4k", "hq", "live", "new", "youtube", "task"
        )
        val authorWords = if (normTaskAuthor.isNotEmpty()) {
            normTaskAuthor.split(" ").map { it.trim() }.filter { it.length >= 2 }.toSet()
        } else {
            emptySet()
        }

        val allTaskWords = normTaskTitle.split(" ").map { it.trim() }.filter { it.length >= 3 && !stopWords.contains(it) }
        val distinctiveTaskWords = allTaskWords.filter { !authorWords.contains(it) }
        val taskWords = distinctiveTaskWords.ifEmpty { allTaskWords }

        val playingWords = normPlayingTitle.split(" ").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val matchingWords = taskWords.count { word ->
            playingWords.contains(word) || (word.length >= 4 && normPlayingTitle.contains(word))
        }

        val keywordMatches = taskWords.isNotEmpty() && (
                (taskWords.size >= 2 && matchingWords >= 2) ||
                (taskWords.size >= 2 && matchingWords >= 1 && (matchingWords.toFloat() / taskWords.size) >= 0.4f) ||
                (taskWords.size == 1 && matchingWords == 1)
        )

        if (!titleMatches && !keywordMatches) {
            return MatchResult.MISMATCH
        }

        return MatchResult.MATCH
    }

    /**
     * Extracts YouTube 11-character video ID from common YouTube URL formats.
     */
    fun extractVideoId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        val patterns = listOf(
            Regex("(?:v=|vi=)([a-zA-Z0-9_-]{11})"),
            Regex("(?:https?:\\/\\/)?(?:www\\.|m\\.|music\\.)?youtube\\.com\\/watch\\?v=([a-zA-Z0-9_-]{11})"),
            Regex("(?:https?:\\/\\/)?(?:www\\.|m\\.)?youtu\\.be\\/([a-zA-Z0-9_-]{11})"),
            Regex("(?:https?:\\/\\/)?(?:www\\.|m\\.)?youtube\\.com\\/embed\\/([a-zA-Z0-9_-]{11})"),
            Regex("(?:https?:\\/\\/)?(?:www\\.|m\\.)?youtube\\.com\\/shorts\\/([a-zA-Z0-9_-]{11})"),
            Regex("(?:https?:\\/\\/)?(?:www\\.|m\\.)?youtube\\.com\\/live\\/([a-zA-Z0-9_-]{11})"),
            Regex("(?:https?:\\/\\/)?(?:www\\.|m\\.)?youtube\\.com\\/v\\/([a-zA-Z0-9_-]{11})"),
            Regex("^[a-zA-Z0-9_-]{11}$")
        )

        for (pattern in patterns) {
            val match = pattern.find(trimmed)
            if (match != null) {
                return match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: match.value
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
        val urlRegex = Regex("""(?:https?://)?(?:www\.|m\.|music\.)?(?:youtube\.com|youtu\.be)[^\s]+""", RegexOption.IGNORE_CASE)
        val match = urlRegex.find(trimmed)
        val candidate = match?.value ?: trimmed
        val videoId = extractVideoId(candidate)
        return if (!videoId.isNullOrBlank()) {
            "https://www.youtube.com/watch?v=$videoId"
        } else if (!candidate.startsWith("http://", ignoreCase = true) &&
            !candidate.startsWith("https://", ignoreCase = true) &&
            (candidate.contains("youtube.com", ignoreCase = true) || candidate.contains("youtu.be", ignoreCase = true))
        ) {
            "https://$candidate"
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

    /**
     * Extracts the unique @handle (e.g. "@newchannel") from an oEmbed author_url
     * such as "https://www.youtube.com/@newchannel".
     */
    fun extractChannelHandle(authorUrl: String?): String? {
        if (authorUrl.isNullOrBlank()) return null
        val match = Regex("(@[a-zA-Z0-9_\\-.]+)").find(authorUrl.trim())
        return match?.groupValues?.getOrNull(1)?.takeIf { it.length >= 2 }
    }
}
