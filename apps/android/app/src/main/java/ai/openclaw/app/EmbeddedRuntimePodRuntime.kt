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
  val runtimeHomeLayout: List<String> = emptyList(),
  val capabilities: List<String> = emptyList(),
  val limitations: List<String> = emptyList(),
)

@Serializable
private data class EmbeddedRuntimeTaskDefinition(
  val schemaVersion: Int = 1,
  val taskId: String,
  val displayName: String? = null,
  val kind: String,
  val summary: String? = null,
  val packagedConfigPath: String? = null,
  val packagedWorkspacePath: String? = null,
  val stateFile: String,
  val logFile: String,
)

data class EmbeddedRuntimeCarrierInspection(
  val runtimeStageInstalled: Boolean,
  val runtimeStageManifestPresent: Boolean,
  val runtimeEngineManifestPresent: Boolean,
  val runtimeTaskCount: Int,
  val runtimeTaskIds: List<String>,
  val runtimeConfigCount: Int,
  val engineId: String? = null,
  val engineVersion: String? = null,
  val runtimeHomeExists: Boolean,
  val runtimeHomeReady: Boolean,
  val runtimeHomeVersion: String? = null,
  val runtimeExecutionStateCount: Int = 0,
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
      runtimeConfigCount = 0,
      runtimeHomeExists = false,
      runtimeHomeReady = false,
      runtimeHomeVersion = null,
      runtimeExecutionStateCount = 0,
    )
  }

  val versionDir = embeddedRuntimePodInstallRoot(context).resolve(manifestVersion)
  val runtimeStageRoot = versionDir.resolve(embeddedRuntimeStageDirectoryName)
  val runtimeStageInstalled = runtimeStageRoot.isDirectory
  val stageManifest = runtimeStageRoot.resolve("manifest.json").takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
  val engineManifestFile = runtimeStageRoot.resolve("engine/manifest.json")
  val engineManifestPresent = engineManifestFile.isFile
  val engineManifest = engineManifestFile.takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
  val taskFiles =
    runtimeStageRoot
      .resolve("tasks")
      .takeIf { it.isDirectory }
      ?.listFiles()
      ?.filter { it.isFile && it.extension.lowercase() == "json" }
      ?.sortedBy { it.name }
      .orEmpty()
  val taskIds =
    jsonStringArray(stageManifest?.get("taskIds"))
      .ifEmpty {
        taskFiles.map { it.nameWithoutExtension }
      }
      .distinct()
      .sorted()
  val runtimeConfigCount =
    runtimeStageRoot
      .resolve("config")
      .takeIf { it.isDirectory }
      ?.listFiles()
      ?.count { it.isFile }
      ?: 0

  val runtimeHome = embeddedRuntimeHomeRoot(context).resolve(manifestVersion)
  val configDir = runtimeHome.resolve("config")
  val logsDir = runtimeHome.resolve("logs")
  val stateDir = runtimeHome.resolve("state")
  val workDir = runtimeHome.resolve("work")
  val runtimeEnvFile = configDir.resolve("runtime-env.json")
  val runtimeHomeExists = runtimeHome.isDirectory
  val runtimeExecutionStateCount =
    stateDir
      .takeIf { it.isDirectory }
      ?.listFiles()
      ?.count { it.isFile && it.extension.lowercase() == "json" }
      ?: 0
  val runtimeHomeReady =
    runtimeHomeExists &&
      configDir.isDirectory &&
      logsDir.isDirectory &&
      stateDir.isDirectory &&
      workDir.isDirectory &&
      runtimeEnvFile.isFile

  return EmbeddedRuntimeCarrierInspection(
    runtimeStageInstalled = runtimeStageInstalled,
    runtimeStageManifestPresent = stageManifest != null,
    runtimeEngineManifestPresent = engineManifestPresent,
    runtimeTaskCount = taskIds.size,
    runtimeTaskIds = taskIds,
    runtimeConfigCount = runtimeConfigCount,
    engineId =
      primitiveContent(engineManifest, "engineId")
        ?: primitiveContent(stageManifest, "engineId"),
    engineVersion = primitiveContent(engineManifest, "engineVersion"),
    runtimeHomeExists = runtimeHomeExists,
    runtimeHomeReady = runtimeHomeReady,
    runtimeHomeVersion = manifestVersion,
    runtimeExecutionStateCount = runtimeExecutionStateCount,
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
  if (!runtimeStageRoot.isDirectory) {
    return EmbeddedRuntimePodRuntimeExecuteResult(
      ok = false,
      code = "RUNTIME_NOT_READY",
      message = "RUNTIME_NOT_READY: runtime stage not installed",
    )
  }

  val stageManifest =
    runtimeStageRoot.resolve("manifest.json").takeIf { it.isFile }?.let(::loadRuntimeStageManifest)
      ?: return EmbeddedRuntimePodRuntimeExecuteResult(
        ok = false,
        code = "RUNTIME_NOT_READY",
        message = "RUNTIME_NOT_READY: runtime stage manifest missing",
      )
  val engineManifest =
    stageManifest.engineManifestPath
      ?.let { resolveRelativePath(runtimeStageRoot, it) }
      ?.takeIf { it.isFile }
      ?.let(::loadRuntimeEngineManifest)
      ?: return EmbeddedRuntimePodRuntimeExecuteResult(
        ok = false,
        code = "RUNTIME_NOT_READY",
        message = "RUNTIME_NOT_READY: runtime engine manifest missing",
      )
  val taskFile =
    runtimeStageRoot
      .resolve("tasks")
      .resolve("$normalizedTaskId.json")
      .takeIf { it.isFile }
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
  if (task.kind != "runtime-smoke") {
    return EmbeddedRuntimePodRuntimeExecuteResult(
      ok = false,
      code = "UNSUPPORTED_TASK",
      message = "UNSUPPORTED_TASK: ${task.kind} is not supported by the embedded runtime carrier yet",
    )
  }

  val packagedConfigFile =
    task.packagedConfigPath
      ?.let { resolveRelativePath(runtimeStageRoot, it) }
      ?.takeIf { it.isFile }
      ?: return EmbeddedRuntimePodRuntimeExecuteResult(
        ok = false,
        code = "RUNTIME_NOT_READY",
        message = "RUNTIME_NOT_READY: packaged runtime config missing",
      )
  val packagedConfigJson =
    readRuntimeJsonObjectOrNull(packagedConfigFile)
      ?: return EmbeddedRuntimePodRuntimeExecuteResult(
        ok = false,
        code = "UNAVAILABLE",
        message = "UNAVAILABLE: packaged runtime config invalid",
      )
  val packagedWorkspaceFile =
    task.packagedWorkspacePath
      ?.let { resolveRelativePath(versionDir, it) }
      ?.takeIf { it.isFile }
      ?: return EmbeddedRuntimePodRuntimeExecuteResult(
        ok = false,
        code = "RUNTIME_NOT_READY",
        message = "RUNTIME_NOT_READY: packaged workspace input missing",
      )

  val runtimeHome = embeddedRuntimeHomeRoot(context).resolve(manifestVersion)
  val configDir = runtimeHome.resolve("config")
  val logsDir = runtimeHome.resolve("logs")
  val stateDir = runtimeHome.resolve("state")
  val workDir = runtimeHome.resolve("work")
  val runtimeHomeExisted = runtimeHome.isDirectory
  listOf(runtimeHome, configDir, logsDir, stateDir, workDir).forEach { directory ->
    directory.mkdirs()
  }

  val now = Instant.now().toString()
  val hydratedConfigPath = configDir.resolve("runtime-env.json")
  val hydratedConfig =
    buildJsonObject {
      packagedConfigJson.forEach { (key, value) -> put(key, value) }
      put("podVersion", JsonPrimitive(manifestVersion))
      put("engineVersion", JsonPrimitive(engineManifest.engineVersion ?: "0.0.0"))
      put("lastTaskId", JsonPrimitive(task.taskId))
      put("lastExecutedAt", JsonPrimitive(now))
    }
  hydratedConfigPath.writeText("${hydratedConfig}\n", Charsets.UTF_8)

  val stateFile =
    resolveRelativePath(runtimeHome, task.stateFile)
      ?: return EmbeddedRuntimePodRuntimeExecuteResult(
        ok = false,
        code = "INVALID_REQUEST",
        message = "INVALID_REQUEST: runtime state file escapes runtime home",
      )
  val previousState =
    stateFile.takeIf { it.isFile }?.let(::readRuntimeJsonObjectOrNull)
  val executionCount = (previousState?.get("executionCount") as? JsonPrimitive)?.content?.toIntOrNull()?.plus(1) ?: 1
  val statePayload =
    buildJsonObject {
      put("taskId", JsonPrimitive(task.taskId))
      put("taskKind", JsonPrimitive(task.kind))
      put("displayName", JsonPrimitive(task.displayName ?: task.taskId))
      put("status", JsonPrimitive("ok"))
      put("engineId", JsonPrimitive(engineManifest.engineId))
      put("engineVersion", JsonPrimitive(engineManifest.engineVersion ?: "0.0.0"))
      put("podVersion", JsonPrimitive(manifestVersion))
      put("executionCount", JsonPrimitive(executionCount))
      put("executedAt", JsonPrimitive(now))
      put("packagedConfigPath", JsonPrimitive(task.packagedConfigPath ?: ""))
      put("packagedWorkspacePath", JsonPrimitive(task.packagedWorkspacePath ?: ""))
      put("workspaceDocumentSha256", JsonPrimitive(sha256(packagedWorkspaceFile)))
      put("workspaceDocumentSizeBytes", JsonPrimitive(packagedWorkspaceFile.length()))
      put("hydratedConfigPath", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion, "config/runtime-env.json")))
      put("runtimeHomePath", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion)))
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
    "$now ${task.taskId} engine=${engineManifest.engineId} pod=$manifestVersion workspace=${task.packagedWorkspacePath}\n",
    Charsets.UTF_8,
  )

  return EmbeddedRuntimePodRuntimeExecuteResult(
    ok = true,
    payload =
      buildJsonObject {
        put("taskId", JsonPrimitive(task.taskId))
        put("taskKind", JsonPrimitive(task.kind))
        put("displayName", JsonPrimitive(task.displayName ?: task.taskId))
        put("engineId", JsonPrimitive(engineManifest.engineId))
        put("engineVersion", JsonPrimitive(engineManifest.engineVersion ?: "0.0.0"))
        put("engineKind", JsonPrimitive(engineManifest.kind ?: "descriptor"))
        put("summary", JsonPrimitive(task.summary ?: ""))
        put("embeddedPodVersion", JsonPrimitive(manifestVersion))
        put("runtimeStageInstalled", JsonPrimitive(true))
        put("runtimeStageManifestPresent", JsonPrimitive(true))
        put("runtimeEngineManifestPresent", JsonPrimitive(true))
        put("packagedTaskPresent", JsonPrimitive(true))
        put("packagedConfigPresent", JsonPrimitive(true))
        put("packagedWorkspacePresent", JsonPrimitive(true))
        put("runtimeHomeCreated", JsonPrimitive(!runtimeHomeExisted))
        put("runtimeHomeReady", JsonPrimitive(true))
        put("executionCount", JsonPrimitive(executionCount))
        put("hydratedConfigPath", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion, "config/runtime-env.json")))
        put("stateFilePath", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion, task.stateFile)))
        put("logFilePath", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion, task.logFile)))
        put(
          "runtimeHome",
          buildJsonObject {
            put("root", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion)))
            put("configDir", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion, "config")))
            put("logsDir", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion, "logs")))
            put("stateDir", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion, "state")))
            put("workDir", JsonPrimitive(runtimeHomeDisplayPath(manifestVersion, "work")))
          },
        )
        put("state", statePayload)
        put("packagedConfig", hydratedConfig)
      },
  )
}

