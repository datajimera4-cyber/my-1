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

        @Volatile
        private var lockedWatchPageTitle: String? = null

        @Volatile
        private var hasTypedCommentText: Boolean = false

        @Volatile
        private var lastTypedCommentTime: Long = 0L

        private val rewardedLikedTaskIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

        private var scrollAttempts = 0

        fun resetMonitoringCounters() {
            wrongVideoStrikeCount = 0
            notInYouTubeStrikeCount = 0
            lastWatchHeaderCheckTime = 0L
            lastExplicitPauseTime = 0L
            lockedWatchPageTitle = null
            hasTypedCommentText = false
            lastTypedCommentTime = 0L
        }

        fun prepareForDirectWatch(title: String, channel: String?, videoUrl: String? = null, videoId: String? = null) {
            targetSearchTitle = title
            targetSearchChannel = channel
            targetVideoUrl = videoUrl
            targetVideoId = videoId
            hasClickedTarget = true
            scrollAttempts = 0
            lastClickTime = System.currentTimeMillis()
            isVideoExplicitlyPaused = false
            resetMonitoringCounters()
            currentPhase = LiveSearchPhase.COMPLETED
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
            if (isSessionActive && elapsedSinceLaunch > 1500L) {
                WatchSessionRepository.hasLeftAppForYouTube = true
            }
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

        // Track genuine comment typing inside YouTube comment box (excluding top search bar)
        if (pkg == "com.google.android.youtube" &&
            (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
             event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED)
        ) {
            try {
                val src = event.source
                val vId = src?.viewIdResourceName?.lowercase() ?: ""
                val isSearchField = vId.contains("search_edit_text") ||
                        vId.contains("search_src_text") ||
                        vId.contains("search_box") ||
                        (currentPhase != LiveSearchPhase.IDLE && currentPhase != LiveSearchPhase.COMPLETED && !hasClickedTarget)
                if (!isSearchField) {
                    val typed = (src?.text?.toString() ?: event.text?.joinToString(" ") { it.toString() } ?: "").trim()
                    val lowerTyped = typed.lowercase()
                    val isPlaceholder = lowerTyped.isEmpty() ||
                            lowerTyped.startsWith("add a comment") ||
                            lowerTyped.startsWith("add a reply") ||
                            lowerTyped.startsWith("search youtube") ||
                            lowerTyped.contains("टिप्पणी जोड़ें")
                    if (!isPlaceholder && typed.length >= 2) {
                        hasTypedCommentText = true
                        lastTypedCommentTime = System.currentTimeMillis()
                    }
                }
                src?.recycle()
            } catch (_: Exception) {}
        }

        // Detect user interactions INSIDE YouTube ONLY (ignore clicks on our own floating overlay!)
        if (pkg == "com.google.android.youtube" && isYouTubeInForeground && event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
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
                val isUnlike = combined.contains("unlike") ||
                        combined.contains("remove like") ||
                        combined.contains("हटाएं") ||
                        node?.isSelected == true ||
                        node?.isChecked == true

                val inWatchActionBarBand = clickRect.top in (screenHeight * 0.22f).toInt()..(screenHeight * 0.62f).toInt()

                // Genuine first-time Like click on the target YouTube video's Like button
                val isGenuineVideoLikeClick = isSessionActive &&
                        elapsedSinceLaunch > 2500L &&
                        inWatchActionBarBand &&
                        !isDislike &&
                        !isUnlike &&
                        !looksLikeVideoCard &&
                        combined.length < 95 && (
                                desc.startsWith("like this video", ignoreCase = true) ||
                                combined.contains("like this video") ||
                                viewId.contains("like_button", ignoreCase = true) ||
                                desc.equals("Like", ignoreCase = true) ||
                                desc.contains("पसंद करें")
                        )

                // Genuine Comment submission: user MUST have typed text in the comment box AND clicked Send/Post
                val isCommentSendButton = !looksLikeVideoCard && combined.length < 80 && (
                        desc.equals("Send", ignoreCase = true) ||
                        desc.equals("Send comment", ignoreCase = true) ||
                        desc.equals("Post", ignoreCase = true) ||
                        desc.equals("Post comment", ignoreCase = true) ||
                        desc.contains("टिप्पणी भेजें") ||
                        desc.equals("भेजें", ignoreCase = true) ||
                        viewId.contains("send_button", ignoreCase = true) ||
                        viewId.contains("post_button", ignoreCase = true) ||
                        viewId.contains("comment_send", ignoreCase = true)
                )
                val isGenuineCommentSubmitted = isSessionActive &&
                        elapsedSinceLaunch > 2500L &&
                        isCommentSendButton &&
                        hasTypedCommentText &&
                        (System.currentTimeMillis() - lastTypedCommentTime) < 180_000L

                if (isGenuineVideoLikeClick) {
                    val activeId = WatchSessionRepository.activeTaskId.value ?: "default_rick"
                    if (rewardedLikedTaskIds.add(activeId)) {
                        WatchSessionRepository.onTaskLikeDetected?.invoke()
                    }
                } else if (isGenuineCommentSubmitted) {
                    hasTypedCommentText = false
                    lastTypedCommentTime = 0L
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
                    handler.postDelayed({ inspectCurrentYouTubeState() }, 450L)
                    handler.postDelayed({ inspectCurrentYouTubeState() }, 1000L)
                    handler.postDelayed({ inspectCurrentYouTubeState() }, 1800L)
                    handler.postDelayed({ inspectCurrentYouTubeState() }, 2800L)
                }
                node?.recycle()
            } catch (_: Exception) {}
        }

        // If target was already clicked or idle, monitor playback controls & active video in YouTube
        if (hasClickedTarget || currentPhase == LiveSearchPhase.IDLE || currentPhase == LiveSearchPhase.COMPLETED) {
            if (isYouTubeInForeground && (
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
            )) {
                val ytRoot = getYouTubeRootNode() ?: (if (pkg == "com.google.android.youtube") event.source else null)
                if (ytRoot != null) {
                    checkPlaybackControls(ytRoot)
                    if (isSessionActive && isReadyForWatchVerification()) {
                        val now = System.currentTimeMillis()
                        if (now - lastWatchHeaderCheckTime >= 650L) {
                            lastWatchHeaderCheckTime = now
                            verifyActiveYouTubeVideo(ytRoot)
                        }
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

    private fun getYouTubeRootNode(): AccessibilityNodeInfo? {
        try {
            val active = try { rootInActiveWindow } catch (_: Exception) { null }
            if (active?.packageName?.toString() == "com.google.android.youtube" && active.childCount > 0) {
                return active
            }
            val winList = try { windows } catch (_: Exception) { null }
            if (!winList.isNullOrEmpty()) {
                for (w in winList) {
                    if (w.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) {
                        val wRoot = try { w.root } catch (_: Exception) { null }
                        if (wRoot?.packageName?.toString() == "com.google.android.youtube" && wRoot.childCount > 0) {
                            return wRoot
                        }
                    }
                }
            }
            if (active?.packageName?.toString() == "com.google.android.youtube") {
                return active
            }
        } catch (_: Exception) {}
        return null
    }

    private fun isReadyForWatchVerification(): Boolean {
        val elapsedSinceLaunch = System.currentTimeMillis() - WatchSessionRepository.taskLaunchTimestampMillis
        if (elapsedSinceLaunch > 7500L && currentPhase != LiveSearchPhase.IDLE && currentPhase != LiveSearchPhase.COMPLETED) {
            currentPhase = LiveSearchPhase.COMPLETED
        }
        val isLiveSearching = currentPhase != LiveSearchPhase.IDLE && currentPhase != LiveSearchPhase.COMPLETED
        val elapsedSinceClick = if (lastClickTime > 0L) System.currentTimeMillis() - lastClickTime else elapsedSinceLaunch
        return !isLiveSearching && elapsedSinceLaunch > 4200L && elapsedSinceClick > 3500L
    }

    fun inspectCurrentYouTubeState() {
        val isSessionActive = WatchSessionRepository.sessionState.value == com.example.data.SessionState.ACTIVE
        if (!isSessionActive) return

        val elapsedSinceLaunch = System.currentTimeMillis() - WatchSessionRepository.taskLaunchTimestampMillis
        if (elapsedSinceLaunch < 4000L || !WatchSessionRepository.hasLeftAppForYouTube) return

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
                val rootToInspect = getYouTubeRootNode() ?: ytAppRoot
                if (rootToInspect != null) {
                    checkPlaybackControls(rootToInspect)
                    val now = System.currentTimeMillis()
                    if (now - lastWatchHeaderCheckTime >= 650L) {
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

        // If user manually clicks YouTube bottom navigation tabs (Home, Subscriptions, You) or Search / Collapse while watching
        if (desc.equals("Home", ignoreCase = true) ||
            desc.equals("Subscriptions", ignoreCase = true) ||
            desc.equals("Library", ignoreCase = true) ||
            desc.equals("You", ignoreCase = true) ||
            desc.equals("Minimize", ignoreCase = true) ||
            desc.equals("Collapse", ignoreCase = true) ||
            viewId.contains("search_edit_text", ignoreCase = true) ||
            viewId.contains("menu_item_search", ignoreCase = true) ||
            viewId.contains("player_collapse_button", ignoreCase = true) ||
            desc.equals("Search", ignoreCase = true) ||
            desc.equals("Search YouTube", ignoreCase = true)
        ) {
            WatchSessionRepository.triggerTaskIncomplete(
                "Task Incomplete! Aapne target video se hat kar YouTube mein doosra page ya search open kar liya."
            )
            return
        }

        val screenHeight = resources.displayMetrics.heightPixels.coerceAtLeast(800)
        val density = resources.displayMetrics.density
        val clickRect = android.graphics.Rect()
        clickedNode?.getBoundsInScreen(clickRect)

        // Check if the directly clicked element itself is a harmless watch header or comment control
        val selfText = "$desc $text $eventSummary $viewId".lowercase()
        if (selfText.contains("reply") ||
            selfText.contains("add a comment") ||
            selfText.contains("pinned by") ||
            desc.equals("Subscribe", ignoreCase = true) ||
            desc.equals("Subscribed", ignoreCase = true) ||
            desc.startsWith("Subscribe to", ignoreCase = true) ||
            desc.equals("Share", ignoreCase = true) ||
            desc.equals("Download", ignoreCase = true) ||
            desc.equals("Remix", ignoreCase = true) ||
            desc.equals("Save", ignoreCase = true) ||
            desc.equals("Close", ignoreCase = true) ||
            desc.equals("Expand description", ignoreCase = true) ||
            text.equals("...more", ignoreCase = true) ||
            selfText.contains("skip ad") ||
            viewId.contains("comment", ignoreCase = true) ||
            viewId.contains("subscribe", ignoreCase = true) ||
            viewId.contains("like_button", ignoreCase = true) ||
            viewId.contains("dislike_button", ignoreCase = true) ||
            viewId.contains("share_button", ignoreCase = true)
        ) {
            return
        }

        // Climb at most 2 parent levels and NEVER into a container taller than 36% of screen height
        var cardNode: AccessibilityNodeInfo? = clickedNode
        var depth = 0
        while (cardNode != null && depth < 2) {
            val parent = cardNode.parent ?: break
            val pRect = android.graphics.Rect()
            parent.getBoundsInScreen(pRect)
            if (pRect.top >= (screenHeight * 0.28f).toInt() && pRect.height() in 48..(screenHeight * 0.36f).toInt()) {
                cardNode = parent
            } else {
                break
            }
            depth++
        }

        val cardRect = android.graphics.Rect()
        if (cardNode != null) {
            cardNode.getBoundsInScreen(cardRect)
        } else {
            cardRect.set(clickRect)
        }

        val sb = StringBuilder()
        if (eventSummary.isNotBlank()) sb.append(eventSummary).append(" ")
        if (text.isNotBlank() && !sb.contains(text)) sb.append(text).append(" ")
        if (desc.isNotBlank() && !sb.contains(desc)) sb.append(desc).append(" ")
        if (cardNode != null) {
            collectSubtreeText(cardNode, sb, 0)
        }

        // If clicked on a thumbnail ImageView or Litho child below the player that had no direct text,
        // gather sibling text nodes in the same vertical card band (including title right below thumbnail)!
        val isBelowPlayer = cardRect.top >= (screenHeight * 0.28f).toInt()
        if (isBelowPlayer && sb.length < 25 && cardRect.height() >= (44 * density).toInt()) {
            val ytRoot = getYouTubeRootNode()
            if (ytRoot != null) {
                val allNodes = mutableListOf<UiNodeEntry>()
                collectScreenNodes(ytRoot, allNodes)
                val bandTop = (cardRect.top - (16 * density).toInt()).coerceAtLeast((screenHeight * 0.28f).toInt())
                val bandBottom = cardRect.bottom + (110 * density).toInt()
                for (n in allNodes) {
                    if (n.rect.top in bandTop..bandBottom) {
                        if (n.text.isNotBlank() && !sb.contains(n.text)) sb.append(n.text).append(" ")
                        if (n.desc.isNotBlank() && !sb.contains(n.desc)) sb.append(n.desc).append(" ")
                    }
                }
            }
        }

        val cardText = sb.toString().trim()
        val lowerCard = cardText.lowercase()
        val cardViewId = (cardNode?.viewIdResourceName ?: viewId).lowercase()

        val hasDurationTimestamp = Regex("\\b\\d{1,2}:\\d{2}\\b").containsMatchIn(lowerCard)
        val isLargeThumbnailTap = cardRect.top >= (screenHeight * 0.36f).toInt() &&
                cardRect.width() >= (110 * density).toInt() &&
                cardRect.height() >= (65 * density).toInt()

        val isVideoListingCard = (cardText.length >= 6 && (
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
        )) || (isBelowPlayer && lowerCard.equals("play video", ignoreCase = true)) || isLargeThumbnailTap

        if (isVideoListingCard) {
            if (lowerCard.equals("play video", ignoreCase = true)) {
                WatchSessionRepository.triggerTaskIncomplete(
                    "Task Incomplete! Aapne YouTube mein target video (\"$targetTitle\") ke bajaye doosra video play kar diya. Sirf target title aur channel wala video play hone par hi timer chalega."
                )
                return
            }
            val cleanClickedTitle = extractCleanTitleCandidate(cardText).ifBlank { cardText }
            val match = TitleMatcher.evaluateMatch(
                playingTitle = cleanClickedTitle,
                taskTitle = targetTitle,
                playingArtist = null,
                taskAuthor = targetAuthor
            )
            if (match != com.example.data.MatchResult.MATCH) {
                WatchSessionRepository.triggerTaskIncomplete(
                    "Task Incomplete! Aapne YouTube mein target video (\"$targetTitle\") ke bajaye doosra video play kar diya. Sirf target title aur channel wala video play hone par hi timer chalega."
                )
            }
        }
    }

    private fun collectSubtreeText(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null || depth > 8) return
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
        depth: Int = 0,
        visitedCount: IntArray = intArrayOf(0)
    ) {
        if (node == null || depth > 42 || out.size > 750 || visitedCount[0] > 1500) return
        visitedCount[0]++

        if (node.isVisibleToUser) {
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
                    v.contains("pivot", ignoreCase = true)
            )
            if (t.isNotEmpty() || d.isNotEmpty() || hasRelevantViewId) {
                val r = android.graphics.Rect()
                node.getBoundsInScreen(r)
                val screenHeight = resources.displayMetrics.heightPixels.coerceAtLeast(800)
                val screenWidth = resources.displayMetrics.widthPixels.coerceAtLeast(400)
                if (r.width() > 0 && r.height() > 0 && r.bottom > 0 && r.top < screenHeight && r.right > 0 && r.left < screenWidth) {
                    out.add(UiNodeEntry(t, d, v, r))
                }
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectScreenNodes(child, out, depth + 1, visitedCount)
            child.recycle()
        }
    }

    /**
     * Strips trailing YouTube view count, upload time ("125K views 3 days ago ...more"), and action suffixes
     * from a combined Litho watch header node while discarding standalone view-count/subscriber strings.
     */
    private fun extractCleanTitleCandidate(raw: String): String {
        val singleLine = raw.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
        if (singleLine.length < 4) return ""

        // Discard player seekbar / duration strings ("0 minutes 15 seconds of 4 minutes 30 seconds")
        if (Regex("^\\d+\\s*(?:hours?|minutes?|seconds?|घंटे|मिनट|सेकंड)\\b.*\\b(?:of|में से)\\b.*$", RegexOption.IGNORE_CASE).matches(singleLine)) {
            return ""
        }
        // Discard strings that start directly with a numeric view/subscriber/like count (including lakh/crore)
        if (Regex("^\\d[0-9.,]*\\s*(?:k|m|b|lakh|lakhs|crore|crores|हज़ार|लाख|करोड़)?\\s*(?:views|view|watching|subscribers|likes|comments|बार|सदस्य)\\b.*$", RegexOption.IGNORE_CASE).matches(singleLine)) {
            return ""
        }
        if (singleLine.equals("No views", ignoreCase = true)) return ""
        // Discard strings that are only relative time ("3 days ago", "Streamed 2 hours ago")
        if (Regex("^(?:streamed\\s+|premiered\\s+)?\\d+\\s+(?:second|minute|hour|day|week|month|year)s?\\s+ago\\b.*$", RegexOption.IGNORE_CASE).matches(singleLine)) {
            return ""
        }

        var cleaned = singleLine
            .replace(Regex("(?:\\.\\.\\.more|…more|\\bshow more\\b)\\s*$", RegexOption.IGNORE_CASE), "")
            .trim()

        // Strip trailing "<number> views / watching ..." metadata appended to the title in YouTube's Litho header
        cleaned = cleaned.replace(
            Regex("(?:[,\\-•·|]|\\s)+\\d[0-9.,]*\\s*(?:k|m|b|lakh|lakhs|crore|crores|हज़ार|लाख|करोड़)?\\s*(?:views|view|watching|subscribers|बार देखा गया|लोग देख रहे हैं)\\b.*$", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        // Strip trailing "<number> minutes/seconds" duration or "<number> days ago"
        cleaned = cleaned.replace(
            Regex("(?:[,\\-•·|]|\\s)+\\d+\\s+(?:second|minute|hour|day|week|month|year)s?\\s+ago\\b.*$", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        return cleaned
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

            // 1. Check if a video Ad is currently playing INSIDE the top video player (check text/desc only!)
            val isAdPlayingInPlayer = entries.any { e ->
                val inTopPlayer = e.rect.bottom in 1..(screenHeight * 0.36f).toInt()
                val textAndDesc = "${e.text} ${e.desc}".lowercase()
                inTopPlayer && (
                        textAndDesc.contains("skip ad") ||
                        textAndDesc.contains("skip ads") ||
                        textAndDesc.contains("विज्ञापन छोड़ें") ||
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

            // 3. Check if user minimized the video into YouTube's bottom Miniplayer bar or navigated to Search / Home / Subscriptions
            val hasMiniplayerBarAtBottom = entries.any { e ->
                val v = e.viewId.lowercase()
                val d = e.desc.lowercase()
                val inBottomZone = e.rect.top >= (screenHeight * 0.65f).toInt()
                inBottomZone && (
                        v.contains("miniplayer") ||
                        v.contains("floaty_bar") ||
                        d.equals("expand miniplayer", ignoreCase = true) ||
                        d.equals("close miniplayer", ignoreCase = true) ||
                        (e.rect.top >= (screenHeight * 0.72f).toInt() && (
                                d == "play video" ||
                                d == "pause video" ||
                                v.contains("play_pause_replay_button")
                        ))
                )
            }

            val isSearchBarAtTop = entries.any { e ->
                e.rect.top < (screenHeight * 0.18f).toInt() && (
                        e.viewId.contains("search_edit_text", ignoreCase = true) ||
                        e.viewId.contains("search_box", ignoreCase = true) ||
                        e.viewId.contains("search_query", ignoreCase = true)
                )
            }

            val hasBottomNavTabsVisible = entries.count { e ->
                val label = e.desc.ifBlank { e.text }.lowercase()
                e.rect.top >= (screenHeight * 0.85f).toInt() &&
                        (label == "home" || label == "shorts" || label == "subscriptions" || label == "you" || label == "library")
            } >= 2

            if (hasMiniplayerBarAtBottom || isSearchBarAtTop || hasBottomNavTabsVisible) {
                wrongVideoStrikeCount++
                if (wrongVideoStrikeCount >= 2) {
                    wrongVideoStrikeCount = 0
                    WatchSessionRepository.triggerTaskIncomplete(
                        "Task Incomplete! Aapne YouTube mein target video se hat kar video minimize kar diya ya doosra page/search open kar liya."
                    )
                }
                return
            }

            // 3b. Check explicit player title if visible
            val explicitPlayerTitleNode = entries.firstOrNull { e ->
                val v = e.viewId.lowercase()
                (v.contains("player_video_title") || v.contains("miniplayer_title") || v.contains("floaty_bar_title")) &&
                        (e.text.length >= 4 || e.desc.length >= 4)
            }
            if (explicitPlayerTitleNode != null) {
                val rawPTitle = explicitPlayerTitleNode.text.ifBlank { explicitPlayerTitleNode.desc }
                val pTitle = extractCleanTitleCandidate(rawPTitle).ifBlank { rawPTitle }
                val match = TitleMatcher.evaluateMatch(pTitle, targetTitle, null, targetAuthor)
                val lockedTitle = lockedWatchPageTitle
                val matchesLocked = lockedTitle == null ||
                        TitleMatcher.evaluateMatch(pTitle, lockedTitle, null, null) == com.example.data.MatchResult.MATCH

                if (match == com.example.data.MatchResult.MISMATCH || !matchesLocked) {
                    wrongVideoStrikeCount++
                    if (wrongVideoStrikeCount >= 2) {
                        wrongVideoStrikeCount = 0
                        WatchSessionRepository.triggerTaskIncomplete(
                            "Task Incomplete! Aapne YouTube mein target video (\"$targetTitle\") ke bajaye doosra video (\"$pTitle\") play kar diya."
                        )
                    }
                    return
                } else if (match == com.example.data.MatchResult.MATCH) {
                    if (lockedWatchPageTitle == null && pTitle.length >= 5) {
                        lockedWatchPageTitle = pTitle
                    }
                    wrongVideoStrikeCount = 0
                    return
                }
            }

            // 4. Check Watch Metadata Header (right below the 16:9 video player, above Like/Share/Comments)
            val likeOrShareAnchor = entries.filter { e ->
                val t = e.text.lowercase()
                val d = e.desc.lowercase()
                val v = e.viewId.lowercase()
                val inMiddleBand = e.rect.top in (screenHeight * 0.24f).toInt()..(screenHeight * 0.64f).toInt()
                inMiddleBand && (
                        d.startsWith("like this video") ||
                        d.startsWith("dislike this video") ||
                        v.contains("like_button") ||
                        v.contains("share_button") ||
                        t == "share" ||
                        d == "share" ||
                        t == "remix" ||
                        t == "download"
                )
            }.minByOrNull { it.rect.top }

            val subscribeAnchor = entries.filter { e ->
                val t = e.text.lowercase()
                val d = e.desc.lowercase()
                val v = e.viewId.lowercase()
                val inMiddleBand = e.rect.top in (screenHeight * 0.22f).toInt()..(screenHeight * 0.60f).toInt()
                inMiddleBand && (
                        t == "subscribe" ||
                        t == "subscribed" ||
                        t.contains("सदस्यता") ||
                        d.startsWith("subscribe") ||
                        d.contains("subscribe to") ||
                        d.contains("सदस्यता") ||
                        v.contains("subscribe_button")
                )
            }.minByOrNull { it.rect.top }

            val commentsAnchor = entries.filter { e ->
                val t = e.text.lowercase()
                val d = e.desc.lowercase()
                val inMiddleBand = e.rect.top in (screenHeight * 0.28f).toInt()..(screenHeight * 0.70f).toInt()
                inMiddleBand && (t.startsWith("comments") || d.startsWith("comments"))
            }.minByOrNull { it.rect.top }

            val headerTopY = (screenHeight * 0.22f).toInt()
            var headerBottomY = when {
                likeOrShareAnchor != null -> (likeOrShareAnchor.rect.top + (12 * density).toInt()).coerceAtMost((screenHeight * 0.52f).toInt())
                subscribeAnchor != null -> (subscribeAnchor.rect.bottom + (24 * density).toInt()).coerceAtMost((screenHeight * 0.50f).toInt())
                commentsAnchor != null -> (commentsAnchor.rect.top - (4 * density).toInt()).coerceAtMost((screenHeight * 0.50f).toInt())
                else -> (screenHeight * 0.43f).toInt()
            }

            if (headerBottomY > headerTopY) {
                val chromeLabels = setOf(
                    "subscribe", "subscribed", "join", "share", "remix", "download",
                    "clip", "save", "report", "comments", "more", "...more", "show more", "show less",
                    "play video", "pause video", "replay video", "autoplay is on", "autoplay is off",
                    "mute", "unmute", "full screen", "enter full screen", "exit full screen",
                    "collapse", "minimize", "close", "sponsored", "visit site", "live chat",
                    "next video", "previous video", "settings", "captions", "video player",
                    "hide controls", "show controls", "more options", "expand description",
                    "collapse description", "description", "seek slider"
                )

                val sortedHeaderEntries = entries
                    .filter { e ->
                        val vLow = e.viewId.lowercase()
                        val isPlayerControlView = vLow.contains("time_bar") ||
                                vLow.contains("scrubber") ||
                                vLow.contains("player_control") ||
                                vLow.contains("player_overlay") ||
                                vLow.contains("inline_player")
                        !isPlayerControlView &&
                                e.rect.top in headerTopY..headerBottomY &&
                                e.rect.bottom > (screenHeight * 0.28f).toInt() &&
                                e.rect.height() <= (screenHeight * 0.36f).toInt()
                    }
                    .sortedBy { it.rect.top }

                val rawHeaderTexts = mutableListOf<String>()
                val cleanedTitleCandidates = mutableListOf<String>()
                val normAuthor = TitleMatcher.normalize(targetAuthor)

                for (e in sortedHeaderEntries) {
                    for (candidate in listOf(e.text, e.desc)) {
                        val rawClean = candidate.trim()
                        if (rawClean.length >= 3 && !chromeLabels.contains(rawClean.lowercase())) {
                            rawHeaderTexts.add(rawClean)
                            val extracted = extractCleanTitleCandidate(rawClean)
                            val low = extracted.lowercase()
                            val normTxt = TitleMatcher.normalize(extracted)
                            val isJustChannel = normAuthor.isNotEmpty() &&
                                    (normTxt == normAuthor || normTxt.replace(" ", "") == normAuthor.replace(" ", ""))

                            if (extracted.length >= 5 &&
                                !isJustChannel &&
                                !chromeLabels.contains(low) &&
                                !low.matches(Regex("^[0-9:\\s/•·.,%-]+$")) &&
                                !low.startsWith("like this video") &&
                                !low.startsWith("dislike this video") &&
                                !low.startsWith("subscribe to") &&
                                !low.startsWith("unsubscribe from") &&
                                !low.startsWith("options for") &&
                                !low.startsWith("save to") &&
                                !low.startsWith("share") &&
                                !low.startsWith("comments") &&
                                !low.startsWith("go to channel") &&
                                !low.startsWith("expand") &&
                                !low.startsWith("collapse") &&
                                !low.startsWith("#")
                            ) {
                                if (!cleanedTitleCandidates.contains(extracted)) {
                                    cleanedTitleCandidates.add(extracted)
                                }
                            }
                        }
                    }
                }

                if (cleanedTitleCandidates.isNotEmpty()) {
                    val topWatchTitle = cleanedTitleCandidates.first()
                    val primaryCandidates = cleanedTitleCandidates.take(2)
                    val isGenericTarget = targetTitle.equals("YouTube Video Task", ignoreCase = true) ||
                            targetTitle.equals("YouTube Video", ignoreCase = true) ||
                            targetTitle.startsWith("YouTube Video (", ignoreCase = true)

                    val anyTitleMatch = isGenericTarget || primaryCandidates.any { cand ->
                        TitleMatcher.evaluateMatch(cand, targetTitle, null, targetAuthor) == com.example.data.MatchResult.MATCH
                    }

                    val lockedTitle = lockedWatchPageTitle
                    val matchesLockedTitle = lockedTitle == null ||
                            TitleMatcher.evaluateMatch(topWatchTitle, lockedTitle, null, null) == com.example.data.MatchResult.MATCH ||
                            primaryCandidates.any { cand ->
                                TitleMatcher.evaluateMatch(cand, lockedTitle, null, null) == com.example.data.MatchResult.MATCH
                            }

                    // Also verify channel if targetAuthor is specified and Subscribe row is visible
                    var channelMatch = true
                    val isGenericAuthor = normAuthor.isEmpty() ||
                            normAuthor == "youtube creator" ||
                            normAuthor == "youtube channel" ||
                            normAuthor == "youtube"
                    if (!isGenericAuthor && subscribeAnchor != null) {
                        val combinedHeader = rawHeaderTexts.joinToString(" ")
                        val normHeader = TitleMatcher.normalize(combinedHeader)
                        val compactHeader = normHeader.replace(" ", "")
                        val compactAuthor = normAuthor.replace(" ", "")
                        val authorWords = normAuthor.split(" ").filter { it.length >= 3 }
                        channelMatch = normHeader.contains(normAuthor) ||
                                compactHeader.contains(compactAuthor) ||
                                (authorWords.isNotEmpty() && authorWords.all { normHeader.contains(it) })
                    }

                    if (anyTitleMatch && channelMatch && matchesLockedTitle) {
                        if (lockedWatchPageTitle == null) {
                            lockedWatchPageTitle = topWatchTitle
                        }
                        wrongVideoStrikeCount = 0
                    } else {
                        wrongVideoStrikeCount++
                        if (wrongVideoStrikeCount >= 2) {
                            val detectedWrong = topWatchTitle.ifBlank { "Doosra video" }
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
