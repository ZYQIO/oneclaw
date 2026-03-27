package ai.openclaw.app.host

import android.net.Uri
import ai.openclaw.app.gateway.GatewaySession
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

data class LocalHostRemoteAccessState(
  val running: Boolean = false,
  val listenUrl: String? = null,
  val statusText: String = "Remote access is off.",
)

private data class RemoteAccessConfig(
  val port: Int,
  val token: String,
)

private data class HttpRequest(
  val method: String,
  val pathWithQuery: String,
  val headers: Map<String, String>,
  val body: String,
)

private data class HttpResponse(
  val statusCode: Int,
  val body: String = "",
  val contentType: String = "application/json; charset=utf-8",
  val extraHeaders: Map<String, String> = emptyMap(),
)

internal data class BufferedRemoteAccessEvent(
  val id: Long,
  val event: String,
  val payloadJson: String?,
  val timestampMs: Long,
)

internal class RemoteAccessEventBuffer(
  private val maxEvents: Int = 200,
) {
  private val lock = Object()
  private val events = ArrayDeque<BufferedRemoteAccessEvent>()
  private val nextId = AtomicLong(0)

  fun append(
    event: String,
    payloadJson: String?,
    timestampMs: Long = System.currentTimeMillis(),
  ): BufferedRemoteAccessEvent {
    val buffered =
      BufferedRemoteAccessEvent(
        id = nextId.incrementAndGet(),
        event = event,
        payloadJson = payloadJson,
        timestampMs = timestampMs,
      )
    synchronized(lock) {
      events.addLast(buffered)
      while (events.size > maxEvents) {
        events.removeFirst()
      }
      lock.notifyAll()
    }
    return buffered
  }

  fun snapshotAfter(
    cursor: Long,
    limit: Int,
  ): List<BufferedRemoteAccessEvent> {
    synchronized(lock) {
      return snapshotAfterLocked(cursor, limit)
    }
  }

  fun hasEventsAfter(cursor: Long): Boolean {
    synchronized(lock) {
      return events.any { it.id > cursor }
    }
  }

  fun latestCursor(): Long {
    synchronized(lock) {
      return events.lastOrNull()?.id ?: 0L
    }
  }

  fun clear() {
    synchronized(lock) {
      events.clear()
    }
  }

  suspend fun awaitAfter(
    cursor: Long,
    limit: Int,
    waitMs: Long,
  ): List<BufferedRemoteAccessEvent> =
    withContext(Dispatchers.IO) {
      synchronized(lock) {
        var snapshot = snapshotAfterLocked(cursor, limit)
        if (snapshot.isNotEmpty() || waitMs <= 0) {
          return@withContext snapshot
        }
        lock.wait(waitMs)
        snapshot = snapshotAfterLocked(cursor, limit)
        return@withContext snapshot
      }
    }

  private fun snapshotAfterLocked(
    cursor: Long,
    limit: Int,
  ): List<BufferedRemoteAccessEvent> {
    return events.asSequence()
      .filter { it.id > cursor }
      .take(limit)
      .toList()
  }
}

