package com.example.nexusremote.telemetry

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.core.content.ContextCompat
import com.example.nexusremote.data.model.TelemetryData

class DeviceTelemetryCollector(private val context: Context) {

  fun collectTelemetry(): TelemetryData {
    return try {
      val batteryInfo = getBatteryInfo()
      val storageInfo = getStorageInfo()
      val networkInfo = getNetworkInfo()
      val ramInfo = getRamInfo()
      val loc = getLocationInfo()

      TelemetryData(
        batteryLevel = batteryInfo.level,
        isCharging = batteryInfo.isCharging,
        batteryTemp = batteryInfo.tempCelsius,
        storageFreeMb = storageInfo.freeMb,
        storageTotalMb = storageInfo.totalMb,
        networkType = networkInfo.type,
        wifiSsid = networkInfo.ssid,
        latitude = loc?.latitude,
        longitude = loc?.longitude,
        accuracyMeters = loc?.accuracy,
        ramFreeMb = ramInfo.freeMb,
        ramTotalMb = ramInfo.totalMb
      )
    } catch (_: Throwable) {
      TelemetryData(
        batteryLevel = 100,
        isCharging = false,
        batteryTemp = null,
        storageFreeMb = 1024L,
        storageTotalMb = 4096L,
        networkType = "CONNECTED",
        wifiSsid = null,
        latitude = null,
        longitude = null,
        accuracyMeters = null,
        ramFreeMb = 1024L,
        ramTotalMb = 4096L
      )
    }
  }

  private data class BatteryInfo(
    val level: Int,
    val isCharging: Boolean,
    val tempCelsius: Float?
  )

  private fun getBatteryInfo(): BatteryInfo {
    return try {
      val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
      val batteryStatus: Intent? = context.registerReceiver(null, ifilter)

      val level: Int = batteryStatus?.let { intent ->
        val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (rawLevel >= 0 && scale > 0) {
          (rawLevel * 100 / scale.toFloat()).toInt()
        } else {
          100
        }
      } ?: 100

      val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
      val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL

      val rawTemp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
      val tempCelsius = if (rawTemp > 0) rawTemp / 10.0f else null

      BatteryInfo(level, isCharging, tempCelsius)
    } catch (_: Throwable) {
      BatteryInfo(level = 100, isCharging = false, tempCelsius = null)
    }
  }

  private data class StorageInfo(
    val freeMb: Long,
    val totalMb: Long
  )

  private fun getStorageInfo(): StorageInfo {
    return try {
      val path = Environment.getDataDirectory()
      val stat = StatFs(path.path)
      val blockSize = stat.blockSizeLong
      val availableBlocks = stat.availableBlocksLong
      val totalBlocks = stat.blockCountLong

      val freeMb = (availableBlocks * blockSize) / (1024 * 1024)
      val totalMb = (totalBlocks * blockSize) / (1024 * 1024)
      StorageInfo(freeMb, totalMb)
    } catch (_: Throwable) {
      StorageInfo(0L, 0L)
    }
  }

  private data class NetworkInfo(
    val type: String,
    val ssid: String?
  )

  private fun getNetworkInfo(): NetworkInfo {
    return try {
      val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return NetworkInfo("UNKNOWN", null)

      val activeNetwork = cm.activeNetwork
      val capabilities = cm.getNetworkCapabilities(activeNetwork)

      when {
        capabilities == null -> NetworkInfo("DISCONNECTED", null)
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
          var ssid: String? = null
          try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val connectionInfo: WifiInfo? = wm?.connectionInfo
            if (connectionInfo != null && connectionInfo.ssid != null && connectionInfo.ssid != "<unknown ssid>") {
              ssid = connectionInfo.ssid.removePrefix("\"").removeSuffix("\"")
            }
          } catch (_: Throwable) {}
          NetworkInfo("WIFI", ssid ?: "Connected Wi-Fi")
        }
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
          NetworkInfo("CELLULAR", null)
        }
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
          NetworkInfo("ETHERNET", null)
        }
        else -> NetworkInfo("ACTIVE_LINK", null)
      }
    } catch (_: Throwable) {
      NetworkInfo("ACTIVE", null)
    }
  }

  private data class RamInfo(val freeMb: Long, val totalMb: Long)

  private fun getRamInfo(): RamInfo {
    return try {
      val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
      val memInfo = ActivityManager.MemoryInfo()
      am?.getMemoryInfo(memInfo)
      val free = memInfo.availMem / (1024 * 1024)
      val total = memInfo.totalMem / (1024 * 1024)
      RamInfo(free, total)
    } catch (_: Throwable) {
      RamInfo(0L, 0L)
    }
  }

  @SuppressLint("MissingPermission")
  fun getLocationInfo(): Location? {
    return try {
      val fineGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
      val coarseGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
      if (!fineGranted && !coarseGranted) return null

      val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
      var bestLocation: Location? = null

      val providers = try { lm.getProviders(true) } catch (_: Throwable) { emptyList<String>() }
      for (provider in providers) {
        try {
          val l = lm.getLastKnownLocation(provider) ?: continue
          if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
            bestLocation = l
          }
        } catch (_: Throwable) {}
      }
      bestLocation
    } catch (_: Throwable) {
      null
    }
  }
}
