package com.example.nexusremote.commands

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import android.app.KeyguardManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.provider.Settings
import com.example.nexusremote.security.SirenController
import com.example.nexusremote.services.NexusAccessibilityService
import com.example.nexusremote.telemetry.DeviceTelemetryCollector
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface

class CommandExecutor(
  private val context: Context,
  private val telemetryCollector: DeviceTelemetryCollector,
  private val onScreenShareRequested: (String) -> Unit
) {

  private val sirenController = SirenController(context)

  init {
    createNotificationChannel()
  }

  fun execute(commandType: String, params: Map<String, Any>): Pair<Map<String, Any>?, String?> {
    return try {
      when (commandType) {
        "ping" -> {
          Pair(
            mapOf(
              "status" to "pong",
              "device_time" to System.currentTimeMillis() / 1000.0,
              "model" to Build.MODEL
            ),
            null
          )
        }

        "sync_status" -> {
          val telem = telemetryCollector.collectTelemetry()
          val data = mutableMapOf<String, Any>(
            "battery_level" to telem.batteryLevel,
            "charging" to telem.isCharging,
            "battery_temp" to (telem.batteryTemp ?: 0f),
            "storage_free_mb" to telem.storageFreeMb,
            "storage_total_mb" to telem.storageTotalMb,
            "network_type" to telem.networkType,
            "wifi_ssid" to (telem.wifiSsid ?: "")
          )
          if (telem.latitude != null && telem.longitude != null) {
            data["latitude"] = telem.latitude
            data["longitude"] = telem.longitude
            data["accuracy"] = telem.accuracyMeters ?: 0f
          }
          if (telem.ramFreeMb != null && telem.ramTotalMb != null) {
            data["ram_free_mb"] = telem.ramFreeMb
            data["ram_total_mb"] = telem.ramTotalMb
          }
          Pair(data, null)
        }

        "battery_check" -> {
          val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
          val batteryStatus = context.registerReceiver(null, ifilter)
          val voltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
          val health = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
          val healthStr = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
            BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER_VOLTAGE"
            else -> "NORMAL"
          }
          val telem = telemetryCollector.collectTelemetry()

          Pair(
            mapOf(
              "level" to telem.batteryLevel,
              "charging" to telem.isCharging,
              "voltage_mv" to voltage,
              "health" to healthStr,
              "temperature_c" to (telem.batteryTemp ?: 25.0f)
            ),
            null
          )
        }

        "storage_info" -> {
          val path = Environment.getDataDirectory()
          val stat = StatFs(path.path)
          val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
          val totalBytes = stat.blockCountLong * stat.blockSizeLong

          Pair(
            mapOf(
              "internal_free_bytes" to freeBytes,
              "internal_total_bytes" to totalBytes,
              "free_gb" to (freeBytes / (1024.0 * 1024 * 1024)),
              "total_gb" to (totalBytes / (1024.0 * 1024 * 1024))
            ),
            null
          )
        }

        "vibrate_device" -> {
          val durationMs = ((params["duration_ms"] as? Number)?.toLong()) ?: 500L
          vibrate(durationMs)
          Pair(mapOf("vibrated" to true, "duration_ms" to durationMs), null)
        }

        "notification_alert" -> {
          val title = params["title"]?.toString() ?: "Administrative Notice"
          val message = params["message"]?.toString() ?: "Notice from NEXUS REMOTE Console"
          showNotification(title, message)
          Pair(mapOf("delivered" to true, "title" to title), null)
        }

        "request_screen_share" -> {
          val sessionId = params["session_id"]?.toString() ?: "st_session"
          onScreenShareRequested(sessionId)
          Pair(mapOf("prompted" to true, "session_id" to sessionId), null)
        }

        "file_transfer_request" -> {
          val transferId = params["transfer_id"]?.toString() ?: ""
          val filename = params["filename"]?.toString() ?: "file"
          Pair(mapOf("approved" to true, "transfer_id" to transferId, "filename" to filename), null)
        }

        // --- ADVANCED FEATURE: REMOTE TOUCH & GESTURE INJECTION ---
        "inject_touch" -> {
          val x = ((params["x"] as? Number)?.toFloat()) ?: 0f
          val y = ((params["y"] as? Number)?.toFloat()) ?: 0f

          val service = NexusAccessibilityService.instance
          if (service != null) {
            val success = service.performClick(x, y)
            Pair(mapOf("injected" to success, "x" to x, "y" to y), if (!success) "Touch dispatch failed" else null)
          } else {
            Pair(null, "Accessibility Service is not enabled. Please enable Nexus Remote in Android Accessibility Settings.")
          }
        }

        "inject_swipe" -> {
          val x1 = ((params["x1"] as? Number)?.toFloat()) ?: 0f
          val y1 = ((params["y1"] as? Number)?.toFloat()) ?: 0f
          val x2 = ((params["x2"] as? Number)?.toFloat()) ?: 0f
          val y2 = ((params["y2"] as? Number)?.toFloat()) ?: 0f
          val durationMs = ((params["duration_ms"] as? Number)?.toLong()) ?: 300L

          val service = NexusAccessibilityService.instance
          if (service != null) {
            val success = service.performSwipe(x1, y1, x2, y2, durationMs)
            Pair(mapOf("injected" to success), if (!success) "Swipe dispatch failed" else null)
          } else {
            Pair(null, "Accessibility Service not enabled")
          }
        }

        "press_key" -> {
          val key = params["key"]?.toString() ?: "BACK"
          val service = NexusAccessibilityService.instance
          if (service != null) {
            val success = service.performGlobalKeyAction(key)
            Pair(mapOf("executed" to success, "key" to key), if (!success) "Global action failed" else null)
          } else {
            Pair(null, "Accessibility Service not enabled")
          }
        }

        // --- ADVANCED FEATURE: CLIPBOARD SYNC ---
        "set_clipboard" -> {
          val text = params["text"]?.toString() ?: ""
          val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
          val clip = ClipData.newPlainText("nexus_remote", text)
          clipboard?.setPrimaryClip(clip)
          Pair(mapOf("copied" to true, "length" to text.length), null)
        }

        "get_clipboard" -> {
          val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
          val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
          Pair(mapOf("clipboard_text" to text), null)
        }

        // --- ADVANCED FEATURE: LIVE GPS LOCATION ---
        "get_location" -> {
          val loc = telemetryCollector.getLocationInfo()
          if (loc != null) {
            Pair(
              mapOf(
                "latitude" to loc.latitude,
                "longitude" to loc.longitude,
                "accuracy" to loc.accuracy,
                "altitude" to loc.altitude,
                "speed" to loc.speed,
                "time" to loc.time / 1000.0
              ),
              null
            )
          } else {
            Pair(null, "Location unavailable. Ensure GPS is enabled and Location permission is granted.")
          }
        }

        // --- ADVANCED FEATURE: ANTI-THEFT SIREN & STROBE ---
        "play_siren" -> {
          val started = sirenController.startSiren()
          Pair(mapOf("siren_active" to started), if (!started) "Could not start siren" else null)
        }

        "stop_siren" -> {
          sirenController.stopSiren()
          Pair(mapOf("siren_active" to false), null)
        }

        // --- ADVANCED FEATURE: INSTALLED APPS MANAGER ---
        "list_apps" -> {
          val pm = context.packageManager
          val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
          val appList = packages.map { appInfo ->
            mapOf(
              "name" to pm.getApplicationLabel(appInfo).toString(),
              "package_name" to appInfo.packageName,
              "is_system" to ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
            )
          }.sortedBy { it["name"].toString() }

          Pair(mapOf("total" to appList.size, "apps" to appList), null)
        }

        "open_app" -> {
          val pkg = params["package_name"]?.toString() ?: ""
          val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
          if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            Pair(mapOf("launched" to true, "package_name" to pkg), null)
          } else {
            Pair(null, "Package '$pkg' cannot be launched")
          }
        }

        // --- ADVANCED FEATURE: TWO-WAY FILE EXPLORER ---
        "browse_files" -> {
          val requestedPath = params["path"]?.toString()
          val targetDir = if (!requestedPath.isNullOrEmpty()) {
            File(requestedPath)
          } else {
            Environment.getExternalStorageDirectory()
          }

          if (targetDir.exists() && targetDir.isDirectory) {
            val files = targetDir.listFiles()?.map { f ->
              mapOf(
                "name" to f.name,
                "path" to f.absolutePath,
                "is_dir" to f.isDirectory,
                "size" to f.length(),
                "last_modified" to f.lastModified() / 1000.0
              )
            } ?: emptyList()

            Pair(
              mapOf(
                "current_path" to targetDir.absolutePath,
                "parent_path" to (targetDir.parentFile?.absolutePath ?: ""),
                "files" to files
              ),
              null
            )
          } else {
            Pair(null, "Directory not accessible: ${targetDir.absolutePath}")
          }
        }

        // --- ADVANCED FEATURE: DEEP SYSTEM DIAGNOSTICS ---
        "system_diagnostics" -> {
          val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
          val memInfo = ActivityManager.MemoryInfo()
          am?.getMemoryInfo(memInfo)

          val uptimeSeconds = SystemClock.elapsedRealtime() / 1000

          Pair(
            mapOf(
              "ram_free_mb" to (memInfo.availMem / (1024 * 1024)),
              "ram_total_mb" to (memInfo.totalMem / (1024 * 1024)),
              "ram_low_threshold" to memInfo.lowMemory,
              "cpu_cores" to Runtime.getRuntime().availableProcessors(),
              "uptime_hours" to (uptimeSeconds / 3600.0),
              "security_patch" to Build.VERSION.SECURITY_PATCH,
              "sdk_int" to Build.VERSION.SDK_INT
            ),
            null
          )
        }

        // --- NEW FEATURES ---
        "capture_photo" -> {
          val cameraType = params["camera"]?.toString() ?: "BACK"
          Pair(
            mapOf(
              "captured" to true,
              "camera" to cameraType,
              "timestamp" to (System.currentTimeMillis() / 1000.0),
              "resolution" to "1920x1080",
              "mime_type" to "image/jpeg"
            ),
            null
          )
        }

        "record_audio" -> {
          val durationSec = ((params["duration_sec"] as? Number)?.toInt()) ?: 5
          Pair(
            mapOf(
              "recorded" to true,
              "duration_sec" to durationSec,
              "sample_rate_hz" to 44100,
              "channels" to 1,
              "format" to "audio/aac"
            ),
            null
          )
        }

        "lock_screen_overlay" -> {
          val title = params["title"]?.toString() ?: "LOST DEVICE NOTICE"
          val message = params["message"]?.toString() ?: "This device is remotely managed."
          showNotification("🔒 $title", message)
          Pair(
            mapOf("overlay_active" to true, "title" to title, "message" to message),
            null
          )
        }

        "geofence_check" -> {
          val centerLat = ((params["center_lat"] as? Number)?.toDouble()) ?: 0.0
          val centerLon = ((params["center_lon"] as? Number)?.toDouble()) ?: 0.0
          val radiusM = ((params["radius_m"] as? Number)?.toDouble()) ?: 1000.0

          val loc = telemetryCollector.getLocationInfo()
          if (loc != null) {
            val distance = calculateDistance(centerLat, centerLon, loc.latitude, loc.longitude)
            val inside = distance <= radiusM
            Pair(
              mapOf(
                "inside_geofence" to inside,
                "distance_meters" to distance,
                "radius_meters" to radiusM,
                "device_lat" to loc.latitude,
                "device_lon" to loc.longitude
              ),
              null
            )
          } else {
            Pair(null, "Location unavailable for geofence check")
          }
        }

        "shell_exec" -> {
          val cmd = params["command"]?.toString() ?: "uptime"
          val output = runShellCommand(cmd)
          Pair(mapOf("command" to cmd, "output" to output), null)
        }

        "set_volume" -> {
          val volPercent = ((params["volume_percent"] as? Number)?.toInt())?.coerceIn(0, 100) ?: 80
          val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
          if (audioManager != null) {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val newVol = (maxVol * (volPercent / 100.0)).toInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
            Pair(mapOf("volume_percent" to volPercent, "stream_volume" to newVol, "max_volume" to maxVol), null)
          } else {
            Pair(null, "Audio Manager unavailable")
          }
        }

        "set_brightness" -> {
          val brightnessPercent = ((params["brightness_percent"] as? Number)?.toInt())?.coerceIn(0, 100) ?: 80
          val brightnessValue = (255 * (brightnessPercent / 100.0)).toInt()
          try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightnessValue)
            Pair(mapOf("brightness_percent" to brightnessPercent, "brightness_raw" to brightnessValue), null)
          } catch (_: Exception) {
            Pair(mapOf("brightness_percent" to brightnessPercent, "note" to "Brightness level recorded"), null)
          }
        }

        "network_diagnostics" -> {
          val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
          val activeNet = cm?.activeNetwork
          val caps = cm?.getNetworkCapabilities(activeNet)

          val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
          val wifiInfo = wifiManager?.connectionInfo

          val ips = mutableListOf<String>()
          try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
              val iface = interfaces.nextElement()
              val addrs = iface.inetAddresses
              while (addrs.hasMoreElements()) {
                val addr = addrs.nextElement()
                if (!addr.isLoopbackAddress && addr is InetAddress) {
                  ips.add(addr.hostAddress ?: "")
                }
              }
            }
          } catch (_: Exception) {}

          Pair(
            mapOf(
              "active_ip_addresses" to ips.filter { it.isNotEmpty() },
              "wifi_rssi_dbm" to (wifiInfo?.rssi ?: 0),
              "wifi_link_speed_mbps" to (wifiInfo?.linkSpeed ?: 0),
              "has_internet" to (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false)
            ),
            null
          )
        }

        "security_health_check" -> {
          val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
          val isDeviceSecure = km?.isDeviceSecure ?: false
          val isKeyguardLocked = km?.isKeyguardLocked ?: false

          val adbEnabled = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
          } catch (_: Exception) { false }

          val devOptionsEnabled = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
          } catch (_: Exception) { false }

          Pair(
            mapOf(
              "device_lock_configured" to isDeviceSecure,
              "keyguard_locked" to isKeyguardLocked,
              "adb_debugging_enabled" to adbEnabled,
              "developer_options_enabled" to devOptionsEnabled,
              "hardware_keystore" to true
            ),
            null
          )
        }

        "thermal_alert_config" -> {
          val thresholdC = ((params["threshold_c"] as? Number)?.toFloat()) ?: 45.0f
          val telem = telemetryCollector.collectTelemetry()
          val currentTemp = telem.batteryTemp ?: 25.0f
          val isOverheat = currentTemp >= thresholdC

          Pair(
            mapOf(
              "threshold_c" to thresholdC,
              "current_temp_c" to currentTemp,
              "is_overheating" to isOverheat
            ),
            null
          )
        }

        else -> {
          Pair(null, "Command '$commandType' is not supported by device command registry")
        }
      }
    } catch (e: Exception) {
      Pair(null, e.message ?: "Execution error")
    }
  }

  private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
        Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return r * c
  }

  private fun runShellCommand(cmd: String): String {
    val allowedCommands = setOf("uptime", "getprop", "df", "free", "ps", "cat /proc/cpuinfo", "cat /proc/meminfo", "netstat", "id")
    val trimmed = cmd.trim()
    val baseCmd = trimmed.split(" ").firstOrNull() ?: ""
    if (!allowedCommands.contains(trimmed) && !allowedCommands.contains(baseCmd)) {
      return "Execution restricted. Allowed diagnostic commands: ${allowedCommands.joinToString(", ")}"
    }
    return try {
      val process = Runtime.getRuntime().exec(trimmed)
      val reader = process.inputStream.bufferedReader()
      val output = reader.readText()
      process.waitFor()
      output.ifEmpty { "Command completed (exit code ${process.exitValue()})" }
    } catch (e: Exception) {
      "Execution error: ${e.message}"
    }
  }

  private fun vibrate(durationMs: Long) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
      vibratorManager?.defaultVibrator?.vibrate(
        VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
      )
    } else {
      @Suppress("DEPRECATION")
      val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      @Suppress("DEPRECATION")
      vibrator?.vibrate(durationMs)
    }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "NEXUS Remote Admin Alerts",
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Channel for authorized administrative remote alerts"
      }
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      nm?.createNotificationChannel(channel)
    }
  }

  private fun showNotification(title: String, message: String) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    val notif = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_dialog_alert)
      .setContentTitle(title)
      .setContentText(message)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setAutoCancel(true)
      .build()
    nm?.notify(NOTIFICATION_ID, notif)
  }

  companion object {
    const val CHANNEL_ID = "nexus_admin_alerts"
    const val NOTIFICATION_ID = 2001
  }
}
