package ai.openclaw.app.host

import android.content.Context
import ai.openclaw.app.SecurePrefs
import ai.openclaw.app.auth.OpenAICodexCredential
import ai.openclaw.app.chat.ChatMessageContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OpenAICodexResponsesClientTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun streamReply_sendsUserMessagesAsResponseItems() =
    runTest {
      val server = MockWebServer()
      server.enqueue(
        MockResponse()
          .setResponseCode(200)
          .addHeader("Content-Type", "text/event-stream")
          .setBody(
            """
            data: {"type":"response.output_text.delta","delta":"Android local host is working."}

            data: {"type":"response.completed","response":{"id":"resp_123","output":[{"type":"message","content":[{"type":"output_text","text":"Android local host is working."}]}]}}

            """.trimIndent(),
          ),
      )
      server.start()

      try {
        val context = RuntimeEnvironment.getApplication()
        val prefs = securePrefs(context, name = "openclaw.node.secure.test.codex.responses")
        prefs.saveOpenAICodexCredential(
          OpenAICodexCredential(
            access = fakeJwt(accountId = "acct_123", email = "person@example.com"),
            refresh = "refresh-token",
            expires = System.currentTimeMillis() + 60_000,
            accountId = "acct_123",
            email = "person@example.com",
          ),
        )

        val client =
          OpenAICodexResponsesClient(
            prefs = prefs,
            json = json,
            client = OkHttpClient(),
            responsesUrl = server.url("/backend-api/codex/responses").toString(),
          )

        val reply =
          client.streamReply(
            sessionId = "session-123",
            messages =
              listOf(
                LocalHostMessage(
                  role = "user",
                  content = listOf(ChatMessageContent(type = "text", text = "Reply exactly once.")),
                  timestampMs = 1L,
                ),
              ),
            thinkingLevel = "off",
            onTextDelta = {},
          )

        assertEquals("Android local host is working.", reply.text)

        val request = server.takeRequest()
        val payload = json.parseToJsonElement(request.body.readUtf8()).jsonObject
        val inputItems = payload.getValue("input").jsonArray
        val firstInput = inputItems.first().jsonObject
        val firstContent = firstInput.getValue("content").jsonArray.first().jsonObject

        assertEquals("message", firstInput.getValue("type").jsonPrimitive.content)
        assertEquals("user", firstInput.getValue("role").jsonPrimitive.content)
        assertEquals("input_text", firstContent.getValue("type").jsonPrimitive.content)
        assertEquals("Reply exactly once.", firstContent.getValue("text").jsonPrimitive.content)
        assertEquals("session-123", request.getHeader("x-client-request-id"))
        assertEquals("session-123", request.getHeader("session_id"))
        assertEquals("application/json", request.getHeader("Content-Type"))
        assertEquals("auto", payload.getValue("tool_choice").jsonPrimitive.content)
        assertEquals(false, payload.getValue("parallel_tool_calls").jsonPrimitive.boolean)
        assertEquals(0, payload.getValue("tools").jsonArray.size)
        assertEquals("session-123", payload.getValue("prompt_cache_key").jsonPrimitive.content)
        assertEquals(0, payload.getValue("include").jsonArray.size)
        assertTrue(
          "instructions should use the Codex baseline prompt",
          payload.getValue("instructions").jsonPrimitive.content.startsWith("You are Codex, based on GPT-5."),
        )
        assertTrue("text tuning should be omitted for Codex compatibility", "text" !in payload)
      } finally {
        server.shutdown()
      }
    }

  @Test
  fun streamReply_surfacesStructuredErrorDetailsWhenCodexRejectsRequest() =
    runTest {
      val server = MockWebServer()
      server.enqueue(
        MockResponse()
          .setResponseCode(400)
          .addHeader("Content-Type", "application/json")
          .addHeader("x-request-id", "req_123")
          .setBody(
            """
            {"error":{"type":"invalid_request_error","code":"instructions_required","details":"instructions must be set"}}
            """.trimIndent(),
          ),
      )
      server.start()

      try {
        val context = RuntimeEnvironment.getApplication()
        val prefs = securePrefs(context, name = "openclaw.node.secure.test.codex.responses.error")
        prefs.saveOpenAICodexCredential(
          OpenAICodexCredential(
            access = fakeJwt(accountId = "acct_123", email = "person@example.com"),
            refresh = "refresh-token",
            expires = System.currentTimeMillis() + 60_000,
            accountId = "acct_123",
            email = "person@example.com",
          ),
        )

        val client =
          OpenAICodexResponsesClient(
            prefs = prefs,
            json = json,
            client = OkHttpClient(),
            responsesUrl = server.url("/backend-api/codex/responses").toString(),
          )

        try {
          client.streamReply(
            sessionId = "session-123",
            messages =
              listOf(
                LocalHostMessage(
                  role = "user",
                  content = listOf(ChatMessageContent(type = "text", text = "Reply exactly once.")),
                  timestampMs = 1L,
                ),
              ),
            thinkingLevel = "off",
            onTextDelta = {},
          )
          fail("Expected streamReply to throw on a 400 response")
        } catch (error: IllegalStateException) {
          assertTrue(error.message.orEmpty().contains("instructions must be set"))
          assertTrue(error.message.orEmpty().contains("errorType=invalid_request_error"))
          assertTrue(error.message.orEmpty().contains("errorCode=instructions_required"))
          assertTrue(error.message.orEmpty().contains("requestId=req_123"))
        }
      } finally {
        server.shutdown()
      }
    }

  private fun securePrefs(context: Context, name: String): SecurePrefs {
    val securePrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    securePrefs.edit().clear().commit()
    return SecurePrefs(context, securePrefsOverride = securePrefs)
  }

  private fun fakeJwt(
    accountId: String,
    email: String,
  ): String {
    val payload =
      """{"email":"$email","https://api.openai.com/auth":{"chatgpt_account_id":"$accountId"}}"""
    val header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
    val encodedPayload =
      java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
    return "$header.$encodedPayload.signature"
  }
}
