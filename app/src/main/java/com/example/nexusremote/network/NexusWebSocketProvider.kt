package com.example.nexusremote.network

import android.content.Context
import com.example.nexusremote.commands.CommandExecutor
import com.example.nexusremote.permissions.PermissionManager
import com.example.nexusremote.telemetry.DeviceTelemetryCollector

object NexusWebSocketProvider {
    private var client: NexusWebSocketClient? = null

    fun getClient(
        context: Context,
        telemetryCollector: DeviceTelemetryCollector,
        permissionManager: PermissionManager,
        commandExecutor: CommandExecutor
    ): NexusWebSocketClient {
        if (client == null) {
            client = NexusWebSocketClient(
                telemetryCollector = telemetryCollector,
                permissionManager = permissionManager,
                commandExecutor = commandExecutor
            )
        }
        return client!!
    }

    // Overloaded for when we know it's already initialized (e.g. from ViewModel after Service started)
    fun getClient(): NexusWebSocketClient {
        return client ?: throw IllegalStateException("NexusWebSocketClient not initialized")
    }

    fun isInitialized(): Boolean = client != null
}
