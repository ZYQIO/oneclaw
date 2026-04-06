package ai.openclaw.app

import android.content.Context
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

private const val embeddedRuntimePodManifestAssetPath = "embedded-runtime-pod/manifest.json"
private const val embeddedRuntimePodLayoutAssetPath = "embedded-runtime-pod/layout.json"
private const val embeddedRuntimePodInstallRootDisplayPath = "filesDir/openclaw/embedded-runtime-pod"
private const val embeddedRuntimePodWorkspaceReadDefaultChars = 4000
private const val embeddedRuntimePodWorkspaceReadMaxChars = 16000
private const val embeddedRuntimeDesktopMainlineBranch = "android-desktop-runtime-mainline-20260403"

private val embeddedRuntimePodJson = Json { ignoreUnknownKeys = true }
private val embeddedRuntimePodTextExtensions = setOf("json", "md", "txt", "yaml", "yml")
private val embeddedRuntimeBootstrapHelperCommands =
  listOf(
    "pod.health",
    "pod.manifest.describe",
    "pod.browser.describe",
    "pod.workspace.scan",
    "pod.workspace.read",
  )
private val embeddedRuntimeExecutionCommands =
  listOf(
    "pod.runtime.execute",
  )
private val embeddedRuntimeBrowserLaunchCommands =
  listOf(
    "pod.browser.auth.start",
  )
private val embeddedRuntimeDesktopMaterializeCommands =
  listOf(
    "pod.desktop.materialize",
  )

@Serializable
private data class EmbeddedRuntimePodAssetManifestStage(
  val name: String,
  val destination: String,
  val assetPath: String? = null,
)

@Serializable
private data class EmbeddedRuntimePodAssetManifestFile(
  val stage: String,
  val relativePath: String,
  val assetPath: String,
  val sizeBytes: Long,
  val sha256: String,
)

@Serializable
private data class EmbeddedRuntimePodAssetManifest(
  val schemaVersion: Int = 1,
  val podId: String,
  val podName: String? = null,
  val version: String,
  val description: String? = null,
  val assetManifestPath: String = embeddedRuntimePodManifestAssetPath,
  val assetLayoutPath: String = embeddedRuntimePodLayoutAssetPath,
  val assetBasePath: String? = null,
  val stageCount: Int? = null,
  val fileCount: Int? = null,
  val stages: List<EmbeddedRuntimePodAssetManifestStage> = emptyList(),
  val files: List<EmbeddedRuntimePodAssetManifestFile> = emptyList(),
)

data class EmbeddedRuntimePodInspection(
  val available: Boolean,
  val ready: Boolean,
  val reason: String,
  val assetManifestPath: String = embeddedRuntimePodManifestAssetPath,
  val assetLayoutPath: String = embeddedRuntimePodLayoutAssetPath,
  val assetManifestPresent: Boolean = false,
  val installRootExists: Boolean = false,
  val installedVersions: List<String> = emptyList(),
  val manifestVersion: String? = null,
  val manifestStageCount: Int? = null,
  val manifestFileCount: Int? = null,
  val manifestVersionExtracted: Boolean = false,
  val verifiedFileCount: Int = 0,
) {
  fun toJson(): JsonObject {
    return buildJsonObject {
      put("available", JsonPrimitive(available))
      put("ready", JsonPrimitive(ready))
      put("reason", JsonPrimitive(reason))
      put("assetManifestPath", JsonPrimitive(assetManifestPath))
      put("assetLayoutPath", JsonPrimitive(assetLayoutPath))
      put("assetManifestPresent", JsonPrimitive(assetManifestPresent))
      put("installRoot", JsonPrimitive(embeddedRuntimePodInstallRootDisplayPath))
      put("installRootExists", JsonPrimitive(installRootExists))
      put("installedVersionCount", JsonPrimitive(installedVersions.size))
      put(
        "installedVersions",
        buildJsonArray {
          installedVersions.forEach { version ->
            add(JsonPrimitive(version))
          }
        },
      )
      manifestVersion?.let { put("manifestVersion", JsonPrimitive(it)) }
      manifestStageCount?.let { put("manifestStageCount", JsonPrimitive(it)) }
      manifestFileCount?.let { put("manifestFileCount", JsonPrimitive(it)) }
      put("manifestVersionExtracted", JsonPrimitive(manifestVersionExtracted))
      put("verifiedFileCount", JsonPrimitive(verifiedFileCount))
    }
  }
}

data class EmbeddedRuntimePodWorkspaceReadResult(
  val ok: Boolean,
  val payload: JsonObject? = null,
  val code: String? = null,
  val message: String? = null,
)

data class EmbeddedRuntimePodManifestDescribeResult(
  val ok: Boolean,
  val payload: JsonObject? = null,
  val code: String? = null,
  val message: String? = null,
)

data class EmbeddedRuntimePodRuntimeDescribeResult(
  val ok: Boolean,
  val payload: JsonObject? = null,
  val code: String? = null,
  val message: String? = null,
)

fun ensureEmbeddedRuntimePodInstalled(context: Context): EmbeddedRuntimePodInspection {
  val manifest = loadEmbeddedRuntimePodManifest(context) ?: return inspectEmbeddedRuntimePod(context)
  val version = manifest.version.trim()
  if (version.isEmpty()) return inspectEmbeddedRuntimePod(context)
  val versionDir = embeddedRuntimePodInstallRoot(context).resolve(version)
  val existing = inspectEmbeddedRuntimePod(context)
  if (existing.ready) return existing

  versionDir.deleteRecursively()
  versionDir.mkdirs()

  copyAssetIntoFile(context, manifest.assetManifestPath, versionDir.resolve("manifest.json"))
  copyAssetIntoFile(context, manifest.assetLayoutPath, versionDir.resolve("layout.json"))

  manifest.files.forEach { entry ->
    val target = resolvePodRelativePath(versionDir, entry.relativePath) ?: return inspectEmbeddedRuntimePod(context)
    copyAssetIntoFile(context, entry.assetPath, target)
    target.setReadOnly()
  }

  return inspectEmbeddedRuntimePod(context)
}

fun inspectEmbeddedRuntimePod(context: Context): EmbeddedRuntimePodInspection {
  val installRoot = embeddedRuntimePodInstallRoot(context)
  val installedVersions =
    installRoot
      .listFiles()
      ?.filter { it.isDirectory }
      ?.map { it.name }
      ?.sorted()
      .orEmpty()

  val manifest = loadEmbeddedRuntimePodManifest(context)
  if (manifest == null) {
    return EmbeddedRuntimePodInspection(
      available = false,
      ready = false,
      reason = "pod_assets_missing",
      assetManifestPresent = false,
      installRootExists = installRoot.isDirectory,
      installedVersions = installedVersions,
    )
  }

  val manifestVersion = manifest.version.trim().takeIf { it.isNotEmpty() }
  val versionDir = manifestVersion?.let { embeddedRuntimePodInstallRoot(context).resolve(it) }
  val manifestVersionExtracted = versionDir?.isDirectory == true
  val verifiedFileCount =
    manifest.files.count { entry ->
      val target = versionDir?.let { resolvePodRelativePath(it, entry.relativePath) } ?: return@count false
      target.isFile && sha256(target) == entry.sha256
    }

  val ready = manifestVersion != null && manifestVersionExtracted && verifiedFileCount == manifest.files.size
  val reason =
    when {
      manifestVersion == null -> "manifest_missing_version"
      !installRoot.isDirectory -> "not_extracted"
      !manifestVersionExtracted -> "version_not_extracted"
      verifiedFileCount != manifest.files.size -> "verification_incomplete"
      else -> "ready"
    }

  return EmbeddedRuntimePodInspection(
    available = true,
    ready = ready,
    reason = reason,
    assetManifestPresent = true,
    installRootExists = installRoot.isDirectory,
    installedVersions = installedVersions,
    manifestVersion = manifestVersion,
    manifestStageCount = manifest.stageCount ?: manifest.stages.size,
    manifestFileCount = manifest.fileCount ?: manifest.files.size,
    manifestVersionExtracted = manifestVersionExtracted,
    verifiedFileCount = verifiedFileCount,
  )
}

