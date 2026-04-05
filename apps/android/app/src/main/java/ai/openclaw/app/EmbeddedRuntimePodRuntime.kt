package ai.openclaw.app

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

private const val embeddedRuntimeStageDirectoryName = "runtime"
private const val embeddedToolkitStageDirectoryName = "toolkit"
private const val embeddedRuntimeDesktopStageDirectoryName = "desktop"
private const val embeddedRuntimeDefaultDesktopProfileId = "openclaw-desktop-host"
private const val embeddedRuntimeHomeRootDisplayPath = "filesDir/openclaw/embedded-runtime-home"
private const val embeddedDesktopHomeRootDisplayPathForRuntime = "filesDir/openclaw/embedded-desktop-home"
private const val embeddedRuntimeDefaultTaskId = "runtime-smoke"
private const val embeddedRuntimeDesktopProfileReplayWorkFile = "work/runtime-smoke-desktop-profile.json"
private const val embeddedRuntimeDesktopProfileReplayStateFile = "state/runtime-smoke-desktop-profile.json"
private const val embeddedRuntimeDesktopHealthReportStateFile = "state/runtime-smoke-health-report.json"
private const val embeddedRuntimeDesktopRestartContractStateFile = "state/runtime-smoke-restart-contract.json"
private const val embeddedRuntimeDesktopProcessModelStateFile = "state/runtime-smoke-process-model.json"
private const val embeddedRuntimeDesktopProcessActivationStateFile = "state/runtime-smoke-activation-contract.json"
private const val embeddedRuntimeDesktopProcessSupervisionStateFile = "state/runtime-smoke-supervision-contract.json"
private const val embeddedRuntimeDesktopProcessObservationStateFile = "state/runtime-smoke-observation-contract.json"
private const val embeddedRuntimeDesktopProcessRecoveryStateFile = "state/runtime-smoke-recovery-contract.json"
private const val embeddedRuntimeDesktopProcessDetachedLaunchStateFile = "state/runtime-smoke-detached-launch-contract.json"
private const val embeddedRuntimeDesktopProcessDetachedLaunchLogFile = "logs/runtime-smoke-detached-launch.log"
private const val embeddedRuntimeDesktopProcessDetachedLaunchSentinelFile = "state/runtime-smoke-detached-launch-sentinel.json"
private const val embeddedRuntimeDesktopLogFileNameForRuntime = "desktop-home.log"

private val embeddedRuntimeRuntimeJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class EmbeddedRuntimeStageManifest(
  val stage: String,
  val purpose: String? = null,
  val engineId: String? = null,
  val engineManifestPath: String? = null,
  val configFiles: List<String> = emptyList(),
  val taskIds: List<String> = emptyList(),
)

@Serializable
private data class EmbeddedRuntimeEngineManifest(
  val engineId: String,
  val engineVersion: String? = null,
  val kind: String? = null,
  val supportedTaskKinds: List<String> = emptyList(),
)

@Serializable
private data class EmbeddedRuntimeTaskDefinition(
  val schemaVersion: Int = 1,
  val taskId: String,
  val displayName: String? = null,
  val kind: String,
  val summary: String? = null,
  val toolId: String? = null,
  val pluginId: String? = null,
  val packagedConfigPath: String? = null,
  val packagedWorkspacePath: String? = null,
  val packagedToolDescriptorPath: String? = null,
  val packagedPluginDescriptorPath: String? = null,
  val stateFile: String,
  val logFile: String,
  val resultFile: String? = null,
)

@Serializable
private data class EmbeddedToolkitStageManifest(
  val stage: String,
  val purpose: String? = null,
  val commandPolicyPath: String? = null,
  val toolDescriptorPaths: List<String> = emptyList(),
  val toolIds: List<String> = emptyList(),
)

@Serializable
private data class EmbeddedRuntimeToolDescriptor(
  val schemaVersion: Int = 1,
  val toolId: String,
  val displayName: String? = null,
  val kind: String,
  val summary: String? = null,
  val runtimeTaskIds: List<String> = emptyList(),
  val inputKind: String? = null,
  val supportedFileExtensions: List<String> = emptyList(),
  val capabilities: List<String> = emptyList(),
  val limitations: List<String> = emptyList(),
)

@Serializable
private data class EmbeddedRuntimeToolCommandPolicy(
  val schemaVersion: Int = 1,
  val allowedTaskKinds: List<String> = emptyList(),
  val allowedToolIds: List<String> = emptyList(),
)

data class EmbeddedRuntimeCarrierInspection(
  val runtimeStageInstalled: Boolean,
  val runtimeStageManifestPresent: Boolean,
  val runtimeEngineManifestPresent: Boolean,
  val runtimeTaskCount: Int,
  val runtimeTaskIds: List<String>,
  val runtimeToolTaskIds: List<String>,
  val runtimePluginTaskIds: List<String>,
  val runtimeConfigCount: Int,
  val engineId: String? = null,
  val engineVersion: String? = null,
  val runtimeHomeExists: Boolean,
  val runtimeHomeReady: Boolean,
  val runtimeHomeVersion: String? = null,
  val runtimeExecutionStateCount: Int = 0,
  val toolkitStageInstalled: Boolean = false,
  val toolkitStageManifestPresent: Boolean = false,
  val toolkitCommandPolicyPresent: Boolean = false,
  val toolDescriptorCount: Int = 0,
  val toolIds: List<String> = emptyList(),
  val runtimeToolExecutionStateCount: Int = 0,
  val runtimePluginExecutionStateCount: Int = 0,
)

data class EmbeddedRuntimePodRuntimeExecuteResult(
  val ok: Boolean,
  val payload: JsonObject? = null,
  val code: String? = null,
  val message: String? = null,
)

data class EmbeddedRuntimeDesktopProfileReplayInspection(
  val replayReady: Boolean = false,
  val statePresent: Boolean = false,
  val resultPresent: Boolean = false,
  val status: String? = null,
  val environmentSupervisionReady: Boolean = false,
  val healthReportPresent: Boolean = false,
  val healthStatus: String? = null,
  val healthReportPath: String? = null,
  val restartContractPresent: Boolean = false,
  val restartStatus: String? = null,
  val restartGeneration: Int = 0,
  val restartContractPath: String? = null,
  val processModelReady: Boolean = false,
  val processStatePresent: Boolean = false,
  val processStatus: String? = null,
  val processStatePath: String? = null,
  val processSessionId: String? = null,
  val processBootstrapOnly: Boolean = false,
  val processActivationReady: Boolean = false,
  val processActivationStatePresent: Boolean = false,
  val processActivationStatus: String? = null,
  val processActivationState: String? = null,
  val processActivationStatePath: String? = null,
  val processActivationGeneration: Int = 0,
  val processActivationBlockedReason: String? = null,
  val processActivationBootstrapOnly: Boolean = false,
  val processSupervisionReady: Boolean = false,
  val processSupervisionStatePresent: Boolean = false,
  val processSupervisionStatus: String? = null,
  val processSupervisionState: String? = null,
  val processSupervisionStatePath: String? = null,
  val processSupervisionGeneration: Int = 0,
  val processSupervisionHeartbeatAt: String? = null,
  val processSupervisionLeaseExpiresAt: String? = null,
  val processSupervisionBlockedReason: String? = null,
  val processSupervisionBootstrapOnly: Boolean = false,
  val processObservationReady: Boolean = false,
  val processObservationStatePresent: Boolean = false,
  val processObservationStatus: String? = null,
  val processObservationState: String? = null,
  val processObservationStatePath: String? = null,
  val processObservationGeneration: Int = 0,
  val processObservationObservedAt: String? = null,
  val processObservationHeartbeatAgeSeconds: Int = 0,
  val processObservationLeaseRemainingSeconds: Int = 0,
  val processObservationLeaseHealth: String? = null,
  val processObservationRecoveryHint: String? = null,
  val processObservationBootstrapOnly: Boolean = false,
  val processRecoveryReady: Boolean = false,
  val processRecoveryStatePresent: Boolean = false,
  val processRecoveryStatus: String? = null,
  val processRecoveryState: String? = null,
  val processRecoveryStatePath: String? = null,
  val processRecoveryGeneration: Int = 0,
  val processRecoveryActionCount: Int = 0,
  val processRecoveryPrimaryAction: String? = null,
  val processRecoveryReason: String? = null,
  val processRecoveryBootstrapOnly: Boolean = false,
  val processDetachedLaunchReady: Boolean = false,
  val processDetachedLaunchStatePresent: Boolean = false,
  val processDetachedLaunchStatus: String? = null,
  val processDetachedLaunchState: String? = null,
  val processDetachedLaunchStatePath: String? = null,
  val processDetachedLaunchGeneration: Int = 0,
  val processDetachedLaunchSessionId: String? = null,
  val processDetachedLaunchCommand: String? = null,
  val processDetachedLaunchBlockedReason: String? = null,
  val processDetachedLaunchBootstrapOnly: Boolean = false,
  val longLivedProcessReady: Boolean = false,
  val profileId: String? = null,
  val environmentId: String? = null,
  val supervisorId: String? = null,
  val restartSupported: Boolean = false,
  val healthReportSupported: Boolean = false,
  val dependencyCount: Int = 0,
  val missingDependencyCount: Int = 0,
  val stateFilePath: String? = null,
  val resultFilePath: String? = null,
)

fun inspectEmbeddedRuntimeCarrier(
  context: Context,
  manifestVersion: String?,
): EmbeddedRuntimeCarrierInspection {
  if (manifestVersion.isNullOrBlank()) {
    return EmbeddedRuntimeCarrierInspection(
      runtimeStageInstalled = false,
      runtimeStageManifestPresent = false,
      runtimeEngineManifestPresent = false,
      runtimeTaskCount = 0,
      runtimeTaskIds = emptyList(),
      runtimeToolTaskIds = emptyList(),
      runtimePluginTaskIds = emptyList(),
      runtimeConfigCount = 0,
      runtimeHomeExists = false,
      runtimeHomeReady = false,
    )
  }

  val versionDir = embeddedRuntimePodInstallRoot(context).resolve(manifestVersion)
  val runtimeStageRoot = versionDir.resolve(embeddedRuntimeStageDirectoryName)
  val runtimeStageInstalled = runtimeStageRoot.isDirectory
  val runtimeStageManifest = runtimeStageRoot.resolve("manifest.json").takeIf { it.isFile }?.let(::loadRuntimeStageManifest)
  val runtimeEngineManifestFile = runtimeStageRoot.resolve("engine/manifest.json")
  val runtimeEngineManifest = runtimeEngineManifestFile.takeIf { it.isFile }?.let(::loadRuntimeEngineManifest)
  val runtimeTaskFiles =
    runtimeStageRoot.resolve("tasks").takeIf { it.isDirectory }?.listFiles()?.filter {
      it.isFile && it.extension.lowercase() == "json"
    }.orEmpty().sortedBy { it.name }
  val runtimeTasks = runtimeTaskFiles.mapNotNull(::loadRuntimeTaskDefinition)
  val runtimeTaskIds =
    runtimeStageManifest?.taskIds
      ?.ifEmpty { runtimeTasks.map { it.taskId } }
      ?.ifEmpty { runtimeTaskFiles.map { it.nameWithoutExtension } }
      .orEmpty()
      .distinct()
      .sorted()
  val runtimeToolTaskIds = runtimeTasks.filter { it.kind == "desktop-tool" }.map { it.taskId }.distinct().sorted()
  val runtimePluginTaskIds = runtimeTasks.filter { it.kind == "desktop-plugin" }.map { it.taskId }.distinct().sorted()
  val runtimeConfigCount = runtimeStageRoot.resolve("config").takeIf { it.isDirectory }?.listFiles()?.count { it.isFile } ?: 0

  val toolkitStageRoot = versionDir.resolve(embeddedToolkitStageDirectoryName)
  val toolkitStageInstalled = toolkitStageRoot.isDirectory
  val toolkitStageManifest = toolkitStageRoot.resolve("manifest.json").takeIf { it.isFile }?.let(::loadToolkitStageManifest)
  val toolDescriptorFiles =
    toolkitStageManifest?.toolDescriptorPaths?.mapNotNull { resolveRelativePath(toolkitStageRoot, it)?.takeIf(File::isFile) }
      .orEmpty()
      .ifEmpty {
        toolkitStageRoot.resolve("tools").takeIf { it.isDirectory }?.listFiles()?.filter {
          it.isFile && it.extension.lowercase() == "json"
        }.orEmpty().sortedBy { it.name }
      }
  val toolDescriptors = toolDescriptorFiles.mapNotNull(::loadRuntimeToolDescriptor)
  val toolIds = (toolkitStageManifest?.toolIds.orEmpty() + toolDescriptors.map { it.toolId }).distinct().sorted()
  val toolkitCommandPolicyPresent =
    toolkitStageManifest?.commandPolicyPath?.let { resolveRelativePath(toolkitStageRoot, it) }?.isFile == true ||
      toolkitStageRoot.resolve("command-policy.json").isFile

  val runtimeHome = embeddedRuntimeHomeRoot(context).resolve(manifestVersion)
  val stateDir = runtimeHome.resolve("state")
  val stateFiles = stateDir.takeIf { it.isDirectory }?.listFiles()?.filter { it.isFile && it.extension.lowercase() == "json" }.orEmpty()
  return EmbeddedRuntimeCarrierInspection(
    runtimeStageInstalled = runtimeStageInstalled,
    runtimeStageManifestPresent = runtimeStageManifest != null,
    runtimeEngineManifestPresent = runtimeEngineManifest != null,
    runtimeTaskCount = runtimeTaskIds.size,
    runtimeTaskIds = runtimeTaskIds,
    runtimeToolTaskIds = runtimeToolTaskIds,
    runtimePluginTaskIds = runtimePluginTaskIds,
    runtimeConfigCount = runtimeConfigCount,
    engineId = runtimeEngineManifest?.engineId ?: runtimeStageManifest?.engineId,
    engineVersion = runtimeEngineManifest?.engineVersion,
    runtimeHomeExists = runtimeHome.isDirectory,
    runtimeHomeReady =
      runtimeHome.isDirectory &&
        runtimeHome.resolve("config/runtime-env.json").isFile &&
        runtimeHome.resolve("logs").isDirectory &&
        runtimeHome.resolve("state").isDirectory &&
        runtimeHome.resolve("work").isDirectory,
    runtimeHomeVersion = manifestVersion,
    runtimeExecutionStateCount = stateFiles.size,
    toolkitStageInstalled = toolkitStageInstalled,
    toolkitStageManifestPresent = toolkitStageManifest != null,
    toolkitCommandPolicyPresent = toolkitCommandPolicyPresent,
    toolDescriptorCount = toolDescriptors.size,
    toolIds = toolIds,
    runtimeToolExecutionStateCount = stateFiles.count { it.nameWithoutExtension in runtimeToolTaskIds },
    runtimePluginExecutionStateCount = stateFiles.count { it.nameWithoutExtension in runtimePluginTaskIds },
  )
}

