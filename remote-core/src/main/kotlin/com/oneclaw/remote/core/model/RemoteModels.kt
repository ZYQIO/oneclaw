package com.oneclaw.remote.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class RemoteMode {
    ROOT,
    COMPATIBILITY
}

@Serializable
data class RemoteCapabilities(
    val video: Boolean,
    val touch: Boolean,
    val keyboard: Boolean,
    val fileTransfer: Boolean,
    val unattended: Boolean,
    val agentControl: Boolean
)

@Serializable
data class RemoteDevice(
    val deviceId: String,
    val name: String,
    val mode: RemoteMode,
    val capabilities: RemoteCapabilities,
    val lastSeen: Long,
    val online: Boolean,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0
)

@Serializable
data class RemoteSession(
    val sessionId: String,
    val deviceId: String,
    val controllerId: String,
    val mode: RemoteMode,
    val startedAt: Long,
    val leaseExpiresAt: Long
)

@Serializable
enum class RemoteControlAction {
    TAP,
    SWIPE,
    LONG_PRESS,
    KEY,
    TEXT,
    LAUNCH_APP,
    HOME,
    BACK,
    RECENT,
    WAKE,
    LOCK
}

@Serializable
data class RemoteControlCommand(
    val action: RemoteControlAction,
    val x: Int? = null,
    val y: Int? = null,
    val x2: Int? = null,
    val y2: Int? = null,
    val durationMs: Long? = null,
    val text: String? = null,
    val key: String? = null,
    val packageName: String? = null
)

@Serializable
enum class RemoteFileAction {
    LIST,
    UPLOAD,
    DOWNLOAD,
    DELETE,
    MKDIR
}

@Serializable
data class RemoteFileCommand(
    val action: RemoteFileAction,
    val path: String? = null,
    val targetPath: String? = null,
    val base64Data: String? = null
)

@Serializable
data class RemoteFileEntry(
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val updatedAt: Long
)

@Serializable
data class RemoteSnapshot(
    val mimeType: String = "image/png",
    val base64Data: String,
    val capturedAt: Long
)

data class RemoteOperationResult<out T>(
    val value: T? = null,
    val error: String? = null
) {
    val isSuccess: Boolean get() = error == null

    companion object {
        fun <T> success(value: T): RemoteOperationResult<T> = RemoteOperationResult(value = value)
        fun <T> error(message: String): RemoteOperationResult<T> = RemoteOperationResult(error = message)
    }
}
