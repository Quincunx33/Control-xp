package com.example.nexusremote.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.nexusremote.commands.CommandExecutor
import com.example.nexusremote.network.NexusWebSocketClient
import com.example.nexusremote.permissions.PermissionManager
import com.example.nexusremote.security.SessionManager
import com.example.nexusremote.telemetry.DeviceTelemetryCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NexusRemoteService : Service() {

    companion object {
        private const val CHANNEL_ID = "nexus_remote_service_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "NexusRemoteService"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private var instance: NexusRemoteService? = null

        fun start(context: Context) {
            try {
                val intent = Intent(context, NexusRemoteService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Throwable) {
                android.util.Log.w(TAG, "Cannot start foreground service yet (system restriction): ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, NexusRemoteService::class.java)
                context.stopService(intent)
            } catch (e: Throwable) {
                android.util.Log.w(TAG, "Error stopping service: ${e.message}")
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    lateinit var sessionManager: SessionManager
    lateinit var telemetryCollector: DeviceTelemetryCollector
    lateinit var permissionManager: PermissionManager
    lateinit var commandExecutor: CommandExecutor
    lateinit var webSocketClient: NexusWebSocketClient

    override fun onCreate() {
        super.onCreate()
        instance = this
        _isRunning.value = true

        sessionManager = SessionManager(this)
        telemetryCollector = DeviceTelemetryCollector(this)
        permissionManager = PermissionManager(this)
        
        commandExecutor = CommandExecutor(
            context = this,
            telemetryCollector = telemetryCollector,
            onScreenShareRequested = { _ -> }
        )

        webSocketClient = com.example.nexusremote.network.NexusWebSocketProvider.getClient(
            this,
            telemetryCollector,
            permissionManager,
            commandExecutor
        )

        try {
            createNotificationChannel()
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "Failed to start foreground notification: ${e.message}", e)
            // If foreground start fails due to Android 14 restrictions, stop self cleanly rather than crash process
            stopSelf()
            return
        }
        
        connectIfRegistered()
    }

    private fun connectIfRegistered() {
        try {
            if (sessionManager.isRegistered) {
                val devId = sessionManager.deviceId
                val devKey = sessionManager.deviceKey
                if (devId != null && devKey != null) {
                    webSocketClient.connect(sessionManager.serverUrl, devId, devKey)
                }
            }
        } catch (e: Throwable) {
            android.util.Log.w(TAG, "Error during connectIfRegistered: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Control-XP Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains real-time link with Control-XP management console"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Control-XP Active")
            .setContentText("Device is securely connected in background")
            .setSmallIcon(com.example.R.drawable.ic_stat_nexus)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            webSocketClient.disconnect()
        } catch (_: Throwable) {}
        _isRunning.value = false
        instance = null
        super.onDestroy()
    }
}