fun executeEmbeddedRuntimePodTask(
  context: Context,
  taskId: String?,
): EmbeddedRuntimePodRuntimeExecuteResult {
  val inspection = inspectEmbeddedRuntimePod(context)
  val manifestVersion = inspection.manifestVersion
  if (!inspection.ready || manifestVersion == null) {
    return EmbeddedRuntimePodRuntimeExecuteResult(
      ok = false,
      code = "POD_NOT_READY",
      message = "POD_NOT_READY: embedded runtime pod not ready",
    )
  }

  val normalizedTaskId = taskId?.trim().takeUnless { it.isNullOrEmpty() } ?: embeddedRuntimeDefaultTaskId
  val versionDir = embeddedRuntimePodInstallRoot(context).resolve(manifestVersion)
  val runtimeStageRoot = versionDir.resolve(embeddedRuntimeStageDirectoryName)
  val runtimeStageManifest =
    runtimeStageRoot.resolve("manifest.json").takeIf { it.isFile }?.let(::loadRuntimeStageManifest)
      ?: return EmbeddedRuntimePodRuntimeExecuteResult(
        ok = false,
        code = "RUNTIME_NOT_READY",
        message = "RUNTIME_NOT_READY: runtime stage manifest missing",
      )
  val runtimeEngineManifest =
    runtimeStageManifest.engineManifestPath
      ?.let { resolveRelativePath(runtimeStageRoot, it) }
      ?.takeIf { it.isFile }
      ?.let(::loadRuntimeEngineManifest)
      ?: return EmbeddedRuntimePodRuntimeExecuteResult(
        ok = false,
        code = "RUNTIME_NOT_READY",
        message = "RUNTIME_NOT_READY: runtime engine manifest missing",
      )
  val taskFile =
    runtimeStageRoot.resolve("tasks/$normalizedTaskId.json").takeIf { it.isFile }
      ?: return EmbeddedRuntimePodRuntimeExecuteResult(
        ok = false,
        code = "NOT_FOUND",
        message = "NOT_FOUND: runtime task not found",
      )
  val task =
    loadRuntimeTaskDefinition(taskFile)
      ?: return EmbeddedRuntimePodRuntimeExecuteResult(
        ok = false,
        code = "UNAVAILABLE",
        message = "UNAVAILABLE: runtime task metadata invalid",
      )
  if (runtimeEngineManifest.supportedTaskKinds.isNotEmpty() && task.kind !in runtimeEngineManifest.supportedTaskKinds) {
    return EmbeddedRuntimePodRuntimeExecuteResult(
      ok = false,
      code = "UNSUPPORTED_TASK",
      message = "UNSUPPORTED_TASK: ${task.kind} is not supported by the embedded runtime engine",
    )
  }
  if (task.kind != "runtime-smoke" && task.kind != "desktop-tool" && task.kind != "desktop-plugin") {
    return EmbeddedRuntimePodRuntimeExecuteResult(
      ok = false,
      code = "UNSUPPORTED_TASK",
      message = "UNSUPPORTED_TASK: ${task.kind} is not supported by the embedded runtime carrier yet",
    )
  }

  val packagedConfigFile =
    task.packagedConfigPath?.let { resolveRelativePath(runtimeStageRoot, it) }?.takeIf { it.isFile }
      ?: return EmbeddedRuntimePodRuntimeExecuteResult(
        ok = false,
        code = "RUNTIME_NOT_READY",
        message = "RUNTIME_NOT_READY: packaged runtime config missing",
      )
  val packagedConfig =
    readRuntimeJsonObjectOrNull(packagedConfigFile)
      ?: return EmbeddedRuntimePodRuntimeExecuteResult(
        ok = false,
        code = "UNAVAILABLE",
        message = "UNAVAILABLE: packaged runtime config invalid",
      )
  val packagedWorkspaceFile =
    task.packagedWorkspacePath?.let { resolveRelativePath(versionDir, it) }?.takeIf { it.isFile }
      ?: return EmbeddedRuntimePodRuntimeExecuteResult(
        ok = false,
        code = "RUNTIME_NOT_READY",
        message = "RUNTIME_NOT_READY: packaged workspace input missing",
      )

  val runtimeHome = embeddedRuntimeHomeRoot(context).resolve(manifestVersion)
  val runtimeHomeExisted = runtimeHome.isDirectory
  listOf(runtimeHome, runtimeHome.resolve("config"), runtimeHome.resolve("logs"), runtimeHome.resolve("state"), runtimeHome.resolve("work"))
    .forEach(File::mkdirs)

  val now = Instant.now().toString()
  val hydratedConfig =
    buildJsonObject {
      packagedConfig.forEach { (key, value) -> put(key, value) }
      put("podVersion", JsonPrimitive(manifestVersion))
      put("engineVersion", JsonPrimitive(runtimeEngineManifest.engineVersion ?: "0.0.0"))
      put("lastTaskId", JsonPrimitive(task.taskId))
      put("lastTaskKind", JsonPrimitive(task.kind))
      put("lastExecutedAt", JsonPrimitive(now))
    }
  runtimeHome.resolve("config/runtime-env.json").writeText("${hydratedConfig}\n", Charsets.UTF_8)

  val stateFile =
    resolveRelativePath(runtimeHome, task.stateFile)
      ?: return EmbeddedRuntimePodRuntimeExecuteResult(
        ok = false,
        code = "INVALID_REQUEST",
        message = "INVALID_REQUEST: runtime state file escapes runtime home",
      )
  val previousState = stateFile.takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
  val executionCount =
    (previousState?.get("executionCount") as? JsonPrimitive)?.content?.toIntOrNull()?.plus(1) ?: 1

  var toolId: String? = null
  var toolResultFilePath: String? = null
  var toolDescriptorPayload: JsonObject? = null
  var toolResultPayload: JsonObject? = null
  var toolkitStageInstalled = false
  var toolkitStageManifestPresent = false
  var toolkitCommandPolicyPresent = false
  var packagedToolDescriptorPresent = false
  var pluginId: String? = null
  var pluginResultFilePath: String? = null
  var pluginDescriptorPayload: JsonObject? = null
  var pluginResultPayload: JsonObject? = null
  var desktopPluginsManifestPresent = false
  var packagedPluginDescriptorPresent = false
  var desktopProfileReplayPayload: JsonObject? = null
  var desktopProfileReplayStatePath: String? = null
  var desktopProfileReplayResultPath: String? = null
  var desktopProfileReplayReady = false
  var desktopEnvironmentSupervisionReady = false
  var desktopHealthReportPath: String? = null
  var desktopRestartContractPath: String? = null
  var desktopProcessModelReady = false
  var desktopProcessStatePath: String? = null
  var desktopProcessStatus: String? = null
  var desktopProcessSessionId: String? = null
  var desktopProcessActivationReady = false
  var desktopProcessActivationStatePath: String? = null
  var desktopProcessActivationStatus: String? = null
  var desktopProcessActivationState: String? = null
  var desktopProcessActivationGeneration = 0
  var desktopProcessActivationBlockedReason: String? = null
  var desktopProcessSupervisionReady = false
  var desktopProcessSupervisionStatePath: String? = null
  var desktopProcessSupervisionStatus: String? = null
  var desktopProcessSupervisionState: String? = null
  var desktopProcessSupervisionGeneration = 0
  var desktopProcessSupervisionHeartbeatAt: String? = null
  var desktopProcessSupervisionLeaseExpiresAt: String? = null
  var desktopProcessSupervisionBlockedReason: String? = null
  var desktopProcessObservationReady = false
  var desktopProcessObservationStatePath: String? = null
  var desktopProcessObservationStatus: String? = null
  var desktopProcessObservationState: String? = null
  var desktopProcessObservationGeneration = 0
  var desktopProcessObservationObservedAt: String? = null
  var desktopProcessObservationHeartbeatAgeSeconds = 0
  var desktopProcessObservationLeaseRemainingSeconds = 0
  var desktopProcessObservationLeaseHealth: String? = null
  var desktopProcessObservationRecoveryHint: String? = null
  var desktopProcessRecoveryReady = false
  var desktopProcessRecoveryStatePath: String? = null
  var desktopProcessRecoveryStatus: String? = null
  var desktopProcessRecoveryState: String? = null
  var desktopProcessRecoveryGeneration = 0
  var desktopProcessRecoveryActionCount = 0
  var desktopProcessRecoveryPrimaryAction: String? = null
  var desktopProcessRecoveryReason: String? = null
  var desktopProcessDetachedLaunchReady = false
  var desktopProcessDetachedLaunchStatePath: String? = null
  var desktopProcessDetachedLaunchStatus: String? = null
  var desktopProcessDetachedLaunchState: String? = null
  var desktopProcessDetachedLaunchGeneration = 0
  var desktopProcessDetachedLaunchSessionId: String? = null
  var desktopProcessDetachedLaunchCommand: String? = null
  var desktopProcessDetachedLaunchBlockedReason: String? = null
  var desktopLongLivedProcessReady = false

  if (task.kind == "desktop-tool") {
    val toolResult =
      executePackagedDesktopTool(
        versionDir = versionDir,
        runtimeHome = runtimeHome,
        task = task,
        packagedWorkspaceFile = packagedWorkspaceFile,
      ) ?: return EmbeddedRuntimePodRuntimeExecuteResult(
        ok = false,
        code = "UNAVAILABLE",
        message = "UNAVAILABLE: packaged desktop tool execution failed",
      )
    toolId = toolResult.toolId
    toolResultFilePath = runtimeHomeDisplayPath(manifestVersion, toolResult.resultRelativePath)
    toolDescriptorPayload = toolResult.descriptor
    toolResultPayload = toolResult.result
    toolkitStageInstalled = toolResult.toolkitStageInstalled
    toolkitStageManifestPresent = toolResult.toolkitStageManifestPresent
    toolkitCommandPolicyPresent = toolResult.toolkitCommandPolicyPresent
    packagedToolDescriptorPresent = toolResult.packagedToolDescriptorPresent
  }
  if (task.kind == "desktop-plugin") {
    val pluginResult =
      executeAllowlistedPluginTask(
        context = context,
        manifestVersion = manifestVersion,
        versionDir = versionDir,
        runtimeHome = runtimeHome,
        task = task,
        packagedWorkspaceFile = packagedWorkspaceFile,
      ) ?: return EmbeddedRuntimePodRuntimeExecuteResult(
        ok = false,
        code = "UNAVAILABLE",
        message = "UNAVAILABLE: allowlisted plugin execution failed",
      )
    pluginId = pluginResult.pluginId
    pluginResultFilePath = runtimeHomeDisplayPath(manifestVersion, pluginResult.resultRelativePath)
    pluginDescriptorPayload = pluginResult.descriptor
    pluginResultPayload = pluginResult.result
    desktopPluginsManifestPresent = pluginResult.desktopPluginsManifestPresent
    packagedPluginDescriptorPresent = pluginResult.packagedPluginDescriptorPresent
  }
  if (task.kind == "runtime-smoke") {
    val desktopReplay =
      executeEmbeddedRuntimeDesktopProfileReplay(
        context = context,
        manifestVersion = manifestVersion,
        runtimeHome = runtimeHome,
        executionCount = executionCount,
        executedAt = now,
      )
    desktopProfileReplayPayload = desktopReplay?.payload
    desktopProfileReplayStatePath = desktopReplay?.stateFilePath
    desktopProfileReplayResultPath = desktopReplay?.resultFilePath
    desktopProfileReplayReady = desktopReplay != null
    desktopEnvironmentSupervisionReady = desktopReplay?.environmentSupervisionReady == true
    desktopHealthReportPath = desktopReplay?.healthReportPath
    desktopRestartContractPath = desktopReplay?.restartContractPath
    desktopProcessModelReady = desktopReplay?.processModelReady == true
    desktopProcessStatePath = desktopReplay?.processStatePath
    desktopProcessStatus = desktopReplay?.processStatus
    desktopProcessSessionId = desktopReplay?.processSessionId
    desktopProcessActivationReady = desktopReplay?.processActivationReady == true
    desktopProcessActivationStatePath = desktopReplay?.processActivationStatePath
    desktopProcessActivationStatus = desktopReplay?.processActivationStatus
    desktopProcessActivationState = desktopReplay?.processActivationState
    desktopProcessActivationGeneration = desktopReplay?.processActivationGeneration ?: 0
    desktopProcessActivationBlockedReason = desktopReplay?.processActivationBlockedReason
    desktopProcessSupervisionReady = desktopReplay?.processSupervisionReady == true
    desktopProcessSupervisionStatePath = desktopReplay?.processSupervisionStatePath
    desktopProcessSupervisionStatus = desktopReplay?.processSupervisionStatus
    desktopProcessSupervisionState = desktopReplay?.processSupervisionState
    desktopProcessSupervisionGeneration = desktopReplay?.processSupervisionGeneration ?: 0
    desktopProcessSupervisionHeartbeatAt = desktopReplay?.processSupervisionHeartbeatAt
    desktopProcessSupervisionLeaseExpiresAt = desktopReplay?.processSupervisionLeaseExpiresAt
    desktopProcessSupervisionBlockedReason = desktopReplay?.processSupervisionBlockedReason
    desktopProcessObservationReady = desktopReplay?.processObservationReady == true
    desktopProcessObservationStatePath = desktopReplay?.processObservationStatePath
    desktopProcessObservationStatus = desktopReplay?.processObservationStatus
    desktopProcessObservationState = desktopReplay?.processObservationState
    desktopProcessObservationGeneration = desktopReplay?.processObservationGeneration ?: 0
    desktopProcessObservationObservedAt = desktopReplay?.processObservationObservedAt
    desktopProcessObservationHeartbeatAgeSeconds = desktopReplay?.processObservationHeartbeatAgeSeconds ?: 0
    desktopProcessObservationLeaseRemainingSeconds = desktopReplay?.processObservationLeaseRemainingSeconds ?: 0
    desktopProcessObservationLeaseHealth = desktopReplay?.processObservationLeaseHealth
    desktopProcessObservationRecoveryHint = desktopReplay?.processObservationRecoveryHint
    desktopProcessRecoveryReady = desktopReplay?.processRecoveryReady == true
    desktopProcessRecoveryStatePath = desktopReplay?.processRecoveryStatePath
    desktopProcessRecoveryStatus = desktopReplay?.processRecoveryStatus
    desktopProcessRecoveryState = desktopReplay?.processRecoveryState
    desktopProcessRecoveryGeneration = desktopReplay?.processRecoveryGeneration ?: 0
    desktopProcessRecoveryActionCount = desktopReplay?.processRecoveryActionCount ?: 0
    desktopProcessRecoveryPrimaryAction = desktopReplay?.processRecoveryPrimaryAction
    desktopProcessRecoveryReason = desktopReplay?.processRecoveryReason
    desktopProcessDetachedLaunchReady = desktopReplay?.processDetachedLaunchReady == true
    desktopProcessDetachedLaunchStatePath = desktopReplay?.processDetachedLaunchStatePath
    desktopProcessDetachedLaunchStatus = desktopReplay?.processDetachedLaunchStatus
    desktopProcessDetachedLaunchState = desktopReplay?.processDetachedLaunchState
    desktopProcessDetachedLaunchGeneration = desktopReplay?.processDetachedLaunchGeneration ?: 0
    desktopProcessDetachedLaunchSessionId = desktopReplay?.processDetachedLaunchSessionId
    desktopProcessDetachedLaunchCommand = desktopReplay?.processDetachedLaunchCommand
    desktopProcessDetachedLaunchBlockedReason = desktopReplay?.processDetachedLaunchBlockedReason
    desktopLongLivedProcessReady = desktopReplay?.longLivedProcessReady == true
  }

  val statePayload =
    buildJsonObject {
      put("taskId", JsonPrimitive(task.taskId))
      put("taskKind", JsonPrimitive(task.kind))
      put("displayName", JsonPrimitive(task.displayName ?: task.taskId))
      put("status", JsonPrimitive("ok"))
      put("engineId", JsonPrimitive(runtimeEngineManifest.engineId))
      put("engineVersion", JsonPrimitive(runtimeEngineManifest.engineVersion ?: "0.0.0"))
      put("podVersion", JsonPrimitive(manifestVersion))
      put("executionCount", JsonPrimitive(executionCount))
      put("executedAt", JsonPrimitive(now))
      put("packagedConfigPath", JsonPrimitive(task.packagedConfigPath ?: ""))
      put("packagedWorkspacePath", JsonPrimitive(task.packagedWorkspacePath ?: ""))
      put("workspaceDocumentSha256", JsonPrimitive(sha256(packagedWorkspaceFile)))
      put("workspaceDocumentSizeBytes", JsonPrimitive(packagedWorkspaceFile.length()))
      put("hydratedConfigPath", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion, "config/runtime-env.json")))
      put("runtimeHomePath", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion)))
      put("desktopProfileReplayReady", JsonPrimitive(desktopProfileReplayReady))
      put("desktopEnvironmentSupervisionReady", JsonPrimitive(desktopEnvironmentSupervisionReady))
      put("desktopProcessModelReady", JsonPrimitive(desktopProcessModelReady))
      put("desktopProcessActivationReady", JsonPrimitive(desktopProcessActivationReady))
      put("desktopProcessSupervisionReady", JsonPrimitive(desktopProcessSupervisionReady))
      put("desktopProcessObservationReady", JsonPrimitive(desktopProcessObservationReady))
      put("desktopProcessRecoveryReady", JsonPrimitive(desktopProcessRecoveryReady))
      put("desktopProcessDetachedLaunchReady", JsonPrimitive(desktopProcessDetachedLaunchReady))
      put("desktopLongLivedProcessReady", JsonPrimitive(desktopLongLivedProcessReady))
      desktopProfileReplayStatePath?.let { put("desktopProfileReplayStatePath", JsonPrimitive(it)) }
      desktopProfileReplayResultPath?.let { put("desktopProfileReplayResultPath", JsonPrimitive(it)) }
      desktopHealthReportPath?.let { put("desktopHealthReportPath", JsonPrimitive(it)) }
      desktopRestartContractPath?.let { put("desktopRestartContractPath", JsonPrimitive(it)) }
      desktopProcessStatePath?.let { put("desktopProcessStatePath", JsonPrimitive(it)) }
      desktopProcessStatus?.let { put("desktopProcessStatus", JsonPrimitive(it)) }
      desktopProcessSessionId?.let { put("desktopProcessSessionId", JsonPrimitive(it)) }
      desktopProcessActivationStatePath?.let { put("desktopProcessActivationStatePath", JsonPrimitive(it)) }
      desktopProcessActivationStatus?.let { put("desktopProcessActivationStatus", JsonPrimitive(it)) }
      desktopProcessActivationState?.let { put("desktopProcessActivationState", JsonPrimitive(it)) }
      put("desktopProcessActivationGeneration", JsonPrimitive(desktopProcessActivationGeneration))
      desktopProcessActivationBlockedReason?.let {
        put("desktopProcessActivationBlockedReason", JsonPrimitive(it))
      }
      desktopProcessSupervisionStatePath?.let { put("desktopProcessSupervisionStatePath", JsonPrimitive(it)) }
      desktopProcessSupervisionStatus?.let { put("desktopProcessSupervisionStatus", JsonPrimitive(it)) }
      desktopProcessSupervisionState?.let { put("desktopProcessSupervisionState", JsonPrimitive(it)) }
      put("desktopProcessSupervisionGeneration", JsonPrimitive(desktopProcessSupervisionGeneration))
      desktopProcessSupervisionHeartbeatAt?.let { put("desktopProcessSupervisionHeartbeatAt", JsonPrimitive(it)) }
      desktopProcessSupervisionLeaseExpiresAt?.let { put("desktopProcessSupervisionLeaseExpiresAt", JsonPrimitive(it)) }
      desktopProcessSupervisionBlockedReason?.let {
        put("desktopProcessSupervisionBlockedReason", JsonPrimitive(it))
      }
      desktopProcessObservationStatePath?.let { put("desktopProcessObservationStatePath", JsonPrimitive(it)) }
      desktopProcessObservationStatus?.let { put("desktopProcessObservationStatus", JsonPrimitive(it)) }
      desktopProcessObservationState?.let { put("desktopProcessObservationState", JsonPrimitive(it)) }
      put("desktopProcessObservationGeneration", JsonPrimitive(desktopProcessObservationGeneration))
      desktopProcessObservationObservedAt?.let { put("desktopProcessObservationObservedAt", JsonPrimitive(it)) }
      put("desktopProcessObservationHeartbeatAgeSeconds", JsonPrimitive(desktopProcessObservationHeartbeatAgeSeconds))
      put("desktopProcessObservationLeaseRemainingSeconds", JsonPrimitive(desktopProcessObservationLeaseRemainingSeconds))
      desktopProcessObservationLeaseHealth?.let { put("desktopProcessObservationLeaseHealth", JsonPrimitive(it)) }
      desktopProcessObservationRecoveryHint?.let { put("desktopProcessObservationRecoveryHint", JsonPrimitive(it)) }
      desktopProcessRecoveryStatePath?.let { put("desktopProcessRecoveryStatePath", JsonPrimitive(it)) }
      desktopProcessRecoveryStatus?.let { put("desktopProcessRecoveryStatus", JsonPrimitive(it)) }
      desktopProcessRecoveryState?.let { put("desktopProcessRecoveryState", JsonPrimitive(it)) }
      put("desktopProcessRecoveryGeneration", JsonPrimitive(desktopProcessRecoveryGeneration))
      put("desktopProcessRecoveryActionCount", JsonPrimitive(desktopProcessRecoveryActionCount))
      desktopProcessRecoveryPrimaryAction?.let { put("desktopProcessRecoveryPrimaryAction", JsonPrimitive(it)) }
      desktopProcessRecoveryReason?.let { put("desktopProcessRecoveryReason", JsonPrimitive(it)) }
      desktopProcessDetachedLaunchStatePath?.let { put("desktopProcessDetachedLaunchStatePath", JsonPrimitive(it)) }
      desktopProcessDetachedLaunchStatus?.let { put("desktopProcessDetachedLaunchStatus", JsonPrimitive(it)) }
      desktopProcessDetachedLaunchState?.let { put("desktopProcessDetachedLaunchState", JsonPrimitive(it)) }
      put("desktopProcessDetachedLaunchGeneration", JsonPrimitive(desktopProcessDetachedLaunchGeneration))
      desktopProcessDetachedLaunchSessionId?.let {
        put("desktopProcessDetachedLaunchSessionId", JsonPrimitive(it))
      }
      desktopProcessDetachedLaunchCommand?.let {
        put("desktopProcessDetachedLaunchCommand", JsonPrimitive(it))
      }
      desktopProcessDetachedLaunchBlockedReason?.let {
        put("desktopProcessDetachedLaunchBlockedReason", JsonPrimitive(it))
      }
      desktopProfileReplayPayload?.let { put("desktopProfileReplay", it) }
      toolId?.let { put("toolId", JsonPrimitive(it)) }
      toolResultFilePath?.let { put("toolResultFilePath", JsonPrimitive(it)) }
      toolResultPayload?.let { put("toolResult", it) }
      pluginId?.let { put("pluginId", JsonPrimitive(it)) }
      pluginResultFilePath?.let { put("pluginResultFilePath", JsonPrimitive(it)) }
      pluginResultPayload?.let { put("pluginResult", it) }
    }
  stateFile.parentFile?.mkdirs()
  stateFile.writeText("${statePayload}\n", Charsets.UTF_8)

  val logFile =
    resolveRelativePath(runtimeHome, task.logFile)
      ?: return EmbeddedRuntimePodRuntimeExecuteResult(
        ok = false,
        code = "INVALID_REQUEST",
        message = "INVALID_REQUEST: runtime log file escapes runtime home",
      )
  logFile.parentFile?.mkdirs()
  logFile.appendText(
    "$now ${task.taskId} kind=${task.kind} engine=${runtimeEngineManifest.engineId} tool=${toolId ?: "none"} plugin=${pluginId ?: "none"} pod=$manifestVersion workspace=${task.packagedWorkspacePath}\n",
    Charsets.UTF_8,
  )

  return EmbeddedRuntimePodRuntimeExecuteResult(
    ok = true,
    payload =
      buildJsonObject {
        put("taskId", JsonPrimitive(task.taskId))
        put("taskKind", JsonPrimitive(task.kind))
        put("displayName", JsonPrimitive(task.displayName ?: task.taskId))
        put("engineId", JsonPrimitive(runtimeEngineManifest.engineId))
        put("engineVersion", JsonPrimitive(runtimeEngineManifest.engineVersion ?: "0.0.0"))
        put("engineKind", JsonPrimitive(runtimeEngineManifest.kind ?: "descriptor"))
        put("summary", JsonPrimitive(task.summary ?: ""))
        put("embeddedPodVersion", JsonPrimitive(manifestVersion))
        put("runtimeHomeCreated", JsonPrimitive(!runtimeHomeExisted))
        put("runtimeHomeReady", JsonPrimitive(true))
        put("executionCount", JsonPrimitive(executionCount))
        put("hydratedConfigPath", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion, "config/runtime-env.json")))
        put("stateFilePath", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion, task.stateFile)))
        put("logFilePath", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion, task.logFile)))
        put("desktopProfileReplayReady", JsonPrimitive(desktopProfileReplayReady))
        put("desktopEnvironmentSupervisionReady", JsonPrimitive(desktopEnvironmentSupervisionReady))
        put("desktopProcessModelReady", JsonPrimitive(desktopProcessModelReady))
        put("desktopProcessActivationReady", JsonPrimitive(desktopProcessActivationReady))
        put("desktopProcessSupervisionReady", JsonPrimitive(desktopProcessSupervisionReady))
        put("desktopProcessObservationReady", JsonPrimitive(desktopProcessObservationReady))
        put("desktopProcessRecoveryReady", JsonPrimitive(desktopProcessRecoveryReady))
        put("desktopProcessDetachedLaunchReady", JsonPrimitive(desktopProcessDetachedLaunchReady))
        put("desktopLongLivedProcessReady", JsonPrimitive(desktopLongLivedProcessReady))
        desktopProfileReplayStatePath?.let { put("desktopProfileReplayStatePath", JsonPrimitive(it)) }
        desktopProfileReplayResultPath?.let { put("desktopProfileReplayResultFilePath", JsonPrimitive(it)) }
        desktopHealthReportPath?.let { put("desktopHealthReportPath", JsonPrimitive(it)) }
        desktopRestartContractPath?.let { put("desktopRestartContractPath", JsonPrimitive(it)) }
        desktopProcessStatePath?.let { put("desktopProcessStatePath", JsonPrimitive(it)) }
        desktopProcessStatus?.let { put("desktopProcessStatus", JsonPrimitive(it)) }
        desktopProcessSessionId?.let { put("desktopProcessSessionId", JsonPrimitive(it)) }
        desktopProcessActivationStatePath?.let { put("desktopProcessActivationStatePath", JsonPrimitive(it)) }
        desktopProcessActivationStatus?.let { put("desktopProcessActivationStatus", JsonPrimitive(it)) }
        desktopProcessActivationState?.let { put("desktopProcessActivationState", JsonPrimitive(it)) }
        put("desktopProcessActivationGeneration", JsonPrimitive(desktopProcessActivationGeneration))
        desktopProcessActivationBlockedReason?.let {
          put("desktopProcessActivationBlockedReason", JsonPrimitive(it))
        }
        desktopProcessSupervisionStatePath?.let { put("desktopProcessSupervisionStatePath", JsonPrimitive(it)) }
        desktopProcessSupervisionStatus?.let { put("desktopProcessSupervisionStatus", JsonPrimitive(it)) }
        desktopProcessSupervisionState?.let { put("desktopProcessSupervisionState", JsonPrimitive(it)) }
        put("desktopProcessSupervisionGeneration", JsonPrimitive(desktopProcessSupervisionGeneration))
        desktopProcessSupervisionHeartbeatAt?.let { put("desktopProcessSupervisionHeartbeatAt", JsonPrimitive(it)) }
        desktopProcessSupervisionLeaseExpiresAt?.let {
          put("desktopProcessSupervisionLeaseExpiresAt", JsonPrimitive(it))
        }
        desktopProcessSupervisionBlockedReason?.let {
          put("desktopProcessSupervisionBlockedReason", JsonPrimitive(it))
        }
        desktopProcessObservationStatePath?.let { put("desktopProcessObservationStatePath", JsonPrimitive(it)) }
        desktopProcessObservationStatus?.let { put("desktopProcessObservationStatus", JsonPrimitive(it)) }
        desktopProcessObservationState?.let { put("desktopProcessObservationState", JsonPrimitive(it)) }
        put("desktopProcessObservationGeneration", JsonPrimitive(desktopProcessObservationGeneration))
        desktopProcessObservationObservedAt?.let { put("desktopProcessObservationObservedAt", JsonPrimitive(it)) }
        put("desktopProcessObservationHeartbeatAgeSeconds", JsonPrimitive(desktopProcessObservationHeartbeatAgeSeconds))
        put("desktopProcessObservationLeaseRemainingSeconds", JsonPrimitive(desktopProcessObservationLeaseRemainingSeconds))
        desktopProcessObservationLeaseHealth?.let { put("desktopProcessObservationLeaseHealth", JsonPrimitive(it)) }
        desktopProcessObservationRecoveryHint?.let {
          put("desktopProcessObservationRecoveryHint", JsonPrimitive(it))
        }
        desktopProcessRecoveryStatePath?.let { put("desktopProcessRecoveryStatePath", JsonPrimitive(it)) }
        desktopProcessRecoveryStatus?.let { put("desktopProcessRecoveryStatus", JsonPrimitive(it)) }
        desktopProcessRecoveryState?.let { put("desktopProcessRecoveryState", JsonPrimitive(it)) }
        put("desktopProcessRecoveryGeneration", JsonPrimitive(desktopProcessRecoveryGeneration))
        put("desktopProcessRecoveryActionCount", JsonPrimitive(desktopProcessRecoveryActionCount))
        desktopProcessRecoveryPrimaryAction?.let {
          put("desktopProcessRecoveryPrimaryAction", JsonPrimitive(it))
        }
        desktopProcessRecoveryReason?.let { put("desktopProcessRecoveryReason", JsonPrimitive(it)) }
        desktopProcessDetachedLaunchStatePath?.let {
          put("desktopProcessDetachedLaunchStatePath", JsonPrimitive(it))
        }
        desktopProcessDetachedLaunchStatus?.let {
          put("desktopProcessDetachedLaunchStatus", JsonPrimitive(it))
        }
        desktopProcessDetachedLaunchState?.let {
          put("desktopProcessDetachedLaunchState", JsonPrimitive(it))
        }
        put("desktopProcessDetachedLaunchGeneration", JsonPrimitive(desktopProcessDetachedLaunchGeneration))
        desktopProcessDetachedLaunchSessionId?.let {
          put("desktopProcessDetachedLaunchSessionId", JsonPrimitive(it))
        }
        desktopProcessDetachedLaunchCommand?.let {
          put("desktopProcessDetachedLaunchCommand", JsonPrimitive(it))
        }
        desktopProcessDetachedLaunchBlockedReason?.let {
          put("desktopProcessDetachedLaunchBlockedReason", JsonPrimitive(it))
        }
        desktopProfileReplayPayload?.let { put("desktopProfileReplay", it) }
        toolId?.let { put("toolId", JsonPrimitive(it)) }
        put("toolkitStageInstalled", JsonPrimitive(toolkitStageInstalled))
        put("toolkitStageManifestPresent", JsonPrimitive(toolkitStageManifestPresent))
        put("toolkitCommandPolicyPresent", JsonPrimitive(toolkitCommandPolicyPresent))
        put("packagedToolDescriptorPresent", JsonPrimitive(packagedToolDescriptorPresent))
        pluginId?.let { put("pluginId", JsonPrimitive(it)) }
        put("desktopPluginsManifestPresent", JsonPrimitive(desktopPluginsManifestPresent))
        put("packagedPluginDescriptorPresent", JsonPrimitive(packagedPluginDescriptorPresent))
        pluginResultFilePath?.let { put("pluginResultFilePath", JsonPrimitive(it)) }
        pluginDescriptorPayload?.let { put("pluginDescriptor", it) }
        pluginResultPayload?.let { put("pluginResult", it) }
        toolResultFilePath?.let { put("toolResultFilePath", JsonPrimitive(it)) }
        toolDescriptorPayload?.let { put("toolDescriptor", it) }
        toolResultPayload?.let { put("toolResult", it) }
        put("state", statePayload)
        put("packagedConfig", hydratedConfig)
      },
  )
}

