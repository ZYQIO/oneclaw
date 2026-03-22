package ai.openclaw.app.host

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
      assertTrue(response.body.contains("\"notifications.list\""))
      assertTrue(response.body.contains("\"location.get\""))
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
