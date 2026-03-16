package com.oneclaw.shadow.tool.builtin.remote

import android.content.Context
import android.util.Base64
import com.oneclaw.remote.core.model.RemoteControlAction
import com.oneclaw.remote.core.model.RemoteControlCommand
import com.oneclaw.shadow.core.repository.RemoteControllerGateway
import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.tool.engine.Tool
import java.io.File

private fun Any?.asString(): String? = this as? String
private fun Any?.asInt(): Int? = when (this) {
    is Number -> toInt()
    is String -> toIntOrNull()
    else -> null
}

private fun Any?.asLong(): Long? = when (this) {
    is Number -> toLong()
    is String -> toLongOrNull()
    else -> null
}

private abstract class BaseRemoteTool(
    internal val remoteControllerGateway: RemoteControllerGateway
) : Tool {
    protected suspend fun ensureConnected(): ToolResult? = when (val result = remoteControllerGateway.connect()) {
        is AppResult.Success -> null
        is AppResult.Error -> ToolResult.error("network_error", result.message)
    }

    protected fun appResultToToolResult(result: AppResult<*>): ToolResult = when (result) {
        is AppResult.Success -> ToolResult.success(result.data.toString())
        is AppResult.Error -> ToolResult.error(result.code.name.lowercase(), result.message)
    }
}

class RemoteListDevicesTool(
    remoteControllerGateway: RemoteControllerGateway
) : BaseRemoteTool(remoteControllerGateway) {
    override val definition = ToolDefinition(
        name = "remote_list_devices",
        description = "List remote Android devices that are currently online through the broker.",
        parametersSchema = ToolParametersSchema(emptyMap()),
        requiredPermissions = emptyList(),
        timeoutSeconds = 15
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        ensureConnected()?.let { return it }
        return when (val result = remoteControllerGateway.refreshDevices()) {
            is AppResult.Success -> {
                if (result.data.isEmpty()) {
                    ToolResult.success("No remote devices are currently online.")
                } else {
                    val lines = result.data.joinToString("\n") { device ->
                        "- ${device.deviceId}: ${device.name} (${device.mode.name.lowercase()}) " +
                            "video=${device.capabilities.video} touch=${device.capabilities.touch} file=${device.capabilities.fileTransfer}"
                    }
                    ToolResult.success(lines)
                }
            }
            is AppResult.Error -> ToolResult.error(result.code.name.lowercase(), result.message)
        }
    }
}

class RemoteOpenSessionTool(
    remoteControllerGateway: RemoteControllerGateway
) : BaseRemoteTool(remoteControllerGateway) {
    override val definition = ToolDefinition(
        name = "remote_open_session",
        description = "Open a control session for a specific remote device.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "device_id" to ToolParameter("string", "Target remote device ID")
            ),
            required = listOf("device_id")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 15
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        ensureConnected()?.let { return it }
        val deviceId = parameters["device_id"].asString()
            ?: return ToolResult.error("validation_error", "device_id is required")
        return when (val result = remoteControllerGateway.openSession(deviceId, source = "agent")) {
            is AppResult.Success -> ToolResult.success(
                "Opened session ${result.data.sessionId} for ${result.data.deviceId}, lease expires at ${result.data.leaseExpiresAt}."
            )
            is AppResult.Error -> ToolResult.error(result.code.name.lowercase(), result.message)
        }
    }
}

class RemoteSnapshotTool(
    private val context: Context,
    remoteControllerGateway: RemoteControllerGateway
) : BaseRemoteTool(remoteControllerGateway) {
    override val definition = ToolDefinition(
        name = "remote_snapshot",
        description = "Capture a screenshot from an open remote session and save it to a local cache file.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "device_id" to ToolParameter("string", "Target remote device ID")
            ),
            required = listOf("device_id")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 25
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val deviceId = parameters["device_id"].asString()
            ?: return ToolResult.error("validation_error", "device_id is required")
        ensureConnected()?.let { return it }
        return when (val result = remoteControllerGateway.requestSnapshot(deviceId)) {
            is AppResult.Success -> {
                val snapshotDir = File(context.cacheDir, "remote-snapshots").apply { mkdirs() }
                val target = File(snapshotDir, "$deviceId-${System.currentTimeMillis()}.png")
                target.writeBytes(Base64.decode(result.data.base64Data, Base64.DEFAULT))
                ToolResult.success("""{"image_path": "${target.absolutePath}"}""")
            }
            is AppResult.Error -> ToolResult.error(result.code.name.lowercase(), result.message)
        }
    }
}

