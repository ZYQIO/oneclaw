package ai.openclaw.app.node

import ai.openclaw.app.accessibility.LocalHostUiAutomationStatus
import ai.openclaw.app.gateway.GatewaySession
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

class UiAutomationHandler(
  private val readinessSnapshot: () -> LocalHostUiAutomationStatus,
  private val activeWindowSnapshot: () -> JsonObject?,
) {
  fun handleUiState(_paramsJson: String?): GatewaySession.InvokeResult {
    val readiness = readinessSnapshot()
    val activeWindow = activeWindowSnapshot()

    return GatewaySession.InvokeResult.ok(
      buildJsonObject {
        put("enabled", JsonPrimitive(readiness.enabled))
        put("serviceConnected", JsonPrimitive(readiness.serviceConnected))
        put("available", JsonPrimitive(readiness.available))
        put("statusText", JsonPrimitive(readiness.statusText))
        put("detailText", JsonPrimitive(readiness.detailText))
        put("activeWindowAvailable", JsonPrimitive(activeWindow != null))
        if (activeWindow != null) {
          activeWindow.forEach { (key, value) -> put(key, value) }
        } else {
          put(
            "reason",
            JsonPrimitive(
              when {
                !readiness.enabled -> "Accessibility service is not enabled."
                !readiness.serviceConnected -> "Accessibility service is enabled but not yet bound."
                else -> "No active accessibility window is available."
              },
            ),
          )
          put("nodeCount", JsonPrimitive(0))
          put("truncated", JsonPrimitive(false))
          put("visibleText", buildJsonArray {})
          put("nodes", buildJsonArray {})
        }
      }.toString(),
    )
  }
}