fun scanEmbeddedRuntimePodWorkspace(
  context: Context,
  limit: Int = 20,
  query: String? = null,
): JsonObject {
  val inspection = inspectEmbeddedRuntimePod(context)
  val manifestVersion = inspection.manifestVersion
  val normalizedLimit = limit.coerceIn(1, 50)
  val normalizedQuery = query?.trim()?.takeIf { it.isNotEmpty() }
  val workspaceRoot = manifestVersion?.let { embeddedRuntimePodInstallRoot(context).resolve(it).resolve("workspace") }
  val workspacePresent = workspaceRoot?.isDirectory == true
  val stageManifest = workspaceRoot?.resolve("manifest.json")?.takeIf { it.isFile }?.let(::readJsonObjectOrNull)
  val contentIndex = workspaceRoot?.resolve("content-index.json")?.takeIf { it.isFile }?.let(::readJsonObjectOrNull)
  val allFiles =
    if (workspacePresent) {
      walkFiles(workspaceRoot)
        .filter { it.isFile }
        .sortedBy { toPodRelativePath(workspaceRoot, it) }
    } else {
      emptyList()
    }
  val matchedFiles =
    allFiles.filter { file ->
      normalizedQuery == null || toPodRelativePath(workspaceRoot!!, file).contains(normalizedQuery, ignoreCase = true)
    }
  val selectedFiles = matchedFiles.take(normalizedLimit)

  return buildJsonObject {
    put("workspaceStagePath", JsonPrimitive("workspace"))
    put("workspaceStagePresent", JsonPrimitive(workspacePresent))
    put("stageManifestPresent", JsonPrimitive(stageManifest != null))
    put("contentIndexPresent", JsonPrimitive(contentIndex != null))
    put("limit", JsonPrimitive(normalizedLimit))
    normalizedQuery?.let { put("query", JsonPrimitive(it)) }
    put("matchedFileCount", JsonPrimitive(matchedFiles.size))
    put("returnedFileCount", JsonPrimitive(selectedFiles.size))
    put("truncated", JsonPrimitive(matchedFiles.size > selectedFiles.size))
    stageManifest?.let { put("stageManifest", it) }
    contentIndex?.let { put("contentIndex", it) }
    put(
      "files",
      buildJsonArray {
        selectedFiles.forEach { file ->
          add(describeWorkspaceFile(workspaceRoot!!, file))
        }
      },
    )
  }
}

fun readEmbeddedRuntimePodWorkspaceFile(
  context: Context,
  relativePath: String,
  maxChars: Int = embeddedRuntimePodWorkspaceReadDefaultChars,
): EmbeddedRuntimePodWorkspaceReadResult {
  val inspection = inspectEmbeddedRuntimePod(context)
  val manifestVersion = inspection.manifestVersion
  if (!inspection.ready || manifestVersion == null) {
    return EmbeddedRuntimePodWorkspaceReadResult(
      ok = false,
      code = "POD_NOT_READY",
      message = "POD_NOT_READY: workspace stage not available",
    )
  }

  val workspaceRoot = embeddedRuntimePodInstallRoot(context).resolve(manifestVersion).resolve("workspace")
  if (!workspaceRoot.isDirectory) {
    return EmbeddedRuntimePodWorkspaceReadResult(
      ok = false,
      code = "POD_NOT_READY",
      message = "POD_NOT_READY: workspace stage not available",
    )
  }

  val normalizedRequest = normalizeWorkspaceRelativePath(relativePath)
  if (normalizedRequest.isEmpty()) {
    return EmbeddedRuntimePodWorkspaceReadResult(
      ok = false,
      code = "INVALID_REQUEST",
      message = "INVALID_REQUEST: path required",
    )
  }
  val target =
    resolveWorkspaceRelativePath(workspaceRoot, normalizedRequest)
      ?: return EmbeddedRuntimePodWorkspaceReadResult(
        ok = false,
        code = "INVALID_REQUEST",
        message = "INVALID_REQUEST: path escapes workspace",
      )
  if (target == workspaceRoot || !target.exists()) {
    return EmbeddedRuntimePodWorkspaceReadResult(
      ok = false,
      code = "NOT_FOUND",
      message = "NOT_FOUND: workspace file not found",
    )
  }
  if (!target.isFile) {
    return EmbeddedRuntimePodWorkspaceReadResult(
      ok = false,
      code = "INVALID_REQUEST",
      message = "INVALID_REQUEST: readable file path required",
    )
  }
  if (!isWorkspaceTextFile(target)) {
    return EmbeddedRuntimePodWorkspaceReadResult(
      ok = false,
      code = "UNSUPPORTED_MEDIA_TYPE",
      message = "UNSUPPORTED_MEDIA_TYPE: workspace read supports only text files",
    )
  }

  val text =
    runCatching { target.readText(Charsets.UTF_8) }.getOrElse {
      return EmbeddedRuntimePodWorkspaceReadResult(
        ok = false,
        code = "UNAVAILABLE",
        message = "UNAVAILABLE: failed to read workspace file",
      )
    }
  val normalizedMaxChars = maxChars.coerceIn(1, embeddedRuntimePodWorkspaceReadMaxChars)
  val truncated = text.length > normalizedMaxChars
  val stageManifest = workspaceRoot.resolve("manifest.json").takeIf { it.isFile }?.let(::readJsonObjectOrNull)
  val contentIndex = workspaceRoot.resolve("content-index.json").takeIf { it.isFile }?.let(::readJsonObjectOrNull)
  val relative = toPodRelativePath(workspaceRoot, target)
  val indexedDocument = indexedWorkspaceDocument(contentIndex = contentIndex, relativePath = relative)

  return EmbeddedRuntimePodWorkspaceReadResult(
    ok = true,
    payload =
      buildJsonObject {
        put("workspaceStagePath", JsonPrimitive("workspace"))
        put("workspaceStagePresent", JsonPrimitive(true))
        put("stageManifestPresent", JsonPrimitive(stageManifest != null))
        put("contentIndexPresent", JsonPrimitive(contentIndex != null))
        put("requestedPath", JsonPrimitive(relativePath.trim()))
        put("relativePath", JsonPrimitive(relative))
        put("maxChars", JsonPrimitive(normalizedMaxChars))
        put("sizeBytes", JsonPrimitive(target.length()))
        put("sha256", JsonPrimitive(sha256(target)))
        put("isManifest", JsonPrimitive(relative == "manifest.json"))
        put("isContentIndex", JsonPrimitive(relative == "content-index.json"))
        put("text", JsonPrimitive(text.take(normalizedMaxChars)))
        put("textTruncated", JsonPrimitive(truncated))
        stageManifest?.let { put("stageManifest", it) }
        contentIndex?.let { put("contentIndex", it) }
        indexedDocument?.let { put("document", it) }
      },
  )
}

