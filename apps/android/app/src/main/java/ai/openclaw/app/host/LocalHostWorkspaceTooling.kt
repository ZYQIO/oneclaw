package ai.openclaw.app.host

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

internal class LocalHostWorkspaceToolBridge(
  private val json: Json,
  workspaceRoot: File,
  private val allowWriteRemoteActions: () -> Boolean = { false },
) : LocalHostToolBridge {
  companion object {
    private const val maxListEntries = 200
    private const val maxReadBytes = 128 * 1024
    private const val maxSearchMatches = 200
    private const val maxSearchFileBytes = 256 * 1024
    private const val maxReplaceBytes = 256 * 1024
  }

  private val rootDir = workspaceRoot.canonicalFile

  override fun toolsForRole(role: String): List<LocalHostFunctionTool> {
    val actions = supportedActionsForRole(role)
    return listOf(
      LocalHostFunctionTool(
        name = "workspace",
        description =
          "Manage text files inside OpenClaw's on-device workspace sandbox. Use this for drafts, notes, plans, and intermediate artifacts that should live on the phone itself.",
        parameters = workspaceToolSchema(actions),
      ),
    )
  }

  override suspend fun executeToolCall(
    role: String,
    name: String,
    argumentsJson: String,
  ): LocalHostToolExecutionResult {
    if (name != "workspace") {
      return LocalHostToolExecutionResult(
        outputText = workspaceError("UNKNOWN_TOOL", "UNKNOWN_TOOL: $name"),
      )
    }

    val params = parseArguments(argumentsJson)
      ?: return LocalHostToolExecutionResult(
        outputText = workspaceError("INVALID_REQUEST", "INVALID_REQUEST: workspace arguments must be a JSON object"),
      )
    val action = params["action"].asStringOrNull()?.trim().orEmpty()
    if (!isActionAllowedForRole(role = role, action = action)) {
      return LocalHostToolExecutionResult(
        outputText =
          workspaceError(
            "COMMAND_DISABLED",
            "COMMAND_DISABLED: workspace action $action is not enabled for this session",
          ),
      )
    }
    return try {
      rootDir.mkdirs()
      when (action) {
        "list" -> LocalHostToolExecutionResult(outputText = handleList(params))
        "read" -> LocalHostToolExecutionResult(outputText = handleRead(params))
        "search" -> LocalHostToolExecutionResult(outputText = handleSearch(params))
        "write" -> LocalHostToolExecutionResult(outputText = handleWrite(params, append = false))
        "append" -> LocalHostToolExecutionResult(outputText = handleWrite(params, append = true))
        "mkdir" -> LocalHostToolExecutionResult(outputText = handleMkdir(params))
        "delete" -> LocalHostToolExecutionResult(outputText = handleDelete(params))
        "replace" -> LocalHostToolExecutionResult(outputText = handleReplace(params))
        "move" -> LocalHostToolExecutionResult(outputText = handleMove(params))
        "copy" -> LocalHostToolExecutionResult(outputText = handleCopy(params))
        "stat" -> LocalHostToolExecutionResult(outputText = handleStat(params))
        else ->
          LocalHostToolExecutionResult(
            outputText = workspaceError("INVALID_REQUEST", "INVALID_REQUEST: unsupported workspace action $action"),
          )
      }
    } catch (err: Throwable) {
      LocalHostToolExecutionResult(
        outputText = workspaceError("WORKSPACE_FAILED", err.message ?: "workspace operation failed"),
      )
    }
  }

  private fun supportedActionsForRole(role: String): List<String> {
    val orderedActions = LocalHostCommandPolicy.readOnlyWorkspaceActions + LocalHostCommandPolicy.writeWorkspaceActions
    return orderedActions.filter { action ->
      isActionAllowedForRole(role = role, action = action)
    }
  }

  private fun isActionAllowedForRole(
    role: String,
    action: String,
  ): Boolean {
    return LocalHostCommandPolicy.isWorkspaceActionAllowedForRole(
      role = role,
      action = action,
      allowWrite = allowWriteRemoteActions(),
    )
  }

  private fun workspaceToolSchema(actions: List<String>): JsonObject =
    buildJsonObject {
      put("type", JsonPrimitive("object"))
      put(
        "properties",
        buildJsonObject {
          put(
            "action",
            buildJsonObject {
              put("type", JsonPrimitive("string"))
              put(
                "enum",
                buildJsonArray {
                  actions.forEach {
                    add(JsonPrimitive(it))
                  }
                },
              )
            },
          )
          putStringProperty("path")
          putStringProperty("content")
          putStringProperty("query")
          putBooleanProperty("caseSensitive")
          putStringProperty("oldText")
          putStringProperty("newText")
          putStringProperty("destinationPath")
          putBooleanProperty("recursive")
          putBooleanProperty("replaceAll")
          putBooleanProperty("overwrite")
          putNumberProperty("limit")
        },
      )
      put(
        "required",
        buildJsonArray {
          add(JsonPrimitive("action"))
        },
      )
      put("additionalProperties", JsonPrimitive(false))
    }

  private fun handleList(params: JsonObject): String {
    val target = resolveWorkspacePath(optionalPath(params)) ?: return workspaceError("INVALID_REQUEST", "INVALID_REQUEST: path escapes workspace")
    if (!target.exists()) {
      return workspaceError("NOT_FOUND", "NOT_FOUND: ${displayPath(target)}")
    }
    if (!target.isDirectory) {
      return workspaceError("INVALID_REQUEST", "INVALID_REQUEST: ${displayPath(target)} is not a directory")
    }
    val recursive = readBoolean(params, "recursive") == true
    val limit = (readInt(params, "limit") ?: 50).coerceIn(1, maxListEntries)
    val entries =
      if (recursive) {
        target.walkTopDown().drop(1).take(limit).toList()
      } else {
        target.listFiles()?.sortedBy { it.name.lowercase() }?.take(limit).orEmpty()
      }
    return buildJsonObject {
      put("ok", JsonPrimitive(true))
      put("path", JsonPrimitive(displayPath(target)))
      put("recursive", JsonPrimitive(recursive))
      put("count", JsonPrimitive(entries.size))
      put(
        "entries",
        JsonArray(
          entries.map { entry ->
            buildJsonObject {
              put("path", JsonPrimitive(displayPath(entry)))
              put("name", JsonPrimitive(entry.name))
              put("kind", JsonPrimitive(if (entry.isDirectory) "directory" else "file"))
              if (entry.isFile) {
                put("sizeBytes", JsonPrimitive(entry.length()))
              }
              put("lastModifiedMs", JsonPrimitive(entry.lastModified()))
            }
          },
        ),
      )
    }.toString()
  }

  private fun handleRead(params: JsonObject): String {
    val target = requireWorkspaceFile(params) ?: return workspaceError("INVALID_REQUEST", "INVALID_REQUEST: readable file path required")
    if (!target.exists() || !target.isFile) {
      return workspaceError("NOT_FOUND", "NOT_FOUND: ${displayPath(target)}")
    }
    val bytes = target.readBytes()
    val truncated = bytes.size > maxReadBytes
    val text = bytes.copyOfRange(0, minOf(bytes.size, maxReadBytes)).toString(Charsets.UTF_8)
    return buildJsonObject {
      put("ok", JsonPrimitive(true))
      put("path", JsonPrimitive(displayPath(target)))
      put("sizeBytes", JsonPrimitive(bytes.size))
      put("truncated", JsonPrimitive(truncated))
      put("content", JsonPrimitive(text))
    }.toString()
  }

  private fun handleSearch(params: JsonObject): String {
    val query = params["query"].asStringOrNull()?.takeIf { it.isNotEmpty() }
      ?: return workspaceError("INVALID_REQUEST", "INVALID_REQUEST: query required")
    val target = resolveWorkspacePath(optionalPath(params))
      ?: return workspaceError("INVALID_REQUEST", "INVALID_REQUEST: path escapes workspace")
    if (!target.exists()) {
      return workspaceError("NOT_FOUND", "NOT_FOUND: ${displayPath(target)}")
    }
    val recursive = if (target.isDirectory) readBoolean(params, "recursive") != false else false
    val caseSensitive = readBoolean(params, "caseSensitive") == true
    val limit = (readInt(params, "limit") ?: 50).coerceIn(1, maxSearchMatches)

    val files =
      when {
        target.isFile -> sequenceOf(target)
        recursive -> target.walkTopDown().filter { it.isFile }
        else -> target.listFiles()?.asSequence()?.filter { it.isFile }.orEmpty()
      }

    val matches = mutableListOf<JsonObject>()
    var scannedFiles = 0
    var skippedLargeFiles = 0
    var skippedBinaryFiles = 0
    var truncated = false

    fileLoop@ for (file in files) {
      if (matches.size >= limit) {
        truncated = true
        break
      }
      val bytes =
        try {
          file.readBytes()
        } catch (_: Throwable) {
          skippedBinaryFiles += 1
          continue
        }
      if (bytes.size > maxSearchFileBytes) {
        skippedLargeFiles += 1
        continue
      }
      if (bytes.any { it == 0.toByte() }) {
        skippedBinaryFiles += 1
        continue
      }
      scannedFiles += 1
      val text = bytes.toString(Charsets.UTF_8)
      var lineNumber = 0
      for (line in text.lineSequence()) {
        lineNumber += 1
        var searchStart = 0
        while (matches.size < limit) {
          val matchIndex = line.indexOf(query, startIndex = searchStart, ignoreCase = !caseSensitive)
          if (matchIndex < 0) {
            break
          }
          matches +=
            buildJsonObject {
              put("path", JsonPrimitive(displayPath(file)))
              put("line", JsonPrimitive(lineNumber))
              put("column", JsonPrimitive(matchIndex + 1))
              put("preview", JsonPrimitive(buildSearchPreview(line, matchIndex, query.length)))
            }
          searchStart = matchIndex + query.length
        }
        if (matches.size >= limit) {
          truncated = true
          break@fileLoop
        }
      }
    }

    return buildJsonObject {
      put("ok", JsonPrimitive(true))
      put("path", JsonPrimitive(displayPath(target)))
      put("query", JsonPrimitive(query))
      put("recursive", JsonPrimitive(recursive))
      put("caseSensitive", JsonPrimitive(caseSensitive))
      put("count", JsonPrimitive(matches.size))
      put("truncated", JsonPrimitive(truncated))
      put("scannedFiles", JsonPrimitive(scannedFiles))
      put("skippedLargeFiles", JsonPrimitive(skippedLargeFiles))
      put("skippedBinaryFiles", JsonPrimitive(skippedBinaryFiles))
      put("matches", JsonArray(matches))
    }.toString()
  }

  private fun handleWrite(
    params: JsonObject,
    append: Boolean,
  ): String {
    val target = requireWorkspaceFile(params) ?: return workspaceError("INVALID_REQUEST", "INVALID_REQUEST: file path required")
    val content =
      params["content"].asStringOrNull()
        ?: return workspaceError("INVALID_REQUEST", "INVALID_REQUEST: content required")
    target.parentFile?.mkdirs()
    if (append) {
      target.appendText(content, Charsets.UTF_8)
    } else {
      target.writeText(content, Charsets.UTF_8)
    }
    return buildJsonObject {
      put("ok", JsonPrimitive(true))
      put("path", JsonPrimitive(displayPath(target)))
      put("bytesWritten", JsonPrimitive(content.toByteArray(Charsets.UTF_8).size))
      put("sizeBytes", JsonPrimitive(target.length()))
      put("mode", JsonPrimitive(if (append) "append" else "write"))
    }.toString()
  }

  private fun handleMkdir(params: JsonObject): String {
    val target = resolveWorkspacePath(requiredPath(params, "path")) ?: return workspaceError("INVALID_REQUEST", "INVALID_REQUEST: path escapes workspace")
    val created = target.mkdirs() || target.isDirectory
    return buildJsonObject {
      put("ok", JsonPrimitive(created))
      put("path", JsonPrimitive(displayPath(target)))
      put("created", JsonPrimitive(created))
    }.toString()
  }

  private fun handleDelete(params: JsonObject): String {
    val target = resolveWorkspacePath(requiredPath(params, "path")) ?: return workspaceError("INVALID_REQUEST", "INVALID_REQUEST: path escapes workspace")
    if (target == rootDir) {
      return workspaceError("INVALID_REQUEST", "INVALID_REQUEST: refusing to delete the workspace root")
    }
    if (!target.exists()) {
      return workspaceError("NOT_FOUND", "NOT_FOUND: ${displayPath(target)}")
    }
    val recursive = readBoolean(params, "recursive") == true
    if (target.isDirectory && !recursive && target.listFiles()?.isNotEmpty() == true) {
      return workspaceError("DIRECTORY_NOT_EMPTY", "DIRECTORY_NOT_EMPTY: pass recursive=true to delete ${displayPath(target)}")
    }
    deleteRecursively(target)
    return buildJsonObject {
      put("ok", JsonPrimitive(true))
      put("path", JsonPrimitive(displayPath(target)))
      put("deleted", JsonPrimitive(true))
    }.toString()
  }

  private fun handleReplace(params: JsonObject): String {
    val target = requireWorkspaceFile(params) ?: return workspaceError("INVALID_REQUEST", "INVALID_REQUEST: file path required")
    if (!target.exists() || !target.isFile) {
      return workspaceError("NOT_FOUND", "NOT_FOUND: ${displayPath(target)}")
    }
    if (target.length() > maxReplaceBytes) {
      return workspaceError("TOO_LARGE", "TOO_LARGE: ${displayPath(target)} exceeds replace size limit")
    }
    val oldText = params["oldText"].asStringOrNull()
      ?: return workspaceError("INVALID_REQUEST", "INVALID_REQUEST: oldText required")
    val newText = params["newText"].asStringOrNull()
      ?: return workspaceError("INVALID_REQUEST", "INVALID_REQUEST: newText required")
    if (oldText.isEmpty()) {
      return workspaceError("INVALID_REQUEST", "INVALID_REQUEST: oldText must not be empty")
    }
    if (oldText == newText) {
      return buildJsonObject {
        put("ok", JsonPrimitive(true))
        put("path", JsonPrimitive(displayPath(target)))
        put("changed", JsonPrimitive(false))
        put("replacements", JsonPrimitive(0))
        put("sizeBytes", JsonPrimitive(target.length()))
      }.toString()
    }

    val content = target.readText(Charsets.UTF_8)
    val replaceAll = readBoolean(params, "replaceAll") == true
    val matchCount = countLiteralOccurrences(content = content, needle = oldText)
    if (matchCount == 0) {
      return buildJsonObject {
        put("ok", JsonPrimitive(true))
        put("path", JsonPrimitive(displayPath(target)))
        put("changed", JsonPrimitive(false))
        put("replacements", JsonPrimitive(0))
        put("sizeBytes", JsonPrimitive(target.length()))
      }.toString()
    }

    val updatedContent =
      if (replaceAll) {
        content.replace(oldText, newText)
      } else {
        val firstIndex = content.indexOf(oldText)
        if (firstIndex < 0) {
          content
        } else {
          content.replaceRange(firstIndex, firstIndex + oldText.length, newText)
        }
      }
    target.writeText(updatedContent, Charsets.UTF_8)
    return buildJsonObject {
      put("ok", JsonPrimitive(true))
      put("path", JsonPrimitive(displayPath(target)))
      put("changed", JsonPrimitive(updatedContent != content))
      put("replacements", JsonPrimitive(if (replaceAll) matchCount else 1))
      put("replaceAll", JsonPrimitive(replaceAll))
      put("sizeBytes", JsonPrimitive(target.length()))
    }.toString()
  }

  private fun handleMove(params: JsonObject): String {
    val source = requireWorkspaceEntry(params, "path") ?: return workspaceError("INVALID_REQUEST", "INVALID_REQUEST: source path required")
    val destination =
      requireWorkspaceEntry(params, "destinationPath")
        ?: return workspaceError("INVALID_REQUEST", "INVALID_REQUEST: destinationPath required")
    if (!source.exists()) {
      return workspaceError("NOT_FOUND", "NOT_FOUND: ${displayPath(source)}")
    }
    if (source == destination) {
      return buildJsonObject {
        put("ok", JsonPrimitive(true))
        put("path", JsonPrimitive(displayPath(destination)))
        put("sourcePath", JsonPrimitive(displayPath(source)))
        put("moved", JsonPrimitive(false))
      }.toString()
    }
    prepareDestinationForWrite(
      source = source,
      destination = destination,
      overwrite = readBoolean(params, "overwrite") == true,
    )
    destination.parentFile?.mkdirs()
    val options =
      if (readBoolean(params, "overwrite") == true) {
        arrayOf(StandardCopyOption.REPLACE_EXISTING)
      } else {
        emptyArray()
      }
    Files.move(source.toPath(), destination.toPath(), *options)
    return buildJsonObject {
      put("ok", JsonPrimitive(true))
      put("path", JsonPrimitive(displayPath(destination)))
      put("sourcePath", JsonPrimitive(displayPath(source)))
      put("moved", JsonPrimitive(true))
      put("kind", JsonPrimitive(if (destination.isDirectory) "directory" else "file"))
    }.toString()
  }

  private fun handleCopy(params: JsonObject): String {
    val source = requireWorkspaceEntry(params, "path") ?: return workspaceError("INVALID_REQUEST", "INVALID_REQUEST: source path required")
    val destination =
      requireWorkspaceEntry(params, "destinationPath")
        ?: return workspaceError("INVALID_REQUEST", "INVALID_REQUEST: destinationPath required")
    if (!source.exists()) {
      return workspaceError("NOT_FOUND", "NOT_FOUND: ${displayPath(source)}")
    }
    if (source == destination) {
      return workspaceError("INVALID_REQUEST", "INVALID_REQUEST: source and destination must differ")
    }
    val recursive = readBoolean(params, "recursive") == true
    if (source.isDirectory && !recursive) {
      return workspaceError(
        "INVALID_REQUEST",
        "INVALID_REQUEST: recursive=true is required to copy directory ${displayPath(source)}",
      )
    }
    val overwrite = readBoolean(params, "overwrite") == true
    prepareDestinationForWrite(source = source, destination = destination, overwrite = overwrite)
    val entriesCopied = copyRecursively(source = source, destination = destination)
    return buildJsonObject {
      put("ok", JsonPrimitive(true))
      put("path", JsonPrimitive(displayPath(destination)))
      put("sourcePath", JsonPrimitive(displayPath(source)))
      put("copied", JsonPrimitive(true))
      put("entriesCopied", JsonPrimitive(entriesCopied))
      put("kind", JsonPrimitive(if (destination.isDirectory) "directory" else "file"))
    }.toString()
  }

  private fun handleStat(params: JsonObject): String {
    val target = resolveWorkspacePath(optionalPath(params)) ?: return workspaceError("INVALID_REQUEST", "INVALID_REQUEST: path escapes workspace")
    return buildJsonObject {
      put("ok", JsonPrimitive(true))
      put("path", JsonPrimitive(displayPath(target)))
      put("exists", JsonPrimitive(target.exists()))
      if (target.exists()) {
        put("kind", JsonPrimitive(if (target.isDirectory) "directory" else "file"))
        if (target.isFile) {
          put("sizeBytes", JsonPrimitive(target.length()))
        }
        put("lastModifiedMs", JsonPrimitive(target.lastModified()))
      }
    }.toString()
  }

  private fun requireWorkspaceFile(params: JsonObject): File? {
    return requireWorkspaceEntry(params = params, key = "path")
  }

  private fun requireWorkspaceEntry(
    params: JsonObject,
    key: String,
  ): File? {
    val path = requiredPath(params, key)
    val resolved = resolveWorkspacePath(path) ?: return null
    if (resolved == rootDir) return null
    return resolved
  }

  private fun requiredPath(
    params: JsonObject,
    key: String,
  ): String {
    return params[key].asStringOrNull()?.trim().orEmpty()
  }

  private fun optionalPath(params: JsonObject): String {
    return params["path"].asStringOrNull()?.trim().orEmpty()
  }

  private fun resolveWorkspacePath(rawPath: String): File? {
    val normalized =
      rawPath
        .replace('\\', '/')
        .trim()
        .removePrefix("./")
        .trimStart('/')
    val candidate =
      if (normalized.isEmpty()) {
        rootDir
      } else {
        File(rootDir, normalized)
      }.canonicalFile
    val rootPath = rootDir.path
    return if (candidate.path == rootPath || candidate.path.startsWith("$rootPath${File.separator}")) {
      candidate
    } else {
      null
    }
  }

  private fun displayPath(file: File): String {
    if (file == rootDir) return "."
    return file.relativeTo(rootDir).path.replace(File.separatorChar, '/')
  }

  private fun prepareDestinationForWrite(
    source: File,
    destination: File,
    overwrite: Boolean,
  ) {
    if (source.isDirectory && isNestedWithin(parent = source, child = destination)) {
      throw IllegalStateException("INVALID_REQUEST: destinationPath cannot be inside ${displayPath(source)}")
    }
    if (!destination.exists()) {
      return
    }
    if (!overwrite) {
      throw IllegalStateException("ALREADY_EXISTS: ${displayPath(destination)}")
    }
    if (source.isDirectory != destination.isDirectory) {
      throw IllegalStateException("TYPE_MISMATCH: ${displayPath(destination)} already exists with a different kind")
    }
    deleteRecursively(destination)
  }

  private fun copyRecursively(
    source: File,
    destination: File,
  ): Int {
    return if (source.isDirectory) {
      destination.mkdirs()
      var copiedEntries = 1
      source.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { child ->
        copiedEntries += copyRecursively(child, File(destination, child.name))
      }
      copiedEntries
    } else {
      destination.parentFile?.mkdirs()
      Files.copy(source.toPath(), destination.toPath())
      1
    }
  }

  private fun isNestedWithin(
    parent: File,
    child: File,
  ): Boolean {
    val parentPath = parent.canonicalFile.path
    val childPath = child.canonicalFile.path
    return childPath == parentPath || childPath.startsWith("$parentPath${File.separator}")
  }

  private fun countLiteralOccurrences(
    content: String,
    needle: String,
  ): Int {
    var count = 0
    var searchStart = 0
    while (searchStart <= content.length - needle.length) {
      val index = content.indexOf(needle, searchStart)
      if (index < 0) {
        break
      }
      count += 1
      searchStart = index + needle.length
    }
    return count
  }

  private fun buildSearchPreview(
    line: String,
    matchIndex: Int,
    matchLength: Int,
  ): String {
    val maxPreviewChars = 160
    if (line.length <= maxPreviewChars) {
      return line.trim()
    }
    val start = maxOf(0, matchIndex - 48)
    val end = minOf(line.length, matchIndex + matchLength + 72)
    val prefix = if (start > 0) "..." else ""
    val suffix = if (end < line.length) "..." else ""
    return prefix + line.substring(start, end).trim() + suffix
  }

  private fun deleteRecursively(target: File) {
    if (target.isDirectory) {
      target.listFiles()?.forEach { child ->
        deleteRecursively(child)
      }
    }
    if (!target.delete()) {
      throw IllegalStateException("DELETE_FAILED: ${displayPath(target)}")
    }
  }

  private fun parseArguments(argumentsJson: String): JsonObject? {
    val trimmed = argumentsJson.trim()
    if (trimmed.isEmpty()) {
      return buildJsonObject {}
    }
    return try {
      json.parseToJsonElement(trimmed).asObjectOrNull()
    } catch (_: Throwable) {
      null
    }
  }

  private fun workspaceError(
    code: String,
    message: String,
  ): String =
    buildJsonObject {
      put("ok", JsonPrimitive(false))
      put(
        "error",
        buildJsonObject {
          put("code", JsonPrimitive(code))
          put("message", JsonPrimitive(message))
        },
      )
    }.toString()
}

