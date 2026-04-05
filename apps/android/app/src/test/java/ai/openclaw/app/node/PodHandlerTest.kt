package ai.openclaw.app.node

import ai.openclaw.app.ensureEmbeddedRuntimePodInstalled
import ai.openclaw.app.executeEmbeddedRuntimePodTask
import ai.openclaw.app.materializeEmbeddedRuntimeDesktopEnvironment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PodHandlerTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun handlePodHealth_reportsMissingExtractionWhenVersionIsAbsent() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    val handler = PodHandler(context)

    val result = handler.handlePodHealth(null)

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals("pod.health", payload.getValue("command").jsonPrimitive.content)
    assertEquals(false, payload.getValue("localExecutionAvailable").jsonPrimitive.boolean)
    assertEquals("not_extracted", payload.getValue("reason").jsonPrimitive.content)
    assertEquals(false, payload.getValue("ready").jsonPrimitive.boolean)
    assertEquals(0, payload.getValue("verifiedFileCount").jsonPrimitive.int)
  }

  @Test
  fun handlePodHealth_reportsReadyAfterInstallation() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)
    val handler = PodHandler(context)

    val result = handler.handlePodHealth(null)

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals(true, payload.getValue("localExecutionAvailable").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("ready").jsonPrimitive.boolean)
    assertEquals("ready", payload.getValue("reason").jsonPrimitive.content)
    assertEquals(26, payload.getValue("verifiedFileCount").jsonPrimitive.int)
    assertEquals("0.10.0", payload.getValue("installedVersions").jsonArray.single().jsonPrimitive.content)
  }

  @Test
  fun handlePodManifestDescribe_prefersInstalledMetadataAfterInstall() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)
    val handler = PodHandler(context)

    val result = handler.handlePodManifestDescribe(null)

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals("pod.manifest.describe", payload.getValue("command").jsonPrimitive.content)
    assertEquals(true, payload.getValue("localExecutionAvailable").jsonPrimitive.boolean)
    assertEquals("installed", payload.getValue("manifestSource").jsonPrimitive.content)
    assertEquals("installed", payload.getValue("layoutSource").jsonPrimitive.content)
    assertEquals(6, payload.getValue("stageCount").jsonPrimitive.int)
    assertEquals(26, payload.getValue("fileCount").jsonPrimitive.int)
    assertEquals(true, payload.getValue("workspaceStageDeclared").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("workspaceStageInstalled").jsonPrimitive.boolean)
    val stageNames = payload.getValue("stageNames").jsonArray.map { it.jsonPrimitive.content }
    assertTrue("browser" in stageNames)
    assertTrue("workspace" in stageNames)
    assertTrue("runtime" in stageNames)
    val fileStageCounts = payload.getValue("fileStageCounts").jsonObject
    assertEquals(2, fileStageCounts.getValue("browser").jsonPrimitive.int)
    assertEquals(5, fileStageCounts.getValue("workspace").jsonPrimitive.int)
    assertEquals(6, fileStageCounts.getValue("runtime").jsonPrimitive.int)
    assertEquals(9, fileStageCounts.getValue("desktop").jsonPrimitive.int)
    assertEquals(3, fileStageCounts.getValue("toolkit").jsonPrimitive.int)
    val podManifest = payload.getValue("podManifest").jsonObject
    assertEquals("0.10.0", podManifest.getValue("version").jsonPrimitive.content)
    val podLayout = payload.getValue("podLayout").jsonObject
    assertEquals("0.10.0", podLayout.getValue("version").jsonPrimitive.content)
  }

  @Test
  fun handlePodManifestDescribe_fallsBackToBundledMetadataBeforeInstall() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    val handler = PodHandler(context)

    val result = handler.handlePodManifestDescribe(null)

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals(false, payload.getValue("localExecutionAvailable").jsonPrimitive.boolean)
    assertEquals("bundled", payload.getValue("manifestSource").jsonPrimitive.content)
    assertEquals("bundled", payload.getValue("layoutSource").jsonPrimitive.content)
    assertEquals(false, payload.getValue("workspaceStageInstalled").jsonPrimitive.boolean)
    assertEquals(6, payload.getValue("stageCount").jsonPrimitive.int)
    assertEquals(26, payload.getValue("fileCount").jsonPrimitive.int)
  }

  @Test
  fun handlePodRuntimeDescribe_reportsDesktopRuntimeMainlineGapMap() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)
    val handler = PodHandler(context)

    val result = handler.handlePodRuntimeDescribe(null)

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals("pod.runtime.describe", payload.getValue("command").jsonPrimitive.content)
    assertEquals(
      "android-desktop-runtime-mainline-20260403",
      payload.getValue("mainlineBranch").jsonPrimitive.content,
    )
    assertEquals(false, payload.getValue("fullDesktopRuntimeBundled").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopEnvironmentBundled").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopBundleReady").jsonPrimitive.boolean)
    assertEquals(false, payload.getValue("desktopHomeReady").jsonPrimitive.boolean)
    assertEquals(1, payload.getValue("desktopProfileCount").jsonPrimitive.int)
    assertEquals(1, payload.getValue("runtimeExecutionCommandCount").jsonPrimitive.int)
    assertEquals(1, payload.getValue("desktopMaterializeCommandCount").jsonPrimitive.int)
    assertEquals(3, payload.getValue("runtimeTaskCount").jsonPrimitive.int)
    assertEquals(1, payload.getValue("toolDescriptorCount").jsonPrimitive.int)
    assertEquals(1, payload.getValue("runtimeToolTaskCount").jsonPrimitive.int)
    assertEquals(1, payload.getValue("runtimePluginTaskCount").jsonPrimitive.int)
    assertEquals(false, payload.getValue("runtimeHomeReady").jsonPrimitive.boolean)
    val domainIds =
      payload.getValue("domains").jsonArray.map { item ->
        item.jsonObject.getValue("id").jsonPrimitive.content
      }
    assertTrue("engine" in domainIds)
    assertTrue("environment" in domainIds)
    assertTrue("browser" in domainIds)
    assertTrue("tools" in domainIds)
    assertTrue("plugins" in domainIds)
    val missingDomains = payload.getValue("missingDomains").jsonArray.map { it.jsonPrimitive.content }
    assertFalse("engine" in missingDomains)
    assertFalse("environment" in missingDomains)
    assertFalse("tools" in missingDomains)
    assertFalse("browser" in missingDomains)
    assertEquals("desktop_home_materialize", payload.getValue("recommendedNextSlice").jsonPrimitive.content)
  }

  @Test
  fun handlePodBrowserDescribe_reportsBoundedBrowserLane() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)
    val handler = PodHandler(context)

    val result = handler.handlePodBrowserDescribe(null)

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals("pod.browser.describe", payload.getValue("command").jsonPrimitive.content)
    assertEquals(true, payload.getValue("browserStageInstalled").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("browserStageManifestPresent").jsonPrimitive.boolean)
    assertEquals(1, payload.getValue("browserAuthFlowCount").jsonPrimitive.int)
    assertEquals(false, payload.getValue("browserReplayReady").jsonPrimitive.boolean)
    assertEquals("openai-codex-oauth", payload.getValue("recommendedFlowId").jsonPrimitive.content)
    assertEquals(
      "pod.browser.auth.start",
      payload.getValue("launchCommands").jsonArray.single().jsonPrimitive.content,
    )
    assertTrue(payload.getValue("stateFilePath").jsonPrimitive.content.endsWith("/state/browser-openai-codex-auth.json"))
    assertTrue(payload.getValue("logFilePath").jsonPrimitive.content.endsWith("/logs/browser-lane.log"))
  }

  @Test
  fun handlePodBrowserDescribe_reportsReplayStateAfterPersistedLaunch() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)
    val runtimeHome = context.filesDir.resolve("openclaw/embedded-runtime-home/0.10.0")
    runtimeHome.resolve("state").mkdirs()
    runtimeHome.resolve("logs").mkdirs()
    runtimeHome
      .resolve("state/browser-openai-codex-auth.json")
      .writeText(
        """{"flowId":"openai-codex-oauth","status":"launch_requested","launchRequested":true,"executedAt":"2026-04-03T12:00:00Z","statusText":"Browser opened"}""",
      )
    runtimeHome.resolve("logs/browser-lane.log").writeText("2026-04-03T12:00:00Z launch_requested\n")
    val handler = PodHandler(context)

    val result = handler.handlePodBrowserDescribe(null)

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals("replayed", payload.getValue("browserStatus").jsonPrimitive.content)
    assertEquals(true, payload.getValue("browserReplayReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("browserStateFilePresent").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("browserLogFilePresent").jsonPrimitive.boolean)
    assertEquals("openai-codex-oauth", payload.getValue("lastLaunchFlowId").jsonPrimitive.content)
    assertEquals("launch_requested", payload.getValue("lastLaunchStatus").jsonPrimitive.content)
    assertEquals(true, payload.getValue("lastLaunchRequested").jsonPrimitive.boolean)
    assertEquals("2026-04-03T12:00:00Z", payload.getValue("lastLaunchExecutedAt").jsonPrimitive.content)
    assertEquals("Browser opened", payload.getValue("lastLaunchStatusText").jsonPrimitive.content)
  }

  @Test
  fun handlePodBrowserAuthStart_returnsStructuredLaunchPayload() {
    val context = RuntimeEnvironment.getApplication()
    val handler =
      PodHandler(
        appContext = context,
        browserAuthStart = { _, flowId ->
          ai.openclaw.app.EmbeddedRuntimePodBrowserStartResult(
            ok = true,
            payload =
              buildJsonObject {
                put("flowId", JsonPrimitive(flowId ?: "openai-codex-oauth"))
                put("launchStatus", JsonPrimitive("launch_requested"))
                put("authInProgress", JsonPrimitive(true))
              },
          )
        },
      )

    val result = handler.handlePodBrowserAuthStart("""{"flowId":"openai-codex-oauth"}""")

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals("pod.browser.auth.start", payload.getValue("command").jsonPrimitive.content)
    assertEquals("openai-codex-oauth", payload.getValue("flowId").jsonPrimitive.content)
    assertEquals("launch_requested", payload.getValue("launchStatus").jsonPrimitive.content)
    assertEquals(true, payload.getValue("authInProgress").jsonPrimitive.boolean)
  }

  @Test
  fun handlePodDesktopMaterialize_materializesDesktopHome() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-desktop-home").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)
    val handler = PodHandler(context)

    val result = handler.handlePodDesktopMaterialize("""{"profileId":"openclaw-desktop-host"}""")

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals("pod.desktop.materialize", payload.getValue("command").jsonPrimitive.content)
    assertEquals("openclaw-desktop-host", payload.getValue("profileId").jsonPrimitive.content)
    assertEquals(true, payload.getValue("desktopHomeReady").jsonPrimitive.boolean)
    assertTrue(payload.getValue("activeProfilePath").jsonPrimitive.content.endsWith("/profiles/active-profile.json"))
    assertTrue(context.filesDir.resolve("openclaw/embedded-desktop-home/0.10.0/profiles/active-profile.json").isFile)
  }

  @Test
  fun handlePodRuntimeExecute_materializesRuntimeHomeAndPersistsState() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)
    val handler = PodHandler(context)

    val result = handler.handlePodRuntimeExecute("""{"taskId":"runtime-smoke"}""")

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals("pod.runtime.execute", payload.getValue("command").jsonPrimitive.content)
    assertEquals("runtime-smoke", payload.getValue("taskId").jsonPrimitive.content)
    assertEquals(true, payload.getValue("runtimeHomeReady").jsonPrimitive.boolean)
    assertEquals(false, payload.getValue("desktopProfileReplayReady").jsonPrimitive.boolean)
    assertEquals(false, payload.getValue("desktopEnvironmentSupervisionReady").jsonPrimitive.boolean)
    assertEquals(1, payload.getValue("executionCount").jsonPrimitive.int)
    assertEquals("embedded-runtime-task-engine-v1", payload.getValue("engineId").jsonPrimitive.content)
    assertTrue(payload.getValue("stateFilePath").jsonPrimitive.content.endsWith("/state/runtime-smoke.json"))
    val state = payload.getValue("state").jsonObject
    assertEquals("ok", state.getValue("status").jsonPrimitive.content)
    assertEquals(1, state.getValue("executionCount").jsonPrimitive.int)
    assertTrue(context.filesDir.resolve("openclaw/embedded-runtime-home/0.10.0/config/runtime-env.json").isFile)
    assertTrue(context.filesDir.resolve("openclaw/embedded-runtime-home/0.10.0/state/runtime-smoke.json").isFile)
    assertTrue(context.filesDir.resolve("openclaw/embedded-runtime-home/0.10.0/logs/runtime-engine.log").isFile)
  }

  @Test
  fun handlePodRuntimeExecute_replaysDesktopProfileAfterMaterialize() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-desktop-home").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)
    materializeEmbeddedRuntimeDesktopEnvironment(context, "openclaw-desktop-host")
    val handler = PodHandler(context)

    val result = handler.handlePodRuntimeExecute("""{"taskId":"runtime-smoke"}""")

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals(true, payload.getValue("desktopProfileReplayReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopEnvironmentSupervisionReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessModelReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessActivationReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessSupervisionReady").jsonPrimitive.boolean)
    assertEquals(false, payload.getValue("desktopLongLivedProcessReady").jsonPrimitive.boolean)
    assertEquals("blocked", payload.getValue("desktopProcessStatus").jsonPrimitive.content)
    assertEquals("blocked", payload.getValue("desktopProcessActivationStatus").jsonPrimitive.content)
    assertEquals("blocked", payload.getValue("desktopProcessActivationState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessActivationGeneration").jsonPrimitive.int)
    assertEquals("blocked", payload.getValue("desktopProcessSupervisionStatus").jsonPrimitive.content)
    assertEquals("blocked", payload.getValue("desktopProcessSupervisionState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessSupervisionGeneration").jsonPrimitive.int)
    assertEquals("openclaw-desktop-host-runtime-smoke-1", payload.getValue("desktopProcessSessionId").jsonPrimitive.content)
    val desktopReplay = payload.getValue("desktopProfileReplay").jsonObject
    assertEquals("openclaw-desktop-host", desktopReplay.getValue("profileId").jsonPrimitive.content)
    assertEquals("degraded", desktopReplay.getValue("healthStatus").jsonPrimitive.content)
    assertEquals(1, desktopReplay.getValue("restartGeneration").jsonPrimitive.int)
    assertEquals(true, desktopReplay.getValue("processModelReady").jsonPrimitive.boolean)
    assertEquals("blocked", desktopReplay.getValue("processStatus").jsonPrimitive.content)
    assertEquals(true, desktopReplay.getValue("processActivationReady").jsonPrimitive.boolean)
    assertEquals("blocked", desktopReplay.getValue("processActivationStatus").jsonPrimitive.content)
    assertEquals("blocked", desktopReplay.getValue("processActivationState").jsonPrimitive.content)
    assertEquals(true, desktopReplay.getValue("processSupervisionReady").jsonPrimitive.boolean)
    assertEquals("blocked", desktopReplay.getValue("processSupervisionStatus").jsonPrimitive.content)
    assertEquals("blocked", desktopReplay.getValue("processSupervisionState").jsonPrimitive.content)
    assertTrue(
      payload.getValue("desktopProfileReplayStatePath").jsonPrimitive.content.endsWith("/state/runtime-smoke-desktop-profile.json"),
    )
    assertTrue(
      payload.getValue("desktopProfileReplayResultFilePath").jsonPrimitive.content.endsWith("/work/runtime-smoke-desktop-profile.json"),
    )
    assertTrue(
      payload.getValue("desktopHealthReportPath").jsonPrimitive.content.endsWith("/state/runtime-smoke-health-report.json"),
    )
    assertTrue(
      payload.getValue("desktopRestartContractPath").jsonPrimitive.content.endsWith("/state/runtime-smoke-restart-contract.json"),
    )
    assertTrue(
      payload.getValue("desktopProcessStatePath").jsonPrimitive.content.endsWith("/state/runtime-smoke-process-model.json"),
    )
    assertTrue(
      payload.getValue("desktopProcessActivationStatePath").jsonPrimitive.content.endsWith("/state/runtime-smoke-activation-contract.json"),
    )
    assertTrue(
      payload.getValue("desktopProcessSupervisionStatePath").jsonPrimitive.content.endsWith("/state/runtime-smoke-supervision-contract.json"),
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.10.0/state/runtime-smoke-desktop-profile.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.10.0/state/runtime-smoke-health-report.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.10.0/state/runtime-smoke-restart-contract.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.10.0/state/runtime-smoke-process-model.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.10.0/state/runtime-smoke-activation-contract.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.10.0/state/runtime-smoke-supervision-contract.json").isFile,
    )
  }

  @Test
  fun handlePodRuntimeExecute_runsPackagedDesktopToolLane() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)
    val handler = PodHandler(context)

    val result = handler.handlePodRuntimeExecute("""{"taskId":"tool-brief-inspect"}""")

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals("pod.runtime.execute", payload.getValue("command").jsonPrimitive.content)
    assertEquals("tool-brief-inspect", payload.getValue("taskId").jsonPrimitive.content)
    assertEquals("packaged-brief-inspector-v1", payload.getValue("toolId").jsonPrimitive.content)
    assertEquals(true, payload.getValue("toolkitCommandPolicyPresent").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("packagedToolDescriptorPresent").jsonPrimitive.boolean)
    assertTrue(payload.getValue("toolResultFilePath").jsonPrimitive.content.endsWith("/work/tool-brief-inspect-result.json"))
    assertEquals(1, payload.getValue("toolResult").jsonObject.getValue("headingCount").jsonPrimitive.int)
    assertTrue(context.filesDir.resolve("openclaw/embedded-runtime-home/0.10.0/work/tool-brief-inspect-result.json").isFile)
  }

  @Test
  fun handlePodRuntimeExecute_runsAllowlistedPluginLane() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-desktop-home").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)
    val handler = PodHandler(context)

    val result = handler.handlePodRuntimeExecute("""{"taskId":"plugin-allowlist-inspect"}""")

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals("pod.runtime.execute", payload.getValue("command").jsonPrimitive.content)
    assertEquals("plugin-allowlist-inspect", payload.getValue("taskId").jsonPrimitive.content)
    assertEquals("openclaw-plugin-host-placeholder", payload.getValue("pluginId").jsonPrimitive.content)
    assertEquals(true, payload.getValue("desktopPluginsManifestPresent").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("packagedPluginDescriptorPresent").jsonPrimitive.boolean)
    assertEquals(
      "packaged_profile_descriptor",
      payload.getValue("pluginResult").jsonObject.getValue("profileSource").jsonPrimitive.content,
    )
    assertTrue(
      payload.getValue("pluginResultFilePath").jsonPrimitive.content.endsWith("/work/plugin-allowlist-inspect-result.json"),
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-runtime-home/0.10.0/work/plugin-allowlist-inspect-result.json").isFile,
    )
  }

  @Test
  fun handlePodRuntimeDescribe_requiresReplayEvidenceBeforeConfiguredBrowserLane() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)
    executeEmbeddedRuntimePodTask(context, "runtime-smoke")
    executeEmbeddedRuntimePodTask(context, "tool-brief-inspect")
    val runtimeHome = context.filesDir.resolve("openclaw/embedded-runtime-home/0.10.0")
    runtimeHome.resolve("state").mkdirs()
    runtimeHome.resolve("logs").mkdirs()
    runtimeHome
      .resolve("state/browser-openai-codex-auth.json")
      .writeText(
        """{"flowId":"openai-codex-oauth","status":"launch_requested","launchRequested":true,"executedAt":"2026-04-03T12:00:00Z"}""",
      )
    runtimeHome.resolve("logs/browser-lane.log").writeText("2026-04-03T12:00:00Z launch_requested\n")
    val handler = PodHandler(context)

    val result = handler.handlePodRuntimeDescribe(null)

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals("desktop_bundle_ready", payload.getValue("mainlineStatus").jsonPrimitive.content)
    assertEquals(true, payload.getValue("browserReplayReady").jsonPrimitive.boolean)
    assertEquals("desktop_home_materialize", payload.getValue("recommendedNextSlice").jsonPrimitive.content)
    val browserDomain =
      payload.getValue("domains").jsonArray.first { item ->
        item.jsonObject.getValue("id").jsonPrimitive.content == "browser"
      }.jsonObject
    assertEquals("landed_bootstrap", browserDomain.getValue("status").jsonPrimitive.content)
  }

  @Test
  fun handlePodRuntimeDescribe_recommendsDesktopHomeReplayAfterMaterialize() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-desktop-home").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)
    executeEmbeddedRuntimePodTask(context, "runtime-smoke")
    executeEmbeddedRuntimePodTask(context, "tool-brief-inspect")
    materializeEmbeddedRuntimeDesktopEnvironment(context, "openclaw-desktop-host")
    val runtimeHome = context.filesDir.resolve("openclaw/embedded-runtime-home/0.10.0")
    runtimeHome.resolve("state").mkdirs()
    runtimeHome.resolve("logs").mkdirs()
    runtimeHome
      .resolve("state/browser-openai-codex-auth.json")
      .writeText(
        """{"flowId":"openai-codex-oauth","status":"launch_requested","launchRequested":true,"executedAt":"2026-04-03T12:00:00Z"}""",
      )
    runtimeHome.resolve("logs/browser-lane.log").writeText("2026-04-03T12:00:00Z launch_requested\n")
    val handler = PodHandler(context)

    val result = handler.handlePodRuntimeDescribe(null)

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals("desktop_home_ready", payload.getValue("mainlineStatus").jsonPrimitive.content)
    assertEquals(false, payload.getValue("desktopProfileReplayReady").jsonPrimitive.boolean)
    assertEquals("desktop_home_replay", payload.getValue("recommendedNextSlice").jsonPrimitive.content)
  }

  @Test
  fun handlePodRuntimeDescribe_reportsProcessModelStateAfterRuntimeSmoke() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-desktop-home").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)
    materializeEmbeddedRuntimeDesktopEnvironment(context, "openclaw-desktop-host")
    executeEmbeddedRuntimePodTask(context, "runtime-smoke")
    val handler = PodHandler(context)

    val result = handler.handlePodRuntimeDescribe(null)

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals(true, payload.getValue("desktopProfileReplayReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopEnvironmentSupervisionReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessModelReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessActivationReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessSupervisionReady").jsonPrimitive.boolean)
    assertEquals(false, payload.getValue("desktopLongLivedProcessReady").jsonPrimitive.boolean)
    assertEquals("blocked", payload.getValue("desktopProcessStatus").jsonPrimitive.content)
    assertEquals("blocked", payload.getValue("desktopProcessActivationStatus").jsonPrimitive.content)
    assertEquals("blocked", payload.getValue("desktopProcessActivationState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessActivationGeneration").jsonPrimitive.int)
    assertEquals("blocked", payload.getValue("desktopProcessSupervisionStatus").jsonPrimitive.content)
    assertEquals("blocked", payload.getValue("desktopProcessSupervisionState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessSupervisionGeneration").jsonPrimitive.int)
    assertEquals(
      "openclaw-desktop-host-runtime-smoke-1",
      payload.getValue("desktopProcessSessionId").jsonPrimitive.content,
    )
    assertEquals(
      "tool_lane_replay",
      payload.getValue("recommendedNextSlice").jsonPrimitive.content,
    )
  }

  @Test
  fun handlePodRuntimeDescribe_reportsProcessRuntimeSupervisionStateAfterFullReplay() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-desktop-home").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)
    materializeEmbeddedRuntimeDesktopEnvironment(context, "openclaw-desktop-host")
    seedBrowserReplayAndCredential(context, "0.10.0")
    executeEmbeddedRuntimePodTask(context, "runtime-smoke")
    executeEmbeddedRuntimePodTask(context, "tool-brief-inspect")
    executeEmbeddedRuntimePodTask(context, "plugin-allowlist-inspect")
    val handler = PodHandler(context)

    val result = handler.handlePodRuntimeDescribe(null)

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals("process_runtime_supervision_bootstrapped", payload.getValue("mainlineStatus").jsonPrimitive.content)
    assertEquals(true, payload.getValue("desktopProcessModelReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessActivationReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessSupervisionReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopLongLivedProcessReady").jsonPrimitive.boolean)
    assertEquals("standby", payload.getValue("desktopProcessStatus").jsonPrimitive.content)
    assertEquals("ready", payload.getValue("desktopProcessActivationStatus").jsonPrimitive.content)
    assertEquals("pending_supervisor", payload.getValue("desktopProcessActivationState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessActivationGeneration").jsonPrimitive.int)
    assertEquals("active", payload.getValue("desktopProcessSupervisionStatus").jsonPrimitive.content)
    assertEquals("lease_active", payload.getValue("desktopProcessSupervisionState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessSupervisionGeneration").jsonPrimitive.int)
    assertEquals("process_runtime_observation", payload.getValue("recommendedNextSlice").jsonPrimitive.content)
  }

  @Test
  fun handlePodWorkspaceScan_reportsWorkspaceAssetsAfterInstall() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)
    val handler = PodHandler(context)

    val result = handler.handlePodWorkspaceScan("""{"limit":2,"query":"handoff"}""")

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals("pod.workspace.scan", payload.getValue("command").jsonPrimitive.content)
    assertEquals(true, payload.getValue("localExecutionAvailable").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("workspaceStagePresent").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("stageManifestPresent").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("contentIndexPresent").jsonPrimitive.boolean)
    assertEquals(1, payload.getValue("matchedFileCount").jsonPrimitive.int)
    assertEquals(1, payload.getValue("returnedFileCount").jsonPrimitive.int)
    assertEquals(false, payload.getValue("truncated").jsonPrimitive.boolean)
    val files = payload.getValue("files").jsonArray
    assertEquals(1, files.size)
    assertEquals(
      "templates/handoff-template.md",
      files.single().jsonObject.getValue("relativePath").jsonPrimitive.content,
    )
    assertTrue(files.single().jsonObject.getValue("textPreview").jsonPrimitive.content.contains("pod.workspace.scan"))
    val stageManifest = payload.getValue("stageManifest").jsonObject
    assertEquals("workspace", stageManifest.getValue("stage").jsonPrimitive.content)
    val contentIndex = payload.getValue("contentIndex").jsonObject
    assertEquals(2, contentIndex.getValue("documents").jsonArray.size)
  }

  @Test
  fun handlePodWorkspaceScan_returnsEmptyInventoryBeforeInstall() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    val handler = PodHandler(context)

    val result = handler.handlePodWorkspaceScan("""{"limit":1}""")

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals(false, payload.getValue("localExecutionAvailable").jsonPrimitive.boolean)
    assertEquals(false, payload.getValue("workspaceStagePresent").jsonPrimitive.boolean)
    assertEquals(0, payload.getValue("matchedFileCount").jsonPrimitive.int)
    assertEquals(0, payload.getValue("returnedFileCount").jsonPrimitive.int)
    assertFalse(payload.containsKey("stageManifest"))
    assertFalse(payload.containsKey("contentIndex"))
  }

  @Test
  fun handlePodWorkspaceRead_returnsPackagedDocumentAfterInstall() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)
    val handler = PodHandler(context)

    val result = handler.handlePodWorkspaceRead("""{"path":"templates/handoff-template.md","maxChars":4096}""")

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals("pod.workspace.read", payload.getValue("command").jsonPrimitive.content)
    assertEquals(true, payload.getValue("localExecutionAvailable").jsonPrimitive.boolean)
    assertEquals("templates/handoff-template.md", payload.getValue("relativePath").jsonPrimitive.content)
    assertEquals(false, payload.getValue("textTruncated").jsonPrimitive.boolean)
    assertTrue(payload.getValue("text").jsonPrimitive.content.contains("# Embedded Runtime Handoff"))
    val document = payload.getValue("document").jsonObject
    assertEquals("template", document.getValue("kind").jsonPrimitive.content)
  }

  @Test
  fun handlePodWorkspaceRead_requiresPath() {
    val context = RuntimeEnvironment.getApplication()
    val handler = PodHandler(context)

    val result = handler.handlePodWorkspaceRead("""{"maxChars":256}""")

    assertFalse(result.ok)
    assertEquals("INVALID_REQUEST", result.error?.code)
  }

  private fun seedBrowserReplayAndCredential(
    context: android.content.Context,
    manifestVersion: String,
  ) {
    val runtimeHome = context.filesDir.resolve("openclaw/embedded-runtime-home/$manifestVersion")
    runtimeHome.resolve("state").mkdirs()
    runtimeHome.resolve("logs").mkdirs()
    runtimeHome
      .resolve("state/browser-openai-codex-auth.json")
      .writeText(
        """{"flowId":"openai-codex-oauth","status":"launch_requested","launchRequested":true,"executedAt":"2026-04-05T12:00:00Z","statusText":"Browser opened","credentialPresentAfterLaunch":true,"signedInEmail":"desktop-runtime@example.com"}""",
      )
    runtimeHome.resolve("logs/browser-lane.log").writeText("2026-04-05T12:00:00Z launch_requested\n")
  }

  private fun parsePayload(payloadJson: String?) =
    json.parseToJsonElement(payloadJson ?: error("expected payload")).jsonObject
}
