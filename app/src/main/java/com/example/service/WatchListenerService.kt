package com.example.service

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.LogType
import com.example.repository.WatchSessionRepository

class WatchListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "WatchListenerService"
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    }

    private var mediaSessionManager: MediaSessionManager? = null
    private var activeYouTubeController: MediaController? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            Log.d(TAG, "onMetadataChanged: title=$title, artist=$artist")
            WatchSessionRepository.onMediaMetadataChanged(title, artist)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            Log.d(TAG, "onPlaybackStateChanged: state=${state?.state}")
            WatchSessionRepository.onPlaybackStateChanged(state?.state)
        }

        override fun onSessionDestroyed() {
            Log.d(TAG, "onSessionDestroyed")
            WatchSessionRepository.onSessionDestroyed()
            activeYouTubeController = null
        }
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            mainHandler.post {
                handleActiveSessions(controllers)
            }
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        WatchSessionRepository.addLog("Notification listener connected", LogType.SUCCESS)
        initMediaSessionTracking()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        WatchSessionRepository.addLog("Notification listener disconnected", LogType.WARNING)
        cleanupMediaSessionTracking()
    }

    override fun onDestroy() {
        cleanupMediaSessionTracking()
        super.onDestroy()
    }

    private fun initMediaSessionTracking() {
        try {
            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val componentName = ComponentName(this, WatchListenerService::class.java)

            mediaSessionManager?.addOnActiveSessionsChangedListener(
                sessionsChangedListener,
                componentName
            )

            // Initial query of active sessions
            val currentControllers = mediaSessionManager?.getActiveSessions(componentName)
            handleActiveSessions(currentControllers)
        } catch (e: SecurityException) {
            WatchSessionRepository.addLog(
                "SecurityException querying media sessions: ${e.message}",
                LogType.ERROR
            )
        } catch (e: Exception) {
            WatchSessionRepository.addLog(
                "Error initializing media sessions: ${e.message}",
                LogType.ERROR
            )
        }
    }

    private fun handleActiveSessions(controllers: List<MediaController>?) {
        val ytController = controllers?.firstOrNull { it.packageName == YOUTUBE_PACKAGE }

        if (ytController != null) {
            if (activeYouTubeController != ytController) {
                // Detach previous if different
                try {
                    activeYouTubeController?.unregisterCallback(mediaControllerCallback)
                } catch (_: Exception) {}

                activeYouTubeController = ytController
                WatchSessionRepository.addLog("YouTube active media session detected", LogType.SUCCESS)

                try {
                    ytController.registerCallback(mediaControllerCallback, mainHandler)

                    // Read current metadata & playback state immediately
                    val metadata = ytController.metadata
                    val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    WatchSessionRepository.onMediaMetadataChanged(title, artist)

                    val playbackState = ytController.playbackState
                    WatchSessionRepository.onPlaybackStateChanged(playbackState?.state)
                } catch (e: Exception) {
                    WatchSessionRepository.addLog("Error reading initial media metadata: ${e.message}", LogType.WARNING)
                }
            }
        } else {
            if (activeYouTubeController != null) {
                try {
                    activeYouTubeController?.unregisterCallback(mediaControllerCallback)
                } catch (_: Exception) {}
                activeYouTubeController = null
                WatchSessionRepository.onSessionDestroyed()
            }
        }
    }

    private fun cleanupMediaSessionTracking() {
        try {
            activeYouTubeController?.unregisterCallback(mediaControllerCallback)
            activeYouTubeController = null
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (_: Exception) {}
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn?.packageName == YOUTUBE_PACKAGE) {
            val extras = sbn.notification?.extras ?: return
            val hasMediaSession = extras.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION) ||
                    sbn.notification?.category == android.app.Notification.CATEGORY_TRANSPORT
            if (!hasMediaSession) return
            val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
            val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
            if (!title.isNullOrBlank()) {
                WatchSessionRepository.onYouTubeNotificationPosted(title, text)
            }
        }
    }
}
