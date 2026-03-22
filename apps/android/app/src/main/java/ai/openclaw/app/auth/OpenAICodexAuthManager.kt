package ai.openclaw.app.auth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import ai.openclaw.app.SecurePrefs
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class OpenAICodexAuthUiState(
  val inProgress: Boolean = false,
  val manualInputEnabled: Boolean = false,
  val statusText: String? = null,
  val errorText: String? = null,
  val signedInEmail: String? = null,
)

class OpenAICodexAuthManager(
  private val appContext: Context,
  private val prefs: SecurePrefs,
  private val scope: CoroutineScope,
  json: Json = Json { ignoreUnknownKeys = true },
  private val oauthApi: OpenAICodexOAuthApi = OpenAICodexOAuthApi(json = json),
  private val browserLauncher: (String) -> Unit = { url ->
    val intent =
      Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    appContext.startActivity(intent)
  },
  private val serverFactory: OpenAICodexLoopbackServerFactory = OpenAICodexLoopbackServerFactory(),
) {
  private data class ActiveLogin(
    val verifier: String,
    val state: String,
    val server: OpenAICodexLoopbackServer,
    val settled: AtomicBoolean = AtomicBoolean(false),
  )

  private val _uiState =
    MutableStateFlow(
      OpenAICodexAuthUiState(
        signedInEmail = prefs.loadOpenAICodexCredential()?.email,
      ),
    )
  val uiState: StateFlow<OpenAICodexAuthUiState> = _uiState.asStateFlow()

  @Volatile private var activeLogin: ActiveLogin? = null
  private var waitJob: Job? = null

  fun startLogin() {
    cancelLoginInternal()

    val flow = oauthApi.createAuthorizationFlow(originator = "pi")
    val server = serverFactory.create(expectedState = flow.state)
    val active =
      ActiveLogin(
        verifier = flow.verifier,
        state = flow.state,
        server = server,
      )
    activeLogin = active
    _uiState.value =
      OpenAICodexAuthUiState(
        inProgress = true,
        manualInputEnabled = true,
        statusText =
          if (server.isBound) {
            "Browser opened. Finish sign-in there. If the callback stalls, paste the redirect URL or code below."
          } else {
            "Couldn't bind the localhost callback. Finish sign-in in the browser, then paste the redirect URL or code below."
          },
        signedInEmail = prefs.loadOpenAICodexCredential()?.email,
      )

    try {
      browserLauncher(flow.url)
    } catch (err: ActivityNotFoundException) {
      server.close()
      activeLogin = null
      _uiState.value =
        OpenAICodexAuthUiState(
          errorText = "No browser is available for OpenAI sign-in.",
          signedInEmail = prefs.loadOpenAICodexCredential()?.email,
        )
      return
    } catch (err: Throwable) {
      server.close()
      activeLogin = null
      _uiState.value =
        OpenAICodexAuthUiState(
          errorText = err.message ?: "Failed to open the OpenAI sign-in page.",
          signedInEmail = prefs.loadOpenAICodexCredential()?.email,
        )
      return
    }

    waitJob =
      scope.launch {
        val code = active.server.waitForCode() ?: return@launch
        completeAuthorization(active = active, code = code)
      }
  }

  fun submitManualInput(input: String) {
    val active = activeLogin ?: run {
      _uiState.value =
        OpenAICodexAuthUiState(
          errorText = "No OpenAI sign-in is currently running.",
          signedInEmail = prefs.loadOpenAICodexCredential()?.email,
        )
      return
    }
    val parsed = parseAuthorizationInput(input)
    if (!parsed.state.isNullOrBlank() && parsed.state != active.state) {
      _uiState.value =
        _uiState.value.copy(
          errorText = "State mismatch. Start sign-in again and retry.",
        )
      return
    }
    val code = parsed.code?.trim().orEmpty()
    if (code.isEmpty()) {
      _uiState.value =
        _uiState.value.copy(
          errorText = "Paste the redirect URL or authorization code from the browser.",
        )
      return
    }
    active.server.cancelWait()
    waitJob?.cancel()
    waitJob =
      scope.launch {
      completeAuthorization(active = active, code = code)
      }
  }

  fun cancelLogin() {
    cancelLoginInternal()
    _uiState.value =
      OpenAICodexAuthUiState(
        signedInEmail = prefs.loadOpenAICodexCredential()?.email,
      )
  }

  fun clearCredential() {
    cancelLoginInternal()
    prefs.clearOpenAICodexCredential()
    _uiState.value = OpenAICodexAuthUiState()
  }

  private suspend fun completeAuthorization(
    active: ActiveLogin,
    code: String,
  ) {
    if (!active.settled.compareAndSet(false, true)) return
    _uiState.value =
      _uiState.value.copy(
        errorText = null,
        statusText = "Exchanging authorization code…",
      )

    try {
      val credential =
        withContext(Dispatchers.IO) {
          oauthApi.exchangeAuthorizationCode(code = code, verifier = active.verifier)
        }
      prefs.saveOpenAICodexCredential(credential)
      active.server.close()
      clearActiveLogin(active)
      _uiState.value =
        OpenAICodexAuthUiState(
          statusText = "OpenAI Codex is connected.",
          signedInEmail = credential.email,
        )
    } catch (_: CancellationException) {
      active.server.close()
      clearActiveLogin(active)
      return
    } catch (err: Throwable) {
      active.server.close()
      clearActiveLogin(active)
      _uiState.value =
        OpenAICodexAuthUiState(
          errorText = err.message ?: "OpenAI sign-in failed.",
          signedInEmail = prefs.loadOpenAICodexCredential()?.email,
        )
    }
  }

  private fun cancelLoginInternal() {
    waitJob?.cancel()
    waitJob = null
    activeLogin?.server?.cancelWait()
    activeLogin?.server?.close()
    activeLogin = null
  }

  private fun clearActiveLogin(active: ActiveLogin) {
    if (activeLogin === active) {
      activeLogin = null
    }
  }
}

