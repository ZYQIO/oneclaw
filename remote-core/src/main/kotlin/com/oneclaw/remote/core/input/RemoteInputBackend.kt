package com.oneclaw.remote.core.input

import com.oneclaw.remote.core.model.RemoteControlAction
import com.oneclaw.remote.core.model.RemoteControlCommand
import com.oneclaw.remote.core.model.RemoteOperationResult
import com.oneclaw.remote.core.util.ShellCommandRunner

interface RemoteInputBackend {
    fun isAvailable(): Boolean
    fun connect(): RemoteOperationResult<Unit>
    fun inject(command: RemoteControlCommand): RemoteOperationResult<String>
    fun disconnect()
}

class PrivilegedInputInjector(
    private val shellCommandRunner: ShellCommandRunner = ShellCommandRunner()
) : RemoteInputBackend {

    override fun isAvailable(): Boolean = shellCommandRunner.run("id", privileged = true).exitCode == 0

    override fun connect(): RemoteOperationResult<Unit> =
        if (isAvailable()) RemoteOperationResult.success(Unit)
        else RemoteOperationResult.error("Root shell is not available.")

    override fun inject(command: RemoteControlCommand): RemoteOperationResult<String> {
        val shellCommand = when (command.action) {
            RemoteControlAction.TAP -> "input tap ${command.x ?: return RemoteOperationResult.error("x is required")} ${command.y ?: return RemoteOperationResult.error("y is required")}"
            RemoteControlAction.SWIPE -> "input swipe ${command.x ?: return RemoteOperationResult.error("x is required")} ${command.y ?: return RemoteOperationResult.error("y is required")} ${command.x2 ?: return RemoteOperationResult.error("x2 is required")} ${command.y2 ?: return RemoteOperationResult.error("y2 is required")} ${command.durationMs ?: 250L}"
            RemoteControlAction.LONG_PRESS -> {
                val x = command.x ?: return RemoteOperationResult.error("x is required")
                val y = command.y ?: return RemoteOperationResult.error("y is required")
                "input swipe $x $y $x $y ${command.durationMs ?: 800L}"
            }
            RemoteControlAction.KEY -> "input keyevent ${command.key ?: return RemoteOperationResult.error("key is required")}"
            RemoteControlAction.TEXT -> "input text ${escapeText(command.text ?: return RemoteOperationResult.error("text is required"))}"
            RemoteControlAction.LAUNCH_APP -> "monkey -p ${command.packageName ?: return RemoteOperationResult.error("packageName is required")} -c android.intent.category.LAUNCHER 1"
            RemoteControlAction.HOME -> "input keyevent KEYCODE_HOME"
            RemoteControlAction.BACK -> "input keyevent KEYCODE_BACK"
            RemoteControlAction.RECENT -> "input keyevent KEYCODE_APP_SWITCH"
            RemoteControlAction.WAKE -> "input keyevent KEYCODE_WAKEUP"
            RemoteControlAction.LOCK -> "input keyevent KEYCODE_POWER"
        }

        val result = shellCommandRunner.run(shellCommand, privileged = true)
        return if (result.exitCode == 0) {
            RemoteOperationResult.success("Executed ${command.action.name.lowercase()}")
        } else {
            RemoteOperationResult.error(result.stderr.ifBlank { "Command failed with code ${result.exitCode}" })
        }
    }

    override fun disconnect() = Unit

    private fun escapeText(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                ' ' -> append("%s")
                '&', '|', ';', '<', '>', '"', '\'', '\\', '(', ')', '$', '`' -> append("\\").append(char)
                else -> append(char)
            }
        }
    }
}

class AccessibilityInputInjector : RemoteInputBackend {
    override fun isAvailable(): Boolean = false

    override fun connect(): RemoteOperationResult<Unit> =
        RemoteOperationResult.error("Accessibility automation is not wired yet.")

    override fun inject(command: RemoteControlCommand): RemoteOperationResult<String> =
        RemoteOperationResult.error("Compatibility input backend is not implemented yet.")

    override fun disconnect() = Unit
}
