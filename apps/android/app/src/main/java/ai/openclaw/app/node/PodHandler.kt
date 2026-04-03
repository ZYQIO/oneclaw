package ai.openclaw.app.node

import android.content.Context
import ai.openclaw.app.describeEmbeddedRuntimeDesktopRuntime
import ai.openclaw.app.describeEmbeddedRuntimePodManifest
import ai.openclaw.app.readEmbeddedRuntimePodWorkspaceFile
import ai.openclaw.app.inspectEmbeddedRuntimePod
import ai.openclaw.app.scanEmbeddedRuntimePodWorkspace
import ai.openclaw.app.gateway.GatewaySession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PodHandler(
  private val appContext: Context,
) {
  private val json = Json { ignoreUnknownKeys = true }

  fun handlePodHealth(_paramsJson: String?): GatewaySession.InvokeResult {
    val inspection = inspectEmbeddedRuntimePod(appContext)
    return GatewaySession.InvokeResult.ok(
      buildPayload(
        command = "pod.health",
        inspection = inspection.toJson(),
        localExecutionAvailable = inspection.ready,
      ).toString(),
    )
  }

  fun handlePodManifestDescribe(_paramsJson: String?): GatewaySession.InvokeResult {
    val inspection = inspectEmbeddedRuntimePod(appContext)
    val describeResult = describeEmbeddedRuntimePodManifest(appContext)
    if (!describeResult.ok) {
      return GatewaySession.InvokeResult.error(
        code = describeResult.code ?: "UNAVAILABLE",
        message = describeResult.message ?: "UNAVAILABLE: manifest metadata unavailable",
      )
    }
    return GatewaySession.InvokeResult.ok(
      buildPayload(
        command = "pod.manifest.describe",
        inspection = inspection.toJson(),
        localExecutionAvailable = inspection.ready,
        extra = describeResult.payload ?: buildJsonObject {},
      ).toString(),
    )
  }

  fun handlePodRuntimeDescribe(_paramsJson: String?): GatewaySession.InvokeResult {
    val inspection = inspectEmbeddedRuntimePod(appContext)
    val describeResult = describeEmbeddedRuntimeDesktopRuntime(appContext)
    if (!describeResult.ok) {
      return GatewaySession.InvokeResult.error(
        code = describeResult.code ?: "UNAVAILABLE",
        message = describeResult.message ?: "UNAVAILABLE: runtime metadata unavailable",
      )
    }
    return GatewaySession.InvokeResult.ok(
      buildPayload(
        command = "pod.runtime.describe",
        inspection = inspection.toJson(),
        localExecutionAvailable = inspection.ready,
        extra = describeResult.payload ?: buildJsonObject {},
      ).toString(),
    )
  }

  fun handlePodWorkspaceScan(paramsJson: String?): GatewaySession.InvokeResult {
    val inspection = inspectEmbeddedRuntimePod(appContext)
    val params = parseScanParams(paramsJson)
    val payload =
      scanEmbeddedRuntimePodWorkspace(
        context = appContext,
        limit = params.limit,
        query = params.query,
      )
    return GatewaySession.InvokeResult.ok(
      buildPayload(
        command = "pod.workspace.scan",
        inspection = inspection.toJson(),
        localExecutionAvailable = inspection.ready,
        extra = payload,
      ).toString(),
    )
  }

  fun handlePodWorkspaceRead(paramsJson: String?): GatewaySession.InvokeResult {
    val inspection = inspectEmbeddedRuntimePod(appContext)
    val params = parseReadParams(paramsJson)
    val path =
      params.path
        ?: return GatewaySession.InvokeResult.error(
          code = "INVALID_REQUEST",
          message = "INVALID_REQUEST: path required",
        )
    val readResult =
      readEmbeddedRuntimePodWorkspaceFile(
        context = appContext,
        relativePath = path,
        maxChars = params.maxChars,
      )
    if (!readResult.ok) {
      return GatewaySession.InvokeResult.error(
        code = readResult.code ?: "UNAVAILABLE",
        message = readResult.message ?: "UNAVAILABLE: workspace read failed",
      )
    }
    return GatewaySession.InvokeResult.ok(
      buildPayload(
        command = "pod.workspace.read",
        inspection = inspection.toJson(),
        localExecutionAvailable = inspection.ready,
        extra = readResult.payload ?: buildJsonObject {},
      ).toString(),
    )
  }

  private fun buildPayload(
    command: String,
    inspection: JsonObject,
    localExecutionAvailable: Boolean,
    extra: JsonObject? = null,
  ): JsonObject =
    buildJsonObject {
      put("command", JsonPrimitive(command))
      put("localExecutionAvailable", JsonPrimitive(localExecutionAvailable))
      inspection.forEach { (key, value) -> put(key, value) }
      extra?.forEach { (key, value) -> put(key, value) }
    }

  private fun parseScanParams(paramsJson: String?): PodWorkspaceScanParams {
    val params =
      try {
        paramsJson
          ?.trim()
          ?.takeIf { it.isNotEmpty() }
          ?.let { json.parseToJsonElement(it) as? JsonObject }
      } catch (_: Throwable) {
        null
      } ?: return PodWorkspaceScanParams()

    val limit =
      primitiveContent(params, "limit")
        ?.toIntOrNull()
        ?.coerceIn(1, 50)
        ?: 20
    val query =
      primitiveContent(params, "query")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    return PodWorkspaceScanParams(limit = limit, query = query)
  }

  private fun parseReadParams(paramsJson: String?): PodWorkspaceReadParams {
    val params =
      try {
        paramsJson
          ?.trim()
          ?.takeIf { it.isNotEmpty() }
          ?.let { json.parseToJsonElement(it) as? JsonObject }
      } catch (_: Throwable) {
        null
      } ?: return PodWorkspaceReadParams()

    val path =
      primitiveContent(params, "path")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val maxChars =
      primitiveContent(params, "maxChars")
        ?.toIntOrNull()
        ?.coerceIn(1, 16000)
        ?: 4000
    return PodWorkspaceReadParams(path = path, maxChars = maxChars)
  }

  private fun primitiveContent(
    params: JsonObject,
    key: String,
  ): String? {
    val primitive = params[key] as? JsonPrimitive ?: return null
    return primitive.content.takeUnless { it == "null" }
  }

  private data class PodWorkspaceScanParams(
    val limit: Int = 20,
    val query: String? = null,
  )

  private data class PodWorkspaceReadParams(
    val path: String? = null,
    val maxChars: Int = 4000,
  )
}
