package ai.openclaw.app

import android.content.Context
import java.io.File
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

private const val embeddedDesktopStageDirectoryName = "desktop"
private const val embeddedDesktopHomeRootDisplayPath = "filesDir/openclaw/embedded-desktop-home"
private const val embeddedDesktopDefaultProfileId = "openclaw-desktop-host"
private const val embeddedDesktopStateFileName = "desktop-materialize.json"
private const val embeddedDesktopLogFileName = "desktop-home.log"

private val embeddedRuntimeDesktopJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class EmbeddedDesktopStageManifest(
  val stage: String,
  val purpose: String? = null,
  val engineManifestPath: String? = null,
  val environmentManifestPath: String? = null,
  val browserManifestPath: String? = null,
  val toolsManifestPath: String? = null,
  val pluginsManifestPath: String? = null,
  val supervisorManifestPath: String? = null,
  val profileDescriptorPaths: List<String> = emptyList(),
  val profileIds: List<String> = emptyList(),
)

@Serializable
private data class EmbeddedDesktopProfileDescriptor(
  val schemaVersion: Int = 1,
  val profileId: String,
  val displayName: String? = null,
  val engineId: String? = null,
  val environmentId: String? = null,
  val browserId: String? = null,
  val toolsetId: String? = null,
  val pluginSetId: String? = null,
  val supervisorId: String? = null,
  val requiredDomains: List<String> = emptyList(),
  val runtimeHomeDependencies: List<String> = emptyList(),
  val defaultCommands: List<String> = emptyList(),
)

data class EmbeddedRuntimeDesktopEnvironmentInspection(
  val desktopStageInstalled: Boolean,
  val desktopStageManifestPresent: Boolean,
  val desktopBundleReady: Boolean,
  val engineManifestPresent: Boolean,
  val environmentManifestPresent: Boolean,
  val browserManifestPresent: Boolean,
  val toolsManifestPresent: Boolean,
  val pluginsManifestPresent: Boolean,
  val supervisorManifestPresent: Boolean,
  val browserFlowCount: Int = 0,
  val toolCount: Int = 0,
  val pluginCount: Int = 0,
  val profileCount: Int = 0,
  val profileIds: List<String> = emptyList(),
  val desktopHomeExists: Boolean = false,
  val desktopHomeReady: Boolean = false,
  val materializeStatePresent: Boolean = false,
  val materializeLogPresent: Boolean = false,
  val activeProfileId: String? = null,
)

data class EmbeddedRuntimePodDesktopMaterializeResult(
  val ok: Boolean,
  val payload: JsonObject? = null,
  val code: String? = null,
  val message: String? = null,
)