fun describeEmbeddedRuntimePodManifest(
  context: Context,
): EmbeddedRuntimePodManifestDescribeResult {
  val inspection = inspectEmbeddedRuntimePod(context)
  val assetManifest = readAssetJsonObjectOrNull(context, embeddedRuntimePodManifestAssetPath)
    ?: return EmbeddedRuntimePodManifestDescribeResult(
      ok = false,
      code = "NOT_FOUND",
      message = "NOT_FOUND: embedded pod manifest asset missing",
    )
  val assetLayout = readAssetJsonObjectOrNull(context, embeddedRuntimePodLayoutAssetPath)
  val manifestVersion =
    inspection.manifestVersion
      ?: primitiveContent(assetManifest, "version")?.trim()?.takeIf { it.isNotEmpty() }
  val versionDir = manifestVersion?.let { embeddedRuntimePodInstallRoot(context).resolve(it) }
  val installedManifest = versionDir?.resolve("manifest.json")?.takeIf { it.isFile }?.let(::readJsonObjectOrNull)
  val installedLayout = versionDir?.resolve("layout.json")?.takeIf { it.isFile }?.let(::readJsonObjectOrNull)
  val selectedManifest = installedManifest ?: assetManifest
  val selectedLayout = installedLayout ?: assetLayout
  val stages = jsonObjectArray(selectedManifest["stages"])
  val directories = jsonObjectArray(selectedLayout?.get("directories"))
  val files = jsonObjectArray(selectedManifest["files"]).ifEmpty { jsonObjectArray(selectedLayout?.get("files")) }
  val fileStageCounts = LinkedHashMap<String, Int>()
  files.forEach { file ->
    val stageName = primitiveContent(file, "stage")?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
    fileStageCounts[stageName] = (fileStageCounts[stageName] ?: 0) + 1
  }
  val stageNames =
    stages.mapNotNull { stage ->
      primitiveContent(stage, "name")?.trim()?.takeIf { it.isNotEmpty() }
    }
  val stageDestinations =
    stages.mapNotNull { stage ->
      primitiveContent(stage, "destination")?.trim()?.takeIf { it.isNotEmpty() }
    }
  val workspaceDeclared =
    stages.any { stage ->
      primitiveContent(stage, "name") == "workspace" || primitiveContent(stage, "destination") == "workspace"
    }
  val workspaceInstalled = versionDir?.resolve("workspace")?.isDirectory == true

  return EmbeddedRuntimePodManifestDescribeResult(
    ok = true,
    payload =
      buildJsonObject {
        put("assetManifestPresent", JsonPrimitive(true))
        put("assetLayoutPresent", JsonPrimitive(assetLayout != null))
        put("installedManifestPresent", JsonPrimitive(installedManifest != null))
        put("installedLayoutPresent", JsonPrimitive(installedLayout != null))
        put("manifestSource", JsonPrimitive(if (installedManifest != null) "installed" else "bundled"))
        put(
          "layoutSource",
          JsonPrimitive(
            when {
              installedLayout != null -> "installed"
              assetLayout != null -> "bundled"
              else -> "missing"
            },
          ),
        )
        put("stageCount", JsonPrimitive(stages.size))
        put("directoryCount", JsonPrimitive(directories.size))
        put("fileCount", JsonPrimitive(files.size))
        put("workspaceStageDeclared", JsonPrimitive(workspaceDeclared))
        put("workspaceStageInstalled", JsonPrimitive(workspaceInstalled))
        put(
          "stageNames",
          buildJsonArray {
            stageNames.forEach { add(JsonPrimitive(it)) }
          },
        )
        put(
          "stageDestinations",
          buildJsonArray {
            stageDestinations.forEach { add(JsonPrimitive(it)) }
          },
        )
        put(
          "fileStageCounts",
          buildJsonObject {
            fileStageCounts.forEach { (stageName, count) ->
              put(stageName, JsonPrimitive(count))
            }
          },
        )
        put("podManifest", selectedManifest)
        selectedLayout?.let { put("podLayout", it) }
      },
  )
}

