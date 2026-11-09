package com.example.nexusremote.network

import android.util.Log
import com.example.nexusremote.commands.CommandExecutor
import com.example.nexusremote.permissions.PermissionManager
import com.example.nexusremote.telemetry.DeviceTelemetryCollector
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class NexusWebSocketClient(
  private val telemetryCollector: DeviceTelemetryCollector,
  private val permissionManager: PermissionManager,
  private val commandExecutor: CommandExecutor
) {
  private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
  private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
  private val mapAdapter = moshi.adapter<Map<String, Any>>(mapType)

  private val scope = CoroutineScope(Dispatchers.IO + Job())
  private var heartbeatJob: Job? = null

  private var webSocket: WebSocket? = null
  private var isManuallyClosed = false

  private val _connectionState = MutableStateFlow("DISCONNECTED") // CONNECTING, ONLINE, OFFLINE
  val connectionState: StateFlow<String> = _connectionState.asStateFlow()

  private val _lastLog = MutableStateFlow<String>("Agent initialized")
  val lastLog: StateFlow<String> = _lastLog.asStateFlow()

  var onWebRtcOfferReceived: ((Map<String, Any>) -> Unit)? = null

  fun connect(serverUrl: String, deviceId: String, deviceKey: String) {
    disconnect()
    isManuallyClosed = false
    _connectionState.value = "CONNECTING"
    _lastLog.value = "Connecting to $serverUrl..."

    val wsUrl = serverUrl
      .replace("http://", "ws://")
      .replace("https://", "wss://")
      .removeSuffix("/") + "/ws"

    val client = OkHttpClient.Builder()
      .readTimeout(0, TimeUnit.MILLISECONDS)
      .pingInterval(15, TimeUnit.SECONDS)
      .build()

    val request = Request.Builder().url(wsUrl).build()

    webSocket = client.newWebSocket(request, object : WebSocketListener() {
      override fun onOpen(ws: WebSocket, response: Response) {
        _lastLog.value = "WebSocket opened. Authenticating..."
        // Send authentication frame
        val authFrame = mapOf(
          "type" to "auth",
          "device_id" to deviceId,
          "device_key" to deviceKey
        )
        ws.send(mapAdapter.toJson(authFrame))
      }

      override fun onMessage(ws: WebSocket, text: String) {
        handleIncomingMessage(ws, text, deviceId)
      }

      override fun onClosed(ws: WebSocket, code: Int, reason: String) {
        _connectionState.value = "OFFLINE"
        _lastLog.value = "Connection closed ($code: $reason)"
        stopHeartbeat()
        scheduleReconnect(serverUrl, deviceId, deviceKey)
      }

      override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
        _connectionState.value = "OFFLINE"
        _lastLog.value = "Connection failed: ${t.localizedMessage ?: "Unknown error"}"
        stopHeartbeat()
        scheduleReconnect(serverUrl, deviceId, deviceKey)
      }
    })
  }

  private fun handleIncomingMessage(ws: WebSocket, text: String, deviceId: String) {
    try {
      val data = mapAdapter.fromJson(text) ?: return
      val type = data["type"] as? String ?: return

      when (type) {
        "auth_ok" -> {
          _connectionState.value = "ONLINE"
          _lastLog.value = "Authenticated with backend. Link established."
          startHeartbeat(deviceId)
          sendTelemetryUpdate(deviceId)
        }

        "auth_error" -> {
          _connectionState.value = "OFFLINE"
          _lastLog.value = "Authentication rejected: ${data["message"]}"
        }

        "command" -> {
          val cmdId = data["command_id"] as? String ?: ""
          val cmdType = data["command_type"] as? String ?: ""
          @Suppress("UNCHECKED_CAST")
          val params = (data["params"] as? Map<String, Any>) ?: emptyMap()

          _lastLog.value = "Received command: $cmdType ($cmdId)"

          // Execute on background
          scope.launch {
            val (result, error) = commandExecutor.execute(cmdType, params)
            val resultFrame = mutableMapOf<String, Any>(
              "type" to "command_result",
              "device_id" to deviceId,
              "command_id" to cmdId
            )
            if (result != null) resultFrame["result"] = result
            if (error != null) resultFrame["error"] = error

            sendJson(resultFrame)
            _lastLog.value = "Dispatched result for $cmdType: ${if (error != null) "FAILED" else "SUCCESS"}"
          }
        }

        "webrtc_offer" -> {
          @Suppress("UNCHECKED_CAST")
          val offerData = (data["data"] as? Map<String, Any>) ?: emptyMap()
          onWebRtcOfferReceived?.invoke(offerData)
        }
      }
    } catch (e: Exception) {
      Log.e("NexusWebSocket", "Error parsing message", e)
    }
  }

  fun sendTelemetryUpdate(deviceId: String) {
    val telem = telemetryCollector.collectTelemetry()
    val perms = permissionManager.getPermissionsMap()

    val payload = mutableMapOf<String, Any>(
      "type" to "status_update",
      "device_id" to deviceId,
      "battery_level" to telem.batteryLevel,
      "charging" to telem.isCharging,
      "storage_free_mb" to telem.storageFreeMb,
      "storage_total_mb" to telem.storageTotalMb,
      "network_type" to telem.networkType,
      "permissions" to perms,
      "timestamp" to System.currentTimeMillis() / 1000.0
    )
    if (telem.batteryTemp != null) payload["battery_temp"] = telem.batteryTemp
    if (telem.wifiSsid != null) payload["wifi_ssid"] = telem.wifiSsid

    sendJson(payload)
  }

  private fun startHeartbeat(deviceId: String) {
    stopHeartbeat()
    heartbeatJob = scope.launch {
      while (isActive) {
        delay(30_000) // Every 30 seconds
        val telem = telemetryCollector.collectTelemetry()
        val payload = mapOf(
          "type" to "heartbeat",
          "device_id" to deviceId,
          "battery_level" to telem.batteryLevel,
          "charging" to telem.isCharging,
          "network_type" to telem.networkType,
          "timestamp" to System.currentTimeMillis() / 1000.0
        )
        sendJson(payload)
      }
    }
  }

  private fun stopHeartbeat() {
    heartbeatJob?.cancel()
    heartbeatJob = null
  }

  fun sendJson(data: Map<String, Any>): Boolean {
    val ws = webSocket
    if (ws != null && _connectionState.value == "ONLINE") {
      val json = mapAdapter.toJson(data)
      return ws.send(json)
    }
    return false
  }

  private fun scheduleReconnect(serverUrl: String, deviceId: String, deviceKey: String) {
    if (isManuallyClosed) return
    scope.launch {
      delay(5000)
      if (!isManuallyClosed && _connectionState.value != "ONLINE") {
        connect(serverUrl, deviceId, deviceKey)
      }
    }
  }

  fun disconnect() {
    isManuallyClosed = true
    stopHeartbeat()
    webSocket?.close(1000, "Normal closure")
    webSocket = null
    _connectionState.value = "DISCONNECTED"
  }
}
