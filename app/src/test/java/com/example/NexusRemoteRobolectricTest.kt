package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.nexusremote.commands.CommandExecutor
import com.example.nexusremote.security.SessionManager
import com.example.nexusremote.telemetry.DeviceTelemetryCollector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NexusRemoteRobolectricTest {

  @Test
  fun testAppNameResource() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Control-XP", appName)
  }

  @Test
  fun testSessionManagerPersistence() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sessionManager = SessionManager(context)

    sessionManager.serverUrl = "https://remote.nexus.example.com/"
    assertEquals("https://remote.nexus.example.com", sessionManager.serverUrl)

    val installId = sessionManager.getOrCreateInstallationId()
    assertNotNull(installId)
    assertEquals(installId, sessionManager.getOrCreateInstallationId())

    sessionManager.deviceId = "dev_test_abc"
    sessionManager.deviceKey = "nxdev_secret"
    assertTrue(sessionManager.isRegistered)

    sessionManager.clearCredentials()
    assertNull(sessionManager.deviceId)
    assertEquals("UNREGISTERED", sessionManager.deviceStatus)
  }

  @Test
  fun testCommandExecutorPing() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val telemetryCollector = DeviceTelemetryCollector(context)
    val executor = CommandExecutor(context, telemetryCollector) {}

    val (result, error) = executor.execute("ping", emptyMap())
    assertNull(error)
    assertNotNull(result)
    assertEquals("pong", result?.get("status"))

    // Test unknown command rejection
    val (unknownRes, unknownErr) = executor.execute("unauthorized_shell_command", emptyMap())
    assertNull(unknownRes)
    assertNotNull(unknownErr)
  }

  @Test
  fun testRequiredRuntimePermissionsContainsStorageAndPhotos() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val permissionManager = com.example.nexusremote.permissions.PermissionManager(context)
    val requiredPerms = permissionManager.getRequiredRuntimePermissions().toList()

    // Verify storage/media permissions are present when ungranted
    assertTrue(
      requiredPerms.contains(android.Manifest.permission.READ_MEDIA_IMAGES) ||
      requiredPerms.contains(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    )
  }
}
