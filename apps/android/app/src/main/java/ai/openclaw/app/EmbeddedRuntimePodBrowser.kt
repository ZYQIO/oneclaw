package ai.openclaw.app

import android.content.Context
import ai.openclaw.app.auth.OpenAICodexOAuthApi
import ai.openclaw.app.auth.buildOpenAICodexAppReturnUri
import java.io.File
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

private const val embeddedBrowserStageDirectoryName = "browser"
private const val embeddedBrowserDefaultFlowId = "openai-codex-oauth"
private const val embeddedBrowserStateFileName = "browser-openai-codex-auth.json"
private const val embeddedBrowserLogFileName = "browser-lane.log"
private const val embeddedRuntimeHomeRootDisplayPathForBrowser = "filesDir/openclaw/embedded-runtime-home"

private val embeddedRuntimeBrowserJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class EmbeddedBrowserStageManifest(
  val stage: String,
  val purpose: String? = null,
  val authFlowDescriptorPaths: List<String> = emptyList(),
  val authFlowIds: List<String> = emptyList(),
)

@Serializable
private data class EmbeddedBrowserAuthFlowDescriptor(
  val schemaVersion: Int = 1,
  val flowId: String,
  val displayName: String? = null,
  val kind: String,
  val provider: String? = null,
  val summary: String? = null,
  val startCommand: String? = null,
  val authorizeUrl: String? = null,
  val redirectUri: String? = null,
  val returnUri: String? = null,
  val browserMode: String? = null,
  val runtimeDependencies: List<String> = emptyList(),
)

data class EmbeddedRuntimeBrowserInspection(
  val browserStageInstalled: Boolean,
  val browserStageManifestPresent: Boolean,
  val browserAuthFlowCount: Int,
  val browserAuthFlowIds: List<String>,
  val browserLaunchStateCount: Int,
  val browserReplayReady: Boolean = false,
  val browserStateFilePresent: Boolean = false,
  val browserLogFilePresent: Boolean = false,
  val authCredentialPresent: Boolean,
  val authSignedInEmail: String? = null,
  val authInProgress: Boolean = false,
  val lastLaunchFlowId: String? = null,
  val lastLaunchStatus: String? = null,
  val lastLaunchRequested: Boolean? = null,
  val lastLaunchExecutedAt: String? = null,
  val lastLaunchStatusText: String? = null,
  val lastLaunchErrorText: String? = null,
  val stateFilePath: String? = null,
  val logFilePath: String? = null,
)

data class EmbeddedRuntimePodBrowserDescribeResult(
  val ok: Boolean,
  val payload: JsonObject? = null,
  val code: String? = null,
  val message: String? = null,
)

data class EmbeddedRuntimePodBrowserStartResult(
  val ok: Boolean,
  val payload: JsonObject? = null,
  val code: String? = null,
  val message: String? = null,
)

