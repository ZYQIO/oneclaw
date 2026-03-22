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
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

class LocalHostRemoteAccessServer(
  private val scope: CoroutineScope,
  private val json: Json,
  private val handleLocalHostRequest: suspend (method: String, paramsJson: String?, timeoutMs: Long) -> String,
  private val handleInvoke: suspend (command: String, paramsJson: String?) -> GatewaySession.InvokeResult,
) {
  companion object {
    private const val apiBasePath = "/api/local-host/v1"
    private const val maxHeaderBytes = 16 * 1024
    private const val maxBodyBytes = 12 * 1024 * 1024
    private const val defaultTimeoutMs = 30_000L
    private val allowedInvokeCommands =
      setOf(
        "device.health",
        "device.info",
        "device.permissions",
        "device.status",
        "system.notify",
      )
  }

  private val _state = MutableStateFlow(LocalHostRemoteAccessState())
  val state: StateFlow<LocalHostRemoteAccessState> = _state.asStateFlow()

  @Volatile private var serverSocket: ServerSocket? = null
  @Volatile private var activeConfig: RemoteAccessConfig? = null
  private var acceptJob: Job? = null

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
    if (command !in allowedInvokeCommands) {
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

private fun parseJsonOrNull(value: String): JsonElement? =
  runCatching {
    Json.Default.parseToJsonElement(value)
  }.getOrNull()
