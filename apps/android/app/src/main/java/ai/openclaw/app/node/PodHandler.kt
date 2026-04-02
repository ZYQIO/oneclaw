package ai.openclaw.app.node

import android.content.Context
import ai.openclaw.app.inspectEmbeddedRuntimePod
import ai.openclaw.app.gateway.GatewaySession
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PodHandler(
  private val appContext: Context,
) {
  fun handlePodHealth(_paramsJson: String?): GatewaySession.InvokeResult {
    val inspection = inspectEmbeddedRuntimePod(appContext)
    return GatewaySession.InvokeResult.ok(
      buildPayload(inspection.toJson(), localExecutionAvailable = inspection.ready).toString(),
    )
  }

  private fun buildPayload(
    inspection: JsonObject,
    localExecutionAvailable: Boolean,
  ): JsonObject =
    buildJsonObject {
      put("command", JsonPrimitive("pod.health"))
      put("localExecutionAvailable", JsonPrimitive(localExecutionAvailable))
      inspection.forEach { (key, value) -> put(key, value) }
    }
}
