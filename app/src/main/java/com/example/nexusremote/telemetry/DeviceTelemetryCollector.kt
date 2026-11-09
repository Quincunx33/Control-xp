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
    val batteryInfo = getBatteryInfo()
    val storageInfo = getStorageInfo()
    val networkInfo = getNetworkInfo()
    val ramInfo = getRamInfo()
    val loc = getLocationInfo()

    return TelemetryData(
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
  }

  private data class BatteryInfo(
    val level: Int,
    val isCharging: Boolean,
    val tempCelsius: Float?
  )

  private fun getBatteryInfo(): BatteryInfo {
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

    return BatteryInfo(level, isCharging, tempCelsius)
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
    } catch (_: Exception) {
      StorageInfo(0L, 0L)
    }
  }

  private data class NetworkInfo(
    val type: String,
    val ssid: String?
  )

  private fun getNetworkInfo(): NetworkInfo {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    if (cm == null) return NetworkInfo("UNKNOWN", null)

    val activeNetwork = cm.activeNetwork
    val capabilities = cm.getNetworkCapabilities(activeNetwork)

    return when {
      capabilities == null -> NetworkInfo("DISCONNECTED", null)
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
        var ssid: String? = null
        try {
          val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
          val connectionInfo: WifiInfo? = wm?.connectionInfo
          if (connectionInfo != null && connectionInfo.ssid != null && connectionInfo.ssid != "<unknown ssid>") {
            ssid = connectionInfo.ssid.removePrefix("\"").removeSuffix("\"")
          }
        } catch (_: Exception) {}
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
    } catch (_: Exception) {
      RamInfo(0L, 0L)
    }
  }

  @SuppressLint("MissingPermission")
  fun getLocationInfo(): Location? {
    val fineGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!fineGranted && !coarseGranted) return null

    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    var bestLocation: Location? = null

    for (provider in lm.getProviders(true)) {
      try {
        val l = lm.getLastKnownLocation(provider) ?: continue
        if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
          bestLocation = l
        }
      } catch (_: Exception) {}
    }
    return bestLocation
  }
}
