package ai.openclaw.app.host

import ai.openclaw.app.gateway.GatewaySession
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalHostNodesToolingTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun remoteRole_filtersWriteActionsFromNodesToolSchema() {
    val bridge =
      LocalHostNodesToolBridge(
        json = json,
        invoke = { _, _ -> GatewaySession.InvokeResult.ok("""{"ok":true}""") },
        allowAdvancedRemoteCommands = { false },
        allowWriteRemoteCommands = { false },
      )

    val tools = bridge.toolsForRole(role = "remote-operator")
    val actions =
      tools
        .single()
        .parameters
        .getValue("properties")
        .jsonObject
        .getValue("action")
        .jsonObject
        .getValue("enum")
        .jsonArray
        .map { it.jsonPrimitive.content }

    assertTrue("device_status" in actions)
    assertTrue("device_permissions" in actions)
    assertTrue("ui_state" in actions)
    assertTrue("ui_wait_for_text" in actions)
    assertFalse("ui_launch_app" in actions)
    assertFalse("ui_input_text" in actions)
    assertFalse("ui_tap" in actions)
    assertFalse("ui_swipe" in actions)
    assertFalse("ui_back" in actions)
    assertFalse("ui_home" in actions)
    assertFalse("sms_send" in actions)
    assertFalse("contacts_add" in actions)
    assertFalse("calendar_add" in actions)
  }

  @Test
  fun remoteRole_blocksWriteActionExecutionWhenWriteTierIsDisabled() =
    runTest {
      var invoked = false
      val bridge =
        LocalHostNodesToolBridge(
          json = json,
          invoke = { _, _ ->
            invoked = true
            GatewaySession.InvokeResult.ok("""{"ok":true}""")
          },
          allowAdvancedRemoteCommands = { false },
          allowWriteRemoteCommands = { false },
        )

      val result =
        bridge.executeToolCall(
          role = "remote-operator",
          name = "nodes",
          argumentsJson = """{"action":"sms_send","to":"+15551234567","message":"Hello"}""",
        )

      assertFalse(invoked)
      assertTrue(result.outputText.contains("COMMAND_DISABLED"))
    }

  @Test
  fun photosLatest_returnsImageInputsForStandaloneVisionWorkflows() =
    runTest {
      val bridge =
        LocalHostNodesToolBridge(
          json = json,
          invoke = { command, _ ->
            org.junit.Assert.assertEquals("photos.latest", command)
            GatewaySession.InvokeResult.ok(
              """{"photos":[{"format":"jpeg","base64":"ZmFrZS1waG90bw==","width":640,"height":480,"createdAt":"2026-03-24T10:00:00Z"}]}""",
            )
          },
          allowAdvancedRemoteCommands = { false },
          allowWriteRemoteCommands = { false },
        )

      val result =
        bridge.executeToolCall(
          role = "operator",
          name = "nodes",
          argumentsJson = """{"action":"photos_latest","limit":1}""",
        )

      assertTrue(result.outputText.contains("imageCount"))
      assertFalse(result.outputText.contains("ZmFrZS1waG90bw=="))
      org.junit.Assert.assertEquals(1, result.imageInputs.size)
      org.junit.Assert.assertEquals("image/jpeg", result.imageInputs.single().mimeType)
      org.junit.Assert.assertEquals("ZmFrZS1waG90bw==", result.imageInputs.single().base64)
    }

  @Test
  fun uiWaitForText_mapsInvokeParams() =
    runTest {
      val bridge =
        LocalHostNodesToolBridge(
          json = json,
          invoke = { command, paramsJson ->
            org.junit.Assert.assertEquals("ui.waitForText", command)
            assertTrue(paramsJson.orEmpty().contains("Continue"))
            assertTrue(paramsJson.orEmpty().contains("packageName"))
            GatewaySession.InvokeResult.ok("""{"ok":true,"wait":{"matched":true}}""")
          },
          allowAdvancedRemoteCommands = { false },
          allowWriteRemoteCommands = { false },
        )

      val result =
        bridge.executeToolCall(
          role = "operator",
          name = "nodes",
          argumentsJson =
            """{"action":"ui_wait_for_text","text":"Continue","timeoutMs":1500,"pollIntervalMs":100,"packageName":"com.example.app"}""",
        )

      assertTrue(result.outputText.contains("matched"))
    }

  @Test
  fun uiWaitForText_acceptsQueryAliasForText() =
    runTest {
      val bridge =
        LocalHostNodesToolBridge(
          json = json,
          invoke = { command, paramsJson ->
            org.junit.Assert.assertEquals("ui.waitForText", command)
            assertTrue(paramsJson.orEmpty().contains("\"text\":\"更多\""))
            GatewaySession.InvokeResult.ok("""{"ok":true,"wait":{"matched":true}}""")
          },
          allowAdvancedRemoteCommands = { false },
          allowWriteRemoteCommands = { false },
        )

      val result =
        bridge.executeToolCall(
          role = "operator",
          name = "nodes",
          argumentsJson =
            """{"action":"ui_wait_for_text","query":"更多","timeoutMs":1500,"pollIntervalMs":100}""",
        )

      assertTrue(result.outputText.contains("matched"))
    }

  @Test
  fun uiBack_mapsInvokeCommandForOperatorSessions() =
    runTest {
      val bridge =
        LocalHostNodesToolBridge(
          json = json,
          invoke = { command, paramsJson ->
            org.junit.Assert.assertEquals("ui.back", command)
            org.junit.Assert.assertEquals("{}", paramsJson)
            GatewaySession.InvokeResult.ok("""{"ok":true,"action":"back"}""")
          },
          allowAdvancedRemoteCommands = { false },
          allowWriteRemoteCommands = { false },
        )

      val result =
        bridge.executeToolCall(
          role = "operator",
          name = "nodes",
          argumentsJson = """{"action":"ui_back"}""",
        )

      assertTrue(result.outputText.contains("back"))
    }

  @Test
  fun uiLaunchApp_mapsPackageNameForOperatorSessions() =
    runTest {
      val bridge =
        LocalHostNodesToolBridge(
          json = json,
          invoke = { command, paramsJson ->
            org.junit.Assert.assertEquals("ui.launchApp", command)
            assertTrue(paramsJson.orEmpty().contains("\"packageName\":\"com.example.app\""))
            GatewaySession.InvokeResult.ok(
              """{"ok":true,"action":"launchApp","packageName":"com.example.app"}""",
            )
          },
          allowAdvancedRemoteCommands = { false },
          allowWriteRemoteCommands = { false },
        )

      val result =
        bridge.executeToolCall(
          role = "operator",
          name = "nodes",
          argumentsJson = """{"action":"ui_launch_app","packageName":"com.example.app"}""",
        )

      assertTrue(result.outputText.contains("launchApp"))
      assertTrue(result.outputText.contains("com.example.app"))
    }

  @Test
  fun uiInputText_mapsValueAndSelectorForOperatorSessions() =
    runTest {
      val bridge =
        LocalHostNodesToolBridge(
          json = json,
          invoke = { command, paramsJson ->
            org.junit.Assert.assertEquals("ui.inputText", command)
            assertTrue(paramsJson.orEmpty().contains("\"value\":\"OpenClaw\""))
            assertTrue(paramsJson.orEmpty().contains("\"resourceId\":\"com.example.app:id/search\""))
            assertTrue(paramsJson.orEmpty().contains("\"packageName\":\"com.example.app\""))
            GatewaySession.InvokeResult.ok(
              """{"ok":true,"action":"inputText","valueLength":8}""",
            )
          },
          allowAdvancedRemoteCommands = { false },
          allowWriteRemoteCommands = { false },
        )

      val result =
        bridge.executeToolCall(
          role = "operator",
          name = "nodes",
          argumentsJson =
            """{"action":"ui_input_text","value":"OpenClaw","resourceId":"com.example.app:id/search","packageName":"com.example.app"}""",
        )

      assertTrue(result.outputText.contains("inputText"))
      assertTrue(result.outputText.contains("valueLength"))
    }

  @Test
  fun remoteRole_includesUiWriteActionsWhenWriteTierIsEnabled() {
    val bridge =
      LocalHostNodesToolBridge(
        json = json,
        invoke = { _, _ -> GatewaySession.InvokeResult.ok("""{"ok":true}""") },
        allowAdvancedRemoteCommands = { false },
        allowWriteRemoteCommands = { true },
      )

    val tools = bridge.toolsForRole(role = "remote-operator")
    val actions =
      tools
        .single()
        .parameters
        .getValue("properties")
        .jsonObject
        .getValue("action")
        .jsonObject
        .getValue("enum")
        .jsonArray
        .map { it.jsonPrimitive.content }

    assertTrue("ui_tap" in actions)
    assertTrue("ui_swipe" in actions)
    assertTrue("ui_back" in actions)
    assertTrue("ui_home" in actions)
    assertTrue("ui_launch_app" in actions)
    assertTrue("ui_input_text" in actions)
  }

  @Test
  fun uiSwipe_mapsCoordinateParamsForOperatorSessions() =
    runTest {
      val bridge =
        LocalHostNodesToolBridge(
          json = json,
          invoke = { command, paramsJson ->
            org.junit.Assert.assertEquals("ui.swipe", command)
            assertTrue(paramsJson.orEmpty().contains("\"startX\":120"))
            assertTrue(paramsJson.orEmpty().contains("\"startY\":600"))
            assertTrue(paramsJson.orEmpty().contains("\"endX\":120"))
            assertTrue(paramsJson.orEmpty().contains("\"endY\":240"))
            assertTrue(paramsJson.orEmpty().contains("\"durationMs\":320"))
            assertTrue(paramsJson.orEmpty().contains("\"packageName\":\"com.example.app\""))
            GatewaySession.InvokeResult.ok("""{"ok":true,"action":"swipe","strategy":"gesture_swipe"}""")
          },
          allowAdvancedRemoteCommands = { false },
          allowWriteRemoteCommands = { false },
        )

      val result =
        bridge.executeToolCall(
          role = "operator",
          name = "nodes",
          argumentsJson =
            """{"action":"ui_swipe","startX":120,"startY":600,"endX":120,"endY":240,"durationMs":320,"packageName":"com.example.app"}""",
        )

      assertTrue(result.outputText.contains("swipe"))
    }

  @Test
  fun uiTap_mapsSelectorParamsForOperatorSessions() =
    runTest {
      val bridge =
        LocalHostNodesToolBridge(
          json = json,
          invoke = { command, paramsJson ->
            org.junit.Assert.assertEquals("ui.tap", command)
            assertTrue(paramsJson.orEmpty().contains("\"text\":\"Continue\""))
            assertTrue(paramsJson.orEmpty().contains("\"packageName\":\"com.example.app\""))
            assertTrue(paramsJson.orEmpty().contains("\"index\":1"))
            GatewaySession.InvokeResult.ok("""{"ok":true,"action":"tap","strategy":"node_click"}""")
          },
          allowAdvancedRemoteCommands = { false },
          allowWriteRemoteCommands = { false },
        )

      val result =
        bridge.executeToolCall(
          role = "operator",
          name = "nodes",
          argumentsJson =
            """{"action":"ui_tap","text":"Continue","packageName":"com.example.app","index":1,"matchMode":"exact"}""",
        )

      assertTrue(result.outputText.contains("tap"))
    }

  @Test
  fun uiTap_acceptsQueryAliasAndIgnoresZeroCoordinateNoise() =
    runTest {
      val bridge =
        LocalHostNodesToolBridge(
          json = json,
          invoke = { command, paramsJson ->
            org.junit.Assert.assertEquals("ui.tap", command)
            assertTrue(paramsJson.orEmpty().contains("\"text\":\"更多\""))
            assertFalse(paramsJson.orEmpty().contains("\"x\":0"))
            assertFalse(paramsJson.orEmpty().contains("\"y\":0"))
            GatewaySession.InvokeResult.ok("""{"ok":true,"action":"tap","strategy":"gesture_tap"}""")
          },
          allowAdvancedRemoteCommands = { false },
          allowWriteRemoteCommands = { false },
        )

      val result =
        bridge.executeToolCall(
          role = "operator",
          name = "nodes",
          argumentsJson =
            """{"action":"ui_tap","query":"更多","x":0,"y":0,"matchMode":"exact"}""",
        )

      assertTrue(result.outputText.contains("tap"))
    }
}
