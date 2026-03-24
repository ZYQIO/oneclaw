package ai.openclaw.app

import android.content.Context
import android.os.Build
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
class DedicatedHostSupportTest {
  private var originalManufacturer: String? = null
  private var originalBrand: String? = null

  @Before
  fun setUp() {
    originalManufacturer = Build.MANUFACTURER
    originalBrand = Build.BRAND
  }

  @After
  fun tearDown() {
    ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", originalManufacturer)
    ReflectionHelpers.setStaticField(Build::class.java, "BRAND", originalBrand)
  }

  @Test
  fun backgroundPolicyNote_flagsOppoRecentsRisk() {
    setBuildManufacturer(manufacturer = "OPPO", brand = "OPPO")

    assertTrue(dedicatedHostRecentsSwipeForceStopRisk())
    assertNotNull(dedicatedHostBackgroundPolicyNote())
    assertTrue(dedicatedHostBackgroundPolicyNote()!!.contains("Recents"))
  }

  @Test
  fun deploymentStatusSnapshot_includesTaskLockGuidanceForOppo() {
    setBuildManufacturer(manufacturer = "OPPO", brand = "OPPO")
    val context = RuntimeEnvironment.getApplication()
    val prefs = securePrefs(context, name = "openclaw.node.secure.test.dedicated.support")
    prefs.setGatewayConnectionMode(GatewayConnectionMode.LocalHost)
    prefs.setOnboardingCompleted(true)
    prefs.setLocalHostDedicatedDeploymentEnabled(true)
    prefs.setLocalHostRemoteAccessEnabled(true)

    val snapshot = dedicatedHostDeploymentStatusSnapshot(context, prefs)

    assertEquals("OPPO", snapshot.getValue("manufacturer").jsonPrimitive.content)
    assertTrue(snapshot.getValue("recentsSwipeForceStopRisk").jsonPrimitive.boolean)
    assertTrue(snapshot.getValue("taskLockRecommended").jsonPrimitive.boolean)
    assertTrue(snapshot.getValue("backgroundPolicyNote").jsonPrimitive.content.contains("force-stop"))
  }

  @Test
  fun deploymentStatusSnapshot_leavesTaskLockGuidanceOffForGenericManufacturer() {
    setBuildManufacturer(manufacturer = "Google", brand = "google")
    val context = RuntimeEnvironment.getApplication()
    val prefs = securePrefs(context, name = "openclaw.node.secure.test.dedicated.generic")

    val snapshot = dedicatedHostDeploymentStatusSnapshot(context, prefs)

    assertEquals("Google", snapshot.getValue("manufacturer").jsonPrimitive.content)
    assertFalse(snapshot.getValue("recentsSwipeForceStopRisk").jsonPrimitive.boolean)
    assertFalse(snapshot.getValue("taskLockRecommended").jsonPrimitive.boolean)
    assertFalse(snapshot.containsKey("backgroundPolicyNote"))
  }

  private fun setBuildManufacturer(manufacturer: String, brand: String) {
    ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", manufacturer)
    ReflectionHelpers.setStaticField(Build::class.java, "BRAND", brand)
  }

  private fun securePrefs(context: Context, name: String): SecurePrefs {
    val securePrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    securePrefs.edit().clear().commit()
    return SecurePrefs(context, securePrefsOverride = securePrefs)
  }
}
