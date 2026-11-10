package com.example.nexusremote.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.nexusremote.data.model.DevicePermissionItem
import com.example.nexusremote.services.ScreenCaptureService
import com.example.ui.theme.NexusAmber
import com.example.ui.theme.NexusCyan
import com.example.ui.theme.NexusEmerald
import com.example.ui.theme.NexusIndigo
import com.example.ui.theme.NexusRose

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentApp(viewModel: AgentViewModel) {
  var selectedTab by remember { mutableStateOf(0) }
  val context = LocalContext.current

  val connState by viewModel.connectionState.collectAsState()
  val lastLog by viewModel.lastLog.collectAsState()
  val telemetry by viewModel.telemetry.collectAsState()
  val permissions by viewModel.permissions.collectAsState()
  val pairingCode by viewModel.pairingCode.collectAsState()
  val showApprovalDialog by viewModel.showApprovalDialog.collectAsState()
  val isPairingLoading by viewModel.isPairingLoading.collectAsState()
  val screenShareSession by viewModel.screenShareRequestedSession.collectAsState()

  // Runtime permissions launcher (Location, Camera, Microphone, Notifications)
  val requestPermissionsLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { _ ->
    viewModel.refreshPermissions()
    viewModel.refreshTelemetry()
  }

  androidx.compose.runtime.LaunchedEffect(Unit) {
    val ungranted = viewModel.permissionManager.getRequiredRuntimePermissions()
    if (ungranted.isNotEmpty()) {
      requestPermissionsLauncher.launch(ungranted)
    }
  }

  // Screen capture launcher
  val mediaProjectionManager = remember {
    context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
  }

  val screenCaptureLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK && result.data != null) {
      val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
        putExtra("result_code", result.resultCode)
        putExtra("data", result.data)
      }
      ContextCompat.startForegroundService(context, serviceIntent)
    }
  }

  // Dialog: Screen share permission request from web dashboard
  if (screenShareSession != null) {
    AlertDialog(
      onDismissRequest = { viewModel.dismissScreenSharePrompt() },
      icon = { Icon(Icons.Default.Cast, contentDescription = null, tint = NexusCyan) },
      title = { Text("Screen Sharing Requested", fontWeight = FontWeight.Bold) },
      text = {
        Text("The remote management console has initiated a screen streaming session. You must explicitly approve the system capture dialog to proceed.")
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.dismissScreenSharePrompt()
            mediaProjectionManager?.let {
              screenCaptureLauncher.launch(it.createScreenCaptureIntent())
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = NexusCyan),
          modifier = Modifier.testTag("approve_stream_btn")
        ) {
          Text("Continue to Consent", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { viewModel.dismissScreenSharePrompt() }) {
          Text("Cancel", color = Color.Gray)
        }
      }
    )
  }

  // Dialog: Explicit Pairing Approval Prompt (Section 3.3)
  if (showApprovalDialog) {
    AlertDialog(
      onDismissRequest = { viewModel.rejectPairing() },
      icon = { Icon(Icons.Default.Link, contentDescription = null, tint = NexusAmber) },
      title = { Text("Approve Device Pairing?", fontWeight = FontWeight.Bold) },
      text = {
        Text("A web management console operator has entered this device's pairing code. Authorize this operator to remotely monitor hardware status and execute authorized administrative commands?")
      },
      confirmButton = {
        Button(
          onClick = { viewModel.confirmApprovePairing() },
          colors = ButtonDefaults.buttonColors(containerColor = NexusEmerald),
          modifier = Modifier.testTag("confirm_pair_btn")
        ) {
          Text("Approve & Link", color = Color.White, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { viewModel.rejectPairing() }) {
          Text("Reject", color = NexusRose)
        }
      }
    )
  }

  // Dialog: Display Pairing Code
  if (pairingCode != null) {
    AlertDialog(
      onDismissRequest = { /* Keep active until paired */ },
      icon = { Icon(Icons.Default.Link, contentDescription = null, tint = NexusCyan) },
      title = { Text("Device Pairing Code", fontWeight = FontWeight.Bold) },
      text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
          Text("Enter this 6-character code in the Web Dashboard to establish secure device association:", fontSize = 13.sp, color = Color.Gray)
          Spacer(modifier = Modifier.height(16.dp))
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.border(1.dp, NexusCyan, RoundedCornerShape(12.dp))
          ) {
            Text(
              text = pairingCode ?: "",
              fontSize = 32.sp,
              fontWeight = FontWeight.Black,
              fontFamily = FontFamily.Monospace,
              color = NexusCyan,
              modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
          }
          Spacer(modifier = Modifier.height(12.dp))
          Text("Waiting for operator to enter code in browser...", fontSize = 12.sp, color = NexusAmber)
        }
      },
      confirmButton = {
        TextButton(onClick = { viewModel.rejectPairing() }) {
          Text("Dismiss", color = Color.Gray)
        }
      }
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(
                  when (connState) {
                    "ONLINE" -> NexusEmerald
                    "CONNECTING" -> NexusAmber
                    else -> NexusRose
                  }
                )
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text("Control-XP", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
              Text("Node Link: $connState", fontSize = 11.sp, color = Color.Gray)
            }
          }
        },
        actions = {
          IconButton(
            onClick = { viewModel.refreshTelemetry() },
            modifier = Modifier.padding(end = 8.dp)
          ) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh Telemetry", tint = NexusCyan)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    },
    bottomBar = {
      NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
          label = { Text("Overview") },
          modifier = Modifier.testTag("tab_dashboard")
        )
        NavigationBarItem(
          selected = selectedTab == 1,
          onClick = {
            selectedTab = 1
            viewModel.refreshPermissions()
          },
          icon = { Icon(Icons.Default.Security, contentDescription = "Permissions") },
          label = { Text("Shields") },
          modifier = Modifier.testTag("tab_permissions")
        )
        NavigationBarItem(
          selected = selectedTab == 2,
          onClick = { selectedTab = 2 },
          icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
          label = { Text("Config") },
          modifier = Modifier.testTag("tab_settings")
        )
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(
              Color(0xFF04060B),
              Color(0xFF0A0F1D)
            )
          )
        )
        .padding(innerPadding)
    ) {
      when (selectedTab) {
        0 -> DashboardTab(
          viewModel = viewModel,
          connState = connState,
          lastLog = lastLog,
          isPairingLoading = isPairingLoading,
          onRequestScreenShare = {
            mediaProjectionManager?.let {
              screenCaptureLauncher.launch(it.createScreenCaptureIntent())
            }
          },
          onRequestPermissions = {
            val ungranted = viewModel.permissionManager.getRequiredRuntimePermissions()
            if (ungranted.isNotEmpty()) {
              requestPermissionsLauncher.launch(ungranted)
            }
          },
          onOpenAccessibility = {
            viewModel.permissionManager.openAccessibilitySettings()
          }
        )
        1 -> PermissionsTab(
          permissions = permissions,
          onOpenSettings = { viewModel.permissionManager.openAppSettings() },
          onOpenAccessibility = { viewModel.permissionManager.openAccessibilitySettings() },
          onRequestPermissions = {
            val ungranted = viewModel.permissionManager.getRequiredRuntimePermissions()
            if (ungranted.isNotEmpty()) {
              requestPermissionsLauncher.launch(ungranted)
            }
          },
          onDisableBatteryRestrictions = {
            viewModel.permissionManager.requestIgnoreBatteryOptimizations()
          },
          onGrantAllFilesAccess = {
            viewModel.permissionManager.requestManageAllFilesAccess()
          }
        )
        2 -> SettingsTab(
          viewModel = viewModel
        )
      }
    }
  }
}