fun inspectEmbeddedRuntimeDesktopEnvironment(
  context: Context,
  manifestVersion: String?,
): EmbeddedRuntimeDesktopEnvironmentInspection {
  if (manifestVersion.isNullOrBlank()) {
    return EmbeddedRuntimeDesktopEnvironmentInspection(
      desktopStageInstalled = false,
      desktopStageManifestPresent = false,
      desktopBundleReady = false,
      engineManifestPresent = false,
      environmentManifestPresent = false,
      browserManifestPresent = false,
      toolsManifestPresent = false,
      pluginsManifestPresent = false,
      supervisorManifestPresent = false,
    )
  }

  val versionDir = embeddedRuntimeDesktopPodInstallRoot(context).resolve(manifestVersion)
  val desktopStageRoot = versionDir.resolve(embeddedDesktopStageDirectoryName)
  val desktopStageInstalled = desktopStageRoot.isDirectory
  val stageManifest = desktopStageRoot.resolve("manifest.json").takeIf { it.isFile }?.let(::loadDesktopStageManifest)
  val engineManifest = stageManifest?.engineManifestPath?.let { resolveDesktopRelativePath(desktopStageRoot, it) }?.takeIf { it.isFile }?.let(::readDesktopJsonObjectOrNull)
  val environmentManifest = stageManifest?.environmentManifestPath?.let { resolveDesktopRelativePath(desktopStageRoot, it) }?.takeIf { it.isFile }?.let(::readDesktopJsonObjectOrNull)
  val browserManifest = stageManifest?.browserManifestPath?.let { resolveDesktopRelativePath(desktopStageRoot, it) }?.takeIf { it.isFile }?.let(::readDesktopJsonObjectOrNull)
  val toolsManifest = stageManifest?.toolsManifestPath?.let { resolveDesktopRelativePath(desktopStageRoot, it) }?.takeIf { it.isFile }?.let(::readDesktopJsonObjectOrNull)
  val pluginsManifest = stageManifest?.pluginsManifestPath?.let { resolveDesktopRelativePath(desktopStageRoot, it) }?.takeIf { it.isFile }?.let(::readDesktopJsonObjectOrNull)
  val supervisorManifest = stageManifest?.supervisorManifestPath?.let { resolveDesktopRelativePath(desktopStageRoot, it) }?.takeIf { it.isFile }?.let(::readDesktopJsonObjectOrNull)
  val profileFiles =
    stageManifest?.profileDescriptorPaths?.mapNotNull { resolveDesktopRelativePath(desktopStageRoot, it)?.takeIf(File::isFile) }
      .orEmpty()
      .ifEmpty {
        desktopStageRoot.resolve("profiles").takeIf { it.isDirectory }?.listFiles()?.filter {
          it.isFile && it.extension.lowercase() == "json"
        }.orEmpty().sortedBy { it.name }
      }
  val profiles = profileFiles.mapNotNull(::loadDesktopProfileDescriptor)
  val profileIds = (stageManifest?.profileIds.orEmpty() + profiles.map { it.profileId }).distinct().sorted()

  val desktopHome = embeddedDesktopHomeRoot(context).resolve(manifestVersion)
  val activeProfile =
    desktopHome.resolve("profiles/active-profile.json").takeIf { it.isFile }?.let(::readDesktopJsonObjectOrNull)
  val activeProfileId = desktopPrimitiveContent(activeProfile, "profileId")
  val materializeState = desktopHome.resolve("state/$embeddedDesktopStateFileName")
  val materializeLog = desktopHome.resolve("logs/$embeddedDesktopLogFileName")
  val desktopHomeReady =
    desktopHome.isDirectory &&
      desktopHome.resolve("engine/manifest.json").isFile &&
      desktopHome.resolve("environment/manifest.json").isFile &&
      desktopHome.resolve("browser/manifest.json").isFile &&
      desktopHome.resolve("tools/manifest.json").isFile &&
      desktopHome.resolve("plugins/manifest.json").isFile &&
      desktopHome.resolve("supervisor/manifest.json").isFile &&
      desktopHome.resolve("profiles/active-profile.json").isFile &&
      desktopHome.resolve("logs").isDirectory &&
      desktopHome.resolve("state").isDirectory &&
      desktopHome.resolve("work").isDirectory

  return EmbeddedRuntimeDesktopEnvironmentInspection(
    desktopStageInstalled = desktopStageInstalled,
    desktopStageManifestPresent = stageManifest != null,
    desktopBundleReady =
      desktopStageInstalled &&
        stageManifest != null &&
        engineManifest != null &&
        environmentManifest != null &&
        browserManifest != null &&
        toolsManifest != null &&
        pluginsManifest != null &&
        supervisorManifest != null &&
        profileIds.isNotEmpty(),
    engineManifestPresent = engineManifest != null,
    environmentManifestPresent = environmentManifest != null,
    browserManifestPresent = browserManifest != null,
    toolsManifestPresent = toolsManifest != null,
    pluginsManifestPresent = pluginsManifest != null,
    supervisorManifestPresent = supervisorManifest != null,
    browserFlowCount = desktopJsonArray(browserManifest, "authFlowIds").size,
    toolCount = desktopJsonArray(toolsManifest, "toolIds").size,
    pluginCount = desktopJsonArray(pluginsManifest, "pluginIds").size,
    profileCount = profileIds.size,
    profileIds = profileIds,
    desktopHomeExists = desktopHome.isDirectory,
    desktopHomeReady = desktopHomeReady,
    materializeStatePresent = materializeState.isFile,
    materializeLogPresent = materializeLog.isFile,
    activeProfileId = activeProfileId,
  )
}

