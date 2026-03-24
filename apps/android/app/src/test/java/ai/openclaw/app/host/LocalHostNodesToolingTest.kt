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
}
