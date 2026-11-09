package com.example.nexusremote.permissions

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.nexusremote.data.model.DevicePermissionItem
import com.example.nexusremote.services.NexusAccessibilityService

class PermissionManager(private val context: Context) {

  fun getDeclaredPermissions(): List<DevicePermissionItem> {
    val items = mutableListOf<DevicePermissionItem>()

    // 1. Accessibility Service (Touch & Remote Gesture Control)
    items.add(
      DevicePermissionItem(
        permission = "android.permission.BIND_ACCESSIBILITY_SERVICE",
        name = "Nexus Accessibility Service",
        description = "Required for remote touch injection, swipe gestures, and virtual navigation key triggers.",
        isGranted = isAccessibilityServiceEnabled()
      )
    )

    // 2. Location (Fine & Coarse)
    val isLocationGranted = isGranted(Manifest.permission.ACCESS_FINE_LOCATION) || isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
    items.add(
      DevicePermissionItem(
        permission = Manifest.permission.ACCESS_FINE_LOCATION,
        name = "GPS & Network Location",
        description = "Provides real-time device coordinate mapping and geofence tracking.",
        isGranted = isLocationGranted
      )
    )

    // 3. Notifications (Android 13+)
    val isNotifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      isGranted(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      true
    }
    items.add(
      DevicePermissionItem(
        permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else "android.permission.POST_NOTIFICATIONS",
        name = "System Notifications",
        description = "Required to display persistent foreground status alerts and administrative notices.",
        isGranted = isNotifGranted
      )
    )

    // 4. Camera & Flashlight Strobe
    items.add(
      DevicePermissionItem(
        permission = Manifest.permission.CAMERA,
        name = "Camera Access & Flashlight Strobe",
        description = "Enables anti-theft camera strobe beacon and visual sensor diagnostics.",
        isGranted = isGranted(Manifest.permission.CAMERA)
      )
    )

    // 5. Audio / Microphone
    items.add(
      DevicePermissionItem(
        permission = Manifest.permission.RECORD_AUDIO,
        name = "Microphone Access",
        description = "Enables optional two-way audio streaming during WebRTC sessions.",
        isGranted = isGranted(Manifest.permission.RECORD_AUDIO)
      )
    )

    // 6. MediaProjection Screen Streaming
    items.add(
      DevicePermissionItem(
        permission = "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
        name = "MediaProjection Screen Sharing",
        description = "Android framework permission to stream screen content to authorized WebRTC sessions.",
        isGranted = true
      )
    )

    // 7. Vibrator
    items.add(
      DevicePermissionItem(
        permission = Manifest.permission.VIBRATE,
        name = "Haptic Feedback (Vibrator)",
        description = "Provides physical locator vibration alerts.",
        isGranted = isGranted(Manifest.permission.VIBRATE)
      )
    )

    return items
  }

  fun isGranted(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
  }

  fun isAccessibilityServiceEnabled(): Boolean {
    val enabledServicesSetting = Settings.Secure.getString(
      context.contentResolver,
      Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val pkgName = context.packageName
    val expectedComponentName = ComponentName(context, NexusAccessibilityService::class.java)

    return enabledServicesSetting.split(":").any { serviceStr ->
      serviceStr.contains(pkgName, ignoreCase = true) && serviceStr.contains("NexusAccessibilityService", ignoreCase = true) ||
          serviceStr.equals(expectedComponentName.flattenToString(), ignoreCase = true) ||
          serviceStr.equals(expectedComponentName.flattenToShortString(), ignoreCase = true)
    }
  }

  fun getRequiredRuntimePermissions(): Array<String> {
    val perms = mutableListOf<String>()

    if (!isGranted(Manifest.permission.ACCESS_FINE_LOCATION) && !isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
      perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
      perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    if (!isGranted(Manifest.permission.CAMERA)) {
      perms.add(Manifest.permission.CAMERA)
    }
    if (!isGranted(Manifest.permission.RECORD_AUDIO)) {
      perms.add(Manifest.permission.RECORD_AUDIO)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isGranted(Manifest.permission.POST_NOTIFICATIONS)) {
      perms.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    return perms.toTypedArray()
  }

  fun getPermissionsMap(): Map<String, Boolean> {
    return getDeclaredPermissions().associate { it.permission to it.isGranted }
  }

  fun openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
      data = Uri.fromParts("package", context.packageName, null)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  }

  fun openAccessibilitySettings() {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  }
}
