package ai.openclaw.app.node

import ai.openclaw.app.ensureEmbeddedRuntimePodInstalled
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    assertEquals(7, payload.getValue("verifiedFileCount").jsonPrimitive.int)
    assertEquals("0.2.0", payload.getValue("installedVersions").jsonArray.single().jsonPrimitive.content)
  }

  @Test
  fun handlePodWorkspaceScan_reportsWorkspaceAssetsAfterInstall() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)
    val handler = PodHandler(context)

    val result = handler.handlePodWorkspaceScan("""{"limit":2,"query":"handoff"}""")

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals("pod.workspace.scan", payload.getValue("command").jsonPrimitive.content)
    assertEquals(true, payload.getValue("localExecutionAvailable").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("workspaceStagePresent").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("stageManifestPresent").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("contentIndexPresent").jsonPrimitive.boolean)
    assertEquals(1, payload.getValue("matchedFileCount").jsonPrimitive.int)
    assertEquals(1, payload.getValue("returnedFileCount").jsonPrimitive.int)
    assertEquals(false, payload.getValue("truncated").jsonPrimitive.boolean)
    val files = payload.getValue("files").jsonArray
    assertEquals(1, files.size)
    assertEquals(
      "templates/handoff-template.md",
      files.single().jsonObject.getValue("relativePath").jsonPrimitive.content,
    )
    assertTrue(files.single().jsonObject.getValue("textPreview").jsonPrimitive.content.contains("pod.workspace.scan"))
    val stageManifest = payload.getValue("stageManifest").jsonObject
    assertEquals("workspace", stageManifest.getValue("stage").jsonPrimitive.content)
    val contentIndex = payload.getValue("contentIndex").jsonObject
    assertEquals(2, contentIndex.getValue("documents").jsonArray.size)
  }

  @Test
  fun handlePodWorkspaceScan_returnsEmptyInventoryBeforeInstall() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    val handler = PodHandler(context)

    val result = handler.handlePodWorkspaceScan("""{"limit":1}""")

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals(false, payload.getValue("localExecutionAvailable").jsonPrimitive.boolean)
    assertEquals(false, payload.getValue("workspaceStagePresent").jsonPrimitive.boolean)
    assertEquals(0, payload.getValue("matchedFileCount").jsonPrimitive.int)
    assertEquals(0, payload.getValue("returnedFileCount").jsonPrimitive.int)
    assertFalse(payload.containsKey("stageManifest"))
    assertFalse(payload.containsKey("contentIndex"))
  }

  private fun parsePayload(payloadJson: String?) =
    json.parseToJsonElement(payloadJson ?: error("expected payload")).jsonObject
}
