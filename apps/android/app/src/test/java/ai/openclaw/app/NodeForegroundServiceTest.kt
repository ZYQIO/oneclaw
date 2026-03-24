package ai.openclaw.app

import android.app.AlarmManager
import android.app.Notification
import android.content.Context
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NodeForegroundServiceTest {
  @Before
  fun setUp() {
    val app = RuntimeEnvironment.getApplication() as Context
    app.getSharedPreferences(SecurePrefs.plainPrefsName, Context.MODE_PRIVATE).edit().clear().commit()
    ShadowAlarmManager.reset()
  }

  @Test
  fun buildNotificationSetsLaunchIntent() {
    val service = Robolectric.buildService(NodeForegroundService::class.java).get()
    val notification = buildNotification(service)

    val pendingIntent = notification.contentIntent
    assertNotNull(pendingIntent)

    val savedIntent = Shadows.shadowOf(pendingIntent).savedIntent
    assertNotNull(savedIntent)
    assertEquals(MainActivity::class.java.name, savedIntent.component?.className)

    val expectedFlags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    assertEquals(expectedFlags, savedIntent.flags and expectedFlags)
  }

  @Test
  @Suppress("DEPRECATION")
  fun onTaskRemoved_schedulesDedicatedRecoveryAlarm() {
    val app = RuntimeEnvironment.getApplication() as Context
    app
      .getSharedPreferences(SecurePrefs.plainPrefsName, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(SecurePrefs.onboardingCompletedKey, true)
      .putString(SecurePrefs.gatewayConnectionModeKey, GatewayConnectionMode.LocalHost.rawValue)
      .putBoolean(SecurePrefs.localHostDedicatedDeploymentEnabledKey, true)
      .commit()

    val service = Robolectric.buildService(NodeForegroundService::class.java).get()
    service.onTaskRemoved(null)

    val alarmManager = app.getSystemService(AlarmManager::class.java)
    val scheduledAlarm = Shadows.shadowOf(alarmManager).peekNextScheduledAlarm()
    assertNotNull(scheduledAlarm)
    val operation = scheduledAlarm?.operation
    assertNotNull(operation)
    val savedIntent = Shadows.shadowOf(operation).savedIntent
    assertEquals(NodeForegroundService::class.java.name, savedIntent.component?.className)
    assertTrue(scheduledAlarm?.isAllowWhileIdle() == true)
  }

  @Test
  @Suppress("DEPRECATION")
  fun onStartCommand_schedulesDedicatedWatchdogAlarm() {
    val app = RuntimeEnvironment.getApplication() as Context
    app
      .getSharedPreferences(SecurePrefs.plainPrefsName, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(SecurePrefs.onboardingCompletedKey, true)
      .putString(SecurePrefs.gatewayConnectionModeKey, GatewayConnectionMode.LocalHost.rawValue)
      .putBoolean(SecurePrefs.localHostDedicatedDeploymentEnabledKey, true)
      .commit()

    val service = Robolectric.buildService(NodeForegroundService::class.java).get()
    service.onStartCommand(null, 0, 1)

    val alarmManager = app.getSystemService(AlarmManager::class.java)
    val scheduledAlarm = Shadows.shadowOf(alarmManager).peekNextScheduledAlarm()
    assertNotNull(scheduledAlarm)
    val operation = scheduledAlarm?.operation
    assertNotNull(operation)
    val savedIntent = Shadows.shadowOf(operation).savedIntent
    assertEquals(NodeForegroundService::class.java.name, savedIntent.component?.className)
    assertEquals("ai.openclaw.app.action.DEDICATED_HOST_WATCHDOG", savedIntent.action)
    assertTrue(scheduledAlarm?.isAllowWhileIdle() == true)
  }

  @Test
  fun onTaskRemoved_doesNotScheduleRecoveryWhenDedicatedModeIsOff() {
    val app = RuntimeEnvironment.getApplication() as Context
    app
      .getSharedPreferences(SecurePrefs.plainPrefsName, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(SecurePrefs.onboardingCompletedKey, true)
      .putString(SecurePrefs.gatewayConnectionModeKey, GatewayConnectionMode.LocalHost.rawValue)
      .putBoolean(SecurePrefs.localHostDedicatedDeploymentEnabledKey, false)
      .commit()

    val service = Robolectric.buildService(NodeForegroundService::class.java).get()
    service.onTaskRemoved(null)

    val alarmManager = app.getSystemService(AlarmManager::class.java)
    assertNull(Shadows.shadowOf(alarmManager).peekNextScheduledAlarm())
  }

  @Test
  fun onStartCommand_doesNotScheduleWatchdogWhenDedicatedModeIsOff() {
    val app = RuntimeEnvironment.getApplication() as Context
    app
      .getSharedPreferences(SecurePrefs.plainPrefsName, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(SecurePrefs.onboardingCompletedKey, true)
      .putString(SecurePrefs.gatewayConnectionModeKey, GatewayConnectionMode.LocalHost.rawValue)
      .putBoolean(SecurePrefs.localHostDedicatedDeploymentEnabledKey, false)
      .commit()

    val service = Robolectric.buildService(NodeForegroundService::class.java).get()
    service.onStartCommand(null, 0, 1)

    val alarmManager = app.getSystemService(AlarmManager::class.java)
    assertNull(Shadows.shadowOf(alarmManager).peekNextScheduledAlarm())
  }

  private fun buildNotification(service: NodeForegroundService): Notification {
    val method =
      NodeForegroundService::class.java.getDeclaredMethod(
        "buildNotification",
        String::class.java,
        String::class.java,
      )
    method.isAccessible = true
    return method.invoke(service, "Title", "Text") as Notification
  }
}