fun materializeEmbeddedRuntimeDesktopEnvironment(
  context: Context,
  profileId: String?,
): EmbeddedRuntimePodDesktopMaterializeResult {
  val podInspection = inspectEmbeddedRuntimePod(context)
  val manifestVersion = podInspection.manifestVersion
  if (!podInspection.ready || manifestVersion.isNullOrBlank()) {
    return EmbeddedRuntimePodDesktopMaterializeResult(
      ok = false,
      code = "POD_NOT_READY",
      message = "POD_NOT_READY: embedded runtime pod not ready",
    )
  }

  val versionDir = embeddedRuntimeDesktopPodInstallRoot(context).resolve(manifestVersion)
  val desktopStageRoot = versionDir.resolve(embeddedDesktopStageDirectoryName)
  val stageManifest =
    desktopStageRoot.resolve("manifest.json").takeIf { it.isFile }?.let(::loadDesktopStageManifest)
      ?: return EmbeddedRuntimePodDesktopMaterializeResult(
        ok = false,
        code = "DESKTOP_NOT_READY",
        message = "DESKTOP_NOT_READY: desktop stage manifest missing",
      )

  val engineManifestFile =
    stageManifest.engineManifestPath?.let { resolveDesktopRelativePath(desktopStageRoot, it) }?.takeIf { it.isFile }
      ?: return EmbeddedRuntimePodDesktopMaterializeResult(
        ok = false,
        code = "DESKTOP_NOT_READY",
        message = "DESKTOP_NOT_READY: desktop engine manifest missing",
      )
  val environmentManifestFile =
    stageManifest.environmentManifestPath?.let { resolveDesktopRelativePath(desktopStageRoot, it) }?.takeIf { it.isFile }
      ?: return EmbeddedRuntimePodDesktopMaterializeResult(
        ok = false,
        code = "DESKTOP_NOT_READY",
        message = "DESKTOP_NOT_READY: desktop environment manifest missing",
      )
  val browserManifestFile =
    stageManifest.browserManifestPath?.let { resolveDesktopRelativePath(desktopStageRoot, it) }?.takeIf { it.isFile }
      ?: return EmbeddedRuntimePodDesktopMaterializeResult(
        ok = false,
        code = "DESKTOP_NOT_READY",
        message = "DESKTOP_NOT_READY: desktop browser manifest missing",
      )
  val toolsManifestFile =
    stageManifest.toolsManifestPath?.let { resolveDesktopRelativePath(desktopStageRoot, it) }?.takeIf { it.isFile }
      ?: return EmbeddedRuntimePodDesktopMaterializeResult(
        ok = false,
        code = "DESKTOP_NOT_READY",
        message = "DESKTOP_NOT_READY: desktop tools manifest missing",
      )
  val pluginsManifestFile =
    stageManifest.pluginsManifestPath?.let { resolveDesktopRelativePath(desktopStageRoot, it) }?.takeIf { it.isFile }
      ?: return EmbeddedRuntimePodDesktopMaterializeResult(
        ok = false,
        code = "DESKTOP_NOT_READY",
        message = "DESKTOP_NOT_READY: desktop plugins manifest missing",
      )
  val supervisorManifestFile =
    stageManifest.supervisorManifestPath?.let { resolveDesktopRelativePath(desktopStageRoot, it) }?.takeIf { it.isFile }
      ?: return EmbeddedRuntimePodDesktopMaterializeResult(
        ok = false,
        code = "DESKTOP_NOT_READY",
        message = "DESKTOP_NOT_READY: desktop supervisor manifest missing",
      )

  val profiles =
    stageManifest.profileDescriptorPaths.mapNotNull { profilePath ->
      val file = resolveDesktopRelativePath(desktopStageRoot, profilePath)?.takeIf { it.isFile } ?: return@mapNotNull null
      loadDesktopProfileDescriptor(file)?.let { descriptor -> descriptor to file }
    }
  val selectedProfileId = profileId?.trim().takeUnless { it.isNullOrEmpty() } ?: embeddedDesktopDefaultProfileId
  val selectedProfile =
    profiles.firstOrNull { (descriptor, _) -> descriptor.profileId == selectedProfileId }
      ?: return EmbeddedRuntimePodDesktopMaterializeResult(
        ok = false,
        code = "NOT_FOUND",
        message = "NOT_FOUND: desktop profile not found",
      )

  val desktopHome = embeddedDesktopHomeRoot(context).resolve(manifestVersion)
  val desktopHomeExisted = desktopHome.isDirectory
  listOf(
    desktopHome,
    desktopHome.resolve("config"),
    desktopHome.resolve("engine"),
    desktopHome.resolve("environment"),
    desktopHome.resolve("browser"),
    desktopHome.resolve("tools"),
    desktopHome.resolve("plugins"),
    desktopHome.resolve("profiles"),
    desktopHome.resolve("supervisor"),
    desktopHome.resolve("logs"),
    desktopHome.resolve("state"),
    desktopHome.resolve("work"),
  ).forEach(File::mkdirs)

  copyDesktopFile(engineManifestFile, desktopHome.resolve("engine/manifest.json"))
  copyDesktopFile(environmentManifestFile, desktopHome.resolve("environment/manifest.json"))
  copyDesktopFile(browserManifestFile, desktopHome.resolve("browser/manifest.json"))
  copyDesktopFile(toolsManifestFile, desktopHome.resolve("tools/manifest.json"))
  copyDesktopFile(pluginsManifestFile, desktopHome.resolve("plugins/manifest.json"))
  copyDesktopFile(supervisorManifestFile, desktopHome.resolve("supervisor/manifest.json"))
  copyDesktopFile(selectedProfile.second, desktopHome.resolve("profiles/${selectedProfile.first.profileId}.json"))

  val now = Instant.now().toString()
  val activeProfilePayload =
    buildJsonObject {
      put("profileId", JsonPrimitive(selectedProfile.first.profileId))
      put("displayName", JsonPrimitive(selectedProfile.first.displayName ?: selectedProfile.first.profileId))
      selectedProfile.first.engineId?.let { put("engineId", JsonPrimitive(it)) }
      selectedProfile.first.environmentId?.let { put("environmentId", JsonPrimitive(it)) }
      selectedProfile.first.browserId?.let { put("browserId", JsonPrimitive(it)) }
      selectedProfile.first.toolsetId?.let { put("toolsetId", JsonPrimitive(it)) }
      selectedProfile.first.pluginSetId?.let { put("pluginSetId", JsonPrimitive(it)) }
      selectedProfile.first.supervisorId?.let { put("supervisorId", JsonPrimitive(it)) }
      put(
        "requiredDomains",
        buildJsonArray {
          selectedProfile.first.requiredDomains.forEach { add(JsonPrimitive(it)) }
        },
      )
      put(
        "runtimeHomeDependencies",
        buildJsonArray {
          selectedProfile.first.runtimeHomeDependencies.forEach { add(JsonPrimitive(it)) }
        },
      )
      put(
        "defaultCommands",
        buildJsonArray {
          selectedProfile.first.defaultCommands.forEach { add(JsonPrimitive(it)) }
        },
      )
      put("materializedAt", JsonPrimitive(now))
      put("podVersion", JsonPrimitive(manifestVersion))
    }
  desktopHome.resolve("profiles/active-profile.json").writeText("${activeProfilePayload}\n", Charsets.UTF_8)

  val previousState =
    desktopHome.resolve("state/$embeddedDesktopStateFileName").takeIf { it.isFile }?.let(::readDesktopJsonObjectOrNull)
  val executionCount =
    (previousState?.get("executionCount") as? JsonPrimitive)?.content?.toIntOrNull()?.plus(1) ?: 1
  val statePayload =
    buildJsonObject {
      put("status", JsonPrimitive("ok"))
      put("profileId", JsonPrimitive(selectedProfile.first.profileId))
      put("podVersion", JsonPrimitive(manifestVersion))
      put("executionCount", JsonPrimitive(executionCount))
      put("executedAt", JsonPrimitive(now))
      put("desktopHomePath", JsonPrimitive(desktopHomeDisplayPath(manifestVersion)))
      put("activeProfilePath", JsonPrimitive(desktopHomeDisplayPath(manifestVersion, "profiles/active-profile.json")))
      put("engineManifestPath", JsonPrimitive(desktopHomeDisplayPath(manifestVersion, "engine/manifest.json")))
      put("environmentManifestPath", JsonPrimitive(desktopHomeDisplayPath(manifestVersion, "environment/manifest.json")))
      put("browserManifestPath", JsonPrimitive(desktopHomeDisplayPath(manifestVersion, "browser/manifest.json")))
      put("toolsManifestPath", JsonPrimitive(desktopHomeDisplayPath(manifestVersion, "tools/manifest.json")))
      put("pluginsManifestPath", JsonPrimitive(desktopHomeDisplayPath(manifestVersion, "plugins/manifest.json")))
      put("supervisorManifestPath", JsonPrimitive(desktopHomeDisplayPath(manifestVersion, "supervisor/manifest.json")))
      put("profileCount", JsonPrimitive(profiles.size))
      put("browserFlowCount", JsonPrimitive(desktopJsonArray(readDesktopJsonObjectOrNull(browserManifestFile), "authFlowIds").size))
      put("toolCount", JsonPrimitive(desktopJsonArray(readDesktopJsonObjectOrNull(toolsManifestFile), "toolIds").size))
      put("pluginCount", JsonPrimitive(desktopJsonArray(readDesktopJsonObjectOrNull(pluginsManifestFile), "pluginIds").size))
    }
  desktopHome.resolve("state/$embeddedDesktopStateFileName").writeText("${statePayload}\n", Charsets.UTF_8)
  desktopHome.resolve("logs/$embeddedDesktopLogFileName").appendText(
    "$now profile=${selectedProfile.first.profileId} pod=$manifestVersion home=${desktopHomeDisplayPath(manifestVersion)}\n",
    Charsets.UTF_8,
  )

  return EmbeddedRuntimePodDesktopMaterializeResult(
    ok = true,
    payload =
      buildJsonObject {
        put("profileId", JsonPrimitive(selectedProfile.first.profileId))
        put("displayName", JsonPrimitive(selectedProfile.first.displayName ?: selectedProfile.first.profileId))
        put("embeddedPodVersion", JsonPrimitive(manifestVersion))
        put("desktopHomeCreated", JsonPrimitive(!desktopHomeExisted))
        put("desktopHomeReady", JsonPrimitive(true))
        put("executionCount", JsonPrimitive(executionCount))
        put("desktopHomePath", JsonPrimitive(desktopHomeDisplayPath(manifestVersion)))
        put("stateFilePath", JsonPrimitive(desktopHomeDisplayPath(manifestVersion, "state/$embeddedDesktopStateFileName")))
        put("logFilePath", JsonPrimitive(desktopHomeDisplayPath(manifestVersion, "logs/$embeddedDesktopLogFileName")))
        put("activeProfilePath", JsonPrimitive(desktopHomeDisplayPath(manifestVersion, "profiles/active-profile.json")))
        put(
          "requiredDomains",
          buildJsonArray {
            selectedProfile.first.requiredDomains.forEach { add(JsonPrimitive(it)) }
          },
        )
        put(
          "defaultCommands",
          buildJsonArray {
            selectedProfile.first.defaultCommands.forEach { add(JsonPrimitive(it)) }
          },
        )
        put("state", statePayload)
      },
  )
}

