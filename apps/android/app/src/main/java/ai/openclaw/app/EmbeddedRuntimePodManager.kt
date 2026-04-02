package ai.openclaw.app

import android.content.Context
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

private const val embeddedRuntimePodManifestAssetPath = "embedded-runtime-pod/manifest.json"
private const val embeddedRuntimePodLayoutAssetPath = "embedded-runtime-pod/layout.json"
private const val embeddedRuntimePodInstallRootDisplayPath = "filesDir/openclaw/embedded-runtime-pod"

private val embeddedRuntimePodJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class EmbeddedRuntimePodAssetManifestStage(
  val name: String,
  val destination: String,
  val assetPath: String? = null,
)

@Serializable
private data class EmbeddedRuntimePodAssetManifestFile(
  val stage: String,
  val relativePath: String,
  val assetPath: String,
  val sizeBytes: Long,
  val sha256: String,
)

@Serializable
private data class EmbeddedRuntimePodAssetManifest(
  val schemaVersion: Int = 1,
  val podId: String,
  val podName: String? = null,
  val version: String,
  val description: String? = null,
  val assetManifestPath: String = embeddedRuntimePodManifestAssetPath,
  val assetLayoutPath: String = embeddedRuntimePodLayoutAssetPath,
  val assetBasePath: String? = null,
  val stageCount: Int? = null,
  val fileCount: Int? = null,
  val stages: List<EmbeddedRuntimePodAssetManifestStage> = emptyList(),
  val files: List<EmbeddedRuntimePodAssetManifestFile> = emptyList(),
)

data class EmbeddedRuntimePodInspection(
  val available: Boolean,
  val ready: Boolean,
  val reason: String,
  val assetManifestPath: String = embeddedRuntimePodManifestAssetPath,
  val assetLayoutPath: String = embeddedRuntimePodLayoutAssetPath,
  val assetManifestPresent: Boolean = false,
  val installRootExists: Boolean = false,
  val installedVersions: List<String> = emptyList(),
  val manifestVersion: String? = null,
  val manifestStageCount: Int? = null,
  val manifestFileCount: Int? = null,
  val manifestVersionExtracted: Boolean = false,
  val verifiedFileCount: Int = 0,
) {
  fun toJson(): JsonObject {
    return buildJsonObject {
      put("available", JsonPrimitive(available))
      put("ready", JsonPrimitive(ready))
      put("reason", JsonPrimitive(reason))
      put("assetManifestPath", JsonPrimitive(assetManifestPath))
      put("assetLayoutPath", JsonPrimitive(assetLayoutPath))
      put("assetManifestPresent", JsonPrimitive(assetManifestPresent))
      put("installRoot", JsonPrimitive(embeddedRuntimePodInstallRootDisplayPath))
      put("installRootExists", JsonPrimitive(installRootExists))
      put("installedVersionCount", JsonPrimitive(installedVersions.size))
      put(
        "installedVersions",
        buildJsonArray {
          installedVersions.forEach { version ->
            add(JsonPrimitive(version))
          }
        },
      )
      manifestVersion?.let { put("manifestVersion", JsonPrimitive(it)) }
      manifestStageCount?.let { put("manifestStageCount", JsonPrimitive(it)) }
      manifestFileCount?.let { put("manifestFileCount", JsonPrimitive(it)) }
      put("manifestVersionExtracted", JsonPrimitive(manifestVersionExtracted))
      put("verifiedFileCount", JsonPrimitive(verifiedFileCount))
    }
  }
}

fun ensureEmbeddedRuntimePodInstalled(context: Context): EmbeddedRuntimePodInspection {
  val manifest = loadEmbeddedRuntimePodManifest(context) ?: return inspectEmbeddedRuntimePod(context)
  val version = manifest.version.trim()
  if (version.isEmpty()) return inspectEmbeddedRuntimePod(context)
  val versionDir = embeddedRuntimePodInstallRoot(context).resolve(version)
  val existing = inspectEmbeddedRuntimePod(context)
  if (existing.ready) return existing

  versionDir.deleteRecursively()
  versionDir.mkdirs()

  copyAssetIntoFile(context, manifest.assetManifestPath, versionDir.resolve("manifest.json"))
  copyAssetIntoFile(context, manifest.assetLayoutPath, versionDir.resolve("layout.json"))

  manifest.files.forEach { entry ->
    val target = resolvePodRelativePath(versionDir, entry.relativePath) ?: return inspectEmbeddedRuntimePod(context)
    copyAssetIntoFile(context, entry.assetPath, target)
    target.setReadOnly()
  }

  return inspectEmbeddedRuntimePod(context)
}

