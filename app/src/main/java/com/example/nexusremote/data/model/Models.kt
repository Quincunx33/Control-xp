package com.example.nexusremote.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RegisterDeviceRequest(
  val name: String,
  val model: String,
  @Json(name = "android_version") val androidVersion: String,
  @Json(name = "app_version") val appVersion: String,
  @Json(name = "screen_res") val screenRes: String
)

@JsonClass(generateAdapter = true)
data class RegisterDeviceResponse(
  @Json(name = "device_id") val deviceId: String,
  @Json(name = "device_key") val deviceKey: String,
  val status: String,
  val name: String,
  val model: String,
  @Json(name = "created_at") val createdAt: Double
)

@JsonClass(generateAdapter = true)
data class PairingRequest(
  @Json(name = "device_id") val deviceId: String,
  @Json(name = "device_info") val deviceInfo: String? = null
)

@JsonClass(generateAdapter = true)
data class PairingResponse(
  @Json(name = "pairing_id") val pairingId: String,
  val code: String,
  @Json(name = "device_id") val deviceId: String,
  @Json(name = "expires_in") val expiresIn: Int,
  @Json(name = "expires_at") val expiresAt: Double
)

@JsonClass(generateAdapter = true)
data class PairingStatusResponse(
  val pairing: PairingStatusData
)

@JsonClass(generateAdapter = true)
data class PairingStatusData(
  val id: String,
  val code: String,
  @Json(name = "device_id") val deviceId: String,
  @Json(name = "user_id") val userId: String?,
  val status: String,
  @Json(name = "expires_at") val expiresAt: Double
)

@JsonClass(generateAdapter = true)
data class ApprovePairingRequest(
  @Json(name = "pairing_id") val pairingId: String,
  @Json(name = "device_id") val deviceId: String
)

@JsonClass(generateAdapter = true)
data class TelemetryData(
  val batteryLevel: Int,
  val isCharging: Boolean,
  val batteryTemp: Float?,
  val storageFreeMb: Long,
  val storageTotalMb: Long,
  val networkType: String,
  val wifiSsid: String?,
  val latitude: Double? = null,
  val longitude: Double? = null,
  val accuracyMeters: Float? = null,
  val ramFreeMb: Long? = null,
  val ramTotalMb: Long? = null
)

@JsonClass(generateAdapter = true)
data class DevicePermissionItem(
  val permission: String,
  val name: String,
  val description: String,
  val isGranted: Boolean
)


@JsonClass(generateAdapter = true)
data class RegisterAccountRequest(
  val name: String,
  val email: String,
  val password: String
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
  val email: String,
  val password: String,
  @Json(name = "device_info") val deviceInfo: String
)

@JsonClass(generateAdapter = true)
data class AuthUser(
  val id: String,
  val name: String,
  val email: String
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
  val token: String,
  @Json(name = "expires_at") val expiresAt: Double,
  val user: AuthUser
)

@JsonClass(generateAdapter = true)
data class RegisterAccountResponse(
  val user: AuthUser,
  val message: String
)