fun inspectEmbeddedRuntimeDesktopProfileReplay(
  context: Context,
  manifestVersion: String?,
): EmbeddedRuntimeDesktopProfileReplayInspection {
  if (manifestVersion.isNullOrBlank()) return EmbeddedRuntimeDesktopProfileReplayInspection()
  val runtimeHome = embeddedRuntimeHomeRoot(context).resolve(manifestVersion)
  val desktopHome = embeddedDesktopHomeRootForRuntime(context).resolve(manifestVersion)
  val stateFile = desktopHome.resolve(embeddedRuntimeDesktopProfileReplayStateFile)
  val resultFile = runtimeHome.resolve(embeddedRuntimeDesktopProfileReplayWorkFile)
  val healthReportFile = desktopHome.resolve(embeddedRuntimeDesktopHealthReportStateFile)
  val restartContractFile = desktopHome.resolve(embeddedRuntimeDesktopRestartContractStateFile)
  val processModelFile = desktopHome.resolve(embeddedRuntimeDesktopProcessModelStateFile)
  val processActivationFile = desktopHome.resolve(embeddedRuntimeDesktopProcessActivationStateFile)
  val processSupervisionFile = desktopHome.resolve(embeddedRuntimeDesktopProcessSupervisionStateFile)
  val processObservationFile = desktopHome.resolve(embeddedRuntimeDesktopProcessObservationStateFile)
  val processRecoveryFile = desktopHome.resolve(embeddedRuntimeDesktopProcessRecoveryStateFile)
  val processDetachedLaunchFile = desktopHome.resolve(embeddedRuntimeDesktopProcessDetachedLaunchStateFile)
  val statePayload = stateFile.takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
  val healthPayload = healthReportFile.takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
  val restartPayload = restartContractFile.takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
  val processPayload = processModelFile.takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
  val activationPayload = processActivationFile.takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
  val supervisionPayload = processSupervisionFile.takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
  val observationPayload = processObservationFile.takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
  val recoveryPayload = processRecoveryFile.takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
  val detachedLaunchPayload = processDetachedLaunchFile.takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
  val dependencyStatus = statePayload?.get("dependencyStatus") as? JsonObject
  val missingDependencies = statePayload?.get("missingDependencies") as? JsonArray
  val status = runtimePrimitiveContent(statePayload, "status")
  val healthStatus = runtimePrimitiveContent(healthPayload, "status")
  val restartStatus = runtimePrimitiveContent(restartPayload, "status")
  val processStatus = runtimePrimitiveContent(processPayload, "status")
  val processSessionId = runtimePrimitiveContent(processPayload, "sessionId")
  val activationStatus = runtimePrimitiveContent(activationPayload, "status")
  val activationState = runtimePrimitiveContent(activationPayload, "activationState")
  val supervisionStatus = runtimePrimitiveContent(supervisionPayload, "status")
  val supervisionState = runtimePrimitiveContent(supervisionPayload, "supervisionState")
  val observationStatus = runtimePrimitiveContent(observationPayload, "status")
  val observationState = runtimePrimitiveContent(observationPayload, "observationState")
  val recoveryStatus = runtimePrimitiveContent(recoveryPayload, "status")
  val recoveryState = runtimePrimitiveContent(recoveryPayload, "recoveryState")
  val detachedLaunchStatus = runtimePrimitiveContent(detachedLaunchPayload, "status")
  val detachedLaunchState = runtimePrimitiveContent(detachedLaunchPayload, "launchState")
  val environmentSupervisionReady =
    stateFile.isFile &&
      resultFile.isFile &&
      healthReportFile.isFile &&
      restartContractFile.isFile &&
      healthStatus != null &&
      restartStatus != null
  val processModelReady =
    processModelFile.isFile &&
      processStatus != null &&
      processSessionId != null
  val processActivationReady =
    processActivationFile.isFile &&
      activationStatus != null &&
      activationState != null &&
      activationStatus != "unsupported"
  val processSupervisionReady =
    processSupervisionFile.isFile &&
      supervisionStatus != null &&
      supervisionState != null &&
      supervisionStatus != "unsupported"
  val processObservationReady =
    processObservationFile.isFile &&
      observationStatus != null &&
      observationState != null &&
      observationStatus != "unsupported"
  val processRecoveryReady =
    processRecoveryFile.isFile &&
      recoveryStatus != null &&
      recoveryState != null &&
      recoveryStatus != "unsupported"
  val processDetachedLaunchReady =
    processDetachedLaunchFile.isFile &&
      detachedLaunchStatus != null &&
      detachedLaunchState != null &&
      detachedLaunchStatus != "unsupported"
  return EmbeddedRuntimeDesktopProfileReplayInspection(
    replayReady = stateFile.isFile && resultFile.isFile && status != null,
    statePresent = stateFile.isFile,
    resultPresent = resultFile.isFile,
    status = status,
    environmentSupervisionReady = environmentSupervisionReady,
    healthReportPresent = healthReportFile.isFile,
    healthStatus = healthStatus,
    healthReportPath = desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopHealthReportStateFile),
    restartContractPresent = restartContractFile.isFile,
    restartStatus = restartStatus,
    restartGeneration = runtimePrimitiveInt(restartPayload, "generation") ?: 0,
    restartContractPath = desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopRestartContractStateFile),
    processModelReady = processModelReady,
    processStatePresent = processModelFile.isFile,
    processStatus = processStatus,
    processStatePath = desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessModelStateFile),
    processSessionId = processSessionId,
    processBootstrapOnly = runtimePrimitiveBoolean(processPayload, "bootstrapOnly") == true,
    processActivationReady = processActivationReady,
    processActivationStatePresent = processActivationFile.isFile,
    processActivationStatus = activationStatus,
    processActivationState = activationState,
    processActivationStatePath = desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessActivationStateFile),
    processActivationGeneration = runtimePrimitiveInt(activationPayload, "generation") ?: 0,
    processActivationBlockedReason = runtimePrimitiveContent(activationPayload, "blockedReason"),
    processActivationBootstrapOnly = runtimePrimitiveBoolean(activationPayload, "bootstrapOnly") == true,
    processSupervisionReady = processSupervisionReady,
    processSupervisionStatePresent = processSupervisionFile.isFile,
    processSupervisionStatus = supervisionStatus,
    processSupervisionState = supervisionState,
    processSupervisionStatePath = desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessSupervisionStateFile),
    processSupervisionGeneration = runtimePrimitiveInt(supervisionPayload, "generation") ?: 0,
    processSupervisionHeartbeatAt = runtimePrimitiveContent(supervisionPayload, "heartbeatAt"),
    processSupervisionLeaseExpiresAt = runtimePrimitiveContent(supervisionPayload, "leaseExpiresAt"),
    processSupervisionBlockedReason = runtimePrimitiveContent(supervisionPayload, "blockedReason"),
    processSupervisionBootstrapOnly = runtimePrimitiveBoolean(supervisionPayload, "bootstrapOnly") == true,
    processObservationReady = processObservationReady,
    processObservationStatePresent = processObservationFile.isFile,
    processObservationStatus = observationStatus,
    processObservationState = observationState,
    processObservationStatePath = desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessObservationStateFile),
    processObservationGeneration = runtimePrimitiveInt(observationPayload, "generation") ?: 0,
    processObservationObservedAt = runtimePrimitiveContent(observationPayload, "observedAt"),
    processObservationHeartbeatAgeSeconds = runtimePrimitiveInt(observationPayload, "heartbeatAgeSeconds") ?: 0,
    processObservationLeaseRemainingSeconds = runtimePrimitiveInt(observationPayload, "leaseRemainingSeconds") ?: 0,
    processObservationLeaseHealth = runtimePrimitiveContent(observationPayload, "leaseHealth"),
    processObservationRecoveryHint = runtimePrimitiveContent(observationPayload, "recoveryHint"),
    processObservationBootstrapOnly = runtimePrimitiveBoolean(observationPayload, "bootstrapOnly") == true,
    processRecoveryReady = processRecoveryReady,
    processRecoveryStatePresent = processRecoveryFile.isFile,
    processRecoveryStatus = recoveryStatus,
    processRecoveryState = recoveryState,
    processRecoveryStatePath = desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessRecoveryStateFile),
    processRecoveryGeneration = runtimePrimitiveInt(recoveryPayload, "generation") ?: 0,
    processRecoveryActionCount = runtimePrimitiveInt(recoveryPayload, "actionCount") ?: 0,
    processRecoveryPrimaryAction = runtimePrimitiveContent(recoveryPayload, "primaryAction"),
    processRecoveryReason = runtimePrimitiveContent(recoveryPayload, "recoveryReason"),
    processRecoveryBootstrapOnly = runtimePrimitiveBoolean(recoveryPayload, "bootstrapOnly") == true,
    processDetachedLaunchReady = processDetachedLaunchReady,
    processDetachedLaunchStatePresent = processDetachedLaunchFile.isFile,
    processDetachedLaunchStatus = detachedLaunchStatus,
    processDetachedLaunchState = detachedLaunchState,
    processDetachedLaunchStatePath = desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessDetachedLaunchStateFile),
    processDetachedLaunchGeneration = runtimePrimitiveInt(detachedLaunchPayload, "generation") ?: 0,
    processDetachedLaunchSessionId = runtimePrimitiveContent(detachedLaunchPayload, "launchSessionId"),
    processDetachedLaunchCommand = runtimePrimitiveContent(detachedLaunchPayload, "launchCommand"),
    processDetachedLaunchBlockedReason = runtimePrimitiveContent(detachedLaunchPayload, "blockedReason"),
    processDetachedLaunchBootstrapOnly = runtimePrimitiveBoolean(detachedLaunchPayload, "bootstrapOnly") == true,
    longLivedProcessReady =
      runtimePrimitiveBoolean(detachedLaunchPayload, "longLivedProcessReady") == true ||
      runtimePrimitiveBoolean(observationPayload, "longLivedProcessReady") == true ||
        runtimePrimitiveBoolean(supervisionPayload, "longLivedProcessReady") == true,
    profileId = runtimePrimitiveContent(statePayload, "profileId"),
    environmentId = runtimePrimitiveContent(statePayload, "environmentId"),
    supervisorId = runtimePrimitiveContent(statePayload, "supervisorId"),
    restartSupported = runtimePrimitiveBoolean(statePayload, "restartSupported") == true,
    healthReportSupported = runtimePrimitiveBoolean(statePayload, "healthReportSupported") == true,
    dependencyCount = dependencyStatus?.size ?: 0,
    missingDependencyCount = missingDependencies?.size ?: 0,
    stateFilePath = desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProfileReplayStateFile),
    resultFilePath = runtimeHomeDisplayPath(manifestVersion, embeddedRuntimeDesktopProfileReplayWorkFile),
  )
}

