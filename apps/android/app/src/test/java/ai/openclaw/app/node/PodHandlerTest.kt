package ai.openclaw.app.node

import ai.openclaw.app.ensureEmbeddedRuntimePodInstalled
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
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
class PodHandlerTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun handlePodHealth_reportsMissingExtractionWhenVersionIsAbsent() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    val handler = PodHandler(context)

    val result = handler.handlePodHealth(null)

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals("pod.health", payload.getValue("command").jsonPrimitive.content)
    assertEquals(false, payload.getValue("localExecutionAvailable").jsonPrimitive.boolean)
    assertEquals("not_extracted", payload.getValue("reason").jsonPrimitive.content)
    assertEquals(false, payload.getValue("ready").jsonPrimitive.boolean)
    assertEquals(0, payload.getValue("verifiedFileCount").jsonPrimitive.int)
  }

  @Test
  fun handlePodHealth_reportsReadyAfterInstallation() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)
    val handler = PodHandler(context)

    val result = handler.handlePodHealth(null)

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals(true, payload.getValue("localExecutionAvailable").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("ready").jsonPrimitive.boolean)
    assertEquals("ready", payload.getValue("reason").jsonPrimitive.content)
    assertEquals(3, payload.getValue("verifiedFileCount").jsonPrimitive.int)
    assertEquals("0.1.0", payload.getValue("installedVersions").jsonArray.single().jsonPrimitive.content)
  }

  private fun parsePayload(payloadJson: String?) =
    json.parseToJsonElement(payloadJson ?: error("expected payload")).jsonObject
}
