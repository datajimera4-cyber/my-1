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
import com.example.data.WatchDurationTier
import com.example.util.TimeFormatter

class FloatingTimerOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayRootView: FrameLayout? = null
    private var timerTextView: TextView? = null
    private var milestoneBadgeTextView: TextView? = null
    private var celebrationContainer: LinearLayout? = null
    private var celebrationText: TextView? = null
    private var lastCelebratedTier: WatchDurationTier? = null

    private var isAttached = false

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    fun showOverlay() {
        if (isAttached || !Settings.canDrawOverlays(context)) return

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
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 180
        }

        val root = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }

        // Main pill layout
        val pillLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val density = context.resources.displayMetrics.density
            setPadding((12 * density).toInt(), (8 * density).toInt(), (14 * density).toInt(), (8 * density).toInt())

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24 * density
                setColor(Color.parseColor("#1E293B")) // Slate 800
                setStroke((1.5f * density).toInt(), Color.parseColor("#F59E0B")) // Amber Primary
            }
            background = bg
            elevation = 12 * density
        }

        val density = context.resources.displayMetrics.density

        // Green live dot
        val liveDot = View(context).apply {
            val dotSize = (10 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                rightMargin = (8 * density).toInt()
            }
            val dotBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#10B981")) // Green
            }
            background = dotBg
        }
        pillLayout.addView(liveDot)

        // Timer text
        val timerTv = TextView(context).apply {
            text = "00:00 / 00:00"
            setTextColor(Color.WHITE)
            textSize = 13f
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

        // Milestone badge text
        val milestoneTv = TextView(context).apply {
            text = "(Min 3m)"
            setTextColor(Color.parseColor("#F59E0B"))
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val badgeBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6 * density
                setColor(Color.parseColor("#334155"))
            }
            background = badgeBg
            setPadding((6 * density).toInt(), (2 * density).toInt(), (6 * density).toInt(), (2 * density).toInt())
        }
        pillLayout.addView(milestoneTv)
        this.milestoneBadgeTextView = milestoneTv

        // Close 'x' button
        val closeBtn = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.parseColor("#94A3B8"))
            val iconSize = (16 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                leftMargin = (10 * density).toInt()
            }
            setOnClickListener {
                hideOverlay()
            }
        }
        pillLayout.addView(closeBtn)

        // Celebration popup banner container (initially hidden)
        val celebrationBox = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            val cBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14 * density
                setColor(Color.parseColor("#065F46")) // Rich emerald green
                setStroke((2 * density).toInt(), Color.parseColor("#F59E0B"))
            }
            background = cBg
            setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = -(36 * density).toInt()
            }
        }

        val celebTv = TextView(context).apply {
            text = "🎉 +10 COINS UNLOCKED!"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        celebrationBox.addView(celebTv)
        this.celebrationContainer = celebrationBox
        this.celebrationText = celebTv

        root.addView(pillLayout)
        root.addView(celebrationBox)

        // Dragging & Tap Touch Listener
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
                    params.x = initialX + deltaX
                    params.y = initialY + deltaY
                    try {
                        windowManager.updateViewLayout(root, params)
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        // Launch app
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
        } catch (_: Exception) {
            isAttached = false
        }
    }

    @SuppressLint("SetTextI18n")
    fun updateProgress(watchedMillis: Long, requiredMillis: Long, milestone: WatchDurationTier?) {
        if (!isAttached) {
            if (Settings.canDrawOverlays(context)) {
                showOverlay()
            }
            if (!isAttached) return
        }

        val watchedStr = TimeFormatter.formatMillisToMmSs(watchedMillis)
        val reqStr = TimeFormatter.formatMillisToMmSs(requiredMillis)
        val watchedSecs = (watchedMillis / 1000).toInt()

        timerTextView?.text = "$watchedStr / $reqStr"

        if (milestone != null) {
            milestoneBadgeTextView?.text = "🏆 +${milestone.coins}c"
            milestoneBadgeTextView?.setTextColor(Color.parseColor("#10B981"))

            // Trigger celebration animation on new milestone
            if (milestone != lastCelebratedTier) {
                lastCelebratedTier = milestone
                triggerCelebrationAnimation(milestone)
            }
        } else {
            val remainSec = (180 - watchedSecs).coerceAtLeast(0)
            if (remainSec > 0) {
                milestoneBadgeTextView?.text = "Min 3m (${remainSec}s)"
                milestoneBadgeTextView?.setTextColor(Color.parseColor("#F59E0B"))
            } else {
                milestoneBadgeTextView?.text = "3m Reached!"
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun triggerCelebrationAnimation(milestone: WatchDurationTier) {
        val container = celebrationContainer ?: return
        val text = celebrationText ?: return

        text.text = "🪙 +${milestone.coins} COINS ADDED! 🎉"
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
            duration = 450
        }
        set.start()

        // Auto hide celebration after 4 seconds
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
        }, 4000)
    }

    fun hideOverlay() {
        if (!isAttached || overlayRootView == null) return
        try {
            windowManager.removeView(overlayRootView)
        } catch (_: Exception) {}
        overlayRootView = null
        isAttached = false
        lastCelebratedTier = null
    }
}