fun describeEmbeddedRuntimeDesktopRuntime(
  context: Context,
): EmbeddedRuntimePodRuntimeDescribeResult {
  val inspection = inspectEmbeddedRuntimePod(context)
  val carrier = inspectEmbeddedRuntimeCarrier(context = context, manifestVersion = inspection.manifestVersion)
  val browser = inspectEmbeddedRuntimeBrowserLane(context = context, manifestVersion = inspection.manifestVersion)
  val desktop = inspectEmbeddedRuntimeDesktopEnvironment(context = context, manifestVersion = inspection.manifestVersion)
  val desktopReplay = inspectEmbeddedRuntimeDesktopProfileReplay(context = context, manifestVersion = inspection.manifestVersion)
  val engineIntegrated =
    carrier.runtimeStageInstalled && carrier.runtimeStageManifestPresent && carrier.runtimeEngineManifestPresent && carrier.runtimeTaskCount > 0
  val environmentIntegrated = carrier.runtimeStageInstalled
  val browserIntegrated =
    browser.browserStageInstalled && browser.browserStageManifestPresent && browser.browserAuthFlowCount > 0
  val toolsIntegrated =
    carrier.toolkitStageInstalled &&
      carrier.toolkitStageManifestPresent &&
      carrier.toolkitCommandPolicyPresent &&
      carrier.toolDescriptorCount > 0 &&
      carrier.runtimeToolTaskIds.isNotEmpty()
  val pluginsIntegrated =
    desktop.pluginsManifestPresent &&
      desktop.pluginCount > 0 &&
      carrier.runtimePluginTaskIds.isNotEmpty()
  val engineStatus =
    when {
      engineIntegrated -> "partial_bootstrap"
      else -> "missing"
    }
  val environmentStatus =
    when {
      carrier.runtimeHomeReady -> "landed_bootstrap"
      environmentIntegrated -> "partial_bootstrap"
      else -> "missing"
    }
  val toolsStatus =
    when {
      carrier.runtimeToolExecutionStateCount > 0 -> "landed_bootstrap"
      toolsIntegrated -> "partial_bootstrap"
      carrier.toolkitStageInstalled || carrier.toolDescriptorCount > 0 -> "bootstrap_present"
      else -> "missing"
    }
  val browserStatus =
    when {
      browserIntegrated && browser.authInProgress -> "auth_in_progress"
      browserIntegrated && browser.browserReplayReady -> "landed_bootstrap"
      browserIntegrated -> "partial_bootstrap"
      browser.browserStageInstalled || browser.browserAuthFlowCount > 0 -> "bootstrap_present"
      else -> "missing"
    }
  val pluginsStatus =
    when {
      carrier.runtimePluginExecutionStateCount > 0 -> "landed_bootstrap"
      pluginsIntegrated -> "partial_bootstrap"
      desktop.pluginsManifestPresent || carrier.runtimePluginTaskIds.isNotEmpty() -> "bootstrap_present"
      else -> "missing"
    }
  val desktopStatus =
    when {
      desktop.desktopHomeReady -> "landed_bootstrap"
      desktop.desktopBundleReady -> "packaged_bundle"
      desktop.desktopStageInstalled || desktop.profileCount > 0 -> "partial_bootstrap"
      else -> "missing"
    }
  val missingDomains = buildList {
    if (!desktop.desktopBundleReady) add("desktopEnvironment")
    if (!engineIntegrated) add("engine")
    if (!environmentIntegrated) add("environment")
    if (!browserIntegrated) add("browser")
    if (!toolsIntegrated) add("tools")
    if (!pluginsIntegrated) add("plugins")
  }
  val mainlineStatus =
    when {
      inspection.ready &&
        desktop.desktopHomeReady &&
        carrier.runtimeHomeReady &&
        toolsIntegrated &&
        browserIntegrated &&
        browser.browserReplayReady &&
        browser.authCredentialPresent &&
        carrier.runtimePluginExecutionStateCount > 0 &&
        desktopReplay.processSupervisorLoopReady -> "process_runtime_supervisor_loop_bootstrapped"
      inspection.ready &&
        desktop.desktopHomeReady &&
        carrier.runtimeHomeReady &&
        toolsIntegrated &&
        browserIntegrated &&
        browser.browserReplayReady &&
        browser.authCredentialPresent &&
        carrier.runtimePluginExecutionStateCount > 0 &&
        desktopReplay.processDetachedLaunchReady -> "process_runtime_detached_launch_bootstrapped"
      inspection.ready &&
        desktop.desktopHomeReady &&
        carrier.runtimeHomeReady &&
        toolsIntegrated &&
        browserIntegrated &&
        browser.browserReplayReady &&
        browser.authCredentialPresent &&
        carrier.runtimePluginExecutionStateCount > 0 &&
        desktopReplay.processRecoveryReady -> "process_runtime_recovery_bootstrapped"
      inspection.ready &&
        desktop.desktopHomeReady &&
        carrier.runtimeHomeReady &&
        toolsIntegrated &&
        browserIntegrated &&
        browser.browserReplayReady &&
        browser.authCredentialPresent &&
        carrier.runtimePluginExecutionStateCount > 0 &&
        desktopReplay.processObservationReady -> "process_runtime_observation_bootstrapped"
      inspection.ready &&
        desktop.desktopHomeReady &&
        carrier.runtimeHomeReady &&
        toolsIntegrated &&
        browserIntegrated &&
        browser.browserReplayReady &&
        browser.authCredentialPresent &&
        carrier.runtimePluginExecutionStateCount > 0 &&
        desktopReplay.processSupervisionReady -> "process_runtime_supervision_bootstrapped"
      inspection.ready &&
        desktop.desktopHomeReady &&
        carrier.runtimeHomeReady &&
        toolsIntegrated &&
        browserIntegrated &&
        browser.browserReplayReady &&
        browser.authCredentialPresent &&
        carrier.runtimePluginExecutionStateCount > 0 &&
        desktopReplay.processActivationReady -> "process_runtime_activation_bootstrapped"
      inspection.ready &&
        desktop.desktopHomeReady &&
        carrier.runtimeHomeReady &&
        toolsIntegrated &&
        browserIntegrated &&
        browser.browserReplayReady &&
        browser.authCredentialPresent &&
        carrier.runtimePluginExecutionStateCount > 0 &&
        desktopReplay.processModelReady -> "process_model_bootstrapped"
      inspection.ready &&
        desktop.desktopHomeReady &&
        carrier.runtimeHomeReady &&
        toolsIntegrated &&
        browserIntegrated &&
        browser.browserReplayReady &&
        browser.authCredentialPresent &&
        carrier.runtimePluginExecutionStateCount > 0 -> "plugin_lane_replayed"
      inspection.ready &&
        desktop.desktopHomeReady &&
        carrier.runtimeHomeReady &&
        toolsIntegrated &&
        browserIntegrated &&
        browser.browserReplayReady &&
        browser.authCredentialPresent -> "desktop_home_configured"
      inspection.ready && desktop.desktopHomeReady -> "desktop_home_ready"
      inspection.ready && desktop.desktopBundleReady -> "desktop_bundle_ready"
      inspection.ready &&
        carrier.runtimeHomeReady &&
        toolsIntegrated &&
        browserIntegrated &&
        browser.browserReplayReady &&
        browser.authCredentialPresent -> "browser_lane_configured"
      inspection.ready &&
        carrier.runtimeHomeReady &&
        toolsIntegrated &&
        browserIntegrated &&
        browser.browserReplayReady -> "browser_lane_replayed"
      inspection.ready && carrier.runtimeHomeReady && toolsIntegrated && browserIntegrated -> "browser_lane_ready"
      inspection.ready && carrier.runtimeHomeReady && toolsIntegrated && carrier.runtimeToolExecutionStateCount > 0 -> "tool_lane_replayed"
      inspection.ready && carrier.runtimeHomeReady && toolsIntegrated -> "tool_lane_ready"
      inspection.ready && carrier.runtimeHomeReady && engineIntegrated -> "runtime_execute_ready"
      inspection.ready && engineIntegrated -> "carrier_ready"
      inspection.ready -> "bootstrap_ready"
      inspection.available -> "bootstrap_partial"
      else -> "bootstrap_missing"
    }

  return EmbeddedRuntimePodRuntimeDescribeResult(
    ok = true,
    payload =
      buildJsonObject {
        put("mainlineBranch", JsonPrimitive(embeddedRuntimeDesktopMainlineBranch))
        put("distributionLane", JsonPrimitive("internal_or_sideload_first"))
        put("mainlineStatus", JsonPrimitive(mainlineStatus))
        put("fullDesktopRuntimeBundled", JsonPrimitive(false))
        put("desktopEnvironmentBundled", JsonPrimitive(desktop.desktopBundleReady))
        put("embeddedPodReady", JsonPrimitive(inspection.ready))
        inspection.manifestVersion?.let { put("embeddedPodVersion", JsonPrimitive(it)) }
        put("desktopStageInstalled", JsonPrimitive(desktop.desktopStageInstalled))
        put("desktopStageManifestPresent", JsonPrimitive(desktop.desktopStageManifestPresent))
        put("desktopHomeReady", JsonPrimitive(desktop.desktopHomeReady))
        put("desktopHomeExists", JsonPrimitive(desktop.desktopHomeExists))
        put("desktopBundleReady", JsonPrimitive(desktop.desktopBundleReady))
        put("desktopEngineManifestPresent", JsonPrimitive(desktop.engineManifestPresent))
        put("desktopEnvironmentManifestPresent", JsonPrimitive(desktop.environmentManifestPresent))
        put("desktopBrowserManifestPresent", JsonPrimitive(desktop.browserManifestPresent))
        put("desktopToolsManifestPresent", JsonPrimitive(desktop.toolsManifestPresent))
        put("desktopPluginsManifestPresent", JsonPrimitive(desktop.pluginsManifestPresent))
        put("desktopSupervisorManifestPresent", JsonPrimitive(desktop.supervisorManifestPresent))
        put("desktopBrowserFlowCount", JsonPrimitive(desktop.browserFlowCount))
        put("desktopToolCount", JsonPrimitive(desktop.toolCount))
        put("desktopPluginCount", JsonPrimitive(desktop.pluginCount))
        put("desktopProfileCount", JsonPrimitive(desktop.profileCount))
        put("desktopMaterializeStatePresent", JsonPrimitive(desktop.materializeStatePresent))
        put("desktopMaterializeLogPresent", JsonPrimitive(desktop.materializeLogPresent))
        desktop.activeProfileId?.let { put("desktopActiveProfileId", JsonPrimitive(it)) }
        put("desktopProfileReplayReady", JsonPrimitive(desktopReplay.replayReady))
        put("desktopProfileReplayStatePresent", JsonPrimitive(desktopReplay.statePresent))
        put("desktopProfileReplayResultPresent", JsonPrimitive(desktopReplay.resultPresent))
        desktopReplay.status?.let { put("desktopProfileReplayStatus", JsonPrimitive(it)) }
        put("desktopEnvironmentSupervisionReady", JsonPrimitive(desktopReplay.environmentSupervisionReady))
        put("desktopHealthReportPresent", JsonPrimitive(desktopReplay.healthReportPresent))
        desktopReplay.healthStatus?.let { put("desktopHealthStatus", JsonPrimitive(it)) }
        desktopReplay.healthReportPath?.let { put("desktopHealthReportPath", JsonPrimitive(it)) }
        put("desktopRestartContractPresent", JsonPrimitive(desktopReplay.restartContractPresent))
        desktopReplay.restartStatus?.let { put("desktopRestartStatus", JsonPrimitive(it)) }
        put("desktopRestartGeneration", JsonPrimitive(desktopReplay.restartGeneration))
        desktopReplay.restartContractPath?.let { put("desktopRestartContractPath", JsonPrimitive(it)) }
        put("desktopProcessModelReady", JsonPrimitive(desktopReplay.processModelReady))
        put("desktopProcessStatePresent", JsonPrimitive(desktopReplay.processStatePresent))
        desktopReplay.processStatus?.let { put("desktopProcessStatus", JsonPrimitive(it)) }
        desktopReplay.processStatePath?.let { put("desktopProcessStatePath", JsonPrimitive(it)) }
        desktopReplay.processSessionId?.let { put("desktopProcessSessionId", JsonPrimitive(it)) }
        put("desktopProcessBootstrapOnly", JsonPrimitive(desktopReplay.processBootstrapOnly))
        put("desktopProcessActivationReady", JsonPrimitive(desktopReplay.processActivationReady))
        put("desktopProcessActivationStatePresent", JsonPrimitive(desktopReplay.processActivationStatePresent))
        desktopReplay.processActivationStatus?.let {
          put("desktopProcessActivationStatus", JsonPrimitive(it))
        }
        desktopReplay.processActivationState?.let {
          put("desktopProcessActivationState", JsonPrimitive(it))
        }
        desktopReplay.processActivationStatePath?.let {
          put("desktopProcessActivationStatePath", JsonPrimitive(it))
        }
        put("desktopProcessActivationGeneration", JsonPrimitive(desktopReplay.processActivationGeneration))
        desktopReplay.processActivationBlockedReason?.let {
          put("desktopProcessActivationBlockedReason", JsonPrimitive(it))
        }
        put("desktopProcessActivationBootstrapOnly", JsonPrimitive(desktopReplay.processActivationBootstrapOnly))
        put("desktopProcessSupervisionReady", JsonPrimitive(desktopReplay.processSupervisionReady))
        put("desktopProcessSupervisionStatePresent", JsonPrimitive(desktopReplay.processSupervisionStatePresent))
        desktopReplay.processSupervisionStatus?.let {
          put("desktopProcessSupervisionStatus", JsonPrimitive(it))
        }
        desktopReplay.processSupervisionState?.let {
          put("desktopProcessSupervisionState", JsonPrimitive(it))
        }
        desktopReplay.processSupervisionStatePath?.let {
          put("desktopProcessSupervisionStatePath", JsonPrimitive(it))
        }
        put("desktopProcessSupervisionGeneration", JsonPrimitive(desktopReplay.processSupervisionGeneration))
        desktopReplay.processSupervisionHeartbeatAt?.let {
          put("desktopProcessSupervisionHeartbeatAt", JsonPrimitive(it))
        }
        desktopReplay.processSupervisionLeaseExpiresAt?.let {
          put("desktopProcessSupervisionLeaseExpiresAt", JsonPrimitive(it))
        }
        desktopReplay.processSupervisionBlockedReason?.let {
          put("desktopProcessSupervisionBlockedReason", JsonPrimitive(it))
        }
        put("desktopProcessSupervisionBootstrapOnly", JsonPrimitive(desktopReplay.processSupervisionBootstrapOnly))
        put("desktopProcessObservationReady", JsonPrimitive(desktopReplay.processObservationReady))
        put("desktopProcessObservationStatePresent", JsonPrimitive(desktopReplay.processObservationStatePresent))
        desktopReplay.processObservationStatus?.let {
          put("desktopProcessObservationStatus", JsonPrimitive(it))
        }
        desktopReplay.processObservationState?.let {
          put("desktopProcessObservationState", JsonPrimitive(it))
        }
        desktopReplay.processObservationStatePath?.let {
          put("desktopProcessObservationStatePath", JsonPrimitive(it))
        }
        put("desktopProcessObservationGeneration", JsonPrimitive(desktopReplay.processObservationGeneration))
        desktopReplay.processObservationObservedAt?.let {
          put("desktopProcessObservationObservedAt", JsonPrimitive(it))
        }
        put("desktopProcessObservationHeartbeatAgeSeconds", JsonPrimitive(desktopReplay.processObservationHeartbeatAgeSeconds))
        put("desktopProcessObservationLeaseRemainingSeconds", JsonPrimitive(desktopReplay.processObservationLeaseRemainingSeconds))
        desktopReplay.processObservationLeaseHealth?.let {
          put("desktopProcessObservationLeaseHealth", JsonPrimitive(it))
        }
        desktopReplay.processObservationRecoveryHint?.let {
          put("desktopProcessObservationRecoveryHint", JsonPrimitive(it))
        }
        put("desktopProcessObservationBootstrapOnly", JsonPrimitive(desktopReplay.processObservationBootstrapOnly))
        put("desktopProcessRecoveryReady", JsonPrimitive(desktopReplay.processRecoveryReady))
        put("desktopProcessRecoveryStatePresent", JsonPrimitive(desktopReplay.processRecoveryStatePresent))
        desktopReplay.processRecoveryStatus?.let {
          put("desktopProcessRecoveryStatus", JsonPrimitive(it))
        }
        desktopReplay.processRecoveryState?.let {
          put("desktopProcessRecoveryState", JsonPrimitive(it))
        }
        desktopReplay.processRecoveryStatePath?.let {
          put("desktopProcessRecoveryStatePath", JsonPrimitive(it))
        }
        put("desktopProcessRecoveryGeneration", JsonPrimitive(desktopReplay.processRecoveryGeneration))
        put("desktopProcessRecoveryActionCount", JsonPrimitive(desktopReplay.processRecoveryActionCount))
        desktopReplay.processRecoveryPrimaryAction?.let {
          put("desktopProcessRecoveryPrimaryAction", JsonPrimitive(it))
        }
        desktopReplay.processRecoveryReason?.let {
          put("desktopProcessRecoveryReason", JsonPrimitive(it))
        }
        put("desktopProcessRecoveryBootstrapOnly", JsonPrimitive(desktopReplay.processRecoveryBootstrapOnly))
        put("desktopProcessDetachedLaunchReady", JsonPrimitive(desktopReplay.processDetachedLaunchReady))
        put("desktopProcessDetachedLaunchStatePresent", JsonPrimitive(desktopReplay.processDetachedLaunchStatePresent))
        desktopReplay.processDetachedLaunchStatus?.let {
          put("desktopProcessDetachedLaunchStatus", JsonPrimitive(it))
        }
        desktopReplay.processDetachedLaunchState?.let {
          put("desktopProcessDetachedLaunchState", JsonPrimitive(it))
        }
        desktopReplay.processDetachedLaunchStatePath?.let {
          put("desktopProcessDetachedLaunchStatePath", JsonPrimitive(it))
        }
        put("desktopProcessDetachedLaunchGeneration", JsonPrimitive(desktopReplay.processDetachedLaunchGeneration))
        desktopReplay.processDetachedLaunchSessionId?.let {
          put("desktopProcessDetachedLaunchSessionId", JsonPrimitive(it))
        }
        desktopReplay.processDetachedLaunchCommand?.let {
          put("desktopProcessDetachedLaunchCommand", JsonPrimitive(it))
        }
        desktopReplay.processDetachedLaunchBlockedReason?.let {
          put("desktopProcessDetachedLaunchBlockedReason", JsonPrimitive(it))
        }
        put("desktopProcessDetachedLaunchBootstrapOnly", JsonPrimitive(desktopReplay.processDetachedLaunchBootstrapOnly))
        put("desktopProcessSupervisorLoopReady", JsonPrimitive(desktopReplay.processSupervisorLoopReady))
        put("desktopProcessSupervisorLoopStatePresent", JsonPrimitive(desktopReplay.processSupervisorLoopStatePresent))
        desktopReplay.processSupervisorLoopStatus?.let {
          put("desktopProcessSupervisorLoopStatus", JsonPrimitive(it))
        }
        desktopReplay.processSupervisorLoopState?.let {
          put("desktopProcessSupervisorLoopState", JsonPrimitive(it))
        }
        desktopReplay.processSupervisorLoopStatePath?.let {
          put("desktopProcessSupervisorLoopStatePath", JsonPrimitive(it))
        }
        put("desktopProcessSupervisorLoopGeneration", JsonPrimitive(desktopReplay.processSupervisorLoopGeneration))
        desktopReplay.processSupervisorLoopSessionId?.let {
          put("desktopProcessSupervisorLoopSessionId", JsonPrimitive(it))
        }
        desktopReplay.processSupervisorLoopHeartbeatAt?.let {
          put("desktopProcessSupervisorLoopHeartbeatAt", JsonPrimitive(it))
        }
        desktopReplay.processSupervisorLoopLeaseExpiresAt?.let {
          put("desktopProcessSupervisorLoopLeaseExpiresAt", JsonPrimitive(it))
        }
        desktopReplay.processSupervisorLoopBlockedReason?.let {
          put("desktopProcessSupervisorLoopBlockedReason", JsonPrimitive(it))
        }
        put("desktopProcessSupervisorLoopBootstrapOnly", JsonPrimitive(desktopReplay.processSupervisorLoopBootstrapOnly))
        put("desktopLongLivedProcessReady", JsonPrimitive(desktopReplay.longLivedProcessReady))
        desktopReplay.profileId?.let { put("desktopReplayProfileId", JsonPrimitive(it)) }
        desktopReplay.environmentId?.let { put("desktopReplayEnvironmentId", JsonPrimitive(it)) }
        desktopReplay.supervisorId?.let { put("desktopReplaySupervisorId", JsonPrimitive(it)) }
        put("desktopRestartSupported", JsonPrimitive(desktopReplay.restartSupported))
        put("desktopHealthReportSupported", JsonPrimitive(desktopReplay.healthReportSupported))
        put("desktopReplayDependencyCount", JsonPrimitive(desktopReplay.dependencyCount))
        put("desktopReplayMissingDependencyCount", JsonPrimitive(desktopReplay.missingDependencyCount))
        desktopReplay.stateFilePath?.let { put("desktopProfileReplayStatePath", JsonPrimitive(it)) }
        desktopReplay.resultFilePath?.let { put("desktopProfileReplayResultPath", JsonPrimitive(it)) }
        put("runtimeStageInstalled", JsonPrimitive(carrier.runtimeStageInstalled))
        put("runtimeStageManifestPresent", JsonPrimitive(carrier.runtimeStageManifestPresent))
        put("runtimeEngineManifestPresent", JsonPrimitive(carrier.runtimeEngineManifestPresent))
        put("runtimeTaskCount", JsonPrimitive(carrier.runtimeTaskCount))
        put("runtimeConfigCount", JsonPrimitive(carrier.runtimeConfigCount))
        put("runtimeHomeReady", JsonPrimitive(carrier.runtimeHomeReady))
        put("runtimeHomeExists", JsonPrimitive(carrier.runtimeHomeExists))
        put("runtimeExecutionStateCount", JsonPrimitive(carrier.runtimeExecutionStateCount))
        put("toolkitStageInstalled", JsonPrimitive(carrier.toolkitStageInstalled))
        put("toolkitStageManifestPresent", JsonPrimitive(carrier.toolkitStageManifestPresent))
        put("toolkitCommandPolicyPresent", JsonPrimitive(carrier.toolkitCommandPolicyPresent))
        put("toolDescriptorCount", JsonPrimitive(carrier.toolDescriptorCount))
        put("runtimeToolTaskCount", JsonPrimitive(carrier.runtimeToolTaskIds.size))
        put("runtimeToolExecutionStateCount", JsonPrimitive(carrier.runtimeToolExecutionStateCount))
        put("runtimePluginTaskCount", JsonPrimitive(carrier.runtimePluginTaskIds.size))
        put("runtimePluginExecutionStateCount", JsonPrimitive(carrier.runtimePluginExecutionStateCount))
        put("browserStageInstalled", JsonPrimitive(browser.browserStageInstalled))
        put("browserStageManifestPresent", JsonPrimitive(browser.browserStageManifestPresent))
        put("browserAuthFlowCount", JsonPrimitive(browser.browserAuthFlowCount))
        put("browserLaunchStateCount", JsonPrimitive(browser.browserLaunchStateCount))
        put("browserReplayReady", JsonPrimitive(browser.browserReplayReady))
        put("browserStateFilePresent", JsonPrimitive(browser.browserStateFilePresent))
        put("browserLogFilePresent", JsonPrimitive(browser.browserLogFilePresent))
        put("authCredentialPresent", JsonPrimitive(browser.authCredentialPresent))
        put("authInProgress", JsonPrimitive(browser.authInProgress))
        browser.authSignedInEmail?.let { put("authSignedInEmail", JsonPrimitive(it)) }
        browser.lastLaunchFlowId?.let { put("lastLaunchFlowId", JsonPrimitive(it)) }
        browser.lastLaunchStatus?.let { put("lastLaunchStatus", JsonPrimitive(it)) }
        browser.lastLaunchRequested?.let { put("lastLaunchRequested", JsonPrimitive(it)) }
        browser.lastLaunchExecutedAt?.let { put("lastLaunchExecutedAt", JsonPrimitive(it)) }
        browser.lastLaunchStatusText?.let { put("lastLaunchStatusText", JsonPrimitive(it)) }
        browser.lastLaunchErrorText?.let { put("lastLaunchErrorText", JsonPrimitive(it)) }
        browser.stateFilePath?.let { put("stateFilePath", JsonPrimitive(it)) }
        browser.logFilePath?.let { put("logFilePath", JsonPrimitive(it)) }
        carrier.engineId?.let { put("engineId", JsonPrimitive(it)) }
        carrier.engineVersion?.let { put("engineVersion", JsonPrimitive(it)) }
        carrier.runtimeHomeVersion?.let { put("runtimeHomeVersion", JsonPrimitive(it)) }
        put("helperBootstrapCommandCount", JsonPrimitive(embeddedRuntimeBootstrapHelperCommands.size))
        put(
          "helperBootstrapCommands",
          buildJsonArray {
            embeddedRuntimeBootstrapHelperCommands.forEach { command ->
              add(JsonPrimitive(command))
            }
          },
        )
        put("runtimeExecutionCommandCount", JsonPrimitive(embeddedRuntimeExecutionCommands.size))
        put(
          "runtimeExecutionCommands",
          buildJsonArray {
            embeddedRuntimeExecutionCommands.forEach { command ->
              add(JsonPrimitive(command))
            }
          },
        )
        put("browserLaunchCommandCount", JsonPrimitive(embeddedRuntimeBrowserLaunchCommands.size))
        put(
          "browserLaunchCommands",
          buildJsonArray {
            embeddedRuntimeBrowserLaunchCommands.forEach { command ->
              add(JsonPrimitive(command))
            }
          },
        )
        put("desktopMaterializeCommandCount", JsonPrimitive(embeddedRuntimeDesktopMaterializeCommands.size))
        put(
          "desktopMaterializeCommands",
          buildJsonArray {
            embeddedRuntimeDesktopMaterializeCommands.forEach { command ->
              add(JsonPrimitive(command))
            }
          },
        )
        put(
          "runtimeTaskIds",
          buildJsonArray {
            carrier.runtimeTaskIds.forEach { taskId ->
              add(JsonPrimitive(taskId))
            }
          },
        )
        put(
          "runtimeToolTaskIds",
          buildJsonArray {
            carrier.runtimeToolTaskIds.forEach { taskId ->
              add(JsonPrimitive(taskId))
            }
          },
        )
        put(
          "runtimePluginTaskIds",
          buildJsonArray {
            carrier.runtimePluginTaskIds.forEach { taskId ->
              add(JsonPrimitive(taskId))
            }
          },
        )
        put(
          "toolIds",
          buildJsonArray {
            carrier.toolIds.forEach { toolId ->
              add(JsonPrimitive(toolId))
            }
          },
        )
        put(
          "desktopProfileIds",
          buildJsonArray {
            desktop.profileIds.forEach { profileId ->
              add(JsonPrimitive(profileId))
            }
          },
        )
        put(
          "missingDomains",
          buildJsonArray {
            missingDomains.forEach { domain ->
              add(JsonPrimitive(domain))
            }
          },
        )
        put(
          "domains",
          buildJsonArray {
            add(
              buildDesktopRuntimeDomain(
                id = "desktopEnvironment",
                status = desktopStatus,
                integrated = desktop.desktopBundleReady,
                summary =
                  when {
                    desktop.desktopHomeReady ->
                      "A packaged desktop-environment bundle is now materialized into app-private storage with active profile, logs, state, and component manifests."
                    desktop.desktopBundleReady ->
                      "A cohesive desktop-environment bundle is now packaged into the APK as one desktop stage and is ready for app-private materialization."
                    desktop.desktopStageInstalled ->
                      "Desktop stage assets have started to land, but the full environment bundle is not assembled yet."
                    else -> "No packaged desktop-environment stage exists yet."
                  },
              ),
            )
            add(
              buildDesktopRuntimeDomain(
                id = "packaging",
                status = if (inspection.ready) "landed_bootstrap" else "partial_bootstrap",
                integrated = inspection.available,
                summary = "Build-time packaging, app-private extraction, and verification are wired into Android.",
              ),
            )
            add(
              buildDesktopRuntimeDomain(
                id = "helperSurface",
                status = "landed_bootstrap",
                integrated = true,
                summary = "Read-only helper entrypoints exist for pod health, manifest metadata, bounded browser metadata, workspace inventory, and workspace reads.",
              ),
            )
            add(
              buildDesktopRuntimeDomain(
                id = "workspaceBridge",
                status = if (inspection.ready) "landed_bootstrap" else "partial_bootstrap",
                integrated = true,
                summary = "Packaged workspace metadata and packaged document reads are already replayable.",
              ),
            )
            add(
              buildDesktopRuntimeDomain(
                id = "engine",
                status = engineStatus,
                integrated = engineIntegrated,
                summary =
                  if (desktopReplay.replayReady) {
                    "A bounded packaged task engine now also replays the active desktop profile from app-private desktop home state, not just the generic runtime carrier."
                  } else {
                    "A bounded packaged task engine now exists, but it is still a curated carrier rather than full desktop JS or browser parity."
                  },
              ),
            )
            add(
              buildDesktopRuntimeDomain(
                id = "environment",
                status = environmentStatus,
                integrated = environmentIntegrated,
                summary =
                  if (desktopReplay.processSupervisorLoopReady) {
                    "The app now leaves a supervisor-loop contract alongside detached-launch, recovery, observation, supervision, activation, and process-model artifacts, including loop session IDs, lease-renewal cadence, and recovery re-entry metadata for the bounded detached runtime handoff."
                  } else if (desktopReplay.processDetachedLaunchReady) {
                    "The app now leaves a detached-launch contract alongside process-model, activation, supervision, observation, and recovery artifacts, including launch session IDs, launch command metadata, and the bootstrap paths for the next bounded background runtime handoff."
                  } else if (desktopReplay.processRecoveryReady) {
                    "The app now leaves process-model, activation-contract, supervision-contract, observation-contract, and recovery-contract artifacts under desktop-home state, including explicit recovery actions, restart semantics, and the primary next step for the bounded runtime session."
                  } else if (desktopReplay.processObservationReady) {
                    "The app now leaves process-model, activation-contract, supervision-contract, and observation-contract artifacts under desktop-home state, including lease freshness, observation timing, and bounded recovery hints for the runtime session."
                  } else if (desktopReplay.processSupervisionReady) {
                    "The app now leaves process-model, activation-contract, and supervision-contract artifacts under desktop-home state, including lease and heartbeat semantics for the bounded runtime session."
                  } else if (desktopReplay.processActivationReady) {
                    "The app now leaves both process-model and activation-contract bootstrap artifacts under desktop-home state, so the next slice can deepen into a real long-lived supervised process instead of more descriptor-only proof."
                  } else if (desktopReplay.processModelReady) {
                    "The app now leaves a structured process-model bootstrap artifact alongside health-report and restart-contract state, so the next slice can deepen into real supervised activation rather than more status-only proof."
                  } else if (desktopReplay.environmentSupervisionReady) {
                    "The app now leaves explicit desktop-home health-report and restart-contract artifacts alongside the bounded profile replay."
                  } else if (desktopReplay.replayReady) {
                    "The app can now replay the active desktop profile against app-private runtime and desktop homes, including persisted environment and supervisor contract state."
                  } else {
                    "The app can now materialize an app-private runtime home with config, state, logs, and work directories for packaged runtime tasks."
                  },
              ),
            )
            add(
              buildDesktopRuntimeDomain(
                id = "browser",
                status = browserStatus,
                integrated = browserIntegrated,
                summary =
                  when {
                    browser.authInProgress ->
                      "The bounded browser-auth lane is active and waiting for the external OpenAI Codex login flow to complete."
                    browser.browserReplayReady && browser.authCredentialPresent ->
                      "A bounded browser-auth lane now has replayable launch evidence on disk and is currently backed by a stored OpenAI Codex credential."
                    browser.browserReplayReady ->
                      "A bounded browser-auth lane now has replayable launch evidence on disk, but the auth flow still needs to finish and persist a credential."
                    browser.authCredentialPresent ->
                      "A bounded browser-auth lane is packaged and a stored OpenAI Codex credential exists, but the packaged lane itself still lacks replay evidence."
                    browserIntegrated ->
                      "A bounded external-browser auth lane is now packaged, but it still needs replayable on-device proof."
                    browser.browserStageInstalled || browser.browserAuthFlowCount > 0 ->
                      "Browser stage assets have started to land, but the bounded auth lane is not fully wired yet."
                    else -> "No bounded desktop browser lane is packaged yet."
                  },
              ),
            )
            add(
              buildDesktopRuntimeDomain(
                id = "tools",
                status = toolsStatus,
                integrated = toolsIntegrated,
                summary =
                  when {
                    carrier.runtimeToolExecutionStateCount > 0 ->
                      "A curated packaged desktop tool lane now executes through pod.runtime.execute and leaves replayable state on disk."
                    toolsIntegrated ->
                      "A curated packaged desktop tool lane is now bundled behind toolkit descriptors and command policy."
                    carrier.toolkitStageInstalled || carrier.toolDescriptorCount > 0 ->
                      "Toolkit assets have started to land, but the packaged desktop tool lane is not callable yet."
                    else -> "No curated desktop tool execution lane is packaged yet."
                  },
              ),
            )
            add(
              buildDesktopRuntimeDomain(
                id = "plugins",
                status = pluginsStatus,
                integrated = pluginsIntegrated,
                summary =
                  when {
                    carrier.runtimePluginExecutionStateCount > 0 ->
                      "A narrow allowlisted plugin lane now replays through pod.runtime.execute and persists structured plugin evidence on disk without exposing generic plugin installation or arbitrary subprocess execution."
                    pluginsIntegrated ->
                      "A narrow allowlisted plugin lane is now bundled behind an explicit plugin allowlist and packaged runtime task."
                    desktop.pluginsManifestPresent ->
                      "The desktop bundle now carries an allowlisted plugin-set descriptor, but the packaged plugin task has not been replayed on-device yet."
                    else ->
                      "No packaged plugin runtime surface is exposed yet."
                  },
              ),
            )
          },
        )
        put(
          "recommendedNextSlice",
          JsonPrimitive(
            when {
              !desktop.desktopBundleReady -> "desktop_bundle_packaging"
              !desktop.desktopHomeReady -> "desktop_home_materialize"
              desktop.desktopHomeReady && !desktopReplay.replayReady -> "desktop_home_replay"
              desktopReplay.replayReady && !desktopReplay.environmentSupervisionReady -> "environment_supervision"
              !carrier.runtimeHomeReady -> "runtime_execute_replay"
              !toolsIntegrated -> "tool_lane_bootstrap"
              carrier.runtimeToolExecutionStateCount < 1 -> "tool_lane_replay"
              !browserIntegrated -> "browser_lane_bootstrap"
              !browser.browserReplayReady -> "browser_lane_replay"
              !browser.authCredentialPresent -> "browser_lane_complete"
              !pluginsIntegrated -> "plugin_lane_bootstrap"
              carrier.runtimePluginExecutionStateCount < 1 -> "plugin_lane_replay"
              !desktopReplay.processModelReady -> "process_model_bootstrap"
              !desktopReplay.processActivationReady -> "process_runtime_activation_bootstrap"
              !desktopReplay.processSupervisionReady -> "process_runtime_supervision"
              !desktopReplay.processObservationReady -> "process_runtime_observation"
              !desktopReplay.processRecoveryReady -> "process_runtime_recovery"
              !desktopReplay.processDetachedLaunchReady -> "process_runtime_detached_launch"
              !desktopReplay.processSupervisorLoopReady -> "process_runtime_supervisor_loop"
              else -> "process_runtime_active_session"
            },
          ),
        )
        put(
          "recommendedNextStep",
          JsonPrimitive(
            when {
              !desktop.desktopBundleReady ->
                "Package the desktop stage so engine, environment, browser, tools, plugins, and supervisor metadata ship together in the APK."
              !desktop.desktopHomeReady ->
                "Run pod.desktop.materialize on-device to materialize the packaged desktop environment into app-private storage before widening execution parity."
              desktop.desktopHomeReady && !desktopReplay.replayReady ->
                "Re-run pod.runtime.execute with the runtime-smoke task after desktop-home materialization so the embedded engine actually replays the active desktop profile and supervisor/environment contracts."
              desktopReplay.replayReady && !desktopReplay.environmentSupervisionReady ->
                "Re-run pod.runtime.execute with the runtime-smoke task on the current build so the desktop-home replay also leaves explicit health-report and restart-contract artifacts."
              !carrier.runtimeHomeReady ->
                "Run pod.runtime.execute with the runtime-smoke task on-device to verify the packaged carrier end to end before widening browser, tools, or plugins."
              !toolsIntegrated ->
                "Use the packaged runtime carrier as the base for the first bounded desktop tool lane before widening into browser or plugins."
              carrier.runtimeToolExecutionStateCount < 1 ->
                "Run pod.runtime.execute with the packaged desktop tool task on-device to prove the new tool lane is replayable."
              !browserIntegrated ->
                "Package the first bounded browser lane around an explicit auth flow instead of widening into generic browser tools."
              !browser.browserReplayReady ->
                "Run pod.browser.describe, then replay pod.browser.auth.start on-device to prove the bounded browser-auth lane before widening into plugins."
              !browser.authCredentialPresent ->
                "Complete the bounded browser-auth flow on-device and re-read pod.browser.describe until the replayed lane is also backed by a stored credential."
              !pluginsIntegrated ->
                "Attach one narrow allowlisted plugin slice behind pod.runtime.execute instead of widening into generic plugin install or process execution."
              carrier.runtimePluginExecutionStateCount < 1 ->
                "Run pod.runtime.execute with the packaged allowlisted plugin task on-device to prove the plugin lane leaves replayable evidence on disk."
              !desktopReplay.processModelReady ->
                "Deepen runtime-smoke so the desktop-home replay also leaves a structured process-model bootstrap artifact that ties together health, restart, desired state, and session semantics."
              !desktopReplay.processActivationReady ->
                "Deepen runtime-smoke again so the desktop-home replay also leaves a structured activation contract artifact that ties process state to supervisor action, blocked reason, and generation semantics."
              !desktopReplay.processSupervisionReady ->
                "Deepen runtime-smoke again so the desktop-home replay also leaves a supervision contract artifact with lease, heartbeat, and long-lived session semantics."
              !desktopReplay.processObservationReady ->
                "Deepen runtime-smoke again so the desktop-home replay also leaves an observation contract artifact with lease freshness, timing, and bounded recovery guidance for the supervised session."
              !desktopReplay.processRecoveryReady ->
                "Deepen runtime-smoke again so the desktop-home replay also leaves an explicit recovery contract artifact with primary actions, restart semantics, and replay guidance for the bounded session."
              !desktopReplay.processDetachedLaunchReady ->
                "Deepen runtime-smoke again so the desktop-home replay also leaves a detached-launch contract artifact with launch session IDs, launch command metadata, and bootstrap paths for the bounded background runtime handoff."
              !desktopReplay.processSupervisorLoopReady ->
                "Deepen runtime-smoke again so the desktop-home replay also leaves a supervisor-loop contract artifact with loop session IDs, lease-renewal cadence, and recovery re-entry metadata for the bounded detached runtime handoff."
              else ->
                "Keep the supervisor-loop bootstrap stable, then prove it as a real detached active session on-device with observed lease renewal, recovery re-entry, and restart continuity."
            },
          ),
        )
      },
  )
}

