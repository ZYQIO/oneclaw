package ai.openclaw.app

import android.content.Context
import ai.openclaw.app.auth.OpenAICodexCredential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SecurePrefsTest {
  @Test
  fun loadLocationMode_migratesLegacyAlwaysValue() {
    val context = RuntimeEnvironment.getApplication()
    val plainPrefs = context.getSharedPreferences("openclaw.node", Context.MODE_PRIVATE)
    val securePrefs = context.getSharedPreferences("openclaw.node.secure.test.location", Context.MODE_PRIVATE)
    plainPrefs.edit().clear().putString("location.enabledMode", "always").commit()
    securePrefs.edit().clear().commit()

    val prefs = SecurePrefs(context, securePrefsOverride = securePrefs)

    assertEquals(LocationMode.WhileUsing, prefs.locationMode.value)
    assertEquals("whileUsing", plainPrefs.getString("location.enabledMode", null))
  }

  @Test
  fun saveGatewayBootstrapToken_persistsSeparatelyFromSharedToken() {
    val context = RuntimeEnvironment.getApplication()
    val securePrefs = context.getSharedPreferences("openclaw.node.secure.test", Context.MODE_PRIVATE)
    securePrefs.edit().clear().commit()
    val prefs = SecurePrefs(context, securePrefsOverride = securePrefs)

    prefs.setGatewayToken("shared-token")
    prefs.setGatewayBootstrapToken("bootstrap-token")

    assertEquals("shared-token", prefs.loadGatewayToken())
    assertEquals("bootstrap-token", prefs.loadGatewayBootstrapToken())
    assertEquals("bootstrap-token", prefs.gatewayBootstrapToken.value)
  }

  @Test
  fun gatewayConnectionMode_defaultsToRemoteAndPersistsLocalHost() {
    val context = RuntimeEnvironment.getApplication()
    val plainPrefs = context.getSharedPreferences("openclaw.node", Context.MODE_PRIVATE)
    val securePrefs = context.getSharedPreferences("openclaw.node.secure.test.gatewayMode", Context.MODE_PRIVATE)
    plainPrefs.edit().clear().commit()
    securePrefs.edit().clear().commit()

    val prefs = SecurePrefs(context, securePrefsOverride = securePrefs)
    assertEquals(GatewayConnectionMode.RemoteGateway, prefs.gatewayConnectionMode.value)

    prefs.setGatewayConnectionMode(GatewayConnectionMode.LocalHost)

    assertEquals(GatewayConnectionMode.LocalHost, prefs.gatewayConnectionMode.value)
    assertEquals("localHost", plainPrefs.getString("gateway.connection.mode", null))
  }

  @Test
  fun appLanguage_defaultsToEnglishAndPersistsChinese() {
    val context = RuntimeEnvironment.getApplication()
    val plainPrefs = context.getSharedPreferences("openclaw.node", Context.MODE_PRIVATE)
    val securePrefs = context.getSharedPreferences("openclaw.node.secure.test.language", Context.MODE_PRIVATE)
    plainPrefs.edit().clear().commit()
    securePrefs.edit().clear().commit()

    val prefs = SecurePrefs(context, securePrefsOverride = securePrefs)
    assertEquals(AppLanguage.English, prefs.appLanguage.value)

    prefs.setAppLanguage(AppLanguage.SimplifiedChinese)

    assertEquals(AppLanguage.SimplifiedChinese, prefs.appLanguage.value)
    assertEquals(AppLanguage.SimplifiedChinese.rawValue, plainPrefs.getString(SecurePrefs.appLanguageKey, null))
  }

  @Test
  fun localHostDedicatedDeployment_defaultsOffAndPersists() {
    val context = RuntimeEnvironment.getApplication()
    val plainPrefs = context.getSharedPreferences("openclaw.node", Context.MODE_PRIVATE)
    val securePrefs = context.getSharedPreferences("openclaw.node.secure.test.dedicated", Context.MODE_PRIVATE)
    plainPrefs.edit().clear().commit()
    securePrefs.edit().clear().commit()

    val prefs = SecurePrefs(context, securePrefsOverride = securePrefs)
    assertFalse(prefs.localHostDedicatedDeploymentEnabled.value)

    prefs.setLocalHostDedicatedDeploymentEnabled(true)

    assertTrue(prefs.localHostDedicatedDeploymentEnabled.value)
    assertTrue(plainPrefs.getBoolean(SecurePrefs.localHostDedicatedDeploymentEnabledKey, false))
  }

  @Test
  fun openAICodexCredential_roundTripsThroughSecurePrefs() {
    val context = RuntimeEnvironment.getApplication()
    val securePrefs = context.getSharedPreferences("openclaw.node.secure.test.codex", Context.MODE_PRIVATE)
    securePrefs.edit().clear().commit()
    val prefs = SecurePrefs(context, securePrefsOverride = securePrefs)
    val expected =
      OpenAICodexCredential(
        access = "access-token",
        refresh = "refresh-token",
        expires = 123456789L,
        accountId = "account-123",
        email = "test@example.com",
      )

    assertFalse(prefs.hasOpenAICodexCredential.value)
    assertNull(prefs.loadOpenAICodexCredential())

    prefs.saveOpenAICodexCredential(expected)

    val actual = prefs.loadOpenAICodexCredential()
    assertTrue(prefs.hasOpenAICodexCredential.value)
    assertNotNull(actual)
    assertEquals(expected, actual)

    prefs.clearOpenAICodexCredential()

    assertFalse(prefs.hasOpenAICodexCredential.value)
    assertNull(prefs.loadOpenAICodexCredential())
  }

  @Test
  fun localHostRemoteAccess_settingsAndTokenPersist() {
    val context = RuntimeEnvironment.getApplication()
    val plainPrefs = context.getSharedPreferences("openclaw.node", Context.MODE_PRIVATE)
    val securePrefs = context.getSharedPreferences("openclaw.node.secure.test.remote", Context.MODE_PRIVATE)
    plainPrefs.edit().clear().commit()
    securePrefs.edit().clear().commit()

    val prefs = SecurePrefs(context, securePrefsOverride = securePrefs)
    val initialToken = prefs.localHostRemoteAccessToken.value

    assertFalse(prefs.localHostRemoteAccessEnabled.value)
    assertFalse(prefs.localHostRemoteAccessAdvancedCommandsEnabled.value)
    assertFalse(prefs.localHostRemoteAccessWriteCommandsEnabled.value)
    assertEquals(3945, prefs.localHostRemoteAccessPort.value)
    assertTrue(initialToken.startsWith("ocrt_"))

    prefs.setLocalHostRemoteAccessEnabled(true)
    prefs.setLocalHostRemoteAccessAdvancedCommandsEnabled(true)
    prefs.setLocalHostRemoteAccessWriteCommandsEnabled(true)
    prefs.setLocalHostRemoteAccessPort(4123)
    val rotatedToken = prefs.regenerateLocalHostRemoteAccessToken()

    assertTrue(prefs.localHostRemoteAccessEnabled.value)
    assertTrue(prefs.localHostRemoteAccessAdvancedCommandsEnabled.value)
    assertTrue(prefs.localHostRemoteAccessWriteCommandsEnabled.value)
    assertEquals(4123, prefs.localHostRemoteAccessPort.value)
    assertEquals(rotatedToken, prefs.localHostRemoteAccessToken.value)
    assertTrue(rotatedToken.startsWith("ocrt_"))
    assertTrue(rotatedToken != initialToken)
    assertEquals(true, plainPrefs.getBoolean("localHost.remoteAccess.enabled", false))
    assertEquals(true, plainPrefs.getBoolean("localHost.remoteAccess.advancedCommands.enabled", false))
    assertEquals(true, plainPrefs.getBoolean("localHost.remoteAccess.writeCommands.enabled", false))
    assertEquals(4123, plainPrefs.getInt("localHost.remoteAccess.port", 0))
  }
}