private data class PackagedDesktopToolExecution(
  val toolId: String,
  val resultRelativePath: String,
  val descriptor: JsonObject,
  val result: JsonObject,
  val toolkitStageInstalled: Boolean,
  val toolkitStageManifestPresent: Boolean,
  val toolkitCommandPolicyPresent: Boolean,
  val packagedToolDescriptorPresent: Boolean,
)

private data class EmbeddedRuntimePluginProfileSelection(
  val payload: JsonObject,
  val source: String,
)

private data class PackagedPluginExecution(
  val pluginId: String,
  val resultRelativePath: String,
  val descriptor: JsonObject,
  val result: JsonObject,
  val desktopPluginsManifestPresent: Boolean,
  val packagedPluginDescriptorPresent: Boolean,
)

private fun executePackagedDesktopTool(
  versionDir: File,
  runtimeHome: File,
  task: EmbeddedRuntimeTaskDefinition,
  packagedWorkspaceFile: File,
): PackagedDesktopToolExecution? {
  val toolkitStageRoot = versionDir.resolve(embeddedToolkitStageDirectoryName)
  if (!toolkitStageRoot.isDirectory) return null
  val toolkitStageManifest = toolkitStageRoot.resolve("manifest.json").takeIf { it.isFile }?.let(::loadToolkitStageManifest) ?: return null
  val commandPolicyFile =
    toolkitStageManifest.commandPolicyPath
      ?.let { resolveRelativePath(toolkitStageRoot, it) }
      ?.takeIf { it.isFile }
      ?: toolkitStageRoot.resolve("command-policy.json").takeIf { it.isFile }
      ?: return null
  val commandPolicy = loadRuntimeToolCommandPolicy(commandPolicyFile) ?: return null
  val toolId = task.toolId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  if (commandPolicy.allowedTaskKinds.isNotEmpty() && task.kind !in commandPolicy.allowedTaskKinds) return null
  if (commandPolicy.allowedToolIds.isNotEmpty() && toolId !in commandPolicy.allowedToolIds) return null
  val toolDescriptorFile =
    task.packagedToolDescriptorPath?.let { resolveRelativePath(versionDir, it) }?.takeIf { it.isFile } ?: return null
  val toolDescriptor = loadRuntimeToolDescriptor(toolDescriptorFile) ?: return null
  if (toolDescriptor.toolId != toolId) return null
  if (toolDescriptor.runtimeTaskIds.isNotEmpty() && task.taskId !in toolDescriptor.runtimeTaskIds) return null

  val supportedExtensions = toolDescriptor.supportedFileExtensions.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
  if (supportedExtensions.isNotEmpty() && packagedWorkspaceFile.extension.lowercase() !in supportedExtensions) return null

  val inputText = runCatching { packagedWorkspaceFile.readText(Charsets.UTF_8) }.getOrNull() ?: return null
  val inputLines = inputText.lines()
  val nonEmptyLines = inputLines.map { it.trim() }.filter { it.isNotEmpty() }
  val headings = inputLines.map { it.trimStart() }.filter { it.startsWith("#") }.map(::extractHeadingText).filter { it.isNotEmpty() }
  val bulletCount = inputLines.count { it.trimStart().startsWith("- ") || it.trimStart().startsWith("* ") }
  val checklistCount = inputLines.count { it.trimStart().startsWith("- [") || it.trimStart().startsWith("* [") }
  val preview = nonEmptyLines.take(2).joinToString(" ").take(240)
  val resultRelativePath = task.resultFile?.trim()?.takeIf { it.isNotEmpty() } ?: "work/${task.taskId}-result.json"
  val resultFile = resolveRelativePath(runtimeHome, resultRelativePath) ?: return null
  resultFile.parentFile?.mkdirs()

  val resultPayload =
    buildJsonObject {
      put("toolId", JsonPrimitive(toolDescriptor.toolId))
      put("toolDisplayName", JsonPrimitive(toolDescriptor.displayName ?: toolDescriptor.toolId))
      put("toolKind", JsonPrimitive(toolDescriptor.kind))
      put("taskId", JsonPrimitive(task.taskId))
      put("inputKind", JsonPrimitive(toolDescriptor.inputKind ?: "text"))
      put("inputPath", JsonPrimitive(task.packagedWorkspacePath ?: ""))
      put("inputFileName", JsonPrimitive(packagedWorkspaceFile.name))
      put("inputSizeBytes", JsonPrimitive(packagedWorkspaceFile.length()))
      put("inputSha256", JsonPrimitive(sha256(packagedWorkspaceFile)))
      put("lineCount", JsonPrimitive(inputLines.size))
      put("nonEmptyLineCount", JsonPrimitive(nonEmptyLines.size))
      put("headingCount", JsonPrimitive(headings.size))
      put("bulletCount", JsonPrimitive(bulletCount))
      put("checklistCount", JsonPrimitive(checklistCount))
      put("browserRequired", JsonPrimitive(false))
      put(
        "headings",
        buildJsonArray {
          headings.forEach { add(JsonPrimitive(it)) }
        },
      )
      put(
        "keywordHits",
        buildJsonObject {
          listOf("browser", "shell", "plugin", "offline", "workspace").forEach { keyword ->
            put(keyword, JsonPrimitive(countKeywordHits(inputText, keyword)))
          }
        },
      )
      put("preview", JsonPrimitive(preview))
    }
  resultFile.writeText("${resultPayload}\n", Charsets.UTF_8)

  val descriptorPayload =
    buildJsonObject {
      put("toolId", JsonPrimitive(toolDescriptor.toolId))
      put("displayName", JsonPrimitive(toolDescriptor.displayName ?: toolDescriptor.toolId))
      put("kind", JsonPrimitive(toolDescriptor.kind))
      put("summary", JsonPrimitive(toolDescriptor.summary ?: ""))
      put(
        "runtimeTaskIds",
        buildJsonArray {
          toolDescriptor.runtimeTaskIds.forEach { add(JsonPrimitive(it)) }
        },
      )
      put(
        "supportedFileExtensions",
        buildJsonArray {
          toolDescriptor.supportedFileExtensions.forEach { add(JsonPrimitive(it)) }
        },
      )
    }

  return PackagedDesktopToolExecution(
    toolId = toolId,
    resultRelativePath = resultRelativePath,
    descriptor = descriptorPayload,
    result = resultPayload,
    toolkitStageInstalled = true,
    toolkitStageManifestPresent = true,
    toolkitCommandPolicyPresent = true,
    packagedToolDescriptorPresent = true,
  )
}

