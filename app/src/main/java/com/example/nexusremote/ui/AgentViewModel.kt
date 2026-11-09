package com.example.nexusremote.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexusremote.commands.CommandExecutor
import com.example.nexusremote.data.model.ApprovePairingRequest
import com.example.nexusremote.data.model.DevicePermissionItem
import com.example.nexusremote.data.model.PairingRequest
import com.example.nexusremote.data.model.RegisterDeviceRequest
import com.example.nexusremote.data.model.TelemetryData
import com.example.nexusremote.network.NexusApiClient
import com.example.nexusremote.network.NexusApiService
import com.example.nexusremote.network.NexusWebSocketClient
import com.example.nexusremote.permissions.PermissionManager
import com.example.nexusremote.security.SessionManager
import com.example.nexusremote.telemetry.DeviceTelemetryCollector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AgentViewModel(application: Application) : AndroidViewModel(application) {

  val sessionManager = SessionManager(application)
  val telemetryCollector = DeviceTelemetryCollector(application)
  val permissionManager = PermissionManager(application)

  private var apiService: NexusApiService = NexusApiClient.createService(sessionManager.serverUrl)

  private val _telemetry = MutableStateFlow(telemetryCollector.collectTelemetry())
  val telemetry: StateFlow<TelemetryData> = _telemetry.asStateFlow()

  private val _permissions = MutableStateFlow(permissionManager.getDeclaredPermissions())
  val permissions: StateFlow<List<DevicePermissionItem>> = _permissions.asStateFlow()

  private val _pairingCode = MutableStateFlow<String?>(null)
  val pairingCode: StateFlow<String?> = _pairingCode.asStateFlow()

  private val _pairingId = MutableStateFlow<String?>(null)
  val pairingId: StateFlow<String?> = _pairingId.asStateFlow()

  private val _showApprovalDialog = MutableStateFlow(false)
  val showApprovalDialog: StateFlow<Boolean> = _showApprovalDialog.asStateFlow()

  private val _isPairingLoading = MutableStateFlow(false)
  val isPairingLoading: StateFlow<Boolean> = _isPairingLoading.asStateFlow()

  private val _screenShareRequestedSession = MutableStateFlow<String?>(null)
  val screenShareRequestedSession: StateFlow<String?> = _screenShareRequestedSession.asStateFlow()

  val commandExecutor = CommandExecutor(
    context = application,
    telemetryCollector = telemetryCollector,
    onScreenShareRequested = { sessionId ->
      _screenShareRequestedSession.value = sessionId
    }
  )

  val webSocketClient = com.example.nexusremote.network.NexusWebSocketProvider.getClient(
    context = application,
    telemetryCollector = telemetryCollector,
    permissionManager = permissionManager,
    commandExecutor = commandExecutor
  )

  val connectionState: StateFlow<String> = webSocketClient.connectionState
  val lastLog: StateFlow<String> = webSocketClient.lastLog

  private var pairingPollJob: Job? = null

  init {
    com.example.nexusremote.services.NexusRemoteService.start(application)
    ensureDeviceRegistered()
  }

  fun updateServerUrl(newUrl: String) {
    sessionManager.serverUrl = newUrl
    apiService = NexusApiClient.createService(sessionManager.serverUrl)
    ensureDeviceRegistered()
  }

  fun ensureDeviceRegistered() {
    viewModelScope.launch {
      if (!sessionManager.isRegistered) {
        try {
          val req = RegisterDeviceRequest(
            name = sessionManager.deviceName,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            appVersion = "1.0.0",
            screenRes = "${getApplication<Application>().resources.displayMetrics.widthPixels}x${getApplication<Application>().resources.displayMetrics.heightPixels}"
          )
          val res = apiService.registerDevice(req)
          if (res.isSuccessful && res.body() != null) {
            val body = res.body()!!
            sessionManager.deviceId = body.deviceId
            sessionManager.deviceKey = body.deviceKey
            sessionManager.deviceStatus = body.status
            connectWebSocket()
          }
        } catch (_: Exception) {}
      } else {
        connectWebSocket()
      }
    }
  }

  fun connectWebSocket() {
    val devId = sessionManager.deviceId
    val devKey = sessionManager.deviceKey
    if (devId != null && devKey != null) {
      webSocketClient.connect(sessionManager.serverUrl, devId, devKey)
    }
  }

  fun requestPairing() {
    val devId = sessionManager.deviceId ?: return
    _isPairingLoading.value = true

    viewModelScope.launch {
      try {
        val res = apiService.createPairingRequest(
          PairingRequest(deviceId = devId, deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
        )
        if (res.isSuccessful && res.body() != null) {
          val body = res.body()!!
          _pairingCode.value = body.code
          _pairingId.value = body.pairingId
          sessionManager.deviceStatus = "PAIRING_PENDING"
          startPairingStatusPolling(body.pairingId)
        }
      } catch (_: Exception) {
      } finally {
        _isPairingLoading.value = false
      }
    }
  }

  private fun startPairingStatusPolling(pairingId: String) {
    pairingPollJob?.cancel()
    pairingPollJob = viewModelScope.launch {
      while (isActive) {
        delay(2000)
        try {
          val res = apiService.getPairingStatus(pairingId)
          if (res.isSuccessful && res.body() != null) {
            val statusData = res.body()!!.pairing
            if (statusData.status == "VERIFIED") {
              // Web dashboard verified the code, device user must explicitly approve
              _showApprovalDialog.value = true
              break
            } else if (statusData.status == "APPROVED") {
              sessionManager.deviceStatus = "PAIRED"
              _pairingCode.value = null
              break
            } else if (statusData.status == "EXPIRED" || statusData.status == "REJECTED") {
              _pairingCode.value = null
              break
            }
          }
        } catch (_: Exception) {}
      }
    }
  }

  fun confirmApprovePairing() {
    val pId = _pairingId.value ?: return
    val devId = sessionManager.deviceId ?: return
    viewModelScope.launch {
      try {
        val res = apiService.approvePairing(ApprovePairingRequest(pairingId = pId, deviceId = devId))
        if (res.isSuccessful) {
          sessionManager.deviceStatus = "PAIRED"
          _showApprovalDialog.value = false
          _pairingCode.value = null
          webSocketClient.sendTelemetryUpdate(devId)
        }
      } catch (_: Exception) {}
    }
  }

  fun rejectPairing() {
    val pId = _pairingId.value ?: return
    val devId = sessionManager.deviceId ?: return
    viewModelScope.launch {
      try {
        apiService.rejectPairing(ApprovePairingRequest(pairingId = pId, deviceId = devId))
      } catch (_: Exception) {}
      _showApprovalDialog.value = false
      _pairingCode.value = null
    }
  }

  fun dismissScreenSharePrompt() {
    _screenShareRequestedSession.value = null
  }

  fun refreshTelemetry() {
    _telemetry.value = telemetryCollector.collectTelemetry()
    sessionManager.deviceId?.let { devId ->
      webSocketClient.sendTelemetryUpdate(devId)
    }
  }

  fun refreshPermissions() {
    _permissions.value = permissionManager.getDeclaredPermissions()
    sessionManager.deviceId?.let { devId ->
      webSocketClient.sendTelemetryUpdate(devId)
    }
  }

  fun unpairAndReset() {
    webSocketClient.disconnect()
    sessionManager.clearCredentials()
    _pairingCode.value = null
    _pairingId.value = null
    _showApprovalDialog.value = false
    ensureDeviceRegistered()
  }

  override fun onCleared() {
    pairingPollJob?.cancel()
    webSocketClient.disconnect()
    super.onCleared()
  }
}