private fun embeddedRuntimePodInstallRoot(context: Context): File =
  context.filesDir.resolve("openclaw/embedded-runtime-pod")

private fun loadEmbeddedRuntimePodManifest(context: Context): EmbeddedRuntimePodAssetManifest? {
  return runCatching {
    context.assets
      .open(embeddedRuntimePodManifestAssetPath)
      .bufferedReader()
      .use { reader ->
        embeddedRuntimePodJson.decodeFromString(EmbeddedRuntimePodAssetManifest.serializer(), reader.readText())
      }
  }.getOrNull()
}

private fun readAssetJsonObjectOrNull(
  context: Context,
  assetPath: String,
): JsonObject? {
  return runCatching {
    context.assets.open(assetPath).bufferedReader().use { reader ->
      embeddedRuntimePodJson.parseToJsonElement(reader.readText()) as? JsonObject
    }
  }.getOrNull()
}

private fun copyAssetIntoFile(context: Context, assetPath: String, target: File) {
  target.parentFile?.mkdirs()
  context.assets.open(assetPath).use { input ->
    target.outputStream().use { output ->
      input.copyTo(output)
    }
  }
}

private fun resolvePodRelativePath(versionDir: File, relativePath: String): File? {
  val normalized = relativePath.replace('\\', '/')
  if (normalized.isBlank()) return null
  if (normalized.startsWith("/") || normalized.contains("../")) return null
  return versionDir.resolve(normalized)
}

