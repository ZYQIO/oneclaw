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
    assertEquals("0.12.0", snapshot.getValue("manifestVersion").jsonPrimitive.content)
    assertEquals(26, snapshot.getValue("manifestFileCount").jsonPrimitive.int)
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
    val versionDir = context.filesDir.resolve("openclaw/embedded-runtime-pod/0.12.0")

    assertTrue(inspection.ready)
    assertEquals("ready", inspection.reason)
    assertEquals(true, snapshot.getValue("available").jsonPrimitive.boolean)
    assertEquals(true, snapshot.getValue("ready").jsonPrimitive.boolean)
    assertEquals("ready", snapshot.getValue("reason").jsonPrimitive.content)
    assertEquals("0.12.0", snapshot.getValue("manifestVersion").jsonPrimitive.content)
    assertEquals(true, snapshot.getValue("manifestVersionExtracted").jsonPrimitive.boolean)
    assertEquals(1, snapshot.getValue("installedVersionCount").jsonPrimitive.int)
    assertEquals(26, snapshot.getValue("verifiedFileCount").jsonPrimitive.int)
    assertTrue(versionDir.resolve("manifest.json").isFile)
    assertTrue(versionDir.resolve("layout.json").isFile)
    assertTrue(versionDir.resolve("bridge/manifest.json").isFile)
    assertTrue(versionDir.resolve("browser/manifest.json").isFile)
    assertTrue(versionDir.resolve("desktop/manifest.json").isFile)
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
    assertEquals(6, payload.getValue("stageCount").jsonPrimitive.int)
    assertEquals(26, payload.getValue("fileCount").jsonPrimitive.int)
    assertEquals(true, payload.getValue("workspaceStageDeclared").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("workspaceStageInstalled").jsonPrimitive.boolean)
    assertEquals("0.12.0", payload.getValue("podManifest").jsonObject.getValue("version").jsonPrimitive.content)
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
    assertEquals(false, payload.getValue("desktopProfileReplayReady").jsonPrimitive.boolean)
    assertEquals(false, payload.getValue("desktopEnvironmentSupervisionReady").jsonPrimitive.boolean)
    assertEquals(1, payload.getValue("executionCount").jsonPrimitive.int)
    assertTrue(context.filesDir.resolve("openclaw/embedded-runtime-home/0.12.0/config/runtime-env.json").isFile)
    assertTrue(context.filesDir.resolve("openclaw/embedded-runtime-home/0.12.0/state/runtime-smoke.json").isFile)
  }

  @Test
  fun executeEmbeddedRuntimePodTask_replaysDesktopProfileAfterMaterialize() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-desktop-home").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)
    materializeEmbeddedRuntimeDesktopEnvironment(context, "openclaw-desktop-host")

    val result = executeEmbeddedRuntimePodTask(context, "runtime-smoke")

    assertTrue(result.ok)
    val payload = result.payload ?: error("expected payload")
    assertEquals(true, payload.getValue("desktopProfileReplayReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopEnvironmentSupervisionReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessModelReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessActivationReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessSupervisionReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessObservationReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessRecoveryReady").jsonPrimitive.boolean)
    assertEquals(false, payload.getValue("desktopLongLivedProcessReady").jsonPrimitive.boolean)
    assertEquals(
      "blocked",
      payload.getValue("desktopProcessStatus").jsonPrimitive.content,
    )
    assertEquals(
      "blocked",
      payload.getValue("desktopProcessActivationStatus").jsonPrimitive.content,
    )
    assertEquals(
      "blocked",
      payload.getValue("desktopProcessActivationState").jsonPrimitive.content,
    )
    assertEquals(1, payload.getValue("desktopProcessActivationGeneration").jsonPrimitive.int)
    assertEquals(
      "blocked",
      payload.getValue("desktopProcessSupervisionStatus").jsonPrimitive.content,
    )
    assertEquals(
      "blocked",
      payload.getValue("desktopProcessSupervisionState").jsonPrimitive.content,
    )
    assertEquals(1, payload.getValue("desktopProcessSupervisionGeneration").jsonPrimitive.int)
    assertEquals(
      "blocked",
      payload.getValue("desktopProcessObservationStatus").jsonPrimitive.content,
    )
    assertEquals(
      "awaiting_recovery",
      payload.getValue("desktopProcessObservationState").jsonPrimitive.content,
    )
    assertEquals(1, payload.getValue("desktopProcessObservationGeneration").jsonPrimitive.int)
    assertEquals(
      "restore_dependencies:browser",
      payload.getValue("desktopProcessObservationRecoveryHint").jsonPrimitive.content,
    )
    assertEquals(
      "planned",
      payload.getValue("desktopProcessRecoveryStatus").jsonPrimitive.content,
    )
    assertEquals(
      "restore_dependencies",
      payload.getValue("desktopProcessRecoveryState").jsonPrimitive.content,
    )
    assertEquals(1, payload.getValue("desktopProcessRecoveryGeneration").jsonPrimitive.int)
    assertEquals(2, payload.getValue("desktopProcessRecoveryActionCount").jsonPrimitive.int)
    assertEquals(
      "restore_dependency:browser",
      payload.getValue("desktopProcessRecoveryPrimaryAction").jsonPrimitive.content,
    )
    assertEquals(
      "missing_dependencies:browser",
      payload.getValue("desktopProcessRecoveryReason").jsonPrimitive.content,
    )
    assertEquals(
      "openclaw-desktop-host-runtime-smoke-1",
      payload.getValue("desktopProcessSessionId").jsonPrimitive.content,
    )
    assertEquals(
      "openclaw-desktop-host",
      payload.getValue("desktopProfileReplay").jsonObject.getValue("profileId").jsonPrimitive.content,
    )
    assertEquals(
      "degraded",
      payload.getValue("desktopProfileReplay").jsonObject.getValue("healthStatus").jsonPrimitive.content,
    )
    assertEquals(
      1,
      payload.getValue("desktopProfileReplay").jsonObject.getValue("restartGeneration").jsonPrimitive.int,
    )
    assertEquals(
      true,
      payload.getValue("desktopProfileReplay").jsonObject.getValue("processModelReady").jsonPrimitive.boolean,
    )
    assertEquals(
      "blocked",
      payload.getValue("desktopProfileReplay").jsonObject.getValue("processStatus").jsonPrimitive.content,
    )
    assertEquals(
      true,
      payload.getValue("desktopProfileReplay").jsonObject.getValue("processActivationReady").jsonPrimitive.boolean,
    )
    assertEquals(
      "blocked",
      payload.getValue("desktopProfileReplay").jsonObject.getValue("processActivationStatus").jsonPrimitive.content,
    )
    assertEquals(
      "blocked",
      payload.getValue("desktopProfileReplay").jsonObject.getValue("processActivationState").jsonPrimitive.content,
    )
    assertEquals(
      true,
      payload.getValue("desktopProfileReplay").jsonObject.getValue("processSupervisionReady").jsonPrimitive.boolean,
    )
    assertEquals(
      "blocked",
      payload.getValue("desktopProfileReplay").jsonObject.getValue("processSupervisionStatus").jsonPrimitive.content,
    )
    assertEquals(
      "blocked",
      payload.getValue("desktopProfileReplay").jsonObject.getValue("processSupervisionState").jsonPrimitive.content,
    )
    assertEquals(
      true,
      payload.getValue("desktopProfileReplay").jsonObject.getValue("processObservationReady").jsonPrimitive.boolean,
    )
    assertEquals(
      true,
      payload.getValue("desktopProfileReplay").jsonObject.getValue("processRecoveryReady").jsonPrimitive.boolean,
    )
    assertEquals(
      "blocked",
      payload.getValue("desktopProfileReplay").jsonObject.getValue("processObservationStatus").jsonPrimitive.content,
    )
    assertEquals(
      "awaiting_recovery",
      payload.getValue("desktopProfileReplay").jsonObject.getValue("processObservationState").jsonPrimitive.content,
    )
    assertEquals(
      "planned",
      payload.getValue("desktopProfileReplay").jsonObject.getValue("processRecoveryStatus").jsonPrimitive.content,
    )
    assertEquals(
      "restore_dependencies",
      payload.getValue("desktopProfileReplay").jsonObject.getValue("processRecoveryState").jsonPrimitive.content,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-runtime-home/0.12.0/work/runtime-smoke-desktop-profile.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.12.0/state/runtime-smoke-desktop-profile.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.12.0/state/runtime-smoke-health-report.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.12.0/state/runtime-smoke-restart-contract.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.12.0/state/runtime-smoke-process-model.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.12.0/state/runtime-smoke-activation-contract.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.12.0/state/runtime-smoke-supervision-contract.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.12.0/state/runtime-smoke-observation-contract.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.12.0/state/runtime-smoke-recovery-contract.json").isFile,
    )
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
    assertTrue(context.filesDir.resolve("openclaw/embedded-runtime-home/0.12.0/work/tool-brief-inspect-result.json").isFile)
  }

  @Test
  fun executeEmbeddedRuntimePodTask_runsAllowlistedPluginLane() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-desktop-home").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)

    val result = executeEmbeddedRuntimePodTask(context, "plugin-allowlist-inspect")

    assertTrue(result.ok)
    val payload = result.payload ?: error("expected payload")
    assertEquals("plugin-allowlist-inspect", payload.getValue("taskId").jsonPrimitive.content)
    assertEquals("openclaw-plugin-host-placeholder", payload.getValue("pluginId").jsonPrimitive.content)
    assertEquals(true, payload.getValue("desktopPluginsManifestPresent").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("packagedPluginDescriptorPresent").jsonPrimitive.boolean)
    assertEquals(
      "packaged_profile_descriptor",
      payload.getValue("pluginResult").jsonObject.getValue("profileSource").jsonPrimitive.content,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-runtime-home/0.12.0/work/plugin-allowlist-inspect-result.json").isFile,
    )
  }

  @Test
  fun materializeEmbeddedRuntimeDesktopEnvironment_materializesDesktopHome() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-desktop-home").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)

    val result = materializeEmbeddedRuntimeDesktopEnvironment(context, "openclaw-desktop-host")

    assertTrue(result.ok)
    val payload = result.payload ?: error("expected payload")
    assertEquals("openclaw-desktop-host", payload.getValue("profileId").jsonPrimitive.content)
    assertEquals(true, payload.getValue("desktopHomeReady").jsonPrimitive.boolean)
    assertTrue(context.filesDir.resolve("openclaw/embedded-desktop-home/0.12.0/engine/manifest.json").isFile)
    assertTrue(context.filesDir.resolve("openclaw/embedded-desktop-home/0.12.0/browser/manifest.json").isFile)
    assertTrue(context.filesDir.resolve("openclaw/embedded-desktop-home/0.12.0/tools/manifest.json").isFile)
    assertTrue(context.filesDir.resolve("openclaw/embedded-desktop-home/0.12.0/plugins/manifest.json").isFile)
    assertTrue(context.filesDir.resolve("openclaw/embedded-desktop-home/0.12.0/profiles/active-profile.json").isFile)
  }
}