fun inspectEmbeddedRuntimeBrowserLane(
  context: Context,
  manifestVersion: String?,
): EmbeddedRuntimeBrowserInspection {
  val nodeApp = context.applicationContext as? NodeApp
  val currentUiState = nodeApp?.openAICodexAuthManager?.uiState?.value
  val credential = nodeApp?.prefs?.loadOpenAICodexCredential()
  if (manifestVersion.isNullOrBlank()) {
    return EmbeddedRuntimeBrowserInspection(
      browserStageInstalled = false,
      browserStageManifestPresent = false,
      browserAuthFlowCount = 0,
      browserAuthFlowIds = emptyList(),
      browserLaunchStateCount = 0,
      browserReplayReady = false,
      browserStateFilePresent = false,
      browserLogFilePresent = false,
      authCredentialPresent = credential != null,
      authSignedInEmail = credential?.email,
      authInProgress = currentUiState?.inProgress == true,
      stateFilePath = null,
      logFilePath = null,
    )
  }

  val versionDir = embeddedRuntimePodInstallRootForBrowser(context).resolve(manifestVersion)
  val browserStageRoot = versionDir.resolve(embeddedBrowserStageDirectoryName)
  val browserStageInstalled = browserStageRoot.isDirectory
  val browserStageManifest = browserStageRoot.resolve("manifest.json").takeIf { it.isFile }?.let(::loadBrowserStageManifest)
  val authFlowFiles =
    browserStageManifest?.authFlowDescriptorPaths
      ?.mapNotNull { resolveBrowserRelativePath(browserStageRoot, it)?.takeIf(File::isFile) }
      .orEmpty()
      .ifEmpty {
        browserStageRoot.resolve("auth").takeIf { it.isDirectory }?.listFiles()?.filter {
          it.isFile && it.extension.lowercase() == "json"
        }.orEmpty().sortedBy { it.name }
      }
  val authFlows = authFlowFiles.mapNotNull(::loadBrowserAuthFlowDescriptor)
  val authFlowIds = (browserStageManifest?.authFlowIds.orEmpty() + authFlows.map { it.flowId }).distinct().sorted()

  val runtimeHome = embeddedRuntimeHomeRootForBrowser(context).resolve(manifestVersion)
  val stateDir = runtimeHome.resolve("state")
  val stateFile = stateDir.resolve(embeddedBrowserStateFileName)
  val logFile = runtimeHome.resolve("logs/$embeddedBrowserLogFileName")
  val launchState = stateFile.takeIf { it.isFile }?.let(::readBrowserJsonObjectOrNull)
  val browserLaunchStateCount =
    stateDir.takeIf { it.isDirectory }?.listFiles()?.count {
      it.isFile && it.name.startsWith("browser-") && it.extension.lowercase() == "json"
    } ?: 0
  val browserReplayReady = launchState != null

  return EmbeddedRuntimeBrowserInspection(
    browserStageInstalled = browserStageInstalled,
    browserStageManifestPresent = browserStageManifest != null,
    browserAuthFlowCount = authFlowIds.size,
    browserAuthFlowIds = authFlowIds,
    browserLaunchStateCount = browserLaunchStateCount,
    browserReplayReady = browserReplayReady,
    browserStateFilePresent = stateFile.isFile,
    browserLogFilePresent = logFile.isFile,
    authCredentialPresent = credential != null,
    authSignedInEmail = credential?.email,
    authInProgress = currentUiState?.inProgress == true,
    lastLaunchFlowId = browserPrimitiveContent(launchState, "flowId"),
    lastLaunchStatus = browserPrimitiveContent(launchState, "status"),
    lastLaunchRequested = browserPrimitiveBoolean(launchState, "launchRequested"),
    lastLaunchExecutedAt = browserPrimitiveContent(launchState, "executedAt"),
    lastLaunchStatusText = browserPrimitiveContent(launchState, "statusText"),
    lastLaunchErrorText = browserPrimitiveContent(launchState, "errorText"),
    stateFilePath = runtimeHomeDisplayPathForBrowser(manifestVersion, "state/$embeddedBrowserStateFileName"),
    logFilePath = runtimeHomeDisplayPathForBrowser(manifestVersion, "logs/$embeddedBrowserLogFileName"),
  )
}

