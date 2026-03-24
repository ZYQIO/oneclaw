package ai.openclaw.app

import android.app.Application
import android.content.Context
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalHostBootReceiverTest {
  private lateinit var context: Application

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    context.getSharedPreferences(SecurePrefs.plainPrefsName, Context.MODE_PRIVATE).edit().clear().commit()
    shadowOf(context).clearStartedServices()
  }

  @Test
  fun shouldKeepAlive_requiresDedicatedDeploymentOnboardingAndLocalHost() {
    writeDedicatedPrefs(
      dedicatedEnabled = false,
      onboardingCompleted = true,
      connectionMode = GatewayConnectionMode.LocalHost,
    )
    assertFalse(LocalHostDedicatedDeploymentManager.shouldKeepAlive(context))

    writeDedicatedPrefs(
      dedicatedEnabled = true,
      onboardingCompleted = false,
      connectionMode = GatewayConnectionMode.LocalHost,
    )
    assertFalse(LocalHostDedicatedDeploymentManager.shouldKeepAlive(context))

    writeDedicatedPrefs(
      dedicatedEnabled = true,
      onboardingCompleted = true,
      connectionMode = GatewayConnectionMode.RemoteGateway,
    )
    assertFalse(LocalHostDedicatedDeploymentManager.shouldKeepAlive(context))

    writeDedicatedPrefs(
      dedicatedEnabled = true,
      onboardingCompleted = true,
      connectionMode = GatewayConnectionMode.LocalHost,
    )
    assertTrue(LocalHostDedicatedDeploymentManager.shouldKeepAlive(context))
  }

  @Test
  fun ensureServiceIfNeeded_startsForegroundServiceWhenKeepAliveIsEnabled() {
    writeDedicatedPrefs(
      dedicatedEnabled = true,
      onboardingCompleted = true,
      connectionMode = GatewayConnectionMode.LocalHost,
    )

    LocalHostDedicatedDeploymentManager.ensureServiceIfNeeded(context)

    val startedIntent = shadowOf(context).peekNextStartedService()
    assertNotNull(startedIntent)
    assertEquals(NodeForegroundService::class.java.name, startedIntent?.component?.className)
  }

  @Test
  fun bootReceiver_startsForegroundServiceOnBootCompleted() {
    writeDedicatedPrefs(
      dedicatedEnabled = true,
      onboardingCompleted = true,
      connectionMode = GatewayConnectionMode.LocalHost,
    )

    LocalHostBootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

    val startedIntent = shadowOf(context).peekNextStartedService()
    assertNotNull(startedIntent)
    assertEquals(NodeForegroundService::class.java.name, startedIntent?.component?.className)
  }

  @Test
  fun bootReceiver_ignoresBootWhenDedicatedKeepAliveIsOff() {
    writeDedicatedPrefs(
      dedicatedEnabled = false,
      onboardingCompleted = true,
      connectionMode = GatewayConnectionMode.LocalHost,
    )

    LocalHostBootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

    assertNull(shadowOf(context).peekNextStartedService())
  }

  private fun writeDedicatedPrefs(
    dedicatedEnabled: Boolean,
    onboardingCompleted: Boolean,
    connectionMode: GatewayConnectionMode,
  ) {
    context
      .getSharedPreferences(SecurePrefs.plainPrefsName, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(SecurePrefs.localHostDedicatedDeploymentEnabledKey, dedicatedEnabled)
      .putBoolean(SecurePrefs.onboardingCompletedKey, onboardingCompleted)
      .putString(SecurePrefs.gatewayConnectionModeKey, connectionMode.rawValue)
      .commit()
    shadowOf(context).clearStartedServices()
  }
}