internal data class ParsedAuthorizationInput(
  val code: String? = null,
  val state: String? = null,
)

internal fun parseAuthorizationInput(input: String): ParsedAuthorizationInput {
  val value = input.trim()
  if (value.isEmpty()) return ParsedAuthorizationInput()
  try {
    val uri = Uri.parse(value)
    if (!uri.scheme.isNullOrBlank()) {
      return ParsedAuthorizationInput(
        code = uri.getQueryParameter("code"),
        state = uri.getQueryParameter("state"),
      )
    }
  } catch (_: Throwable) {
    // Ignore parse failures and fall back to the plain-text formats below.
  }
  if (value.contains("#")) {
    val parts = value.split("#", limit = 2)
    return ParsedAuthorizationInput(
      code = parts.getOrNull(0),
      state = parts.getOrNull(1),
    )
  }
  if (value.contains("code=")) {
    val uri = Uri.parse("https://localhost/?$value")
    return ParsedAuthorizationInput(
      code = uri.getQueryParameter("code"),
      state = uri.getQueryParameter("state"),
    )
  }
  return ParsedAuthorizationInput(code = value)
}

internal class OpenAICodexLoopbackServerFactory {
  fun create(expectedState: String): OpenAICodexLoopbackServer {
    return try {
      OpenAICodexLoopbackServer(expectedState)
    } catch (_: Throwable) {
      OpenAICodexLoopbackServer.unbound()
    }
  }
}