fun describeEmbeddedRuntimePodBrowser(
  context: Context,
): EmbeddedRuntimePodBrowserDescribeResult {
  val inspection = inspectEmbeddedRuntimePod(context)
  val browser = inspectEmbeddedRuntimeBrowserLane(context = context, manifestVersion = inspection.manifestVersion)
  val flows = loadEmbeddedRuntimeBrowserFlowDescriptors(context, inspection.manifestVersion)
  val selectedFlowId = flows.firstOrNull()?.flowId ?: browser.browserAuthFlowIds.firstOrNull() ?: embeddedBrowserDefaultFlowId
  val browserIntegrated =
    browser.browserStageInstalled && browser.browserStageManifestPresent && browser.browserAuthFlowCount > 0
  val browserStatus =
    when {
      browserIntegrated && browser.authInProgress -> "auth_in_progress"
      browserIntegrated && browser.browserReplayReady && browser.authCredentialPresent -> "configured"
      browserIntegrated && browser.browserReplayReady -> "replayed"
      browserIntegrated -> "bounded_lane_ready"
      browser.browserStageInstalled || browser.browserAuthFlowCount > 0 -> "bootstrap_present"
      else -> "missing"
    }

  return EmbeddedRuntimePodBrowserDescribeResult(
    ok = true,
    payload =
      buildJsonObject {
        put("mainlineBranch", JsonPrimitive("android-desktop-runtime-mainline-20260403"))
        put("browserStatus", JsonPrimitive(browserStatus))
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
        put("launchCommandCount", JsonPrimitive(1))
        put(
          "launchCommands",
          buildJsonArray {
            add(JsonPrimitive("pod.browser.auth.start"))
          },
        )
        put("recommendedFlowId", JsonPrimitive(selectedFlowId))
        put(
          "authFlowIds",
          buildJsonArray {
            browser.browserAuthFlowIds.forEach { add(JsonPrimitive(it)) }
          },
        )
        put(
          "flows",
          buildJsonArray {
            flows.forEach { flow ->
              add(
                buildJsonObject {
                  put("flowId", JsonPrimitive(flow.flowId))
                  put("displayName", JsonPrimitive(flow.displayName ?: flow.flowId))
                  put("kind", JsonPrimitive(flow.kind))
                  put("provider", JsonPrimitive(flow.provider ?: "unknown"))
                  put("summary", JsonPrimitive(flow.summary ?: ""))
                  put("startCommand", JsonPrimitive(flow.startCommand ?: ""))
                  put("authorizeUrl", JsonPrimitive(flow.authorizeUrl ?: ""))
                  put("redirectUri", JsonPrimitive(flow.redirectUri ?: ""))
                  put("returnUri", JsonPrimitive(flow.returnUri ?: ""))
                  put("browserMode", JsonPrimitive(flow.browserMode ?: ""))
                  put(
                    "runtimeDependencies",
                    buildJsonArray {
                      flow.runtimeDependencies.forEach { dependency ->
                        add(JsonPrimitive(dependency))
                      }
                    },
                  )
                },
              )
            }
          },
        )
      },
  )
}