class RemoteTapTool(
    remoteControllerGateway: RemoteControllerGateway
) : BaseRemoteTool(remoteControllerGateway) {
    override val definition = ToolDefinition(
        name = "remote_tap",
        description = "Tap a point on the remote Android device screen.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "device_id" to ToolParameter("string", "Target remote device ID"),
                "x" to ToolParameter("integer", "Tap X coordinate"),
                "y" to ToolParameter("integer", "Tap Y coordinate")
            ),
            required = listOf("device_id", "x", "y")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 15
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult =
        sendCommand(
            parameters = parameters,
            builder = {
                RemoteControlCommand(
                    action = RemoteControlAction.TAP,
                    x = parameters["x"].asInt(),
                    y = parameters["y"].asInt()
                )
            }
        )
}

class RemoteSwipeTool(
    remoteControllerGateway: RemoteControllerGateway
) : BaseRemoteTool(remoteControllerGateway) {
    override val definition = ToolDefinition(
        name = "remote_swipe",
        description = "Swipe between two points on the remote Android device screen.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "device_id" to ToolParameter("string", "Target remote device ID"),
                "x1" to ToolParameter("integer", "Start X"),
                "y1" to ToolParameter("integer", "Start Y"),
                "x2" to ToolParameter("integer", "End X"),
                "y2" to ToolParameter("integer", "End Y"),
                "duration_ms" to ToolParameter("integer", "Swipe duration in milliseconds", default = 250)
            ),
            required = listOf("device_id", "x1", "y1", "x2", "y2")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 15
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult =
        sendCommand(
            parameters = parameters,
            builder = {
                RemoteControlCommand(
                    action = RemoteControlAction.SWIPE,
                    x = parameters["x1"].asInt(),
                    y = parameters["y1"].asInt(),
                    x2 = parameters["x2"].asInt(),
                    y2 = parameters["y2"].asInt(),
                    durationMs = parameters["duration_ms"].asLong() ?: 250L
                )
            }
        )
}

class RemoteInputTextTool(
    remoteControllerGateway: RemoteControllerGateway
) : BaseRemoteTool(remoteControllerGateway) {
    override val definition = ToolDefinition(
        name = "remote_input_text",
        description = "Send text input to the remote Android device.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "device_id" to ToolParameter("string", "Target remote device ID"),
                "text" to ToolParameter("string", "Text to input")
            ),
            required = listOf("device_id", "text")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 15
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult =
        sendCommand(
            parameters = parameters,
            builder = {
                RemoteControlCommand(
                    action = RemoteControlAction.TEXT,
                    text = parameters["text"].asString()
                )
            }
        )
}

class RemotePressKeyTool(
    remoteControllerGateway: RemoteControllerGateway
) : BaseRemoteTool(remoteControllerGateway) {
    override val definition = ToolDefinition(
        name = "remote_press_key",
        description = "Send a key event such as KEYCODE_ENTER or KEYCODE_BACK to the remote device.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "device_id" to ToolParameter("string", "Target remote device ID"),
                "key" to ToolParameter("string", "Android key event code")
            ),
            required = listOf("device_id", "key")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 15
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult =
        sendCommand(
            parameters = parameters,
            builder = {
                RemoteControlCommand(
                    action = RemoteControlAction.KEY,
                    key = parameters["key"].asString()
                )
            }
        )
}

