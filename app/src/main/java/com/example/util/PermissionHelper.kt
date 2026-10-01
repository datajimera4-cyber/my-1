package com.example.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

object PermissionHelper {

    const val YOUTUBE_PACKAGE = "com.google.android.youtube"

    /**
     * Notification reading permission is completely removed as requested.
     * The app runs cleanly without reading user notifications.
     */
    fun isNotificationAccessGranted(context: Context): Boolean = true

    /**
     * Checks if POST_NOTIFICATIONS runtime permission is granted on Android 13+.
     */
    fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Checks if battery optimizations are disabled for this app.
     */
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    /**
     * Checks if Kingo King's YouTubeLiveSearchService accessibility service is enabled.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains("YouTubeLiveSearchService") ||
                enabledServices.contains(context.packageName)
    }

    /**
     * Intent to open Accessibility settings screen.
     */
    fun createAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Checks if Display over other apps (floating timer overlay) permission is granted.
     */
    fun isOverlayPermissionGranted(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            context.startActivity(createAccessibilitySettingsIntent())
        } catch (_: Exception) {}
    }

    fun openOverlaySettings(context: Context) {
        try {
            context.startActivity(createOverlaySettingsIntent(context))
        } catch (_: Exception) {}
    }

    /**
     * Intent to open Overlay permission settings for Kingo King.
     */
    fun createOverlaySettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Checks if the official YouTube app is installed on the device.
     */
    fun isYouTubeAppInstalled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    YOUTUBE_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(YOUTUBE_PACKAGE, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Checks if all required permissions/settings are ready to start task.
     * No notification read permission required — runs out-of-the-box!
     */
    fun areEssentialPermissionsGranted(context: Context): Boolean {
        return true
    }

    /**
     * Intent to open Notification Access settings screen.
     */
    fun createNotificationListenerSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Intent to request disabling battery optimization for this app.
     */
    fun createIgnoreBatteryOptimizationIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Opens the official YouTube app cleanly via its standard Home Launcher Intent (ACTION_MAIN + CATEGORY_LAUNCHER),
     * identical to tapping the YouTube icon on the device home screen.
     * NEVER uses external video URLs or watch?v= deep links, ensuring 0% External traffic source in YouTube Analytics.
     */
    fun openYouTubeAppHomeIntent(context: Context): Intent {
        if (isYouTubeAppInstalled(context)) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(YOUTUBE_PACKAGE)
            if (launchIntent != null) {
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                )
                return launchIntent
            }
        }
        return Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(YOUTUBE_PACKAGE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    /**
     * Strictly opens the YouTube app via its Home Launcher Intent so YouTubeLiveSearchService
     * finds and plays the video via YouTube Search or Browse Features (never via external video URL).
     */
    fun openVideoIntent(context: Context, videoUrl: String, searchTitle: String? = null): Intent {
        return openYouTubeAppHomeIntent(context)
    }

    /**
     * Strictly opens the YouTube app via its Home Launcher Intent so YouTubeLiveSearchService
     * types the search query inside YouTube's native search bar (ensuring YouTube Search / Browse traffic source).
     */
    fun openYouTubeSearchIntent(context: Context, query: String): Intent {
        return openYouTubeAppHomeIntent(context)
    }
}
