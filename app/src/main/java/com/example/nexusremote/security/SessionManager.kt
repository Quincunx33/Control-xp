package com.example.nexusremote.security

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

class SessionManager(context: Context) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences("nexus_remote_agent_prefs", Context.MODE_PRIVATE)

  var serverUrl: String
    get() = prefs.getString("server_url", "https://ais-dev-uvj7ghc5qbsxb3devsdsgw-360203191339.asia-east1.run.app") ?: "https://ais-dev-uvj7ghc5qbsxb3devsdsgw-360203191339.asia-east1.run.app"
    set(value) = prefs.edit().putString("server_url", value.trim().removeSuffix("/")).apply()

  var deviceId: String?
    get() = prefs.getString("device_id", null)
    set(value) = prefs.edit().putString("device_id", value).apply()

  var deviceKey: String?
    get() = prefs.getString("device_key", null)
    set(value) = prefs.edit().putString("device_key", value).apply()

  var deviceName: String
    get() = prefs.getString("device_name", android.os.Build.MODEL) ?: android.os.Build.MODEL
    set(value) = prefs.edit().putString("device_name", value).apply()

  var deviceStatus: String
    get() = prefs.getString("device_status", "UNREGISTERED") ?: "UNREGISTERED"
    set(value) = prefs.edit().putString("device_status", value).apply()

  var isRegistered: Boolean
    get() = deviceId != null && deviceKey != null
    set(_) {}

  var operatorToken: String?
    get() = prefs.getString("operator_token", null)
    set(value) = prefs.edit().putString("operator_token", value).apply()

  fun getOrCreateInstallationId(): String {
    val existing = prefs.getString("installation_uuid", null)
    if (existing != null) return existing
    val newId = UUID.randomUUID().toString()
    prefs.edit().putString("installation_uuid", newId).apply()
    return newId
  }

  fun clearCredentials() {
    prefs.edit()
      .remove("device_id")
      .remove("device_key")
      .remove("operator_token")
      .putString("device_status", "UNREGISTERED")
      .apply()
  }
}
