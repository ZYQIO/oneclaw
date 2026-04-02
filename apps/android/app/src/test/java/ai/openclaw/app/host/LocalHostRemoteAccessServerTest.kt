package ai.openclaw.app.host

import ai.openclaw.app.auth.OpenAICodexCredential
import ai.openclaw.app.gateway.GatewaySession
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalHostRemoteAccessServerTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun health_requiresBearerToken() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val port = reservePort()
    val server =
      LocalHostRemoteAccessServer(
        scope = scope,
        json = json,
        handleLocalHostRequest = { _, _, _ -> """{"ok":true}""" },
        handleInvoke = { _, _ -> GatewaySession.InvokeResult.ok(null) },
      )

    try {
      server.start(port = port, token = "secret-token")

      val response = request(port = port, method = "GET", path = "/api/local-host/v1/health")

      assertEquals(401, response.statusCode)
      assertTrue(response.body.contains("invalid bearer token"))
    } finally {
      server.stop()
      scope.cancel()
    }
  }

  @Test
  fun chatSendWait_returnsTerminalChatEvent() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val port = reservePort()
    val eventSinkRef = AtomicReference<((String, String?) -> Unit)?>(null)
    val server =
      LocalHostRemoteAccessServer(
        scope = scope,
        json = json,
        handleLocalHostRequest = { method, _, _ ->
          when (method) {
            "chat.send" -> """{"runId":"run-123","status":"started"}"""
            else -> """{"ok":true}"""
          }
        },
        handleInvoke = { _, _ -> GatewaySession.InvokeResult.ok(null) },
        registerEventClient = { onEvent ->
          eventSinkRef.set(onEvent)
          "remote-events"
        },
        unregisterEventClientFn = { _ ->
          eventSinkRef.set(null)
        },
      )

    try {
      server.start(port = port, token = "secret-token")

      Thread {
        Thread.sleep(80)
        eventSinkRef.get()?.invoke(
          "chat",
          """{"runId":"run-123","state":"final","message":{"role":"assistant","content":[{"type":"text","text":"hello"}]}}""",
        )
      }.start()

      val response =
        request(
          port = port,
          method = "POST",
          path = "/api/local-host/v1/chat/send-wait",
          token = "secret-token",
          body = """{"message":"hello","waitMs":5000}""",
        )

      assertEquals(200, response.statusCode)
      assertTrue(response.body.contains("run-123"))
      assertTrue(response.body.contains("\"timedOut\":false"))
      assertTrue(response.body.contains("\"state\":\"final\""))
    } finally {
      server.stop()
      scope.cancel()
    }
  }

  @Test
  fun status_returnsRemoteAccessAndHostReadinessSnapshot() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val port = reservePort()
    val server =
      LocalHostRemoteAccessServer(
        scope = scope,
        json = json,
        handleLocalHostRequest = { _, _, _ -> """{"ok":true}""" },
        handleInvoke = { _, _ -> GatewaySession.InvokeResult.ok(null) },
        allowAdvancedInvokeCommands = { true },
        allowWriteInvokeCommands = { true },
        statusSnapshotProvider = {
          buildJsonObject {
            put("codexAuthConfigured", JsonPrimitive(true))
            put("uiAutomationAvailable", JsonPrimitive(true))
            put(
              "codexAuth",
              buildJsonObject {
                put("configured", JsonPrimitive(true))
                put("refreshRecommended", JsonPrimitive(false))
              },
            )
            put(
              "uiAutomation",
              buildJsonObject {
                put("enabled", JsonPrimitive(true))
                put("serviceConnected", JsonPrimitive(true))
                put("available", JsonPrimitive(true))
              },
            )
            put("sessionCount", JsonPrimitive(2))
            put("activeRunCount", JsonPrimitive(1))
            put(
              "deployment",
              buildJsonObject {
                put("dedicatedEnabled", JsonPrimitive(true))
                put("batteryOptimizationIgnored", JsonPrimitive(false))
                put("recentsSwipeForceStopRisk", JsonPrimitive(true))
                put("taskLockRecommended", JsonPrimitive(true))
              },
            )
          }
        },
      )

    try {
      server.start(port = port, token = "secret-token")

      val response =
        request(
          port = port,
          method = "GET",
          path = "/api/local-host/v1/status",
          token = "secret-token",
        )

      assertEquals(200, response.statusCode)
      assertTrue(response.body.contains("\"mode\":\"local-host\""))
      assertTrue(response.body.contains("\"remoteAccess\""))
      assertTrue(response.body.contains("\"advancedEnabled\":true"))
      assertTrue(response.body.contains("\"writeEnabled\":true"))
      assertTrue(response.body.contains("\"codexAuthConfigured\":true"))
      assertTrue(response.body.contains("\"codexAuth\""))
      assertTrue(response.body.contains("\"uiAutomationAvailable\":true"))
      assertTrue(response.body.contains("\"uiAutomation\""))
      assertTrue(response.body.contains("\"sessionCount\":2"))
      assertTrue(response.body.contains("\"activeRunCount\":1"))
      assertTrue(response.body.contains("\"deployment\""))
      assertTrue(response.body.contains("\"dedicatedEnabled\":true"))
      assertTrue(response.body.contains("\"taskLockRecommended\":true"))
      assertTrue(response.body.contains("\"sms.send\""))
      assertTrue(response.body.contains("\"ui.launchApp\""))
      assertTrue(response.body.contains("\"ui.inputText\""))
      assertTrue(response.body.contains("\"ui.tap\""))
      assertTrue(response.body.contains("\"ui.swipe\""))
      assertTrue(response.body.contains("\"ui.back\""))
      assertTrue(response.body.contains("\"ui.home\""))
    } finally {
      server.stop()
      scope.cancel()
    }
  }

  @Test
  fun invokeCapabilities_listsAllowedReadOnlyCommands() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val port = reservePort()
    val server =
      LocalHostRemoteAccessServer(
        scope = scope,
        json = json,
        handleLocalHostRequest = { _, _, _ -> """{"ok":true}""" },
        handleInvoke = { _, _ -> GatewaySession.InvokeResult.ok(null) },
      )

    try {
      server.start(port = port, token = "secret-token")

      val response =
        request(
          port = port,
          method = "GET",
          path = "/api/local-host/v1/invoke/capabilities",
          token = "secret-token",
        )

      assertEquals(200, response.statusCode)
      assertTrue(response.body.contains("\"device.status\""))
      assertTrue(response.body.contains("\"pod.health\""))
      assertTrue(response.body.contains("\"pod.workspace.scan\""))
      assertTrue(response.body.contains("\"notifications.list\""))
      assertTrue(response.body.contains("\"location.get\""))
      assertTrue(response.body.contains("\"writeEnabled\":false"))
    } finally {
      server.stop()
      scope.cancel()
    }
  }

  @Test
  fun invoke_rejectsCommandsOutsideRemoteAllowlist() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val port = reservePort()
    val server =
      LocalHostRemoteAccessServer(
        scope = scope,
        json = json,
        handleLocalHostRequest = { _, _, _ -> """{"ok":true}""" },
        handleInvoke = { _, _ -> GatewaySession.InvokeResult.ok(null) },
      )

    try {
      server.start(port = port, token = "secret-token")

      val response =
        request(
          port = port,
          method = "POST",
          path = "/api/local-host/v1/invoke",
          token = "secret-token",
          body = """{"command":"camera.snap"}""",
        )

      assertEquals(403, response.statusCode)
      assertTrue(response.body.contains("not enabled for remote access"))
    } finally {
      server.stop()
      scope.cancel()
    }
  }

  @Test
  fun invokeCapabilities_includesCameraCommandsWhenAdvancedModeIsEnabled() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val port = reservePort()
    val server =
      LocalHostRemoteAccessServer(
        scope = scope,
        json = json,
        handleLocalHostRequest = { _, _, _ -> """{"ok":true}""" },
        handleInvoke = { _, _ -> GatewaySession.InvokeResult.ok(null) },
        allowAdvancedInvokeCommands = { true },
      )

    try {
      server.start(port = port, token = "secret-token")

      val response =
        request(
          port = port,
          method = "GET",
          path = "/api/local-host/v1/invoke/capabilities",
          token = "secret-token",
        )

      assertEquals(200, response.statusCode)
      assertTrue(response.body.contains("\"advancedEnabled\":true"))
      assertTrue(response.body.contains("\"camera.snap\""))
      assertTrue(response.body.contains("\"camera.clip\""))
    } finally {
      server.stop()
      scope.cancel()
    }
  }

  @Test
  fun invokeCapabilities_includesWriteCommandsWhenWriteModeIsEnabled() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val port = reservePort()
    val server =
      LocalHostRemoteAccessServer(
        scope = scope,
        json = json,
        handleLocalHostRequest = { _, _, _ -> """{"ok":true}""" },
        handleInvoke = { _, _ -> GatewaySession.InvokeResult.ok(null) },
        allowWriteInvokeCommands = { true },
      )

    try {
      server.start(port = port, token = "secret-token")

      val response =
        request(
          port = port,
          method = "GET",
          path = "/api/local-host/v1/invoke/capabilities",
          token = "secret-token",
        )

      assertEquals(200, response.statusCode)
      assertTrue(response.body.contains("\"writeEnabled\":true"))
      assertTrue(response.body.contains("\"sms.send\""))
      assertTrue(response.body.contains("\"notifications.actions\""))
      assertTrue(response.body.contains("\"contacts.add\""))
      assertTrue(response.body.contains("\"calendar.add\""))
      assertTrue(response.body.contains("\"ui.launchApp\""))
      assertTrue(response.body.contains("\"ui.inputText\""))
      assertTrue(response.body.contains("\"ui.tap\""))
      assertTrue(response.body.contains("\"ui.back\""))
      assertTrue(response.body.contains("\"ui.home\""))
    } finally {
      server.stop()
      scope.cancel()
    }
  }

  @Test
  fun invoke_rejectsWriteCommandsWhenWriteModeIsDisabled() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val port = reservePort()
    val server =
      LocalHostRemoteAccessServer(
        scope = scope,
        json = json,
        handleLocalHostRequest = { _, _, _ -> """{"ok":true}""" },
        handleInvoke = { _, _ -> GatewaySession.InvokeResult.ok(null) },
      )

    try {
      server.start(port = port, token = "secret-token")

      val response =
        request(
          port = port,
          method = "POST",
          path = "/api/local-host/v1/invoke",
          token = "secret-token",
          body = """{"command":"sms.send","params":{"to":"+15551234567","body":"hello"}}""",
        )

      assertEquals(403, response.statusCode)
      assertTrue(response.body.contains("not enabled for remote access"))
    } finally {
      server.stop()
      scope.cancel()
    }
  }

  @Test
  fun examples_returnsReadyToUseCurlTemplates() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val port = reservePort()
    val server =
      LocalHostRemoteAccessServer(
        scope = scope,
        json = json,
        handleLocalHostRequest = { _, _, _ -> """{"ok":true}""" },
        handleInvoke = { _, _ -> GatewaySession.InvokeResult.ok(null) },
        codexAuthStatusProvider = {
          buildJsonObject {
            put("configured", JsonPrimitive(true))
          }
        },
        importCodexAuth = { _, _ ->
          buildJsonObject {
            put("imported", JsonPrimitive(true))
          }
        },
        refreshCodexAuth = {
          buildJsonObject {
            put("refreshed", JsonPrimitive(true))
          }
        },
      )

    try {
      server.start(port = port, token = "secret-token")

      val response =
        request(
          port = port,
          method = "GET",
          path = "/api/local-host/v1/examples",
          token = "secret-token",
        )

      assertEquals(200, response.statusCode)
      assertTrue(response.body.contains("\"auth-codex-status\""))
      assertTrue(response.body.contains("\"auth-codex-import\""))
      assertTrue(response.body.contains("\"auth-codex-refresh\""))
      assertTrue(response.body.contains("\"status\""))
      assertTrue(response.body.contains("/api/local-host/v1/status"))
      assertTrue(response.body.contains("\"chat-send-wait\""))
      assertTrue(response.body.contains("/api/local-host/v1/events"))
      assertTrue(response.body.contains("<TOKEN>"))
      assertTrue(!response.body.contains("\"sms.send\""))
    } finally {
      server.stop()
      scope.cancel()
    }
  }

  @Test
  fun codexAuthStatus_returnsSanitizedCredentialMetadata() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val port = reservePort()
    val server =
      LocalHostRemoteAccessServer(
        scope = scope,
        json = json,
        handleLocalHostRequest = { _, _, _ -> """{"ok":true}""" },
        handleInvoke = { _, _ -> GatewaySession.InvokeResult.ok(null) },
        codexAuthStatusProvider = {
          buildJsonObject {
            put("provider", JsonPrimitive("openai-codex"))
            put("configured", JsonPrimitive(true))
            put("emailHint", JsonPrimitive("p***n@example.com"))
            put("expiresAt", JsonPrimitive(1234))
            put("refreshRecommended", JsonPrimitive(false))
          }
        },
      )

    try {
      server.start(port = port, token = "secret-token")

      val response =
        request(
          port = port,
          method = "GET",
          path = "/api/local-host/v1/auth/codex/status",
          token = "secret-token",
        )

      assertEquals(200, response.statusCode)
      assertTrue(response.body.contains("\"configured\":true"))
      assertTrue(response.body.contains("\"emailHint\":\"p***n@example.com\""))
      assertTrue(response.body.contains("\"refreshRecommended\":false"))
    } finally {
      server.stop()
      scope.cancel()
    }
  }

  @Test
  fun codexAuthRefresh_returnsStructuredErrorWhenCredentialMissing() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val port = reservePort()
    val server =
      LocalHostRemoteAccessServer(
        scope = scope,
        json = json,
        handleLocalHostRequest = { _, _, _ -> """{"ok":true}""" },
        handleInvoke = { _, _ -> GatewaySession.InvokeResult.ok(null) },
        refreshCodexAuth = {
          throw IllegalStateException("OpenAI Codex login required")
        },
      )

    try {
      server.start(port = port, token = "secret-token")

      val response =
        request(
          port = port,
          method = "POST",
          path = "/api/local-host/v1/auth/codex/refresh",
          token = "secret-token",
        )

      assertEquals(400, response.statusCode)
      assertTrue(response.body.contains("\"ok\":false"))
      assertTrue(response.body.contains("OpenAI Codex login required"))
    } finally {
      server.stop()
      scope.cancel()
    }
  }

  @Test
  fun codexAuthImport_savesCredentialPayloadAndReturnsMetadata() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val port = reservePort()
    val importedCredential = AtomicReference<OpenAICodexCredential?>(null)
    val importedSource = AtomicReference<String?>(null)
    val server =
      LocalHostRemoteAccessServer(
        scope = scope,
        json = json,
        handleLocalHostRequest = { _, _, _ -> """{"ok":true}""" },
        handleInvoke = { _, _ -> GatewaySession.InvokeResult.ok(null) },
        importCodexAuth = { credential, source ->
          importedCredential.set(credential)
          importedSource.set(source)
          buildJsonObject {
            put("provider", JsonPrimitive("openai-codex"))
            put("configured", JsonPrimitive(true))
            put("imported", JsonPrimitive(true))
            put("source", JsonPrimitive(source))
            put("expiresAt", JsonPrimitive(credential.expires))
          }
        },
      )

    try {
      server.start(port = port, token = "secret-token")

      val response =
        request(
          port = port,
          method = "POST",
          path = "/api/local-host/v1/auth/codex/import",
          token = "secret-token",
          body =
            """
            {"access":"access-123","refresh":"refresh-123","expires":123456,"accountId":"acct_123","email":"person@example.com","source":"desktop-sync"}
            """.trimIndent(),
        )

      assertEquals(200, response.statusCode)
      assertTrue(response.body.contains("\"imported\":true"))
      assertTrue(response.body.contains("\"source\":\"desktop-sync\""))
      assertEquals("access-123", importedCredential.get()?.access)
      assertEquals("refresh-123", importedCredential.get()?.refresh)
      assertEquals(123456L, importedCredential.get()?.expires)
      assertEquals("acct_123", importedCredential.get()?.accountId)
      assertEquals("person@example.com", importedCredential.get()?.email)
      assertEquals("desktop-sync", importedSource.get())
    } finally {
      server.stop()
      scope.cancel()
    }
  }

  @Test
  fun codexAuthImport_rejectsInvalidBodyWithStructuredError() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val port = reservePort()
    val server =
      LocalHostRemoteAccessServer(
        scope = scope,
        json = json,
        handleLocalHostRequest = { _, _, _ -> """{"ok":true}""" },
        handleInvoke = { _, _ -> GatewaySession.InvokeResult.ok(null) },
        importCodexAuth = { _, _ ->
          buildJsonObject {
            put("ok", JsonPrimitive(true))
          }
        },
      )

    try {
      server.start(port = port, token = "secret-token")

      val response =
        request(
          port = port,
          method = "POST",
          path = "/api/local-host/v1/auth/codex/import",
          token = "secret-token",
          body = """{"refresh":"refresh-only"}""",
        )

      assertEquals(400, response.statusCode)
      assertTrue(response.body.contains("\"ok\":false"))
      assertTrue(response.body.contains("access is required"))
    } finally {
      server.stop()
      scope.cancel()
    }
  }

  @Test
  fun codexAuthRefresh_returnsRefreshedMetadata() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val port = reservePort()
    val server =
      LocalHostRemoteAccessServer(
        scope = scope,
        json = json,
        handleLocalHostRequest = { _, _, _ -> """{"ok":true}""" },
        handleInvoke = { _, _ -> GatewaySession.InvokeResult.ok(null) },
        refreshCodexAuth = {
          buildJsonObject {
            put("provider", JsonPrimitive("openai-codex"))
            put("configured", JsonPrimitive(true))
            put("refreshed", JsonPrimitive(true))
            put("previousExpiresAt", JsonPrimitive(1000))
            put("expiresAt", JsonPrimitive(5000))
          }
        },
      )

    try {
      server.start(port = port, token = "secret-token")

      val response =
        request(
          port = port,
          method = "POST",
          path = "/api/local-host/v1/auth/codex/refresh",
          token = "secret-token",
        )

      assertEquals(200, response.statusCode)
      assertTrue(response.body.contains("\"refreshed\":true"))
      assertTrue(response.body.contains("\"previousExpiresAt\":1000"))
      assertTrue(response.body.contains("\"expiresAt\":5000"))
    } finally {
      server.stop()
      scope.cancel()
    }
  }

  @Test
  fun examples_includeWriteCommandTemplateWhenWriteModeIsEnabled() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val port = reservePort()
    val server =
      LocalHostRemoteAccessServer(
        scope = scope,
        json = json,
        handleLocalHostRequest = { _, _, _ -> """{"ok":true}""" },
        handleInvoke = { _, _ -> GatewaySession.InvokeResult.ok(null) },
        allowWriteInvokeCommands = { true },
      )

    try {
      server.start(port = port, token = "secret-token")

      val response =
        request(
          port = port,
          method = "GET",
          path = "/api/local-host/v1/examples",
          token = "secret-token",
        )

      assertEquals(200, response.statusCode)
      assertTrue(response.body.contains("\"invoke-sms-send\""))
      assertTrue(response.body.contains("sms.send"))
      assertTrue(response.body.contains("\"invoke-ui-launch-app\""))
      assertTrue(response.body.contains("ui.launchApp"))
      assertTrue(response.body.contains("\"invoke-ui-input-text\""))
      assertTrue(response.body.contains("ui.inputText"))
      assertTrue(response.body.contains("\"invoke-ui-tap\""))
      assertTrue(response.body.contains("ui.tap"))
      assertTrue(response.body.contains("\"invoke-ui-swipe\""))
      assertTrue(response.body.contains("ui.swipe"))
    } finally {
      server.stop()
      scope.cancel()
    }
  }

  private fun request(
    port: Int,
    method: String,
    path: String,
    token: String? = null,
    body: String? = null,
  ): HttpResponseSnapshot {
    val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
    connection.requestMethod = method
    connection.connectTimeout = 5_000
    connection.readTimeout = 5_000
    connection.setRequestProperty("Accept", "application/json")
    token?.let {
      connection.setRequestProperty("Authorization", "Bearer $it")
    }
    if (body != null) {
      connection.doOutput = true
      connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
      connection.outputStream.use { output ->
        output.write(body.toByteArray(Charsets.UTF_8))
      }
    }
    val statusCode = connection.responseCode
    val stream = connection.errorStream ?: connection.inputStream
    val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    connection.disconnect()
    return HttpResponseSnapshot(statusCode = statusCode, body = responseBody)
  }

  private fun reservePort(): Int {
    return ServerSocket(0).use { it.localPort }
  }
}

private data class HttpResponseSnapshot(
  val statusCode: Int,
  val body: String,
)