private fun executeAllowlistedPluginTask(
  context: Context,
  manifestVersion: String,
  versionDir: File,
  runtimeHome: File,
  task: EmbeddedRuntimeTaskDefinition,
  packagedWorkspaceFile: File,
): PackagedPluginExecution? {
  val desktopStageRoot = versionDir.resolve(embeddedRuntimeDesktopStageDirectoryName)
  val pluginsManifestFile = desktopStageRoot.resolve("plugins/manifest.json").takeIf { it.isFile } ?: return null
  val pluginsManifest = readRuntimeJsonObjectOrNull(pluginsManifestFile) ?: return null
  val pluginId = task.pluginId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  val allowlistedPluginIds =
    runtimeJsonArray(pluginsManifest, "pluginIds").map { it.content.trim() }.filter { it.isNotEmpty() }
  if (pluginId !in allowlistedPluginIds) return null

  val pluginDescriptorFile =
    task.packagedPluginDescriptorPath?.let { resolveRelativePath(versionDir, it) }?.takeIf { it.isFile } ?: return null
  val pluginDescriptor = readRuntimeJsonObjectOrNull(pluginDescriptorFile) ?: return null
  if (runtimePrimitiveContent(pluginDescriptor, "pluginId") != pluginId) return null
  val runtimeTaskIds = runtimeJsonArray(pluginDescriptor, "runtimeTaskIds").map { it.content.trim() }.filter { it.isNotEmpty() }
  if (runtimeTaskIds.isNotEmpty() && task.taskId !in runtimeTaskIds) return null

  val selectedProfile = selectAllowlistedPluginProfile(context, manifestVersion, versionDir) ?: return null
  val pluginSetId = runtimePrimitiveContent(selectedProfile.payload, "pluginSetId") ?: return null
  val componentId = runtimePrimitiveContent(pluginsManifest, "componentId") ?: return null
  if (pluginSetId != componentId) return null

  val inputText = runCatching { packagedWorkspaceFile.readText(Charsets.UTF_8) }.getOrNull() ?: return null
  val inputLines = inputText.lines()
  val nonEmptyLines = inputLines.map { it.trim() }.filter { it.isNotEmpty() }
  val preview = nonEmptyLines.take(2).joinToString(" ").take(240)
  val browserInspection = inspectEmbeddedRuntimeBrowserLane(context, manifestVersion)
  val desktopInspection = inspectEmbeddedRuntimeDesktopEnvironment(context, manifestVersion)
  val resultRelativePath = task.resultFile?.trim()?.takeIf { it.isNotEmpty() } ?: "work/${task.taskId}-result.json"
  val resultFile = resolveRelativePath(runtimeHome, resultRelativePath) ?: return null
  resultFile.parentFile?.mkdirs()

  val descriptorPayload =
    buildJsonObject {
      put("pluginId", JsonPrimitive(pluginId))
      put("displayName", JsonPrimitive(runtimePrimitiveContent(pluginDescriptor, "displayName") ?: pluginId))
      put("kind", JsonPrimitive(runtimePrimitiveContent(pluginDescriptor, "kind") ?: "allowlisted-plugin"))
      put("summary", JsonPrimitive(runtimePrimitiveContent(pluginDescriptor, "summary") ?: ""))
      put(
        "runtimeTaskIds",
        buildJsonArray {
          runtimeJsonArray(pluginDescriptor, "runtimeTaskIds").forEach { add(it) }
        },
      )
      put(
        "allowedCommands",
        buildJsonArray {
          runtimeJsonArray(pluginDescriptor, "allowedCommands").forEach { add(it) }
        },
      )
      put(
        "capabilities",
        buildJsonArray {
          runtimeJsonArray(pluginDescriptor, "capabilities").forEach { add(it) }
        },
      )
      put(
        "limitations",
        buildJsonArray {
          runtimeJsonArray(pluginDescriptor, "limitations").forEach { add(it) }
        },
      )
    }
  val resultPayload =
    buildJsonObject {
      put("pluginId", JsonPrimitive(pluginId))
      put("pluginDisplayName", JsonPrimitive(runtimePrimitiveContent(pluginDescriptor, "displayName") ?: pluginId))
      put("pluginKind", JsonPrimitive(runtimePrimitiveContent(pluginDescriptor, "kind") ?: "allowlisted-plugin"))
      put("taskId", JsonPrimitive(task.taskId))
      put("allowlisted", JsonPrimitive(true))
      put("pluginManifestComponentId", JsonPrimitive(componentId))
      put("pluginCount", JsonPrimitive(allowlistedPluginIds.size))
      put("profileId", JsonPrimitive(runtimePrimitiveContent(selectedProfile.payload, "profileId") ?: "unknown"))
      put("profileSource", JsonPrimitive(selectedProfile.source))
      put("pluginSetId", JsonPrimitive(pluginSetId))
      put("desktopHomeReady", JsonPrimitive(desktopInspection.desktopHomeReady))
      put("browserReplayReady", JsonPrimitive(browserInspection.browserReplayReady))
      put("authCredentialPresent", JsonPrimitive(browserInspection.authCredentialPresent))
      put("inputPath", JsonPrimitive(task.packagedWorkspacePath ?: ""))
      put("inputFileName", JsonPrimitive(packagedWorkspaceFile.name))
      put("inputSizeBytes", JsonPrimitive(packagedWorkspaceFile.length()))
      put("inputSha256", JsonPrimitive(sha256(packagedWorkspaceFile)))
      put("lineCount", JsonPrimitive(inputLines.size))
      put("nonEmptyLineCount", JsonPrimitive(nonEmptyLines.size))
      put("preview", JsonPrimitive(preview))
      put(
        "capabilities",
        buildJsonArray {
          runtimeJsonArray(pluginDescriptor, "capabilities").forEach { add(it) }
        },
      )
      put(
        "allowedCommands",
        buildJsonArray {
          runtimeJsonArray(pluginDescriptor, "allowedCommands").forEach { add(it) }
        },
      )
      put(
        "limitations",
        buildJsonArray {
          runtimeJsonArray(pluginDescriptor, "limitations").forEach { add(it) }
        },
      )
      put(
        "keywordHits",
        buildJsonObject {
          listOf("plugin", "browser", "workspace", "runtime", "supervisor").forEach { keyword ->
            put(keyword, JsonPrimitive(countKeywordHits(inputText, keyword)))
          }
        },
      )
    }
  resultFile.writeText("${resultPayload}\n", Charsets.UTF_8)

  return PackagedPluginExecution(
    pluginId = pluginId,
    resultRelativePath = resultRelativePath,
    descriptor = descriptorPayload,
    result = resultPayload,
    desktopPluginsManifestPresent = true,
    packagedPluginDescriptorPresent = true,
  )
}

private fun selectAllowlistedPluginProfile(
  context: Context,
  manifestVersion: String,
  versionDir: File,
): EmbeddedRuntimePluginProfileSelection? {
  val desktopHome = embeddedDesktopHomeRootForRuntime(context).resolve(manifestVersion)
  val activeProfile =
    desktopHome.resolve("profiles/active-profile.json").takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
  if (activeProfile != null && !runtimePrimitiveContent(activeProfile, "pluginSetId").isNullOrBlank()) {
    return EmbeddedRuntimePluginProfileSelection(
      payload = activeProfile,
      source = "materialized_active_profile",
    )
  }

  val packagedProfile =
    versionDir.resolve("desktop/profiles/$embeddedRuntimeDefaultDesktopProfileId.json").takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
      ?: versionDir.resolve("desktop/profiles/openclaw-desktop-host.json").takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
      ?: return null
  return EmbeddedRuntimePluginProfileSelection(
    payload = packagedProfile,
    source = "packaged_profile_descriptor",
  )
}

private data class EmbeddedRuntimeDesktopProfileReplayExecution(
  val payload: JsonObject,
  val stateFilePath: String,
  val resultFilePath: String,
  val environmentSupervisionReady: Boolean,
  val healthReportPath: String,
  val restartContractPath: String,
  val processModelReady: Boolean,
  val processStatePath: String,
  val processStatus: String,
  val processSessionId: String,
  val processActivationReady: Boolean,
  val processActivationStatePath: String,
  val processActivationStatus: String,
  val processActivationState: String,
  val processActivationGeneration: Int,
  val processActivationBlockedReason: String?,
  val processSupervisionReady: Boolean,
  val processSupervisionStatePath: String,
  val processSupervisionStatus: String,
  val processSupervisionState: String,
  val processSupervisionGeneration: Int,
  val processSupervisionHeartbeatAt: String,
  val processSupervisionLeaseExpiresAt: String,
  val processSupervisionBlockedReason: String?,
  val processObservationReady: Boolean,
  val processObservationStatePath: String,
  val processObservationStatus: String,
  val processObservationState: String,
  val processObservationGeneration: Int,
  val processObservationObservedAt: String,
  val processObservationHeartbeatAgeSeconds: Int,
  val processObservationLeaseRemainingSeconds: Int,
  val processObservationLeaseHealth: String,
  val processObservationRecoveryHint: String,
  val processRecoveryReady: Boolean,
  val processRecoveryStatePath: String,
  val processRecoveryStatus: String,
  val processRecoveryState: String,
  val processRecoveryGeneration: Int,
  val processRecoveryActionCount: Int,
  val processRecoveryPrimaryAction: String,
  val processRecoveryReason: String,
  val processDetachedLaunchReady: Boolean,
  val processDetachedLaunchStatePath: String,
  val processDetachedLaunchStatus: String,
  val processDetachedLaunchState: String,
  val processDetachedLaunchGeneration: Int,
  val processDetachedLaunchSessionId: String,
  val processDetachedLaunchCommand: String,
  val processDetachedLaunchBlockedReason: String?,
  val longLivedProcessReady: Boolean,
)

