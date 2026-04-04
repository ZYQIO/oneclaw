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
      desktopProfileReplayStatePath?.let { put("desktopProfileReplayStatePath", JsonPrimitive(it)) }
      desktopProfileReplayResultPath?.let { put("desktopProfileReplayResultPath", JsonPrimitive(it)) }
      desktopHealthReportPath?.let { put("desktopHealthReportPath", JsonPrimitive(it)) }
      desktopRestartContractPath?.let { put("desktopRestartContractPath", JsonPrimitive(it)) }
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
        desktopProfileReplayStatePath?.let { put("desktopProfileReplayStatePath", JsonPrimitive(it)) }
        desktopProfileReplayResultPath?.let { put("desktopProfileReplayResultFilePath", JsonPrimitive(it)) }
        desktopHealthReportPath?.let { put("desktopHealthReportPath", JsonPrimitive(it)) }
        desktopRestartContractPath?.let { put("desktopRestartContractPath", JsonPrimitive(it)) }
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
  val statePayload = stateFile.takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
  val healthPayload = healthReportFile.takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
  val restartPayload = restartContractFile.takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
  val dependencyStatus = statePayload?.get("dependencyStatus") as? JsonObject
  val missingDependencies = statePayload?.get("missingDependencies") as? JsonArray
  val status = runtimePrimitiveContent(statePayload, "status")
  val healthStatus = runtimePrimitiveContent(healthPayload, "status")
  val restartStatus = runtimePrimitiveContent(restartPayload, "status")
  val environmentSupervisionReady =
    stateFile.isFile &&
      resultFile.isFile &&
      healthReportFile.isFile &&
      restartContractFile.isFile &&
      healthStatus != null &&
      restartStatus != null
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
