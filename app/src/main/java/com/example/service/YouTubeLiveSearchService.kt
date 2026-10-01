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
        var lastExplicitPlayClickTime: Long = 0L

        @Volatile
        var isPlayButtonCurrentlyVisible: Boolean = false

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
        private var lastTypedCommentText: String = ""

        @Volatile
        private var lastTypedCommentTime: Long = 0L

        @Volatile
        private var wasCommentComposerOpen: Boolean = false

        @Volatile
        private var wasCommentEditTextActive: Boolean = false

        @Volatile
        private var lastCommentComposerOpenTime: Long = 0L

        @Volatile
        private var lastCommentCancelClickTime: Long = 0L

        @Volatile
        private var lastCommentRewardTriggerTime: Long = 0L

        @Volatile
        private var wasTargetVideoLikedInSession: Boolean = false

        private val rewardedLikedTaskIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

        private var scrollAttempts = 0

        @Volatile
        private var lastSearchStepTime: Long = 0L

        @Volatile
        private var lastTypedQueryTime: Long = 0L

        @Volatile
        private var submitQueryAttempts: Int = 0

        @Volatile
        private var openSearchBarAttempts: Int = 0

        fun resetMonitoringCounters() {
            wrongVideoStrikeCount = 0
            notInYouTubeStrikeCount = 0
            lastWatchHeaderCheckTime = 0L
            lastExplicitPauseTime = 0L
            lastExplicitPlayClickTime = 0L
            isPlayButtonCurrentlyVisible = false
            lockedWatchPageTitle = null
            hasTypedCommentText = false
            lastTypedCommentText = ""
            lastTypedCommentTime = 0L
            wasCommentComposerOpen = false
            wasCommentEditTextActive = false
            lastCommentComposerOpenTime = 0L
            lastCommentCancelClickTime = 0L
            lastCommentRewardTriggerTime = 0L
            wasTargetVideoLikedInSession = false
        }

        fun prepareForDirectWatch(title: String, channel: String?, videoUrl: String? = null, videoId: String? = null) {
            // Strictly route through organic YouTube Search / Browse Features — never direct URL watch
            armSearchTrigger(title, channel, null, null)
        }

        fun armSearchTrigger(title: String, channel: String?, videoUrl: String? = null, videoId: String? = null) {
            targetSearchTitle = title
            targetSearchChannel = channel
            targetVideoUrl = null
            targetVideoId = null
            hasClickedTarget = false
            scrollAttempts = 0
            lastClickTime = 0L
            lastSearchStepTime = 0L
            lastTypedQueryTime = 0L
            submitQueryAttempts = 0
            openSearchBarAttempts = 0
            isVideoExplicitlyPaused = false
            resetMonitoringCounters()
            currentPhase = LiveSearchPhase.OPEN_SEARCH_BAR
            WatchSessionRepository.addLog("Organic YouTube Search/Browse armed for: \"$title\"", LogType.INFO)
        }

        fun disarm() {
            targetSearchTitle = null
            targetSearchChannel = null
            targetVideoUrl = null
            targetVideoId = null
            hasClickedTarget = false
            scrollAttempts = 0
            lastClickTime = 0L
            lastSearchStepTime = 0L
            lastTypedQueryTime = 0L
            submitQueryAttempts = 0
            openSearchBarAttempts = 0
            isVideoExplicitlyPaused = false
            resetMonitoringCounters()
            currentPhase = LiveSearchPhase.IDLE
        }

        fun runPeriodicVerificationIfActive() {
            val svc = instance ?: return
            if (!isServiceConnected || !isYouTubeInForeground || !svc.isReadyForWatchVerification()) return
            try {
                val root = svc.getYouTubeRootNode() ?: svc.rootInActiveWindow ?: return
                svc.checkPlaybackControls(root)
                svc.verifyActiveYouTubeVideo(root)
            } catch (_: Exception) {}
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
                lower.contains("facemoji") ||
                lower.contains("bobble") ||
                lower.contains("mint") ||
                lower.contains("indic") ||
                lower.contains("kika") ||
                lower.contains("baidu") ||
                lower.contains("sogou") ||
                lower.contains("touchpal") ||
                lower.contains("fleksy") ||
                lower.contains("openboard") ||
                lower.contains("anysoft") ||
                lower.contains("latin") ||
                lower.contains("ime") ||
                lower.contains("autofill") ||
                lower.contains("credential") ||
                lower.contains("tts") ||
                lower.contains("accessibility") ||
                lower.contains("overlay") ||
                lower.contains("permission") ||
                lower.contains("packageinstaller")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: ""
        val myPkg = packageName ?: "com.example"
        if (pkg == myPkg) {
            return
        }

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
            if (isSessionActive && elapsedSinceLaunch > 25000L &&
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

        // Check for YouTube "Comment added" / "Reply added" confirmation in any event
        if (isSessionActive && elapsedSinceLaunch > 2500L && (pkg == "com.google.android.youtube" || isYouTubeInForeground)) {
            try {
                val evTxt = event.text?.joinToString(" ") { it.toString() }?.trim() ?: ""
                val evDsc = event.contentDescription?.toString()?.trim() ?: ""
                val evCombined = "$evTxt $evDsc".lowercase()
                if (isCommentAddedConfirmationText(evCombined)) {
                    triggerGenuineCommentReward("YouTube confirmation banner/announcement detected")
                }
            } catch (_: Exception) {}
        }

        // Track genuine comment typing inside YouTube comment box (excluding top search bar)
        if ((pkg == "com.google.android.youtube" || (isYouTubeInForeground && isTransientSystemPackage(pkg))) &&
            (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
             event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED ||
             event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED)
        ) {
            try {
                val src = event.source
                val vId = src?.viewIdResourceName?.lowercase() ?: ""
                val cls = src?.className?.toString()?.lowercase() ?: ""
                val isSearchField = vId.contains("search_edit_text") ||
                        vId.contains("search_src_text") ||
                        vId.contains("search_box") ||
                        (currentPhase != LiveSearchPhase.IDLE && currentPhase != LiveSearchPhase.COMPLETED && !hasClickedTarget)
                if (!isSearchField) {
                    val srcText = src?.text?.toString()?.trim().orEmpty()
                    val evTextStr = event.text?.joinToString(" ") { it.toString() }?.trim().orEmpty()
                    val typed = if (srcText.isNotEmpty() && !isCommentPlaceholder(srcText)) srcText else evTextStr
                    if (cls.contains("edittext") || src?.isEditable == true || vId.contains("comment")) {
                        wasCommentComposerOpen = true
                        lastCommentComposerOpenTime = System.currentTimeMillis()
                    }
                    if (typed.isNotEmpty() && !isCommentPlaceholder(typed)) {
                        hasTypedCommentText = true
                        lastTypedCommentText = typed
                        lastTypedCommentTime = System.currentTimeMillis()
                        wasCommentComposerOpen = true
                        wasCommentEditTextActive = true
                        lastCommentComposerOpenTime = System.currentTimeMillis()
                    }
                }
                src?.recycle()
            } catch (_: Exception) {}
        }

        // Detect user interactions INSIDE YouTube ONLY (ignore clicks on our own floating overlay!)
        if ((pkg == "com.google.android.youtube" || activeRootPkg == "com.google.android.youtube") &&
            isYouTubeInForeground &&
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
        ) {
            try {
                val screenHeight = resources.displayMetrics.heightPixels.coerceAtLeast(800)
                val screenWidth = resources.displayMetrics.widthPixels.coerceAtLeast(400)
                val density = resources.displayMetrics.density
                val node = event.source
                val clickRect = android.graphics.Rect()
                node?.getBoundsInScreen(clickRect)
                val evText = event.text?.joinToString(" ") { it.toString() }?.trim() ?: ""
                val evDesc = event.contentDescription?.toString()?.trim() ?: ""
                val desc = node?.contentDescription?.toString()?.ifBlank { evDesc } ?: evDesc
                val text = node?.text?.toString()?.ifBlank { evText } ?: evText
                val viewId = node?.viewIdResourceName ?: ""
                val subtreeSb = StringBuilder()
                if (node != null) {
                    collectSubtreeText(node, subtreeSb, 0)
                }
                val subtreeText = subtreeSb.toString().trim()
                val combined = "$desc $text $evText $evDesc $viewId $subtreeText".lowercase()

                // Track if user clicked to open the comment box / composer
                if (combined.contains("add a comment") ||
                    combined.contains("add a reply") ||
                    combined.contains("टिप्पणी जोड़ें") ||
                    combined.contains("जवाब जोड़ें") ||
                    viewId.contains("comment_composer", ignoreCase = true) ||
                    viewId.contains("comments_entry_point", ignoreCase = true)
                ) {
                    wasCommentComposerOpen = true
                    lastCommentComposerOpenTime = System.currentTimeMillis()
                }

                // Track if user clicked Cancel / Close / Discard on a comment draft
                if (desc.equals("Cancel", ignoreCase = true) ||
                    desc.equals("Discard", ignoreCase = true) ||
                    text.equals("Cancel", ignoreCase = true) ||
                    text.equals("Discard", ignoreCase = true) ||
                    desc.contains("रद्द करें") ||
                    text.contains("रद्द करें")
                ) {
                    lastCommentCancelClickTime = System.currentTimeMillis()
                    hasTypedCommentText = false
                    wasCommentEditTextActive = false
                }

                val statusBarHeight = getStatusBarHeight()
                val playerBottomY = statusBarHeight + ((screenWidth * 9) / 16)
                val topPlayerMaxBottom = (playerBottomY + (48 * density).toInt()).coerceAtMost((screenHeight * 0.42f).toInt())
                val inTopPlayerArea = (clickRect.top in 0..topPlayerMaxBottom && clickRect.bottom in 1..(topPlayerMaxBottom + (30 * density).toInt())) ||
                        viewId.contains("player_control", ignoreCase = true) ||
                        viewId.contains("play_pause_replay_button", ignoreCase = true) ||
                        viewId.contains("player_overlay", ignoreCase = true) ||
                        viewId.contains("player_view", ignoreCase = true) ||
                        viewId.contains("player_fragment", ignoreCase = true) ||
                        desc.equals("Video player", ignoreCase = true) ||
                        desc.equals("Hide controls", ignoreCase = true) ||
                        desc.equals("Show controls", ignoreCase = true)

                val isNextOrPrevOrCollapse = desc.equals("Next video", ignoreCase = true) ||
                        desc.equals("Previous video", ignoreCase = true) ||
                        evDesc.equals("Next video", ignoreCase = true) ||
                        evDesc.equals("Previous video", ignoreCase = true) ||
                        desc.contains("अगला वीडियो") ||
                        desc.contains("पिछला वीडियो") ||
                        desc.equals("Minimize", ignoreCase = true) ||
                        desc.equals("Collapse", ignoreCase = true) ||
                        viewId.contains("player_control_next", ignoreCase = true) ||
                        viewId.contains("player_control_previous", ignoreCase = true) ||
                        viewId.contains("player_collapse_button", ignoreCase = true) ||
                        viewId.contains("autonav", ignoreCase = true)

                val isPlayPauseBtnClick = !isNextOrPrevOrCollapse && (
                        viewId.contains("play_pause_replay_button", ignoreCase = true) ||
                        viewId.contains("player_control_play_pause", ignoreCase = true) ||
                        evDesc.equals("Pause video", ignoreCase = true) ||
                        evDesc.equals("Pause", ignoreCase = true) ||
                        evDesc.equals("Play video", ignoreCase = true) ||
                        evDesc.equals("Replay video", ignoreCase = true) ||
                        evDesc.equals("Play", ignoreCase = true) ||
                        evDesc.equals("Replay", ignoreCase = true) ||
                        evDesc.contains("वीडियो रोकें") ||
                        evDesc.contains("वीडियो चलाएं") ||
                        evDesc.contains("फिर से चलाएं") ||
                        desc.equals("Pause video", ignoreCase = true) ||
                        desc.equals("Pause", ignoreCase = true) ||
                        desc.equals("Play video", ignoreCase = true) ||
                        desc.equals("Replay video", ignoreCase = true) ||
                        desc.equals("Play", ignoreCase = true) ||
                        desc.equals("Replay", ignoreCase = true) ||
                        desc.contains("वीडियो रोकें") ||
                        desc.contains("वीडियो चलाएं") ||
                        desc.contains("फिर से चलाएं")
                )

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
                // Note: In Android Accessibility TYPE_VIEW_CLICKED, node.isSelected / node.isChecked is ALREADY toggled to the NEW state after click!
                // Therefore, checking node?.isSelected == true here previously inverted Like & Unlike!
                // Instead, check if the label explicitly says "unlike" / "remove like", or if the button was already liked in this session.
                val explicitUnlikeLabel = combined.contains("unlike") ||
                        combined.contains("remove like") ||
                        combined.contains("हटाएं") ||
                        evDesc.contains("unlike", ignoreCase = true) ||
                        evDesc.contains("remove like", ignoreCase = true)
                val postClickUnchecked = node != null && node.isCheckable && !node.isChecked

                val inWatchActionBarBand = clickRect.top in (screenHeight * 0.16f).toInt()..(screenHeight * 0.66f).toInt()

                // Genuine first-time Like click on the target YouTube video's Like button
                val isCommentLike = combined.contains("comment") ||
                        combined.contains("टिप्पणी") ||
                        combined.contains("reply") ||
                        combined.contains("जवाब")
                val isVideoLikeButtonTarget = isSessionActive &&
                        elapsedSinceLaunch > 2000L &&
                        inWatchActionBarBand &&
                        !isDislike &&
                        !isCommentLike &&
                        !looksLikeVideoCard &&
                        combined.length < 160 && (
                                desc.startsWith("like this video", ignoreCase = true) ||
                                evDesc.startsWith("like this video", ignoreCase = true) ||
                                combined.contains("like this video") ||
                                viewId.contains("like_button", ignoreCase = true) ||
                                viewId.contains("segmented_like", ignoreCase = true) ||
                                desc.equals("Like", ignoreCase = true) ||
                                evDesc.equals("Like", ignoreCase = true) ||
                                desc.contains("पसंद करें") ||
                                evDesc.contains("पसंद करें")
                        )

                val isGenuineVideoLikeClick = if (isVideoLikeButtonTarget) {
                    if (explicitUnlikeLabel || postClickUnchecked || wasTargetVideoLikedInSession) {
                        // User clicked the Like button while it was already liked -> this is an UNLIKE action!
                        wasTargetVideoLikedInSession = false
                        false
                    } else {
                        // User clicked the Like button to LIKE the video!
                        wasTargetVideoLikedInSession = true
                        true
                    }
                } else {
                    false
                }

                val now = System.currentTimeMillis()
                val hadRecentCommentActivity = hasTypedCommentText ||
                        wasCommentComposerOpen ||
                        (now - lastTypedCommentTime) < 120_000L ||
                        (now - lastCommentComposerOpenTime) < 120_000L

                val isExplicitCommentSendLabel =
                        desc.equals("Send", ignoreCase = true) ||
                        desc.equals("Send comment", ignoreCase = true) ||
                        desc.equals("Post", ignoreCase = true) ||
                        desc.equals("Post comment", ignoreCase = true) ||
                        desc.equals("Comment", ignoreCase = true) ||
                        desc.equals("Reply", ignoreCase = true) ||
                        evDesc.equals("Send", ignoreCase = true) ||
                        evDesc.equals("Send comment", ignoreCase = true) ||
                        evDesc.equals("Post", ignoreCase = true) ||
                        evDesc.equals("Post comment", ignoreCase = true) ||
                        text.equals("Send", ignoreCase = true) ||
                        text.equals("Post", ignoreCase = true) ||
                        text.equals("Comment", ignoreCase = true) ||
                        text.equals("Reply", ignoreCase = true) ||
                        desc.contains("टिप्पणी भेजें") ||
                        desc.contains("टिप्पणी करें") ||
                        desc.equals("भेजें", ignoreCase = true) ||
                        evDesc.contains("टिप्पणी भेजें") ||
                        evDesc.equals("भेजें", ignoreCase = true) ||
                        text.equals("भेजें", ignoreCase = true) ||
                        subtreeText.equals("Send", ignoreCase = true) ||
                        subtreeText.equals("Send comment", ignoreCase = true) ||
                        subtreeText.equals("Post", ignoreCase = true) ||
                        viewId.contains("send_button", ignoreCase = true) ||
                        viewId.contains("post_button", ignoreCase = true) ||
                        viewId.contains("comment_send", ignoreCase = true) ||
                        viewId.contains("composer_send", ignoreCase = true) ||
                        (viewId.contains("send", ignoreCase = true) && !viewId.contains("share", ignoreCase = true))

                val isRightSideSendIcon = hadRecentCommentActivity &&
                        clickRect.right >= (screenWidth * 0.72f).toInt() &&
                        clickRect.left >= (screenWidth * 0.58f).toInt() &&
                        clickRect.top >= (screenHeight * 0.25f).toInt() &&
                        clickRect.width() in 10..(120 * density).toInt() &&
                        clickRect.height() in 10..(120 * density).toInt() &&
                        !inTopPlayerArea &&
                        !isPlayPauseBtnClick &&
                        !isNextOrPrevOrCollapse &&
                        !isDislike &&
                        !desc.startsWith("like", ignoreCase = true) &&
                        !desc.equals("Close", ignoreCase = true) &&
                        !desc.equals("Close comments", ignoreCase = true) &&
                        !desc.equals("Cancel", ignoreCase = true) &&
                        !desc.contains("More", ignoreCase = true) &&
                        !desc.contains("Sort", ignoreCase = true)

                val isGenuineCommentSubmitted = isSessionActive &&
                        elapsedSinceLaunch > 2500L &&
                        !looksLikeVideoCard &&
                        (isExplicitCommentSendLabel || isRightSideSendIcon)

                if (isGenuineVideoLikeClick) {
                    val activeId = WatchSessionRepository.activeTaskId.value ?: "default_rick"
                    if (rewardedLikedTaskIds.add(activeId)) {
                        WatchSessionRepository.onTaskLikeDetected?.invoke()
                    }
                } else if (isGenuineCommentSubmitted) {
                    triggerGenuineCommentReward("Clicked YouTube Comment Send button")
                } else if (isPlayPauseBtnClick) {
                    // Toggle immediately for instant UI responsiveness, then verify actual post-click button state
                    updateVideoPausedState(!isVideoExplicitlyPaused)
                    val handler = android.os.Handler(android.os.Looper.getMainLooper())
                    handler.postDelayed({ checkPlaybackControls(getYouTubeRootNode()) }, 180L)
                    handler.postDelayed({ checkPlaybackControls(getYouTubeRootNode()) }, 450L)
                    handler.postDelayed({ checkPlaybackControls(getYouTubeRootNode()) }, 900L)
                } else if (inTopPlayerArea && !isNextOrPrevOrCollapse) {
                    // User tapped video surface to show/hide controls or tapped player settings/seekbar:
                    // Never treat this as a video switch! Just inspect playback controls shortly after.
                    val handler = android.os.Handler(android.os.Looper.getMainLooper())
                    handler.postDelayed({ checkPlaybackControls(getYouTubeRootNode()) }, 200L)
                    handler.postDelayed({ checkPlaybackControls(getYouTubeRootNode()) }, 500L)
                } else if (isSessionActive && isReadyForWatchVerification()) {
                    checkIfUserClickedDifferentVideo(node, desc, text, viewId, "$evText $evDesc".trim())
                }

                if (isSessionActive && isReadyForWatchVerification()) {
                    val handler = android.os.Handler(android.os.Looper.getMainLooper())
                    handler.postDelayed({ inspectCurrentYouTubeState() }, 350L)
                    handler.postDelayed({ inspectCurrentYouTubeState() }, 800L)
                    handler.postDelayed({ inspectCurrentYouTubeState() }, 1500L)
                    handler.postDelayed({ inspectCurrentYouTubeState() }, 2500L)
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
                        if (now - lastWatchHeaderCheckTime >= 400L) {
                            lastWatchHeaderCheckTime = now
                            verifyActiveYouTubeVideo(ytRoot)
                        }
                    }
                }
            }
            return
        }

        val titleToFind = targetSearchTitle ?: return
        val rootNode = getYouTubeRootNode() ?: rootInActiveWindow ?: event.source ?: return
        driveLiveSearchStep(rootNode, titleToFind)
    }

    private fun buildSearchQuery(title: String, channel: String?): String {
        val cleanTitle = title.trim()
        val cleanChannel = channel?.trim().orEmpty()
        return if (cleanChannel.isNotBlank() &&
            !cleanChannel.equals("YouTube Creator", ignoreCase = true) &&
            !cleanChannel.equals("YouTube Channel", ignoreCase = true) &&
            !cleanTitle.contains(cleanChannel, ignoreCase = true)
        ) {
            "$cleanTitle $cleanChannel"
        } else {
            cleanTitle
        }
    }

    private fun driveLiveSearchStep(rootNode: AccessibilityNodeInfo, titleToFind: String) {
        if (hasClickedTarget || currentPhase == LiveSearchPhase.IDLE || currentPhase == LiveSearchPhase.COMPLETED) {
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastSearchStepTime < 420L) {
            return
        }
        lastSearchStepTime = now

        try {
            when (currentPhase) {
                LiveSearchPhase.OPEN_SEARCH_BAR -> {
                    // 1. If search EditText is already open and visible, go straight to typing!
                    val existingEditText = findSearchEditText(rootNode)
                    if (existingEditText != null) {
                        currentPhase = LiveSearchPhase.TYPE_QUERY
                        existingEditText.recycle()
                        handleTyping(rootNode, titleToFind)
                        return
                    }

                    // 2. BROWSE FEATURES CHECK: If target video card is already visible on the YouTube Home feed,
                    // tap it directly on the Home feed so YouTube Studio counts it as "Browse features"!
                    if (openSearchBarAttempts == 0 && findAndClickVideoNode(rootNode, titleToFind, targetSearchChannel, requireFeedCardMetadata = true)) {
                        return
                    }

                    // 3. Look for the YouTube Search icon/bar in the top toolbar
                    val searchBtn = findSearchButton(rootNode)
                    if (searchBtn != null) {
                        val btnRect = android.graphics.Rect()
                        searchBtn.getBoundsInScreen(btnRect)
                        val clicked = searchBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
                                (searchBtn.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) ||
                                dispatchTapGesture(btnRect.centerX(), btnRect.centerY())
                        searchBtn.recycle()
                        openSearchBarAttempts++
                        if (clicked) {
                            currentPhase = LiveSearchPhase.TYPE_QUERY
                            WatchSessionRepository.addLog(
                                "YouTube Search: Tapped Search bar inside YouTube",
                                LogType.INFO
                            )
                        }
                    } else {
                        openSearchBarAttempts++
                        // If an old video player is open full-screen covering the top search bar, press Back once to collapse it
                        if (openSearchBarAttempts == 2 || openSearchBarAttempts == 4) {
                            performGlobalAction(GLOBAL_ACTION_BACK)
                        }
                    }
                }

                LiveSearchPhase.TYPE_QUERY -> {
                    val existingEditText = findSearchEditText(rootNode)
                    if (existingEditText == null) {
                        // Search EditText not open yet; tap Search button again
                        val searchBtn = findSearchButton(rootNode)
                        if (searchBtn != null) {
                            val btnRect = android.graphics.Rect()
                            searchBtn.getBoundsInScreen(btnRect)
                            searchBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            dispatchTapGesture(btnRect.centerX(), btnRect.centerY())
                            searchBtn.recycle()
                        }
                        return
                    }
                    existingEditText.recycle()
                    handleTyping(rootNode, titleToFind)
                }

                LiveSearchPhase.SUBMIT_QUERY -> {
                    handleSubmitQuery(rootNode, titleToFind)
                }

                LiveSearchPhase.FIND_AND_CLICK_VIDEO -> {
                    // Make sure the search EditText isn't still actively focused with autocomplete suggestions covering results
                    val activeEdit = findSearchEditText(rootNode)
                    if (activeEdit != null && activeEdit.isFocused && isSoftKeyboardVisible() && submitQueryAttempts < 3) {
                        activeEdit.recycle()
                        handleSubmitQuery(rootNode, titleToFind)
                        return
                    }
                    activeEdit?.recycle()

                    val found = findAndClickVideoNode(rootNode, titleToFind, targetSearchChannel, requireFeedCardMetadata = false)
                    if (!found && scrollAttempts < 8) {
                        scrollAttempts++
                        if (!scrollForward(rootNode)) {
                            dispatchSwipeUpGesture()
                        }
                    }
                }

                else -> {}
            }
        } catch (_: Exception) {
            // Traversal resilience
        }
    }

    private fun handleTyping(rootNode: AccessibilityNodeInfo, titleToFind: String) {
        val searchEditText = findSearchEditText(rootNode) ?: return
        try {
            val fullQuery = buildSearchQuery(titleToFind, targetSearchChannel)

            // Step A: Focus the edit text
            searchEditText.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            searchEditText.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            // Step B: Set text with target task title (+ channel name for #1 ranking)
            val typeArgs = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, fullQuery)
            }
            val typed = searchEditText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, typeArgs)

            if (typed) {
                lastTypedQueryTime = System.currentTimeMillis()
                WatchSessionRepository.addLog(
                    "YouTube Search: Typed \"$fullQuery\" into YouTube search bar",
                    LogType.INFO
                )

                // Try submitting immediately via ACTION_IME_ENTER
                val submitted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    searchEditText.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                } else {
                    false
                }

                if (submitted) {
                    WatchSessionRepository.addLog("YouTube Search: Submitted search query via IME Enter", LogType.INFO)
                }
                // Always verify in SUBMIT_QUERY on next tick that search results opened (or click suggestion if IME Enter was ignored)
                currentPhase = LiveSearchPhase.SUBMIT_QUERY
            }
        } finally {
            searchEditText.recycle()
        }
    }

    private fun handleSubmitQuery(rootNode: AccessibilityNodeInfo, query: String) {
        submitQueryAttempts++
        val activeEdit = findSearchEditText(rootNode)

        // If the search EditText is no longer focused (meaning IME Enter already submitted the search!),
        // move immediately to FIND_AND_CLICK_VIDEO!
        if (activeEdit == null || (!activeEdit.isFocused && !isSoftKeyboardVisible())) {
            activeEdit?.recycle()
            currentPhase = LiveSearchPhase.FIND_AND_CLICK_VIDEO
            return
        }

        // 1. Try ACTION_IME_ENTER on the focused EditText
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (activeEdit.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) {
                activeEdit.recycle()
                currentPhase = LiveSearchPhase.FIND_AND_CLICK_VIDEO
                return
            }
        }
        activeEdit.recycle()

        // 2. Click the first search suggestion row in YouTube's autocomplete list (left/center area, NOT the right Refine arrow)
        val suggestion = findFirstSearchSuggestion(rootNode, query)
        if (suggestion != null) {
            val sugRect = android.graphics.Rect()
            suggestion.getBoundsInScreen(sugRect)
            val screenWidth = resources.displayMetrics.widthPixels.coerceAtLeast(400)
            val safeTapX = (screenWidth * 0.42f).toInt()
            val safeTapY = sugRect.centerY()
            val clicked = suggestion.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
                    (suggestion.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) ||
                    dispatchTapGesture(safeTapX, safeTapY)
            suggestion.recycle()
            if (clicked) {
                WatchSessionRepository.addLog("YouTube Search: Tapped search suggestion for \"$query\"", LogType.INFO)
                currentPhase = LiveSearchPhase.FIND_AND_CLICK_VIDEO
                return
            }
        }

        // 3. Try finding a submit / search button in the search bar container
        val submitBtn = findSearchSubmitButton(rootNode)
        if (submitBtn != null) {
            val clicked = submitBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
                    (submitBtn.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
            submitBtn.recycle()
            if (clicked) {
                WatchSessionRepository.addLog("YouTube Search: Clicked search submit button", LogType.INFO)
                currentPhase = LiveSearchPhase.FIND_AND_CLICK_VIDEO
                return
            }
        }

        // 4. Fallback: tap the bottom-right keyboard Search/Enter key
        if (isSoftKeyboardVisible()) {
            val screenWidth = resources.displayMetrics.widthPixels.coerceAtLeast(400)
            val screenHeight = resources.displayMetrics.heightPixels.coerceAtLeast(800)
            dispatchTapGesture((screenWidth * 0.90f).toInt(), (screenHeight * 0.91f).toInt())
        }

        currentPhase = LiveSearchPhase.FIND_AND_CLICK_VIDEO
    }

    private fun findSearchButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val text = node.text?.toString()?.trim() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val cls = node.className?.toString() ?: ""

        val lowerDesc = desc.lowercase()
        val lowerText = text.lowercase()
        val lowerId = viewId.lowercase()

        // Never click Voice Search, Cast, Notifications, Filter, or More buttons
        val isExcluded = lowerDesc.contains("voice") ||
                lowerDesc.contains("आवाज़") ||
                lowerDesc.contains("cast") ||
                lowerDesc.contains("notification") ||
                lowerDesc.contains("filter") ||
                lowerDesc.contains("more") ||
                lowerDesc.contains("account") ||
                lowerId.contains("voice") ||
                lowerId.contains("fab")

        if (!isExcluded && node.isVisibleToUser && !cls.contains("EditText", ignoreCase = true)) {
            val screenHeight = resources.displayMetrics.heightPixels.coerceAtLeast(800)
            val r = android.graphics.Rect()
            node.getBoundsInScreen(r)

            if (r.top in 0..(screenHeight * 0.18f).toInt() && r.width() > 10 && r.height() > 10) {
                val isSearchBtn = desc.equals("Search", ignoreCase = true) ||
                        desc.equals("Search YouTube", ignoreCase = true) ||
                        desc.contains("खोजें") ||
                        desc.contains("Khojein", ignoreCase = true) ||
                        text.equals("Search YouTube", ignoreCase = true) ||
                        lowerId.contains("menu_item_search") ||
                        lowerId.contains("search_button") ||
                        lowerId.contains("action_search") ||
                        lowerId.contains("search_box") ||
                        lowerId.contains("search_query")

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
        val desc = node.contentDescription?.toString() ?: ""
        val text = node.text?.toString() ?: ""

        if (node.isVisibleToUser) {
            val screenHeight = resources.displayMetrics.heightPixels.coerceAtLeast(800)
            val r = android.graphics.Rect()
            node.getBoundsInScreen(r)
            if (r.top in 0..(screenHeight * 0.25f).toInt()) {
                if (node.isEditable ||
                    className.contains("EditText", ignoreCase = true) ||
                    viewId.contains("search_edit_text", ignoreCase = true) ||
                    viewId.contains("search_input", ignoreCase = true) ||
                    viewId.contains("search_src_text", ignoreCase = true) ||
                    (desc.contains("Search YouTube", ignoreCase = true) && node.isFocusable) ||
                    (text.contains("Search YouTube", ignoreCase = true) && node.isFocusable)
                ) {
                    return node
                }
            }
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
        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val cls = node.className?.toString() ?: ""

        // Skip the top EditText itself and skip the right-hand "Refine" diagonal arrow button
        val isEditOrRefine = cls.contains("EditText", ignoreCase = true) ||
                node.isEditable ||
                viewId.contains("search_edit_text", ignoreCase = true) ||
                viewId.contains("edit_query", ignoreCase = true) ||
                desc.contains("Refine", ignoreCase = true) ||
                desc.contains("Clear", ignoreCase = true) ||
                desc.contains("voice", ignoreCase = true)

        if (!isEditOrRefine && node.isVisibleToUser) {
            val screenHeight = resources.displayMetrics.heightPixels.coerceAtLeast(800)
            val screenWidth = resources.displayMetrics.widthPixels.coerceAtLeast(400)
            val r = android.graphics.Rect()
            node.getBoundsInScreen(r)

            if (r.top in (screenHeight * 0.09f).toInt()..(screenHeight * 0.48f).toInt() &&
                r.left < (screenWidth * 0.75f).toInt() &&
                r.height() in 20..(screenHeight * 0.12f).toInt()
            ) {
                val normText = TitleMatcher.normalize(text.ifBlank { desc })
                val normQuery = TitleMatcher.normalize(query)

                val isSuggestion = viewId.contains("suggestion", ignoreCase = true) ||
                        viewId.contains("search_typeahead", ignoreCase = true) ||
                        (normText.length >= 3 && normQuery.length >= 3 &&
                                (normText.contains(normQuery.take(6)) || normQuery.contains(normText.take(6))))

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

    private fun findSearchSubmitButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val lowerDesc = desc.lowercase()

        if (!lowerDesc.contains("voice") && !lowerDesc.contains("आवाज़") &&
            (desc.equals("Search", ignoreCase = true) ||
             viewId.contains("search_button", ignoreCase = true) ||
             viewId.contains("btn_search", ignoreCase = true))
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
        targetChannel: String?,
        requireFeedCardMetadata: Boolean = false
    ): Boolean {
        if (hasClickedTarget) return true

        val cls = node.className?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        if (node.isEditable ||
            cls.contains("EditText", ignoreCase = true) ||
            viewId.contains("search_edit_text", ignoreCase = true) ||
            viewId.contains("search_box", ignoreCase = true) ||
            viewId.contains("search_query", ignoreCase = true) ||
            viewId.contains("suggestion", ignoreCase = true) ||
            viewId.contains("typeahead", ignoreCase = true)
        ) {
            return false
        }

        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val combinedText = "$text $desc".trim()

        if (combinedText.isNotBlank() && node.isVisibleToUser) {
            val screenHeight = resources.displayMetrics.heightPixels.coerceAtLeast(800)
            val screenWidth = resources.displayMetrics.widthPixels.coerceAtLeast(400)
            val density = resources.displayMetrics.density
            val nodeRect = android.graphics.Rect()
            node.getBoundsInScreen(nodeRect)

            // Skip nodes in the top search bar / filter chips header (top 14% of screen)
            // and skip 3-dot action menu buttons on the far right
            val isMenuOrChannelAvatar = desc.equals("Action menu", ignoreCase = true) ||
                    desc.equals("More", ignoreCase = true) ||
                    desc.equals("More options", ignoreCase = true) ||
                    desc.startsWith("Go to channel", ignoreCase = true) ||
                    nodeRect.left > (screenWidth * 0.82f).toInt()

            if (!isMenuOrChannelAvatar && nodeRect.top >= (screenHeight * 0.14f).toInt() && nodeRect.height() > 10) {
                val normText = TitleMatcher.normalize(combinedText)
                val normTarget = TitleMatcher.normalize(targetTitle)

                // Distinctive keywords from target title (length >= 3, skipping generic stopwords)
                val stopWords = setOf("the", "and", "official", "video", "audio", "with", "from", "feat", "music", "song", "lyrics", "full", "hd")
                val targetWords = normTarget.split(" ").map { it.trim() }.filter { it.length >= 3 && !stopWords.contains(it) }

                val directSubstringMatch = (normTarget.length >= 4 && normText.contains(normTarget)) ||
                        (normTarget.length >= 8 && normText.contains(normTarget.take(12)))
                val matchingWordCount = targetWords.count { word -> normText.contains(word) }
                val keywordMatch = targetWords.isNotEmpty() && matchingWordCount >= 1 &&
                        (matchingWordCount.toFloat() / targetWords.size.coerceAtLeast(1)) >= 0.35f

                if (directSubstringMatch || keywordMatch) {
                    // Climb up to nearest clickable video card container
                    var clickTarget: AccessibilityNodeInfo? = node
                    var climbDepth = 0
                    while (clickTarget != null && !clickTarget.isClickable && climbDepth < 5) {
                        clickTarget = clickTarget.parent
                        climbDepth++
                    }

                    val toClick = if (clickTarget != null && clickTarget.isClickable) clickTarget else node
                    val cardRect = android.graphics.Rect()
                    toClick.getBoundsInScreen(cardRect)

                    val subtreeSb = StringBuilder(combinedText).append(" ")
                    collectSubtreeText(toClick, subtreeSb, 0)
                    val fullCardText = subtreeSb.toString()
                    val lowerCard = fullCardText.lowercase()
                    val normCard = TitleMatcher.normalize(fullCardText)

                    val hasVideoCardIndicators = lowerCard.contains("views") ||
                            lowerCard.contains("watching") ||
                            lowerCard.contains("minutes") ||
                            lowerCard.contains("seconds") ||
                            lowerCard.contains("ago") ||
                            lowerCard.contains("बार देखा गया") ||
                            lowerCard.contains("मिनट") ||
                            lowerCard.contains("सेकंड") ||
                            Regex("\\b\\d{1,2}:\\d{2}\\b").containsMatchIn(lowerCard) ||
                            cardRect.height() >= (48 * density).toInt()

                    if (!requireFeedCardMetadata || hasVideoCardIndicators) {
                        var channelMatches = true
                        if (!targetChannel.isNullOrBlank() &&
                            targetChannel.length >= 3 &&
                            !targetChannel.equals("YouTube Creator", ignoreCase = true) &&
                            !targetChannel.equals("YouTube Channel", ignoreCase = true)
                        ) {
                            val normChannel = TitleMatcher.normalize(targetChannel)
                            val compactChannel = normChannel.replace(" ", "")
                            val compactCard = normCard.replace(" ", "")
                            channelMatches = compactCard.contains(compactChannel) ||
                                    normCard.contains(normChannel) ||
                                    matchingWordCount >= 2 ||
                                    directSubstringMatch
                        }

                        if (channelMatches && cardRect.height() >= (36 * density).toInt()) {
                            // Calculate safe coordinates on the video card (away from left channel icon and right 3-dot menu)
                            val tapX = (screenWidth * 0.46f).toInt()
                            val tapY = if (nodeRect.height() in 10..(140 * density).toInt()) {
                                nodeRect.centerY()
                            } else {
                                cardRect.centerY()
                            }

                            // Perform organic click directly on the YouTube video result card
                            val clickedByNode = toClick.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            if (!clickedByNode) {
                                dispatchTapGesture(tapX, tapY)
                            }

                            hasClickedTarget = true
                            lastClickTime = System.currentTimeMillis()
                            currentPhase = LiveSearchPhase.COMPLETED
                            WatchSessionRepository.addLog(
                                "🎉 Organic YouTube Search/Browse: Clicked video card inside YouTube!",
                                LogType.SUCCESS
                            )

                            // Verify 950ms later that the full watch player opened (if inline preview started on thumbnail, tap once to expand player)
                            // Strictly inside YouTube UI — NEVER opens any URL or external intent!
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                ensureWatchPlayerExpandedAfterClick(tapX, tapY)
                            }, 950L)

                            return true
                        }
                    }
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findAndClickVideoNode(child, targetTitle, targetChannel, requireFeedCardMetadata)
            child.recycle()
            if (found) return true
        }

        return false
    }

    private fun ensureWatchPlayerExpandedAfterClick(tapX: Int, tapY: Int) {
        if (!isYouTubeInForeground) return
        try {
            val root = getYouTubeRootNode() ?: rootInActiveWindow ?: return
            val entries = mutableListOf<UiNodeEntry>()
            collectScreenNodes(root, entries)
            val isWatchPlayerOpen = entries.any { e ->
                val v = e.viewId.lowercase()
                val d = e.desc.lowercase()
                val t = e.text.lowercase()
                v.contains("player_view") ||
                        v.contains("player_control") ||
                        v.contains("watch_metadata") ||
                        v.contains("subscribe_button") ||
                        v.contains("like_button") ||
                        d.startsWith("like this video") ||
                        d.equals("pause video", ignoreCase = true) ||
                        d.equals("play video", ignoreCase = true) ||
                        t == "subscribe" ||
                        t == "subscribed"
            }
            if (!isWatchPlayerOpen) {
                dispatchTapGesture(tapX, tapY)
            }
        } catch (_: Exception) {}
    }

    private fun getStatusBarHeight(): Int {
        return try {
            val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resId > 0) {
                resources.getDimensionPixelSize(resId)
            } else {
                (32 * resources.displayMetrics.density).toInt()
            }
        } catch (_: Exception) {
            (32 * resources.displayMetrics.density).toInt()
        }
    }

    private fun updateVideoPausedState(paused: Boolean) {
        if (paused) {
            isVideoExplicitlyPaused = true
            lastExplicitPauseTime = System.currentTimeMillis()
            WatchSessionRepository.setPlaybackPlaying(false)
        } else {
            isVideoExplicitlyPaused = false
            lastExplicitPlayClickTime = System.currentTimeMillis()
            WatchSessionRepository.setPlaybackPlaying(true)
        }
    }

    private fun checkPlaybackControls(node: AccessibilityNodeInfo?) {
        if (node == null) return
        try {
            val state = findPlayerControlState(node)
            when (state) {
                true -> {
                    // Explicit "Play video" / "Replay video" player control is visible -> video is paused
                    isPlayButtonCurrentlyVisible = true
                    updateVideoPausedState(true)
                }
                false -> {
                    // Explicit "Pause video" player control is visible -> video is playing
                    isPlayButtonCurrentlyVisible = false
                    updateVideoPausedState(false)
                }
                null -> {
                    isPlayButtonCurrentlyVisible = false
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
            val text = node.text?.toString()?.trim() ?: ""
            val label = desc.ifBlank { text }
            val viewId = node.viewIdResourceName ?: ""
            val isPlayerControlBtn = viewId.contains("player_control_play_pause_replay_button", ignoreCase = true) ||
                    viewId.contains("play_pause_replay_button", ignoreCase = true) ||
                    viewId.contains("player_control_play_pause", ignoreCase = true)

            if (label.isNotEmpty() || isPlayerControlBtn) {
                val screenHeight = resources.displayMetrics.heightPixels.coerceAtLeast(800)
                val r = android.graphics.Rect()
                node.getBoundsInScreen(r)
                val inPlayerRegion = isPlayerControlBtn || (r.bottom in 1..(screenHeight * 0.65f).toInt() && r.top >= 0)

                if (inPlayerRegion) {
                    if (label.equals("Play video", ignoreCase = true) ||
                        label.equals("Replay video", ignoreCase = true) ||
                        (isPlayerControlBtn && label.equals("Play", ignoreCase = true)) ||
                        (isPlayerControlBtn && label.equals("Replay", ignoreCase = true)) ||
                        label.contains("वीडियो चलाएं") ||
                        label.contains("फिर से चलाएं")
                    ) {
                        return true
                    }
                    if (label.equals("Pause video", ignoreCase = true) ||
                        (isPlayerControlBtn && label.equals("Pause", ignoreCase = true)) ||
                        label.contains("वीडियो रोकें")
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

    private fun isCommentPlaceholder(raw: String): Boolean {
        val lower = raw.trim().lowercase()
        return lower.isEmpty() ||
                lower.startsWith("add a comment") ||
                lower.startsWith("add a public comment") ||
                lower.startsWith("add a reply") ||
                lower.startsWith("comment as ") ||
                lower.startsWith("reply as ") ||
                lower.startsWith("search youtube") ||
                lower.contains("टिप्पणी जोड़ें") ||
                lower.contains("जवाब जोड़ें")
    }

    private fun isCommentAddedConfirmationText(lowerText: String): Boolean {
        if (lowerText.isBlank()) return false
        return lowerText.contains("comment added") ||
                lowerText.contains("comment posted") ||
                lowerText.contains("reply added") ||
                lowerText.contains("reply posted") ||
                lowerText.contains("your comment was added") ||
                lowerText.contains("टिप्पणी जोड़ी गई") ||
                lowerText.contains("टिप्पणी पोस्ट की गई") ||
                lowerText.contains("जवाब जोड़ा गया")
    }

    private fun triggerGenuineCommentReward(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastCommentRewardTriggerTime < 3500L) {
            return
        }
        lastCommentRewardTriggerTime = now
        hasTypedCommentText = false
        lastTypedCommentText = ""
        lastTypedCommentTime = 0L
        wasCommentComposerOpen = false
        wasCommentEditTextActive = false
        lastCommentComposerOpenTime = 0L
        WatchSessionRepository.addLog("Comment detected ($reason)", LogType.SUCCESS)
        WatchSessionRepository.onTaskCommentDetected?.invoke()
    }

    private fun getAllYouTubeRootNodes(primaryRoot: AccessibilityNodeInfo? = null): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        try {
            if (primaryRoot != null && primaryRoot.packageName?.toString() == "com.google.android.youtube") {
                roots.add(primaryRoot)
            }
            val active = try { rootInActiveWindow } catch (_: Exception) { null }
            if (active != null && active.packageName?.toString() == "com.google.android.youtube" && !roots.contains(active)) {
                roots.add(active)
            }
            val winList = try { windows } catch (_: Exception) { null }
            if (!winList.isNullOrEmpty()) {
                for (w in winList) {
                    val wRoot = try { w.root } catch (_: Exception) { null }
                    if (wRoot != null && wRoot.packageName?.toString() == "com.google.android.youtube" && !roots.contains(wRoot)) {
                        roots.add(wRoot)
                    }
                }
            }
        } catch (_: Exception) {}
        return roots
    }

    private fun isSoftKeyboardVisible(): Boolean {
        return try {
            val winList = windows
            winList?.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD } == true
        } catch (_: Exception) {
            false
        }
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
        if (elapsedSinceLaunch > 25000L && currentPhase != LiveSearchPhase.IDLE && currentPhase != LiveSearchPhase.COMPLETED) {
            currentPhase = LiveSearchPhase.COMPLETED
        }
        val isLiveSearching = currentPhase != LiveSearchPhase.IDLE && currentPhase != LiveSearchPhase.COMPLETED
        val elapsedSinceClick = if (lastClickTime > 0L) System.currentTimeMillis() - lastClickTime else elapsedSinceLaunch
        return !isLiveSearching && elapsedSinceLaunch > 3200L && elapsedSinceClick > 2800L
    }

    fun inspectCurrentYouTubeState() {
        val isSessionActive = WatchSessionRepository.sessionState.value == com.example.data.SessionState.ACTIVE
        if (!isSessionActive) return

        val elapsedSinceLaunch = System.currentTimeMillis() - WatchSessionRepository.taskLaunchTimestampMillis
        if (elapsedSinceLaunch < 1200L) return

        // Drive the organic YouTube Search / Browse state machine periodically every 500ms while searching
        if (!hasClickedTarget && currentPhase != LiveSearchPhase.IDLE && currentPhase != LiveSearchPhase.COMPLETED) {
            val ytRootForSearch = getYouTubeRootNode()
            val titleToFind = targetSearchTitle
            if (ytRootForSearch != null && !titleToFind.isNullOrBlank()) {
                isYouTubeInForeground = true
                WatchSessionRepository.hasLeftAppForYouTube = true
                driveLiveSearchStep(ytRootForSearch, titleToFind)
            }
        }

        if (elapsedSinceLaunch < 3200L || !WatchSessionRepository.hasLeftAppForYouTube) return

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

                if (ytAppRoot != null) {
                    // YouTube application window is actively visible on screen (even if a keyboard/IME window is also open)
                    notInYouTubeStrikeCount = 0
                    isYouTubeInForeground = true
                } else if (topExternalAppPkg != null && topExternalAppPkg != "com.google.android.youtube") {
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
                    if (now - lastWatchHeaderCheckTime >= 400L) {
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

        if ((desc.equals("Shorts", ignoreCase = true) || text.equals("Shorts", ignoreCase = true) || viewId.contains("reel", ignoreCase = true)) &&
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
        val screenWidth = resources.displayMetrics.widthPixels.coerceAtLeast(400)
        val density = resources.displayMetrics.density
        val statusBarHeight = getStatusBarHeight()
        val playerBottomY = statusBarHeight + ((screenWidth * 9) / 16)
        val topPlayerMaxBottom = (playerBottomY + (48 * density).toInt()).coerceAtMost((screenHeight * 0.44f).toInt())
        val clickRect = android.graphics.Rect()
        clickedNode?.getBoundsInScreen(clickRect)

        // Ignore clicks with invalid/full-screen container bounds or clicks inside the top video player area
        if (clickRect.width() <= 0 ||
            clickRect.height() <= 0 ||
            clickRect.height() > (screenHeight * 0.56f).toInt() ||
            clickRect.bottom in 1..topPlayerMaxBottom ||
            clickRect.top < (playerBottomY + (24 * density).toInt())
        ) {
            return
        }

        // Schedule follow-up verification checks after any click below the video player
        // so if YouTube transitions to a new video asynchronously, we immediately detect the new title
        val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
        for (delayMs in listOf(350L, 800L, 1400L)) {
            uiHandler.postDelayed({
                if (isYouTubeInForeground && isReadyForWatchVerification()) {
                    try {
                        val root = getYouTubeRootNode() ?: rootInActiveWindow
                        if (root != null) {
                            verifyActiveYouTubeVideo(root)
                        }
                    } catch (_: Exception) {}
                }
            }, delayMs)
        }

        // Check if the directly clicked element itself is a harmless watch header, player setting, pause/play, or comment control
        val selfText = "$desc $text $eventSummary $viewId".lowercase()
        if (selfText.contains("comment") ||
            selfText.contains("टिप्पणी") ||
            selfText.contains("टिप्पणियाँ") ||
            selfText.contains("reply") ||
            selfText.contains("replies") ||
            selfText.contains("जवाब") ||
            selfText.contains("add a comment") ||
            selfText.contains("add a reply") ||
            selfText.contains("pinned by") ||
            selfText.contains("hearted by") ||
            selfText.contains("newest") ||
            selfText.contains("engagement_panel") ||
            selfText.contains("description") ||
            desc.equals("Pause video", ignoreCase = true) ||
            desc.equals("Play video", ignoreCase = true) ||
            desc.equals("Replay video", ignoreCase = true) ||
            desc.equals("Pause", ignoreCase = true) ||
            desc.equals("Play", ignoreCase = true) ||
            desc.equals("Replay", ignoreCase = true) ||
            desc.equals("Video player", ignoreCase = true) ||
            desc.contains("वीडियो रोकें") ||
            desc.contains("वीडियो चलाएं") ||
            desc.contains("फिर से चलाएं") ||
            desc.equals("Subscribe", ignoreCase = true) ||
            desc.equals("Subscribed", ignoreCase = true) ||
            desc.startsWith("Subscribe to", ignoreCase = true) ||
            desc.equals("Share", ignoreCase = true) ||
            desc.startsWith("Share ", ignoreCase = true) ||
            desc.equals("Download", ignoreCase = true) ||
            desc.startsWith("Download ", ignoreCase = true) ||
            desc.equals("Remix", ignoreCase = true) ||
            desc.equals("Save", ignoreCase = true) ||
            desc.equals("Clip", ignoreCase = true) ||
            desc.equals("Close", ignoreCase = true) ||
            desc.equals("Settings", ignoreCase = true) ||
            desc.equals("Captions", ignoreCase = true) ||
            desc.equals("More options", ignoreCase = true) ||
            desc.equals("Hide controls", ignoreCase = true) ||
            desc.equals("Show controls", ignoreCase = true) ||
            desc.equals("Enter full screen", ignoreCase = true) ||
            desc.equals("Exit full screen", ignoreCase = true) ||
            desc.equals("Full screen", ignoreCase = true) ||
            desc.equals("Expand description", ignoreCase = true) ||
            desc.equals("Collapse description", ignoreCase = true) ||
            text.equals("...more", ignoreCase = true) ||
            text.equals("Show more", ignoreCase = true) ||
            text.equals("Show less", ignoreCase = true) ||
            selfText.contains("skip ad") ||
            viewId.contains("play_pause", ignoreCase = true) ||
            viewId.contains("player_control", ignoreCase = true) ||
            viewId.contains("player_overlay", ignoreCase = true) ||
            viewId.contains("comment", ignoreCase = true) ||
            viewId.contains("subscribe", ignoreCase = true) ||
            viewId.contains("like_button", ignoreCase = true) ||
            viewId.contains("dislike_button", ignoreCase = true) ||
            viewId.contains("share_button", ignoreCase = true)
        ) {
            return
        }

        // Climb up to 3 parent levels to reach the full video card container in the feed below the watch header,
        // but NEVER climb into a scrollable container (RecyclerView / ScrollView) or above the feed area!
        var cardNode: AccessibilityNodeInfo? = clickedNode
        var depth = 0
        while (cardNode != null && depth < 3) {
            val parent = cardNode.parent ?: break
            if (parent.isScrollable) break
            val pViewId = parent.viewIdResourceName?.lowercase() ?: ""
            if (pViewId.contains("comment") || pViewId.contains("engagement")) {
                return
            }
            val pRect = android.graphics.Rect()
            parent.getBoundsInScreen(pRect)
            if (pRect.top >= (screenHeight * 0.40f).toInt() && pRect.height() in 40..(screenHeight * 0.48f).toInt()) {
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

        val cardText = sb.toString().trim()
        val lowerCard = cardText.lowercase()
        val cardViewId = (cardNode?.viewIdResourceName ?: viewId).lowercase()

        // Never treat clicks on the Comments teaser card, comment items, or description box as a video switch!
        val isCommentOrDescriptionItem = cardViewId.contains("comment") ||
                cardViewId.contains("engagement") ||
                cardViewId.contains("teaser") ||
                lowerCard.contains("comments") ||
                lowerCard.contains("add a comment") ||
                lowerCard.contains("add a reply") ||
                lowerCard.contains("टिप्पणियाँ") ||
                lowerCard.contains("टिप्पणी") ||
                lowerCard.contains("like this comment") ||
                lowerCard.contains("dislike this comment") ||
                lowerCard.contains("pinned by") ||
                lowerCard.contains("hearted by") ||
                lowerCard.contains(" reply") ||
                lowerCard.contains(" replies") ||
                lowerCard.contains("जवाब")

        if (isCommentOrDescriptionItem) {
            return
        }

        val hasDurationPattern = lowerCard.contains("minutes") ||
                lowerCard.contains("seconds") ||
                lowerCard.contains("मिनट") ||
                lowerCard.contains("सेकंड") ||
                Regex("\\b\\d{1,2}:\\d{2}\\b").containsMatchIn(lowerCard)

        val hasViewCountPattern = lowerCard.contains("views") ||
                lowerCard.contains("watching") ||
                lowerCard.contains("बार देखा गया") ||
                lowerCard.contains("no views") ||
                lowerCard.contains("ago") ||
                lowerCard.contains("पहले")

        // Treat as a video card click if it has feed video card viewId OR duration/view metadata below the watch header
        val isVideoListingCard = cardText.length >= 8 && (
                hasDurationPattern ||
                (hasViewCountPattern && cardRect.top >= (screenHeight * 0.42f).toInt()) ||
                cardViewId.contains("video_lockup") ||
                cardViewId.contains("compact_video") ||
                cardViewId.contains("video_card") ||
                cardViewId.contains("reel_item") ||
                cardViewId.contains("related_item") ||
                cardViewId.contains("endscreen") ||
                cardViewId.contains("lockup")
        )

        if (isVideoListingCard) {
            val cleanClickedTitle = extractCleanTitleCandidate(cardText)
            if (cleanClickedTitle.length < 4) return

            val match = TitleMatcher.evaluateMatch(
                playingTitle = cleanClickedTitle,
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
        val rect: android.graphics.Rect,
        val className: String = "",
        val isEditable: Boolean = false,
        val isFocused: Boolean = false
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
            val cls = node.className?.toString() ?: ""
            val editable = node.isEditable || cls.contains("EditText", ignoreCase = true)
            val focused = node.isFocused
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
                    v.contains("pivot", ignoreCase = true) ||
                    v.contains("comment", ignoreCase = true) ||
                    v.contains("engagement", ignoreCase = true) ||
                    v.contains("send", ignoreCase = true) ||
                    v.contains("snackbar", ignoreCase = true)
            )
            if (t.isNotEmpty() || d.isNotEmpty() || hasRelevantViewId || editable) {
                val r = android.graphics.Rect()
                node.getBoundsInScreen(r)
                val screenHeight = resources.displayMetrics.heightPixels.coerceAtLeast(800)
                val screenWidth = resources.displayMetrics.widthPixels.coerceAtLeast(400)
                if (r.width() > 0 && r.height() > 0 && r.bottom > 0 && r.top < screenHeight && r.right > 0 && r.left < screenWidth) {
                    out.add(UiNodeEntry(t, d, v, r, cls, editable, focused))
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
        var singleLine = raw.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
        if (singleLine.length < 4) return ""

        // Strip leading "Expand description" or "Description" prefixes added by accessibility labels
        singleLine = singleLine
            .replace(Regex("^(?:expand description|collapse description|description)\\s*[:,\\-•·|]?\\s*", RegexOption.IGNORE_CASE), "")
            .trim()

        // Strip leading hashtags if followed by actual title words
        val withoutLeadingHashtags = singleLine.replace(Regex("^(?:#\\S+\\s+)+"), "").trim()
        if (withoutLeadingHashtags.length >= 4) {
            singleLine = withoutLeadingHashtags
        }
        if (singleLine.startsWith("#") && !singleLine.contains(" ")) return ""

        // Discard player seekbar / duration strings ("0 minutes 15 seconds of 4 minutes 30 seconds" or "0:15 / 4:30")
        if (Regex("^\\d+\\s*(?:hours?|minutes?|seconds?|घंटे|मिनट|सेकंड)\\b.*\\b(?:of|में से)\\b.*$", RegexOption.IGNORE_CASE).matches(singleLine)) {
            return ""
        }
        if (Regex("^\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*/\\s*\\d{1,2}:\\d{2}(?::\\d{2})?)?$", RegexOption.IGNORE_CASE).matches(singleLine)) {
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
            .replace(Regex("(?:\\.\\.\\.more|…more|\\bshow more\\b|\\bexpand description\\b)\\s*$", RegexOption.IGNORE_CASE), "")
            .trim()

        // Strip trailing "<number> views / watching / No views ..." metadata appended to the title in YouTube's Litho header
        cleaned = cleaned.replace(
            Regex("(?:[,\\-•·|]|\\s)+(?:no\\s+views|\\d[0-9.,]*\\s*(?:k|m|b|lakh|lakhs|crore|crores|हज़ार|लाख|करोड़)?\\s*(?:views|view|watching|subscribers|बार देखा गया|लोग देख रहे हैं))\\b.*$", RegexOption.IGNORE_CASE),
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
            val allRoots = getAllYouTubeRootNodes(rootNode)
            if (allRoots.isEmpty()) {
                collectScreenNodes(rootNode, entries)
            } else {
                for (r in allRoots) {
                    collectScreenNodes(r, entries)
                }
            }
            if (entries.isEmpty()) return

            val screenHeight = resources.displayMetrics.heightPixels.coerceAtLeast(800)
            val screenWidth = resources.displayMetrics.widthPixels.coerceAtLeast(400)
            val density = resources.displayMetrics.density
            val statusBarHeight = getStatusBarHeight()
            val playerBottomY = statusBarHeight + ((screenWidth * 9) / 16)
            val now = System.currentTimeMillis()

            // 0. Check for YouTube Comment Added / Composer State / Newly Posted Comment in real-time
            if (entries.any { e -> isCommentAddedConfirmationText("${e.text} ${e.desc}".lowercase()) }) {
                triggerGenuineCommentReward("YouTube 'Comment added' screen confirmation")
            } else {
                val activeCommentEditEntry = entries.firstOrNull { e ->
                    val v = e.viewId.lowercase()
                    val isSearch = v.contains("search_edit_text") || v.contains("search_src_text") || v.contains("search_box") || e.rect.top < (screenHeight * 0.16f).toInt()
                    !isSearch && (
                        e.isEditable ||
                        e.className.contains("EditText", ignoreCase = true) ||
                        v.contains("comment_composer") ||
                        v.contains("comment_box")
                    )
                }

                val isSendButtonCurrentlyVisible = entries.any { e ->
                    val d = e.desc.trim()
                    val t = e.text.trim()
                    val v = e.viewId.lowercase()
                    e.rect.top >= (screenHeight * 0.25f).toInt() && (
                        d.equals("Send", ignoreCase = true) ||
                        d.equals("Send comment", ignoreCase = true) ||
                        d.equals("Post", ignoreCase = true) ||
                        d.equals("Post comment", ignoreCase = true) ||
                        d.contains("टिप्पणी भेजें") ||
                        d.equals("भेजें", ignoreCase = true) ||
                        t.equals("Send", ignoreCase = true) ||
                        t.equals("Post", ignoreCase = true) ||
                        v.contains("send_button") ||
                        v.contains("comment_send") ||
                        v.contains("post_button")
                    )
                }

                if (activeCommentEditEntry != null || isSendButtonCurrentlyVisible) {
                    wasCommentComposerOpen = true
                    lastCommentComposerOpenTime = now
                    val editTxt = activeCommentEditEntry?.text?.trim().orEmpty()
                    if ((editTxt.isNotEmpty() && !isCommentPlaceholder(editTxt)) || isSendButtonCurrentlyVisible) {
                        hasTypedCommentText = true
                        if (editTxt.isNotEmpty() && !isCommentPlaceholder(editTxt)) {
                            lastTypedCommentText = editTxt
                        }
                        lastTypedCommentTime = now
                        wasCommentEditTextActive = true
                    }
                } else if (isSoftKeyboardVisible() && entries.any { e ->
                        val comb = "${e.text} ${e.desc} ${e.viewId}".lowercase()
                        comb.contains("comment") || comb.contains("reply") || comb.contains("टिप्पणी")
                    }) {
                    wasCommentComposerOpen = true
                    lastCommentComposerOpenTime = now
                } else {
                    // Comment composer EditText is no longer open!
                    // Check if user had typed a comment or had the Send button visible and the composer just closed after submission
                    val notCancelled = (now - lastCommentCancelClickTime) > 3500L
                    if (wasCommentEditTextActive && hasTypedCommentText && notCancelled && (now - lastTypedCommentTime) in 120L..30_000L) {
                        triggerGenuineCommentReward("Comment composer submitted and closed")
                    } else if ((hasTypedCommentText || wasCommentComposerOpen) && notCancelled && (now - lastCommentComposerOpenTime) < 90_000L) {
                        // Also check if a newly posted comment ("0 seconds ago", "1 second ago", "Just now", or matching typed text) is visible in the comments list
                        val freshCommentEntry = entries.firstOrNull { e ->
                            val comb = "${e.text} ${e.desc}".lowercase()
                            !e.isEditable && e.rect.top >= playerBottomY && (
                                Regex("\\b(?:0|1|2|3|4|5|6|7|8)\\s*(?:seconds?|secs?|s)\\s+ago\\b", RegexOption.IGNORE_CASE).containsMatchIn(comb) ||
                                comb.contains("just now") ||
                                comb.contains("a moment ago") ||
                                comb.contains("few seconds ago") ||
                                comb.contains("अभी") ||
                                comb.contains("कुछ सेकंड पहले") ||
                                Regex("\\b[0-8]\\s*सेकंड\\s*पहले\\b").containsMatchIn(comb) ||
                                (lastTypedCommentText.length >= 2 && comb.contains(lastTypedCommentText.lowercase()))
                            )
                        }
                        if (freshCommentEntry != null) {
                            triggerGenuineCommentReward("Newly posted comment visible in comments list")
                        }
                    }
                }
            }

            // 1. Check if user switched to YouTube Shorts player
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

            // 2. Check if user minimized the video into YouTube's bottom Miniplayer bar
            val hasMiniplayerBarAtBottom = entries.any { e ->
                val v = e.viewId.lowercase()
                val d = e.desc.lowercase()
                val inBottomZone = e.rect.top >= (screenHeight * 0.65f).toInt()
                inBottomZone && (
                        v.contains("miniplayer") ||
                        v.contains("floaty_bar") ||
                        d.equals("expand miniplayer", ignoreCase = true) ||
                        d.equals("close miniplayer", ignoreCase = true)
                )
            }

            if (hasMiniplayerBarAtBottom) {
                wrongVideoStrikeCount++
                if (wrongVideoStrikeCount >= 2) {
                    wrongVideoStrikeCount = 0
                    WatchSessionRepository.triggerTaskIncomplete(
                        "Task Incomplete! Aapne YouTube mein target video minimize (miniplayer) kar diya."
                    )
                }
                return
            }

            // 3. Check explicit player title if visible inside the top player
            val explicitPlayerTitleNode = entries.firstOrNull { e ->
                val v = e.viewId.lowercase()
                v.contains("player_video_title") && (e.text.length >= 4 || e.desc.length >= 4)
            }
            if (explicitPlayerTitleNode != null) {
                val rawPTitle = explicitPlayerTitleNode.text.ifBlank { explicitPlayerTitleNode.desc }
                val pTitle = extractCleanTitleCandidate(rawPTitle).ifBlank { rawPTitle }
                val match = TitleMatcher.evaluateMatch(pTitle, targetTitle, null, targetAuthor)

                if (match == com.example.data.MatchResult.MATCH) {
                    if (lockedWatchPageTitle == null && pTitle.length >= 5) {
                        lockedWatchPageTitle = pTitle
                    }
                    wrongVideoStrikeCount = 0
                    return
                } else if (match == com.example.data.MatchResult.MISMATCH) {
                    wrongVideoStrikeCount++
                    if (wrongVideoStrikeCount >= 2) {
                        wrongVideoStrikeCount = 0
                        WatchSessionRepository.triggerTaskIncomplete(
                            "Task Incomplete! Aapne YouTube mein target video (\"$targetTitle\") ke bajaye doosra video (\"$pTitle\") play kar diya."
                        )
                    }
                    return
                }
            }

            // 3b. Also check active MediaSession metadata title if YouTube reported a new video title
            val mediaTitle = WatchSessionRepository.currentMediaTitle.value
            val mediaArtist = WatchSessionRepository.currentMediaArtist.value
            val mediaDetected = WatchSessionRepository.mediaSessionDetected.value
            if (mediaDetected && !mediaTitle.isNullOrBlank() && mediaTitle.length >= 4) {
                val isGenericTarget = targetTitle.equals("YouTube Video Task", ignoreCase = true) ||
                        targetTitle.equals("YouTube Video", ignoreCase = true) ||
                        targetTitle.startsWith("YouTube Video (", ignoreCase = true)
                if (!isGenericTarget) {
                    val mediaMatch = TitleMatcher.evaluateMatch(mediaTitle, targetTitle, mediaArtist, targetAuthor)
                    if (mediaMatch == com.example.data.MatchResult.MISMATCH) {
                        wrongVideoStrikeCount++
                        if (wrongVideoStrikeCount >= 2) {
                            wrongVideoStrikeCount = 0
                            WatchSessionRepository.triggerTaskIncomplete(
                                "Task Incomplete! Aapne YouTube par target video (\"$targetTitle\") ke bajaye doosra video (\"$mediaTitle\") play kar diya."
                            )
                        }
                        return
                    }
                }
            }

            // 4. Check Watch Metadata Header (right below the 16:9 video player, above Subscribe/Like/Share)
            val maxButtonHeight = (115 * density).toInt()
            val likeOrShareAnchor = entries.filter { e ->
                val t = e.text.lowercase()
                val d = e.desc.lowercase()
                val v = e.viewId.lowercase()
                val inMiddleBand = e.rect.top in (screenHeight * 0.19f).toInt()..(screenHeight * 0.66f).toInt() &&
                        e.rect.height() <= maxButtonHeight
                val isCommentLikeBtn = d.contains("comment") || d.contains("टिप्पणी") || v.contains("comment")
                inMiddleBand && !isCommentLikeBtn && (
                        d.startsWith("like this video") ||
                        d.startsWith("dislike this video") ||
                        d.contains("like this") ||
                        d.equals("share", ignoreCase = true) ||
                        d.startsWith("share ") ||
                        d.equals("remix", ignoreCase = true) ||
                        d.startsWith("download") ||
                        d.contains("शेयर करें") ||
                        v.contains("share_button") ||
                        v.contains("like_button") ||
                        t == "share" ||
                        t == "remix" ||
                        t == "download"
                )
            }.minByOrNull { it.rect.top }

            val subscribeAnchor = entries.filter { e ->
                val t = e.text.lowercase()
                val d = e.desc.lowercase()
                val v = e.viewId.lowercase()
                val inMiddleBand = e.rect.top in (screenHeight * 0.19f).toInt()..(screenHeight * 0.64f).toInt() &&
                        e.rect.height() <= maxButtonHeight
                inMiddleBand && (
                        t == "subscribe" ||
                        t == "subscribed" ||
                        t.contains("subscribe") ||
                        t.contains("सदस्यता") ||
                        d.startsWith("subscribe") ||
                        d.contains("subscribe to") ||
                        d.contains("सदस्यता") ||
                        v.contains("subscribe_button") ||
                        v.contains("channel_bar")
                )
            }.minByOrNull { it.rect.top }

            val watchHeaderMetaAnchor = entries.filter { e ->
                val comb = "${e.text} ${e.desc} ${e.viewId}".lowercase()
                val inHeaderBand = e.rect.top in (playerBottomY - (8 * density).toInt())..(playerBottomY + (160 * density).toInt()) &&
                        e.rect.height() <= (160 * density).toInt()
                inHeaderBand && (
                        comb.contains("expand description") ||
                        comb.contains("...more") ||
                        comb.contains("…more") ||
                        comb.contains("video_metadata") ||
                        comb.contains("watch_metadata") ||
                        comb.contains("title")
                )
            }.maxByOrNull { it.rect.bottom }

            // Check if a full-height Engagement Panel (Comments / Description sheet) is actively covering the Watch Header
            val isFullEngagementSheetCoveringHeader = (subscribeAnchor == null && likeOrShareAnchor == null && watchHeaderMetaAnchor == null) && entries.any { e ->
                val d = e.desc.lowercase().trim()
                val v = e.viewId.lowercase()
                val inPanelZone = e.rect.top >= (playerBottomY - (24 * density).toInt()).coerceAtLeast((screenHeight * 0.18f).toInt())
                inPanelZone && (
                        v.contains("engagement_panel") ||
                        v.contains("comment_composer") ||
                        d == "close comments" ||
                        d == "close description" ||
                        d.startsWith("like this comment") ||
                        d.startsWith("dislike this comment")
                )
            }

            if (isFullEngagementSheetCoveringHeader) {
                wrongVideoStrikeCount = 0
                return
            }

            // Only verify the Watch Header title when the Watch Header is visible below the player
            if (subscribeAnchor == null && likeOrShareAnchor == null && watchHeaderMetaAnchor == null) {
                return
            }

            val headerTopY = (playerBottomY - (4 * density).toInt()).coerceAtLeast((screenHeight * 0.18f).toInt())
            val minTitleBottomY = (playerBottomY + (10 * density).toInt()).coerceAtLeast((screenHeight * 0.21f).toInt())
            val headerBottomY = when {
                subscribeAnchor != null && likeOrShareAnchor != null ->
                    maxOf(subscribeAnchor.rect.bottom, likeOrShareAnchor.rect.top).coerceAtMost((screenHeight * 0.56f).toInt())
                subscribeAnchor != null ->
                    (subscribeAnchor.rect.bottom + (20 * density).toInt()).coerceAtMost((screenHeight * 0.54f).toInt())
                likeOrShareAnchor != null ->
                    (likeOrShareAnchor.rect.top + (12 * density).toInt()).coerceAtMost((screenHeight * 0.56f).toInt())
                watchHeaderMetaAnchor != null ->
                    (watchHeaderMetaAnchor.rect.bottom + (24 * density).toInt()).coerceAtMost((screenHeight * 0.52f).toInt())
                else -> 0
            }

            // Require at least 36dp of visible Watch Header height between the player bottom and the Subscribe/Like anchor
            // so we never mistake a partially scrolled Watch Header for a different video
            if (headerBottomY - headerTopY >= (36 * density).toInt()) {
                val chromeLabels = setOf(
                    "subscribe", "subscribed", "join", "share", "remix", "download",
                    "clip", "save", "report", "comments", "more", "...more", "show more", "show less",
                    "play video", "pause video", "replay video", "autoplay is on", "autoplay is off",
                    "mute", "unmute", "full screen", "enter full screen", "exit full screen",
                    "collapse", "minimize", "close", "sponsored", "visit site", "live chat",
                    "next video", "previous video", "settings", "captions", "video player",
                    "hide controls", "show controls", "more options", "expand description",
                    "collapse description", "description", "seek slider", "skip ad", "skip ads",
                    "pull up for precise seeking", "slide left or right to seek", "release to cancel",
                    "more videos", "tap to unmute", "double-tap to seek", "playing next", "auto-dubbed"
                )

                val sortedHeaderEntries = entries
                    .filter { e ->
                        val vLow = e.viewId.lowercase()
                        val isPlayerControlView = vLow.contains("player") ||
                                vLow.contains("time_bar") ||
                                vLow.contains("scrubber") ||
                                vLow.contains("control") ||
                                vLow.contains("overlay") ||
                                vLow.contains("inline") ||
                                vLow.contains("autonav") ||
                                vLow.contains("seek") ||
                                vLow.contains("chapter") ||
                                vLow.contains("caption") ||
                                vLow.contains("subtitle") ||
                                vLow.contains("live_chat") ||
                                vLow.contains("tooltip") ||
                                vLow.contains("hint")
                        !isPlayerControlView &&
                                e.rect.top in headerTopY..headerBottomY &&
                                e.rect.bottom > minTitleBottomY &&
                                e.rect.height() <= (screenHeight * 0.35f).toInt()
                    }
                    .sortedWith(compareBy<UiNodeEntry> { it.rect.top }.thenByDescending { it.rect.width() })

                val cleanedTitleCandidates = mutableListOf<String>()
                val normAuthor = TitleMatcher.normalize(targetAuthor)

                for (e in sortedHeaderEntries) {
                    for (candidate in listOf(e.text, e.desc)) {
                        val rawClean = candidate.trim()
                        if (rawClean.length >= 3 && !chromeLabels.contains(rawClean.lowercase())) {
                            val extracted = extractCleanTitleCandidate(rawClean)
                            val low = extracted.lowercase()
                            val normTxt = TitleMatcher.normalize(extracted)
                            val isJustChannel = normAuthor.isNotEmpty() &&
                                    (normTxt == normAuthor || normTxt.replace(" ", "") == normAuthor.replace(" ", ""))

                            if (extracted.length >= 4 &&
                                !isJustChannel &&
                                !chromeLabels.contains(low) &&
                                !low.startsWith("@") &&
                                !low.matches(Regex("^[0-9:\\s/•·.,%-]+$")) &&
                                !low.startsWith("ad ·") &&
                                !low.startsWith("sponsored ·") &&
                                !low.startsWith("skip ad") &&
                                !low.startsWith("like this") &&
                                !low.startsWith("dislike this") &&
                                !low.startsWith("subscribe to") &&
                                !low.startsWith("unsubscribe from") &&
                                !low.startsWith("options for") &&
                                !low.startsWith("save to") &&
                                !low.startsWith("share") &&
                                !low.startsWith("comments") &&
                                !low.startsWith("add a comment") &&
                                !low.startsWith("add a reply") &&
                                !low.startsWith("pinned by") &&
                                !low.startsWith("go to channel")
                            ) {
                                if (!cleanedTitleCandidates.contains(extracted)) {
                                    cleanedTitleCandidates.add(extracted)
                                }
                            }
                        }
                    }
                }

                if (cleanedTitleCandidates.isNotEmpty()) {
                    val isGenericTarget = targetTitle.equals("YouTube Video Task", ignoreCase = true) ||
                            targetTitle.equals("YouTube Video", ignoreCase = true) ||
                            targetTitle.startsWith("YouTube Video (", ignoreCase = true)

                    // Check if ANY candidate in the Watch Header above the Subscribe/Like row matches our target video
                    val matchingCandidate = cleanedTitleCandidates.firstOrNull { candidate ->
                        isGenericTarget ||
                                TitleMatcher.evaluateMatch(candidate, targetTitle, null, targetAuthor) == com.example.data.MatchResult.MATCH
                    }

                    if (matchingCandidate != null) {
                        if (lockedWatchPageTitle == null) {
                            lockedWatchPageTitle = matchingCandidate
                        }
                        wrongVideoStrikeCount = 0
                    } else {
                        wrongVideoStrikeCount++
                        if (wrongVideoStrikeCount >= 2) {
                            val detectedWrong = cleanedTitleCandidates.first().ifBlank { "Doosra video" }
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

    private fun dispatchSwipeUpGesture(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val screenWidth = resources.displayMetrics.widthPixels.coerceAtLeast(400)
            val screenHeight = resources.displayMetrics.heightPixels.coerceAtLeast(800)
            val centerX = (screenWidth * 0.50f)
            val startY = (screenHeight * 0.72f)
            val endY = (screenHeight * 0.32f)
            val path = android.graphics.Path().apply {
                moveTo(centerX, startY)
                lineTo(centerX, endY)
            }
            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 280)
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