private fun loadDesktopStageManifest(file: File): EmbeddedDesktopStageManifest? =
  runCatching {
    embeddedRuntimeDesktopJson.decodeFromString(EmbeddedDesktopStageManifest.serializer(), file.readText(Charsets.UTF_8))
  }.getOrNull()

private fun loadDesktopProfileDescriptor(file: File): EmbeddedDesktopProfileDescriptor? =
  runCatching {
    embeddedRuntimeDesktopJson.decodeFromString(
      EmbeddedDesktopProfileDescriptor.serializer(),
      file.readText(Charsets.UTF_8),
    )
  }.getOrNull()

private fun readDesktopJsonObjectOrNull(file: File): JsonObject? =
  runCatching { embeddedRuntimeDesktopJson.parseToJsonElement(file.readText(Charsets.UTF_8)) as? JsonObject }.getOrNull()

private fun resolveDesktopRelativePath(rootDir: File, relativePath: String): File? {
  val normalized = relativePath.replace('\\', '/').trim().trimStart('/').removePrefix("./")
  if (normalized.isEmpty() || normalized.contains("../")) return null
  val candidate = File(rootDir, normalized).canonicalFile
  val rootPath = rootDir.canonicalPath
  return if (candidate.path == rootPath || candidate.path.startsWith("$rootPath${File.separator}")) candidate else null
}

