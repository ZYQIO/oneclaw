package ai.openclaw.app

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class EmbeddedRuntimePodStatusTest {
  @Test
  fun snapshot_reportsMissingAssetsWhenManifestIsAbsent() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()

    val snapshot = embeddedRuntimePodStatusSnapshot(context)

    assertFalse(snapshot.getValue("available").jsonPrimitive.boolean)
    assertFalse(snapshot.getValue("ready").jsonPrimitive.boolean)
    assertEquals("pod_assets_missing", snapshot.getValue("reason").jsonPrimitive.content)
    assertEquals("embedded-runtime-pod/manifest.json", snapshot.getValue("assetManifestPath").jsonPrimitive.content)
    assertEquals("filesDir/openclaw/embedded-runtime-pod", snapshot.getValue("installRoot").jsonPrimitive.content)
    assertFalse(snapshot.getValue("installRootExists").jsonPrimitive.boolean)
  }
}