@Composable
fun DashboardTab(
  viewModel: AgentViewModel,
  connState: String,
  lastLog: String,
  isPairingLoading: Boolean,
  onRequestScreenShare: () -> Unit,
  onRequestPermissions: () -> Unit,
  onOpenAccessibility: () -> Unit
) {
  val telemetry by viewModel.telemetry.collectAsState()
  val devStatus = viewModel.sessionManager.deviceStatus
  val ungrantedRuntime = remember(telemetry) { viewModel.permissionManager.getRequiredRuntimePermissions() }
  val isAccessibilityEnabled = remember(telemetry) { viewModel.permissionManager.isAccessibilityServiceEnabled() }
  val isBatteryOptimized = remember(telemetry) { !viewModel.permissionManager.isIgnoringBatteryOptimizations() }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // 0. Permission Warning Banner if any permission missing
    if (ungrantedRuntime.isNotEmpty() || !isAccessibilityEnabled || isBatteryOptimized) {
      item {
        Card(
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = NexusAmber.copy(alpha = 0.12f)),
          modifier = Modifier.border(1.dp, NexusAmber.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Warning, contentDescription = null, tint = NexusAmber, modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Background Operations Alert", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NexusAmber)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              "Unrestricted battery and touch accessibility permissions are recommended for robust background telemetry and remote control.",
              fontSize = 12.sp,
              color = Color.LightGray,
              lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              if (ungrantedRuntime.isNotEmpty()) {
                Button(
                  onClick = onRequestPermissions,
                  colors = ButtonDefaults.buttonColors(containerColor = NexusAmber),
                  modifier = Modifier.weight(1f).testTag("grant_runtime_perms_btn")
                ) {
                  Text("Perms", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }
              if (isBatteryOptimized) {
                Button(
                  onClick = { viewModel.permissionManager.requestIgnoreBatteryOptimizations() },
                  colors = ButtonDefaults.buttonColors(containerColor = NexusCyan),
                  modifier = Modifier.weight(1f)
                ) {
                  Text("Battery", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }
              if (!isAccessibilityEnabled) {
                OutlinedButton(
                  onClick = onOpenAccessibility,
                  modifier = Modifier.weight(1f).testTag("enable_accessibility_btn")
                ) {
                  Text("Touch", color = NexusCyan, fontSize = 11.sp)
                }
              }
            }
          }
        }
      }
    }

    // 1. Connection & Pairing Status Banner
    item {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = viewModel.sessionManager.deviceName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "${android.os.Build.MODEL} • Android ${android.os.Build.VERSION.RELEASE}",
                fontSize = 12.sp,
                color = Color.Gray
              )
            }
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = when (devStatus) {
                "PAIRED", "ONLINE" -> NexusEmerald.copy(alpha = 0.2f)
                "PAIRING_PENDING" -> NexusAmber.copy(alpha = 0.2f)
                else -> Color.Gray.copy(alpha = 0.2f)
              }
            ) {
              Text(
                text = devStatus,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = when (devStatus) {
                  "PAIRED", "ONLINE" -> NexusEmerald
                  "PAIRING_PENDING" -> NexusAmber
                  else -> Color.White
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
              onClick = { viewModel.requestPairing() },
              enabled = !isPairingLoading,
              modifier = Modifier.weight(1f).testTag("request_pair_btn"),
              colors = ButtonDefaults.buttonColors(containerColor = NexusCyan)
            ) {
              if (isPairingLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
              } else {
                Text("Pair Node", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
              }
            }

            OutlinedButton(
              onClick = { onRequestScreenShare() },
              modifier = Modifier.weight(1f).testTag("screen_share_btn")
            ) {
              Icon(Icons.Default.Cast, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Stream")
            }
          }
        }
      }
    }

    // 2. Telemetry Grid
    item {
      Text("Live Hardware Telemetry", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
    }

    item {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Battery Card
        Card(
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1f),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text("BATTERY LEVEL", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text("${telemetry.batteryLevel}%", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(
              if (telemetry.isCharging) "⚡ Charging" else "Discharging",
              fontSize = 11.sp,
              color = if (telemetry.isCharging) NexusEmerald else Color.Gray
            )
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
              progress = { telemetry.batteryLevel / 100f },
              modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
              color = if (telemetry.batteryLevel < 20) NexusRose else NexusEmerald,
              trackColor = Color.DarkGray
            )
          }
        }

        // Thermal Card
        Card(
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1f),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text("THERMAL SENSOR", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              if (telemetry.batteryTemp != null) "${telemetry.batteryTemp}°C" else "Nominal",
              fontSize = 24.sp,
              fontWeight = FontWeight.ExtraBold
            )
            Text("Battery Thermal", fontSize = 11.sp, color = Color.Gray)
          }
        }
      }
    }

    item {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Storage Card
        Card(
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1f),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text("STORAGE FREE", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Spacer(modifier = Modifier.height(6.dp))
            val freeGb = (telemetry.storageFreeMb / 1024.0).let { "%.1f".format(it) }
            val totalGb = (telemetry.storageTotalMb / 1024.0).let { "%.1f".format(it) }
            Text("${freeGb} GB", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("of $totalGb GB Available", fontSize = 11.sp, color = Color.Gray)
          }
        }

        // Network Card
        Card(
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1f),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text("UPLINK NETWORK", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(telemetry.networkType, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(telemetry.wifiSsid ?: "Active Link", fontSize = 11.sp, color = Color.Gray)
          }
        }
      }
    }

    // 3. Real-Time Activity Log
    item {
      Text("Agent Activity Trail", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
    }

    item {
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF060911)),
        modifier = Modifier.border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text(
            text = "> $lastLog",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = NexusCyan
          )
        }
      }
    }
  }
}