internal class OpenAICodexLoopbackServer private constructor(
  private val serverSocket: ServerSocket?,
  private val expectedState: String?,
) {
  companion object {
    private const val callbackPort = 1455
    private const val callbackPath = "/auth/callback"

    fun unbound(): OpenAICodexLoopbackServer {
      return OpenAICodexLoopbackServer(serverSocket = null, expectedState = null)
    }
  }

  constructor(expectedState: String) : this(
    serverSocket =
      ServerSocket().apply {
        reuseAddress = true
        // Bind broadly so localhost callbacks can arrive over either IPv4 or IPv6.
        bind(InetSocketAddress(callbackPort))
      },
    expectedState = expectedState,
  )

  private val result = CompletableDeferred<String?>()
  val isBound: Boolean
    get() = serverSocket != null

  suspend fun waitForCode(): String? {
    if (serverSocket == null) return null
    if (result.isCompleted) return result.await()

    return withContext(Dispatchers.IO) {
      while (!result.isCompleted) {
        val socket =
          try {
            serverSocket.accept()
          } catch (_: SocketException) {
            result.complete(null)
            break
          }
        socket.use(::handleSocket)
      }
      result.await()
    }
  }

  fun cancelWait() {
    result.complete(null)
  }

  fun close() {
    cancelWait()
    try {
      serverSocket?.close()
    } catch (_: Throwable) {
      // Ignore close failures while tearing down the loopback server.
    }
  }

  private fun handleSocket(socket: Socket) {
    socket.soTimeout = 10_000
    if (!socket.inetAddress.isLoopbackAddress) {
      writeHttpResponse(socket, 403, oauthErrorHtml("Only loopback callbacks are allowed."))
      return
    }
    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
    val requestLine = reader.readLine() ?: return
    while (true) {
      val line = reader.readLine() ?: break
      if (line.isEmpty()) break
    }

    val path = requestLine.split(" ").getOrNull(1).orEmpty()
    val uri = Uri.parse("http://localhost$path")
    val (statusCode, html) =
      when {
        uri.path != callbackPath -> 404 to oauthErrorHtml("Callback route not found.")
        uri.getQueryParameter("state") != expectedState -> 400 to oauthErrorHtml("State mismatch.")
        uri.getQueryParameter("code").isNullOrBlank() -> 400 to oauthErrorHtml("Missing authorization code.")
        else -> {
          result.complete(uri.getQueryParameter("code"))
          200 to oauthSuccessHtml("OpenAI authentication completed. You can close this window.")
        }
      }
    writeHttpResponse(socket, statusCode, html)
  }

  private fun writeHttpResponse(
    socket: Socket,
    statusCode: Int,
    html: String,
  ) {
    val body = html.toByteArray(Charsets.UTF_8)
    val header =
      buildString {
        append("HTTP/1.1 ")
        append(statusCode)
        append(if (statusCode == 200) " OK\r\n" else " Error\r\n")
        append("Content-Type: text/html; charset=utf-8\r\n")
        append("Content-Length: ${body.size}\r\n")
        append("Connection: close\r\n\r\n")
      }.toByteArray(Charsets.UTF_8)
    socket.getOutputStream().use { out ->
      out.write(header)
      out.write(body)
      out.flush()
    }
  }
}

private fun oauthSuccessHtml(message: String): String =
  renderOauthHtml(
    title = "Authentication successful",
    heading = "Authentication successful",
    message = message,
  )

private fun oauthErrorHtml(
  message: String,
  details: String? = null,
): String =
  renderOauthHtml(
    title = "Authentication failed",
    heading = "Authentication failed",
    message = message,
    details = details,
  )

private fun renderOauthHtml(
  title: String,
  heading: String,
  message: String,
  details: String? = null,
): String {
  val safeTitle = escapeHtml(title)
  val safeHeading = escapeHtml(heading)
  val safeMessage = escapeHtml(message)
  val safeDetails = details?.let(::escapeHtml)
  return """
    <!doctype html>
    <html lang="en">
    <head>
      <meta charset="utf-8" />
      <meta name="viewport" content="width=device-width, initial-scale=1" />
      <title>$safeTitle</title>
      <style>
        html { color-scheme: dark; }
        body {
          margin: 0;
          min-height: 100vh;
          display: flex;
          align-items: center;
          justify-content: center;
          padding: 24px;
          background: #09090b;
          color: #fafafa;
          font-family: ui-sans-serif, system-ui, sans-serif;
          text-align: center;
        }
        main { max-width: 520px; }
        h1 { margin-bottom: 10px; font-size: 28px; }
        p { margin: 0; color: #a1a1aa; line-height: 1.7; }
        .details {
          margin-top: 16px;
          color: #a1a1aa;
          font-family: ui-monospace, monospace;
          white-space: pre-wrap;
          word-break: break-word;
        }
      </style>
    </head>
    <body>
      <main>
        <h1>$safeHeading</h1>
        <p>$safeMessage</p>
        ${safeDetails?.let { "<div class=\"details\">$it</div>" }.orEmpty()}
      </main>
    </body>
    </html>
  """.trimIndent()
}

private fun escapeHtml(value: String): String {
  return value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")
}
