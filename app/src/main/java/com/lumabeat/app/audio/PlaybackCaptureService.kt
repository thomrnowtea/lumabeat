package com.lumabeat.app.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lumabeat.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

class PlaybackCaptureService : Service() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            createNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
        isForeground.value = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isForeground.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Music reactive lighting",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps system audio analysis active"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentTitle("LumaBeat is following the audio")
        .setContentText("WiZ lights are reacting to percussion")
        .setOngoing(true)
        .setSilent(true)
        .build()

    companion object {
        private const val CHANNEL_ID = "playback_capture"
        private const val NOTIFICATION_ID = 1001
        private val isForeground = MutableStateFlow(false)

        fun start(context: Context) {
            isForeground.value = false
            ContextCompat.startForegroundService(
                context,
                Intent(context, PlaybackCaptureService::class.java),
            )
        }

        suspend fun awaitForeground() {
            isForeground.first { it }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlaybackCaptureService::class.java))
        }
    }
}
