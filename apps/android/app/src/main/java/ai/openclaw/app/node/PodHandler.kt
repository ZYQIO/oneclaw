package ai.openclaw.app.node

import android.content.Context
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

  fun handlePodWorkspaceScan(paramsJson: String?): GatewaySession.InvokeResult {
    val inspection = inspectEmbeddedRuntimePod(appContext)
    val params = parseParams(paramsJson)
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

  private fun parseParams(paramsJson: String?): PodWorkspaceScanParams {
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
}
