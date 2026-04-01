package ai.openclaw.app

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val embeddedRuntimePodManifestAssetPath = "embedded-runtime-pod/manifest.json"
private const val embeddedRuntimePodInstallRoot = "filesDir/openclaw/embedded-runtime-pod"

private val embeddedRuntimePodJson = Json { ignoreUnknownKeys = true }

fun embeddedRuntimePodStatusSnapshot(context: Context): JsonObject {
  val installRoot = context.filesDir.resolve("openclaw/embedded-runtime-pod")
  val installedVersions =
    installRoot
      .listFiles()
      ?.filter { it.isDirectory }
      ?.map { it.name }
      ?.sorted()
      .orEmpty()

  val manifest = loadEmbeddedRuntimePodManifest(context)
  val manifestVersion =
    manifest
      ?.get("version")
      ?.jsonPrimitive
      ?.contentOrNull
      ?.trim()
      ?.takeIf { it.isNotEmpty() }

  val manifestFeatureCount =
    manifest
      ?.get("features")
      ?.jsonArray
      ?.size
      ?: manifest
        ?.get("helpers")
        ?.jsonArray
        ?.size

  val manifestVersionExtracted = manifestVersion != null && installRoot.resolve(manifestVersion).isDirectory

  val reason =
    when {
      manifest == null -> "pod_assets_missing"
      manifestVersion == null -> "manifest_missing_version"
      !installRoot.isDirectory -> "not_extracted"
      !manifestVersionExtracted -> "version_not_extracted"
      else -> "ready"
    }

  return buildJsonObject {
    put("available", JsonPrimitive(manifest != null))
    put("ready", JsonPrimitive(reason == "ready"))
    put("reason", JsonPrimitive(reason))
    put("assetManifestPath", JsonPrimitive(embeddedRuntimePodManifestAssetPath))
    put("assetManifestPresent", JsonPrimitive(manifest != null))
    put("installRoot", JsonPrimitive(embeddedRuntimePodInstallRoot))
    put("installRootExists", JsonPrimitive(installRoot.isDirectory))
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
    manifestFeatureCount?.let { put("manifestFeatureCount", JsonPrimitive(it)) }
    put("manifestVersionExtracted", JsonPrimitive(manifestVersionExtracted))
  }
}

private fun loadEmbeddedRuntimePodManifest(context: Context): JsonObject? {
  return runCatching {
    context.assets
      .open(embeddedRuntimePodManifestAssetPath)
      .bufferedReader()
      .use { reader ->
        embeddedRuntimePodJson.parseToJsonElement(reader.readText()).jsonObject
      }
  }.getOrNull()
}
