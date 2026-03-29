package ai.openclaw.app.node

import ai.openclaw.app.accessibility.LocalHostUiAutomationStatus
import ai.openclaw.app.accessibility.UiAutomationInputTextRequest
import ai.openclaw.app.accessibility.UiAutomationInputTextResult
import ai.openclaw.app.accessibility.UiAutomationSwipeRequest
import ai.openclaw.app.accessibility.UiAutomationSwipeResult
import ai.openclaw.app.accessibility.UiAutomationTapRequest
import ai.openclaw.app.accessibility.UiAutomationTapResult
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

  @Test
  fun handleLaunchApp_passesPackageNameToInjectedAction() {
    var capturedPackageName: String? = null
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
        launchAppAction = { packageName ->
          capturedPackageName = packageName
          UiAutomationLaunchAppResult(
            launched = true,
            packageName = packageName,
            activityClassName = "com.example.app.MainActivity",
          )
        },
      )

    val result = handler.handleLaunchApp("""{"packageName":"com.example.app"}""")

    assertTrue(result.ok)
    assertEquals("com.example.app", capturedPackageName)
    val payload = json.parseToJsonElement(result.payloadJson!!).jsonObject
    assertEquals("launchApp", payload.getValue("action").jsonPrimitive.content)
    assertEquals("com.example.app", payload.getValue("packageName").jsonPrimitive.content)
    assertEquals(
      "com.example.app.MainActivity",
      payload.getValue("activityClassName").jsonPrimitive.content,
    )
  }

  @Test
  fun handleLaunchApp_requiresPackageName() {
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

    val result = handler.handleLaunchApp("""{"packageName":"   "}""")

    assertFalse(result.ok)
    assertEquals("INVALID_REQUEST", result.error?.code)
    assertTrue(result.error?.message.orEmpty().contains("packageName is required"))
  }

  @Test
  fun handleLaunchApp_surfacesLaunchFailuresClearly() {
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
        launchAppAction = { packageName ->
          UiAutomationLaunchAppResult(
            launched = false,
            packageName = packageName,
            errorCode = "APP_NOT_LAUNCHABLE",
            reason = "Package `com.example.app` is installed but has no launchable activity.",
          )
        },
      )

    val result = handler.handleLaunchApp("""{"packageName":"com.example.app"}""")

    assertFalse(result.ok)
    assertEquals("APP_NOT_LAUNCHABLE", result.error?.code)
    assertTrue(result.error?.message.orEmpty().contains("no launchable activity"))
  }

  @Test
  fun handleInputText_passesRequestToInjectedAction() {
    var capturedRequest: UiAutomationInputTextRequest? = null
    val handler =
      UiAutomationHandler(
        readinessSnapshot = {
          LocalHostUiAutomationStatus(
            enabled = true,
            serviceConnected = true,
            available = true,
          )
        },
        activeWindowSnapshot = { null },
        performInputTextAction = { request ->
          capturedRequest = request
          UiAutomationInputTextResult(
            performed = true,
            strategy = "selector_editable",
            packageName = "com.example.app",
            resourceId = "com.example.app:id/search",
            valueLength = request.value.length,
          )
        },
      )

    val result =
      handler.handleInputText(
        """{"value":"OpenClaw","resourceId":"com.example.app:id/search","packageName":"com.example.app","matchMode":"exact","index":1}""",
      )

    assertTrue(result.ok)
    assertEquals("OpenClaw", capturedRequest?.value)
    assertEquals("com.example.app:id/search", capturedRequest?.resourceId)
    assertEquals("com.example.app", capturedRequest?.packageName)
    assertEquals(true, capturedRequest?.exactMatch)
    assertEquals(1, capturedRequest?.index)
    val payload = json.parseToJsonElement(result.payloadJson!!).jsonObject
    assertEquals("inputText", payload.getValue("action").jsonPrimitive.content)
    assertEquals(8, payload.getValue("valueLength").jsonPrimitive.content.toInt())
  }

  @Test
  fun handleInputText_requiresValueField() {
    val handler =
      UiAutomationHandler(
        readinessSnapshot = {
          LocalHostUiAutomationStatus(
            enabled = true,
            serviceConnected = true,
            available = true,
          )
        },
        activeWindowSnapshot = { null },
      )

    val result = handler.handleInputText("""{"resourceId":"com.example.app:id/search"}""")

    assertFalse(result.ok)
    assertEquals("INVALID_REQUEST", result.error?.code)
    assertTrue(result.error?.message.orEmpty().contains("with value"))
  }

  @Test
  fun handleInputText_allowsEmptyStringToClearFocusedField() {
    var capturedRequest: UiAutomationInputTextRequest? = null
    val handler =
      UiAutomationHandler(
        readinessSnapshot = {
          LocalHostUiAutomationStatus(
            enabled = true,
            serviceConnected = true,
            available = true,
          )
        },
        activeWindowSnapshot = { null },
        performInputTextAction = { request ->
          capturedRequest = request
          UiAutomationInputTextResult(
            performed = true,
            strategy = "focused_editable",
            packageName = "com.example.app",
            valueLength = request.value.length,
          )
        },
      )

    val result = handler.handleInputText("""{"value":"","packageName":"com.example.app"}""")

    assertTrue(result.ok)
    assertEquals("", capturedRequest?.value)
    val payload = json.parseToJsonElement(result.payloadJson!!).jsonObject
    assertEquals(0, payload.getValue("valueLength").jsonPrimitive.content.toInt())
  }

  @Test
  fun handleInputText_surfacesServiceFailuresClearly() {
    val handler =
      UiAutomationHandler(
        readinessSnapshot = {
          LocalHostUiAutomationStatus(
            enabled = true,
            serviceConnected = true,
            available = true,
          )
        },
        activeWindowSnapshot = { null },
        performInputTextAction = {
          UiAutomationInputTextResult(
            performed = false,
            errorCode = "UI_TARGET_NOT_FOUND",
            reason = "No editable accessibility node is available for ui.inputText.",
          )
        },
      )

    val result = handler.handleInputText("""{"value":"OpenClaw"}""")

    assertFalse(result.ok)
    assertEquals("UI_TARGET_NOT_FOUND", result.error?.code)
    assertTrue(result.error?.message.orEmpty().contains("editable accessibility node"))
  }

  @Test
  fun handleTap_passesSelectorRequestToInjectedAction() {
    var capturedRequest: UiAutomationTapRequest? = null
    val handler =
      UiAutomationHandler(
        readinessSnapshot = {
          LocalHostUiAutomationStatus(
            enabled = true,
            serviceConnected = true,
            available = true,
          )
        },
        activeWindowSnapshot = { null },
        performTapAction = { request ->
          capturedRequest = request
          UiAutomationTapResult(
            performed = true,
            strategy = "node_click",
            packageName = "com.example.app",
            matchedText = "Continue",
            x = 120.0,
            y = 320.0,
          )
        },
      )

    val result =
      handler.handleTap(
        """{"text":"Continue","packageName":"com.example.app","matchMode":"exact","index":1}""",
      )

    assertTrue(result.ok)
    val request = capturedRequest
    org.junit.Assert.assertNotNull(request)
    assertEquals("Continue", request?.text)
    assertEquals("com.example.app", request?.packageName)
    assertEquals(true, request?.exactMatch)
    assertEquals(1, request?.index)
    val payload = json.parseToJsonElement(result.payloadJson!!).jsonObject
    assertEquals("tap", payload.getValue("action").jsonPrimitive.content)
    assertEquals("Continue", payload.getValue("matchedText").jsonPrimitive.content)
  }

  @Test
  fun handleTap_rejectsMixedCoordinateAndSelectorModes() {
    val handler =
      UiAutomationHandler(
        readinessSnapshot = {
          LocalHostUiAutomationStatus(
            enabled = true,
            serviceConnected = true,
            available = true,
          )
        },
        activeWindowSnapshot = { null },
      )

    val result = handler.handleTap("""{"x":12,"y":34,"text":"Continue"}""")

    assertFalse(result.ok)
    assertEquals("INVALID_REQUEST", result.error?.code)
  }

  @Test
  fun handleTap_surfacesTargetLookupFailuresClearly() {
    val handler =
      UiAutomationHandler(
        readinessSnapshot = {
          LocalHostUiAutomationStatus(
            enabled = true,
            serviceConnected = true,
            available = true,
          )
        },
        activeWindowSnapshot = { null },
        performTapAction = {
          UiAutomationTapResult(
            performed = false,
            errorCode = "UI_TARGET_NOT_FOUND",
            reason = "No matching accessibility node was found for the requested tap selector.",
          )
        },
      )

    val result = handler.handleTap("""{"text":"Continue"}""")

    assertFalse(result.ok)
    assertEquals("UI_TARGET_NOT_FOUND", result.error?.code)
    assertTrue(result.error?.message.orEmpty().contains("requested tap selector"))
  }

  @Test
  fun handleSwipe_passesCoordinateRequestToInjectedAction() {
    var capturedRequest: UiAutomationSwipeRequest? = null
    val handler =
      UiAutomationHandler(
        readinessSnapshot = {
          LocalHostUiAutomationStatus(
            enabled = true,
            serviceConnected = true,
            available = true,
          )
        },
        activeWindowSnapshot = { null },
        performSwipeAction = { request ->
          capturedRequest = request
          UiAutomationSwipeResult(
            performed = true,
            strategy = "gesture_swipe",
            packageName = "com.example.app",
            startX = request.startX,
            startY = request.startY,
            endX = request.endX,
            endY = request.endY,
            durationMs = request.durationMs,
          )
        },
      )

    val result =
      handler.handleSwipe(
        """{"startX":120,"startY":600,"endX":120,"endY":240,"durationMs":320,"packageName":"com.example.app"}""",
      )

    assertTrue(result.ok)
    assertEquals(120.0, capturedRequest?.startX)
    assertEquals(600.0, capturedRequest?.startY)
    assertEquals(120.0, capturedRequest?.endX)
    assertEquals(240.0, capturedRequest?.endY)
    assertEquals(320L, capturedRequest?.durationMs)
    assertEquals("com.example.app", capturedRequest?.packageName)
    val payload = json.parseToJsonElement(result.payloadJson!!).jsonObject
    assertEquals("swipe", payload.getValue("action").jsonPrimitive.content)
    assertEquals(320, payload.getValue("durationMs").jsonPrimitive.content.toInt())
  }

  @Test
  fun handleSwipe_requiresFullCoordinateSet() {
    val handler =
      UiAutomationHandler(
        readinessSnapshot = {
          LocalHostUiAutomationStatus(
            enabled = true,
            serviceConnected = true,
            available = true,
          )
        },
        activeWindowSnapshot = { null },
      )

    val result = handler.handleSwipe("""{"startX":120,"startY":600,"endX":120}""")

    assertFalse(result.ok)
    assertEquals("INVALID_REQUEST", result.error?.code)
    assertTrue(result.error?.message.orEmpty().contains("startX/startY/endX/endY"))
  }

  @Test
  fun handleSwipe_rejectsZeroDistanceGesture() {
    val handler =
      UiAutomationHandler(
        readinessSnapshot = {
          LocalHostUiAutomationStatus(
            enabled = true,
            serviceConnected = true,
            available = true,
          )
        },
        activeWindowSnapshot = { null },
      )

    val result =
      handler.handleSwipe("""{"startX":120,"startY":600,"endX":120,"endY":600}""")

    assertFalse(result.ok)
    assertEquals("INVALID_REQUEST", result.error?.code)
    assertTrue(result.error?.message.orEmpty().contains("distinct start and end coordinates"))
  }

  @Test
  fun handleSwipe_surfacesGestureFailuresClearly() {
    val handler =
      UiAutomationHandler(
        readinessSnapshot = {
          LocalHostUiAutomationStatus(
            enabled = true,
            serviceConnected = true,
            available = true,
          )
        },
        activeWindowSnapshot = { null },
        performSwipeAction = {
          UiAutomationSwipeResult(
            performed = false,
            errorCode = "UI_ACTION_FAILED",
            reason = "Accessibility service rejected the swipe gesture.",
          )
        },
      )

    val result =
      handler.handleSwipe("""{"startX":120,"startY":600,"endX":120,"endY":240}""")

    assertFalse(result.ok)
    assertEquals("UI_ACTION_FAILED", result.error?.code)
    assertTrue(result.error?.message.orEmpty().contains("swipe gesture"))
  }

  @Test
  fun handleBack_runsInjectedGlobalAction() {
    var backInvoked = false
    val handler =
      UiAutomationHandler(
        readinessSnapshot = {
          LocalHostUiAutomationStatus(
            enabled = true,
            serviceConnected = true,
            available = true,
          )
        },
        activeWindowSnapshot = { null },
        performBackAction = {
          backInvoked = true
          true
        },
      )

    val result = handler.handleBack(null)

    assertTrue(backInvoked)
    assertTrue(result.ok)
    val payload = json.parseToJsonElement(result.payloadJson!!).jsonObject
    assertEquals("back", payload.getValue("action").jsonPrimitive.content)
  }

  @Test
  fun handleHome_failsClearlyWhenGlobalActionRejected() {
    val handler =
      UiAutomationHandler(
        readinessSnapshot = {
          LocalHostUiAutomationStatus(
            enabled = true,
            serviceConnected = true,
            available = true,
          )
        },
        activeWindowSnapshot = { null },
        performHomeAction = { false },
      )

    val result = handler.handleHome(null)

    assertFalse(result.ok)
    assertEquals("UI_ACTION_FAILED", result.error?.code)
  }
}
