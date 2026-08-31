package com.lumabeat.app.media

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.createBitmap

class MediaNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        activeNotifications
            .asSequence()
            .filter(::isMediaNotification)
            .maxByOrNull(StatusBarNotification::getPostTime)
            ?.let(::publishArtworkColors)
    }

    override fun onNotificationPosted(notification: StatusBarNotification) {
        if (isMediaNotification(notification)) publishArtworkColors(notification)
    }

    private fun isMediaNotification(notification: StatusBarNotification): Boolean =
        notification.packageName != packageName &&
            notification.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)

    private fun publishArtworkColors(statusBarNotification: StatusBarNotification) {
        if (!mediaColorsEnabled()) return
        val artwork = mediaSessionArtwork(statusBarNotification.notification)
            ?: notificationArtwork(statusBarNotification.notification)
            ?: return
        val sampled = samplePixels(artwork)
        val colors = DominantColorExtractor.extract(sampled)
        if (colors.isNotEmpty()) MediaColorRepository.publish(colors)
    }

    @Suppress("DEPRECATION")
    private fun mediaSessionArtwork(notification: Notification): Bitmap? {
        val token = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notification.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            notification.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
        } ?: return null
        val metadata = runCatching { MediaController(this, token).metadata }.getOrNull() ?: return null
        return metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.description.iconBitmap
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

    private companion object {
        const val PREFERENCES_NAME = "lumabeat_preferences"
        const val MEDIA_COLORS_KEY = "media_colors_enabled"
        const val SAMPLE_SIZE = 64
    }
}