class RemoteLaunchAppTool(
    remoteControllerGateway: RemoteControllerGateway
) : BaseRemoteTool(remoteControllerGateway) {
    override val definition = ToolDefinition(
        name = "remote_launch_app",
        description = "Launch an installed Android application on the remote device.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "device_id" to ToolParameter("string", "Target remote device ID"),
                "package_name" to ToolParameter("string", "Android package name")
            ),
            required = listOf("device_id", "package_name")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 15
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult =
        sendCommand(
            parameters = parameters,
            builder = {
                RemoteControlCommand(
                    action = RemoteControlAction.LAUNCH_APP,
                    packageName = parameters["package_name"].asString()
                )
            }
        )
}

class RemotePushFileTool(
    remoteControllerGateway: RemoteControllerGateway
) : BaseRemoteTool(remoteControllerGateway) {
    override val definition = ToolDefinition(
        name = "remote_push_file",
        description = "Upload a local file from OneClaw storage to the remote host app share directory.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "device_id" to ToolParameter("string", "Target remote device ID"),
                "local_path" to ToolParameter("string", "Local source file path"),
                "remote_path" to ToolParameter("string", "Remote destination path")
            ),
            required = listOf("device_id", "local_path", "remote_path")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 30
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        ensureConnected()?.let { return it }
        val deviceId = parameters["device_id"].asString()
            ?: return ToolResult.error("validation_error", "device_id is required")
        val localPath = parameters["local_path"].asString()
            ?: return ToolResult.error("validation_error", "local_path is required")
        val remotePath = parameters["remote_path"].asString()
            ?: return ToolResult.error("validation_error", "remote_path is required")
        return appResultToToolResult(remoteControllerGateway.pushFile(deviceId, localPath, remotePath))
    }
}

class RemotePullFileTool(
    remoteControllerGateway: RemoteControllerGateway
) : BaseRemoteTool(remoteControllerGateway) {
    override val definition = ToolDefinition(
        name = "remote_pull_file",
        description = "Download a file from the remote host app share directory to local storage.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "device_id" to ToolParameter("string", "Target remote device ID"),
                "remote_path" to ToolParameter("string", "Remote source path"),
                "local_path" to ToolParameter("string", "Local destination path")
            ),
            required = listOf("device_id", "remote_path", "local_path")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 30
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        ensureConnected()?.let { return it }
        val deviceId = parameters["device_id"].asString()
            ?: return ToolResult.error("validation_error", "device_id is required")
        val remotePath = parameters["remote_path"].asString()
            ?: return ToolResult.error("validation_error", "remote_path is required")
        val localPath = parameters["local_path"].asString()
            ?: return ToolResult.error("validation_error", "local_path is required")
        return appResultToToolResult(remoteControllerGateway.pullFile(deviceId, remotePath, localPath))
    }
}

class RemoteCloseSessionTool(
    remoteControllerGateway: RemoteControllerGateway
) : BaseRemoteTool(remoteControllerGateway) {
    override val definition = ToolDefinition(
        name = "remote_close_session",
        description = "Close an active remote control session.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "device_id" to ToolParameter("string", "Target remote device ID")
            ),
            required = listOf("device_id")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 15
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        ensureConnected()?.let { return it }
        val deviceId = parameters["device_id"].asString()
            ?: return ToolResult.error("validation_error", "device_id is required")
        return appResultToToolResult(remoteControllerGateway.closeSession(deviceId))
    }
}

private suspend fun BaseRemoteTool.sendCommand(
    parameters: Map<String, Any?>,
    builder: () -> RemoteControlCommand
): ToolResult {
    ensureConnected()?.let { return it }
    val deviceId = parameters["device_id"].asString()
        ?: return ToolResult.error("validation_error", "device_id is required")
    val command = builder()
    return when (val result = remoteControllerGateway.sendControl(deviceId, command, source = "agent")) {
        is AppResult.Success -> ToolResult.success(result.data)
        is AppResult.Error -> ToolResult.error(result.code.name.lowercase(), result.message)
    }
}
