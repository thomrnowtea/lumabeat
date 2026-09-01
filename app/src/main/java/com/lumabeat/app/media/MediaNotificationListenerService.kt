package com.lumabeat.app.media

import android.app.Notification
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.graphics.createBitmap

class MediaNotificationListenerService : NotificationListenerService() {
    private val listenerComponent by lazy {
        ComponentName(this, MediaNotificationListenerService::class.java)
    }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val mediaSessionManager by lazy { getSystemService(MediaSessionManager::class.java) }
    private val controllerRegistrations = mutableMapOf<MediaSession.Token, ControllerRegistration>()
    private var sessionListenerRegistered = false

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateMediaControllers(controllers.orEmpty())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val controllers = activeMediaControllers()
        registerSessionListener()
        val sessionArtworkPublished = updateMediaControllers(controllers)
        if (!sessionArtworkPublished) publishMostRecentNotification(controllers)
    }

    override fun onListenerDisconnected() {
        stopSessionMonitoring()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        stopSessionMonitoring()
        super.onDestroy()
    }

    override fun onNotificationPosted(notification: StatusBarNotification) {
        val controllers = activeMediaControllers()
        val matchingController = controllers.firstOrNull { controller ->
            controller.packageName == notification.packageName && isActivePlayback(controller)
        }
        if (matchingController != null && publishControllerArtwork(matchingController)) return
        if (isMediaNotification(notification, activeMediaPackages(controllers))) {
            publishNotificationArtwork(notification)
        }
    }

    private fun registerSessionListener() {
        if (sessionListenerRegistered) return
        runCatching {
            mediaSessionManager.addOnActiveSessionsChangedListener(
                activeSessionsListener,
                listenerComponent,
                mainHandler,
            )
            sessionListenerRegistered = true
        }.onFailure { error ->
            Log.w(LOG_TAG, "Unable to monitor active media sessions.", error)
        }
    }

    private fun updateMediaControllers(controllers: List<MediaController>): Boolean {
        val activeTokens = controllers.mapTo(mutableSetOf(), MediaController::getSessionToken)
        controllerRegistrations.entries.removeAll { (token, registration) ->
            if (token in activeTokens) return@removeAll false
            registration.controller.unregisterCallback(registration.callback)
            true
        }
        controllers.forEach(::registerController)
        return controllers.firstOrNull(::isActivePlayback)?.let(::publishControllerArtwork) ?: false
    }

    private fun registerController(controller: MediaController) {
        val token = controller.sessionToken
        if (token in controllerRegistrations) return
        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                if (metadata != null && isActivePlayback(controller)) publishControllerArtwork(controller)
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                if (state?.state in ACTIVE_PLAYBACK_STATES) publishControllerArtwork(controller)
            }

            override fun onSessionDestroyed() {
                unregisterController(token)
            }
        }
        controller.registerCallback(callback, mainHandler)
        controllerRegistrations[token] = ControllerRegistration(controller, callback)
    }

    private fun unregisterController(token: MediaSession.Token) {
        val registration = controllerRegistrations.remove(token) ?: return
        registration.controller.unregisterCallback(registration.callback)
    }

    private fun stopSessionMonitoring() {
        if (sessionListenerRegistered) {
            runCatching { mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsListener) }
            sessionListenerRegistered = false
        }
        controllerRegistrations.values.forEach { registration ->
            registration.controller.unregisterCallback(registration.callback)
        }
        controllerRegistrations.clear()
    }

    private fun publishMostRecentNotification(controllers: List<MediaController>) {
        val mediaPackages = activeMediaPackages(controllers)
        activeNotifications
            .asSequence()
            .filter { notification -> isMediaNotification(notification, mediaPackages) }
            .maxByOrNull(StatusBarNotification::getPostTime)
            ?.let(::publishNotificationArtwork)
    }

    private fun isMediaNotification(
        notification: StatusBarNotification,
        activeMediaPackages: Set<String>,
    ): Boolean = notification.packageName != packageName && (
        notification.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
            notification.packageName in activeMediaPackages
        )

    private fun activeMediaControllers(): List<MediaController> = runCatching {
        mediaSessionManager.getActiveSessions(listenerComponent)
    }.onFailure { error ->
        Log.w(LOG_TAG, "Unable to read active media sessions.", error)
    }.getOrDefault(emptyList())

    private fun activeMediaPackages(controllers: List<MediaController>): Set<String> = controllers
        .asSequence()
        .filter(::isActivePlayback)
        .map(MediaController::getPackageName)
        .toSet()

    private fun isActivePlayback(controller: MediaController): Boolean =
        controller.playbackState?.state in ACTIVE_PLAYBACK_STATES

    private fun publishControllerArtwork(controller: MediaController): Boolean = publishArtwork(
        artwork = metadataArtwork(controller.metadata),
        sourcePackage = controller.packageName,
        source = "media session",
    )

    private fun publishNotificationArtwork(statusBarNotification: StatusBarNotification): Boolean {
        val notification = statusBarNotification.notification
        val sessionArtwork = mediaSessionArtwork(notification)
        return if (sessionArtwork != null) {
            publishArtwork(sessionArtwork, statusBarNotification.packageName, "notification media session")
        } else {
            publishArtwork(
                notificationArtwork(notification),
                statusBarNotification.packageName,
                "notification large icon",
            )
        }
    }

    private fun publishArtwork(artwork: Bitmap?, sourcePackage: String, source: String): Boolean {
        if (!mediaColorsEnabled() || artwork == null) return false
        val colors = DominantColorExtractor.extract(samplePixels(artwork))
        if (colors.isEmpty()) {
            Log.w(LOG_TAG, "Artwork from $sourcePackage produced no usable colors.")
            return false
        }
        MediaColorRepository.publish(colors)
        val palette = colors.joinToString(separator = " | ") { color ->
            "${color.red},${color.green},${color.blue}"
        }
        Log.i(LOG_TAG, "Published ${colors.size} artwork colors from $sourcePackage via $source: $palette")
        return true
    }

    @Suppress("DEPRECATION")
    private fun mediaSessionArtwork(notification: Notification): Bitmap? {
        val token = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notification.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            notification.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
        } ?: return null
        val metadata = runCatching { MediaController(this, token).metadata }.getOrNull()
        return metadataArtwork(metadata)
    }

    private fun metadataArtwork(metadata: MediaMetadata?): Bitmap? = metadata?.run {
        getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: description.iconBitmap
    }

    private fun notificationArtwork(notification: Notification): Bitmap? =
        notification.getLargeIcon()?.loadDrawable(this)?.toBitmap()

    private fun Drawable.toBitmap(): Bitmap {
        if (this is BitmapDrawable && bitmap != null) return bitmap
        val width = intrinsicWidth.takeIf { it > 0 } ?: SAMPLE_SIZE
        val height = intrinsicHeight.takeIf { it > 0 } ?: SAMPLE_SIZE
        return createBitmap(width, height).also { bitmap ->
            val canvas = Canvas(bitmap)
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
        }
    }

    private fun samplePixels(source: Bitmap): IntArray {
        val scaled = Bitmap.createScaledBitmap(source, SAMPLE_SIZE, SAMPLE_SIZE, true)
        return IntArray(SAMPLE_SIZE * SAMPLE_SIZE).also { pixels ->
            scaled.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
            if (scaled !== source) scaled.recycle()
        }
    }

    private fun mediaColorsEnabled(): Boolean = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        .getBoolean(MEDIA_COLORS_KEY, false)

    private data class ControllerRegistration(
        val controller: MediaController,
        val callback: MediaController.Callback,
    )

    private companion object {
        const val PREFERENCES_NAME = "lumabeat_preferences"
        const val MEDIA_COLORS_KEY = "media_colors_enabled"
        const val SAMPLE_SIZE = 64
        const val LOG_TAG = "LumaBeatMedia"
        val ACTIVE_PLAYBACK_STATES = setOf(
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING,
        )
    }
}