private fun walkFiles(rootDir: File): List<File> {
  val entries = rootDir.listFiles().orEmpty().sortedBy { it.name.lowercase() }
  val files = mutableListOf<File>()
  entries.forEach { entry ->
    if (entry.isDirectory) {
      files.addAll(walkFiles(entry))
    } else if (entry.isFile) {
      files.add(entry)
    }
  }
  return files
}

private fun toPodRelativePath(rootDir: File, file: File): String =
  file.canonicalFile.relativeTo(rootDir.canonicalFile).invariantSeparatorsPath

private fun normalizeWorkspaceRelativePath(rawPath: String): String =
  rawPath
    .replace('\\', '/')
    .trim()
    .removePrefix("./")
    .trimStart('/')

private fun resolveWorkspaceRelativePath(rootDir: File, rawPath: String): File? {
  val normalized = normalizeWorkspaceRelativePath(rawPath)
  val candidate =
    if (normalized.isEmpty()) {
      rootDir
    } else {
      File(rootDir, normalized)
    }.canonicalFile
  val rootPath = rootDir.canonicalPath
  return if (candidate.path == rootPath || candidate.path.startsWith("$rootPath${File.separator}")) {
    candidate
  } else {
    null
  }
}

private fun readJsonObjectOrNull(file: File): JsonObject? {
  return runCatching {
    embeddedRuntimePodJson.parseToJsonElement(file.readText(Charsets.UTF_8)) as? JsonObject
  }.getOrNull()
}

