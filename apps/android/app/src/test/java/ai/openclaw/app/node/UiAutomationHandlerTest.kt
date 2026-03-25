package ai.openclaw.app.node

import ai.openclaw.app.accessibility.LocalHostUiAutomationStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiAutomationHandlerTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun handleUiState_reportsReadinessWhenServiceIsDisabled() {
    val handler =
      UiAutomationHandler(
        readinessSnapshot = {
          LocalHostUiAutomationStatus(
            enabled = false,
            serviceConnected = false,
            available = false,
          )
        },
        activeWindowSnapshot = { null },
      )

    val result = handler.handleUiState(null)

    assertTrue(result.ok)
    val payload = json.parseToJsonElement(result.payloadJson!!).jsonObject
    assertEquals(false, payload.getValue("available").jsonPrimitive.boolean)
    assertTrue(payload.getValue("reason").jsonPrimitive.content.contains("not enabled"))
    assertEquals("[]", payload.getValue("nodes").toString())
  }

  @Test
  fun handleUiState_mergesActiveWindowSnapshot() {
    val handler =
      UiAutomationHandler(
        readinessSnapshot = {
          LocalHostUiAutomationStatus(
            enabled = true,
            serviceConnected = true,
            available = true,
          )
        },
        activeWindowSnapshot = {
          buildJsonObject {
            put("packageName", "com.example.app")
            put("nodeCount", 2)
            put("truncated", false)
            put("visibleText", buildJsonArray { add(JsonPrimitive("Hello")) })
            put(
              "nodes",
              buildJsonArray {
                add(
                  buildJsonObject {
                    put("className", "android.widget.TextView")
                  },
                )
              },
            )
          }
        },
      )

    val result = handler.handleUiState(null)

    assertTrue(result.ok)
    val payload = json.parseToJsonElement(result.payloadJson!!).jsonObject
    assertEquals(true, payload.getValue("activeWindowAvailable").jsonPrimitive.boolean)
    assertEquals("com.example.app", payload.getValue("packageName").jsonPrimitive.content)
    assertTrue(payload.getValue("nodes").toString().contains("TextView"))
  }
}
