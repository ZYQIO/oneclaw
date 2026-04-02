package ai.openclaw.app

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class EmbeddedRuntimePodStatusTest {
  @Test
  fun snapshot_reportsVersionNotExtractedBeforeInstall() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()

    val snapshot = embeddedRuntimePodStatusSnapshot(context)

    assertTrue(snapshot.getValue("available").jsonPrimitive.boolean)
    assertTrue(snapshot.getValue("assetManifestPresent").jsonPrimitive.boolean)
    assertEquals(false, snapshot.getValue("ready").jsonPrimitive.boolean)
    assertEquals("not_extracted", snapshot.getValue("reason").jsonPrimitive.content)
    assertEquals("embedded-runtime-pod/manifest.json", snapshot.getValue("assetManifestPath").jsonPrimitive.content)
    assertEquals("filesDir/openclaw/embedded-runtime-pod", snapshot.getValue("installRoot").jsonPrimitive.content)
    assertEquals("0.1.0", snapshot.getValue("manifestVersion").jsonPrimitive.content)
    assertEquals(3, snapshot.getValue("manifestFileCount").jsonPrimitive.int)
    assertEquals(false, snapshot.getValue("installRootExists").jsonPrimitive.boolean)
    assertEquals(0, snapshot.getValue("verifiedFileCount").jsonPrimitive.int)
  }

  @Test
  fun snapshot_reportsReadyAfterInstall() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()

    val inspection = ensureEmbeddedRuntimePodInstalled(context)
    val snapshot = embeddedRuntimePodStatusSnapshot(context)
    val versionDir = context.filesDir.resolve("openclaw/embedded-runtime-pod/0.1.0")

    assertTrue(inspection.ready)
    assertEquals("ready", inspection.reason)
    assertEquals(true, snapshot.getValue("available").jsonPrimitive.boolean)
    assertEquals(true, snapshot.getValue("ready").jsonPrimitive.boolean)
    assertEquals("ready", snapshot.getValue("reason").jsonPrimitive.content)
    assertEquals("0.1.0", snapshot.getValue("manifestVersion").jsonPrimitive.content)
    assertEquals(true, snapshot.getValue("manifestVersionExtracted").jsonPrimitive.boolean)
    assertEquals(1, snapshot.getValue("installedVersionCount").jsonPrimitive.int)
    assertEquals(3, snapshot.getValue("verifiedFileCount").jsonPrimitive.int)
    assertTrue(versionDir.resolve("manifest.json").isFile)
    assertTrue(versionDir.resolve("layout.json").isFile)
    assertTrue(versionDir.resolve("bridge/manifest.json").isFile)
  }
}