private fun describeWorkspaceFile(
  workspaceRoot: File,
  file: File,
): JsonObject {
  val relativePath = toPodRelativePath(workspaceRoot, file)
  val previewText = readWorkspaceTextPreview(file)
  return buildJsonObject {
    put("relativePath", JsonPrimitive(relativePath))
    put("sizeBytes", JsonPrimitive(file.length()))
    put("sha256", JsonPrimitive(sha256(file)))
    put("isManifest", JsonPrimitive(relativePath == "manifest.json"))
    put("isContentIndex", JsonPrimitive(relativePath == "content-index.json"))
    if (previewText != null) {
      put("textPreview", JsonPrimitive(previewText.text))
      put("textPreviewTruncated", JsonPrimitive(previewText.truncated))
    }
  }
}

private data class WorkspaceTextPreview(
  val text: String,
  val truncated: Boolean,
)

private fun readWorkspaceTextPreview(
  file: File,
): WorkspaceTextPreview? {
  if (!isWorkspaceTextFile(file)) return null
  val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return null
  val trimmed = text.trim()
  if (trimmed.isEmpty()) return WorkspaceTextPreview(text = "", truncated = false)
  val maxChars = 240
  val truncated = trimmed.length > maxChars
  return WorkspaceTextPreview(
    text = trimmed.take(maxChars),
    truncated = truncated,
  )
}

