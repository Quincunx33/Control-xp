package com.example.nexusremote.network

import com.example.nexusremote.data.model.ApprovePairingRequest
import com.example.nexusremote.data.model.PairingRequest
import com.example.nexusremote.data.model.PairingResponse
import com.example.nexusremote.data.model.PairingStatusResponse
import com.example.nexusremote.data.model.RegisterDeviceRequest
import com.example.nexusremote.data.model.RegisterDeviceResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface NexusApiService {
  @POST("/api/v1/devices/register")
  suspend fun registerDevice(@Body req: RegisterDeviceRequest): Response<RegisterDeviceResponse>

  @POST("/api/v1/pairing/create")
  suspend fun createPairingRequest(@Body req: PairingRequest): Response<PairingResponse>

  @GET("/api/v1/pairing/{id}")
  suspend fun getPairingStatus(@Path("id") id: String): Response<PairingStatusResponse>

  @POST("/api/v1/pairing/approve")
  suspend fun approvePairing(@Body req: ApprovePairingRequest): Response<Map<String, Any>>

  @POST("/api/v1/pairing/reject")
  suspend fun rejectPairing(@Body req: ApprovePairingRequest): Response<Map<String, Any>>
}

object NexusApiClient {
  private val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

  fun createService(baseUrl: String): NexusApiService {
    val logging = HttpLoggingInterceptor().apply {
      level = HttpLoggingInterceptor.Level.BASIC
    }

    val okHttpClient = OkHttpClient.Builder()
      .addInterceptor(logging)
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .writeTimeout(15, TimeUnit.SECONDS)
      .build()

    val formattedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

    return Retrofit.Builder()
      .baseUrl(formattedBaseUrl)
      .client(okHttpClient)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(NexusApiService::class.java)
  }
}
