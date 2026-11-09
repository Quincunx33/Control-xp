package com.example.nexusremote.security

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SirenController(private val context: Context) {
  private var mediaPlayer: MediaPlayer? = null
  private var strobeJob: Job? = null
  private val scope = CoroutineScope(Dispatchers.Default)

  fun startSiren(): Boolean {
    stopSiren()
    try {
      // 1. Maximize Alarm stream volume
      val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
      audioManager?.let { am ->
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        am.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
      }

      // 2. Play alarm tone on loop
      val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

      mediaPlayer = MediaPlayer().apply {
        setDataSource(context, alertUri)
        setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        )
        isLooping = true
        prepare()
        start()
      }

      // 3. Strobe flashlight if camera torch is available
      startStrobe()
      return true
    } catch (_: Exception) {
      return false
    }
  }

  private fun startStrobe() {
    strobeJob?.cancel()
    strobeJob = scope.launch {
      val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
      val cameraId = try {
        cm?.cameraIdList?.firstOrNull()
      } catch (_: Exception) {
        null
      }

      if (cm != null && cameraId != null) {
        var torchOn = false
        while (isActive) {
          try {
            torchOn = !torchOn
            cm.setTorchMode(cameraId, torchOn)
          } catch (_: Exception) {}
          delay(200)
        }
        try {
          cm.setTorchMode(cameraId, false)
        } catch (_: Exception) {}
      }
    }
  }

  fun stopSiren() {
    try {
      strobeJob?.cancel()
      strobeJob = null

      val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
      val cameraId = cm?.cameraIdList?.firstOrNull()
      if (cm != null && cameraId != null) {
        try {
          cm.setTorchMode(cameraId, false)
        } catch (_: Exception) {}
      }

      mediaPlayer?.stop()
      mediaPlayer?.release()
      mediaPlayer = null
    } catch (_: Exception) {}
  }
}
