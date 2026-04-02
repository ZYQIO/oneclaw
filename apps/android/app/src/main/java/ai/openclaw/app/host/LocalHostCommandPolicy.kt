package ai.openclaw.app.host

import ai.openclaw.app.protocol.OpenClawCalendarCommand
import ai.openclaw.app.protocol.OpenClawCameraCommand
import ai.openclaw.app.protocol.OpenClawCallLogCommand
import ai.openclaw.app.protocol.OpenClawContactsCommand
import ai.openclaw.app.protocol.OpenClawDeviceCommand
import ai.openclaw.app.protocol.OpenClawLocationCommand
import ai.openclaw.app.protocol.OpenClawMotionCommand
import ai.openclaw.app.protocol.OpenClawNotificationsCommand
import ai.openclaw.app.protocol.OpenClawPodCommand
import ai.openclaw.app.protocol.OpenClawPhotosCommand
import ai.openclaw.app.protocol.OpenClawSmsCommand
import ai.openclaw.app.protocol.OpenClawSystemCommand
import ai.openclaw.app.protocol.OpenClawUiCommand

internal object LocalHostCommandPolicy {
  val readOnlyWorkspaceActions: List<String> =
    listOf(
      "list",
      "read",
      "search",
      "stat",
    )

  val writeWorkspaceActions: List<String> =
    listOf(
      "write",
      "append",
      "mkdir",
      "delete",
      "replace",
      "move",
      "copy",
    )

  val baseRemoteCommands: List<String> =
    listOf(
      OpenClawCalendarCommand.Events.rawValue,
      OpenClawCallLogCommand.Search.rawValue,
      OpenClawContactsCommand.Search.rawValue,
      OpenClawDeviceCommand.Health.rawValue,
      OpenClawDeviceCommand.Info.rawValue,
      OpenClawDeviceCommand.Permissions.rawValue,
      OpenClawDeviceCommand.Status.rawValue,
      OpenClawLocationCommand.Get.rawValue,
      OpenClawMotionCommand.Activity.rawValue,
      OpenClawMotionCommand.Pedometer.rawValue,
      OpenClawNotificationsCommand.List.rawValue,
      OpenClawPodCommand.Health.rawValue,
      OpenClawPodCommand.WorkspaceScan.rawValue,
      OpenClawPhotosCommand.Latest.rawValue,
      OpenClawSystemCommand.Notify.rawValue,
      OpenClawUiCommand.State.rawValue,
      OpenClawUiCommand.WaitForText.rawValue,
    )

  val advancedRemoteCommands: List<String> =
    listOf(
      OpenClawCameraCommand.Clip.rawValue,
      OpenClawCameraCommand.List.rawValue,
      OpenClawCameraCommand.Snap.rawValue,
    )

  val writeRemoteCommands: List<String> =
    listOf(
      OpenClawCalendarCommand.Add.rawValue,
      OpenClawContactsCommand.Add.rawValue,
      OpenClawNotificationsCommand.Actions.rawValue,
      OpenClawSmsCommand.Send.rawValue,
      OpenClawUiCommand.LaunchApp.rawValue,
      OpenClawUiCommand.InputText.rawValue,
      OpenClawUiCommand.Tap.rawValue,
      OpenClawUiCommand.Swipe.rawValue,
      OpenClawUiCommand.Back.rawValue,
      OpenClawUiCommand.Home.rawValue,
    )

  fun isRemoteChatRole(role: String): Boolean = role.trim() == "remote-operator"

  fun allowedRemoteCommands(
    allowAdvanced: Boolean,
    allowWrite: Boolean,
  ): Set<String> {
    val commands = LinkedHashSet<String>()
    commands.addAll(baseRemoteCommands)
    if (allowAdvanced) {
      commands.addAll(advancedRemoteCommands)
    }
    if (allowWrite) {
      commands.addAll(writeRemoteCommands)
    }
    return commands
  }

  fun allowedWorkspaceActions(allowWrite: Boolean): Set<String> {
    val actions = LinkedHashSet<String>()
    actions.addAll(readOnlyWorkspaceActions)
    if (allowWrite) {
      actions.addAll(writeWorkspaceActions)
    }
    return actions
  }

  fun isCommandAllowedForRole(
    role: String,
    command: String,
    allowAdvanced: Boolean,
    allowWrite: Boolean,
  ): Boolean {
    if (!isRemoteChatRole(role)) {
      return true
    }
    return command in allowedRemoteCommands(
      allowAdvanced = allowAdvanced,
      allowWrite = allowWrite,
    )
  }

  fun isWorkspaceActionAllowedForRole(
    role: String,
    action: String,
    allowWrite: Boolean,
  ): Boolean {
    if (!isRemoteChatRole(role)) {
      return true
    }
    return action in allowedWorkspaceActions(allowWrite = allowWrite)
  }
}
