package ai.openclaw.app.host

import android.content.Context
import ai.openclaw.app.SecurePrefs
import ai.openclaw.app.auth.OpenAICodexCredential
import java.util.UUID
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LocalHostRuntimeTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun chatSend_streamsAssistantReplyAndPersistsHistory() =
    runTest {
      val context = RuntimeEnvironment.getApplication()
      val prefs = securePrefs(context, name = "openclaw.node.secure.test.localhost.chat")
      val expectedCredential =
        OpenAICodexCredential(
          access = "access-token",
          refresh = "refresh-token",
          expires = 123456789L,
          accountId = "account-123",
          email = "test@example.com",
        )
      val events = mutableListOf<Pair<String, String?>>()
      val runtime =
        LocalHostRuntime(
          scope = this,
          prefs = prefs,
          json = json,
          codexClient =
            FakeLocalHostResponsesClient { role, sessionId, messages, thinkingLevel, onTextDelta, _ ->
              assertEquals("operator", role)
              UUID.fromString(sessionId)
              assertEquals("low", thinkingLevel)
              assertEquals(1, messages.size)
              assertEquals("user", messages.single().role)
              onTextDelta("Hello from Codex")
              OpenAICodexAssistantReply(
                text = "Hello from Codex",
                responseId = "resp_123",
                credential = expectedCredential,
              )
            },
        )

      runtime.registerClient(role = "operator") { event, payloadJson ->
        events += event to payloadJson
      }

      val sendResult =
        parseObject(
          runtime.request(
            role = "operator",
            method = "chat.send",
            paramsJson = """{"sessionKey":"main","message":"Hi there","thinking":"low","idempotencyKey":"run-1"}""",
            timeoutMs = 15_000,
          ),
        )
      assertEquals("run-1", sendResult.getValue("runId").jsonPrimitive.content)

      advanceUntilIdle()

      val history =
        parseObject(
          runtime.request(
            role = "operator",
            method = "chat.history",
            paramsJson = """{"sessionKey":"main"}""",
            timeoutMs = 15_000,
          ),
        )
      val messages = history.getValue("messages").jsonArray
      assertEquals(2, messages.size)
      assertEquals("user", messages[0].jsonObject.getValue("role").jsonPrimitive.content)
      assertEquals("assistant", messages[1].jsonObject.getValue("role").jsonPrimitive.content)
      assertEquals(
        "Hello from Codex",
        messages[1].jsonObject.getValue("content").jsonArray.first().jsonObject.getValue("text").jsonPrimitive.content,
      )
      assertEquals(expectedCredential, prefs.loadOpenAICodexCredential())

      val chatStates =
        events
          .filter { (event, _) -> event == "chat" }
          .map { (_, payloadJson) -> parseObject(payloadJson!!).getValue("state").jsonPrimitive.content }
      assertEquals(listOf("delta", "final"), chatStates)
      assertTrue(events.any { (event, _) -> event == "agent" })
    }

  @Test
  fun chatAbort_emitsAbortedOnce() =
    runTest {
      val context = RuntimeEnvironment.getApplication()
      val prefs = securePrefs(context, name = "openclaw.node.secure.test.localhost.abort")
      val events = mutableListOf<Pair<String, String?>>()
      val runtime =
        LocalHostRuntime(
          scope = this,
          prefs = prefs,
          json = json,
          codexClient =
            FakeLocalHostResponsesClient { role, sessionId, _, _, _, _ ->
              assertEquals("operator", role)
              UUID.fromString(sessionId)
              awaitCancellation()
            },
        )

      runtime.registerClient(role = "operator") { event, payloadJson ->
        events += event to payloadJson
      }

      runtime.request(
        role = "operator",
        method = "chat.send",
        paramsJson = """{"sessionKey":"main","message":"Hi there","idempotencyKey":"run-abort"}""",
        timeoutMs = 15_000,
      )
      advanceUntilIdle()

      runtime.request(
        role = "operator",
        method = "chat.abort",
        paramsJson = """{"sessionKey":"main","runId":"run-abort"}""",
        timeoutMs = 15_000,
      )
      advanceUntilIdle()

      val abortedEvents =
        events.filter { (event, payloadJson) ->
          event == "chat" && parseObject(payloadJson!!).getValue("state").jsonPrimitive.content == "aborted"
        }
      assertEquals(1, abortedEvents.size)
    }

  @Test
  fun chatSend_emitsToolLifecycleEvents() =
    runTest {
      val context = RuntimeEnvironment.getApplication()
      val prefs = securePrefs(context, name = "openclaw.node.secure.test.localhost.tools")
      val events = mutableListOf<Pair<String, String?>>()
      val runtime =
        LocalHostRuntime(
          scope = this,
          prefs = prefs,
          json = json,
          codexClient =
            FakeLocalHostResponsesClient { role, sessionId, _, _, onTextDelta, onToolEvent ->
              assertEquals("operator", role)
              UUID.fromString(sessionId)
              onToolEvent(
                LocalHostToolCallEvent(
                  toolCallId = "call_nodes_1",
                  name = "nodes",
                  args = parseObject("""{"action":"device_status"}"""),
                  phase = "start",
                ),
              )
              onToolEvent(
                LocalHostToolCallEvent(
                  toolCallId = "call_nodes_1",
                  name = "nodes",
                  args = null,
                  phase = "result",
                ),
              )
              onTextDelta("Tool-backed reply")
              OpenAICodexAssistantReply(
                text = "Tool-backed reply",
                responseId = "resp_tool_123",
                credential =
                  OpenAICodexCredential(
                    access = "access-token",
                    refresh = "refresh-token",
                    expires = 123456789L,
                    accountId = "account-123",
                    email = "test@example.com",
                  ),
              )
            },
        )

      runtime.registerClient(role = "operator") { event, payloadJson ->
        events += event to payloadJson
      }

      runtime.request(
        role = "operator",
        method = "chat.send",
        paramsJson = """{"sessionKey":"main","message":"Check device","idempotencyKey":"run-tool"}""",
        timeoutMs = 15_000,
      )
      advanceUntilIdle()

      val toolEvents =
        events.filter { (event, payloadJson) ->
          event == "agent" && parseObject(payloadJson!!).getValue("stream").jsonPrimitive.content == "tool"
        }
      assertEquals(listOf("start", "result"), toolEvents.map { (_, payloadJson) ->
        parseObject(payloadJson!!).getValue("data").jsonObject.getValue("phase").jsonPrimitive.content
      })
      val firstToolArgs =
        parseObject(toolEvents.first().second!!)
          .getValue("data")
          .jsonObject
          .getValue("args")
          .jsonObject
      assertEquals("device_status", firstToolArgs.getValue("action").jsonPrimitive.content)
    }

  @Test
  fun statusSnapshot_includesDeploymentStatusWhenProvided() =
    runTest {
      val context = RuntimeEnvironment.getApplication()
      val prefs = securePrefs(context, name = "openclaw.node.secure.test.localhost.status")
      val runtime =
        LocalHostRuntime(
          scope = this,
          prefs = prefs,
          json = json,
          deploymentStatusProvider = {
            buildJsonObject {
              put("dedicatedEnabled", JsonPrimitive(true))
              put("batteryOptimizationIgnored", JsonPrimitive(false))
            }
          },
          embeddedRuntimePodStatusProvider = {
            buildJsonObject {
              put("available", JsonPrimitive(true))
              put("ready", JsonPrimitive(false))
              put("reason", JsonPrimitive("not_extracted"))
              put("assetManifestPresent", JsonPrimitive(true))
            }
          },
          uiAutomationStatusProvider = {
            buildJsonObject {
              put("enabled", JsonPrimitive(true))
              put("serviceConnected", JsonPrimitive(true))
              put("available", JsonPrimitive(true))
            }
          },
          codexClient = FakeLocalHostResponsesClient { _, _, _, _, _, _ ->
            error("status snapshot should not call codex client")
          },
        )

      val snapshot = runtime.statusSnapshot()
      val deployment = snapshot.getValue("deployment").jsonObject
      val embeddedRuntimePod = snapshot.getValue("embeddedRuntimePod").jsonObject
      val uiAutomation = snapshot.getValue("uiAutomation").jsonObject
      assertEquals(true, deployment.getValue("dedicatedEnabled").jsonPrimitive.boolean)
      assertEquals(false, deployment.getValue("batteryOptimizationIgnored").jsonPrimitive.boolean)
      assertEquals(true, snapshot.getValue("embeddedRuntimePodAvailable").jsonPrimitive.boolean)
      assertEquals("not_extracted", embeddedRuntimePod.getValue("reason").jsonPrimitive.content)
      assertEquals(true, snapshot.getValue("uiAutomationAvailable").jsonPrimitive.boolean)
      assertEquals(true, uiAutomation.getValue("enabled").jsonPrimitive.boolean)
      assertEquals(true, uiAutomation.getValue("serviceConnected").jsonPrimitive.boolean)
    }

  private fun securePrefs(context: Context, name: String): SecurePrefs {
    val securePrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    securePrefs.edit().clear().commit()
    return SecurePrefs(context, securePrefsOverride = securePrefs)
  }

  private fun parseObject(raw: String): JsonObject = json.parseToJsonElement(raw).jsonObject
}

private class FakeLocalHostResponsesClient(
  private val block: suspend (
    role: String,
    sessionId: String,
    messages: List<LocalHostMessage>,
    thinkingLevel: String,
    onTextDelta: suspend (fullText: String) -> Unit,
    onToolEvent: suspend (LocalHostToolCallEvent) -> Unit,
  ) -> OpenAICodexAssistantReply,
) : LocalHostResponsesClient {
  override suspend fun streamReply(
    role: String,
    sessionId: String,
    messages: List<LocalHostMessage>,
    thinkingLevel: String,
    onTextDelta: suspend (fullText: String) -> Unit,
    onToolEvent: suspend (LocalHostToolCallEvent) -> Unit,
  ): OpenAICodexAssistantReply {
    return block(role, sessionId, messages, thinkingLevel, onTextDelta, onToolEvent)
  }
}
