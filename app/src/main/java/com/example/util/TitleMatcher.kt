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
                (taskWords.size >= 2 && matchingWords >= 2 && (matchingWords.toFloat() / taskWords.size) >= 0.6f) ||
                (taskWords.size == 1 && matchingWords == 1 && normPlayingTitle.contains(normTaskTitle))
        )

        if (!titleMatches && !keywordMatches) {
            return MatchResult.MISMATCH
        }

        return MatchResult.MATCH
    }

    /**
     * Extracts ONLY the video title portion from a YouTube Accessibility video card label.
     * YouTube formats video card contentDescriptions as:
     * "<Video Title> - <Duration> - Go to channel - <Channel Name> - <Views> - <Time ago> - play video"
     * or "<Video Title> • <Channel Name> • <Views> • <Time ago>"
     */
    fun extractCardVideoTitleOnly(rawCardText: String?, channelName: String?, channelHandle: String? = null): String {
        if (rawCardText.isNullOrBlank()) return ""
        var text = rawCardText.replace("\n", " ").replace(Regex("\\s+"), " ").trim()

        // 1. Cut everything from "Go to channel" / "चैनल पर जाएं" onwards
        val goToChannelIdx = Regex("\\b(?:go to channel|चैनल पर जाएं)\\b", RegexOption.IGNORE_CASE).find(text)?.range?.first
        if (goToChannelIdx != null && goToChannelIdx > 0) {
            text = text.substring(0, goToChannelIdx).trim()
        }

        // 2. Strip trailing duration before "Go to channel", e.g. " - 3 minutes, 45 seconds -" or " - 12:34 -"
        text = text.replace(
            Regex("(?:[,\\-•·|]|\\s)+(?:\\d+\\s*(?:hours?|hr|hrs|minutes?|mins?|min|seconds?|secs?|sec|घंटे|मिनट|सेकंड)(?:[,\\s]+\\d+\\s*(?:minutes?|mins?|min|seconds?|secs?|sec|मिनट|सेकंड))*)(?:\\s*[,\\-•·|])?\\s*$", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        // 3. Strip trailing "<number> views / No views / watching / ago" metadata
        text = text.replace(
            Regex("(?:[,\\-•·|]|\\s)+(?:no\\s+views|कोई\\s+व्यू\\s+नहीं|\\d[0-9.,]*\\s*(?:k|m|b|lakh|lakhs|crore|crores|हज़ार|लाख|करोड़)?\\s*(?:views|view|watching|subscribers|बार देखा गया|लोग देख रहे हैं))\\b.*$", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        // 4. Strip trailing relative time ("2 hours ago", "Just now", etc.) and "play video"
        text = text.replace(
            Regex("(?:[,\\-•·|]|\\s)+(?:streamed\\s+|premiered\\s+)?(?:\\d+\\s+(?:second|minute|hour|day|week|month|year)s?\\s+ago|just\\s+now|play\\s+video|वीडियो\\s+चलाएं)\\b.*$", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        // 5. Strip trailing MM:SS duration badge
        text = text.replace(
            Regex("(?:[,\\-•·|]|\\s)+\\d{1,2}:\\d{2}(?::\\d{2})?\\s*$"),
            ""
        ).trim()

        // 6. If channelName is appended after a separator (" - ChannelName" or " • ChannelName"), strip it
        val cleanChannel = channelName?.trim().orEmpty()
        if (cleanChannel.length >= 2 &&
            !cleanChannel.equals("YouTube Creator", ignoreCase = true) &&
            !cleanChannel.equals("YouTube Channel", ignoreCase = true)
        ) {
            val escapedChannel = Regex.escape(cleanChannel)
            text = text.replace(
                Regex("(?:[,\\-•·|])\\s*$escapedChannel\\b.*$", RegexOption.IGNORE_CASE),
                ""
            ).trim()
        }

        // 7. Strip any @handle token
        val cleanHandle = channelHandle?.removePrefix("@")?.trim().orEmpty()
        if (cleanHandle.length >= 2) {
            text = text.replace(Regex("@?${Regex.escape(cleanHandle)}\\b", RegexOption.IGNORE_CASE), "").trim()
        }

        return text.trim(' ', '-', '•', '·', '|', ',')
    }

    /**
     * Strictly verifies that a candidate video card title in YouTube Search / Channel Videos
     * is the EXACT target video, preventing clicking other videos from the same channel.
     */
    fun isStrictTargetVideoMatch(
        rawCandidateText: String?,
        targetTitle: String?,
        targetChannel: String?,
        targetHandle: String? = null
    ): Boolean {
        val cleanTarget = targetTitle?.trim().orEmpty()
        if (cleanTarget.isEmpty()) return false

        val extractedCardTitle = extractCardVideoTitleOnly(rawCandidateText, targetChannel, targetHandle)
        val normCard = normalize(extractedCardTitle)
        val normTarget = normalize(cleanTarget)
        if (normCard.isEmpty() || normTarget.isEmpty()) return false

        val normChannel = normalize(targetChannel)
        val normHandle = normalize(targetHandle?.removePrefix("@"))

        // Reject if the candidate node text is ONLY the channel name or @handle
        if (normChannel.isNotEmpty() && (normCard == normChannel || normCard.replace(" ", "") == normChannel.replace(" ", ""))) {
            return false
        }
        if (normHandle.isNotEmpty() && (normCard == normHandle || normCard.replace(" ", "") == normHandle.replace(" ", ""))) {
            return false
        }

        val compactCard = normCard.replace(" ", "")
        val compactTarget = normTarget.replace(" ", "")

        // 1. Exact or full-title containment match
        if (normCard == normTarget || compactCard == compactTarget) {
            return true
        }
        if (normTarget.length >= 5 && (normCard.contains(normTarget) || compactCard.contains(compactTarget))) {
            return true
        }

        // 2. Truncated long title prefix match (when YouTube truncates a long 2-line title with "...")
        if (normTarget.length >= 16 && normCard.length >= 14) {
            if (normCard.startsWith(normTarget.take(16)) || normTarget.startsWith(normCard.take(16))) {
                return true
            }
            if (compactCard.length >= 14 && compactTarget.length >= 14 &&
                (compactCard.startsWith(compactTarget.take(14)) || compactTarget.startsWith(compactCard.take(14)))
            ) {
                return true
            }
        }

        // 3. Strict distinctive word matching on the extracted video title ONLY (excluding channel words)
        val stopWords = setOf(
            "the", "and", "official", "video", "music", "audio", "with",
            "from", "feat", "song", "lyrics", "full", "remaster", "remastered",
            "hd", "4k", "hq", "youtube", "task"
        )
        val channelWords = if (normChannel.isNotEmpty()) {
            normChannel.split(" ").map { it.trim() }.filter { it.length >= 2 }.toSet()
        } else {
            emptySet()
        }

        val allTargetWords = normTarget.split(" ").map { it.trim() }.filter { it.length >= 2 && !stopWords.contains(it) }
        val nonChannelTargetWords = allTargetWords.filter { !channelWords.contains(it) && it != normHandle }
        val distinctiveWords = nonChannelTargetWords.ifEmpty { allTargetWords }

        if (distinctiveWords.isEmpty()) return false

        val cardWords = normCard.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
        val cardWordSet = cardWords.toSet()

        val matchedCount = distinctiveWords.count { targetWord ->
            cardWordSet.contains(targetWord) ||
                    (targetWord.length >= 5 && cardWords.any { cw -> cw.startsWith(targetWord) || (cw.length >= 5 && targetWord.startsWith(cw)) })
        }

        return when (distinctiveWords.size) {
            1 -> matchedCount == 1 && (normCard == normTarget || normCard.startsWith(distinctiveWords[0]))
            2 -> matchedCount == 2 // BOTH words must match (100%)
            3 -> matchedCount == 3 || (matchedCount == 2 && normCard.startsWith(normTarget.take(6)))
            else -> matchedCount >= 3 && (matchedCount.toFloat() / distinctiveWords.size.toFloat()) >= 0.75f
        }
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
