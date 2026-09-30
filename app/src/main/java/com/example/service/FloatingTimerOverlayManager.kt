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
            // Clean up any stale view before re-attaching
            if (isAttached || overlayRootView != null) {
                try {
                    overlayRootView?.let { windowManager.removeView(it) }
                } catch (_: Exception) {}
                overlayRootView = null
                isAttached = false
            }

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

            // Auto Like Monitor Badge (NOT a manual clickable button - automatically detects YouTube like!)
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
            }
            pillLayout.addView(likeBadge)
            this.likeBadgeView = likeBadge

            // Auto Comment Monitor Badge (NOT a manual clickable button - automatically detects YouTube comment!)
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

            // Dragging & Tap Listener
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var isClick = true

            pillLayout.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                            isClick = false
                        }
                        params.x = (initialX + deltaX).coerceAtLeast(0)
                        params.y = (initialY + deltaY).coerceAtLeast(30)
                        try {
                            windowManager.updateViewLayout(root, params)
                        } catch (_: Exception) {}
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            // Tapping the overlay pill brings Kingo King app back to foreground
                            val launchIntent = Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            context.startActivity(launchIntent)
                        }
                        true
                    }
                    else -> false
                }
            }

            try {
                windowManager.addView(root, params)
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
                handleLikeDetected()
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
                        if (alreadyLiked) {
                            isTaskLiked = true
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
     * When user likes video in YouTube, accessibility detects it immediately.
     * We instantly award +5 coins, update DataStore, update badge style to emerald "✓ Liked (+5c)",
     * and trigger celebration!
     */
    private fun handleLikeDetected() {
        val taskId = WatchSessionRepository.activeTaskId.value ?: "default_task"
        val taskTitle = WatchSessionRepository.targetTaskTitle.value ?: "YouTube Video"

        overlayScope.launch {
            val result = dataStoreManager.recordTaskLike(taskId, taskTitle)
            runOnMain {
                isTaskLiked = true
                applyLikedBadgeStyle()
                if (result.first) {
                    triggerCelebration("🪙 +5 COINS ADDED FOR LIKE! 🎉")
                    WatchSessionRepository.addLog("Auto-detected Like! +5 coins added.", LogType.SUCCESS)
                } else {
                    triggerCelebration("✓ Video Liked (+5c)")
                }
            }
        }
    }

    private fun handleCommentDetected() {
        val taskId = WatchSessionRepository.activeTaskId.value ?: "default_task"
        val taskTitle = WatchSessionRepository.targetTaskTitle.value ?: "YouTube Video"

        overlayScope.launch {
            val result = dataStoreManager.recordTaskComment(taskId, taskTitle)
            runOnMain {
                currentCommentCount = (currentCommentCount + 1).coerceAtMost(2)
                updateCommentBadge()
                if (result.first) {
                    triggerCelebration("🪙 +5 COINS ADDED FOR COMMENT! 🎉")
                    WatchSessionRepository.addLog("Auto-detected Comment! +5 coins added (#$currentCommentCount).", LogType.SUCCESS)
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

    fun hideOverlay() {
        runOnMain {
            overlayRootView?.let { root ->
                try {
                    windowManager.removeView(root)
                } catch (_: Exception) {}
            }
            overlayRootView = null
            isAttached = false
            lastCelebratedTier = null
            currentCommentCount = 0
            isTaskLiked = false
        }
    }
}
