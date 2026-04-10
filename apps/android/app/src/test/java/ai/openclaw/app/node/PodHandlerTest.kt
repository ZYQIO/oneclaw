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
    assertEquals("0.17.0", payload.getValue("installedVersions").jsonArray.single().jsonPrimitive.content)
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
    assertEquals("0.17.0", podManifest.getValue("version").jsonPrimitive.content)
    val podLayout = payload.getValue("podLayout").jsonObject
    assertEquals("0.17.0", podLayout.getValue("version").jsonPrimitive.content)
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
    val runtimeHome = context.filesDir.resolve("openclaw/embedded-runtime-home/0.17.0")
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
    assertTrue(context.filesDir.resolve("openclaw/embedded-desktop-home/0.17.0/profiles/active-profile.json").isFile)
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
    assertTrue(context.filesDir.resolve("openclaw/embedded-runtime-home/0.17.0/config/runtime-env.json").isFile)
    assertTrue(context.filesDir.resolve("openclaw/embedded-runtime-home/0.17.0/state/runtime-smoke.json").isFile)
    assertTrue(context.filesDir.resolve("openclaw/embedded-runtime-home/0.17.0/logs/runtime-engine.log").isFile)
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
    assertEquals(true, payload.getValue("desktopProcessObservationReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessRecoveryReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessDetachedLaunchReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessSupervisorLoopReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessActiveSessionReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessActiveSessionBootstrapOnly").jsonPrimitive.boolean)
    assertEquals(false, payload.getValue("desktopProcessActiveSessionObserved").jsonPrimitive.boolean)
    assertEquals(false, payload.getValue("desktopProcessActiveSessionRecoveryReentryReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessActiveSessionRestartContinuityReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessActiveSessionValidationReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessActiveSessionValidationBootstrapOnly").jsonPrimitive.boolean)
    assertEquals(false, payload.getValue("desktopProcessActiveSessionValidationLeaseRenewalObserved").jsonPrimitive.boolean)
    assertEquals(false, payload.getValue("desktopProcessActiveSessionValidationRecoveryReentryObserved").jsonPrimitive.boolean)
    assertEquals(false, payload.getValue("desktopProcessActiveSessionValidationRestartContinuityObserved").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessActiveSessionValidationDeviceProofRequired").jsonPrimitive.boolean)
    assertEquals(false, payload.getValue("desktopLongLivedProcessReady").jsonPrimitive.boolean)
    assertEquals("blocked", payload.getValue("desktopProcessStatus").jsonPrimitive.content)
    assertEquals("blocked", payload.getValue("desktopProcessActivationStatus").jsonPrimitive.content)
    assertEquals("blocked", payload.getValue("desktopProcessActivationState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessActivationGeneration").jsonPrimitive.int)
    assertEquals("blocked", payload.getValue("desktopProcessSupervisionStatus").jsonPrimitive.content)
    assertEquals("blocked", payload.getValue("desktopProcessSupervisionState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessSupervisionGeneration").jsonPrimitive.int)
    assertEquals("blocked", payload.getValue("desktopProcessObservationStatus").jsonPrimitive.content)
    assertEquals("awaiting_recovery", payload.getValue("desktopProcessObservationState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessObservationGeneration").jsonPrimitive.int)
    assertEquals("restore_dependencies:browser", payload.getValue("desktopProcessObservationRecoveryHint").jsonPrimitive.content)
    assertEquals("planned", payload.getValue("desktopProcessRecoveryStatus").jsonPrimitive.content)
    assertEquals("restore_dependencies", payload.getValue("desktopProcessRecoveryState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessRecoveryGeneration").jsonPrimitive.int)
    assertEquals(2, payload.getValue("desktopProcessRecoveryActionCount").jsonPrimitive.int)
    assertEquals("restore_dependency:browser", payload.getValue("desktopProcessRecoveryPrimaryAction").jsonPrimitive.content)
    assertEquals("missing_dependencies:browser", payload.getValue("desktopProcessRecoveryReason").jsonPrimitive.content)
    assertEquals("blocked", payload.getValue("desktopProcessDetachedLaunchStatus").jsonPrimitive.content)
    assertEquals("awaiting_recovery", payload.getValue("desktopProcessDetachedLaunchState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessDetachedLaunchGeneration").jsonPrimitive.int)
    assertEquals(
      "openclaw-desktop-host-runtime-smoke-1-detached-launch-1",
      payload.getValue("desktopProcessDetachedLaunchSessionId").jsonPrimitive.content,
    )
    assertTrue(
      payload.getValue("desktopProcessDetachedLaunchCommand").jsonPrimitive.content.contains("--launch-detached"),
    )
    assertEquals(
      "recovery_not_ready:planned",
      payload.getValue("desktopProcessDetachedLaunchBlockedReason").jsonPrimitive.content,
    )
    assertEquals("blocked", payload.getValue("desktopProcessSupervisorLoopStatus").jsonPrimitive.content)
    assertEquals("awaiting_detached_launch", payload.getValue("desktopProcessSupervisorLoopState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessSupervisorLoopGeneration").jsonPrimitive.int)
    assertEquals(
      "openclaw-desktop-host-runtime-smoke-1-supervisor-loop-1",
      payload.getValue("desktopProcessSupervisorLoopSessionId").jsonPrimitive.content,
    )
    assertEquals(
      "recovery_not_ready:planned",
      payload.getValue("desktopProcessSupervisorLoopBlockedReason").jsonPrimitive.content,
    )
    assertEquals("blocked", payload.getValue("desktopProcessActiveSessionStatus").jsonPrimitive.content)
    assertEquals("awaiting_supervisor_loop", payload.getValue("desktopProcessActiveSessionState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessActiveSessionGeneration").jsonPrimitive.int)
    assertEquals(
      "openclaw-desktop-host-runtime-smoke-1-active-session-1",
      payload.getValue("desktopProcessActiveSessionSessionId").jsonPrimitive.content,
    )
    assertEquals(
      "openclaw-desktop-host-runtime-smoke-1-supervisor-loop-1",
      payload.getValue("desktopProcessActiveSessionLeaseOwnerSessionId").jsonPrimitive.content,
    )
    assertEquals(
      "recovery_not_ready:planned",
      payload.getValue("desktopProcessActiveSessionBlockedReason").jsonPrimitive.content,
    )
    assertEquals(
      "pending_device_proof",
      payload.getValue("desktopProcessActiveSessionValidationStatus").jsonPrimitive.content,
    )
    assertEquals(
      "awaiting_device_proof",
      payload.getValue("desktopProcessActiveSessionValidationState").jsonPrimitive.content,
    )
    assertEquals(1, payload.getValue("desktopProcessActiveSessionValidationGeneration").jsonPrimitive.int)
    assertEquals(
      "openclaw-desktop-host-runtime-smoke-1-active-session-1",
      payload.getValue("desktopProcessActiveSessionValidationSessionId").jsonPrimitive.content,
    )
    assertEquals(
      "openclaw-desktop-host-runtime-smoke-1-supervisor-loop-1",
      payload.getValue("desktopProcessActiveSessionValidationLeaseOwnerSessionId").jsonPrimitive.content,
    )
    assertEquals(
      "device_active_session_proof_missing",
      payload.getValue("desktopProcessActiveSessionValidationBlockedReason").jsonPrimitive.content,
    )
    assertEquals(
      "pending_live_proof",
      payload.getValue("desktopProcessActiveSessionDeviceProofStatus").jsonPrimitive.content,
    )
    assertEquals(
      "awaiting_live_proof",
      payload.getValue("desktopProcessActiveSessionDeviceProofState").jsonPrimitive.content,
    )
    assertEquals(1, payload.getValue("desktopProcessActiveSessionDeviceProofGeneration").jsonPrimitive.int)
    assertEquals(
      "openclaw-desktop-host-runtime-smoke-1-active-session-1",
      payload.getValue("desktopProcessActiveSessionDeviceProofSessionId").jsonPrimitive.content,
    )
    assertEquals(
      "openclaw-desktop-host-runtime-smoke-1-supervisor-loop-1",
      payload.getValue("desktopProcessActiveSessionDeviceProofLeaseOwnerSessionId").jsonPrimitive.content,
    )
    assertEquals(
      "live_active_session_proof_missing",
      payload.getValue("desktopProcessActiveSessionDeviceProofBlockedReason").jsonPrimitive.content,
    )
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
    assertEquals(true, desktopReplay.getValue("processObservationReady").jsonPrimitive.boolean)
    assertEquals("blocked", desktopReplay.getValue("processObservationStatus").jsonPrimitive.content)
    assertEquals("awaiting_recovery", desktopReplay.getValue("processObservationState").jsonPrimitive.content)
    assertEquals(true, desktopReplay.getValue("processRecoveryReady").jsonPrimitive.boolean)
    assertEquals("planned", desktopReplay.getValue("processRecoveryStatus").jsonPrimitive.content)
    assertEquals("restore_dependencies", desktopReplay.getValue("processRecoveryState").jsonPrimitive.content)
    assertEquals(true, desktopReplay.getValue("processDetachedLaunchReady").jsonPrimitive.boolean)
    assertEquals("blocked", desktopReplay.getValue("processDetachedLaunchStatus").jsonPrimitive.content)
    assertEquals("awaiting_recovery", desktopReplay.getValue("processDetachedLaunchState").jsonPrimitive.content)
    assertEquals(true, desktopReplay.getValue("processSupervisorLoopReady").jsonPrimitive.boolean)
    assertEquals("blocked", desktopReplay.getValue("processSupervisorLoopStatus").jsonPrimitive.content)
    assertEquals("awaiting_detached_launch", desktopReplay.getValue("processSupervisorLoopState").jsonPrimitive.content)
    assertEquals(true, desktopReplay.getValue("processActiveSessionReady").jsonPrimitive.boolean)
    assertEquals("blocked", desktopReplay.getValue("processActiveSessionStatus").jsonPrimitive.content)
    assertEquals("awaiting_supervisor_loop", desktopReplay.getValue("processActiveSessionState").jsonPrimitive.content)
    assertEquals(true, desktopReplay.getValue("processActiveSessionValidationReady").jsonPrimitive.boolean)
    assertEquals(
      "pending_device_proof",
      desktopReplay.getValue("processActiveSessionValidationStatus").jsonPrimitive.content,
    )
    assertEquals(
      "awaiting_device_proof",
      desktopReplay.getValue("processActiveSessionValidationState").jsonPrimitive.content,
    )
    assertEquals(true, desktopReplay.getValue("processActiveSessionDeviceProofReady").jsonPrimitive.boolean)
    assertEquals(
      "pending_live_proof",
      desktopReplay.getValue("processActiveSessionDeviceProofStatus").jsonPrimitive.content,
    )
    assertEquals(
      "awaiting_live_proof",
      desktopReplay.getValue("processActiveSessionDeviceProofState").jsonPrimitive.content,
    )
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
      payload.getValue("desktopProcessObservationStatePath").jsonPrimitive.content.endsWith("/state/runtime-smoke-observation-contract.json"),
    )
    assertTrue(
      payload.getValue("desktopProcessRecoveryStatePath").jsonPrimitive.content.endsWith("/state/runtime-smoke-recovery-contract.json"),
    )
    assertTrue(
      payload.getValue("desktopProcessDetachedLaunchStatePath").jsonPrimitive.content.endsWith("/state/runtime-smoke-detached-launch-contract.json"),
    )
    assertTrue(
      payload.getValue("desktopProcessSupervisorLoopStatePath").jsonPrimitive.content.endsWith("/state/runtime-smoke-supervisor-loop-contract.json"),
    )
    assertTrue(
      payload.getValue("desktopProcessActiveSessionStatePath").jsonPrimitive.content.endsWith("/state/runtime-smoke-active-session-contract.json"),
    )
    assertTrue(
      payload
        .getValue("desktopProcessActiveSessionValidationStatePath")
        .jsonPrimitive
        .content
        .endsWith("/state/runtime-smoke-active-session-validation.json"),
    )
    assertTrue(
      payload
        .getValue("desktopProcessActiveSessionDeviceProofStatePath")
        .jsonPrimitive
        .content
        .endsWith("/state/runtime-smoke-active-session-device-proof.json"),
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.17.0/state/runtime-smoke-desktop-profile.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.17.0/state/runtime-smoke-health-report.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.17.0/state/runtime-smoke-restart-contract.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.17.0/state/runtime-smoke-process-model.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.17.0/state/runtime-smoke-activation-contract.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.17.0/state/runtime-smoke-supervision-contract.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.17.0/state/runtime-smoke-observation-contract.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.17.0/state/runtime-smoke-recovery-contract.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.17.0/state/runtime-smoke-detached-launch-contract.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.17.0/state/runtime-smoke-supervisor-loop-contract.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.17.0/state/runtime-smoke-active-session-contract.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.17.0/state/runtime-smoke-active-session-validation.json").isFile,
    )
    assertTrue(
      context.filesDir.resolve("openclaw/embedded-desktop-home/0.17.0/state/runtime-smoke-active-session-device-proof.json").isFile,
    )
    val pendingLiveProofPayload =
      json
        .parseToJsonElement(
          context.filesDir
            .resolve("openclaw/embedded-desktop-home/0.17.0/state/runtime-smoke-active-session-device-proof.json")
            .readText(),
        ).jsonObject
    assertEquals(
      "pnpm android:local-host:embedded-runtime-pod:doctor -- --json",
      pendingLiveProofPayload.getValue("proofCommand").jsonPrimitive.content,
    )
    assertEquals(
      "doctor-summary",
      pendingLiveProofPayload.getValue("requiredArtifacts").jsonArray.first().jsonPrimitive.content,
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
    assertTrue(context.filesDir.resolve("openclaw/embedded-runtime-home/0.17.0/work/tool-brief-inspect-result.json").isFile)
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
      context.filesDir.resolve("openclaw/embedded-runtime-home/0.17.0/work/plugin-allowlist-inspect-result.json").isFile,
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
    val runtimeHome = context.filesDir.resolve("openclaw/embedded-runtime-home/0.17.0")
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
    val runtimeHome = context.filesDir.resolve("openclaw/embedded-runtime-home/0.17.0")
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
    assertEquals(true, payload.getValue("desktopProcessObservationReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessRecoveryReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessDetachedLaunchReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessSupervisorLoopReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessActiveSessionReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessActiveSessionBootstrapOnly").jsonPrimitive.boolean)
    assertEquals(false, payload.getValue("desktopProcessActiveSessionObserved").jsonPrimitive.boolean)
    assertEquals(false, payload.getValue("desktopProcessActiveSessionRecoveryReentryReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessActiveSessionRestartContinuityReady").jsonPrimitive.boolean)
    assertEquals(false, payload.getValue("desktopLongLivedProcessReady").jsonPrimitive.boolean)
    assertEquals("blocked", payload.getValue("desktopProcessStatus").jsonPrimitive.content)
    assertEquals("blocked", payload.getValue("desktopProcessActivationStatus").jsonPrimitive.content)
    assertEquals("blocked", payload.getValue("desktopProcessActivationState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessActivationGeneration").jsonPrimitive.int)
    assertEquals("blocked", payload.getValue("desktopProcessSupervisionStatus").jsonPrimitive.content)
    assertEquals("blocked", payload.getValue("desktopProcessSupervisionState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessSupervisionGeneration").jsonPrimitive.int)
    assertEquals("blocked", payload.getValue("desktopProcessObservationStatus").jsonPrimitive.content)
    assertEquals("awaiting_recovery", payload.getValue("desktopProcessObservationState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessObservationGeneration").jsonPrimitive.int)
    assertEquals("planned", payload.getValue("desktopProcessRecoveryStatus").jsonPrimitive.content)
    assertEquals("restore_dependencies", payload.getValue("desktopProcessRecoveryState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessRecoveryGeneration").jsonPrimitive.int)
    assertEquals("blocked", payload.getValue("desktopProcessDetachedLaunchStatus").jsonPrimitive.content)
    assertEquals("awaiting_recovery", payload.getValue("desktopProcessDetachedLaunchState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessDetachedLaunchGeneration").jsonPrimitive.int)
    assertEquals("blocked", payload.getValue("desktopProcessSupervisorLoopStatus").jsonPrimitive.content)
    assertEquals("awaiting_detached_launch", payload.getValue("desktopProcessSupervisorLoopState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessSupervisorLoopGeneration").jsonPrimitive.int)
    assertEquals("blocked", payload.getValue("desktopProcessActiveSessionStatus").jsonPrimitive.content)
    assertEquals("awaiting_supervisor_loop", payload.getValue("desktopProcessActiveSessionState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessActiveSessionGeneration").jsonPrimitive.int)
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
  fun handlePodRuntimeDescribe_reportsProcessDetachedLaunchStateAfterFullReplay() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-desktop-home").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)
    materializeEmbeddedRuntimeDesktopEnvironment(context, "openclaw-desktop-host")
    seedBrowserReplayAndCredential(context, "0.17.0")
    executeEmbeddedRuntimePodTask(context, "runtime-smoke")
    executeEmbeddedRuntimePodTask(context, "tool-brief-inspect")
    executeEmbeddedRuntimePodTask(context, "plugin-allowlist-inspect")
    val handler = PodHandler(context)

    val result = handler.handlePodRuntimeDescribe(null)

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals(
      "process_runtime_active_session_device_proof_bootstrapped",
      payload.getValue("mainlineStatus").jsonPrimitive.content,
    )
    assertEquals(true, payload.getValue("desktopProcessModelReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessActivationReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessSupervisionReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessObservationReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessRecoveryReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessDetachedLaunchReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessSupervisorLoopReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessActiveSessionReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessActiveSessionBootstrapOnly").jsonPrimitive.boolean)
    assertEquals(false, payload.getValue("desktopProcessActiveSessionObserved").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessActiveSessionRecoveryReentryReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessActiveSessionRestartContinuityReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessActiveSessionValidationReady").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessActiveSessionValidationBootstrapOnly").jsonPrimitive.boolean)
    assertEquals(false, payload.getValue("desktopProcessActiveSessionValidationLeaseRenewalObserved").jsonPrimitive.boolean)
    assertEquals(false, payload.getValue("desktopProcessActiveSessionValidationRecoveryReentryObserved").jsonPrimitive.boolean)
    assertEquals(false, payload.getValue("desktopProcessActiveSessionValidationRestartContinuityObserved").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessActiveSessionValidationDeviceProofRequired").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopLongLivedProcessReady").jsonPrimitive.boolean)
    assertEquals("standby", payload.getValue("desktopProcessStatus").jsonPrimitive.content)
    assertEquals("ready", payload.getValue("desktopProcessActivationStatus").jsonPrimitive.content)
    assertEquals("pending_supervisor", payload.getValue("desktopProcessActivationState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessActivationGeneration").jsonPrimitive.int)
    assertEquals("active", payload.getValue("desktopProcessSupervisionStatus").jsonPrimitive.content)
    assertEquals("lease_active", payload.getValue("desktopProcessSupervisionState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessSupervisionGeneration").jsonPrimitive.int)
    assertEquals("healthy", payload.getValue("desktopProcessObservationStatus").jsonPrimitive.content)
    assertEquals("lease_fresh", payload.getValue("desktopProcessObservationState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessObservationGeneration").jsonPrimitive.int)
    assertEquals("keep_lease_fresh", payload.getValue("desktopProcessObservationRecoveryHint").jsonPrimitive.content)
    assertEquals("ready", payload.getValue("desktopProcessRecoveryStatus").jsonPrimitive.content)
    assertEquals("monitoring", payload.getValue("desktopProcessRecoveryState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessRecoveryGeneration").jsonPrimitive.int)
    assertEquals(1, payload.getValue("desktopProcessRecoveryActionCount").jsonPrimitive.int)
    assertEquals("keep_lease_fresh", payload.getValue("desktopProcessRecoveryPrimaryAction").jsonPrimitive.content)
    assertEquals("lease_fresh", payload.getValue("desktopProcessRecoveryReason").jsonPrimitive.content)
    assertEquals("ready", payload.getValue("desktopProcessDetachedLaunchStatus").jsonPrimitive.content)
    assertEquals("launch_contract_ready", payload.getValue("desktopProcessDetachedLaunchState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessDetachedLaunchGeneration").jsonPrimitive.int)
    assertEquals(
      "openclaw-desktop-host-runtime-smoke-1-detached-launch-1",
      payload.getValue("desktopProcessDetachedLaunchSessionId").jsonPrimitive.content,
    )
    assertTrue(
      payload.getValue("desktopProcessDetachedLaunchCommand").jsonPrimitive.content.contains("--launch-detached"),
    )
    assertEquals("ready", payload.getValue("desktopProcessSupervisorLoopStatus").jsonPrimitive.content)
    assertEquals("bootstrap_ready", payload.getValue("desktopProcessSupervisorLoopState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessSupervisorLoopGeneration").jsonPrimitive.int)
    assertEquals(
      "openclaw-desktop-host-runtime-smoke-1-supervisor-loop-1",
      payload.getValue("desktopProcessSupervisorLoopSessionId").jsonPrimitive.content,
    )
    assertEquals("ready", payload.getValue("desktopProcessActiveSessionStatus").jsonPrimitive.content)
    assertEquals("contract_ready", payload.getValue("desktopProcessActiveSessionState").jsonPrimitive.content)
    assertEquals(1, payload.getValue("desktopProcessActiveSessionGeneration").jsonPrimitive.int)
    assertEquals(
      "openclaw-desktop-host-runtime-smoke-1-active-session-1",
      payload.getValue("desktopProcessActiveSessionSessionId").jsonPrimitive.content,
    )
    assertEquals(
      "openclaw-desktop-host-runtime-smoke-1-supervisor-loop-1",
      payload.getValue("desktopProcessActiveSessionLeaseOwnerSessionId").jsonPrimitive.content,
    )
    assertEquals(
      "pending_device_proof",
      payload.getValue("desktopProcessActiveSessionValidationStatus").jsonPrimitive.content,
    )
    assertEquals(
      "awaiting_device_proof",
      payload.getValue("desktopProcessActiveSessionValidationState").jsonPrimitive.content,
    )
    assertEquals(1, payload.getValue("desktopProcessActiveSessionValidationGeneration").jsonPrimitive.int)
    assertEquals(
      "openclaw-desktop-host-runtime-smoke-1-active-session-1",
      payload.getValue("desktopProcessActiveSessionValidationSessionId").jsonPrimitive.content,
    )
    assertEquals(
      "openclaw-desktop-host-runtime-smoke-1-supervisor-loop-1",
      payload.getValue("desktopProcessActiveSessionValidationLeaseOwnerSessionId").jsonPrimitive.content,
    )
    assertEquals(
      "device_active_session_proof_missing",
      payload.getValue("desktopProcessActiveSessionValidationBlockedReason").jsonPrimitive.content,
    )
    assertEquals(
      "pending_live_proof",
      payload.getValue("desktopProcessActiveSessionDeviceProofStatus").jsonPrimitive.content,
    )
    assertEquals(
      "awaiting_live_proof",
      payload.getValue("desktopProcessActiveSessionDeviceProofState").jsonPrimitive.content,
    )
    assertEquals(1, payload.getValue("desktopProcessActiveSessionDeviceProofGeneration").jsonPrimitive.int)
    assertEquals(
      "openclaw-desktop-host-runtime-smoke-1-active-session-1",
      payload.getValue("desktopProcessActiveSessionDeviceProofSessionId").jsonPrimitive.content,
    )
    assertEquals(
      "openclaw-desktop-host-runtime-smoke-1-supervisor-loop-1",
      payload.getValue("desktopProcessActiveSessionDeviceProofLeaseOwnerSessionId").jsonPrimitive.content,
    )
    assertEquals(
      "live_active_session_proof_missing",
      payload.getValue("desktopProcessActiveSessionDeviceProofBlockedReason").jsonPrimitive.content,
    )
    assertEquals(
      "process_runtime_active_session_live_proof",
      payload.getValue("recommendedNextSlice").jsonPrimitive.content,
    )
    assertEquals(
      "pnpm android:local-host:embedded-runtime-pod:doctor -- --json",
      payload.getValue("recommendedProofCommand").jsonPrimitive.content,
    )
  }

  @Test
  fun handlePodRuntimeDescribe_reportsCapturedLiveActiveSessionProofAfterRepeatReplay() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-runtime-home").deleteRecursively()
    context.filesDir.resolve("openclaw/embedded-desktop-home").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)
    materializeEmbeddedRuntimeDesktopEnvironment(context, "openclaw-desktop-host")
    seedBrowserReplayAndCredential(context, "0.17.0")
    executeEmbeddedRuntimePodTask(context, "runtime-smoke")
    executeEmbeddedRuntimePodTask(context, "runtime-smoke")
    executeEmbeddedRuntimePodTask(context, "tool-brief-inspect")
    executeEmbeddedRuntimePodTask(context, "plugin-allowlist-inspect")
    val handler = PodHandler(context)

    val result = handler.handlePodRuntimeDescribe(null)

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals(
      "process_runtime_active_session_live_proof_captured",
      payload.getValue("mainlineStatus").jsonPrimitive.content,
    )
    assertEquals(false, payload.getValue("desktopProcessActiveSessionBootstrapOnly").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("desktopProcessActiveSessionObserved").jsonPrimitive.boolean)
    assertEquals(
      "validated",
      payload.getValue("desktopProcessActiveSessionValidationStatus").jsonPrimitive.content,
    )
    assertEquals(
      false,
      payload.getValue("desktopProcessActiveSessionValidationBootstrapOnly").jsonPrimitive.boolean,
    )
    assertEquals(
      true,
      payload.getValue("desktopProcessActiveSessionValidationLeaseRenewalObserved").jsonPrimitive.boolean,
    )
    assertEquals(
      true,
      payload.getValue("desktopProcessActiveSessionValidationRecoveryReentryObserved").jsonPrimitive.boolean,
    )
    assertEquals(
      true,
      payload.getValue("desktopProcessActiveSessionValidationRestartContinuityObserved").jsonPrimitive.boolean,
    )
    assertEquals(
      false,
      payload.getValue("desktopProcessActiveSessionValidationDeviceProofRequired").jsonPrimitive.boolean,
    )
    assertEquals(
      "verified",
      payload.getValue("desktopProcessActiveSessionDeviceProofStatus").jsonPrimitive.content,
    )
    assertEquals(
      false,
      payload.getValue("desktopProcessActiveSessionDeviceProofBootstrapOnly").jsonPrimitive.boolean,
    )
    assertEquals(true, payload.getValue("desktopProcessActiveSessionDeviceProofObserved").jsonPrimitive.boolean)
    assertEquals(
      "process_runtime_lane_hardening",
      payload.getValue("recommendedNextSlice").jsonPrimitive.content,
    )
    assertEquals(
      "pnpm android:local-host:embedded-runtime-pod:stability -- --json --iterations 3",
      payload.getValue("recommendedProofCommand").jsonPrimitive.content,
    )
    assertEquals(
      "pnpm android:local-host:embedded-runtime-pod:soak -- --json",
      payload.getValue("recommendedHardeningCommand").jsonPrimitive.content,
    )
    assertTrue(
      payload
        .getValue("recommendedNextStep")
        .jsonPrimitive
        .content
        .contains("embedded-runtime-pod:stability -- --json --iterations 3"),
    )
    assertTrue(
      payload
        .getValue("recommendedNextStep")
        .jsonPrimitive
        .content
        .contains("embedded-runtime-pod:soak -- --json"),
    )
    val capturedLiveProofPayload =
      json
        .parseToJsonElement(
          context.filesDir
            .resolve("openclaw/embedded-desktop-home/0.17.0/state/runtime-smoke-active-session-device-proof.json")
            .readText(),
        ).jsonObject
    assertEquals(
      "pnpm android:local-host:embedded-runtime-pod:stability -- --json --iterations 3",
      capturedLiveProofPayload.getValue("proofCommand").jsonPrimitive.content,
    )
    assertEquals(
      "pnpm android:local-host:embedded-runtime-pod:soak -- --json",
      capturedLiveProofPayload.getValue("hardeningCommand").jsonPrimitive.content,
    )
    val proofCommands = capturedLiveProofPayload.getValue("proofCommands").jsonArray
    assertEquals(
      "pnpm android:local-host:embedded-runtime-pod:stability -- --json --iterations 3",
      proofCommands.first().jsonPrimitive.content,
    )
    assertEquals(
      "pnpm android:local-host:embedded-runtime-pod:soak -- --json",
      proofCommands[1].jsonPrimitive.content,
    )
    assertEquals(
      "stability-summary",
      capturedLiveProofPayload.getValue("requiredArtifacts").jsonArray.first().jsonPrimitive.content,
    )
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