private fun isWorkspaceTextFile(file: File): Boolean =
  file.extension.lowercase() in embeddedRuntimePodTextExtensions

private fun indexedWorkspaceDocument(
  contentIndex: JsonObject?,
  relativePath: String,
): JsonObject? {
  val documents = jsonObjectArray(contentIndex?.get("documents"))
  return documents
    .firstOrNull { doc ->
      doc["path"]?.let { value -> value as? JsonPrimitive }?.content == relativePath
    }
}

private fun jsonObjectArray(element: kotlinx.serialization.json.JsonElement?): List<JsonObject> {
  val array = element as? JsonArray ?: return emptyList()
  return array.mapNotNull { it as? JsonObject }
}

private fun primitiveContent(
  jsonObject: JsonObject,
  key: String,
): String? {
  val primitive = jsonObject[key] as? JsonPrimitive ?: return null
  return primitive.content.takeUnless { it == "null" }
}

private fun buildDesktopRuntimeDomain(
  id: String,
  status: String,
  integrated: Boolean,
  summary: String,
): JsonObject =
  buildJsonObject {
    put("id", JsonPrimitive(id))
    put("status", JsonPrimitive(status))
    put("targeted", JsonPrimitive(true))
    put("integrated", JsonPrimitive(integrated))
    put("summary", JsonPrimitive(summary))
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
