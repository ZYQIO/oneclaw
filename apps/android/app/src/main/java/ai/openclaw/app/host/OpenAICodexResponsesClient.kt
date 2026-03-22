package ai.openclaw.app.host

import android.util.Base64
import ai.openclaw.app.SecurePrefs
import ai.openclaw.app.auth.OpenAICodexCredential
import ai.openclaw.app.chat.ChatMessageContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class OpenAICodexAssistantReply(
  val text: String,
  val responseId: String?,
  val credential: OpenAICodexCredential,
)

interface LocalHostResponsesClient {
  suspend fun streamReply(
    sessionId: String,
    messages: List<LocalHostMessage>,
    thinkingLevel: String,
    onTextDelta: suspend (fullText: String) -> Unit,
  ): OpenAICodexAssistantReply
}

class OpenAICodexResponsesClient(
  private val prefs: SecurePrefs,
  private val json: Json,
  private val client: OkHttpClient = OkHttpClient(),
) : LocalHostResponsesClient {
  companion object {
    private const val authorizeClientId = "app_EMoamEEZ73f0CkXaXp7hrann"
    private const val tokenUrl = "https://auth.openai.com/oauth/token"
    private const val responsesUrl = "https://chatgpt.com/backend-api/codex/responses"
    private const val defaultModelId = "gpt-5.4"
  }

  override suspend fun streamReply(
    sessionId: String,
    messages: List<LocalHostMessage>,
    thinkingLevel: String,
    onTextDelta: suspend (fullText: String) -> Unit,
  ): OpenAICodexAssistantReply =
    withContext(Dispatchers.IO) {
      var credential = prefs.loadOpenAICodexCredential()
        ?: throw IllegalStateException("OpenAI Codex login required")
      if (credential.expires <= System.currentTimeMillis() + 30_000) {
        credential = refreshCredential(credential)
        prefs.saveOpenAICodexCredential(credential)
      }

      val accountId = extractAccountId(credential.access) ?: credential.accountId
      if (accountId.isBlank()) {
        throw IllegalStateException("OpenAI Codex credential is missing accountId")
      }

      val requestBody =
        buildRequestBody(
          sessionId = sessionId,
          messages = messages,
          thinkingLevel = thinkingLevel,
        ).toString()
          .toRequestBody("application/json; charset=utf-8".toMediaType())

      val request =
        Request.Builder()
          .url(responsesUrl)
          .post(requestBody)
          .header("Authorization", "Bearer ${credential.access}")
          .header("chatgpt-account-id", accountId)
          .header("originator", "pi")
          .header("User-Agent", "OpenClaw Android Host")
          .header("OpenAI-Beta", "responses=experimental")
          .header("accept", "text/event-stream")
          .header("content-type", "application/json")
          .header("session_id", sessionId)
          .build()

      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          val body = response.body?.string().orEmpty()
          throw IllegalStateException(parseCodexError(response.code, body))
        }

        val reader = response.body?.charStream()?.buffered()
          ?: throw IllegalStateException("OpenAI Codex returned no response body")
        val eventLines = mutableListOf<String>()
        val fullText = StringBuilder()
        var responseId: String? = null
        var finalText: String? = null

        fun consumeEvent(raw: String) {
          val trimmed = raw.trim()
          if (trimmed.isEmpty() || trimmed == "[DONE]") return
          val root = json.parseToJsonElement(trimmed) as? JsonObject ?: return
          when (root["type"].asStringOrNull()) {
            "response.created" -> {
              responseId = root["response"].asObjectOrNull()?.get("id").asStringOrNull()
            }
            "response.output_text.delta" -> {
              val delta = root["delta"].asStringOrNull().orEmpty()
              if (delta.isEmpty()) return
              fullText.append(delta)
            }
            "response.output_item.done" -> {
              val item = root["item"].asObjectOrNull() ?: return
              if (item["type"].asStringOrNull() != "message") return
              val content = item["content"] as? JsonArray ?: return
              val text = extractMessageText(content)
              if (text.isNotBlank()) {
                finalText = text
              }
            }
            "response.completed", "response.done", "response.incomplete" -> {
              responseId = root["response"].asObjectOrNull()?.get("id").asStringOrNull() ?: responseId
              val response = root["response"].asObjectOrNull() ?: return
              val output = response["output"] as? JsonArray ?: return
              val text = extractResponseOutputText(output)
              if (text.isNotBlank()) {
                finalText = text
              }
            }
            "response.failed" -> {
              val error = root["response"].asObjectOrNull()?.get("error").asObjectOrNull()
              val message = error?.get("message").asStringOrNull() ?: "OpenAI Codex request failed"
              throw IllegalStateException(message)
            }
            "error" -> {
              val message = root["message"].asStringOrNull() ?: "OpenAI Codex request failed"
              throw IllegalStateException(message)
            }
          }
        }

        while (true) {
          val line = reader.readLine() ?: break
          if (line.isBlank()) {
            if (eventLines.isNotEmpty()) {
              val previous = fullText.toString()
              consumeEvent(eventLines.joinToString(separator = "\n"))
              if (fullText.toString() != previous) {
                onTextDelta(fullText.toString())
              }
              eventLines.clear()
            }
            continue
          }
          if (line.startsWith("data:")) {
            eventLines += line.removePrefix("data:").trim()
          }
        }
        if (eventLines.isNotEmpty()) {
          val previous = fullText.toString()
          consumeEvent(eventLines.joinToString(separator = "\n"))
          if (fullText.toString() != previous) {
            onTextDelta(fullText.toString())
          }
        }

        val resolvedText = finalText?.takeIf { it.isNotBlank() } ?: fullText.toString().trim()
        if (resolvedText.isBlank()) {
          throw IllegalStateException("OpenAI Codex returned an empty response")
        }

        val persistedCredential =
          if (credential.accountId == accountId) {
            credential
          } else {
            credential.copy(accountId = accountId)
          }

        OpenAICodexAssistantReply(
          text = resolvedText,
          responseId = responseId,
          credential = persistedCredential,
        )
      }
    }

  private fun buildRequestBody(
    sessionId: String,
    messages: List<LocalHostMessage>,
    thinkingLevel: String,
  ): JsonObject {
    return buildJsonObject {
      put("model", JsonPrimitive(defaultModelId))
      put("store", JsonPrimitive(false))
      put("stream", JsonPrimitive(true))
      put("instructions", JsonPrimitive("You are OpenClaw running locally on an Android phone. Reply clearly and concisely."))
      put("input", buildJsonArray {
        messages.forEachIndexed { index, message ->
          when (message.role) {
            "user" ->
              add(
                buildJsonObject {
                  put("role", JsonPrimitive("user"))
                  put("content", buildUserContent(message.content))
                },
              )
            "assistant" ->
              add(
                buildJsonObject {
                  put("type", JsonPrimitive("message"))
                  put("role", JsonPrimitive("assistant"))
                  put(
                    "content",
                    buildJsonArray {
                      val text =
                        message.content
                          .filter { it.type == "text" }
                          .joinToString(separator = "\n") { it.text.orEmpty() }
                          .trim()
                      if (text.isNotEmpty()) {
                        add(
                          buildJsonObject {
                            put("type", JsonPrimitive("output_text"))
                            put("text", JsonPrimitive(text))
                            put("annotations", JsonArray(emptyList()))
                          },
                        )
                      }
                    },
                  )
                  put("status", JsonPrimitive("completed"))
                  put("id", JsonPrimitive("msg_$index"))
                },
              )
          }
        }
      })
      put(
        "text",
        buildJsonObject {
          put("verbosity", JsonPrimitive("medium"))
        },
      )
      put("include", buildJsonArray { add(JsonPrimitive("reasoning.encrypted_content")) })
      put("prompt_cache_key", JsonPrimitive(sessionId))
      val reasoning = normalizeThinkingEffort(thinkingLevel)
      if (reasoning != null) {
        put(
          "reasoning",
          buildJsonObject {
            put("effort", JsonPrimitive(reasoning))
            put("summary", JsonPrimitive("auto"))
          },
        )
      }
    }
  }

  private fun buildUserContent(content: List<ChatMessageContent>): JsonArray {
    return buildJsonArray {
      content.forEach { item ->
        if (item.type == "text") {
          val text = item.text?.trim().orEmpty()
          if (text.isNotEmpty()) {
            add(
              buildJsonObject {
                put("type", JsonPrimitive("input_text"))
                put("text", JsonPrimitive(text))
              },
            )
          }
          return@forEach
        }
        val mimeType = item.mimeType?.trim().orEmpty()
        val base64 = item.base64?.trim().orEmpty()
        if (mimeType.isNotEmpty() && base64.isNotEmpty()) {
          add(
            buildJsonObject {
              put("type", JsonPrimitive("input_image"))
              put("detail", JsonPrimitive("auto"))
              put("image_url", JsonPrimitive("data:$mimeType;base64,$base64"))
            },
          )
        }
      }
    }
  }

  private fun normalizeThinkingEffort(raw: String): String? {
    return when (raw.trim().lowercase()) {
      "low" -> "low"
      "medium" -> "medium"
      "high" -> "high"
      else -> null
    }
  }

  private fun extractResponseOutputText(output: JsonArray): String {
    return output.mapNotNull { item ->
      val message = item as? JsonObject ?: return@mapNotNull null
      if (message["type"].asStringOrNull() != "message") return@mapNotNull null
      extractMessageText(message["content"] as? JsonArray ?: return@mapNotNull null)
    }.joinToString(separator = "")
  }

  private fun extractMessageText(content: JsonArray): String {
    return content.mapNotNull { part ->
      val obj = part as? JsonObject ?: return@mapNotNull null
      when (obj["type"].asStringOrNull()) {
        "output_text" -> obj["text"].asStringOrNull()
        "refusal" -> obj["refusal"].asStringOrNull()
        else -> null
      }
    }.joinToString(separator = "")
  }

  private fun refreshCredential(current: OpenAICodexCredential): OpenAICodexCredential {
    val formBody =
      FormBody.Builder()
        .add("grant_type", "refresh_token")
        .add("refresh_token", current.refresh)
        .add("client_id", authorizeClientId)
        .build()
    val request =
      Request.Builder()
        .url(tokenUrl)
        .post(formBody)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .build()

    client.newCall(request).execute().use { response ->
      val body = response.body?.string().orEmpty()
      if (!response.isSuccessful) {
        throw IllegalStateException(parseCodexError(response.code, body))
      }
      val root = json.parseToJsonElement(body) as? JsonObject
        ?: throw IllegalStateException("OpenAI Codex refresh returned invalid JSON")
      val access = root["access_token"].asStringOrNull().orEmpty()
      val refresh = root["refresh_token"].asStringOrNull().orEmpty()
      val expiresIn = root["expires_in"].asLongOrNull()
      if (access.isEmpty() || refresh.isEmpty() || expiresIn == null) {
        throw IllegalStateException("OpenAI Codex refresh response was missing required fields")
      }
      val accountId = extractAccountId(access) ?: current.accountId
      return OpenAICodexCredential(
        access = access,
        refresh = refresh,
        expires = System.currentTimeMillis() + expiresIn * 1000,
        accountId = accountId,
        email = current.email,
      )
    }
  }

  private fun extractAccountId(accessToken: String): String? {
    val parts = accessToken.split(".")
    if (parts.size != 3) return null
    val payload = decodeJwtPayload(parts[1]) ?: return null
    val auth = payload["https://api.openai.com/auth"].asObjectOrNull() ?: return null
    return auth["chatgpt_account_id"].asStringOrNull()?.takeIf { it.isNotBlank() }
  }

  private fun decodeJwtPayload(value: String): JsonObject? {
    val normalized = when (value.length % 4) {
      2 -> "$value=="
      3 -> "$value="
      else -> value
    }
    return try {
      val decoded = Base64.decode(normalized, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
      json.parseToJsonElement(String(decoded, Charsets.UTF_8)) as? JsonObject
    } catch (_: Throwable) {
      null
    }
  }

  private fun parseCodexError(statusCode: Int, body: String): String {
    if (body.isBlank()) {
      return "OpenAI Codex request failed ($statusCode)"
    }
    return try {
      val root = json.parseToJsonElement(body) as? JsonObject
      val error = root?.get("error").asObjectOrNull()
      error?.get("message").asStringOrNull()
        ?: root?.get("message").asStringOrNull()
        ?: "OpenAI Codex request failed ($statusCode)"
    } catch (_: Throwable) {
      if (statusCode == 429) {
        "OpenAI Codex request was rate limited"
      } else {
        body.take(200)
      }
    }
  }
}

private fun kotlinx.serialization.json.JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

private fun kotlinx.serialization.json.JsonElement?.asStringOrNull(): String? =
  when (this) {
    is JsonPrimitive -> content
    else -> null
  }

private fun kotlinx.serialization.json.JsonElement?.asLongOrNull(): Long? =
  when (this) {
    is JsonPrimitive -> content.toLongOrNull()
    else -> null
  }
