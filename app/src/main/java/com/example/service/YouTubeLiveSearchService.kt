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
        var isServiceConnected: Boolean = false
            private set

        @Volatile
        var isYouTubeInForeground: Boolean = false

        @Volatile
        var isVideoExplicitlyPaused: Boolean = false

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

        private var scrollAttempts = 0

        fun armSearchTrigger(title: String, channel: String?, videoUrl: String? = null, videoId: String? = null) {
            targetSearchTitle = title
            targetSearchChannel = channel
            targetVideoUrl = videoUrl
            targetVideoId = videoId
            hasClickedTarget = false
            scrollAttempts = 0
            lastClickTime = 0L
            isVideoExplicitlyPaused = false
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
            currentPhase = LiveSearchPhase.IDLE
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceConnected = true
        WatchSessionRepository.addLog("YouTube Human Live Search Accessibility Service Connected", LogType.INFO)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: ""
        val myPkg = packageName ?: "com.example"

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if (pkg == "com.google.android.youtube") {
                isYouTubeInForeground = true
            } else if (pkg.isNotBlank() &&
                pkg != myPkg &&
                !pkg.contains("systemui") &&
                !pkg.contains("accessibility") &&
                !pkg.contains("inputmethod")
            ) {
                isYouTubeInForeground = false
                WatchSessionRepository.setPlaybackPlaying(false)
            }
        } else if (pkg == "com.google.android.youtube") {
            isYouTubeInForeground = true
        }

        // Detect user interactions on YouTube like and comment buttons
        if (isYouTubeInForeground && event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            try {
                val node = event.source
                if (node != null) {
                    val desc = node.contentDescription?.toString() ?: ""
                    val text = node.text?.toString() ?: ""
                    val combined = "$desc $text".lowercase()
                    if ((combined.contains("like this video") || combined.contains("like")) && !combined.contains("dislike")) {
                        WatchSessionRepository.onTaskLikeDetected?.invoke()
                    } else if (combined.contains("comment") || combined.contains("add a comment") || combined.contains("send comment") || combined.contains("post")) {
                        WatchSessionRepository.onTaskCommentDetected?.invoke()
                    }
                    node.recycle()
                }
            } catch (_: Exception) {}
        }

        // If target was already clicked or idle, monitor playback controls in YouTube
        if (hasClickedTarget || currentPhase == LiveSearchPhase.IDLE || currentPhase == LiveSearchPhase.COMPLETED) {
            if (isYouTubeInForeground && event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                checkPlaybackControls(event.source ?: rootInActiveWindow)
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
            val desc = node.contentDescription?.toString() ?: ""
            if (desc.contains("Play video", ignoreCase = true) || desc.equals("Play", ignoreCase = true)) {
                // Video is currently paused in YouTube
                isVideoExplicitlyPaused = true
                WatchSessionRepository.setPlaybackPlaying(false)
                return
            } else if (desc.contains("Pause video", ignoreCase = true) || desc.equals("Pause", ignoreCase = true)) {
                // Video is actively playing in YouTube
                isVideoExplicitlyPaused = false
                WatchSessionRepository.setPlaybackPlaying(true)
                return
            } else if (desc.contains("Replay video", ignoreCase = true) || desc.equals("Replay", ignoreCase = true)) {
                // Video ended
                isVideoExplicitlyPaused = true
                WatchSessionRepository.setPlaybackPlaying(false)
                return
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                checkPlaybackControls(child)
                child.recycle()
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
        isServiceConnected = false
        currentPhase = LiveSearchPhase.IDLE
    }
}
