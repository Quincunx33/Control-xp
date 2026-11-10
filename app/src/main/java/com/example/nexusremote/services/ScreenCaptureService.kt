package com.example.nexusremote.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class ScreenCaptureService : Service() {

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopScreenCapture()
      return START_NOT_STICKY
    }

    try {
      val notification = buildForegroundNotification()

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
      } else {
        startForeground(NOTIFICATION_ID, notification)
      }
      isStreaming = true
    } catch (e: Throwable) {
      android.util.Log.e("ScreenCaptureService", "Failed to startForeground: ${e.message}", e)
      stopSelf()
      return START_NOT_STICKY
    }
    return START_STICKY
  }

  private fun stopScreenCapture() {
    isStreaming = false
    try {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } catch (_: Throwable) {}
    stopSelf()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Control-XP Screen Streaming",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Indicates an active user-approved screen streaming session"
      }
      val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      nm?.createNotificationChannel(channel)
    }
  }

  private fun buildForegroundNotification(): Notification {
    val openIntent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val openPendingIntent = PendingIntent.getActivity(
      this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
      action = ACTION_STOP
    }
    val stopPendingIntent = PendingIntent.getService(
      this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(com.example.R.drawable.ic_stat_screen_share)
      .setContentTitle("Control-XP Screen Sharing Active")
      .setContentText("Display is being streamed to authorized management console")
      .setContentIntent(openPendingIntent)
      .setOngoing(true)
      .addAction(com.example.R.drawable.ic_stat_nexus, "Stop Sharing", stopPendingIntent)
      .build()
  }

  override fun onDestroy() {
    isStreaming = false
    super.onDestroy()
  }

  companion object {
    const val CHANNEL_ID = "nexus_screen_stream_channel"
    const val NOTIFICATION_ID = 3001
    const val ACTION_STOP = "com.example.nexusremote.ACTION_STOP_STREAM"

    var isStreaming = false
      private set
  }
}
