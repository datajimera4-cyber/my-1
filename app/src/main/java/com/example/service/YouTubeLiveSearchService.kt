package com.example.service

import android.accessibilityservice.AccessibilityService
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
        TYPE_AND_SUBMIT,
        FIND_AND_CLICK_VIDEO,
        COMPLETED
    }

    companion object {
        @Volatile
        var isServiceConnected: Boolean = false
            private set

        @Volatile
        var targetSearchTitle: String? = null

        @Volatile
        var targetSearchChannel: String? = null

        @Volatile
        var hasClickedTarget: Boolean = false

        @Volatile
        var currentPhase: LiveSearchPhase = LiveSearchPhase.IDLE

        fun armSearchTrigger(title: String, channel: String?) {
            targetSearchTitle = title
            targetSearchChannel = channel
            hasClickedTarget = false
            currentPhase = LiveSearchPhase.OPEN_SEARCH_BAR
            WatchSessionRepository.addLog("Live Human Search armed for: \"$title\"", LogType.INFO)
        }

        fun disarm() {
            targetSearchTitle = null
            targetSearchChannel = null
            hasClickedTarget = false
            currentPhase = LiveSearchPhase.IDLE
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceConnected = true
        WatchSessionRepository.addLog("YouTube Human Live Search Accessibility service connected", LogType.INFO)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || hasClickedTarget || currentPhase == LiveSearchPhase.IDLE || currentPhase == LiveSearchPhase.COMPLETED) {
            return
        }

        val titleToFind = targetSearchTitle ?: return
        val rootNode = rootInActiveWindow ?: return

        try {
            when (currentPhase) {
                LiveSearchPhase.OPEN_SEARCH_BAR -> {
                    // Check if search edit text is already visible on screen
                    val existingEditText = findSearchEditText(rootNode)
                    if (existingEditText != null) {
                        currentPhase = LiveSearchPhase.TYPE_AND_SUBMIT
                        existingEditText.recycle()
                        handleTypingAndSubmit(rootNode, titleToFind)
                    } else {
                        // Look for the YouTube search button in top bar
                        val searchBtn = findSearchButton(rootNode)
                        if (searchBtn != null) {
                            val clicked = searchBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            searchBtn.recycle()
                            if (clicked) {
                                currentPhase = LiveSearchPhase.TYPE_AND_SUBMIT
                                WatchSessionRepository.addLog(
                                    "Human search: Tapped YouTube search button",
                                    LogType.INFO
                                )
                            }
                        }
                    }
                }

                LiveSearchPhase.TYPE_AND_SUBMIT -> {
                    handleTypingAndSubmit(rootNode, titleToFind)
                }

                LiveSearchPhase.FIND_AND_CLICK_VIDEO -> {
                    findAndClickVideoNode(rootNode, titleToFind, targetSearchChannel)
                }

                else -> {}
            }
        } catch (_: Exception) {
            // Ignore traversal errors
        } finally {
            rootNode.recycle()
        }
    }

    private fun handleTypingAndSubmit(rootNode: AccessibilityNodeInfo, titleToFind: String) {
        val searchEditText = findSearchEditText(rootNode) ?: return
        try {
            // Step A: Clear any old text in search bar
            val clearArgs = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            }
            searchEditText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)

            // Step B: Type new target task title like a human
            val typeArgs = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, titleToFind)
            }
            val typed = searchEditText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, typeArgs)

            if (typed) {
                WatchSessionRepository.addLog(
                    "Human search: Cleared search bar and typed task title: \"$titleToFind\"",
                    LogType.INFO
                )

                // Step C: Submit query via IME Action or search suggestion
                val submitted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    searchEditText.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                } else {
                    false
                }
                currentPhase = LiveSearchPhase.FIND_AND_CLICK_VIDEO

                if (submitted) {
                    WatchSessionRepository.addLog("Human search: Pressed Enter/Search in YouTube", LogType.INFO)
                } else {
                    // Try finding first search suggestion row to click
                    val suggestion = findFirstSearchSuggestion(rootNode, titleToFind)
                    if (suggestion != null) {
                        suggestion.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        suggestion.recycle()
                    }
                }
            }
        } finally {
            searchEditText.recycle()
        }
    }

    private fun findSearchButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString() ?: ""
        val text = node.text?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""

        val isSearchBtn = desc.contains("Search", ignoreCase = true) ||
                text.contains("Search", ignoreCase = true) ||
                viewId.contains("search", ignoreCase = true) ||
                viewId.contains("menu_item_view", ignoreCase = true)

        if (isSearchBtn) {
            var target: AccessibilityNodeInfo? = node
            while (target != null && !target.isClickable) {
                target = target.parent
            }
            if (target != null && target.isClickable) {
                return target
            }
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

        if (className.contains("EditText", ignoreCase = true) ||
            viewId.contains("search_edit_text", ignoreCase = true) ||
            viewId.contains("search_input", ignoreCase = true)
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

        if (viewId.contains("suggestion", ignoreCase = true) ||
            (text.isNotBlank() && TitleMatcher.normalize(text).contains(TitleMatcher.normalize(query).take(10)))
        ) {
            var target: AccessibilityNodeInfo? = node
            while (target != null && !target.isClickable) {
                target = target.parent
            }
            if (target != null && target.isClickable) {
                return target
            }
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

            // Distinctive keywords from target title (length >= 3, skipping generic words)
            val stopWords = setOf("the", "and", "official", "video", "audio", "with", "from", "feat", "music", "song", "lyrics", "full", "hd")
            val targetWords = normTarget.split(" ").map { it.trim() }.filter { it.length >= 3 && !stopWords.contains(it) }

            val directSubstringMatch = normTarget.length >= 4 && normText.contains(normTarget)
            val matchingWordCount = targetWords.count { word -> normText.contains(word) }
            val keywordMatch = targetWords.isNotEmpty() && matchingWordCount >= 2 && (matchingWordCount.toFloat() / targetWords.size) >= 0.5f

            if (directSubstringMatch || keywordMatch) {
                var channelMatches = true
                if (!targetChannel.isNullOrBlank() && targetChannel.length >= 3) {
                    val normChannel = TitleMatcher.normalize(targetChannel)
                    val compactChannel = normChannel.replace(" ", "")
                    val compactText = normText.replace(" ", "")
                    channelMatches = compactText.contains(compactChannel) || normText.contains(normChannel) || matchingWordCount >= 3
                }

                if (channelMatches) {
                    // Find clickable parent container (the entire video card)
                    var clickTarget: AccessibilityNodeInfo? = node
                    while (clickTarget != null && !clickTarget.isClickable) {
                        clickTarget = clickTarget.parent
                    }

                    if (clickTarget != null && clickTarget.isClickable) {
                        val clicked = clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (clicked) {
                            hasClickedTarget = true
                            currentPhase = LiveSearchPhase.COMPLETED
                            WatchSessionRepository.addLog(
                                "🎉 Human search: Found and clicked target video card! Video is opening to play full-screen.",
                                LogType.SUCCESS
                            )
                            return true
                        }
                    }
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

    override fun onInterrupt() {
        WatchSessionRepository.addLog("YouTube Live Search service interrupted", LogType.WARNING)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceConnected = false
        currentPhase = LiveSearchPhase.IDLE
    }
}
