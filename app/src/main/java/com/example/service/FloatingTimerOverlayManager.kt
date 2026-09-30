package com.example.service

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MainActivity
import com.example.data.DataStoreManager
import com.example.data.LogType
import com.example.data.WatchDurationTier
import com.example.repository.WatchSessionRepository
import com.example.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FloatingTimerOverlayManager(private val context: Context) {

    companion object {
        private val globalAttachedViews = mutableListOf<View>()

        private fun removeAllGlobalViews(wm: WindowManager) {
            val iterator = globalAttachedViews.iterator()
            while (iterator.hasNext()) {
                val v = iterator.next()
                try {
                    wm.removeViewImmediate(v)
                } catch (_: Exception) {
                    try {
                        wm.removeView(v)
                    } catch (_: Exception) {}
                }
                iterator.remove()
            }
        }
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val dataStoreManager = DataStoreManager(context)

    private var overlayRootView: FrameLayout? = null
    private var timerTextView: TextView? = null
    private var liveDotView: View? = null
    private var milestoneBadgeTextView: TextView? = null
    private var likeBadgeView: TextView? = null
    private var commentBadgeView: TextView? = null
    private var celebrationContainer: LinearLayout? = null
    private var celebrationText: TextView? = null
    private var lastCelebratedTier: WatchDurationTier? = null
    private var incompletePopupView: FrameLayout? = null

    private var isAttached = false
    private var currentCommentCount = 0
    private var isTaskLiked = false
    private var density = context.resources.displayMetrics.density

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    fun showOverlay() {
        runOnMain {
            if (WatchSessionRepository.sessionState.value != com.example.data.SessionState.ACTIVE ||
                WatchSessionRepository.isAppInForeground
            ) {
                return@runOnMain
            }

            // If already attached and active, do not recreate or duplicate the overlay
            if (isAttached && overlayRootView != null && incompletePopupView == null) {
                return@runOnMain
            }

            // Guarantee no stale or duplicate overlay views exist in WindowManager
            removeAllGlobalViews(windowManager)
            overlayRootView?.let {
                try { windowManager.removeView(it) } catch (_: Exception) {}
            }
            incompletePopupView?.let {
                try { windowManager.removeView(it) } catch (_: Exception) {}
            }
            overlayRootView = null
            incompletePopupView = null
            isAttached = false

            if (!Settings.canDrawOverlays(context)) {
                WatchSessionRepository.addLog(
                    "Floating timer overlay not displayed: 'Display over other apps' permission required.",
                    LogType.WARNING
                )
                return@runOnMain
            }

            density = context.resources.displayMetrics.density

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (8 * density).toInt()
                y = (120 * density).toInt()
            }

            val root = FrameLayout(context).apply {
                clipChildren = false
                clipToPadding = false
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // Sleek side pill container
            val pillLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())

                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 20 * density
                    setColor(Color.parseColor("#0B1120")) // Deep Obsidian Slate
                    setStroke((1.5f * density).toInt(), Color.parseColor("#F59E0B")) // Glowing Gold Amber
                }
                background = bg
                elevation = 16 * density
            }

            // Live pulsing status dot
            val liveDot = View(context).apply {
                val dotSize = (8 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    rightMargin = (6 * density).toInt()
                }
                val dotBg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#10B981")) // Emerald Green
                }
                background = dotBg
            }
            pillLayout.addView(liveDot)
            this.liveDotView = liveDot

            // Timer display
            val timerTv = TextView(context).apply {
                text = "▶ 00:00 / 00:00"
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = (6 * density).toInt()
                }
            }
            pillLayout.addView(timerTv)
            this.timerTextView = timerTv

            // Reward / Milestone badge
            val milestoneTv = TextView(context).apply {
                text = "Min 3m"
                setTextColor(Color.parseColor("#F59E0B"))
                textSize = 10f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                val badgeBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 6 * density
                    setColor(Color.parseColor("#1E293B"))
                }
                background = badgeBg
                setPadding((6 * density).toInt(), (3 * density).toInt(), (6 * density).toInt(), (3 * density).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = (5 * density).toInt()
                }
            }
            pillLayout.addView(milestoneTv)
            this.milestoneBadgeTextView = milestoneTv

            // Auto Like Monitor Badge (Read-only status indicator - coins only awarded when liking inside YouTube!)
            val likeBadge = TextView(context).apply {
                text = "👍 Like (+5c)"
                setTextColor(Color.parseColor("#F59E0B"))
                textSize = 10f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8 * density
                    setColor(Color.parseColor("#1E293B"))
                    setStroke((1 * density).toInt(), Color.parseColor("#F59E0B"))
                }
                background = bg
                setPadding((6 * density).toInt(), (3 * density).toInt(), (6 * density).toInt(), (3 * density).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = (5 * density).toInt()
                }
                setOnClickListener {
                    if (isTaskLiked) {
                        triggerCelebration("✓ Like bonus (+5c) already received once!")
                    } else {
                        triggerCelebration("👇 Tap YouTube's real Like button below video for +5c!")
                    }
                }
            }
            pillLayout.addView(likeBadge)
            this.likeBadgeView = likeBadge

            // Auto Comment Monitor Badge (Read-only status indicator - coins only awarded when posting comment inside YouTube!)
            val commentBadge = TextView(context).apply {
                text = "💬 +5c (0/2)"
                setTextColor(Color.parseColor("#38BDF8")) // Sky blue
                textSize = 10f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8 * density
                    setColor(Color.parseColor("#1E293B"))
                    setStroke((1 * density).toInt(), Color.parseColor("#38BDF8"))
                }
                background = bg
                setPadding((6 * density).toInt(), (3 * density).toInt(), (6 * density).toInt(), (3 * density).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = (6 * density).toInt()
                }
                setOnClickListener {
                    if (currentCommentCount >= 2) {
                        triggerCelebration("✓ Max 2 Comment bonuses (+10c) already received!")
                    } else {
                        triggerCelebration("👇 Post a real comment inside YouTube for +5c!")
                    }
                }
            }
            pillLayout.addView(commentBadge)
            this.commentBadgeView = commentBadge

            // Dismiss / Close button
            val closeBtn = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setColorFilter(Color.parseColor("#94A3B8"))
                val iconSize = (14 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                setOnClickListener {
                    hideOverlay()
                }
            }
            pillLayout.addView(closeBtn)

            // Celebration banner (Animated badge popping above the pill)
            val celebrationBox = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                visibility = View.GONE
                val cBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 14 * density
                    setColor(Color.parseColor("#065F46")) // Rich emerald
                    setStroke((1.5f * density).toInt(), Color.parseColor("#F59E0B"))
                }
                background = cBg
                setPadding((12 * density).toInt(), (5 * density).toInt(), (12 * density).toInt(), (5 * density).toInt())
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    topMargin = -(34 * density).toInt()
                }
            }

            val celebTv = TextView(context).apply {
                text = "🎉 +10 COINS UNLOCKED!"
                setTextColor(Color.WHITE)
                textSize = 11f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            celebrationBox.addView(celebTv)
            this.celebrationContainer = celebrationBox
            this.celebrationText = celebTv

            root.addView(pillLayout)
            root.addView(celebrationBox)

            // Dragging Listener (Does NOT switch app on tap so YouTube playback is never interrupted)
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

            pillLayout.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()
                        params.x = (initialX + deltaX).coerceAtLeast(0)
                        params.y = (initialY + deltaY).coerceAtLeast(30)
                        try {
                            windowManager.updateViewLayout(root, params)
                        } catch (_: Exception) {}
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        true
                    }
                    else -> false
                }
            }

            try {
                windowManager.addView(root, params)
                globalAttachedViews.add(root)
                overlayRootView = root
                isAttached = true
                WatchSessionRepository.addLog("Side floating watch pill active on screen!", LogType.SUCCESS)
            } catch (e: Exception) {
                isAttached = false
                WatchSessionRepository.addLog("Failed to add floating timer: ${e.message}", LogType.ERROR)
            }

            // Hook live auto-detection listeners from accessibility & repository
            WatchSessionRepository.onTaskLikeDetected = {
                handleLikeDetected()
            }
            WatchSessionRepository.onVideoAlreadyLikedDetected = { _ ->
                // If video was already liked prior to this watch session, mark badge without awarding duplicate coins
                val activeId = WatchSessionRepository.activeTaskId.value ?: "default_task"
                overlayScope.launch {
                    dataStoreManager.markTaskAlreadyLiked(activeId)
                    runOnMain {
                        isTaskLiked = true
                        applyLikedBadgeStyle()
                    }
                }
            }
            WatchSessionRepository.onTaskCommentDetected = {
                handleCommentDetected()
            }
            WatchSessionRepository.onRequestHideOverlay = {
                hideOverlay()
            }
            WatchSessionRepository.onRequestShowOverlay = {
                showOverlay()
            }

            // Check if active task is already liked or commented
            val activeId = WatchSessionRepository.activeTaskId.value ?: "default_task"
            overlayScope.launch {
                try {
                    val likedSet = dataStoreManager.likedTasksFlow.first()
                    val alreadyLiked = likedSet.contains(activeId)
                    val commentMap = dataStoreManager.commentCountsFlow.first()
                    val cCount = commentMap[activeId] ?: 0

                    runOnMain {
                        isTaskLiked = alreadyLiked
                        if (alreadyLiked) {
                            applyLikedBadgeStyle()
                        }
                        currentCommentCount = cCount
                        updateCommentBadge()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Automatic Like Detection Handler:
     * When user genuinely likes the target video in YouTube for the first time,
     * award +5 coins ONCE, update DataStore, and update badge style to emerald "✓ Liked (+5c)".
     */
    private fun handleLikeDetected() {
        if (isTaskLiked) return
        val taskId = WatchSessionRepository.activeTaskId.value ?: "default_task"
        val taskTitle = WatchSessionRepository.targetTaskTitle.value ?: "YouTube Video"

        overlayScope.launch {
            val result = dataStoreManager.recordTaskLike(taskId, taskTitle)
            runOnMain {
                isTaskLiked = true
                applyLikedBadgeStyle()
                if (result.first) {
                    triggerCelebration("🪙 +5 COINS ADDED FOR YOUTUBE LIKE! 🎉")
                    WatchSessionRepository.addLog("Auto-detected genuine YouTube Like! +5 coins added (1-time reward).", LogType.SUCCESS)
                }
            }
        }
    }

    private fun handleCommentDetected() {
        if (currentCommentCount >= 2) return
        val taskId = WatchSessionRepository.activeTaskId.value ?: "default_task"
        val taskTitle = WatchSessionRepository.targetTaskTitle.value ?: "YouTube Video"

        overlayScope.launch {
            val result = dataStoreManager.recordTaskComment(taskId, taskTitle)
            runOnMain {
                if (result.first) {
                    currentCommentCount = (currentCommentCount + 1).coerceAtMost(2)
                    updateCommentBadge()
                    triggerCelebration("🪙 +5 COINS ADDED FOR YOUTUBE COMMENT! 🎉")
                    WatchSessionRepository.addLog("Auto-detected genuine YouTube Comment! +5 coins added (#$currentCommentCount).", LogType.SUCCESS)
                }
            }
        }
    }

    private fun applyLikedBadgeStyle() {
        likeBadgeView?.text = "✓ Liked (+5c)"
        likeBadgeView?.setTextColor(Color.parseColor("#10B981"))
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8 * density
            setColor(Color.parseColor("#064E3B")) // Emerald dark
            setStroke((1 * density).toInt(), Color.parseColor("#10B981"))
        }
        likeBadgeView?.background = bg
    }

    private fun updateCommentBadge() {
        if (currentCommentCount >= 2) {
            commentBadgeView?.text = "✓ Comments (+10c)"
            commentBadgeView?.setTextColor(Color.parseColor("#10B981"))
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * density
                setColor(Color.parseColor("#064E3B"))
                setStroke((1 * density).toInt(), Color.parseColor("#10B981"))
            }
            commentBadgeView?.background = bg
        } else if (currentCommentCount > 0) {
            commentBadgeView?.text = "💬 +5c ($currentCommentCount/2)"
        }
    }

    fun isOverlayAttached(): Boolean = isAttached

    @SuppressLint("SetTextI18n")
    fun updateProgress(
        watchedMillis: Long,
        requiredMillis: Long,
        milestone: WatchDurationTier?,
        isPaused: Boolean = false
    ) {
        runOnMain {
            if (!isAttached) {
                return@runOnMain
            }

            val watchedStr = TimeFormatter.formatMillisToMmSs(watchedMillis)
            val reqStr = TimeFormatter.formatMillisToMmSs(requiredMillis)
            val watchedSecs = (watchedMillis / 1000).toInt()

            if (isPaused) {
                timerTextView?.text = "⏸ $watchedStr / $reqStr"
                timerTextView?.setTextColor(Color.parseColor("#F59E0B"))
                (liveDotView?.background as? GradientDrawable)?.setColor(Color.parseColor("#F59E0B"))
            } else {
                timerTextView?.text = "▶ $watchedStr / $reqStr"
                timerTextView?.setTextColor(Color.WHITE)
                (liveDotView?.background as? GradientDrawable)?.setColor(Color.parseColor("#10B981"))
            }

            if (milestone != null) {
                milestoneBadgeTextView?.text = "🏆 +${milestone.coins}c"
                milestoneBadgeTextView?.setTextColor(Color.parseColor("#10B981"))

                if (milestone != lastCelebratedTier) {
                    lastCelebratedTier = milestone
                    triggerCelebration("🪙 +${milestone.coins} COINS UNLOCKED! 🎉")
                }
            } else {
                val remainSec = (180 - watchedSecs).coerceAtLeast(0)
                if (remainSec > 0) {
                    milestoneBadgeTextView?.text = if (isPaused) "Paused (${remainSec}s)" else "Goal: 3m (${remainSec}s)"
                    milestoneBadgeTextView?.setTextColor(Color.parseColor("#F59E0B"))
                } else {
                    milestoneBadgeTextView?.text = "3m Passed!"
                    milestoneBadgeTextView?.setTextColor(Color.parseColor("#10B981"))
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    fun showCoinAddedCelebration(coins: Int, message: String = "+$coins COINS ADDED!") {
        runOnMain {
            milestoneBadgeTextView?.text = "🏆 +${coins}c Added"
            milestoneBadgeTextView?.setTextColor(Color.parseColor("#10B981"))
            triggerCelebration("🪙 +$coins COINS ADDED! 🎉")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun triggerCelebration(message: String) {
        val container = celebrationContainer ?: return
        val text = celebrationText ?: return

        text.text = message
        container.visibility = View.VISIBLE
        container.alpha = 0f
        container.scaleX = 0.5f
        container.scaleY = 0.5f

        val scaleX = ObjectAnimator.ofFloat(container, "scaleX", 0.5f, 1.15f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(container, "scaleY", 0.5f, 1.15f, 1.0f)
        val alpha = ObjectAnimator.ofFloat(container, "alpha", 0f, 1.0f)

        val set = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            interpolator = OvershootInterpolator(1.4f)
            duration = 400
        }
        set.start()

        container.postDelayed({
            if (isAttached && container.visibility == View.VISIBLE) {
                val fadeOut = ObjectAnimator.ofFloat(container, "alpha", 1f, 0f).apply {
                    duration = 300
                }
                fadeOut.start()
                container.postDelayed({
                    container.visibility = View.GONE
                }, 300)
            }
        }, 3200)
    }

    @SuppressLint("SetTextI18n")
    fun showTaskIncompletePopup(message: String, onDismissed: (() -> Unit)? = null) {
        runOnMain {
            // Remove all floating timer pills and previous popups first
            removeAllGlobalViews(windowManager)
            overlayRootView?.let { root ->
                try {
                    windowManager.removeView(root)
                } catch (_: Exception) {}
            }
            overlayRootView = null
            isAttached = false

            incompletePopupView?.let { prev ->
                try {
                    windowManager.removeView(prev)
                } catch (_: Exception) {}
            }
            incompletePopupView = null

            if (!Settings.canDrawOverlays(context) || WatchSessionRepository.isAppInForeground) {
                onDismissed?.invoke()
                return@runOnMain
            }

            density = context.resources.displayMetrics.density
            val screenWidth = context.resources.displayMetrics.widthPixels.coerceAtLeast(600)
            val cardWidth = (screenWidth * 0.88f).toInt().coerceAtMost((360 * density).toInt())

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            val scrimRoot = FrameLayout(context).apply {
                setBackgroundColor(Color.parseColor("#B3000000"))
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }

            val dialogCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                val pad = (22 * density).toInt()
                setPadding(pad, pad, pad, pad)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 24 * density
                    setColor(Color.parseColor("#0F172A"))
                    setStroke((2 * density).toInt(), Color.parseColor("#EF4444"))
                }
                elevation = 24 * density
                layoutParams = FrameLayout.LayoutParams(
                    cardWidth,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER
                }
            }

            val iconBadge = TextView(context).apply {
                text = "⚠️"
                textSize = 28f
                gravity = Gravity.CENTER
                val badgeSize = (64 * density).toInt()
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#33EF4444"))
                }
                layoutParams = LinearLayout.LayoutParams(badgeSize, badgeSize).apply {
                    bottomMargin = (14 * density).toInt()
                }
            }
            dialogCard.addView(iconBadge)

            val titleTv = TextView(context).apply {
                text = "Task Incomplete!"
                setTextColor(Color.parseColor("#EF4444"))
                textSize = 20f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (8 * density).toInt()
                }
            }
            dialogCard.addView(titleTv)

            val lockBadgeTv = TextView(context).apply {
                text = "🔒 Locked for 12 Hours • Timer Stopped"
                setTextColor(Color.parseColor("#F87171"))
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding((12 * density).toInt(), (5 * density).toInt(), (12 * density).toInt(), (5 * density).toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8 * density
                    setColor(Color.parseColor("#26EF4444"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (12 * density).toInt()
                }
            }
            dialogCard.addView(lockBadgeTv)

            val msgTv = TextView(context).apply {
                text = message
                setTextColor(Color.parseColor("#E2E8F0"))
                textSize = 13.5f
                gravity = Gravity.CENTER
                setLineSpacing(4 * density, 1f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (18 * density).toInt()
                }
            }
            dialogCard.addView(msgTv)

            val okBtn = TextView(context).apply {
                text = "OK, Samjh Gaya"
                setTextColor(Color.BLACK)
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 12 * density
                    setColor(Color.parseColor("#F59E0B"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    dismissIncompletePopup()
                    WatchSessionRepository.dismissTaskIncompleteMessage()
                    onDismissed?.invoke()
                }
            }
            dialogCard.addView(okBtn)

            scrimRoot.addView(dialogCard)

            try {
                windowManager.addView(scrimRoot, params)
                globalAttachedViews.add(scrimRoot)
                incompletePopupView = scrimRoot
            } catch (_: Exception) {
                incompletePopupView = null
                onDismissed?.invoke()
            }
        }
    }

    fun dismissIncompletePopup() {
        runOnMain {
            incompletePopupView?.let { popup ->
                try {
                    windowManager.removeView(popup)
                } catch (_: Exception) {}
                globalAttachedViews.remove(popup)
            }
            incompletePopupView = null
        }
    }

    fun hideOverlay() {
        runOnMain {
            removeAllGlobalViews(windowManager)
            overlayRootView?.let { root ->
                try {
                    windowManager.removeView(root)
                } catch (_: Exception) {}
            }
            overlayRootView = null
            incompletePopupView?.let { popup ->
                try {
                    windowManager.removeView(popup)
                } catch (_: Exception) {}
            }
            incompletePopupView = null
            isAttached = false
            lastCelebratedTier = null
            currentCommentCount = 0
            isTaskLiked = false
        }
    }
}
