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

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private var instance: NexusRemoteService? = null

        fun start(context: Context) {
            val intent = Intent(context, NexusRemoteService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, NexusRemoteService::class.java)
            context.stopService(intent)
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
            onScreenShareRequested = { sessionId ->
                // In a service, we don't have a direct way to show UI, 
                // but we could send a broadcast or update a shared state.
                // For now, we'll rely on the UI observing the state if it's open.
            }
        )

        webSocketClient = com.example.nexusremote.network.NexusWebSocketProvider.getClient(
            this,
            telemetryCollector,
            permissionManager,
            commandExecutor
        )

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        connectIfRegistered()
    }

    private fun connectIfRegistered() {
        if (sessionManager.isRegistered) {
            val devId = sessionManager.deviceId
            val devKey = sessionManager.deviceKey
            if (devId != null && devKey != null) {
                webSocketClient.connect(sessionManager.serverUrl, devId, devKey)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Nexus Remote Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nexus Remote Active")
            .setContentText("Maintaining secure connection in background")
            .setSmallIcon(android.R.drawable.ic_menu_compass) // Use a better icon if available
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        webSocketClient.disconnect()
        _isRunning.value = false
        instance = null
        super.onDestroy()
    }
}
