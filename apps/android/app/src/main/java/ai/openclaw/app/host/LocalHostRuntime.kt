package ai.openclaw.app.host

import ai.openclaw.app.SecurePrefs
import ai.openclaw.app.chat.ChatMessageContent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

data class LocalGatewayClientSnapshot(
  val clientId: String,
  val serverName: String,
  val remoteAddress: String,
  val mainSessionKey: String,
  val canvasHostUrl: String? = null,
)

private data class LocalClientRegistration(
  val clientId: String,
  val role: String,
  val onEvent: (event: String, payloadJson: String?) -> Unit,
)

internal data class LocalHostMessage(
  val role: String,
  val content: List<ChatMessageContent>,
  val timestampMs: Long,
)

private data class LocalHostSession(
  val key: String,
  val sessionId: String = key,
  val messages: MutableList<LocalHostMessage> = mutableListOf(),
  var thinkingLevel: String = "off",
  var updatedAtMs: Long = System.currentTimeMillis(),
)

class LocalHostRuntime(
  private val scope: CoroutineScope,
  private val prefs: SecurePrefs,
  private val json: Json,
  private val codexClient: LocalHostResponsesClient = OpenAICodexResponsesClient(prefs, json),
) {
  companion object {
    private const val serverName = "OpenClaw Local Host"
    private const val remoteAddress = "127.0.0.1 (on device)"
    private const val mainSessionKey = "main"
  }

  private val sessions = ConcurrentHashMap<String, LocalHostSession>()
  private val clients = CopyOnWriteArrayList<LocalClientRegistration>()
  private val activeRuns = ConcurrentHashMap<String, Job>()
  private val sessionSeq = ConcurrentHashMap<String, AtomicLong>()

  fun registerClient(
    role: String,
    onEvent: (event: String, payloadJson: String?) -> Unit,
  ): LocalGatewayClientSnapshot {
    val registration =
      LocalClientRegistration(
        clientId = UUID.randomUUID().toString(),
        role = role,
        onEvent = onEvent,
      )
    clients += registration
    return LocalGatewayClientSnapshot(
      clientId = registration.clientId,
      serverName = serverName,
      remoteAddress = remoteAddress,
      mainSessionKey = mainSessionKey,
      canvasHostUrl = null,
    )
  }

  fun unregisterClient(clientId: String) {
    clients.removeAll { it.clientId == clientId }
  }

  suspend fun request(role: String, method: String, paramsJson: String?, timeoutMs: Long): String {
    require(timeoutMs > 0) { "timeoutMs must be positive" }
    val params = parseParams(paramsJson)
    return when (method) {
      "health" -> buildJsonObject { put("ok", JsonPrimitive(true)) }.toString()
      "config.get" -> configPayload().toString()
      "agents.list" -> agentsPayload().toString()
      "sessions.list" -> sessionsPayload().toString()
      "chat.history" -> chatHistoryPayload(params).toString()
      "chat.send" -> chatSend(params).toString()
      "chat.abort" -> chatAbort(params).toString()
      "talk.config" -> talkConfigPayload().toString()
      "voicewake.get" -> voiceWakePayload().toString()
      "voicewake.set" -> {
        applyVoiceWake(params)
        voiceWakePayload().toString()
      }
      "gateway.identity.get" ->
        buildJsonObject {
          put("host", JsonPrimitive(serverName))
          put("mode", JsonPrimitive("local-host"))
        }.toString()
      else -> throw IllegalStateException("INVALID_REQUEST: unsupported local method $method for role=$role")
    }
  }

  suspend fun handleNodeEvent(role: String, event: String, payloadJson: String?): Boolean {
    if (role.isBlank()) return false
    val payload = parseParams(payloadJson)
    return when (event) {
      "chat.subscribe" -> true
      "agent.request" -> {
        val sessionKey = payload["sessionKey"].asStringOrNull()?.trim().orEmpty().ifEmpty { mainSessionKey }
        val message = payload["message"].asStringOrNull()?.trim().orEmpty()
        val thinking = payload["thinking"].asStringOrNull()?.trim().orEmpty().ifEmpty { "low" }
        val deliver = payload["deliver"].asBooleanOrNull() != false
        val runId = payload["key"].asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
        if (message.isEmpty()) return false
        startChatRun(
          sessionKey = sessionKey,
          message = message,
          thinking = thinking,
          runId = runId,
          attachments = emptyList(),
          emitEvents = deliver,
        )
        true
      }
      else -> true
    }
  }

  fun refreshNodeCanvasCapability(): String? = null

  private fun configPayload(): JsonObject {
    return buildJsonObject {
      put(
        "config",
        buildJsonObject {
          put(
            "ui",
            buildJsonObject {
              put("seamColor", JsonPrimitive("#0EA5E9"))
            },
          )
          put(
            "session",
            buildJsonObject {
              put("mainKey", JsonPrimitive(mainSessionKey))
            },
          )
        },
      )
    }
  }

  private fun agentsPayload(): JsonObject {
    return buildJsonObject {
      put("defaultId", JsonPrimitive("main"))
      put("mainKey", JsonPrimitive(mainSessionKey))
      put(
        "agents",
        buildJsonArray {
          add(
            buildJsonObject {
              put("id", JsonPrimitive("main"))
              put("name", JsonPrimitive("Main"))
              put(
                "identity",
                buildJsonObject {
                  put("emoji", JsonPrimitive("OC"))
                },
              )
            },
          )
        },
      )
    }
  }

  private fun sessionsPayload(): JsonObject {
    val ordered =
      sessions.values
        .map(::snapshotSession)
        .sortedByDescending { it.updatedAtMs }
        .map { session ->
          buildJsonObject {
            put("key", JsonPrimitive(session.sessionKey))
            put("updatedAt", JsonPrimitive(session.updatedAtMs))
            put("displayName", JsonPrimitive(session.sessionKey))
          }
        }
    return buildJsonObject {
      put("sessions", JsonArray(ordered))
    }
  }

  private fun chatHistoryPayload(params: JsonObject): JsonObject {
    val session = snapshotSession(resolveSession(params["sessionKey"].asStringOrNull()))
    return buildJsonObject {
      put("sessionId", JsonPrimitive(session.sessionId))
      put("thinkingLevel", JsonPrimitive(session.thinkingLevel))
      put(
        "messages",
        buildJsonArray {
          session.messages.forEach { message ->
            add(
              buildJsonObject {
                put("role", JsonPrimitive(message.role))
                put(
                  "content",
                  buildJsonArray {
                    message.content.forEach { part ->
                      add(
                        buildJsonObject {
                          put("type", JsonPrimitive(part.type))
                          part.text?.let { put("text", JsonPrimitive(it)) }
                          part.mimeType?.let { put("mimeType", JsonPrimitive(it)) }
                          part.fileName?.let { put("fileName", JsonPrimitive(it)) }
                          part.base64?.let { put("content", JsonPrimitive(it)) }
                        },
                      )
                    }
                  },
                )
                put("timestamp", JsonPrimitive(message.timestampMs))
              },
            )
          }
        },
      )
    }
  }

  private fun chatSend(params: JsonObject): JsonObject {
    val sessionKey = params["sessionKey"].asStringOrNull()?.trim().orEmpty().ifEmpty { mainSessionKey }
    val message = params["message"].asStringOrNull()?.trim().orEmpty()
    val runId = params["idempotencyKey"].asStringOrNull()?.trim().orEmpty().ifEmpty { UUID.randomUUID().toString() }
    val thinking = params["thinking"].asStringOrNull()?.trim().orEmpty().ifEmpty { "off" }
    val attachments = parseAttachments(params["attachments"] as? JsonArray)
    if (message.isEmpty() && attachments.isEmpty()) {
      throw IllegalStateException("INVALID_REQUEST: message or attachment required")
    }
    startChatRun(
      sessionKey = sessionKey,
      message = message,
      thinking = thinking,
      runId = runId,
      attachments = attachments,
      emitEvents = true,
    )
    return buildJsonObject {
      put("runId", JsonPrimitive(runId))
      put("status", JsonPrimitive("started"))
    }
  }

  private fun chatAbort(params: JsonObject): JsonObject {
    val runId = params["runId"].asStringOrNull()?.trim().orEmpty()
    if (runId.isEmpty()) {
      throw IllegalStateException("INVALID_REQUEST: runId required")
    }
    val cancelled = activeRuns.remove(runId)?.cancel() != null
    return buildJsonObject {
      put("ok", JsonPrimitive(true))
      put("aborted", JsonPrimitive(cancelled))
      put(
        "runIds",
        buildJsonArray {
          if (cancelled) add(JsonPrimitive(runId))
        },
      )
    }
  }

  private fun talkConfigPayload(): JsonObject {
    return buildJsonObject {
      put(
        "config",
        buildJsonObject {
          put(
            "session",
            buildJsonObject {
              put("mainKey", JsonPrimitive(mainSessionKey))
            },
          )
          put(
            "talk",
            buildJsonObject {
              put(
                "resolved",
                buildJsonObject {
                  put("provider", JsonPrimitive("system"))
                  put("config", buildJsonObject {})
                },
              )
            },
          )
        },
      )
    }
  }

  private fun voiceWakePayload(): JsonObject {
    return buildJsonObject {
      put(
        "triggers",
        buildJsonArray {
          prefs.wakeWords.value.forEach { add(JsonPrimitive(it)) }
        },
      )
    }
  }

  private fun applyVoiceWake(params: JsonObject) {
    val triggers = (params["triggers"] as? JsonArray)?.mapNotNull { it.asStringOrNull() } ?: return
    prefs.setWakeWords(triggers)
    emitEvent(
      event = "voicewake.changed",
      payload =
        buildJsonObject {
          put(
            "triggers",
            buildJsonArray {
              triggers.forEach { add(JsonPrimitive(it)) }
            },
          )
        },
    )
  }

  private fun startChatRun(
    sessionKey: String,
    message: String,
    thinking: String,
    runId: String,
    attachments: List<ChatMessageContent>,
    emitEvents: Boolean,
  ) {
    activeRuns[runId]?.cancel()
    val session = resolveSession(sessionKey)
    val now = System.currentTimeMillis()
    val requestMessages =
      synchronized(session) {
        session.thinkingLevel = thinking
        session.messages +=
          LocalHostMessage(
            role = "user",
            content = buildUserContent(message = message, attachments = attachments),
            timestampMs = now,
          )
        session.updatedAtMs = now
        session.messages.toList()
      }

    activeRuns[runId] =
      scope.launch {
        try {
          val reply =
            codexClient.streamReply(
              sessionId = session.sessionId,
              messages = requestMessages,
              thinkingLevel = thinking,
              onTextDelta = { fullText ->
                if (!emitEvents) return@streamReply
                emitAssistantText(runId = runId, sessionKey = sessionKey, text = fullText)
              },
            )
          val assistantMessage =
            LocalHostMessage(
              role = "assistant",
              content = listOf(ChatMessageContent(type = "text", text = reply.text)),
              timestampMs = System.currentTimeMillis(),
            )
          synchronized(session) {
            session.messages += assistantMessage
            session.updatedAtMs = assistantMessage.timestampMs
          }
          prefs.saveOpenAICodexCredential(reply.credential)
          if (emitEvents) {
            emitChatFinal(runId = runId, sessionKey = sessionKey, text = reply.text, timestampMs = assistantMessage.timestampMs)
          }
        } catch (_: CancellationException) {
          if (emitEvents) {
            emitChatAborted(runId = runId, sessionKey = sessionKey)
          }
        } catch (err: Throwable) {
          if (emitEvents) {
            emitChatError(runId = runId, sessionKey = sessionKey, message = err.message ?: "Chat failed")
          }
        } finally {
          activeRuns.remove(runId)
        }
      }
  }

  private fun resolveSession(key: String?): LocalHostSession {
    val sessionKey = key?.trim().orEmpty().ifEmpty { mainSessionKey }
    return sessions.getOrPut(sessionKey) { LocalHostSession(key = sessionKey) }
  }

  private fun snapshotSession(session: LocalHostSession): LocalHostSessionSnapshot {
    return synchronized(session) {
      LocalHostSessionSnapshot(
        sessionKey = session.key,
        sessionId = session.sessionId,
        messages = session.messages.toList(),
        thinkingLevel = session.thinkingLevel,
        updatedAtMs = session.updatedAtMs,
      )
    }
  }

  private fun buildUserContent(
    message: String,
    attachments: List<ChatMessageContent>,
  ): List<ChatMessageContent> {
    val content = mutableListOf<ChatMessageContent>()
    if (message.isNotBlank()) {
      content += ChatMessageContent(type = "text", text = message)
    }
    content += attachments
    return content
  }

  private fun parseAttachments(items: JsonArray?): List<ChatMessageContent> {
    if (items == null) return emptyList()
    return items.mapNotNull { item ->
      val obj = item as? JsonObject ?: return@mapNotNull null
      val type = obj["type"].asStringOrNull()?.trim().orEmpty().ifEmpty { "image" }
      val mimeType = obj["mimeType"].asStringOrNull()?.trim().orEmpty()
      val fileName = obj["fileName"].asStringOrNull()?.trim().orEmpty()
      val content = obj["content"].asStringOrNull()?.trim().orEmpty()
      if (content.isEmpty()) return@mapNotNull null
      ChatMessageContent(
        type = type,
        mimeType = mimeType.ifEmpty { null },
        fileName = fileName.ifEmpty { null },
        base64 = content,
      )
    }
  }

  private suspend fun emitAssistantText(
    runId: String,
    sessionKey: String,
    text: String,
  ) {
    val timestamp = System.currentTimeMillis()
    val seq = nextSeq(sessionKey)
    emitEvent(
      event = "agent",
      payload =
        buildJsonObject {
          put("runId", JsonPrimitive(runId))
          put("stream", JsonPrimitive("assistant"))
          put("ts", JsonPrimitive(timestamp))
          put("sessionKey", JsonPrimitive(sessionKey))
          put(
            "data",
            buildJsonObject {
              put("text", JsonPrimitive(text))
            },
          )
        },
    )
    emitEvent(
      event = "chat",
      payload =
        buildJsonObject {
          put("runId", JsonPrimitive(runId))
          put("sessionKey", JsonPrimitive(sessionKey))
          put("seq", JsonPrimitive(seq))
          put("state", JsonPrimitive("delta"))
          put(
            "message",
            buildJsonObject {
              put("role", JsonPrimitive("assistant"))
              put(
                "content",
                buildJsonArray {
                  add(
                    buildJsonObject {
                      put("type", JsonPrimitive("text"))
                      put("text", JsonPrimitive(text))
                    },
                  )
                },
              )
              put("timestamp", JsonPrimitive(timestamp))
            },
          )
        },
    )
  }

  private fun emitChatFinal(
    runId: String,
    sessionKey: String,
    text: String,
    timestampMs: Long,
  ) {
    val seq = nextSeq(sessionKey)
    emitEvent(
      event = "chat",
      payload =
        buildJsonObject {
          put("runId", JsonPrimitive(runId))
          put("sessionKey", JsonPrimitive(sessionKey))
          put("seq", JsonPrimitive(seq))
          put("state", JsonPrimitive("final"))
          put(
            "message",
            buildJsonObject {
              put("role", JsonPrimitive("assistant"))
              put(
                "content",
                buildJsonArray {
                  add(
                    buildJsonObject {
                      put("type", JsonPrimitive("text"))
                      put("text", JsonPrimitive(text))
                    },
                  )
                },
              )
              put("timestamp", JsonPrimitive(timestampMs))
            },
          )
        },
    )
  }

  private fun emitChatError(
    runId: String,
    sessionKey: String,
    message: String,
  ) {
    val seq = nextSeq(sessionKey)
    emitEvent(
      event = "chat",
      payload =
        buildJsonObject {
          put("runId", JsonPrimitive(runId))
          put("sessionKey", JsonPrimitive(sessionKey))
          put("seq", JsonPrimitive(seq))
          put("state", JsonPrimitive("error"))
          put("errorMessage", JsonPrimitive(message))
        },
    )
  }

  private fun emitChatAborted(
    runId: String,
    sessionKey: String,
  ) {
    val seq = nextSeq(sessionKey)
    emitEvent(
      event = "chat",
      payload =
        buildJsonObject {
          put("runId", JsonPrimitive(runId))
          put("sessionKey", JsonPrimitive(sessionKey))
          put("seq", JsonPrimitive(seq))
          put("state", JsonPrimitive("aborted"))
        },
    )
  }

  private fun emitEvent(event: String, payload: JsonObject) {
    val payloadJson = payload.toString()
    clients.forEach { client ->
      try {
        client.onEvent(event, payloadJson)
      } catch (_: Throwable) {
        // Ignore listener failures so the local host stays alive.
      }
    }
  }

  private fun nextSeq(sessionKey: String): Long {
    val counter = sessionSeq.getOrPut(sessionKey) { AtomicLong(0) }
    return counter.incrementAndGet()
  }

  private fun parseParams(paramsJson: String?): JsonObject {
    if (paramsJson.isNullOrBlank()) return buildJsonObject {}
    return json.parseToJsonElement(paramsJson) as? JsonObject ?: buildJsonObject {}
  }
}

private fun kotlinx.serialization.json.JsonElement?.asStringOrNull(): String? =
  when (this) {
    is JsonPrimitive -> content
    else -> null
  }

private fun kotlinx.serialization.json.JsonElement?.asBooleanOrNull(): Boolean? =
  when (this) {
    is JsonPrimitive -> {
      when (content.trim().lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
      }
    }
    else -> null
  }

private data class LocalHostSessionSnapshot(
  val sessionKey: String,
  val sessionId: String,
  val messages: List<LocalHostMessage>,
  val thinkingLevel: String,
  val updatedAtMs: Long,
)
