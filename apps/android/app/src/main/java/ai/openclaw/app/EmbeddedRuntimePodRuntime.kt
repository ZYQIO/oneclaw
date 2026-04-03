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
private const val embeddedRuntimeHomeRootDisplayPath = "filesDir/openclaw/embedded-runtime-home"
private const val embeddedRuntimeDefaultTaskId = "runtime-smoke"

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
  val packagedConfigPath: String? = null,
  val packagedWorkspacePath: String? = null,
  val packagedToolDescriptorPath: String? = null,
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
)

data class EmbeddedRuntimePodRuntimeExecuteResult(
  val ok: Boolean,
  val payload: JsonObject? = null,
  val code: String? = null,
  val message: String? = null,
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
  if (task.kind != "runtime-smoke" && task.kind != "desktop-tool") {
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
      toolId?.let { put("toolId", JsonPrimitive(it)) }
      toolResultFilePath?.let { put("toolResultFilePath", JsonPrimitive(it)) }
      toolResultPayload?.let { put("toolResult", it) }
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
    "$now ${task.taskId} kind=${task.kind} engine=${runtimeEngineManifest.engineId} tool=${toolId ?: "none"} pod=$manifestVersion workspace=${task.packagedWorkspacePath}\n",
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
        toolId?.let { put("toolId", JsonPrimitive(it)) }
        put("toolkitStageInstalled", JsonPrimitive(toolkitStageInstalled))
        put("toolkitStageManifestPresent", JsonPrimitive(toolkitStageManifestPresent))
        put("toolkitCommandPolicyPresent", JsonPrimitive(toolkitCommandPolicyPresent))
        put("packagedToolDescriptorPresent", JsonPrimitive(packagedToolDescriptorPresent))
        toolResultFilePath?.let { put("toolResultFilePath", JsonPrimitive(it)) }
        toolDescriptorPayload?.let { put("toolDescriptor", it) }
        toolResultPayload?.let { put("toolResult", it) }
        put("state", statePayload)
        put("packagedConfig", hydratedConfig)
      },
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