class LocalHostRemoteAccessServer(
  private val scope: CoroutineScope,
  private val json: Json,
  private val handleLocalHostRequest: suspend (method: String, paramsJson: String?, timeoutMs: Long) -> String,
  private val handleInvoke: suspend (command: String, paramsJson: String?) -> GatewaySession.InvokeResult,
  private val registerEventClient: (((event: String, payloadJson: String?) -> Unit) -> String)? = null,
  private val unregisterEventClientFn: ((clientId: String) -> Unit)? = null,
  private val allowAdvancedInvokeCommands: () -> Boolean = { false },
  private val allowWriteInvokeCommands: () -> Boolean = { false },
  private val statusSnapshotProvider: (() -> JsonObject)? = null,
  private val codexAuthStatusProvider: (() -> JsonObject)? = null,
  private val refreshCodexAuth: (suspend () -> JsonObject)? = null,
) {
  companion object {
    private const val apiBasePath = "/api/local-host/v1"
    private const val maxHeaderBytes = 16 * 1024
    private const val maxBodyBytes = 12 * 1024 * 1024
    private const val defaultTimeoutMs = 30_000L
    private const val maxEventWaitMs = 20_000L
    private const val maxEventsPerResponse = 100
  }

  private val _state = MutableStateFlow(LocalHostRemoteAccessState())
  val state: StateFlow<LocalHostRemoteAccessState> = _state.asStateFlow()

  @Volatile private var serverSocket: ServerSocket? = null
  @Volatile private var activeConfig: RemoteAccessConfig? = null
  private var acceptJob: Job? = null
  private var eventClientId: String? = null
  private val eventBuffer = RemoteAccessEventBuffer()

  fun start(
    port: Int,
    token: String,
  ) {
    val trimmedToken = token.trim()
    if (port !in 1..65535) {
      stop(statusText = "Remote access port must be between 1 and 65535.")
      return
    }
    if (trimmedToken.isEmpty()) {
      stop(statusText = "Remote access token is missing.")
      return
    }

    val desired = RemoteAccessConfig(port = port, token = trimmedToken)
    if (activeConfig == desired && serverSocket?.isClosed == false) return

    stop()

    try {
      val socket =
        ServerSocket().apply {
          reuseAddress = true
          bind(InetSocketAddress(port))
        }
      serverSocket = socket
      activeConfig = desired
      ensureEventClientRegistered()
      val listenUrl = detectListenUrl(port)
      _state.value =
        LocalHostRemoteAccessState(
          running = true,
          listenUrl = listenUrl,
          statusText =
            listenUrl?.let { "Remote access ready at $it" }
              ?: "Remote access is listening on port $port.",
        )
      acceptJob =
        scope.launch(Dispatchers.IO) {
          acceptLoop(socket, desired)
        }
    } catch (err: Throwable) {
      serverSocket = null
      activeConfig = null
      _state.value =
        LocalHostRemoteAccessState(
          statusText = "Remote access failed to start: ${err.message ?: err::class.java.simpleName}",
        )
    }
  }

  fun stop(statusText: String = "Remote access is off.") {
    activeConfig = null
    acceptJob?.cancel()
    acceptJob = null
    unregisterEventClient()
    eventBuffer.clear()
    try {
      serverSocket?.close()
    } catch (_: Throwable) {
      // Ignore teardown failures while replacing the server configuration.
    }
    serverSocket = null
    _state.value = LocalHostRemoteAccessState(statusText = statusText)
  }

  private suspend fun acceptLoop(
    socket: ServerSocket,
    config: RemoteAccessConfig,
  ) {
    try {
      while (scope.isActive && !socket.isClosed && activeConfig == config) {
        val client =
          try {
            socket.accept()
          } catch (_: SocketException) {
            break
          }
        scope.launch(Dispatchers.IO) {
          client.use { handleClient(it, config) }
        }
      }
    } finally {
      if (activeConfig == config) {
        stop(statusText = "Remote access stopped.")
      }
    }
  }

  private suspend fun handleClient(
    socket: Socket,
    config: RemoteAccessConfig,
  ) {
    socket.soTimeout = 15_000
    val response =
      try {
        val request = readRequest(socket) ?: return
        route(request, config)
      } catch (err: HttpRequestException) {
        jsonResponse(
          statusCode = err.statusCode,
          body =
            buildJsonObject {
              put("ok", JsonPrimitive(false))
              put("error", JsonPrimitive(err.messageText))
            }.toString(),
        )
      } catch (err: Throwable) {
        jsonResponse(
          statusCode = 500,
          body =
            buildJsonObject {
              put("ok", JsonPrimitive(false))
              put("error", JsonPrimitive(err.message ?: "Remote access request failed"))
            }.toString(),
        )
      }
    writeResponse(socket, response)
  }

  private suspend fun route(
    request: HttpRequest,
    config: RemoteAccessConfig,
  ): HttpResponse {
    if (request.method == "OPTIONS") {
      return HttpResponse(statusCode = 204, contentType = "text/plain; charset=utf-8")
    }
    if (!isAuthorized(request.headers["authorization"], config.token)) {
      return jsonResponse(
        statusCode = 401,
        body =
          buildJsonObject {
            put("ok", JsonPrimitive(false))
            put("error", JsonPrimitive("Missing or invalid bearer token"))
          }.toString(),
        extraHeaders = mapOf("WWW-Authenticate" to "Bearer realm=\"OpenClaw Local Host\""),
      )
    }

    val uri = Uri.parse("http://localhost${request.pathWithQuery}")
    return when {
      request.method == "GET" && uri.path == "$apiBasePath/health" -> jsonResponse(
        statusCode = 200,
        body =
          buildJsonObject {
            put("ok", JsonPrimitive(true))
            put("mode", JsonPrimitive("local-host"))
            put("server", JsonPrimitive("OpenClaw Local Host"))
            state.value.listenUrl?.let { put("listenUrl", JsonPrimitive(it)) }
          }.toString(),
      )
      request.method == "GET" && uri.path == "$apiBasePath/status" -> statusResponse()
      request.method == "GET" && uri.path == "$apiBasePath/examples" -> examplesResponse()
      request.method == "GET" && uri.path == "$apiBasePath/auth/codex/status" -> codexAuthStatusResponse()
      request.method == "POST" && uri.path == "$apiBasePath/auth/codex/refresh" -> codexAuthRefreshResponse()
      request.method == "GET" && uri.path == apiBasePath -> jsonResponse(
        statusCode = 200,
        body =
          buildJsonObject {
            put("ok", JsonPrimitive(true))
            put(
              "routes",
              buildJsonArray {
                add(JsonPrimitive("GET $apiBasePath"))
                add(JsonPrimitive("GET $apiBasePath/health"))
                add(JsonPrimitive("GET $apiBasePath/status"))
                add(JsonPrimitive("GET $apiBasePath/examples"))
                if (codexAuthStatusProvider != null) {
                  add(JsonPrimitive("GET $apiBasePath/auth/codex/status"))
                }
                if (refreshCodexAuth != null) {
                  add(JsonPrimitive("POST $apiBasePath/auth/codex/refresh"))
                }
                add(JsonPrimitive("GET $apiBasePath/identity"))
                add(JsonPrimitive("GET $apiBasePath/config"))
                add(JsonPrimitive("GET $apiBasePath/agents"))
                add(JsonPrimitive("GET $apiBasePath/talk"))
                add(JsonPrimitive("GET $apiBasePath/voicewake"))
                add(JsonPrimitive("POST $apiBasePath/voicewake"))
                add(JsonPrimitive("GET $apiBasePath/chat/sessions"))
                add(JsonPrimitive("GET $apiBasePath/chat/history"))
                add(JsonPrimitive("POST $apiBasePath/chat/send"))
                add(JsonPrimitive("POST $apiBasePath/chat/send-wait"))
                add(JsonPrimitive("POST $apiBasePath/chat/abort"))
                add(JsonPrimitive("GET $apiBasePath/events"))
                add(JsonPrimitive("GET $apiBasePath/invoke/capabilities"))
                add(JsonPrimitive("POST $apiBasePath/invoke"))
              },
            )
            put(
              "allowedInvokeCommands",
              buildJsonArray {
                allowedInvokeCommands().forEach { command ->
                  add(JsonPrimitive(command))
                }
              },
            )
            put("advancedEnabled", JsonPrimitive(allowAdvancedInvokeCommands()))
            put("writeEnabled", JsonPrimitive(allowWriteInvokeCommands()))
          }.toString(),
      )
      request.method == "GET" && uri.path == "$apiBasePath/identity" -> forwardToLocalHost(
        method = "gateway.identity.get",
        params = null,
      )
      request.method == "GET" && uri.path == "$apiBasePath/config" -> forwardToLocalHost(
        method = "config.get",
        params = null,
      )
      request.method == "GET" && uri.path == "$apiBasePath/agents" -> forwardToLocalHost(
        method = "agents.list",
        params = null,
      )
      request.method == "GET" && uri.path == "$apiBasePath/talk" -> forwardToLocalHost(
        method = "talk.config",
        params = null,
      )
      request.method == "GET" && uri.path == "$apiBasePath/voicewake" -> forwardToLocalHost(
        method = "voicewake.get",
        params = null,
      )
      request.method == "POST" && uri.path == "$apiBasePath/voicewake" -> forwardToLocalHost(
        method = "voicewake.set",
        params = requireJsonBody(request),
      )
      request.method == "GET" && uri.path == "$apiBasePath/chat/history" -> forwardToLocalHost(
        method = "chat.history",
        params =
          buildJsonObject {
            uri.getQueryParameter("sessionKey")?.takeIf { it.isNotBlank() }?.let {
              put("sessionKey", JsonPrimitive(it))
            }
          },
      )
      request.method == "GET" && uri.path == "$apiBasePath/chat/sessions" -> forwardToLocalHost(
        method = "sessions.list",
        params = null,
      )
      request.method == "GET" && uri.path == "$apiBasePath/events" -> eventsResponse(uri)
      request.method == "GET" && uri.path == "$apiBasePath/invoke/capabilities" -> invokeCapabilitiesResponse()
      request.method == "POST" && uri.path == "$apiBasePath/chat/send-wait" -> sendAndWaitForChat(requireJsonBody(request))
      request.method == "POST" && uri.path == "$apiBasePath/chat/send" -> forwardToLocalHost(
        method = "chat.send",
        params = requireJsonBody(request),
      )
      request.method == "POST" && uri.path == "$apiBasePath/chat/abort" -> forwardToLocalHost(
        method = "chat.abort",
        params = requireJsonBody(request),
      )
      request.method == "POST" && uri.path == "$apiBasePath/invoke" -> handleInvokeRequest(requireJsonBody(request))
      else -> jsonResponse(
        statusCode = 404,
        body =
          buildJsonObject {
            put("ok", JsonPrimitive(false))
            put("error", JsonPrimitive("Unknown remote access route"))
          }.toString(),
      )
    }
  }

  private fun statusResponse(): HttpResponse {
    return jsonResponse(
      statusCode = 200,
      body =
        buildJsonObject {
          put("ok", JsonPrimitive(true))
          put("mode", JsonPrimitive("local-host"))
          put("server", JsonPrimitive("OpenClaw Local Host"))
          state.value.listenUrl?.let { put("listenUrl", JsonPrimitive(it)) }
          put(
            "remoteAccess",
            buildJsonObject {
              activeConfig?.let { config ->
                put("port", JsonPrimitive(config.port))
              }
              put("advancedEnabled", JsonPrimitive(allowAdvancedInvokeCommands()))
              put("writeEnabled", JsonPrimitive(allowWriteInvokeCommands()))
              put("eventCursor", JsonPrimitive(eventBuffer.latestCursor()))
              put(
                "allowedInvokeCommands",
                buildJsonArray {
                  allowedInvokeCommands().forEach { command ->
                    add(JsonPrimitive(command))
                  }
                },
              )
            },
          )
          statusSnapshotProvider?.invoke()?.let { snapshot ->
            put("host", snapshot)
          }
        }.toString(),
    )
  }

  private suspend fun eventsResponse(uri: Uri): HttpResponse {
    val cursor = uri.getQueryParameter("cursor")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    val limit = uri.getQueryParameter("limit")?.toIntOrNull()?.coerceIn(1, maxEventsPerResponse) ?: 50
    val waitMs = uri.getQueryParameter("waitMs")?.toLongOrNull()?.coerceIn(0L, maxEventWaitMs) ?: 0L
    val events = eventBuffer.awaitAfter(cursor = cursor, limit = limit, waitMs = waitMs)
    val nextCursor = events.lastOrNull()?.id ?: cursor
    return jsonResponse(
      statusCode = 200,
      body =
        buildJsonObject {
          put(
            "events",
            buildJsonArray {
              events.forEach { item ->
                add(
                  buildJsonObject {
                    put("id", JsonPrimitive(item.id))
                    put("event", JsonPrimitive(item.event))
                    put("timestamp", JsonPrimitive(item.timestampMs))
                    item.payloadJson?.let { raw ->
                      val parsedPayload = parseJsonOrNull(raw)
                      if (parsedPayload != null) {
                        put("payload", parsedPayload)
                      } else {
                        put("payloadJSON", JsonPrimitive(raw))
                      }
                    }
                  },
                )
              }
            },
          )
          put("nextCursor", JsonPrimitive(nextCursor))
          put("hasMore", JsonPrimitive(eventBuffer.hasEventsAfter(nextCursor)))
        }.toString(),
    )
  }

  private suspend fun sendAndWaitForChat(body: JsonObject): HttpResponse {
    val waitMs = body["waitMs"].asLongOrNull()?.coerceIn(1_000L, 120_000L) ?: 30_000L
    val cursor = eventBuffer.latestCursor()
    val sendResponseBody = handleLocalHostRequest("chat.send", body.toString(), defaultTimeoutMs)
    val sendResponse =
      parseJsonOrNull(sendResponseBody).asObjectOrNull()
        ?: throw HttpRequestException(500, "chat.send returned invalid JSON")
    val runId = sendResponse["runId"].asStringOrNull()?.takeIf { it.isNotBlank() }
      ?: throw HttpRequestException(500, "chat.send response was missing runId")
    val terminalEvent = waitForTerminalRunEvent(runId = runId, cursor = cursor, waitMs = waitMs)
    if (terminalEvent == null) {
      return jsonResponse(
        statusCode = 202,
        body =
          buildJsonObject {
            put("ok", JsonPrimitive(true))
            put("runId", JsonPrimitive(runId))
            put("timedOut", JsonPrimitive(true))
            put("nextCursor", JsonPrimitive(eventBuffer.latestCursor()))
          }.toString(),
      )
    }
    val parsedPayload = terminalEvent.payloadJson?.let(::parseJsonOrNull)
    return jsonResponse(
      statusCode = 200,
      body =
        buildJsonObject {
          put("ok", JsonPrimitive(true))
          put("runId", JsonPrimitive(runId))
          put("timedOut", JsonPrimitive(false))
          put("event", JsonPrimitive(terminalEvent.event))
          put("cursor", JsonPrimitive(terminalEvent.id))
          if (parsedPayload != null) {
            put("payload", parsedPayload)
          }
        }.toString(),
    )
  }

  private fun codexAuthStatusResponse(): HttpResponse {
    val provider = codexAuthStatusProvider ?: throw HttpRequestException(404, "Codex auth status is unavailable")
    return jsonResponse(statusCode = 200, body = provider().toString())
  }

  private suspend fun codexAuthRefreshResponse(): HttpResponse {
    val refresh = refreshCodexAuth ?: throw HttpRequestException(404, "Codex auth refresh is unavailable")
    return try {
      jsonResponse(statusCode = 200, body = refresh().toString())
    } catch (err: IllegalStateException) {
      jsonResponse(
        statusCode = 400,
        body =
          buildJsonObject {
            put("ok", JsonPrimitive(false))
            put("error", JsonPrimitive(err.message ?: "OpenAI Codex refresh failed"))
          }.toString(),
      )
    }
  }

  private fun invokeCapabilitiesResponse(): HttpResponse {
    return jsonResponse(
      statusCode = 200,
      body =
        buildJsonObject {
          put(
            "commands",
            buildJsonArray {
              allowedInvokeCommands().forEach { command ->
                add(JsonPrimitive(command))
              }
            },
          )
          put(
            "readCommands",
            buildJsonArray {
              LocalHostCommandPolicy.baseRemoteCommands.forEach { command ->
                add(JsonPrimitive(command))
              }
            },
          )
          put(
            "advancedCommands",
            buildJsonArray {
              LocalHostCommandPolicy.advancedRemoteCommands.forEach { command ->
                add(JsonPrimitive(command))
              }
            },
          )
          put(
            "writeCommands",
            buildJsonArray {
              LocalHostCommandPolicy.writeRemoteCommands.forEach { command ->
                add(JsonPrimitive(command))
              }
            },
          )
          put("advancedEnabled", JsonPrimitive(allowAdvancedInvokeCommands()))
          put("writeEnabled", JsonPrimitive(allowWriteInvokeCommands()))
        }.toString(),
    )
  }

  private fun examplesResponse(): HttpResponse {
    val baseUrl = state.value.listenUrl ?: "http://<phone-ip>:${activeConfig?.port ?: 3945}"
    val tokenPlaceholder = "<TOKEN>"
    return jsonResponse(
      statusCode = 200,
      body =
        buildJsonObject {
          put("baseUrl", JsonPrimitive(baseUrl))
          put("tokenPlaceholder", JsonPrimitive(tokenPlaceholder))
          put(
            "examples",
            buildJsonArray {
              if (codexAuthStatusProvider != null) {
                add(
                  buildJsonObject {
                    put("name", JsonPrimitive("auth-codex-status"))
                    put("method", JsonPrimitive("GET"))
                    put("path", JsonPrimitive("$apiBasePath/auth/codex/status"))
                    put(
                      "curl",
                      JsonPrimitive(
                        "curl -H 'Authorization: Bearer $tokenPlaceholder' $baseUrl$apiBasePath/auth/codex/status",
                      ),
                    )
                  },
                )
              }
              if (refreshCodexAuth != null) {
                add(
                  buildJsonObject {
                    put("name", JsonPrimitive("auth-codex-refresh"))
                    put("method", JsonPrimitive("POST"))
                    put("path", JsonPrimitive("$apiBasePath/auth/codex/refresh"))
                    put(
                      "curl",
                      JsonPrimitive(
                        "curl -X POST -H 'Authorization: Bearer $tokenPlaceholder' $baseUrl$apiBasePath/auth/codex/refresh",
                      ),
                    )
                  },
                )
              }
              add(
                buildJsonObject {
                  put("name", JsonPrimitive("status"))
                  put("method", JsonPrimitive("GET"))
                  put("path", JsonPrimitive("$apiBasePath/status"))
                  put(
                    "curl",
                    JsonPrimitive(
                      "curl -H 'Authorization: Bearer $tokenPlaceholder' $baseUrl$apiBasePath/status",
                    ),
                  )
                },
              )
              add(
                buildJsonObject {
                  put("name", JsonPrimitive("health"))
                  put("method", JsonPrimitive("GET"))
                  put("path", JsonPrimitive("$apiBasePath/health"))
                  put(
                    "curl",
                    JsonPrimitive(
                      "curl -H 'Authorization: Bearer $tokenPlaceholder' $baseUrl$apiBasePath/health",
                    ),
                  )
                },
              )
              add(
                buildJsonObject {
                  put("name", JsonPrimitive("chat-send-wait"))
                  put("method", JsonPrimitive("POST"))
                  put("path", JsonPrimitive("$apiBasePath/chat/send-wait"))
                  put(
                    "curl",
                    JsonPrimitive(
                      "curl -X POST -H 'Authorization: Bearer $tokenPlaceholder' -H 'Content-Type: application/json' $baseUrl$apiBasePath/chat/send-wait -d '{\"message\":\"Summarize my notifications\",\"waitMs\":30000}'",
                    ),
                  )
                },
              )
              add(
                buildJsonObject {
                  put("name", JsonPrimitive("events"))
                  put("method", JsonPrimitive("GET"))
                  put("path", JsonPrimitive("$apiBasePath/events"))
                  put(
                    "curl",
                    JsonPrimitive(
                      "curl -H 'Authorization: Bearer $tokenPlaceholder' '$baseUrl$apiBasePath/events?cursor=0&waitMs=20000'",
                    ),
                  )
                },
              )
              add(
                buildJsonObject {
                  put("name", JsonPrimitive("invoke-device-status"))
                  put("method", JsonPrimitive("POST"))
                  put("path", JsonPrimitive("$apiBasePath/invoke"))
                  put(
                    "curl",
                    JsonPrimitive(
                      "curl -X POST -H 'Authorization: Bearer $tokenPlaceholder' -H 'Content-Type: application/json' $baseUrl$apiBasePath/invoke -d '{\"command\":\"device.status\"}'",
                    ),
                  )
                },
              )
              if (allowAdvancedInvokeCommands()) {
                add(
                  buildJsonObject {
                    put("name", JsonPrimitive("invoke-camera-snap"))
                    put("method", JsonPrimitive("POST"))
                    put("path", JsonPrimitive("$apiBasePath/invoke"))
                    put(
                      "curl",
                      JsonPrimitive(
                        "curl -X POST -H 'Authorization: Bearer $tokenPlaceholder' -H 'Content-Type: application/json' $baseUrl$apiBasePath/invoke -d '{\"command\":\"camera.snap\"}'",
                      ),
                    )
                  },
                )
              }
              if (allowWriteInvokeCommands()) {
                add(
                  buildJsonObject {
                    put("name", JsonPrimitive("invoke-sms-send"))
                    put("method", JsonPrimitive("POST"))
                    put("path", JsonPrimitive("$apiBasePath/invoke"))
                    put(
                      "curl",
                      JsonPrimitive(
                        "curl -X POST -H 'Authorization: Bearer $tokenPlaceholder' -H 'Content-Type: application/json' $baseUrl$apiBasePath/invoke -d '{\"command\":\"sms.send\",\"params\":{\"to\":\"+15551234567\",\"body\":\"Check in when you land.\"}}'",
                      ),
                    )
                  },
                )
                add(
                  buildJsonObject {
                    put("name", JsonPrimitive("invoke-ui-launch-app"))
                    put("method", JsonPrimitive("POST"))
                    put("path", JsonPrimitive("$apiBasePath/invoke"))
                    put(
                      "curl",
                      JsonPrimitive(
                        "curl -X POST -H 'Authorization: Bearer $tokenPlaceholder' -H 'Content-Type: application/json' $baseUrl$apiBasePath/invoke -d '{\"command\":\"ui.launchApp\",\"params\":{\"packageName\":\"com.android.settings\"}}'",
                      ),
                    )
                  },
                )
                add(
                  buildJsonObject {
                    put("name", JsonPrimitive("invoke-ui-input-text"))
                    put("method", JsonPrimitive("POST"))
                    put("path", JsonPrimitive("$apiBasePath/invoke"))
                    put(
                      "curl",
                      JsonPrimitive(
                        "curl -X POST -H 'Authorization: Bearer $tokenPlaceholder' -H 'Content-Type: application/json' $baseUrl$apiBasePath/invoke -d '{\"command\":\"ui.inputText\",\"params\":{\"value\":\"OpenClaw\"}}'",
                      ),
                    )
                  },
                )
                add(
                  buildJsonObject {
                    put("name", JsonPrimitive("invoke-ui-tap"))
                    put("method", JsonPrimitive("POST"))
                    put("path", JsonPrimitive("$apiBasePath/invoke"))
                    put(
                      "curl",
                      JsonPrimitive(
                        "curl -X POST -H 'Authorization: Bearer $tokenPlaceholder' -H 'Content-Type: application/json' $baseUrl$apiBasePath/invoke -d '{\"command\":\"ui.tap\",\"params\":{\"text\":\"Chat\",\"matchMode\":\"exact\"}}'",
                      ),
                    )
                  },
                )
              }
            },
          )
        }.toString(),
    )
  }

  private suspend fun forwardToLocalHost(
    method: String,
    params: JsonObject?,
  ): HttpResponse {
    val body = handleLocalHostRequest(method, params?.toString(), defaultTimeoutMs)
    return jsonResponse(statusCode = 200, body = body)
  }

  private suspend fun handleInvokeRequest(body: JsonObject): HttpResponse {
    val command = body["command"].asStringOrNull()?.trim().orEmpty()
    if (command.isEmpty()) {
      throw HttpRequestException(400, "command is required")
    }
    if (command !in allowedInvokeCommands()) {
      throw HttpRequestException(403, "command is not enabled for remote access")
    }
    val paramsJson =
      body["params"]?.let { params ->
        if (params is JsonNull) {
          null
        } else {
          params.toString()
        }
      }
    val result = handleInvoke(command, paramsJson)
    val responseBody =
      buildJsonObject {
        put("ok", JsonPrimitive(result.ok))
        result.payloadJson?.let { raw ->
          val parsedPayload = parseJsonOrNull(raw)
          if (parsedPayload != null) {
            put("payload", parsedPayload)
          } else {
            put("payloadJSON", JsonPrimitive(raw))
          }
        }
        result.error?.let { error ->
          put(
            "error",
            buildJsonObject {
              put("code", JsonPrimitive(error.code))
              put("message", JsonPrimitive(error.message))
            },
          )
        }
      }.toString()
    return jsonResponse(statusCode = if (result.ok) 200 else 400, body = responseBody)
  }

  private fun requireJsonBody(request: HttpRequest): JsonObject {
    val body = request.body.trim()
    if (body.isEmpty()) {
      throw HttpRequestException(400, "JSON body is required")
    }
    return json.parseToJsonElement(body).asObjectOrNull()
      ?: throw HttpRequestException(400, "JSON object body is required")
  }

  private fun readRequest(socket: Socket): HttpRequest? {
    val input = socket.getInputStream().buffered()
    val headerBytes = ByteArrayOutputStream()
    var matchLength = 0

    while (matchLength < 4) {
      val next = input.read()
      if (next == -1) {
        return if (headerBytes.size() == 0) {
          null
        } else {
          throw HttpRequestException(400, "Unexpected end of request")
        }
      }
      headerBytes.write(next)
      if (headerBytes.size() > maxHeaderBytes) {
        throw HttpRequestException(431, "Request headers are too large")
      }
      matchLength =
        when {
          matchLength == 0 && next == '\r'.code -> 1
          matchLength == 1 && next == '\n'.code -> 2
          matchLength == 2 && next == '\r'.code -> 3
          matchLength == 3 && next == '\n'.code -> 4
          next == '\r'.code -> 1
          else -> 0
        }
    }

    val headerText = headerBytes.toString(Charsets.UTF_8.name())
    val lines = headerText.split("\r\n").filter { it.isNotEmpty() }
    val requestLine = lines.firstOrNull() ?: throw HttpRequestException(400, "Missing request line")
    val parts = requestLine.split(" ", limit = 3)
    if (parts.size < 2) throw HttpRequestException(400, "Invalid request line")

    val headers = linkedMapOf<String, String>()
    lines.drop(1).forEach { line ->
      val idx = line.indexOf(':')
      if (idx <= 0) return@forEach
      val name = line.substring(0, idx).trim().lowercase()
      val value = line.substring(idx + 1).trim()
      headers[name] = value
    }

    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
    if (contentLength < 0 || contentLength > maxBodyBytes) {
      throw HttpRequestException(413, "Request body is too large")
    }
    val bodyBytes = ByteArray(contentLength)
    var offset = 0
    while (offset < contentLength) {
      val read = input.read(bodyBytes, offset, contentLength - offset)
      if (read == -1) throw HttpRequestException(400, "Unexpected end of request body")
      offset += read
    }

    return HttpRequest(
      method = parts[0].uppercase(),
      pathWithQuery = parts[1],
      headers = headers,
      body = String(bodyBytes, Charsets.UTF_8),
    )
  }

  private fun writeResponse(
    socket: Socket,
    response: HttpResponse,
  ) {
    val bodyBytes = response.body.toByteArray(Charsets.UTF_8)
    val headerText =
      buildString {
        append("HTTP/1.1 ")
        append(response.statusCode)
        append(" ")
        append(statusLabel(response.statusCode))
        append("\r\n")
        append("Content-Type: ${response.contentType}\r\n")
        append("Content-Length: ${bodyBytes.size}\r\n")
        append("Access-Control-Allow-Origin: *\r\n")
        append("Access-Control-Allow-Headers: authorization, content-type\r\n")
        append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        append("Connection: close\r\n")
        response.extraHeaders.forEach { (key, value) ->
          append("$key: $value\r\n")
        }
        append("\r\n")
      }.toByteArray(Charsets.UTF_8)

    socket.getOutputStream().use { output ->
      output.write(headerText)
      if (bodyBytes.isNotEmpty()) {
        output.write(bodyBytes)
      }
      output.flush()
    }
  }

  private fun jsonResponse(
    statusCode: Int,
    body: String,
    extraHeaders: Map<String, String> = emptyMap(),
  ): HttpResponse {
    return HttpResponse(
      statusCode = statusCode,
      body = body,
      extraHeaders = extraHeaders,
    )
  }

  private fun statusLabel(statusCode: Int): String {
    return when (statusCode) {
      202 -> "Accepted"
      200 -> "OK"
      204 -> "No Content"
      400 -> "Bad Request"
      401 -> "Unauthorized"
      403 -> "Forbidden"
      404 -> "Not Found"
      413 -> "Payload Too Large"
      431 -> "Request Header Fields Too Large"
      else -> "Internal Server Error"
    }
  }

  private fun isAuthorized(
    authorizationHeader: String?,
    expectedToken: String,
  ): Boolean {
    val prefix = "Bearer "
    val providedToken =
      authorizationHeader
        ?.takeIf { it.startsWith(prefix, ignoreCase = true) }
        ?.substring(prefix.length)
        ?.trim()
        .orEmpty()
    if (providedToken.isEmpty()) return false
    return MessageDigest.isEqual(
      providedToken.toByteArray(Charsets.UTF_8),
      expectedToken.toByteArray(Charsets.UTF_8),
    )
  }

  private fun detectListenUrl(port: Int): String? {
    val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return null
    return runCatching {
      val addresses =
        Collections.list(interfaces)
          .filter { iface -> runCatching { iface.isUp && !iface.isLoopback }.getOrDefault(false) }
          .flatMap { iface -> Collections.list(iface.inetAddresses) }
          .filterIsInstance<Inet4Address>()
          .filterNot { it.isLoopbackAddress }

      val preferred =
        addresses.firstOrNull { address ->
          address.isSiteLocalAddress
        } ?: addresses.firstOrNull()

      preferred?.hostAddress?.takeIf { it.isNotBlank() }?.let { "http://$it:$port" }
    }.getOrNull()
  }

  private fun ensureEventClientRegistered() {
    if (eventClientId != null) return
    val registrar = registerEventClient ?: return
    eventClientId =
      registrar { event, payloadJson ->
        eventBuffer.append(event = event, payloadJson = payloadJson)
      }
  }

  private fun unregisterEventClient() {
    val clientId = eventClientId ?: return
    eventClientId = null
    try {
      unregisterEventClientFn?.invoke(clientId)
    } catch (_: Throwable) {
      // Ignore listener cleanup failures while shutting down remote access.
    }
  }

  private suspend fun waitForTerminalRunEvent(
    runId: String,
    cursor: Long,
    waitMs: Long,
  ): BufferedRemoteAccessEvent? {
    val deadlineMs = System.currentTimeMillis() + waitMs
    var nextCursor = cursor
    while (true) {
      val remainingMs = deadlineMs - System.currentTimeMillis()
      if (remainingMs <= 0) {
        return null
      }
      val events =
        try {
          eventBuffer.awaitAfter(
            cursor = nextCursor,
            limit = maxEventsPerResponse,
            waitMs = remainingMs.coerceAtMost(maxEventWaitMs),
          )
        } catch (_: TimeoutCancellationException) {
          return null
        }
      events.firstOrNull { event ->
        isTerminalRunEvent(event = event, runId = runId)
      }?.let { return it }
      nextCursor = events.lastOrNull()?.id ?: nextCursor
    }
  }

  private fun isTerminalRunEvent(
    event: BufferedRemoteAccessEvent,
    runId: String,
  ): Boolean {
    if (event.event != "chat") return false
    val payload = event.payloadJson?.let(::parseJsonOrNull).asObjectOrNull() ?: return false
    val payloadRunId = payload["runId"].asStringOrNull() ?: return false
    val state = payload["state"].asStringOrNull()?.trim().orEmpty()
    if (payloadRunId != runId) return false
    return state == "final" || state == "error" || state == "aborted"
  }

  private fun allowedInvokeCommands(): List<String> {
    return LocalHostCommandPolicy.allowedRemoteCommands(
      allowAdvanced = allowAdvancedInvokeCommands(),
      allowWrite = allowWriteInvokeCommands(),
    ).toList()
  }
}

private class HttpRequestException(
  val statusCode: Int,
  val messageText: String,
) : IllegalStateException(messageText)

private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.asStringOrNull(): String? =
  when (this) {
    is JsonPrimitive -> content
    else -> null
  }

private fun JsonElement?.asLongOrNull(): Long? =
  when (this) {
    is JsonPrimitive -> content.toLongOrNull()
    else -> null
  }

private fun parseJsonOrNull(value: String): JsonElement? =
  runCatching {
    Json.Default.parseToJsonElement(value)
  }.getOrNull()
