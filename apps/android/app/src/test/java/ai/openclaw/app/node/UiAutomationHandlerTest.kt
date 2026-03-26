package ai.openclaw.app.node

import ai.openclaw.app.accessibility.LocalHostUiAutomationStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

  @Test
  fun handleWaitForText_returnsMatchedSnapshotWhenTextAppears() =
    runTest {
      var nowMs = 0L
      var polls = 0
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
            polls += 1
            if (polls < 2) {
              buildJsonObject {
                put("packageName", "com.example.app")
                put("nodeCount", 0)
                put("truncated", false)
                put("visibleText", buildJsonArray {})
                put("nodes", buildJsonArray {})
              }
            } else {
              buildJsonObject {
                put("packageName", "com.example.app")
                put("nodeCount", 1)
                put("truncated", false)
                put("visibleText", buildJsonArray { add(JsonPrimitive("Continue")) })
                put(
                  "nodes",
                  buildJsonArray {
                    add(
                      buildJsonObject {
                        put("text", "Continue")
                      },
                    )
                  },
                )
              }
            }
          },
          monotonicClockMs = { nowMs },
          sleeper = { sleepMs -> nowMs += sleepMs },
        )

      val result =
        handler.handleWaitForText(
          """{"text":"continue","timeoutMs":500,"pollIntervalMs":100,"ignoreCase":true}""",
        )

      assertTrue(result.ok)
      val payload = json.parseToJsonElement(result.payloadJson!!).jsonObject
      val wait = payload.getValue("wait").jsonObject
      assertTrue(wait.getValue("matched").jsonPrimitive.boolean)
      assertEquals("Continue", wait.getValue("matchedText").jsonPrimitive.content)
      assertEquals(100, wait.getValue("elapsedMs").jsonPrimitive.content.toInt())
    }

  @Test
  fun handleWaitForText_timesOutWhenTextNeverAppears() =
    runTest {
      var nowMs = 0L
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
              put("nodeCount", 1)
              put("truncated", false)
              put("visibleText", buildJsonArray { add(JsonPrimitive("Settings")) })
              put(
                "nodes",
                buildJsonArray {
                  add(
                    buildJsonObject {
                      put("text", "Settings")
                    },
                  )
                },
              )
            }
          },
          monotonicClockMs = { nowMs },
          sleeper = { sleepMs -> nowMs += sleepMs },
        )

      val result =
        handler.handleWaitForText(
          """{"text":"Continue","timeoutMs":200,"pollIntervalMs":100}""",
        )

      assertFalse(result.ok)
      assertEquals("UI_WAIT_TIMEOUT", result.error?.code)
      assertTrue(result.error?.message.orEmpty().contains("Continue"))
    }

  @Test
  fun handleWaitForText_failsClearlyWhenServiceIsDisabled() =
    runTest {
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
          monotonicClockMs = { 0L },
          sleeper = { },
        )

      val result = handler.handleWaitForText("""{"text":"Continue"}""")

      assertFalse(result.ok)
      assertEquals("UI_AUTOMATION_DISABLED", result.error?.code)
    }
}
