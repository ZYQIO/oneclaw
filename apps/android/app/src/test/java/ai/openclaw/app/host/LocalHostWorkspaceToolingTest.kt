package ai.openclaw.app.host

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalHostWorkspaceToolingTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun workspaceTool_supportsSearchReplaceCopyMoveAndListWithinPhoneWorkspace() =
    runTest {
      val root = Files.createTempDirectory("openclaw-workspace-test").toFile()
      try {
        val bridge = LocalHostWorkspaceToolBridge(json = json, workspaceRoot = root)

        val writeResult =
          parseObject(
            bridge.executeToolCall(
              role = "operator",
              name = "workspace",
              argumentsJson =
                """{"action":"write","path":"notes/today.md","content":"hello phone workspace\nTODO buy milk"}""",
            ).outputText,
          )
        assertEquals(true, writeResult.getValue("ok").jsonPrimitive.booleanOrNull())
        assertEquals("notes/today.md", writeResult.getValue("path").jsonPrimitive.content)

        val searchResult =
          parseObject(
            bridge.executeToolCall(
              role = "operator",
              name = "workspace",
              argumentsJson = """{"action":"search","path":".","query":"todo"}""",
            ).outputText,
          )
        assertEquals(1, searchResult.getValue("count").jsonPrimitive.content.toInt())
        assertEquals(
          "notes/today.md",
          searchResult.getValue("matches").jsonArray.first().jsonObject.getValue("path").jsonPrimitive.content,
        )
        assertEquals(
          2,
          searchResult.getValue("matches").jsonArray.first().jsonObject.getValue("line").jsonPrimitive.content.toInt(),
        )

        val replaceResult =
          parseObject(
            bridge.executeToolCall(
              role = "operator",
              name = "workspace",
              argumentsJson =
                """{"action":"replace","path":"notes/today.md","oldText":"buy milk","newText":"buy tea"}""",
            ).outputText,
          )
        assertEquals(true, replaceResult.getValue("changed").jsonPrimitive.booleanOrNull())
        assertEquals(1, replaceResult.getValue("replacements").jsonPrimitive.content.toInt())

        val readResult =
          parseObject(
            bridge.executeToolCall(
              role = "operator",
              name = "workspace",
              argumentsJson = """{"action":"read","path":"notes/today.md"}""",
            ).outputText,
          )
        assertTrue(readResult.getValue("content").jsonPrimitive.content.contains("buy tea"))
        assertEquals(false, readResult.getValue("truncated").jsonPrimitive.booleanOrNull())

        val copyResult =
          parseObject(
            bridge.executeToolCall(
              role = "operator",
              name = "workspace",
              argumentsJson =
                """{"action":"copy","path":"notes/today.md","destinationPath":"archive/today-copy.md"}""",
            ).outputText,
          )
        assertEquals(true, copyResult.getValue("copied").jsonPrimitive.booleanOrNull())
        assertTrue(root.resolve("archive/today-copy.md").exists())

        val moveResult =
          parseObject(
            bridge.executeToolCall(
              role = "operator",
              name = "workspace",
              argumentsJson =
                """{"action":"move","path":"archive/today-copy.md","destinationPath":"archive/today-final.md"}""",
            ).outputText,
          )
        assertEquals(true, moveResult.getValue("moved").jsonPrimitive.booleanOrNull())
        assertTrue(root.resolve("archive/today-final.md").exists())
        assertFalse(root.resolve("archive/today-copy.md").exists())

        val listResult =
          parseObject(
            bridge.executeToolCall(
              role = "operator",
              name = "workspace",
              argumentsJson = """{"action":"list","path":".","recursive":true}""",
            ).outputText,
          )
        val paths = listResult.getValue("entries").jsonArray.map { it.jsonObject.getValue("path").jsonPrimitive.content }
        assertTrue("notes" in paths)
        assertTrue("notes/today.md" in paths)
        assertTrue("archive/today-final.md" in paths)
      } finally {
        root.deleteRecursively()
      }
    }

  @Test
  fun workspaceTool_rejectsPathTraversalOutsideWorkspace() =
    runTest {
      val root = Files.createTempDirectory("openclaw-workspace-test").toFile()
      try {
        val bridge = LocalHostWorkspaceToolBridge(json = json, workspaceRoot = root)

        val result =
          parseObject(
            bridge.executeToolCall(
              role = "operator",
              name = "workspace",
              argumentsJson = """{"action":"write","path":"../escape.txt","content":"nope"}""",
            ).outputText,
          )

        assertEquals(false, result.getValue("ok").jsonPrimitive.booleanOrNull())
        assertTrue(result.getValue("error").jsonObject.getValue("message").jsonPrimitive.content.contains("file path required"))
        assertFalse(root.parentFile?.resolve("escape.txt")?.exists() == true)
      } finally {
        root.deleteRecursively()
      }
    }

  @Test
  fun workspaceTool_blocksRemoteWritesUntilWriteModeIsEnabled() =
    runTest {
      val root = Files.createTempDirectory("openclaw-workspace-test").toFile()
      try {
        val readOnlyBridge = LocalHostWorkspaceToolBridge(json = json, workspaceRoot = root)
        val readOnlyActions = actionEnum(readOnlyBridge)
        assertTrue("search" in readOnlyActions)
        assertFalse("write" in readOnlyActions)
        assertFalse("replace" in readOnlyActions)

        val blockedResult =
          parseObject(
            readOnlyBridge.executeToolCall(
              role = "remote-operator",
              name = "workspace",
              argumentsJson = """{"action":"write","path":"notes/today.md","content":"blocked"}""",
            ).outputText,
          )
        assertEquals(false, blockedResult.getValue("ok").jsonPrimitive.booleanOrNull())
        assertTrue(blockedResult.getValue("error").jsonObject.getValue("message").jsonPrimitive.content.contains("COMMAND_DISABLED"))

        val writableBridge =
          LocalHostWorkspaceToolBridge(
            json = json,
            workspaceRoot = root,
            allowWriteRemoteActions = { true },
          )
        val writableActions = actionEnum(writableBridge)
        assertTrue("write" in writableActions)
        assertTrue("replace" in writableActions)
        assertTrue("move" in writableActions)
        assertTrue("copy" in writableActions)

        val writeResult =
          parseObject(
            writableBridge.executeToolCall(
              role = "remote-operator",
              name = "workspace",
              argumentsJson = """{"action":"write","path":"notes/today.md","content":"allowed"}""",
            ).outputText,
          )
        assertEquals(true, writeResult.getValue("ok").jsonPrimitive.booleanOrNull())
        assertTrue(root.resolve("notes/today.md").exists())
      } finally {
        root.deleteRecursively()
      }
    }

  private fun parseObject(raw: String): JsonObject = json.parseToJsonElement(raw).jsonObject

  private fun actionEnum(bridge: LocalHostWorkspaceToolBridge): List<String> {
    return bridge
      .toolsForRole("remote-operator")
      .single()
      .parameters
      .getValue("properties")
      .jsonObject
      .getValue("action")
      .jsonObject
      .getValue("enum")
      .jsonArray
      .map { it.jsonPrimitive.content }
  }
}

private fun kotlinx.serialization.json.JsonPrimitive.booleanOrNull(): Boolean? =
  when (content.trim().lowercase()) {
    "true" -> true
    "false" -> false
    else -> null
  }