fun startEmbeddedRuntimePodBrowserAuth(
  context: Context,
  flowId: String?,
): EmbeddedRuntimePodBrowserStartResult {
  val inspection = inspectEmbeddedRuntimePod(context)
  val manifestVersion = inspection.manifestVersion
  if (!inspection.ready || manifestVersion.isNullOrBlank()) {
    return EmbeddedRuntimePodBrowserStartResult(
      ok = false,
      code = "POD_NOT_READY",
      message = "POD_NOT_READY: embedded runtime pod not ready",
    )
  }

  val flow = selectBrowserAuthFlow(context = context, manifestVersion = manifestVersion, requestedFlowId = flowId)
    ?: return EmbeddedRuntimePodBrowserStartResult(
      ok = false,
      code = "NOT_FOUND",
      message = "NOT_FOUND: browser auth flow not found",
    )
  if (flow.startCommand?.trim().takeUnless { it.isNullOrEmpty() } != "pod.browser.auth.start") {
    return EmbeddedRuntimePodBrowserStartResult(
      ok = false,
      code = "UNAVAILABLE",
      message = "UNAVAILABLE: browser auth flow is not wired to pod.browser.auth.start",
    )
  }

  val nodeApp =
    context.applicationContext as? NodeApp
      ?: return EmbeddedRuntimePodBrowserStartResult(
        ok = false,
        code = "UNAVAILABLE",
        message = "UNAVAILABLE: node app context unavailable",
      )

  val authManager = nodeApp.openAICodexAuthManager
  val credentialBefore = nodeApp.prefs.loadOpenAICodexCredential()
  val wasInProgress = authManager.uiState.value.inProgress
  if (!wasInProgress) {
    runCatching {
      authManager.startLogin()
    }.getOrElse { err ->
      return EmbeddedRuntimePodBrowserStartResult(
        ok = false,
        code = "UNAVAILABLE",
        message = "UNAVAILABLE: failed to start browser auth (${err.message ?: "unknown error"})",
      )
    }
  }
  val currentUiState = authManager.uiState.value
  val credentialAfter = nodeApp.prefs.loadOpenAICodexCredential()
  val now = Instant.now().toString()
  val launchStatus =
    when {
      wasInProgress -> "already_in_progress"
      currentUiState.inProgress -> "launch_requested"
      !currentUiState.errorText.isNullOrBlank() -> "launch_failed"
      else -> "launch_requested"
    }

  val runtimeHome = embeddedRuntimeHomeRootForBrowser(context).resolve(manifestVersion)
  listOf(runtimeHome, runtimeHome.resolve("logs"), runtimeHome.resolve("state"), runtimeHome.resolve("work")).forEach(File::mkdirs)
  val stateFile = runtimeHome.resolve("state/$embeddedBrowserStateFileName")
  val previousExecutionCount =
    stateFile.takeIf { it.isFile }?.let(::readBrowserJsonObjectOrNull)?.get("executionCount")?.let {
      it as? JsonPrimitive
    }?.content?.toIntOrNull() ?: 0
  val executionCount = previousExecutionCount + 1
  val statePayload =
    buildJsonObject {
      put("flowId", JsonPrimitive(flow.flowId))
      put("displayName", JsonPrimitive(flow.displayName ?: flow.flowId))
      put("status", JsonPrimitive(launchStatus))
      put("launchRequested", JsonPrimitive(launchStatus != "launch_failed"))
      put("executionCount", JsonPrimitive(executionCount))
      put("executedAt", JsonPrimitive(now))
      put("browserMode", JsonPrimitive(flow.browserMode ?: ""))
      put("authorizeUrl", JsonPrimitive(flow.authorizeUrl ?: ""))
      put("redirectUri", JsonPrimitive(flow.redirectUri ?: OpenAICodexOAuthApi.defaultRedirectUri))
      put("returnUri", JsonPrimitive(flow.returnUri ?: buildOpenAICodexAppReturnUri()))
      put("authInProgress", JsonPrimitive(currentUiState.inProgress))
      put("credentialPresentBeforeLaunch", JsonPrimitive(credentialBefore != null))
      put("credentialPresentAfterLaunch", JsonPrimitive(credentialAfter != null))
      currentUiState.statusText?.let { put("statusText", JsonPrimitive(it)) }
      currentUiState.errorText?.let { put("errorText", JsonPrimitive(it)) }
      credentialAfter?.email?.let { put("signedInEmail", JsonPrimitive(it)) }
    }
  stateFile.writeText("${statePayload}\n", Charsets.UTF_8)
  val logFile = runtimeHome.resolve("logs/$embeddedBrowserLogFileName")
  logFile.appendText(
    "$now flow=${flow.flowId} status=$launchStatus inProgress=${currentUiState.inProgress} credentialPresent=${credentialAfter != null}\n",
    Charsets.UTF_8,
  )

  return EmbeddedRuntimePodBrowserStartResult(
    ok = true,
    payload =
      buildJsonObject {
        put("flowId", JsonPrimitive(flow.flowId))
        put("displayName", JsonPrimitive(flow.displayName ?: flow.flowId))
        put("provider", JsonPrimitive(flow.provider ?: "unknown"))
        put("kind", JsonPrimitive(flow.kind))
        put("summary", JsonPrimitive(flow.summary ?: ""))
        put("browserMode", JsonPrimitive(flow.browserMode ?: ""))
        put("authorizeUrl", JsonPrimitive(flow.authorizeUrl ?: ""))
        put("redirectUri", JsonPrimitive(flow.redirectUri ?: OpenAICodexOAuthApi.defaultRedirectUri))
        put("returnUri", JsonPrimitive(flow.returnUri ?: buildOpenAICodexAppReturnUri()))
        put("launchStatus", JsonPrimitive(launchStatus))
        put("launchRequested", JsonPrimitive(launchStatus != "launch_failed"))
        put("authInProgress", JsonPrimitive(currentUiState.inProgress))
        put("credentialPresentBeforeLaunch", JsonPrimitive(credentialBefore != null))
        put("authCredentialPresent", JsonPrimitive(credentialAfter != null))
        currentUiState.statusText?.let { put("statusText", JsonPrimitive(it)) }
        currentUiState.errorText?.let { put("errorText", JsonPrimitive(it)) }
        credentialAfter?.email?.let { put("authSignedInEmail", JsonPrimitive(it)) }
        put("executionCount", JsonPrimitive(executionCount))
        put("runtimeHomePath", JsonPrimitive(runtimeHomeDisplayPathForBrowser(manifestVersion)))
        put("stateFilePath", JsonPrimitive(runtimeHomeDisplayPathForBrowser(manifestVersion, "state/$embeddedBrowserStateFileName")))
        put("logFilePath", JsonPrimitive(runtimeHomeDisplayPathForBrowser(manifestVersion, "logs/$embeddedBrowserLogFileName")))
        put("state", statePayload)
      },
  )
}