private fun executeEmbeddedRuntimeDesktopProfileReplay(
  context: Context,
  manifestVersion: String,
  runtimeHome: File,
  executionCount: Int,
  executedAt: String,
): EmbeddedRuntimeDesktopProfileReplayExecution? {
  val desktopInspection = inspectEmbeddedRuntimeDesktopEnvironment(context, manifestVersion)
  if (!desktopInspection.desktopHomeReady) return null

  val desktopHome = embeddedDesktopHomeRootForRuntime(context).resolve(manifestVersion)
  val activeProfileFile = desktopHome.resolve("profiles/active-profile.json").takeIf { it.isFile } ?: return null
  val environmentManifestFile = desktopHome.resolve("environment/manifest.json").takeIf { it.isFile } ?: return null
  val supervisorManifestFile = desktopHome.resolve("supervisor/manifest.json").takeIf { it.isFile } ?: return null
  val browserInspection = inspectEmbeddedRuntimeBrowserLane(context, manifestVersion)
  val carrierInspection = inspectEmbeddedRuntimeCarrier(context, manifestVersion)
  val activeProfile = readRuntimeJsonObjectOrNull(activeProfileFile) ?: return null
  val environmentManifest = readRuntimeJsonObjectOrNull(environmentManifestFile) ?: return null
  val supervisorManifest = readRuntimeJsonObjectOrNull(supervisorManifestFile) ?: return null
  val versionDir = embeddedRuntimePodInstallRoot(context).resolve(manifestVersion)
  val workspaceReady =
    versionDir.resolve("workspace/manifest.json").isFile &&
      versionDir.resolve("workspace/content-index.json").isFile
  val dependencyStatus =
    linkedMapOf(
      "runtime" to carrierInspection.runtimeHomeReady,
      "browser" to browserInspection.browserReplayReady,
      "toolkit" to (
        carrierInspection.toolkitStageInstalled &&
          carrierInspection.toolkitStageManifestPresent &&
          carrierInspection.toolkitCommandPolicyPresent
      ),
      "workspace" to workspaceReady,
    )
  val missingDependencies = dependencyStatus.filterValues { !it }.keys.toList()
  val restartSupported =
    runtimeJsonArray(supervisorManifest, "managedActions").any { it.content == "restart" } ||
      runtimeJsonArray(environmentManifest, "capabilities").any { it.content == "restart-contract" }
  val healthReportSupported = runtimeJsonArray(supervisorManifest, "managedActions").any { it.content == "health-report" }
  val healthReportStatus = if (missingDependencies.isEmpty()) "healthy" else "degraded"
  val healthReportFile = desktopHome.resolve(embeddedRuntimeDesktopHealthReportStateFile)
  healthReportFile.parentFile?.mkdirs()
  val healthReportPayload =
    buildJsonObject {
      put("status", JsonPrimitive(healthReportStatus))
      put("taskId", JsonPrimitive(embeddedRuntimeDefaultTaskId))
      put("profileId", JsonPrimitive(runtimePrimitiveContent(activeProfile, "profileId") ?: "unknown"))
      runtimePrimitiveContent(activeProfile, "environmentId")?.let { put("environmentId", JsonPrimitive(it)) }
      runtimePrimitiveContent(activeProfile, "supervisorId")?.let { put("supervisorId", JsonPrimitive(it)) }
      put("healthReportSupported", JsonPrimitive(healthReportSupported))
      put("runtimeHomeReady", JsonPrimitive(carrierInspection.runtimeHomeReady))
      put("browserReplayReady", JsonPrimitive(browserInspection.browserReplayReady))
      put("reportedAt", JsonPrimitive(executedAt))
      put("executionCount", JsonPrimitive(executionCount))
      put(
        "dependencyStatus",
        buildJsonObject {
          dependencyStatus.forEach { (dependency, ready) ->
            put(dependency, JsonPrimitive(ready))
          }
        },
      )
      put(
        "missingDependencies",
        buildJsonArray {
          missingDependencies.forEach { dependency ->
            add(JsonPrimitive(dependency))
          }
        },
      )
      put("runtimeStatePath", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion, "state/runtime-smoke.json")))
      put("desktopProfileReplayPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProfileReplayStateFile)))
    }
  healthReportFile.writeText("${healthReportPayload}\n", Charsets.UTF_8)
  val restartContractFile = desktopHome.resolve(embeddedRuntimeDesktopRestartContractStateFile)
  val previousRestartContract = restartContractFile.takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
  val previousGeneration = runtimePrimitiveInt(previousRestartContract, "generation") ?: 0
  val restartGeneration = previousGeneration + 1
  val restartStatus = if (restartSupported) "ready" else "unsupported"
  restartContractFile.parentFile?.mkdirs()
  val restartContractPayload =
    buildJsonObject {
      put("status", JsonPrimitive(restartStatus))
      put("taskId", JsonPrimitive(embeddedRuntimeDefaultTaskId))
      put("profileId", JsonPrimitive(runtimePrimitiveContent(activeProfile, "profileId") ?: "unknown"))
      runtimePrimitiveContent(activeProfile, "environmentId")?.let { put("environmentId", JsonPrimitive(it)) }
      runtimePrimitiveContent(activeProfile, "supervisorId")?.let { put("supervisorId", JsonPrimitive(it)) }
      put("restartSupported", JsonPrimitive(restartSupported))
      put("healthReportSupported", JsonPrimitive(healthReportSupported))
      put("generation", JsonPrimitive(restartGeneration))
      put("previousGeneration", JsonPrimitive(previousGeneration))
      put("restartObserved", JsonPrimitive(previousGeneration > 0))
      put("restartTaskId", JsonPrimitive(embeddedRuntimeDefaultTaskId))
      put("restartCommand", JsonPrimitive("pod.runtime.execute"))
      put("podVersion", JsonPrimitive(manifestVersion))
      put("replayedAt", JsonPrimitive(executedAt))
      put("executionCount", JsonPrimitive(executionCount))
      put("desktopHomePath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion)))
    }
  restartContractFile.writeText("${restartContractPayload}\n", Charsets.UTF_8)
  val environmentSupervisionReady = healthReportFile.isFile && restartContractFile.isFile
  val processModelFile = desktopHome.resolve(embeddedRuntimeDesktopProcessModelStateFile)
  processModelFile.parentFile?.mkdirs()
  val profileId = runtimePrimitiveContent(activeProfile, "profileId") ?: "unknown"
  val processSessionId = "$profileId-runtime-smoke-$executionCount"
  val processStatus = if (missingDependencies.isEmpty()) "standby" else "blocked"
  val activationSupported =
    runtimeJsonArray(supervisorManifest, "managedActions").any { it.content == "activate-process" } ||
      runtimeJsonArray(supervisorManifest, "capabilities").any {
        it.content == "process-runtime-activation-bootstrap"
      } ||
      runtimeJsonArray(environmentManifest, "capabilities").any {
        it.content == "process-runtime-activation-bootstrap"
      }
  val processActivationFile = desktopHome.resolve(embeddedRuntimeDesktopProcessActivationStateFile)
  processActivationFile.parentFile?.mkdirs()
  val previousActivationPayload = processActivationFile.takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
  val previousActivationGeneration = runtimePrimitiveInt(previousActivationPayload, "generation") ?: 0
  val processActivationGeneration = previousActivationGeneration + 1
  val supervisionSupported =
    runtimeJsonArray(supervisorManifest, "managedActions").any { it.content == "supervise-process" } ||
      runtimeJsonArray(supervisorManifest, "capabilities").any {
        it.content == "process-runtime-supervision-bootstrap"
      } ||
      runtimeJsonArray(environmentManifest, "capabilities").any {
        it.content == "process-runtime-supervision-bootstrap"
      }
  val processActivationStatus =
    when {
      !activationSupported -> "unsupported"
      missingDependencies.isNotEmpty() -> "blocked"
      else -> "ready"
    }
  val processActivationState =
    when (processActivationStatus) {
      "ready" -> "pending_supervisor"
      "blocked" -> "blocked"
      else -> "unsupported"
    }
  val processActivationBlockedReason =
    when {
      !activationSupported -> "activation_not_supported"
      missingDependencies.isNotEmpty() -> "missing_dependencies:${missingDependencies.joinToString("|")}"
      else -> null
    }
  val processSupervisionFile = desktopHome.resolve(embeddedRuntimeDesktopProcessSupervisionStateFile)
  processSupervisionFile.parentFile?.mkdirs()
  val previousSupervisionPayload = processSupervisionFile.takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
  val previousSupervisionGeneration = runtimePrimitiveInt(previousSupervisionPayload, "generation") ?: 0
  val processSupervisionGeneration = previousSupervisionGeneration + 1
  val processSupervisionStatus =
    when {
      !supervisionSupported -> "unsupported"
      processActivationStatus != "ready" -> "blocked"
      else -> "active"
    }
  val processSupervisionState =
    when (processSupervisionStatus) {
      "active" -> "lease_active"
      "blocked" -> "blocked"
      else -> "unsupported"
    }
  val processSupervisionBlockedReason =
    when {
      !supervisionSupported -> "supervision_not_supported"
      processActivationBlockedReason != null -> processActivationBlockedReason
      processActivationStatus != "ready" -> "activation_not_ready:$processActivationStatus"
      else -> null
    }
  val observationSupported =
    runtimeJsonArray(supervisorManifest, "managedActions").any { it.content == "observe-process" } ||
      runtimeJsonArray(supervisorManifest, "capabilities").any {
        it.content == "process-runtime-observation-bootstrap"
      } ||
      runtimeJsonArray(environmentManifest, "capabilities").any {
        it.content == "process-runtime-observation-bootstrap"
      }
  val supervisionHeartbeatAt = executedAt
  val supervisionLeaseExpiresAt =
    Instant.parse(executedAt).plusSeconds(300).toString()
  val longLivedProcessReady = processSupervisionStatus == "active"
  val processObservationFile = desktopHome.resolve(embeddedRuntimeDesktopProcessObservationStateFile)
  processObservationFile.parentFile?.mkdirs()
  val previousObservationPayload = processObservationFile.takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
  val previousObservationGeneration = runtimePrimitiveInt(previousObservationPayload, "generation") ?: 0
  val processObservationGeneration = previousObservationGeneration + 1
  val processObservationStatus =
    when {
      !observationSupported -> "unsupported"
      processSupervisionStatus == "active" -> "healthy"
      processSupervisionStatus == "blocked" -> "blocked"
      else -> "unsupported"
    }
  val processObservationState =
    when (processObservationStatus) {
      "healthy" -> "lease_fresh"
      "blocked" -> "awaiting_recovery"
      else -> "unsupported"
    }
  val processObservationHeartbeatAgeSeconds = 0
  val processObservationLeaseRemainingSeconds = if (processSupervisionStatus == "active") 300 else 0
  val processObservationLeaseHealth =
    when (processObservationStatus) {
      "healthy" -> "fresh"
      "blocked" -> "blocked"
      else -> "unsupported"
    }
  val processObservationRecoveryHint =
    when {
      !observationSupported -> "observation_not_supported"
      missingDependencies.isNotEmpty() -> "restore_dependencies:${missingDependencies.joinToString("|")}"
      processSupervisionBlockedReason != null -> "clear_blocker:$processSupervisionBlockedReason"
      processSupervisionStatus == "active" -> "keep_lease_fresh"
      else -> "rerun_runtime_smoke"
    }
  val recoverySupported =
    runtimeJsonArray(supervisorManifest, "managedActions").any { it.content == "recover-process" } ||
      runtimeJsonArray(supervisorManifest, "capabilities").any {
        it.content == "process-runtime-recovery-bootstrap"
      } ||
      runtimeJsonArray(environmentManifest, "capabilities").any {
        it.content == "process-runtime-recovery-bootstrap"
      }
  val processRecoveryFile = desktopHome.resolve(embeddedRuntimeDesktopProcessRecoveryStateFile)
  processRecoveryFile.parentFile?.mkdirs()
  val previousRecoveryPayload = processRecoveryFile.takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
  val previousRecoveryGeneration = runtimePrimitiveInt(previousRecoveryPayload, "generation") ?: 0
  val processRecoveryGeneration = previousRecoveryGeneration + 1
  val processRecoveryStatus =
    when {
      !recoverySupported -> "unsupported"
      processObservationStatus == "healthy" -> "ready"
      else -> "planned"
    }
  val processRecoveryState =
    when {
      !recoverySupported -> "unsupported"
      missingDependencies.isNotEmpty() -> "restore_dependencies"
      processSupervisionBlockedReason != null -> "clear_blocker"
      processObservationStatus == "healthy" -> "monitoring"
      else -> "rerun_required"
    }
  val processRecoveryReason =
    when {
      !recoverySupported -> "recovery_not_supported"
      missingDependencies.isNotEmpty() -> "missing_dependencies:${missingDependencies.joinToString("|")}"
      processSupervisionBlockedReason != null -> processSupervisionBlockedReason
      processObservationStatus == "healthy" -> "lease_fresh"
      else -> processObservationRecoveryHint
    }
  val processRecoveryActions =
    buildList<JsonObject> {
      when {
        !recoverySupported -> Unit
        missingDependencies.isNotEmpty() -> {
          missingDependencies.forEach { dependency ->
            add(
              buildJsonObject {
                put("actionId", JsonPrimitive("restore_dependency:$dependency"))
                put("kind", JsonPrimitive("restore_dependency"))
                put("target", JsonPrimitive(dependency))
                put("reason", JsonPrimitive("dependency_missing:$dependency"))
                put("command", JsonPrimitive("pod.runtime.execute"))
                put("taskId", JsonPrimitive(embeddedRuntimeDefaultTaskId))
                put("blocking", JsonPrimitive(true))
              },
            )
          }
          add(
            buildJsonObject {
              put("actionId", JsonPrimitive("rerun_runtime_smoke"))
              put("kind", JsonPrimitive("rerun_runtime_smoke"))
              put("reason", JsonPrimitive("refresh_runtime_recovery_contract"))
              put("command", JsonPrimitive("pod.runtime.execute"))
              put("taskId", JsonPrimitive(embeddedRuntimeDefaultTaskId))
              put("blocking", JsonPrimitive(false))
            },
          )
        }
        processSupervisionBlockedReason != null -> {
          add(
            buildJsonObject {
              put("actionId", JsonPrimitive("clear_blocker"))
              put("kind", JsonPrimitive("clear_blocker"))
              put("reason", JsonPrimitive(processSupervisionBlockedReason))
              put("command", JsonPrimitive("pod.runtime.execute"))
              put("taskId", JsonPrimitive(embeddedRuntimeDefaultTaskId))
              put("blocking", JsonPrimitive(true))
            },
          )
          add(
            buildJsonObject {
              put("actionId", JsonPrimitive("rerun_runtime_smoke"))
              put("kind", JsonPrimitive("rerun_runtime_smoke"))
              put("reason", JsonPrimitive("refresh_runtime_recovery_contract"))
              put("command", JsonPrimitive("pod.runtime.execute"))
              put("taskId", JsonPrimitive(embeddedRuntimeDefaultTaskId))
              put("blocking", JsonPrimitive(false))
            },
          )
        }
        processObservationStatus == "healthy" -> {
          add(
            buildJsonObject {
              put("actionId", JsonPrimitive("keep_lease_fresh"))
              put("kind", JsonPrimitive("keep_lease_fresh"))
              put("reason", JsonPrimitive("observation_healthy"))
              put("command", JsonPrimitive("pod.runtime.execute"))
              put("taskId", JsonPrimitive(embeddedRuntimeDefaultTaskId))
              put("leaseCadenceSeconds", JsonPrimitive(300))
              put("blocking", JsonPrimitive(false))
            },
          )
        }
        else -> {
          add(
            buildJsonObject {
              put("actionId", JsonPrimitive("rerun_runtime_smoke"))
              put("kind", JsonPrimitive("rerun_runtime_smoke"))
              put("reason", JsonPrimitive(processObservationRecoveryHint))
              put("command", JsonPrimitive("pod.runtime.execute"))
              put("taskId", JsonPrimitive(embeddedRuntimeDefaultTaskId))
              put("blocking", JsonPrimitive(false))
            },
          )
        }
      }
    }
  val processRecoveryPrimaryAction =
    (processRecoveryActions.firstOrNull()?.get("actionId") as? JsonPrimitive)?.content ?: "none"
  val processModelPayload =
    buildJsonObject {
      put("status", JsonPrimitive(processStatus))
      put("taskId", JsonPrimitive(embeddedRuntimeDefaultTaskId))
      put("profileId", JsonPrimitive(profileId))
      runtimePrimitiveContent(activeProfile, "environmentId")?.let { put("environmentId", JsonPrimitive(it)) }
      runtimePrimitiveContent(activeProfile, "supervisorId")?.let { put("supervisorId", JsonPrimitive(it)) }
      put("sessionId", JsonPrimitive(processSessionId))
      put("bootstrapOnly", JsonPrimitive(true))
      put("longLivedProcessReady", JsonPrimitive(longLivedProcessReady))
      put("desiredState", JsonPrimitive("running"))
      put("observedState", JsonPrimitive(processStatus))
      put("activationState", JsonPrimitive(processActivationState))
      put("supervisionState", JsonPrimitive(processSupervisionState))
      put("entryCommand", JsonPrimitive("pod.runtime.execute"))
      put("entryTaskId", JsonPrimitive(embeddedRuntimeDefaultTaskId))
      put("runtimeHomeReady", JsonPrimitive(carrierInspection.runtimeHomeReady))
      put("browserReplayReady", JsonPrimitive(browserInspection.browserReplayReady))
      put("restartSupported", JsonPrimitive(restartSupported))
      put("healthReportSupported", JsonPrimitive(healthReportSupported))
      put("healthReportPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopHealthReportStateFile)))
      put("restartContractPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopRestartContractStateFile)))
      put("runtimeStatePath", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion, "state/runtime-smoke.json")))
      put("desktopProfileReplayPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProfileReplayStateFile)))
      put("runtimeHomePath", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion)))
      put("desktopHomePath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion)))
      put("observedAt", JsonPrimitive(executedAt))
      put("executionCount", JsonPrimitive(executionCount))
      put(
        "dependencyStatus",
        buildJsonObject {
          dependencyStatus.forEach { (dependency, ready) ->
            put(dependency, JsonPrimitive(ready))
          }
        },
      )
      put(
        "missingDependencies",
        buildJsonArray {
          missingDependencies.forEach { dependency ->
            add(JsonPrimitive(dependency))
          }
        },
      )
    }
  processModelFile.writeText("${processModelPayload}\n", Charsets.UTF_8)
  val processModelReady = processModelFile.isFile
  val processActivationPayload =
    buildJsonObject {
      put("status", JsonPrimitive(processActivationStatus))
      put("activationState", JsonPrimitive(processActivationState))
      put("taskId", JsonPrimitive(embeddedRuntimeDefaultTaskId))
      put("profileId", JsonPrimitive(profileId))
      runtimePrimitiveContent(activeProfile, "environmentId")?.let { put("environmentId", JsonPrimitive(it)) }
      runtimePrimitiveContent(activeProfile, "supervisorId")?.let { put("supervisorId", JsonPrimitive(it)) }
      put("sessionId", JsonPrimitive(processSessionId))
      put("generation", JsonPrimitive(processActivationGeneration))
      put("previousGeneration", JsonPrimitive(previousActivationGeneration))
      put("activationSupported", JsonPrimitive(activationSupported))
      put("activationRequested", JsonPrimitive(processActivationStatus == "ready"))
      put("bootstrapOnly", JsonPrimitive(true))
      put("longLivedProcessReady", JsonPrimitive(longLivedProcessReady))
      put("desiredProcessState", JsonPrimitive("running"))
      put("observedProcessStatus", JsonPrimitive(processStatus))
      put("supervisionState", JsonPrimitive(processSupervisionState))
      put("entryCommand", JsonPrimitive("pod.runtime.execute"))
      put("entryTaskId", JsonPrimitive(embeddedRuntimeDefaultTaskId))
      put("supervisorAction", JsonPrimitive("activate-process"))
      put("supervisorCommand", JsonPrimitive("openclaw-desktop-runtime-supervisor"))
      put("processModelPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessModelStateFile)))
      put("healthReportPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopHealthReportStateFile)))
      put("restartContractPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopRestartContractStateFile)))
      put("runtimeStatePath", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion, "state/runtime-smoke.json")))
      put("runtimeHomePath", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion)))
      put("desktopHomePath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion)))
      put("observedAt", JsonPrimitive(executedAt))
      put("executionCount", JsonPrimitive(executionCount))
      processActivationBlockedReason?.let { put("blockedReason", JsonPrimitive(it)) }
      put(
        "dependencyStatus",
        buildJsonObject {
          dependencyStatus.forEach { (dependency, ready) ->
            put(dependency, JsonPrimitive(ready))
          }
        },
      )
      put(
        "missingDependencies",
        buildJsonArray {
          missingDependencies.forEach { dependency ->
            add(JsonPrimitive(dependency))
          }
        },
      )
    }
  processActivationFile.writeText("${processActivationPayload}\n", Charsets.UTF_8)
  val processActivationReady =
    processActivationFile.isFile && activationSupported && processActivationStatus != "unsupported"
  val processSupervisionPayload =
    buildJsonObject {
      put("status", JsonPrimitive(processSupervisionStatus))
      put("supervisionState", JsonPrimitive(processSupervisionState))
      put("taskId", JsonPrimitive(embeddedRuntimeDefaultTaskId))
      put("profileId", JsonPrimitive(profileId))
      runtimePrimitiveContent(activeProfile, "environmentId")?.let { put("environmentId", JsonPrimitive(it)) }
      runtimePrimitiveContent(activeProfile, "supervisorId")?.let { put("supervisorId", JsonPrimitive(it)) }
      put("sessionId", JsonPrimitive(processSessionId))
      put("generation", JsonPrimitive(processSupervisionGeneration))
      put("previousGeneration", JsonPrimitive(previousSupervisionGeneration))
      put("heartbeatAt", JsonPrimitive(supervisionHeartbeatAt))
      put("leaseDurationSeconds", JsonPrimitive(300))
      put("leaseExpiresAt", JsonPrimitive(supervisionLeaseExpiresAt))
      put("supervisionSupported", JsonPrimitive(supervisionSupported))
      put("bootstrapOnly", JsonPrimitive(true))
      put("longLivedProcessReady", JsonPrimitive(longLivedProcessReady))
      put("desiredProcessState", JsonPrimitive("running"))
      put("observedProcessStatus", JsonPrimitive(processStatus))
      put("activationState", JsonPrimitive(processActivationState))
      put("activationContractPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessActivationStateFile)))
      put("processModelPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessModelStateFile)))
      put("healthReportPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopHealthReportStateFile)))
      put("restartContractPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopRestartContractStateFile)))
      put("entryCommand", JsonPrimitive("pod.runtime.execute"))
      put("entryTaskId", JsonPrimitive(embeddedRuntimeDefaultTaskId))
      put("supervisorAction", JsonPrimitive("supervise-process"))
      put("supervisorCommand", JsonPrimitive("openclaw-desktop-runtime-supervisor"))
      put("runtimeStatePath", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion, "state/runtime-smoke.json")))
      put("runtimeHomePath", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion)))
      put("desktopHomePath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion)))
      put("observedAt", JsonPrimitive(executedAt))
      put("executionCount", JsonPrimitive(executionCount))
      processSupervisionBlockedReason?.let { put("blockedReason", JsonPrimitive(it)) }
      put(
        "dependencyStatus",
        buildJsonObject {
          dependencyStatus.forEach { (dependency, ready) ->
            put(dependency, JsonPrimitive(ready))
          }
        },
      )
      put(
        "missingDependencies",
        buildJsonArray {
          missingDependencies.forEach { dependency ->
            add(JsonPrimitive(dependency))
          }
        },
      )
    }
  processSupervisionFile.writeText("${processSupervisionPayload}\n", Charsets.UTF_8)
  val processSupervisionReady =
    processSupervisionFile.isFile && supervisionSupported && processSupervisionStatus != "unsupported"
  val processObservationPayload =
    buildJsonObject {
      put("status", JsonPrimitive(processObservationStatus))
      put("observationState", JsonPrimitive(processObservationState))
      put("taskId", JsonPrimitive(embeddedRuntimeDefaultTaskId))
      put("profileId", JsonPrimitive(profileId))
      runtimePrimitiveContent(activeProfile, "environmentId")?.let { put("environmentId", JsonPrimitive(it)) }
      runtimePrimitiveContent(activeProfile, "supervisorId")?.let { put("supervisorId", JsonPrimitive(it)) }
      put("sessionId", JsonPrimitive(processSessionId))
      put("generation", JsonPrimitive(processObservationGeneration))
      put("previousGeneration", JsonPrimitive(previousObservationGeneration))
      put("observedAt", JsonPrimitive(executedAt))
      put("heartbeatAt", JsonPrimitive(supervisionHeartbeatAt))
      put("leaseExpiresAt", JsonPrimitive(supervisionLeaseExpiresAt))
      put("heartbeatAgeSeconds", JsonPrimitive(processObservationHeartbeatAgeSeconds))
      put("leaseRemainingSeconds", JsonPrimitive(processObservationLeaseRemainingSeconds))
      put("leaseHealth", JsonPrimitive(processObservationLeaseHealth))
      put("observationSupported", JsonPrimitive(observationSupported))
      put("bootstrapOnly", JsonPrimitive(true))
      put("longLivedProcessReady", JsonPrimitive(longLivedProcessReady))
      put("desiredProcessState", JsonPrimitive("running"))
      put("observedProcessStatus", JsonPrimitive(processStatus))
      put("activationState", JsonPrimitive(processActivationState))
      put("supervisionState", JsonPrimitive(processSupervisionState))
      put("supervisionContractPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessSupervisionStateFile)))
      put("activationContractPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessActivationStateFile)))
      put("processModelPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessModelStateFile)))
      put("healthReportPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopHealthReportStateFile)))
      put("restartContractPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopRestartContractStateFile)))
      put("entryCommand", JsonPrimitive("pod.runtime.execute"))
      put("entryTaskId", JsonPrimitive(embeddedRuntimeDefaultTaskId))
      put("supervisorAction", JsonPrimitive("observe-process"))
      put("supervisorCommand", JsonPrimitive("openclaw-desktop-runtime-supervisor"))
      put("runtimeStatePath", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion, "state/runtime-smoke.json")))
      put("runtimeHomePath", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion)))
      put("desktopHomePath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion)))
      put("recoveryHint", JsonPrimitive(processObservationRecoveryHint))
      put(
        "dependencyStatus",
        buildJsonObject {
          dependencyStatus.forEach { (dependency, ready) ->
            put(dependency, JsonPrimitive(ready))
          }
        },
      )
      put(
        "missingDependencies",
        buildJsonArray {
          missingDependencies.forEach { dependency ->
            add(JsonPrimitive(dependency))
          }
        },
      )
    }
  processObservationFile.writeText("${processObservationPayload}\n", Charsets.UTF_8)
  val processObservationReady =
    processObservationFile.isFile && observationSupported && processObservationStatus != "unsupported"
  val processRecoveryPayload =
    buildJsonObject {
      put("status", JsonPrimitive(processRecoveryStatus))
      put("recoveryState", JsonPrimitive(processRecoveryState))
      put("taskId", JsonPrimitive(embeddedRuntimeDefaultTaskId))
      put("profileId", JsonPrimitive(profileId))
      runtimePrimitiveContent(activeProfile, "environmentId")?.let { put("environmentId", JsonPrimitive(it)) }
      runtimePrimitiveContent(activeProfile, "supervisorId")?.let { put("supervisorId", JsonPrimitive(it)) }
      put("sessionId", JsonPrimitive(processSessionId))
      put("generation", JsonPrimitive(processRecoveryGeneration))
      put("previousGeneration", JsonPrimitive(previousRecoveryGeneration))
      put("recoverySupported", JsonPrimitive(recoverySupported))
      put("bootstrapOnly", JsonPrimitive(true))
      put("longLivedProcessReady", JsonPrimitive(longLivedProcessReady))
      put("desiredProcessState", JsonPrimitive("running"))
      put("observedProcessStatus", JsonPrimitive(processStatus))
      put("activationState", JsonPrimitive(processActivationState))
      put("supervisionState", JsonPrimitive(processSupervisionState))
      put("observationState", JsonPrimitive(processObservationState))
      put("observationRecoveryHint", JsonPrimitive(processObservationRecoveryHint))
      put("supervisionContractPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessSupervisionStateFile)))
      put("observationContractPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessObservationStateFile)))
      put("activationContractPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessActivationStateFile)))
      put("processModelPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessModelStateFile)))
      put("healthReportPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopHealthReportStateFile)))
      put("restartContractPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopRestartContractStateFile)))
      put("restartGeneration", JsonPrimitive(restartGeneration))
      put("restartSupported", JsonPrimitive(restartSupported))
      put("restartCommand", JsonPrimitive("pod.runtime.execute"))
      put("entryCommand", JsonPrimitive("pod.runtime.execute"))
      put("entryTaskId", JsonPrimitive(embeddedRuntimeDefaultTaskId))
      put("supervisorAction", JsonPrimitive("recover-process"))
      put("supervisorCommand", JsonPrimitive("openclaw-desktop-runtime-supervisor"))
      put("runtimeStatePath", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion, "state/runtime-smoke.json")))
      put("runtimeHomePath", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion)))
      put("desktopHomePath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion)))
      put("observedAt", JsonPrimitive(executedAt))
      put("executionCount", JsonPrimitive(executionCount))
      put("primaryAction", JsonPrimitive(processRecoveryPrimaryAction))
      put("recoveryReason", JsonPrimitive(processRecoveryReason))
      put("actionCount", JsonPrimitive(processRecoveryActions.size))
      put(
        "actions",
        buildJsonArray {
          processRecoveryActions.forEach { action ->
            add(action)
          }
        },
      )
      put(
        "dependencyStatus",
        buildJsonObject {
          dependencyStatus.forEach { (dependency, ready) ->
            put(dependency, JsonPrimitive(ready))
          }
        },
      )
      put(
        "missingDependencies",
        buildJsonArray {
          missingDependencies.forEach { dependency ->
            add(JsonPrimitive(dependency))
          }
        },
      )
    }
  processRecoveryFile.writeText("${processRecoveryPayload}\n", Charsets.UTF_8)
  val processRecoveryReady =
    processRecoveryFile.isFile && recoverySupported && processRecoveryStatus != "unsupported"
  val detachedLaunchSupported =
    runtimeJsonArray(supervisorManifest, "managedActions").any { it.content == "launch-detached-process" } ||
      runtimeJsonArray(supervisorManifest, "capabilities").any {
        it.content == "process-runtime-detached-launch-bootstrap"
      } ||
      runtimeJsonArray(environmentManifest, "capabilities").any {
        it.content == "process-runtime-detached-launch-bootstrap"
      }
  val processDetachedLaunchFile = desktopHome.resolve(embeddedRuntimeDesktopProcessDetachedLaunchStateFile)
  processDetachedLaunchFile.parentFile?.mkdirs()
  val previousDetachedLaunchPayload = processDetachedLaunchFile.takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
  val previousDetachedLaunchGeneration = runtimePrimitiveInt(previousDetachedLaunchPayload, "generation") ?: 0
  val processDetachedLaunchGeneration = previousDetachedLaunchGeneration + 1
  val processDetachedLaunchSessionId = "$processSessionId-detached-launch-$processDetachedLaunchGeneration"
  val processDetachedLaunchStatus =
    when {
      !detachedLaunchSupported -> "unsupported"
      processRecoveryStatus == "ready" && longLivedProcessReady -> "ready"
      else -> "blocked"
    }
  val processDetachedLaunchState =
    when {
      !detachedLaunchSupported -> "unsupported"
      processRecoveryStatus == "ready" && longLivedProcessReady -> "launch_contract_ready"
      else -> "awaiting_recovery"
    }
  val processDetachedLaunchBlockedReason =
    when {
      !detachedLaunchSupported -> "detached_launch_not_supported"
      processRecoveryStatus != "ready" -> "recovery_not_ready:$processRecoveryStatus"
      !longLivedProcessReady -> "long_lived_process_not_ready"
      else -> null
    }
  val processDetachedLaunchCommand =
    "openclaw-desktop-runtime-supervisor --launch-detached --session-id " +
      "$processDetachedLaunchSessionId --runtime-home ${runtimeHomeDisplayPath(manifestVersion)} " +
      "--desktop-home ${desktopHomeDisplayPathForRuntime(manifestVersion)} --task-id $embeddedRuntimeDefaultTaskId"
  val processDetachedLaunchPayload =
    buildJsonObject {
      put("status", JsonPrimitive(processDetachedLaunchStatus))
      put("launchState", JsonPrimitive(processDetachedLaunchState))
      put("taskId", JsonPrimitive(embeddedRuntimeDefaultTaskId))
      put("profileId", JsonPrimitive(profileId))
      runtimePrimitiveContent(activeProfile, "environmentId")?.let { put("environmentId", JsonPrimitive(it)) }
      runtimePrimitiveContent(activeProfile, "supervisorId")?.let { put("supervisorId", JsonPrimitive(it)) }
      put("sessionId", JsonPrimitive(processSessionId))
      put("launchSessionId", JsonPrimitive(processDetachedLaunchSessionId))
      put("generation", JsonPrimitive(processDetachedLaunchGeneration))
      put("previousGeneration", JsonPrimitive(previousDetachedLaunchGeneration))
      put("detachedLaunchSupported", JsonPrimitive(detachedLaunchSupported))
      put("bootstrapOnly", JsonPrimitive(true))
      put("longLivedProcessReady", JsonPrimitive(longLivedProcessReady))
      put("desiredProcessState", JsonPrimitive("running"))
      put("observedProcessStatus", JsonPrimitive(processStatus))
      put("recoveryState", JsonPrimitive(processRecoveryState))
      put("recoveryStatus", JsonPrimitive(processRecoveryStatus))
      put("recoveryContractPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessRecoveryStateFile)))
      put("observationContractPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessObservationStateFile)))
      put("supervisionContractPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessSupervisionStateFile)))
      put("activationContractPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessActivationStateFile)))
      put("processModelPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessModelStateFile)))
      put("healthReportPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopHealthReportStateFile)))
      put("restartContractPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopRestartContractStateFile)))
      put("launchCommand", JsonPrimitive(processDetachedLaunchCommand))
      put("launchLogPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessDetachedLaunchLogFile)))
      put("launchSentinelPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessDetachedLaunchSentinelFile)))
      put("entryCommand", JsonPrimitive("pod.runtime.execute"))
      put("entryTaskId", JsonPrimitive(embeddedRuntimeDefaultTaskId))
      put("supervisorAction", JsonPrimitive("launch-detached-process"))
      put("supervisorCommand", JsonPrimitive("openclaw-desktop-runtime-supervisor"))
      put("runtimeStatePath", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion, "state/runtime-smoke.json")))
      put("runtimeHomePath", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion)))
      put("desktopHomePath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion)))
      put("observedAt", JsonPrimitive(executedAt))
      put("executionCount", JsonPrimitive(executionCount))
      processDetachedLaunchBlockedReason?.let { put("blockedReason", JsonPrimitive(it)) }
      put(
        "dependencyStatus",
        buildJsonObject {
          dependencyStatus.forEach { (dependency, ready) ->
            put(dependency, JsonPrimitive(ready))
          }
        },
      )
      put(
        "missingDependencies",
        buildJsonArray {
          missingDependencies.forEach { dependency ->
            add(JsonPrimitive(dependency))
          }
        },
      )
    }
  processDetachedLaunchFile.writeText("${processDetachedLaunchPayload}\n", Charsets.UTF_8)
  val processDetachedLaunchReady =
    processDetachedLaunchFile.isFile &&
      detachedLaunchSupported &&
      processDetachedLaunchStatus != "unsupported"
  val resultPayload =
    buildJsonObject {
      put("status", JsonPrimitive(if (missingDependencies.isEmpty()) "ready" else "degraded"))
      put("profileId", JsonPrimitive(runtimePrimitiveContent(activeProfile, "profileId") ?: "unknown"))
      runtimePrimitiveContent(activeProfile, "displayName")?.let { put("displayName", JsonPrimitive(it)) }
      runtimePrimitiveContent(activeProfile, "engineId")?.let { put("engineId", JsonPrimitive(it)) }
      runtimePrimitiveContent(activeProfile, "environmentId")?.let { put("environmentId", JsonPrimitive(it)) }
      runtimePrimitiveContent(activeProfile, "browserId")?.let { put("browserId", JsonPrimitive(it)) }
      runtimePrimitiveContent(activeProfile, "toolsetId")?.let { put("toolsetId", JsonPrimitive(it)) }
      runtimePrimitiveContent(activeProfile, "pluginSetId")?.let { put("pluginSetId", JsonPrimitive(it)) }
      runtimePrimitiveContent(activeProfile, "supervisorId")?.let { put("supervisorId", JsonPrimitive(it)) }
      put(
        "requiredDomains",
        buildJsonArray {
          runtimeJsonArray(activeProfile, "requiredDomains").forEach { add(it) }
        },
      )
      put(
        "runtimeHomeDependencies",
        buildJsonArray {
          runtimeJsonArray(activeProfile, "runtimeHomeDependencies").forEach { add(it) }
        },
      )
      put(
        "defaultCommands",
        buildJsonArray {
          runtimeJsonArray(activeProfile, "defaultCommands").forEach { add(it) }
        },
      )
      put("environmentComponentId", JsonPrimitive(runtimePrimitiveContent(environmentManifest, "componentId") ?: ""))
      put("environmentBundleState", JsonPrimitive(runtimePrimitiveContent(environmentManifest, "bundleState") ?: ""))
      put(
        "environmentCapabilities",
        buildJsonArray {
          runtimeJsonArray(environmentManifest, "capabilities").forEach { add(it) }
        },
      )
      put("supervisorComponentId", JsonPrimitive(runtimePrimitiveContent(supervisorManifest, "componentId") ?: ""))
      put(
        "supervisorManagedActions",
        buildJsonArray {
          runtimeJsonArray(supervisorManifest, "managedActions").forEach { add(it) }
        },
      )
      put(
        "supervisorCapabilities",
        buildJsonArray {
          runtimeJsonArray(supervisorManifest, "capabilities").forEach { add(it) }
        },
      )
      put("restartSupported", JsonPrimitive(restartSupported))
      put("healthReportSupported", JsonPrimitive(healthReportSupported))
      put("environmentSupervisionReady", JsonPrimitive(environmentSupervisionReady))
      put("processModelReady", JsonPrimitive(processModelReady))
      put("processStatus", JsonPrimitive(processStatus))
      put("processStatePath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessModelStateFile)))
      put("processSessionId", JsonPrimitive(processSessionId))
      put("processBootstrapOnly", JsonPrimitive(true))
      put("processActivationReady", JsonPrimitive(processActivationReady))
      put("processActivationStatus", JsonPrimitive(processActivationStatus))
      put("processActivationState", JsonPrimitive(processActivationState))
      put("processActivationStatePath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessActivationStateFile)))
      put("processActivationGeneration", JsonPrimitive(processActivationGeneration))
      processActivationBlockedReason?.let { put("processActivationBlockedReason", JsonPrimitive(it)) }
      put("processActivationBootstrapOnly", JsonPrimitive(true))
      put("processSupervisionReady", JsonPrimitive(processSupervisionReady))
      put("processSupervisionStatus", JsonPrimitive(processSupervisionStatus))
      put("processSupervisionState", JsonPrimitive(processSupervisionState))
      put("processSupervisionStatePath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessSupervisionStateFile)))
      put("processSupervisionGeneration", JsonPrimitive(processSupervisionGeneration))
      put("processSupervisionHeartbeatAt", JsonPrimitive(supervisionHeartbeatAt))
      put("processSupervisionLeaseExpiresAt", JsonPrimitive(supervisionLeaseExpiresAt))
      processSupervisionBlockedReason?.let { put("processSupervisionBlockedReason", JsonPrimitive(it)) }
      put("processSupervisionBootstrapOnly", JsonPrimitive(true))
      put("processObservationReady", JsonPrimitive(processObservationReady))
      put("processObservationStatus", JsonPrimitive(processObservationStatus))
      put("processObservationState", JsonPrimitive(processObservationState))
      put("processObservationStatePath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessObservationStateFile)))
      put("processObservationGeneration", JsonPrimitive(processObservationGeneration))
      put("processObservationObservedAt", JsonPrimitive(executedAt))
      put("processObservationHeartbeatAgeSeconds", JsonPrimitive(processObservationHeartbeatAgeSeconds))
      put("processObservationLeaseRemainingSeconds", JsonPrimitive(processObservationLeaseRemainingSeconds))
      put("processObservationLeaseHealth", JsonPrimitive(processObservationLeaseHealth))
      put("processObservationRecoveryHint", JsonPrimitive(processObservationRecoveryHint))
      put("processObservationBootstrapOnly", JsonPrimitive(true))
      put("processRecoveryReady", JsonPrimitive(processRecoveryReady))
      put("processRecoveryStatus", JsonPrimitive(processRecoveryStatus))
      put("processRecoveryState", JsonPrimitive(processRecoveryState))
      put("processRecoveryStatePath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessRecoveryStateFile)))
      put("processRecoveryGeneration", JsonPrimitive(processRecoveryGeneration))
      put("processRecoveryActionCount", JsonPrimitive(processRecoveryActions.size))
      put("processRecoveryPrimaryAction", JsonPrimitive(processRecoveryPrimaryAction))
      put("processRecoveryReason", JsonPrimitive(processRecoveryReason))
      put("processRecoveryBootstrapOnly", JsonPrimitive(true))
      put("processDetachedLaunchReady", JsonPrimitive(processDetachedLaunchReady))
      put("processDetachedLaunchStatus", JsonPrimitive(processDetachedLaunchStatus))
      put("processDetachedLaunchState", JsonPrimitive(processDetachedLaunchState))
      put("processDetachedLaunchStatePath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessDetachedLaunchStateFile)))
      put("processDetachedLaunchGeneration", JsonPrimitive(processDetachedLaunchGeneration))
      put("processDetachedLaunchSessionId", JsonPrimitive(processDetachedLaunchSessionId))
      put("processDetachedLaunchCommand", JsonPrimitive(processDetachedLaunchCommand))
      processDetachedLaunchBlockedReason?.let { put("processDetachedLaunchBlockedReason", JsonPrimitive(it)) }
      put("processDetachedLaunchBootstrapOnly", JsonPrimitive(true))
      put("longLivedProcessReady", JsonPrimitive(longLivedProcessReady))
      put("healthStatus", JsonPrimitive(healthReportStatus))
      put("healthReportPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopHealthReportStateFile)))
      put("restartStatus", JsonPrimitive(restartStatus))
      put("restartGeneration", JsonPrimitive(restartGeneration))
      put("restartContractPath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopRestartContractStateFile)))
      put(
        "dependencyStatus",
        buildJsonObject {
          dependencyStatus.forEach { (dependency, ready) ->
            put(dependency, JsonPrimitive(ready))
          }
        },
      )
      put(
        "missingDependencies",
        buildJsonArray {
          missingDependencies.forEach { dependency ->
            add(JsonPrimitive(dependency))
          }
        },
      )
      put("browserReplayReady", JsonPrimitive(browserInspection.browserReplayReady))
      put("runtimeHomeReady", JsonPrimitive(carrierInspection.runtimeHomeReady))
      put("executedAt", JsonPrimitive(executedAt))
      put("executionCount", JsonPrimitive(executionCount))
      put("runtimeHomePath", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion)))
      put("desktopHomePath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion)))
      put("activeProfilePath", JsonPrimitive(desktopHomeDisplayPathForRuntime(manifestVersion, "profiles/active-profile.json")))
    }

  val resultFile = resolveRelativePath(runtimeHome, embeddedRuntimeDesktopProfileReplayWorkFile) ?: return null
  resultFile.parentFile?.mkdirs()
  resultFile.writeText("${resultPayload}\n", Charsets.UTF_8)

  val desktopStateFile = desktopHome.resolve(embeddedRuntimeDesktopProfileReplayStateFile)
  desktopStateFile.parentFile?.mkdirs()
  desktopStateFile.writeText("${resultPayload}\n", Charsets.UTF_8)
  desktopHome.resolve("logs/$embeddedRuntimeDesktopLogFileNameForRuntime").appendText(
    "$executedAt runtime-smoke profile=${runtimePrimitiveContent(activeProfile, "profileId") ?: "unknown"} status=${runtimePrimitiveContent(resultPayload, "status") ?: "unknown"} health=$healthReportStatus restartGeneration=$restartGeneration missingDependencies=${missingDependencies.joinToString("|").ifEmpty { "none" }}\n",
    Charsets.UTF_8,
  )

  return EmbeddedRuntimeDesktopProfileReplayExecution(
    payload = resultPayload,
    stateFilePath = desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProfileReplayStateFile),
    resultFilePath = runtimeHomeDisplayPath(manifestVersion, embeddedRuntimeDesktopProfileReplayWorkFile),
    environmentSupervisionReady = environmentSupervisionReady,
    healthReportPath = desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopHealthReportStateFile),
    restartContractPath = desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopRestartContractStateFile),
    processModelReady = processModelReady,
    processStatePath = desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessModelStateFile),
    processStatus = processStatus,
    processSessionId = processSessionId,
    processActivationReady = processActivationReady,
    processActivationStatePath = desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessActivationStateFile),
    processActivationStatus = processActivationStatus,
    processActivationState = processActivationState,
    processActivationGeneration = processActivationGeneration,
    processActivationBlockedReason = processActivationBlockedReason,
    processSupervisionReady = processSupervisionReady,
    processSupervisionStatePath = desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessSupervisionStateFile),
    processSupervisionStatus = processSupervisionStatus,
    processSupervisionState = processSupervisionState,
    processSupervisionGeneration = processSupervisionGeneration,
    processSupervisionHeartbeatAt = supervisionHeartbeatAt,
    processSupervisionLeaseExpiresAt = supervisionLeaseExpiresAt,
    processSupervisionBlockedReason = processSupervisionBlockedReason,
    processObservationReady = processObservationReady,
    processObservationStatePath = desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessObservationStateFile),
    processObservationStatus = processObservationStatus,
    processObservationState = processObservationState,
    processObservationGeneration = processObservationGeneration,
    processObservationObservedAt = executedAt,
    processObservationHeartbeatAgeSeconds = processObservationHeartbeatAgeSeconds,
    processObservationLeaseRemainingSeconds = processObservationLeaseRemainingSeconds,
    processObservationLeaseHealth = processObservationLeaseHealth,
    processObservationRecoveryHint = processObservationRecoveryHint,
    processRecoveryReady = processRecoveryReady,
    processRecoveryStatePath = desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessRecoveryStateFile),
    processRecoveryStatus = processRecoveryStatus,
    processRecoveryState = processRecoveryState,
    processRecoveryGeneration = processRecoveryGeneration,
    processRecoveryActionCount = processRecoveryActions.size,
    processRecoveryPrimaryAction = processRecoveryPrimaryAction,
    processRecoveryReason = processRecoveryReason,
    processDetachedLaunchReady = processDetachedLaunchReady,
    processDetachedLaunchStatePath = desktopHomeDisplayPathForRuntime(manifestVersion, embeddedRuntimeDesktopProcessDetachedLaunchStateFile),
    processDetachedLaunchStatus = processDetachedLaunchStatus,
    processDetachedLaunchState = processDetachedLaunchState,
    processDetachedLaunchGeneration = processDetachedLaunchGeneration,
    processDetachedLaunchSessionId = processDetachedLaunchSessionId,
    processDetachedLaunchCommand = processDetachedLaunchCommand,
    processDetachedLaunchBlockedReason = processDetachedLaunchBlockedReason,
    longLivedProcessReady = longLivedProcessReady,
  )
}

private fun loadRuntimeStageManifest(file: File): EmbeddedRuntimeStageManifest? =
  runCatching { embeddedRuntimeRuntimeJson.decodeFromString(EmbeddedRuntimeStageManifest.serializer(), file.readText(Charsets.UTF_8)) }.getOrNull()

private fun loadRuntimeEngineManifest(file: File): EmbeddedRuntimeEngineManifest? =
  runCatching { embeddedRuntimeRuntimeJson.decodeFromString(EmbeddedRuntimeEngineManifest.serializer(), file.readText(Charsets.UTF_8)) }.getOrNull()

private fun loadRuntimeTaskDefinition(file: File): EmbeddedRuntimeTaskDefinition? =
  runCatching { embeddedRuntimeRuntimeJson.decodeFromString(EmbeddedRuntimeTaskDefinition.serializer(), file.readText(Charsets.UTF_8)) }.getOrNull()

private fun loadToolkitStageManifest(file: File): EmbeddedToolkitStageManifest? =
  runCatching { embeddedRuntimeRuntimeJson.decodeFromString(EmbeddedToolkitStageManifest.serializer(), file.readText(Charsets.UTF_8)) }.getOrNull()

private fun loadRuntimeToolDescriptor(file: File): EmbeddedRuntimeToolDescriptor? =
  runCatching { embeddedRuntimeRuntimeJson.decodeFromString(EmbeddedRuntimeToolDescriptor.serializer(), file.readText(Charsets.UTF_8)) }.getOrNull()

private fun loadRuntimeToolCommandPolicy(file: File): EmbeddedRuntimeToolCommandPolicy? =
  runCatching { embeddedRuntimeRuntimeJson.decodeFromString(EmbeddedRuntimeToolCommandPolicy.serializer(), file.readText(Charsets.UTF_8)) }.getOrNull()

private fun embeddedRuntimePodInstallRoot(context: Context): File = context.filesDir.resolve("openclaw/embedded-runtime-pod")

private fun embeddedRuntimeHomeRoot(context: Context): File = context.filesDir.resolve("openclaw/embedded-runtime-home")

private fun runtimeHomeDisplayPath(manifestVersion: String, relativePath: String? = null): String {
  val normalized = relativePath?.trim()?.replace('\\', '/')?.trimStart('/').orEmpty()
  return if (normalized.isEmpty()) "$embeddedRuntimeHomeRootDisplayPath/$manifestVersion" else "$embeddedRuntimeHomeRootDisplayPath/$manifestVersion/$normalized"
}

private fun resolveRelativePath(rootDir: File, relativePath: String): File? {
  val normalized = relativePath.replace('\\', '/').trim().trimStart('/').removePrefix("./")
  if (normalized.isEmpty() || normalized.contains("../")) return if (normalized.isEmpty()) rootDir else null
  val candidate = File(rootDir, normalized).canonicalFile
  val rootPath = rootDir.canonicalPath
  return if (candidate.path == rootPath || candidate.path.startsWith("$rootPath${File.separator}")) candidate else null
}

private fun readRuntimeJsonObjectOrNull(file: File): JsonObject? =
  runCatching { embeddedRuntimeRuntimeJson.parseToJsonElement(file.readText(Charsets.UTF_8)) as? JsonObject }.getOrNull()

private fun runtimePrimitiveContent(
  payload: JsonObject?,
  key: String,
): String? = (payload?.get(key) as? JsonPrimitive)?.content?.takeUnless { it == "null" }

private fun runtimePrimitiveBoolean(
  payload: JsonObject?,
  key: String,
): Boolean? = (payload?.get(key) as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

private fun runtimePrimitiveInt(
  payload: JsonObject?,
  key: String,
): Int? = (payload?.get(key) as? JsonPrimitive)?.content?.toIntOrNull()

private fun runtimeJsonArray(
  payload: JsonObject?,
  key: String,
): List<JsonPrimitive> {
  val array = payload?.get(key) as? JsonArray ?: return emptyList()
  return array.mapNotNull { it as? JsonPrimitive }
}

private fun extractHeadingText(line: String): String = line.trimStart().trimStart('#').trim()

private fun countKeywordHits(text: String, keyword: String): Int {
  var count = 0
  var index = 0
  val haystack = text.lowercase()
  val needle = keyword.lowercase()
  while (true) {
    index = haystack.indexOf(needle, index)
    if (index < 0) return count
    count += 1
    index += needle.length
  }
}

private fun sha256(file: File): String {
  val digest = MessageDigest.getInstance("SHA-256")
  file.inputStream().use { input ->
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
      val read = input.read(buffer)
      if (read <= 0) break
      digest.update(buffer, 0, read)
    }
  }
  return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun embeddedDesktopHomeRootForRuntime(context: Context): File =
  context.filesDir.resolve("openclaw/embedded-desktop-home")

private fun desktopHomeDisplayPathForRuntime(
  manifestVersion: String,
  relativePath: String? = null,
): String {
  val normalized = relativePath?.trim()?.replace('\\', '/')?.trimStart('/').orEmpty()
  return if (normalized.isEmpty()) {
    "$embeddedDesktopHomeRootDisplayPathForRuntime/$manifestVersion"
  } else {
    "$embeddedDesktopHomeRootDisplayPathForRuntime/$manifestVersion/$normalized"
  }
}