private fun loadRuntimeStageManifest(file: File): EmbeddedRuntimeStageManifest? {
  return runCatching {
    embeddedRuntimeRuntimeJson.decodeFromString(
      EmbeddedRuntimeStageManifest.serializer(),
      file.readText(Charsets.UTF_8),
    )
  }.getOrNull()
}

private fun loadRuntimeEngineManifest(file: File): EmbeddedRuntimeEngineManifest? {
  return runCatching {
    embeddedRuntimeRuntimeJson.decodeFromString(
      EmbeddedRuntimeEngineManifest.serializer(),
      file.readText(Charsets.UTF_8),
    )
  }.getOrNull()
}

private fun loadRuntimeTaskDefinition(file: File): EmbeddedRuntimeTaskDefinition? {
  return runCatching {
    embeddedRuntimeRuntimeJson.decodeFromString(
      EmbeddedRuntimeTaskDefinition.serializer(),
      file.readText(Charsets.UTF_8),
    )
  }.getOrNull()
}

private fun embeddedRuntimePodInstallRoot(context: Context): File =
  context.filesDir.resolve("openclaw/embedded-runtime-pod")

private fun embeddedRuntimeHomeRoot(context: Context): File =
  context.filesDir.resolve("openclaw/embedded-runtime-home")

private fun runtimeHomeDisplayPath(
  manifestVersion: String,
  relativePath: String? = null,
): String {
  val normalized = relativePath?.trim()?.replace('\\', '/')?.trimStart('/').orEmpty()
  return if (normalized.isEmpty()) {
    "$embeddedRuntimeHomeRootDisplayPath/$manifestVersion"
  } else {
    "$embeddedRuntimeHomeRootDisplayPath/$manifestVersion/$normalized"
  }
}

private fun resolveRelativePath(
  rootDir: File,
  relativePath: String,
): File? {
  val normalized = relativePath.replace('\\', '/').trim().trimStart('/').removePrefix("./")
  if (normalized.isEmpty()) return rootDir
  if (normalized.contains("../")) return null
  val candidate = File(rootDir, normalized).canonicalFile
  val rootPath = rootDir.canonicalPath
  return if (candidate.path == rootPath || candidate.path.startsWith("$rootPath${File.separator}")) {
    candidate
  } else {
    null
  }
}

private fun readRuntimeJsonObjectOrNull(file: File): JsonObject? {
  return runCatching {
    embeddedRuntimeRuntimeJson.parseToJsonElement(file.readText(Charsets.UTF_8)) as? JsonObject
  }.getOrNull()
}

private fun primitiveContent(
  jsonObject: JsonObject?,
  key: String,
): String? {
  val primitive = jsonObject?.get(key) as? JsonPrimitive ?: return null
  return primitive.content.takeUnless { it == "null" }
}

private fun jsonStringArray(element: kotlinx.serialization.json.JsonElement?): List<String> {
  val array = element as? JsonArray ?: return emptyList()
  return array.mapNotNull { item ->
    (item as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }
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
  return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}