fun inspectEmbeddedRuntimePod(context: Context): EmbeddedRuntimePodInspection {
  val installRoot = embeddedRuntimePodInstallRoot(context)
  val installedVersions =
    installRoot
      .listFiles()
      ?.filter { it.isDirectory }
      ?.map { it.name }
      ?.sorted()
      .orEmpty()

  val manifest = loadEmbeddedRuntimePodManifest(context)
  if (manifest == null) {
    return EmbeddedRuntimePodInspection(
      available = false,
      ready = false,
      reason = "pod_assets_missing",
      assetManifestPresent = false,
      installRootExists = installRoot.isDirectory,
      installedVersions = installedVersions,
    )
  }

  val manifestVersion = manifest.version.trim().takeIf { it.isNotEmpty() }
  val versionDir = manifestVersion?.let { embeddedRuntimePodInstallRoot(context).resolve(it) }
  val manifestVersionExtracted = versionDir?.isDirectory == true
  val verifiedFileCount =
    manifest.files.count { entry ->
      val target = versionDir?.let { resolvePodRelativePath(it, entry.relativePath) } ?: return@count false
      target.isFile && sha256(target) == entry.sha256
    }

  val ready = manifestVersion != null && manifestVersionExtracted && verifiedFileCount == manifest.files.size
  val reason =
    when {
      manifestVersion == null -> "manifest_missing_version"
      !installRoot.isDirectory -> "not_extracted"
      !manifestVersionExtracted -> "version_not_extracted"
      verifiedFileCount != manifest.files.size -> "verification_incomplete"
      else -> "ready"
    }

  return EmbeddedRuntimePodInspection(
    available = true,
    ready = ready,
    reason = reason,
    assetManifestPresent = true,
    installRootExists = installRoot.isDirectory,
    installedVersions = installedVersions,
    manifestVersion = manifestVersion,
    manifestStageCount = manifest.stageCount ?: manifest.stages.size,
    manifestFileCount = manifest.fileCount ?: manifest.files.size,
    manifestVersionExtracted = manifestVersionExtracted,
    verifiedFileCount = verifiedFileCount,
  )
}

private fun embeddedRuntimePodInstallRoot(context: Context): File =
  context.filesDir.resolve("openclaw/embedded-runtime-pod")

private fun loadEmbeddedRuntimePodManifest(context: Context): EmbeddedRuntimePodAssetManifest? {
  return runCatching {
    context.assets
      .open(embeddedRuntimePodManifestAssetPath)
      .bufferedReader()
      .use { reader ->
        embeddedRuntimePodJson.decodeFromString(EmbeddedRuntimePodAssetManifest.serializer(), reader.readText())
      }
  }.getOrNull()
}

private fun copyAssetIntoFile(context: Context, assetPath: String, target: File) {
  target.parentFile?.mkdirs()
  context.assets.open(assetPath).use { input ->
    target.outputStream().use { output ->
      input.copyTo(output)
    }
  }
}

private fun resolvePodRelativePath(versionDir: File, relativePath: String): File? {
  val normalized = relativePath.replace('\\', '/')
  if (normalized.isBlank()) return null
  if (normalized.startsWith("/") || normalized.contains("../")) return null
  return versionDir.resolve(normalized)
}

private fun sha256(file: File): String {
  val digest = MessageDigest.getInstance("SHA-256")
  file.inputStream().use { input ->
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
      val read = input.read(buffer)
      if (read <= 0) break
      digest.update(buffer, 0, read)
    }
  }
  return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}
