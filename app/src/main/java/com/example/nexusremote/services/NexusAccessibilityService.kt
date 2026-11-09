package com.example.nexusremote.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NexusAccessibilityService : AccessibilityService() {

  override fun onServiceConnected() {
    super.onServiceConnected()
    instance = this
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    // Event monitoring (idle)
  }

  override fun onInterrupt() {
    // Interrupted
  }

  override fun onDestroy() {
    if (instance == this) {
      instance = null
    }
    super.onDestroy()
  }

  fun performClick(x: Float, y: Float): Boolean {
    val path = Path().apply {
      moveTo(x, y)
    }
    val gesture = GestureDescription.Builder()
      .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
      .build()

    return dispatchGestureSynchronous(gesture)
  }

  fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean {
    val path = Path().apply {
      moveTo(x1, y1)
      lineTo(x2, y2)
    }
    val gesture = GestureDescription.Builder()
      .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(100)))
      .build()

    return dispatchGestureSynchronous(gesture)
  }

  fun performGlobalKeyAction(key: String): Boolean {
    val action = when (key.uppercase()) {
      "BACK" -> GLOBAL_ACTION_BACK
      "HOME" -> GLOBAL_ACTION_HOME
      "RECENTS" -> GLOBAL_ACTION_RECENTS
      "NOTIFICATIONS" -> GLOBAL_ACTION_NOTIFICATIONS
      "QUICK_SETTINGS" -> GLOBAL_ACTION_QUICK_SETTINGS
      "POWER_DIALOG" -> GLOBAL_ACTION_POWER_DIALOG
      "LOCK_SCREEN" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) GLOBAL_ACTION_LOCK_SCREEN else GLOBAL_ACTION_POWER_DIALOG
      else -> return false
    }
    return performGlobalAction(action)
  }

  private fun dispatchGestureSynchronous(gesture: GestureDescription): Boolean {
    var success = false
    val latch = CountDownLatch(1)

    dispatchGesture(gesture, object : GestureResultCallback() {
      override fun onCompleted(gestureDescription: GestureDescription?) {
        success = true
        latch.countDown()
      }

      override fun onCancelled(gestureDescription: GestureDescription?) {
        success = false
        latch.countDown()
      }
    }, null)

    try {
      latch.await(300, TimeUnit.MILLISECONDS)
    } catch (_: Exception) {}

    return success
  }

  companion object {
    var instance: NexusAccessibilityService? = null
      private set

    val isServiceActive: Boolean
      get() = instance != null
  }
}
