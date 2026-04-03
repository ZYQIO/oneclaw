package ai.openclaw.app

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
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
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()

    val snapshot = embeddedRuntimePodStatusSnapshot(context)

    assertTrue(snapshot.getValue("available").jsonPrimitive.boolean)
    assertTrue(snapshot.getValue("assetManifestPresent").jsonPrimitive.boolean)
    assertEquals(false, snapshot.getValue("ready").jsonPrimitive.boolean)
    assertEquals("not_extracted", snapshot.getValue("reason").jsonPrimitive.content)
    assertEquals("embedded-runtime-pod/manifest.json", snapshot.getValue("assetManifestPath").jsonPrimitive.content)
    assertEquals("filesDir/openclaw/embedded-runtime-pod", snapshot.getValue("installRoot").jsonPrimitive.content)
    assertEquals("0.4.0", snapshot.getValue("manifestVersion").jsonPrimitive.content)
    assertEquals(14, snapshot.getValue("manifestFileCount").jsonPrimitive.int)
    assertEquals(false, snapshot.getValue("installRootExists").jsonPrimitive.boolean)
    assertEquals(0, snapshot.getValue("verifiedFileCount").jsonPrimitive.int)
  }

  @Test
  fun snapshot_reportsReadyAfterInstall() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()

    val inspection = ensureEmbeddedRuntimePodInstalled(context)
    val snapshot = embeddedRuntimePodStatusSnapshot(context)
    val versionDir = context.filesDir.resolve("openclaw/embedded-runtime-pod/0.4.0")

    assertTrue(inspection.ready)
    assertEquals("ready", inspection.reason)
    assertEquals(true, snapshot.getValue("available").jsonPrimitive.boolean)
    assertEquals(true, snapshot.getValue("ready").jsonPrimitive.boolean)
    assertEquals("ready", snapshot.getValue("reason").jsonPrimitive.content)
    assertEquals("0.4.0", snapshot.getValue("manifestVersion").jsonPrimitive.content)
    assertEquals(true, snapshot.getValue("manifestVersionExtracted").jsonPrimitive.boolean)
    assertEquals(1, snapshot.getValue("installedVersionCount").jsonPrimitive.int)
    assertEquals(14, snapshot.getValue("verifiedFileCount").jsonPrimitive.int)
    assertTrue(versionDir.resolve("manifest.json").isFile)
    assertTrue(versionDir.resolve("layout.json").isFile)
    assertTrue(versionDir.resolve("bridge/manifest.json").isFile)
    assertTrue(versionDir.resolve("runtime/manifest.json").isFile)
    assertTrue(versionDir.resolve("workspace/content-index.json").isFile)
  }

  @Test
  fun describeEmbeddedRuntimePodManifest_reportsInstalledManifestAndLayout() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)

    val describeResult = describeEmbeddedRuntimePodManifest(context)

    assertTrue(describeResult.ok)
    val payload = describeResult.payload ?: error("expected payload")
    assertEquals("installed", payload.getValue("manifestSource").jsonPrimitive.content)
    assertEquals("installed", payload.getValue("layoutSource").jsonPrimitive.content)
    assertEquals(4, payload.getValue("stageCount").jsonPrimitive.int)
    assertEquals(14, payload.getValue("fileCount").jsonPrimitive.int)
    assertEquals(true, payload.getValue("workspaceStageDeclared").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("workspaceStageInstalled").jsonPrimitive.boolean)
    assertEquals("0.4.0", payload.getValue("podManifest").jsonObject.getValue("version").jsonPrimitive.content)
  }

  @Test
  fun executeEmbeddedRuntimePodTask_materializesRuntimeHome() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)

    val result = executeEmbeddedRuntimePodTask(context, "runtime-smoke")

    assertTrue(result.ok)
    val payload = result.payload ?: error("expected payload")
    assertEquals("runtime-smoke", payload.getValue("taskId").jsonPrimitive.content)
    assertEquals(true, payload.getValue("runtimeHomeReady").jsonPrimitive.boolean)
    assertEquals(1, payload.getValue("executionCount").jsonPrimitive.int)
    assertTrue(context.filesDir.resolve("openclaw/embedded-runtime-home/0.4.0/config/runtime-env.json").isFile)
    assertTrue(context.filesDir.resolve("openclaw/embedded-runtime-home/0.4.0/state/runtime-smoke.json").isFile)
  }

  @Test
  fun executeEmbeddedRuntimePodTask_runsPackagedDesktopTool() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)

    val result = executeEmbeddedRuntimePodTask(context, "tool-brief-inspect")

    assertTrue(result.ok)
    val payload = result.payload ?: error("expected payload")
    assertEquals("tool-brief-inspect", payload.getValue("taskId").jsonPrimitive.content)
    assertEquals("packaged-brief-inspector-v1", payload.getValue("toolId").jsonPrimitive.content)
    assertEquals(true, payload.getValue("toolkitCommandPolicyPresent").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("packagedToolDescriptorPresent").jsonPrimitive.boolean)
    assertEquals(1, payload.getValue("toolResult").jsonObject.getValue("headingCount").jsonPrimitive.int)
    assertTrue(context.filesDir.resolve("openclaw/embedded-runtime-home/0.4.0/work/tool-brief-inspect-result.json").isFile)
  }
}
