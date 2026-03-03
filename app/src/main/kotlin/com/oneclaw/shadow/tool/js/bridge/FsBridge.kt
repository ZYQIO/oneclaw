package com.oneclaw.shadow.tool.js.bridge

import android.util.Log
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.define
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Injects file system functions into the QuickJS context.
 * All paths are confined to [allowedRoot] (typically context.filesDir).
 *
 * If [onFileWritten] is provided, it is called fire-and-forget after every
 * successful write or append, enabling RFC-050 git auto-commit.
 */
class FsBridge(
    private val allowedRoot: File,
    private val onFileWritten: (suspend (relativePath: String, message: String) -> Unit)? = null
) {

    private val canonicalRoot: String = allowedRoot.canonicalPath

    companion object {
        private const val TAG = "FsBridge"
        private const val MAX_FILE_SIZE = 1024 * 1024  // 1MB, same as ReadFileTool
    }

    fun inject(quickJs: QuickJs) {
        quickJs.define("fs") {
            // fs.readFile(path) -> string
            function("readFile") { args: Array<Any?> ->
                val path = args.getOrNull(0)?.toString()
                    ?: throw IllegalArgumentException("readFile: path argument required")
                readFile(path)
            }

            // fs.writeFile(path, content) -> void (returns null)
            function("writeFile") { args: Array<Any?> ->
                val path = args.getOrNull(0)?.toString()
                    ?: throw IllegalArgumentException("writeFile: path argument required")
                val content = args.getOrNull(1)?.toString() ?: ""
                writeFile(path, content)
                null
            }

            // fs.exists(path) -> boolean
            function("exists") { args: Array<Any?> ->
                val path = args.getOrNull(0)?.toString()
                    ?: throw IllegalArgumentException("exists: path argument required")
                fileExists(path)
            }

            // fs.appendFile(path, content) -> void (returns null)
            function("appendFile") { args: Array<Any?> ->
                val path = args.getOrNull(0)?.toString()
                    ?: throw IllegalArgumentException("appendFile: path argument required")
                val content = args.getOrNull(1)?.toString() ?: ""
                appendFile(path, content)
                null
            }
        }
    }

    internal fun validatePath(path: String): String {
        val resolved = if (File(path).isAbsolute) {
            File(path)
        } else {
            File(allowedRoot, path)
        }
        val canonicalPath = resolved.canonicalPath
        if (!canonicalPath.startsWith(canonicalRoot + File.separator) && canonicalPath != canonicalRoot) {
            throw SecurityException("Access denied: path is outside app storage")
        }
        return canonicalPath
    }

    internal fun readFile(path: String): String {
        val canonical = validatePath(path)
        val file = File(canonical)

        if (!file.exists()) throw IllegalArgumentException("File not found: $path")
        if (!file.isFile) throw IllegalArgumentException("Path is a directory: $path")
        if (file.length() > MAX_FILE_SIZE) {
            throw IllegalArgumentException(
                "File too large (${file.length()} bytes). Maximum: $MAX_FILE_SIZE bytes."
            )
        }

        return file.readText(Charsets.UTF_8)
    }

    internal fun writeFile(path: String, content: String) {
        val canonical = validatePath(path)
        val file = File(canonical)
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
        fireCommit(canonical, "file: write $path")
    }

    internal fun appendFile(path: String, content: String) {
        val canonical = validatePath(path)
        val file = File(canonical)
        file.parentFile?.mkdirs()
        file.appendText(content, Charsets.UTF_8)
        fireCommit(canonical, "file: append $path")
    }

    internal fun fileExists(path: String): Boolean {
        return try {
            val canonical = validatePath(path)
            File(canonical).exists()
        } catch (e: SecurityException) {
            false  // paths outside allowed root report as non-existent
        }
    }

    private fun fireCommit(canonicalPath: String, message: String) {
        val callback = onFileWritten ?: return
        val relativePath = File(canonicalPath).canonicalPath
            .removePrefix(allowedRoot.canonicalPath)
            .trimStart('/')
        CoroutineScope(Dispatchers.IO).launch {
            try {
                callback(relativePath, message)
            } catch (e: Exception) {
                Log.w(TAG, "Git commit after file write failed: ${e.message}")
            }
        }
    }
}
