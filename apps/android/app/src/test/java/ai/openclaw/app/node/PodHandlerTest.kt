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
  fun handlePodManifestDescribe_prefersInstalledMetadataAfterInstall() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)
    val handler = PodHandler(context)

    val result = handler.handlePodManifestDescribe(null)

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals("pod.manifest.describe", payload.getValue("command").jsonPrimitive.content)
    assertEquals(true, payload.getValue("localExecutionAvailable").jsonPrimitive.boolean)
    assertEquals("installed", payload.getValue("manifestSource").jsonPrimitive.content)
    assertEquals("installed", payload.getValue("layoutSource").jsonPrimitive.content)
    assertEquals(3, payload.getValue("stageCount").jsonPrimitive.int)
    assertEquals(7, payload.getValue("fileCount").jsonPrimitive.int)
    assertEquals(true, payload.getValue("workspaceStageDeclared").jsonPrimitive.boolean)
    assertEquals(true, payload.getValue("workspaceStageInstalled").jsonPrimitive.boolean)
    val stageNames = payload.getValue("stageNames").jsonArray.map { it.jsonPrimitive.content }
    assertTrue("workspace" in stageNames)
    val fileStageCounts = payload.getValue("fileStageCounts").jsonObject
    assertEquals(5, fileStageCounts.getValue("workspace").jsonPrimitive.int)
    val podManifest = payload.getValue("podManifest").jsonObject
    assertEquals("0.2.0", podManifest.getValue("version").jsonPrimitive.content)
    val podLayout = payload.getValue("podLayout").jsonObject
    assertEquals("0.2.0", podLayout.getValue("version").jsonPrimitive.content)
  }

  @Test
  fun handlePodManifestDescribe_fallsBackToBundledMetadataBeforeInstall() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    val handler = PodHandler(context)

    val result = handler.handlePodManifestDescribe(null)

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals(false, payload.getValue("localExecutionAvailable").jsonPrimitive.boolean)
    assertEquals("bundled", payload.getValue("manifestSource").jsonPrimitive.content)
    assertEquals("bundled", payload.getValue("layoutSource").jsonPrimitive.content)
    assertEquals(false, payload.getValue("workspaceStageInstalled").jsonPrimitive.boolean)
    assertEquals(3, payload.getValue("stageCount").jsonPrimitive.int)
    assertEquals(7, payload.getValue("fileCount").jsonPrimitive.int)
  }

  @Test
  fun handlePodRuntimeDescribe_reportsDesktopRuntimeMainlineGapMap() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)
    val handler = PodHandler(context)

    val result = handler.handlePodRuntimeDescribe(null)

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals("pod.runtime.describe", payload.getValue("command").jsonPrimitive.content)
    assertEquals(
      "android-desktop-runtime-mainline-20260403",
      payload.getValue("mainlineBranch").jsonPrimitive.content,
    )
    assertEquals(false, payload.getValue("fullDesktopRuntimeBundled").jsonPrimitive.boolean)
    val domainIds =
      payload.getValue("domains").jsonArray.map { item ->
        item.jsonObject.getValue("id").jsonPrimitive.content
      }
    assertTrue("engine" in domainIds)
    assertTrue("environment" in domainIds)
    assertTrue("browser" in domainIds)
    assertTrue("tools" in domainIds)
    assertTrue("plugins" in domainIds)
    val missingDomains = payload.getValue("missingDomains").jsonArray.map { it.jsonPrimitive.content }
    assertTrue("engine" in missingDomains)
    assertTrue("browser" in missingDomains)
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

  @Test
  fun handlePodWorkspaceRead_returnsPackagedDocumentAfterInstall() {
    val context = RuntimeEnvironment.getApplication()
    context.filesDir.resolve("openclaw/embedded-runtime-pod").deleteRecursively()
    ensureEmbeddedRuntimePodInstalled(context)
    val handler = PodHandler(context)

    val result = handler.handlePodWorkspaceRead("""{"path":"templates/handoff-template.md","maxChars":4096}""")

    assertTrue(result.ok)
    val payload = parsePayload(result.payloadJson)
    assertEquals("pod.workspace.read", payload.getValue("command").jsonPrimitive.content)
    assertEquals(true, payload.getValue("localExecutionAvailable").jsonPrimitive.boolean)
    assertEquals("templates/handoff-template.md", payload.getValue("relativePath").jsonPrimitive.content)
    assertEquals(false, payload.getValue("textTruncated").jsonPrimitive.boolean)
    assertTrue(payload.getValue("text").jsonPrimitive.content.contains("# Embedded Runtime Handoff"))
    val document = payload.getValue("document").jsonObject
    assertEquals("template", document.getValue("kind").jsonPrimitive.content)
  }

  @Test
  fun handlePodWorkspaceRead_requiresPath() {
    val context = RuntimeEnvironment.getApplication()
    val handler = PodHandler(context)

    val result = handler.handlePodWorkspaceRead("""{"maxChars":256}""")

    assertFalse(result.ok)
    assertEquals("INVALID_REQUEST", result.error?.code)
  }

  private fun parsePayload(payloadJson: String?) =
    json.parseToJsonElement(payloadJson ?: error("expected payload")).jsonObject
}
