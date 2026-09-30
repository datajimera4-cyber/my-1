package com.example.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.LogType
import com.example.repository.WatchSessionRepository
import com.example.util.TitleMatcher

class YouTubeLiveSearchService : AccessibilityService() {

    enum class LiveSearchPhase {
        IDLE,
        OPEN_SEARCH_BAR,
        TYPE_QUERY,
        SUBMIT_QUERY,
        FIND_AND_CLICK_VIDEO,
        COMPLETED
    }

    companion object {
        @Volatile
        var instance: YouTubeLiveSearchService? = null
            private set

        @Volatile
        var isServiceConnected: Boolean = false
            private set

        @Volatile
        var isYouTubeInForeground: Boolean = false

        @Volatile
        var isVideoExplicitlyPaused: Boolean = false

        @Volatile
        var lastExplicitPauseTime: Long = 0L

        @Volatile
        var targetSearchTitle: String? = null

        @Volatile
        var targetSearchChannel: String? = null

        @Volatile
        var targetVideoUrl: String? = null

        @Volatile
        var targetVideoId: String? = null

        @Volatile
        var hasClickedTarget: Boolean = false

        @Volatile
        var lastClickTime: Long = 0L

        @Volatile
        var currentPhase: LiveSearchPhase = LiveSearchPhase.IDLE

        @Volatile
        private var wrongVideoStrikeCount = 0

        @Volatile
        private var notInYouTubeStrikeCount = 0

        @Volatile
        private var lastWatchHeaderCheckTime = 0L

        private var scrollAttempts = 0

        fun resetMonitoringCounters() {
            wrongVideoStrikeCount = 0
            notInYouTubeStrikeCount = 0
            lastWatchHeaderCheckTime = 0L
            lastExplicitPauseTime = 0L
        }

        fun armSearchTrigger(title: String, channel: String?, videoUrl: String? = null, videoId: String? = null) {
            targetSearchTitle = title
            targetSearchChannel = channel
            targetVideoUrl = videoUrl
            targetVideoId = videoId
            hasClickedTarget = false
            scrollAttempts = 0
            lastClickTime = 0L
            isVideoExplicitlyPaused = false
            resetMonitoringCounters()
            currentPhase = LiveSearchPhase.OPEN_SEARCH_BAR
            WatchSessionRepository.addLog("Live Human Search armed for: \"$title\"", LogType.INFO)
        }

        fun disarm() {
            targetSearchTitle = null
            targetSearchChannel = null
            targetVideoUrl = null
            targetVideoId = null
            hasClickedTarget = false
            scrollAttempts = 0
            lastClickTime = 0L
            isVideoExplicitlyPaused = false
            resetMonitoringCounters()
            currentPhase = LiveSearchPhase.IDLE
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isServiceConnected = true
        WatchSessionRepository.addLog("YouTube Human Live Search Accessibility Service Connected", LogType.INFO)
    }

    private fun isTransientSystemPackage(pkg: String): Boolean {
        if (pkg.isBlank()) return true
        val lower = pkg.lowercase()
        return lower == "android" ||
                lower.contains("systemui") ||
                lower.contains("inputmethod") ||
                lower.contains("keyboard") ||
                lower.contains("gboard") ||
                lower.contains("honeyboard") ||
                lower.contains("swiftkey") ||
                lower.contains("accessibility") ||
                lower.contains("overlay") ||
                lower.contains("permission") ||
                lower.contains("packageinstaller")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: ""
        val myPkg = packageName ?: "com.example"
        val activeRootPkg = try { rootInActiveWindow?.packageName?.toString() } catch (_: Exception) { null }
        val elapsedSinceLaunch = System.currentTimeMillis() - WatchSessionRepository.taskLaunchTimestampMillis
        val isSessionActive = WatchSessionRepository.sessionState.value == com.example.data.SessionState.ACTIVE

        if (pkg == "com.google.android.youtube" || activeRootPkg == "com.google.android.youtube") {
            // Check that YouTube is not minimized into Picture-in-Picture (PiP) mode
            val inPip = try { rootInActiveWindow?.window?.isInPictureInPictureMode == true } catch (_: Exception) { false }
            if (inPip && isSessionActive && elapsedSinceLaunch > 4500L) {
                isYouTubeInForeground = false
                WatchSessionRepository.triggerTaskIncomplete(
                    "Task Incomplete! Aapne YouTube minimize kar diya hai. Task complete hone tak YouTube par target video full screen mein dekhna zaroori hai."
                )
                return
            }
            notInYouTubeStrikeCount = 0
            isYouTubeInForeground = true
            if (isSessionActive && !isVideoExplicitlyPaused) {
                WatchSessionRepository.setPlaybackPlaying(true)
            }
            if (isSessionActive && elapsedSinceLaunch > 8500L &&
                currentPhase != LiveSearchPhase.IDLE && currentPhase != LiveSearchPhase.COMPLETED
            ) {
                currentPhase = LiveSearchPhase.COMPLETED
            }
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (pkg.isNotBlank() &&
                pkg != myPkg &&
                elapsedSinceLaunch > 4000L &&
                !isTransientSystemPackage(pkg)
            ) {
                // Verify activeRootPkg is also not YouTube before declaring exit
                if (activeRootPkg != "com.google.android.youtube") {
                    isYouTubeInForeground = false
                    WatchSessionRepository.setPlaybackPlaying(false)
                    WatchSessionRepository.onRequestHideOverlay?.invoke()
                    if (isSessionActive && WatchSessionRepository.hasLeftAppForYouTube) {
                        WatchSessionRepository.triggerTaskIncomplete(
                            "Task Incomplete! Aapne YouTube minimize kar diya ya YouTube se back kar ke doosre app mein switch kar liya."
                        )
                        return
                    }
                }
            }
        }

        // Detect user interactions on YouTube like, comment, pause/play, or clicking another video
        if (isYouTubeInForeground && event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            try {
                val screenHeight = resources.displayMetrics.heightPixels.coerceAtLeast(800)
                val node = event.source
                val clickRect = android.graphics.Rect()
                node?.getBoundsInScreen(clickRect)
                val evText = event.text?.joinToString(" ") { it.toString() }?.trim() ?: ""
                val evDesc = event.contentDescription?.toString()?.trim() ?: ""
                val desc = node?.contentDescription?.toString()?.ifBlank { evDesc } ?: evDesc
                val text = node?.text?.toString()?.ifBlank { evText } ?: evText
                val viewId = node?.viewIdResourceName ?: ""
                val combined = "$desc $text $evText $evDesc $viewId".lowercase()

                val isTopPlayerControl = (clickRect.bottom in 1..(screenHeight * 0.38f).toInt()) ||
                        viewId.contains("player_control", ignoreCase = true) ||
                        viewId.contains("play_pause_replay_button", ignoreCase = true)

                val looksLikeVideoCard = combined.contains("views") ||
                        combined.contains("go to channel") ||
                        combined.contains("minutes") ||
                        combined.contains("seconds") ||
                        combined.contains("watching") ||
                        viewId.contains("video_lockup", ignoreCase = true) ||
                        viewId.contains("compact_video", ignoreCase = true) ||
                        viewId.contains("video_card", ignoreCase = true) ||
                        viewId.contains("rich_item", ignoreCase = true)

                val isDislike = combined.contains("dislike") || combined.contains("नापसंद")
                val isLike = !isDislike && !looksLikeVideoCard && combined.length < 95 && (
                        combined.contains("like") ||
                        combined.contains("पसंद") ||
                        combined.contains("thumbs up")
                )

                val isComment = !looksLikeVideoCard && combined.length < 95 && (
                        combined.contains("comment") ||
                        combined.contains("add a comment") ||
                        combined.contains("send comment") ||
                        combined.contains("टिप्पणी")
                )

                if (isLike) {
                    WatchSessionRepository.onTaskLikeDetected?.invoke()
                } else if (isComment) {
                    WatchSessionRepository.onTaskCommentDetected?.invoke()
                } else if (isTopPlayerControl && (desc.equals("Pause video", ignoreCase = true) || desc.contains("वीडियो रोकें"))) {
                    isVideoExplicitlyPaused = true
                    lastExplicitPauseTime = System.currentTimeMillis()
                    WatchSessionRepository.setPlaybackPlaying(false)
                } else if (isTopPlayerControl && (
                    desc.equals("Play video", ignoreCase = true) ||
                    desc.equals("Replay video", ignoreCase = true) ||
                    desc.contains("वीडियो चलाएं")
                )) {
                    isVideoExplicitlyPaused = false
                    WatchSessionRepository.setPlaybackPlaying(true)
                } else if (isSessionActive && isReadyForWatchVerification()) {
                    checkIfUserClickedDifferentVideo(node, desc, text, viewId, "$evText $evDesc".trim())
                }

                if (isSessionActive && isReadyForWatchVerification()) {
                    val handler = android.os.Handler(android.os.Looper.getMainLooper())
                    handler.postDelayed({ inspectCurrentYouTubeState() }, 750L)
                    handler.postDelayed({ inspectCurrentYouTubeState() }, 1600L)
                }
                node?.recycle()
            } catch (_: Exception) {}
        }

        // If target was already clicked or idle, monitor playback controls, like status & active video in YouTube
        if (hasClickedTarget || currentPhase == LiveSearchPhase.IDLE || currentPhase == LiveSearchPhase.COMPLETED) {
            if (isYouTubeInForeground && (
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
            )) {
                val rootWin = rootInActiveWindow ?: event.source
                checkPlaybackControls(rootWin)
                checkVideoLikeState(event.source ?: rootWin)
                if (isSessionActive && isReadyForWatchVerification()) {
                    val now = System.currentTimeMillis()
                    if (now - lastWatchHeaderCheckTime >= 700L) {
                        lastWatchHeaderCheckTime = now
                        verifyActiveYouTubeVideo(rootWin)
                    }
                }
            }
            return
        }

        val titleToFind = targetSearchTitle ?: return
        val rootNode = rootInActiveWindow ?: event.source ?: return

        try {
            // Priority 1: Check if target video card is already visible on screen!
            if (findAndClickVideoNode(rootNode, titleToFind, targetSearchChannel)) {
                return
            }

            when (currentPhase) {
                LiveSearchPhase.OPEN_SEARCH_BAR -> {
                    // Check if search edit text is already visible on screen
                    val existingEditText = findSearchEditText(rootNode)
                    if (existingEditText != null) {
                        currentPhase = LiveSearchPhase.TYPE_QUERY
                        existingEditText.recycle()
                        handleTyping(rootNode, titleToFind)
                    } else {
                        // Look for the YouTube search button/icon in top toolbar
                        val searchBtn = findSearchButton(rootNode)
                        if (searchBtn != null) {
                            val clicked = searchBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
                                    (searchBtn.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
                            searchBtn.recycle()
                            if (clicked) {
                                currentPhase = LiveSearchPhase.TYPE_QUERY
                                WatchSessionRepository.addLog(
                                    "Human search: Tapped YouTube search button",
                                    LogType.INFO
                                )
                            }
                        }
                    }
                }

                LiveSearchPhase.TYPE_QUERY -> {
                    handleTyping(rootNode, titleToFind)
                }

                LiveSearchPhase.SUBMIT_QUERY -> {
                    handleSubmitQuery(rootNode, titleToFind)
                }

                LiveSearchPhase.FIND_AND_CLICK_VIDEO -> {
                    val found = findAndClickVideoNode(rootNode, titleToFind, targetSearchChannel)
                    if (!found && scrollAttempts < 4) {
                        scrollAttempts++
                        scrollForward(rootNode)
                    }
                }

                else -> {}
            }
        } catch (_: Exception) {
            // Traversal resilience
        } finally {
            rootNode.recycle()
        }
    }

    private fun handleTyping(rootNode: AccessibilityNodeInfo, titleToFind: String) {
        val searchEditText = findSearchEditText(rootNode) ?: return
        try {
            // Step A: Focus the edit text
            searchEditText.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

            // Step B: Set text with target task title
            val typeArgs = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, titleToFind)
            }
            val typed = searchEditText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, typeArgs)

            if (typed) {
                WatchSessionRepository.addLog(
                    "Human search: Typed task title into search bar: \"$titleToFind\"",
                    LogType.INFO
                )

                // Try submitting immediately via ACTION_IME_ENTER
                val submitted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    searchEditText.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                } else {
                    false
                }

                if (submitted) {
                    WatchSessionRepository.addLog("Human search: Pressed Enter/Search in YouTube", LogType.INFO)
                    currentPhase = LiveSearchPhase.FIND_AND_CLICK_VIDEO
                } else {
                    // Look for suggestions or submit button on next accessibility event
                    currentPhase = LiveSearchPhase.SUBMIT_QUERY
                }
            }
        } finally {
            searchEditText.recycle()
        }
    }

    private fun handleSubmitQuery(rootNode: AccessibilityNodeInfo, query: String) {
        // Try finding search suggestion item
        val suggestion = findFirstSearchSuggestion(rootNode, query)
        if (suggestion != null) {
            val clicked = suggestion.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
                    (suggestion.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
            suggestion.recycle()
            if (clicked) {
                WatchSessionRepository.addLog("Human search: Clicked search suggestion for \"$query\"", LogType.INFO)
                currentPhase = LiveSearchPhase.FIND_AND_CLICK_VIDEO
                return
            }
        }

        // Try finding a submit / search button in the search bar container
        val submitBtn = findSearchSubmitButton(rootNode)
        if (submitBtn != null) {
            val clicked = submitBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
                    (submitBtn.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
            submitBtn.recycle()
            if (clicked) {
                WatchSessionRepository.addLog("Human search: Clicked search query submit button", LogType.INFO)
                currentPhase = LiveSearchPhase.FIND_AND_CLICK_VIDEO
                return
            }
        }

        // Advance to results searching after timeout
        currentPhase = LiveSearchPhase.FIND_AND_CLICK_VIDEO
    }

    private fun findSearchButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString() ?: ""
        val text = node.text?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""

        val isSearchBtn = desc.contains("Search", ignoreCase = true) ||
                desc.contains("Khojein", ignoreCase = true) ||
                text.contains("Search", ignoreCase = true) ||
                viewId.contains("search", ignoreCase = true) ||
                viewId.contains("menu_item_0", ignoreCase = true) ||
                viewId.contains("menu_item_view", ignoreCase = true)

        if (isSearchBtn) {
            var target: AccessibilityNodeInfo? = node
            while (target != null && !target.isClickable) {
                target = target.parent
            }
            if (target != null && target.isClickable) {
                return target
            }
            if (node.isClickable) return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findSearchButton(child)
            if (found != null) {
                child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun findSearchEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val className = node.className?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val text = node.text?.toString() ?: ""

        if (className.contains("EditText", ignoreCase = true) ||
            viewId.contains("search_edit_text", ignoreCase = true) ||
            viewId.contains("search_input", ignoreCase = true) ||
            viewId.contains("search_src_text", ignoreCase = true) ||
            desc.contains("Search YouTube", ignoreCase = true) ||
            text.contains("Search YouTube", ignoreCase = true)
        ) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findSearchEditText(child)
            if (found != null) {
                child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun findFirstSearchSuggestion(node: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""

        val normText = TitleMatcher.normalize(text)
        val normQuery = TitleMatcher.normalize(query)

        val isSuggestion = viewId.contains("suggestion", ignoreCase = true) ||
                viewId.contains("search_typeahead", ignoreCase = true) ||
                (text.isNotBlank() && (normText.contains(normQuery.take(8)) || normQuery.contains(normText.take(8))))

        if (isSuggestion) {
            var target: AccessibilityNodeInfo? = node
            while (target != null && !target.isClickable) {
                target = target.parent
            }
            if (target != null && target.isClickable) {
                return target
            }
            if (node.isClickable) return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstSearchSuggestion(child, query)
            if (found != null) {
                child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun findSearchSubmitButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""

        if (desc.contains("Search", ignoreCase = true) ||
            viewId.contains("search_button", ignoreCase = true) ||
            viewId.contains("btn_search", ignoreCase = true)
        ) {
            if (node.isClickable) return node
            val parent = node.parent
            if (parent?.isClickable == true) return parent
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findSearchSubmitButton(child)
            if (found != null) {
                child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun findAndClickVideoNode(
        node: AccessibilityNodeInfo,
        targetTitle: String,
        targetChannel: String?
    ): Boolean {
        if (hasClickedTarget) return true

        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val combinedText = "$text $desc"

        if (combinedText.isNotBlank()) {
            val normText = TitleMatcher.normalize(combinedText)
            val normTarget = TitleMatcher.normalize(targetTitle)

            // Distinctive keywords from target title (length >= 3, skipping generic stopwords)
            val stopWords = setOf("the", "and", "official", "video", "audio", "with", "from", "feat", "music", "song", "lyrics", "full", "hd")
            val targetWords = normTarget.split(" ").map { it.trim() }.filter { it.length >= 3 && !stopWords.contains(it) }

            val directSubstringMatch = (normTarget.length >= 4 && normText.contains(normTarget)) ||
                    (normTarget.length >= 8 && normText.contains(normTarget.take(12)))
            val matchingWordCount = targetWords.count { word -> normText.contains(word) }
            val keywordMatch = targetWords.isNotEmpty() && matchingWordCount >= 1 &&
                    (matchingWordCount.toFloat() / targetWords.size.coerceAtLeast(1)) >= 0.30f

            if (directSubstringMatch || keywordMatch) {
                var channelMatches = true
                if (!targetChannel.isNullOrBlank() && targetChannel.length >= 3) {
                    val normChannel = TitleMatcher.normalize(targetChannel)
                    val compactChannel = normChannel.replace(" ", "")
                    val compactText = normText.replace(" ", "")
                    channelMatches = compactText.contains(compactChannel) ||
                            normText.contains(normChannel) ||
                            matchingWordCount >= 2 ||
                            directSubstringMatch
                }

                if (channelMatches) {
                    // Climb up to nearest clickable container/card
                    var clickTarget: AccessibilityNodeInfo? = node
                    while (clickTarget != null && !clickTarget.isClickable) {
                        clickTarget = clickTarget.parent
                    }

                    val toClick = if (clickTarget != null && clickTarget.isClickable) clickTarget else node

                    val nodeRect = android.graphics.Rect()
                    node.getBoundsInScreen(nodeRect)
                    val cardRect = android.graphics.Rect()
                    toClick.getBoundsInScreen(cardRect)

                    // Calculate real screen coordinates to simulate a human finger tap on the TITLE
                    val tapX = if (nodeRect.width() > 0) nodeRect.centerX() else cardRect.centerX()
                    val tapY = if (nodeRect.height() > 0) {
                        nodeRect.centerY()
                    } else if (cardRect.height() > 100) {
                        // Tapping lower half (title/info area) opens the full video player page
                        cardRect.top + (cardRect.height() * 0.70f).toInt()
                    } else {
                        cardRect.centerY()
                    }

                    // 1. Dispatch real human touch tap on video title text
                    dispatchTapGesture(tapX, tapY)

                    // 2. Perform accessibility click on the title node and card container
                    toClick.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                    hasClickedTarget = true
                    lastClickTime = System.currentTimeMillis()
                    currentPhase = LiveSearchPhase.COMPLETED
                    WatchSessionRepository.addLog(
                        "🎉 Human search: Clicked video title at ($tapX, $tapY)! Opening full watch player...",
                        LogType.SUCCESS
                    )

                    // Ensure the full watch player opens (not inline list preview)
                    val fallbackUrl = targetVideoUrl ?: if (!targetVideoId.isNullOrBlank()) "https://www.youtube.com/watch?v=$targetVideoId" else ""
                    if (fallbackUrl.isNotBlank()) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            try {
                                WatchSessionRepository.addLog("Opening full video player in YouTube", LogType.INFO)
                                val openIntent = com.example.util.PermissionHelper.openVideoIntent(applicationContext, fallbackUrl, targetTitle)
                                applicationContext.startActivity(openIntent)
                            } catch (_: Exception) {}
                        }, 400L)
                    }

                    return true
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findAndClickVideoNode(child, targetTitle, targetChannel)
            child.recycle()
            if (found) return true
        }

        return false
    }

    private fun checkPlaybackControls(node: AccessibilityNodeInfo?) {
        if (node == null) return
        try {
            val state = findPlayerControlState(node)
            when (state) {
                true -> {
                    // Explicit "Play video" / "Replay video" player control is visible -> video is paused
                    isVideoExplicitlyPaused = true
                    lastExplicitPauseTime = System.currentTimeMillis()
                    WatchSessionRepository.setPlaybackPlaying(false)
                }
                false -> {
                    // Explicit "Pause video" player control is visible -> video is playing
                    isVideoExplicitlyPaused = false
                    WatchSessionRepository.setPlaybackPlaying(true)
                }
                null -> {
                    // Controls overlay not visible in this node; preserve current pause state
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Returns true if the YouTube video player control is showing "Play video" / "Replay video" (meaning paused),
     * false if showing "Pause video" (meaning playing), or null if player controls overlay is hidden.
     */
    private fun findPlayerControlState(node: AccessibilityNodeInfo?): Boolean? {
        if (node == null) return null
        if (node.isVisibleToUser) {
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            val viewId = node.viewIdResourceName ?: ""
            val isPlayerControlBtn = viewId.contains("player_control_play_pause_replay_button", ignoreCase = true) ||
                    viewId.contains("play_pause_replay_button", ignoreCase = true) ||
                    viewId.contains("player_control", ignoreCase = true)

            if (desc.isNotEmpty() || isPlayerControlBtn) {
                val screenHeight = resources.displayMetrics.heightPixels.coerceAtLeast(800)
                val r = android.graphics.Rect()
                node.getBoundsInScreen(r)
                val inPlayerRegion = isPlayerControlBtn || (r.bottom in 1..(screenHeight * 0.38f).toInt())

                if (inPlayerRegion) {
                    if (desc.equals("Play video", ignoreCase = true) ||
                        desc.equals("Replay video", ignoreCase = true) ||
                        desc.contains("वीडियो चलाएं") ||
                        desc.contains("फिर से चलाएं") ||
                        (isPlayerControlBtn && (desc.equals("Play", ignoreCase = true) || desc.equals("Replay", ignoreCase = true)))
                    ) {
                        return true
                    }
                    if (desc.equals("Pause video", ignoreCase = true) ||
                        desc.contains("वीडियो रोकें") ||
                        (isPlayerControlBtn && desc.equals("Pause", ignoreCase = true))
                    ) {
                        return false
                    }
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findPlayerControlState(child)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    private fun checkVideoLikeState(node: AccessibilityNodeInfo?) {
        if (node == null) return
        try {
            val desc = node.contentDescription?.toString() ?: ""
            val text = node.text?.toString() ?: ""
            val combined = "$desc $text".lowercase()

            // In YouTube, a video that is already liked has an "unlike" action,
            // or the like button has isSelected == true, or description says "liked"
            if (combined.contains("unlike") || combined.contains("remove like") || 
                (combined.contains("like") && !combined.contains("dislike") && (node.isSelected || combined.contains("liked")))) {
                val taskId = WatchSessionRepository.activeTaskId.value
                if (!taskId.isNullOrBlank()) {
                    WatchSessionRepository.onVideoAlreadyLikedDetected?.invoke(taskId)
                }
                return
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                checkVideoLikeState(child)
                child.recycle()
            }
        } catch (_: Exception) {}
    }

    private fun isReadyForWatchVerification(): Boolean {
        val elapsedSinceLaunch = System.currentTimeMillis() - WatchSessionRepository.taskLaunchTimestampMillis
        if (elapsedSinceLaunch > 8500L && currentPhase != LiveSearchPhase.IDLE && currentPhase != LiveSearchPhase.COMPLETED) {
            currentPhase = LiveSearchPhase.COMPLETED
        }
        val isLiveSearching = currentPhase != LiveSearchPhase.IDLE && currentPhase != LiveSearchPhase.COMPLETED
        val elapsedSinceClick = if (lastClickTime > 0L) System.currentTimeMillis() - lastClickTime else elapsedSinceLaunch
        return !isLiveSearching && elapsedSinceLaunch > 5000L && elapsedSinceClick > 4500L
    }

    fun inspectCurrentYouTubeState() {
        val isSessionActive = WatchSessionRepository.sessionState.value == com.example.data.SessionState.ACTIVE
        if (!isSessionActive) return

        val elapsedSinceLaunch = System.currentTimeMillis() - WatchSessionRepository.taskLaunchTimestampMillis
        if (elapsedSinceLaunch < 4500L || !WatchSessionRepository.hasLeftAppForYouTube) return

        val myPkg = packageName ?: "com.example"
        var ytAppRoot: AccessibilityNodeInfo? = null

        try {
            val winList = try { windows } catch (_: Exception) { null }
            if (!winList.isNullOrEmpty()) {
                var topExternalAppPkg: String? = null
                for (w in winList) {
                    if (w.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) {
                        val wRoot = try { w.root } catch (_: Exception) { null }
                        val wPkg = wRoot?.packageName?.toString() ?: ""
                        if (wPkg == "com.google.android.youtube") {
                            if (w.isInPictureInPictureMode) {
                                isYouTubeInForeground = false
                                WatchSessionRepository.triggerTaskIncomplete(
                                    "Task Incomplete! Aapne YouTube minimize kar diya hai. Task complete hone tak YouTube par target video dekhna zaroori hai."
                                )
                                return
                            }
                            if (ytAppRoot == null) {
                                ytAppRoot = wRoot
                            }
                        }
                        if (topExternalAppPkg == null && wPkg.isNotBlank() && wPkg != myPkg && !isTransientSystemPackage(wPkg)) {
                            topExternalAppPkg = wPkg
                        }
                    }
                }

                if (topExternalAppPkg != null && topExternalAppPkg != "com.google.android.youtube") {
                    notInYouTubeStrikeCount++
                    if (notInYouTubeStrikeCount >= 2) {
                        isYouTubeInForeground = false
                        WatchSessionRepository.triggerTaskIncomplete(
                            "Task Incomplete! Aapne YouTube minimize kar diya ya YouTube se back kar ke doosre app mein switch kar liya."
                        )
                    }
                    return
                } else if (topExternalAppPkg == "com.google.android.youtube") {
                    notInYouTubeStrikeCount = 0
                    isYouTubeInForeground = true
                }
            } else {
                val activeRoot = try { rootInActiveWindow } catch (_: Exception) { null }
                val inPip = try { activeRoot?.window?.isInPictureInPictureMode == true } catch (_: Exception) { false }
                if (inPip) {
                    isYouTubeInForeground = false
                    WatchSessionRepository.triggerTaskIncomplete(
                        "Task Incomplete! Aapne YouTube minimize kar diya hai."
                    )
                    return
                }
                val activePkg = activeRoot?.packageName?.toString() ?: ""
                if (activePkg.isNotBlank() && activePkg != "com.google.android.youtube" && activePkg != myPkg && !isTransientSystemPackage(activePkg)) {
                    notInYouTubeStrikeCount++
                    if (notInYouTubeStrikeCount >= 2) {
                        isYouTubeInForeground = false
                        WatchSessionRepository.triggerTaskIncomplete(
                            "Task Incomplete! Aapne YouTube minimize kar diya ya YouTube se back kar ke doosre app mein switch kar liya."
                        )
                    }
                    return
                } else if (activePkg == "com.google.android.youtube") {
                    notInYouTubeStrikeCount = 0
                    isYouTubeInForeground = true
                    ytAppRoot = activeRoot
                }
            }

            if (isYouTubeInForeground && isReadyForWatchVerification()) {
                val rootToInspect = ytAppRoot ?: try { rootInActiveWindow } catch (_: Exception) { null }
                if (rootToInspect != null) {
                    checkPlaybackControls(rootToInspect)
                    val now = System.currentTimeMillis()
                    if (now - lastWatchHeaderCheckTime >= 850L) {
                        lastWatchHeaderCheckTime = now
                        verifyActiveYouTubeVideo(rootToInspect)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun checkIfUserClickedDifferentVideo(
        clickedNode: AccessibilityNodeInfo?,
        desc: String,
        text: String,
        viewId: String,
        eventSummary: String = ""
    ) {
        val targetTitle = WatchSessionRepository.targetTaskTitle.value ?: return
        val targetAuthor = WatchSessionRepository.targetTaskAuthor.value

        if (desc.equals("Next video", ignoreCase = true) ||
            desc.equals("Previous video", ignoreCase = true) ||
            desc.contains("अगला वीडियो") ||
            desc.contains("पिछला वीडियो") ||
            viewId.contains("player_control_next", ignoreCase = true) ||
            viewId.contains("player_control_previous", ignoreCase = true) ||
            viewId.contains("autonav", ignoreCase = true)
        ) {
            WatchSessionRepository.triggerTaskIncomplete(
                "Task Incomplete! Aapne YouTube player mein doosra video switch kar diya. Sirf target video dekhne par hi timer chalega."
            )
            return
        }

        if ((desc.equals("Shorts", ignoreCase = true) || text.equals("Shorts", ignoreCase = true)) &&
            !targetTitle.contains("shorts", ignoreCase = true)
        ) {
            WatchSessionRepository.triggerTaskIncomplete(
                "Task Incomplete! Aapne target video chod kar YouTube Shorts open kar liya."
            )
            return
        }

        // If user manually clicks YouTube bottom navigation tabs (Home, Subscriptions) or Search icon while watching
        if (desc.equals("Home", ignoreCase = true) ||
            desc.equals("Subscriptions", ignoreCase = true) ||
            viewId.contains("search_edit_text", ignoreCase = true) ||
            viewId.contains("menu_item_search", ignoreCase = true) ||
            desc.equals("Search", ignoreCase = true) ||
            desc.equals("Search YouTube", ignoreCase = true)
        ) {
            WatchSessionRepository.triggerTaskIncomplete(
                "Task Incomplete! Aapne target video chod kar YouTube mein doosra video search ya switch kar liya."
            )
            return
        }

        val screenHeight = resources.displayMetrics.heightPixels.coerceAtLeast(800)
        var cardNode: AccessibilityNodeInfo? = clickedNode
        var depth = 0
        while (cardNode != null && depth < 4) {
            val parent = cardNode.parent ?: break
            val pRect = android.graphics.Rect()
            parent.getBoundsInScreen(pRect)
            if (pRect.height() in 48..(screenHeight * 0.65f).toInt()) {
                cardNode = parent
                if (parent.isClickable) break
            } else {
                break
            }
            depth++
        }

        val cardRect = android.graphics.Rect()
        cardNode?.getBoundsInScreen(cardRect)

        val sb = StringBuilder()
        if (eventSummary.isNotBlank()) sb.append(eventSummary).append(" ")
        if (text.isNotBlank() && !sb.contains(text)) sb.append(text).append(" ")
        if (desc.isNotBlank() && !sb.contains(desc)) sb.append(desc).append(" ")
        if (cardNode != null) {
            collectSubtreeText(cardNode, sb, 0)
        }
        val cardText = sb.toString().trim()
        val lowerCard = cardText.lowercase()
        val cardViewId = (cardNode?.viewIdResourceName ?: viewId).lowercase()

        // Ignore harmless controls, comments, channel subscribe, share, or description expanders
        if (lowerCard.contains("reply") ||
            lowerCard.contains("add a comment") ||
            lowerCard.contains("pinned by") ||
            lowerCard.equals("subscribe", ignoreCase = true) ||
            lowerCard.equals("subscribed", ignoreCase = true) ||
            lowerCard.equals("share", ignoreCase = true) ||
            lowerCard.equals("download", ignoreCase = true) ||
            lowerCard.equals("remix", ignoreCase = true) ||
            lowerCard.equals("save", ignoreCase = true) ||
            lowerCard.equals("close", ignoreCase = true) ||
            lowerCard.contains("skip ad") ||
            cardViewId.contains("comment") ||
            cardViewId.contains("subscribe") ||
            cardViewId.contains("expand")
        ) {
            return
        }

        val isBelowPlayer = cardRect.top >= (screenHeight * 0.28f).toInt()
        val hasDurationTimestamp = Regex("\\b\\d{1,2}:\\d{2}\\b").containsMatchIn(lowerCard)

        val isVideoListingCard = (cardText.length >= 10 && (
                lowerCard.contains("go to channel") ||
                lowerCard.contains("play video") ||
                lowerCard.contains("views") ||
                lowerCard.contains("watching") ||
                lowerCard.contains("streamed") ||
                lowerCard.contains("बार देखा गया") ||
                (isBelowPlayer && (lowerCard.contains("minutes") || lowerCard.contains("seconds") || hasDurationTimestamp)) ||
                cardViewId.contains("video_lockup") ||
                cardViewId.contains("compact_video") ||
                cardViewId.contains("video_card") ||
                cardViewId.contains("rich_item") ||
                cardViewId.contains("reel_item") ||
                cardViewId.contains("related_item") ||
                cardViewId.contains("endscreen")
        )) || (isBelowPlayer && lowerCard.equals("play video", ignoreCase = true))

        if (isVideoListingCard) {
            if (lowerCard.equals("play video", ignoreCase = true)) {
                WatchSessionRepository.triggerTaskIncomplete(
                    "Task Incomplete! Aapne YouTube mein target video (\"$targetTitle\") ke bajaye doosra video play kar diya. Sirf target title aur channel wala video play hone par hi timer chalega."
                )
                return
            }
            val match = TitleMatcher.evaluateMatch(
                playingTitle = cardText,
                taskTitle = targetTitle,
                playingArtist = null,
                taskAuthor = targetAuthor
            )
            if (match == com.example.data.MatchResult.MISMATCH) {
                WatchSessionRepository.triggerTaskIncomplete(
                    "Task Incomplete! Aapne YouTube mein target video (\"$targetTitle\") ke bajaye doosra video play kar diya. Sirf target title aur channel wala video play hone par hi timer chalega."
                )
            }
        }
    }

    private fun collectSubtreeText(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null || depth > 7) return
        val t = node.text?.toString()?.trim()
        val d = node.contentDescription?.toString()?.trim()
        if (!t.isNullOrBlank()) sb.append(t).append(" ")
        if (!d.isNullOrBlank() && d != t) sb.append(d).append(" ")
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectSubtreeText(child, sb, depth + 1)
            child.recycle()
        }
    }

    private data class UiNodeEntry(
        val text: String,
        val desc: String,
        val viewId: String,
        val rect: android.graphics.Rect
    )

    private fun collectScreenNodes(
        node: AccessibilityNodeInfo?,
        out: MutableList<UiNodeEntry>,
        depth: Int = 0
    ) {
        if (node == null || depth > 35 || out.size > 650) return
        if (!node.isVisibleToUser) return
        val t = node.text?.toString()?.trim() ?: ""
        val d = node.contentDescription?.toString()?.trim() ?: ""
        val v = node.viewIdResourceName ?: ""
        val hasRelevantViewId = v.isNotEmpty() && (
                v.contains("player", ignoreCase = true) ||
                v.contains("reel", ignoreCase = true) ||
                v.contains("subscribe", ignoreCase = true) ||
                v.contains("like", ignoreCase = true) ||
                v.contains("title", ignoreCase = true) ||
                v.contains("miniplayer", ignoreCase = true) ||
                v.contains("floaty", ignoreCase = true) ||
                v.contains("watch", ignoreCase = true) ||
                v.contains("search", ignoreCase = true) ||
                v.contains("ad_", ignoreCase = true)
        )
        if (t.isNotEmpty() || d.isNotEmpty() || hasRelevantViewId) {
            val r = android.graphics.Rect()
            node.getBoundsInScreen(r)
            if (r.width() > 0 && r.height() > 0) {
                out.add(UiNodeEntry(t, d, v, r))
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectScreenNodes(child, out, depth + 1)
            child.recycle()
        }
    }

    private fun verifyActiveYouTubeVideo(rootNode: AccessibilityNodeInfo?) {
        if (rootNode == null) return
        val targetTitle = WatchSessionRepository.targetTaskTitle.value ?: return
        val targetAuthor = WatchSessionRepository.targetTaskAuthor.value

        try {
            val entries = mutableListOf<UiNodeEntry>()
            collectScreenNodes(rootNode, entries)
            if (entries.isEmpty()) return

            val screenHeight = resources.displayMetrics.heightPixels.coerceAtLeast(800)
            val density = resources.displayMetrics.density

            // 1. Check if a video Ad is currently playing INSIDE the top video player (ignore Sponsored feed banners below player!)
            val isAdPlayingInPlayer = entries.any { e ->
                val inTopPlayer = e.rect.bottom in 1..(screenHeight * 0.36f).toInt()
                val combined = "${e.text} ${e.desc} ${e.viewId}".lowercase()
                inTopPlayer && (
                        combined.contains("skip ad") ||
                        combined.contains("skip ads") ||
                        combined.contains("ad_progress") ||
                        combined.contains("ad_countdown") ||
                        e.text.startsWith("Ad ·", ignoreCase = true) ||
                        e.text.startsWith("Sponsored ·", ignoreCase = true)
                )
            }
            if (isAdPlayingInPlayer) {
                wrongVideoStrikeCount = 0
                return
            }

            // 2. Check if user switched to YouTube Shorts player
            val isShortsPlayer = entries.any { e ->
                val v = e.viewId.lowercase()
                v.contains("reel_player") || v.contains("reel_recycler") || v.contains("reel_dyn_")
            }
            if (isShortsPlayer) {
                val allShortsText = entries.joinToString(" ") { "${it.text} ${it.desc}" }
                val shortsMatch = TitleMatcher.evaluateMatch(allShortsText, targetTitle, null, targetAuthor)
                if (shortsMatch == com.example.data.MatchResult.MISMATCH) {
                    wrongVideoStrikeCount++
                    if (wrongVideoStrikeCount >= 2) {
                        wrongVideoStrikeCount = 0
                        WatchSessionRepository.triggerTaskIncomplete(
                            "Task Incomplete! Aapne target video chod kar YouTube Shorts play kar diya."
                        )
                    }
                    return
                }
            }

            // 3. Check explicit player title / miniplayer title if visible
            val explicitPlayerTitleNode = entries.firstOrNull { e ->
                val v = e.viewId.lowercase()
                (v.contains("player_video_title") || v.contains("miniplayer_title") || v.contains("floaty_bar_title")) &&
                        (e.text.length >= 4 || e.desc.length >= 4)
            }
            if (explicitPlayerTitleNode != null) {
                val pTitle = explicitPlayerTitleNode.text.ifBlank { explicitPlayerTitleNode.desc }
                val match = TitleMatcher.evaluateMatch(pTitle, targetTitle, null, targetAuthor)
                if (match == com.example.data.MatchResult.MISMATCH) {
                    wrongVideoStrikeCount++
                    if (wrongVideoStrikeCount >= 2) {
                        wrongVideoStrikeCount = 0
                        WatchSessionRepository.triggerTaskIncomplete(
                            "Task Incomplete! Aapne YouTube mein target video (\"$targetTitle\") ke bajaye doosra video (\"$pTitle\") play kar diya."
                        )
                    }
                    return
                } else if (match == com.example.data.MatchResult.MATCH) {
                    wrongVideoStrikeCount = 0
                    return
                }
            }

            // 3b. Check if user collapsed the player to browse YouTube Search or Home feed
            val isSearchBarAtTop = entries.any { e ->
                e.rect.top < (screenHeight * 0.18f).toInt() && (
                        e.viewId.contains("search_edit_text", ignoreCase = true) ||
                        e.viewId.contains("search_box", ignoreCase = true)
                )
            }
            if (isSearchBarAtTop) {
                wrongVideoStrikeCount++
                if (wrongVideoStrikeCount >= 2) {
                    wrongVideoStrikeCount = 0
                    WatchSessionRepository.triggerTaskIncomplete(
                        "Task Incomplete! Aapne target video player minimize/band kar ke YouTube search open kar liya."
                    )
                }
                return
            }

            // 4. Check Watch Metadata Header (between bottom of video player and Subscribe / Like / Comments row)
            val anchorNode = entries.filter { e ->
                val t = e.text.lowercase()
                val d = e.desc.lowercase()
                val v = e.viewId.lowercase()
                val inMiddleBand = e.rect.top in (screenHeight * 0.20f).toInt()..(screenHeight * 0.82f).toInt()
                inMiddleBand && (
                        t == "subscribe" ||
                        t == "subscribed" ||
                        t.contains("सदस्यता") ||
                        d.startsWith("subscribe") ||
                        d.contains("subscribe to") ||
                        d.contains("सदस्यता") ||
                        v.contains("subscribe_button") ||
                        d.contains("like this video") ||
                        v.contains("like_button") ||
                        v.contains("share_button") ||
                        t == "remix" ||
                        t == "download" ||
                        t.startsWith("comments") ||
                        d.startsWith("comments")
                )
            }.minByOrNull { it.rect.top }

            val headerTopY = (screenHeight * 0.16f).toInt()
            val headerBottomY = if (anchorNode != null) {
                anchorNode.rect.bottom + (36 * density).toInt()
            } else {
                // Fallback: inspect watch title zone right below the 16:9 player if no Sponsored panel is covering it
                val hasSponsoredOverlayBelowPlayer = entries.any { e ->
                    e.rect.top in (screenHeight * 0.25f).toInt()..(screenHeight * 0.48f).toInt() &&
                            (e.text.equals("Sponsored", ignoreCase = true) || e.desc.equals("Sponsored", ignoreCase = true))
                }
                if (!hasSponsoredOverlayBelowPlayer) (screenHeight * 0.45f).toInt() else -1
            }

            if (headerBottomY > headerTopY) {
                val chromeLabels = setOf(
                    "subscribe", "subscribed", "join", "share", "remix", "download",
                    "clip", "save", "report", "comments", "more", "...more",
                    "play video", "pause video", "autoplay is on", "autoplay is off",
                    "mute", "unmute", "full screen", "collapse", "close", "sponsored", "visit site"
                )

                val headerTexts = mutableListOf<String>()
                for (e in entries) {
                    if (e.rect.top >= headerTopY && e.rect.bottom <= headerBottomY) {
                        for (candidate in listOf(e.text, e.desc)) {
                            val clean = candidate.trim()
                            if (clean.length >= 3 && !chromeLabels.contains(clean.lowercase())) {
                                headerTexts.add(clean)
                            }
                        }
                    }
                }

                // Check if there is a real video title in the header (not just view count / timestamp / channel name)
                val normAuthor = TitleMatcher.normalize(targetAuthor)
                val titleCandidates = headerTexts.filter { txt ->
                    val low = txt.lowercase()
                    val normTxt = TitleMatcher.normalize(txt)
                    val isJustChannel = normAuthor.isNotEmpty() && (normTxt == normAuthor || normTxt.replace(" ", "") == normAuthor.replace(" ", ""))
                    txt.length >= 5 &&
                            !isJustChannel &&
                            !low.matches(Regex("^[0-9:\\s/•·]+$")) &&
                            !low.startsWith("like this video") &&
                            !low.startsWith("dislike this video") &&
                            !low.startsWith("subscribe to") &&
                            !low.startsWith("save to") &&
                            !low.startsWith("share") &&
                            !low.startsWith("comments") &&
                            !(low.contains("views") && (low.contains("ago") || low.length < 38) && !low.contains("...more")) &&
                            !(low.contains("subscribers") && txt.length < 35)
                }

                if (titleCandidates.isNotEmpty()) {
                    val combinedTitleCandidates = titleCandidates.joinToString(" ")
                    val anyTitleMatch = titleCandidates.any { cand ->
                        TitleMatcher.evaluateMatch(cand, targetTitle, null, targetAuthor) == com.example.data.MatchResult.MATCH
                    } || TitleMatcher.evaluateMatch(combinedTitleCandidates, targetTitle, null, targetAuthor) == com.example.data.MatchResult.MATCH

                    // Also verify channel if targetAuthor is specified and Subscribe row is visible
                    var channelMatch = true
                    val isGenericAuthor = normAuthor.isEmpty() ||
                            normAuthor == "youtube creator" ||
                            normAuthor == "youtube channel" ||
                            normAuthor == "youtube"
                    if (!isGenericAuthor && anchorNode != null &&
                        (anchorNode.text.contains("Subscribe", ignoreCase = true) || anchorNode.desc.contains("Subscribe", ignoreCase = true))
                    ) {
                        val combinedHeader = headerTexts.joinToString(" ")
                        val normHeader = TitleMatcher.normalize(combinedHeader)
                        val compactHeader = normHeader.replace(" ", "")
                        val compactAuthor = normAuthor.replace(" ", "")
                        val authorWords = normAuthor.split(" ").filter { it.length >= 3 }
                        channelMatch = normHeader.contains(normAuthor) ||
                                compactHeader.contains(compactAuthor) ||
                                (authorWords.isNotEmpty() && authorWords.all { normHeader.contains(it) })
                    }

                    if (anyTitleMatch && channelMatch) {
                        wrongVideoStrikeCount = 0
                    } else {
                        wrongVideoStrikeCount++
                        if (wrongVideoStrikeCount >= 2) {
                            val detectedWrong = titleCandidates.firstOrNull() ?: "Doosra video"
                            wrongVideoStrikeCount = 0
                            WatchSessionRepository.triggerTaskIncomplete(
                                "Task Incomplete! Aapne YouTube par target video (\"$targetTitle\") ke bajaye doosra video (\"$detectedWrong\") play kar diya. Sirf target title aur channel wala video play hone par hi timer chalega."
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun scrollForward(node: AccessibilityNodeInfo): Boolean {
        if (node.isScrollable) {
            val scrolled = node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            if (scrolled) {
                WatchSessionRepository.addLog("Human search: Scrolling YouTube search results to locate video...", LogType.INFO)
                return true
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (scrollForward(child)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
    }

    private fun dispatchTapGesture(x: Int, y: Int): Boolean {
        if (x <= 0 || y <= 0) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = android.graphics.Path().apply {
                moveTo(x.toFloat(), y.toFloat())
            }
            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 75)
            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(stroke)
                .build()
            return dispatchGesture(gesture, null, null)
        }
        return false
    }

    override fun onInterrupt() {
        WatchSessionRepository.addLog("YouTube Live Search Service Interrupted", LogType.WARNING)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        isServiceConnected = false
        currentPhase = LiveSearchPhase.IDLE
    }
}