private fun loadEmbeddedRuntimeBrowserFlowDescriptors(
  context: Context,
  manifestVersion: String?,
): List<EmbeddedBrowserAuthFlowDescriptor> {
  if (manifestVersion.isNullOrBlank()) return emptyList()
  val browserStageRoot =
    embeddedRuntimePodInstallRootForBrowser(context).resolve(manifestVersion).resolve(embeddedBrowserStageDirectoryName)
  if (!browserStageRoot.isDirectory) return emptyList()
  val browserStageManifest = browserStageRoot.resolve("manifest.json").takeIf { it.isFile }?.let(::loadBrowserStageManifest)
  val authFlowFiles =
    browserStageManifest?.authFlowDescriptorPaths
      ?.mapNotNull { resolveBrowserRelativePath(browserStageRoot, it)?.takeIf(File::isFile) }
      .orEmpty()
      .ifEmpty {
        browserStageRoot.resolve("auth").takeIf { it.isDirectory }?.listFiles()?.filter {
          it.isFile && it.extension.lowercase() == "json"
        }.orEmpty().sortedBy { it.name }
      }
  return authFlowFiles.mapNotNull(::loadBrowserAuthFlowDescriptor)
}

private fun selectBrowserAuthFlow(
  context: Context,
  manifestVersion: String,
  requestedFlowId: String?,
): EmbeddedBrowserAuthFlowDescriptor? {
  val normalizedFlowId = requestedFlowId?.trim().takeUnless { it.isNullOrEmpty() } ?: embeddedBrowserDefaultFlowId
  return loadEmbeddedRuntimeBrowserFlowDescriptors(context, manifestVersion).firstOrNull { it.flowId == normalizedFlowId }
}

private fun loadBrowserStageManifest(file: File): EmbeddedBrowserStageManifest? =
  runCatching {
    embeddedRuntimeBrowserJson.decodeFromString(
      EmbeddedBrowserStageManifest.serializer(),
      file.readText(Charsets.UTF_8),
    )
  }.getOrNull()

private fun loadBrowserAuthFlowDescriptor(file: File): EmbeddedBrowserAuthFlowDescriptor? =
  runCatching {
    embeddedRuntimeBrowserJson.decodeFromString(
      EmbeddedBrowserAuthFlowDescriptor.serializer(),
      file.readText(Charsets.UTF_8),
    )
  }.getOrNull()

private fun resolveBrowserRelativePath(rootDir: File, relativePath: String): File? {
  val normalized = relativePath.replace('\\', '/').trim()
  if (normalized.isEmpty()) return null
  if (normalized.startsWith("/") || normalized.contains("../")) return null
  return rootDir.resolve(normalized)
}

private fun readBrowserJsonObjectOrNull(file: File): JsonObject? =
  runCatching {
    embeddedRuntimeBrowserJson.parseToJsonElement(file.readText(Charsets.UTF_8)) as? JsonObject
  }.getOrNull()

private fun browserPrimitiveContent(
  payload: JsonObject?,
  key: String,
): String? = (payload?.get(key) as? JsonPrimitive)?.content?.takeUnless { it == "null" }

private fun browserPrimitiveBoolean(
  payload: JsonObject?,
  key: String,
): Boolean? =
  when (browserPrimitiveContent(payload, key)?.lowercase()) {
    "true" -> true
    "false" -> false
    else -> null
  }

private fun embeddedRuntimePodInstallRootForBrowser(context: Context): File =
  context.filesDir.resolve("openclaw/embedded-runtime-pod")

private fun embeddedRuntimeHomeRootForBrowser(context: Context): File =
  context.filesDir.resolve("openclaw/embedded-runtime-home")

private fun runtimeHomeDisplayPathForBrowser(
  version: String,
  relativePath: String? = null,
): String {
  val suffix = relativePath?.trim()?.trimStart('/')?.takeIf { it.isNotEmpty() }
  return if (suffix == null) {
    "$embeddedRuntimeHomeRootDisplayPathForBrowser/$version"
  } else {
    "$embeddedRuntimeHomeRootDisplayPathForBrowser/$version/$suffix"
  }
}
