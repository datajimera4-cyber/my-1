package com.example.util

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.service.WatchListenerService

object PermissionHelper {

    const val YOUTUBE_PACKAGE = "com.google.android.youtube"

    /**
     * Checks if this app has been granted NotificationListenerService access.
     */
    fun isNotificationAccessGranted(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false

        val myService = ComponentName(context, WatchListenerService::class.java).flattenToString()
        return flat.contains(myService) || flat.contains(context.packageName)
    }

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
     */
    fun areEssentialPermissionsGranted(context: Context): Boolean {
        return isNotificationAccessGranted(context) && isNotificationPermissionGranted(context)
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
     * Launches the video URL in the YouTube app with natural search referrer headers.
     * Uses vnd.youtube:$id when installed so YouTube opens the actual full-screen video player
     * instead of inline feed preview.
     */
    fun openVideoIntent(context: Context, videoUrl: String, searchTitle: String? = null): Intent {
        val videoId = TitleMatcher.extractVideoId(videoUrl)
        val uri = if (!videoId.isNullOrBlank() && isYouTubeAppInstalled(context)) {
            Uri.parse("vnd.youtube:$videoId")
        } else {
            Uri.parse(videoUrl)
        }

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://com.google.android.youtube/search"))
            if (!searchTitle.isNullOrBlank()) {
                putExtra("query", searchTitle)
                putExtra("search_query", searchTitle)
            }
        }

        if (isYouTubeAppInstalled(context)) {
            intent.setPackage(YOUTUBE_PACKAGE)
        }
        return intent
    }

    /**
     * Direct YouTube search intent (opens YouTube app straight to search results page).
     * Uses ACTION_SEARCH if supported, falls back to search URI.
     */
    fun openYouTubeSearchIntent(context: Context, query: String): Intent {
        val cleanQuery = query.replace("\"", "").trim()

        if (isYouTubeAppInstalled(context)) {
            val searchActionIntent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage(YOUTUBE_PACKAGE)
                putExtra("query", cleanQuery)
                putExtra(android.app.SearchManager.QUERY, cleanQuery)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (searchActionIntent.resolveActivity(context.packageManager) != null) {
                return searchActionIntent
            }
        }

        val searchUri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(cleanQuery)}")
        val intent = Intent(Intent.ACTION_VIEW, searchUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("query", cleanQuery)
            putExtra("search_query", cleanQuery)
        }
        if (isYouTubeAppInstalled(context)) {
            intent.setPackage(YOUTUBE_PACKAGE)
        }
        return intent
    }
}