private fun embeddedRuntimeDesktopPodInstallRoot(context: Context): File =
  context.filesDir.resolve("openclaw/embedded-runtime-pod")

private fun embeddedDesktopHomeRoot(context: Context): File =
  context.filesDir.resolve("openclaw/embedded-desktop-home")

private fun desktopHomeDisplayPath(
  version: String,
  relativePath: String? = null,
): String {
  val suffix = relativePath?.trim()?.replace('\\', '/')?.trimStart('/')?.takeIf { it.isNotEmpty() }
  return if (suffix == null) {
    "$embeddedDesktopHomeRootDisplayPath/$version"
  } else {
    "$embeddedDesktopHomeRootDisplayPath/$version/$suffix"
  }
}

private fun copyDesktopFile(source: File, target: File) {
  target.parentFile?.mkdirs()
  target.writeBytes(source.readBytes())
}

private fun desktopPrimitiveContent(
  payload: JsonObject?,
  key: String,
): String? = (payload?.get(key) as? JsonPrimitive)?.content?.takeUnless { it == "null" }

private fun desktopJsonArray(
  payload: JsonObject?,
  key: String,
): List<JsonPrimitive> {
  val array = payload?.get(key) as? JsonArray ?: return emptyList()
  return array.mapNotNull { it as? JsonPrimitive }
}