@Composable
fun PermissionsTab(
  permissions: List<DevicePermissionItem>,
  onOpenSettings: () -> Unit,
  onOpenAccessibility: () -> Unit,
  onRequestPermissions: () -> Unit,
  onDisableBatteryRestrictions: () -> Unit,
  onGrantAllFilesAccess: () -> Unit
) {
  val total = permissions.size
  val grantedCount = permissions.count { it.isGranted }
  val progress = if (total > 0) grantedCount.toFloat() / total else 0f
  val allGranted = grantedCount == total

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Top Summary Banner Card
    item {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
          containerColor = if (allGranted) NexusEmerald.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.border(
          width = 1.dp,
          color = if (allGranted) NexusEmerald.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
          shape = RoundedCornerShape(16.dp)
        )
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "Security & Shields",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "$grantedCount of $total capabilities active",
                fontSize = 12.sp,
                color = Color.Gray
              )
            }
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = if (allGranted) NexusEmerald.copy(alpha = 0.2f) else NexusAmber.copy(alpha = 0.2f)
            ) {
              Text(
                text = if (allGranted) "ACTIVE" else "ACTION REQ",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (allGranted) NexusEmerald else NexusAmber,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(14.dp))
          LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
              .fillMaxWidth()
              .height(6.dp)
              .clip(RoundedCornerShape(3.dp)),
            color = if (allGranted) NexusEmerald else NexusCyan,
            trackColor = Color.DarkGray
          )
          Spacer(modifier = Modifier.height(16.dp))

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = onRequestPermissions,
              colors = ButtonDefaults.buttonColors(containerColor = NexusCyan),
              modifier = Modifier.weight(1f).testTag("request_perms_btn")
            ) {
              Text("Grant Perms", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Button(
              onClick = onOpenAccessibility,
              colors = ButtonDefaults.buttonColors(containerColor = NexusAmber),
              modifier = Modifier.weight(1f).testTag("open_accessibility_btn")
            ) {
              Text("Touch Service", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
          }

          Spacer(modifier = Modifier.height(8.dp))
          OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth().testTag("open_settings_btn")
          ) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Open System App Settings", fontSize = 12.sp)
          }
        }
      }
    }

    item {
      Text(
        text = "Declared Capabilities",
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = Color.White,
        modifier = Modifier.padding(top = 4.dp)
      )
    }

    items(permissions) { perm ->
      val isAccessibility = perm.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE"
      val isAllFilesAccess = perm.permission == "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION"

      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
          containerColor = if (perm.isGranted) MaterialTheme.colorScheme.surfaceVariant else NexusRose.copy(alpha = 0.08f)
        ),
        modifier = Modifier.border(
          width = 1.dp,
          color = if (perm.isGranted) Color.Transparent else NexusRose.copy(alpha = 0.25f),
          shape = RoundedCornerShape(14.dp)
        )
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = perm.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (perm.isGranted) Color.White else NexusRose
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = perm.description,
                fontSize = 11.sp,
                color = Color.Gray,
                lineHeight = 15.sp
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = if (perm.isGranted) NexusEmerald.copy(alpha = 0.2f) else NexusRose.copy(alpha = 0.2f)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  if (perm.isGranted) Icons.Default.CheckCircle else Icons.Default.Close,
                  contentDescription = null,
                  tint = if (perm.isGranted) NexusEmerald else NexusRose,
                  modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  if (perm.isGranted) "Granted" else "Denied",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (perm.isGranted) NexusEmerald else NexusRose
                )
              }
            }
          }

          if (!perm.isGranted) {
            Spacer(modifier = Modifier.height(12.dp))
            if (isAccessibility) {
              Button(
                onClick = onOpenAccessibility,
                colors = ButtonDefaults.buttonColors(containerColor = NexusAmber),
                modifier = Modifier.fillMaxWidth()
              ) {
                Text("Enable Nexus Accessibility Service", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
              }
            } else if (perm.permission == "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS") {
              Button(
                onClick = onDisableBatteryRestrictions,
                colors = ButtonDefaults.buttonColors(containerColor = NexusAmber),
                modifier = Modifier.fillMaxWidth()
              ) {
                Text("Disable Battery Restrictions", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
              }
            } else if (isAllFilesAccess) {
              Button(
                onClick = onGrantAllFilesAccess,
                colors = ButtonDefaults.buttonColors(containerColor = NexusCyan),
                modifier = Modifier.fillMaxWidth()
              ) {
                Text("Allow All Files Access (Android Settings)", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 11.sp)
              }
            } else {
              Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(containerColor = NexusCyan),
                modifier = Modifier.fillMaxWidth()
              ) {
                Text("Grant Permission", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 11.sp)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun SettingsTab(viewModel: AgentViewModel) {
  var serverInput by remember { mutableStateOf(viewModel.sessionManager.serverUrl) }
  var isSaved by remember { mutableStateOf(false) }
  var selectedSyncInterval by remember { mutableStateOf(15) }
  var showResetConfirm by remember { mutableStateOf(false) }
  var pingStatus by remember { mutableStateOf<String?>(null) }
  val context = LocalContext.current

  // Reset Confirmation Dialog
  if (showResetConfirm) {
    AlertDialog(
      onDismissRequest = { showResetConfirm = false },
      icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = NexusRose) },
      title = { Text("Confirm Unpair & Reset?", fontWeight = FontWeight.Bold, color = NexusRose) },
      text = {
        Text("This action will clear stored device keys, unpair from the management console, and reset connection settings. Proceed?")
      },
      confirmButton = {
        Button(
          onClick = {
            showResetConfirm = false
            viewModel.unpairAndReset()
          },
          colors = ButtonDefaults.buttonColors(containerColor = NexusRose),
          modifier = Modifier.testTag("confirm_reset_btn")
        ) {
          Text("Reset & Unpair Device", color = Color.White, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { showResetConfirm = false }) {
          Text("Cancel", color = Color.Gray)
        }
      }
    )
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Text("Platform & Configuration", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }

    // 1. Server Uplink Config Card
    item {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Text("Limbo Backend Server Address", fontWeight = FontWeight.Bold, fontSize = 14.sp)
          Text("Set self-hosted server address or IP endpoint for real-time WebSocket telemetry.", fontSize = 12.sp, color = Color.Gray)
          Spacer(modifier = Modifier.height(12.dp))

          OutlinedTextField(
            value = serverInput,
            onValueChange = {
              serverInput = it
              isSaved = false
            },
            modifier = Modifier.fillMaxWidth().testTag("server_url_input"),
            singleLine = true,
            label = { Text("Server URL") }
          )

          Spacer(modifier = Modifier.height(12.dp))

          Button(
            onClick = {
              viewModel.updateServerUrl(serverInput)
              isSaved = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = NexusCyan),
            modifier = Modifier.fillMaxWidth().testTag("save_server_btn")
          ) {
            Text(if (isSaved) "Saved ✓" else "Save Server URL", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 12.sp)
          }



          if (pingStatus != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(pingStatus ?: "", fontSize = 11.sp, color = NexusEmerald, fontWeight = FontWeight.SemiBold)
          }
        }
      }
    }

    // 2. Telemetry & Sync Frequency Card
    item {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Text("Telemetry Sync Interval", fontWeight = FontWeight.Bold, fontSize = 14.sp)
          Text("Configure frequency for battery, thermal, storage, and network reporting.", fontSize = 12.sp, color = Color.Gray)
          Spacer(modifier = Modifier.height(14.dp))

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 15, 30, 60).forEach { intervalSec ->
              val isSelected = selectedSyncInterval == intervalSec
              Button(
                onClick = {
                  selectedSyncInterval = intervalSec
                  viewModel.refreshTelemetry()
                },
                colors = ButtonDefaults.buttonColors(
                  containerColor = if (isSelected) NexusCyan else MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
              ) {
                Text(
                  text = "${intervalSec}s",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (isSelected) Color(0xFF0F172A) else Color.White
                )
              }
            }
          }
        }
      }
    }

    // 3. Installation Profile & Copy Identifiers Card
    item {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Text("Device Identity & Keys", fontWeight = FontWeight.Bold, fontSize = 14.sp)
          Spacer(modifier = Modifier.height(12.dp))

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
              Text("Device Name", fontSize = 11.sp, color = Color.Gray)
              Text(viewModel.sessionManager.deviceName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
              Text("Android Model", fontSize = 11.sp, color = Color.Gray)
              Text("${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Registered Device ID", fontSize = 11.sp, color = Color.Gray)
              Text(
                text = viewModel.sessionManager.deviceId ?: "Unregistered",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = NexusCyan,
                fontWeight = FontWeight.Bold
              )
            }
            IconButton(
              onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("device_id", viewModel.sessionManager.deviceId ?: "")
                clipboard?.setPrimaryClip(clip)
                pingStatus = "Copied Device ID to clipboard ✓"
              }
            ) {
              Icon(Icons.Default.Info, contentDescription = "Copy ID", tint = NexusCyan, modifier = Modifier.size(18.dp))
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Installation UUID", fontSize = 11.sp, color = Color.Gray)
              Text(
                text = viewModel.sessionManager.getOrCreateInstallationId(),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color.LightGray
              )
            }
            IconButton(
              onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("install_uuid", viewModel.sessionManager.getOrCreateInstallationId())
                clipboard?.setPrimaryClip(clip)
                pingStatus = "Copied UUID to clipboard ✓"
              }
            ) {
              Icon(Icons.Default.Info, contentDescription = "Copy UUID", tint = Color.Gray, modifier = Modifier.size(18.dp))
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = {
                viewModel.connectWebSocket()
                pingStatus = "WebSocket reconnect triggered ✓"
              },
              colors = ButtonDefaults.buttonColors(containerColor = NexusIndigo),
              modifier = Modifier.weight(1f)
            ) {
              Text("Reconnect WS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
              onClick = {
                viewModel.refreshTelemetry()
                viewModel.refreshPermissions()
                pingStatus = "Refreshed stats & permissions ✓"
              },
              modifier = Modifier.weight(1f)
            ) {
              Text("Sync Stats", fontSize = 11.sp)
            }
          }
        }
      }
    }

    // 4. Danger Zone Card
    item {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NexusRose.copy(alpha = 0.1f)),
        modifier = Modifier.border(1.dp, NexusRose.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Text("Unpair & Reset Agent", fontWeight = FontWeight.Bold, color = NexusRose, fontSize = 14.sp)
          Text("Clear local device credentials and unpair from the management console.", fontSize = 12.sp, color = Color.Gray)
          Spacer(modifier = Modifier.height(12.dp))
          Button(
            onClick = { showResetConfirm = true },
            colors = ButtonDefaults.buttonColors(containerColor = NexusRose),
            modifier = Modifier.fillMaxWidth().testTag("reset_device_btn")
          ) {
            Text("Reset & Disconnect Device", color = Color.White, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}