internal class CompositeLocalHostToolBridge(
  private val delegates: List<LocalHostToolBridge>,
) : LocalHostToolBridge {
  override fun toolsForRole(role: String): List<LocalHostFunctionTool> {
    return delegates.flatMap { it.toolsForRole(role) }
  }

  override suspend fun executeToolCall(
    role: String,
    name: String,
    argumentsJson: String,
  ): LocalHostToolExecutionResult {
    delegates.forEach { delegate ->
      val toolNames = delegate.toolsForRole(role).map { it.name }
      if (name in toolNames) {
        return delegate.executeToolCall(role = role, name = name, argumentsJson = argumentsJson)
      }
    }
    return LocalHostToolExecutionResult(
      outputText =
        buildJsonObject {
          put("ok", JsonPrimitive(false))
          put(
            "error",
            buildJsonObject {
              put("code", JsonPrimitive("UNKNOWN_TOOL"))
              put("message", JsonPrimitive("UNKNOWN_TOOL: $name"))
            },
          )
        }.toString(),
    )
  }
}

private fun JsonObjectBuilder.putStringProperty(name: String) {
  put(name, buildJsonObject { put("type", JsonPrimitive("string")) })
}

private fun JsonObjectBuilder.putNumberProperty(name: String) {
  put(name, buildJsonObject { put("type", JsonPrimitive("number")) })
}

private fun JsonObjectBuilder.putBooleanProperty(name: String) {
  put(name, buildJsonObject { put("type", JsonPrimitive("boolean")) })
}

private fun readBoolean(
  params: JsonObject,
  key: String,
): Boolean? =
  when (params[key].asStringOrNull()?.trim()?.lowercase()) {
    "true" -> true
    "false" -> false
    else -> null
  }

private fun readInt(
  params: JsonObject,
  key: String,
): Int? = params[key].asStringOrNull()?.trim()?.toIntOrNull()

private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.asStringOrNull(): String? =
  when (this) {
    is JsonPrimitive -> content
    else -> null
  }
