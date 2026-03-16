package com.oneclaw.remote.core.file

import android.content.Context
import android.util.Base64
import com.oneclaw.remote.core.model.RemoteFileCommand
import com.oneclaw.remote.core.model.RemoteFileEntry
import com.oneclaw.remote.core.model.RemoteOperationResult
import java.io.File

interface RemoteFileBackend {
    fun list(path: String?): RemoteOperationResult<List<RemoteFileEntry>>
    fun upload(command: RemoteFileCommand): RemoteOperationResult<String>
    fun download(path: String?): RemoteOperationResult<String>
    fun delete(path: String?): RemoteOperationResult<String>
    fun mkdir(path: String?): RemoteOperationResult<String>
}

class AppFileTransferBackend(context: Context) : RemoteFileBackend {
    private val rootDir = File(context.filesDir, "remote-share").apply { mkdirs() }

    override fun list(path: String?): RemoteOperationResult<List<RemoteFileEntry>> {
        val target = resolve(path) ?: return RemoteOperationResult.error("Invalid path.")
        if (!target.exists()) {
            return RemoteOperationResult.error("Path does not exist: ${target.path}")
        }
        val entries = (if (target.isDirectory) target.listFiles()?.toList().orEmpty() else listOf(target))
            .sortedBy { it.name.lowercase() }
            .map {
                RemoteFileEntry(
                    path = relativePath(it),
                    isDirectory = it.isDirectory,
                    sizeBytes = if (it.isDirectory) 0L else it.length(),
                    updatedAt = it.lastModified()
                )
            }
        return RemoteOperationResult.success(entries)
    }

    override fun upload(command: RemoteFileCommand): RemoteOperationResult<String> {
        val targetPath = command.targetPath ?: command.path ?: return RemoteOperationResult.error("targetPath is required")
        val data = command.base64Data ?: return RemoteOperationResult.error("base64Data is required")
        val target = resolve(targetPath) ?: return RemoteOperationResult.error("Invalid target path.")
        target.parentFile?.mkdirs()
        target.writeBytes(Base64.decode(data, Base64.DEFAULT))
        return RemoteOperationResult.success("Uploaded to ${relativePath(target)}")
    }

    override fun download(path: String?): RemoteOperationResult<String> {
        val target = resolve(path) ?: return RemoteOperationResult.error("Invalid path.")
        if (!target.exists() || target.isDirectory) {
            return RemoteOperationResult.error("Download target must be an existing file.")
        }
        return RemoteOperationResult.success(Base64.encodeToString(target.readBytes(), Base64.NO_WRAP))
    }

    override fun delete(path: String?): RemoteOperationResult<String> {
        val target = resolve(path) ?: return RemoteOperationResult.error("Invalid path.")
        if (!target.exists()) {
            return RemoteOperationResult.error("Nothing to delete.")
        }
        target.deleteRecursively()
        return RemoteOperationResult.success("Deleted ${relativePath(target)}")
    }

    override fun mkdir(path: String?): RemoteOperationResult<String> {
        val target = resolve(path) ?: return RemoteOperationResult.error("Invalid path.")
        target.mkdirs()
        return RemoteOperationResult.success("Created directory ${relativePath(target)}")
    }

    private fun resolve(path: String?): File? {
        val clean = path?.trim()?.trimStart('/')?.takeIf { it.isNotEmpty() } ?: ""
        val candidate = File(rootDir, clean).canonicalFile
        val rootPath = rootDir.canonicalPath
        return candidate.takeIf { it.path.startsWith(rootPath) }
    }

    private fun relativePath(file: File): String =
        file.canonicalPath.removePrefix(rootDir.canonicalPath).trimStart('/').ifEmpty { "." }
}
