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
                (normTaskTitle.length >= 6 && (normPlayingTitle.contains(normTaskTitle) || compactPlaying.contains(compactTask))) ||
                (normPlayingTitle.length >= 16 && normTaskTitle.startsWith(normPlayingTitle))
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
            playingWords.contains(word)
        }

        val keywordMatches = !isJustAuthorName && taskWords.isNotEmpty() && (
                normPlayingTitle.contains(normTaskTitle) ||
                (normPlayingTitle.length >= 16 && normPlayingTitle.length >= (normTaskTitle.length * 0.70f) && normTaskTitle.contains(normPlayingTitle)) ||
                compactPlaying.contains(compactTask) ||
                (compactPlaying.length >= 16 && compactPlaying.length >= (compactTask.length * 0.70f) && compactTask.contains(compactPlaying)) ||
                (taskWords.size == 1 && matchingWords == 1 && normPlayingTitle.length <= normTaskTitle.length + 8) ||
                (taskWords.size == 2 && matchingWords == 2) ||
                (taskWords.size == 3 && matchingWords >= 2 && normPlayingTitle.length >= (normTaskTitle.length * 0.65f)) ||
                (taskWords.size >= 4 && (matchingWords.toFloat() / taskWords.size) >= 0.70f)
        )

        if (!titleMatches && !keywordMatches) {
            return MatchResult.MISMATCH
        }

        // If both playingArtist and taskAuthor are present and non-generic, verify they are not a different channel
        val isGenericAuthor = normTaskAuthor.isEmpty() ||
                normTaskAuthor == "youtube creator" ||
                normTaskAuthor == "youtube channel" ||
                normTaskAuthor == "youtube"
        val isGenericPlayingArtist = normPlayingArtist.isEmpty() ||
                normPlayingArtist == "youtube creator" ||
                normPlayingArtist == "youtube channel" ||
                normPlayingArtist == "youtube"

        if (!isGenericAuthor && !isGenericPlayingArtist && !titleMatches) {
            val compactArtist = normPlayingArtist.replace(" ", "")
            val compactAuthor = normTaskAuthor.replace(" ", "")
            val artistMatches = normPlayingArtist.contains(normTaskAuthor) ||
                    normTaskAuthor.contains(normPlayingArtist) ||
                    compactArtist.contains(compactAuthor) ||
                    compactAuthor.contains(compactArtist)
            if (!artistMatches) {
                return MatchResult.MISMATCH
            }
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

        // 0. Strip leading duration badges or playback/live prefixes (e.g. "10:25 " or "LIVE " or "Play video ")
        text = text.replace(
            Regex("^(?:(?:\\d{1,2}:\\d{2}(?::\\d{2})?|now\\s+playing|play\\s+video|shorts|live\\s+now|live|लाइव)\\s*[,\\-•·|]?\\s*)+", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        // 1. Cut everything from "Go to channel" / "चैनल पर जाएं" onwards
        val goToChannelIdx = Regex("\\b(?:go to channel|चैनल पर जाएं)\\b", RegexOption.IGNORE_CASE).find(text)?.range?.first
        if (goToChannelIdx != null && goToChannelIdx > 0) {
            text = text.substring(0, goToChannelIdx).trim()
        }

        // 2. Strip trailing duration before "Go to channel", e.g. " - 3 minutes, 45 seconds -" or " - 12:34 -" or " - LIVE -"
        text = text.replace(
            Regex("(?:[,\\-•·|]|\\s)+(?:live\\s+now|live|लाइव|(?:\\d+\\s*(?:hours?|hr|hrs|minutes?|mins?|min|seconds?|secs?|sec|घंटे|घंटा|मिनट|सेकंड)(?:[,\\s]+\\d+\\s*(?:minutes?|mins?|min|seconds?|secs?|sec|मिनट|सेकंड))*))(?:\\s*[,\\-•·|])?\\s*$", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        // 3. Strip trailing "<number> views / No views / watching / ago" metadata
        text = text.replace(
            Regex("(?:[,\\-•·|]|\\s)+(?:no\\s+views|कोई\\s+व्यू\\s+नहीं|\\d[0-9.,]*\\s*(?:k|m|b|lakh|lakhs|crore|crores|हज़ार|लाख|करोड़)?\\s*(?:views|view|watching|subscribers|बार देखा गया|लोग देख रहे हैं))\\b.*$", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        // 4. Strip trailing relative time ("2 hours ago", "Streamed 3 hours ago", "Started streaming...", "Just now", etc.) and "play video"
        text = text.replace(
            Regex("(?:[,\\-•·|]|\\s)+(?:streamed\\s+live\\s+|streamed\\s+|started\\s+streaming\\s+|premiered\\s+|scheduled\\s+for\\s+)?(?:\\d+\\s+(?:second|minute|hour|day|week|month|year)s?\\s+ago|\\d+\\s+(?:सेकंड|मिनट|घंटे|घंटा|दिन|हफ़्ते|महीने|साल)\\s+पहले|just\\s+now|live\\s+now|live|लाइव|play\\s+video|वीडियो\\s+चलाएं)\\b.*$", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        // 5. Strip trailing MM:SS duration badge
        text = text.replace(
            Regex("(?:[,\\-•·|]|\\s)+\\d{1,2}:\\d{2}(?::\\d{2})?\\s*$"),
            ""
        ).trim()

        // 6. If channelName is appended right at the end after a separator (" - ChannelName" or " • ChannelName"), strip only the trailing suffix
        val cleanChannel = channelName?.trim().orEmpty()
        if (cleanChannel.length >= 2 &&
            !cleanChannel.equals("YouTube Creator", ignoreCase = true) &&
            !cleanChannel.equals("YouTube Channel", ignoreCase = true)
        ) {
            val escapedChannel = Regex.escape(cleanChannel)
            val strippedSuffix = text.replace(
                Regex("(?:[,\\-•·|])\\s*$escapedChannel\\s*$", RegexOption.IGNORE_CASE),
                ""
            ).trim()
            if (strippedSuffix.length >= 3) {
                text = strippedSuffix
            }
        }

        // 7. Strip any @handle token
        val cleanHandle = channelHandle?.removePrefix("@")?.trim().orEmpty()
        if (cleanHandle.length >= 2) {
            val strippedHandle = text.replace(Regex("@?${Regex.escape(cleanHandle)}\\b", RegexOption.IGNORE_CASE), "").trim()
            if (strippedHandle.length >= 3) {
                text = strippedHandle
            }
        }

        return text.trim(' ', '-', '•', '·', '|', ',')
    }

    /**
     * Strictly verifies that a candidate video card title in YouTube Search / Channel Videos
     * matches the target video, while handling 2-line truncated YouTube card titles ("..."),
     * emojis, pipes, hashtags, and multilingual (Hindi/English/Hinglish) titles.
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
        val extractedTargetTitle = extractCardVideoTitleOnly(cleanTarget, targetChannel, targetHandle).ifBlank { cleanTarget }

        val normCard = normalize(extractedCardTitle)
        val normTarget = normalize(extractedTargetTitle).ifBlank { normalize(cleanTarget) }
        val normRawCandidate = normalize(rawCandidateText)
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

        // Reject if candidate is purely view count / duration / subscriber metadata
        if (Regex("^(?:\\d+\\s*(?:views|view|subscribers|subscriber|minutes|minute|seconds|second|hours|hour|days|day|ago)|no\\s+views)+$").matches(normCard)) {
            return false
        }

        val compactCard = normCard.replace(" ", "")
        val compactTarget = normTarget.replace(" ", "")

        // 1. Exact title match
        if (normCard == normTarget || compactCard == compactTarget) {
            return true
        }

        // 2. Full-target containment match (card or raw candidate contains complete target title)
        if (normTarget.length >= 6 && (
                normCard.contains(normTarget) ||
                compactCard.contains(compactTarget) ||
                normRawCandidate.contains(normTarget) ||
                normRawCandidate.replace(" ", "").contains(compactTarget)
        )) {
            return true
        }

        // 3. When YouTube truncates a long 2-line video title at the end with "...",
        // normCard will be a strict prefix of normTarget (at least 14 chars long)
        if (normCard.length >= 14 && normTarget.length >= 16 &&
            (normTarget.startsWith(normCard) || compactTarget.startsWith(compactCard))
        ) {
            return true
        }

        // 4. If target title contains the card title, only match if card title is substantial
        // (at least 16 chars and at least 70% of target title length) so random short words never match!
        if (normCard.length >= 16 && normCard.length >= (normTarget.length * 0.70f) &&
            (normTarget.contains(normCard) || compactTarget.contains(compactCard))
        ) {
            return true
        }

        // 5. Strict distinctive word matching on the video title
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

        val cardWords = normCard.split(" ").map { it.trim() }.filter { it.length >= 2 && !stopWords.contains(it) }
        val cardWordSet = cardWords.toSet()

        val matchedTargetCount = distinctiveWords.count { targetWord ->
            cardWordSet.contains(targetWord)
        }

        return when (distinctiveWords.size) {
            1 -> matchedTargetCount == 1 && (normCard == normTarget || (normCard.contains(distinctiveWords[0]) && normCard.length <= distinctiveWords[0].length + 6))
            2 -> matchedTargetCount == 2
            3 -> matchedTargetCount >= 2 && (matchedTargetCount == 3 || normCard.length >= (normTarget.length * 0.65f))
            else -> (matchedTargetCount.toFloat() / distinctiveWords.size.toFloat()) >= 0.72f
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
