package ai.openclaw.app.host

import ai.openclaw.app.gateway.GatewaySession
import ai.openclaw.app.protocol.OpenClawCalendarCommand
import ai.openclaw.app.protocol.OpenClawCameraCommand
import ai.openclaw.app.protocol.OpenClawCallLogCommand
import ai.openclaw.app.protocol.OpenClawContactsCommand
import ai.openclaw.app.protocol.OpenClawDeviceCommand
import ai.openclaw.app.protocol.OpenClawLocationCommand
import ai.openclaw.app.protocol.OpenClawMotionCommand
import ai.openclaw.app.protocol.OpenClawNotificationsCommand
import ai.openclaw.app.protocol.OpenClawPhotosCommand
import ai.openclaw.app.protocol.OpenClawSmsCommand
import ai.openclaw.app.protocol.OpenClawSystemCommand
import ai.openclaw.app.protocol.OpenClawUiCommand
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

data class LocalHostFunctionTool(
  val name: String,
  val description: String,
  val parameters: JsonObject,
) {
  fun asJsonObject(): JsonObject =
    buildJsonObject {
      put("type", JsonPrimitive("function"))
      put("name", JsonPrimitive(name))
      put("description", JsonPrimitive(description))
      put("parameters", parameters)
    }
}

data class LocalHostToolExecutionResult(
  val outputText: String,
  val imageInputs: List<LocalHostToolImageInput> = emptyList(),
)

data class LocalHostToolCallEvent(
  val toolCallId: String,
  val name: String,
  val args: JsonObject?,
  val phase: String,
)

data class LocalHostToolImageInput(
  val mimeType: String,
  val base64: String,
)

interface LocalHostToolBridge {
  fun toolsForRole(role: String): List<LocalHostFunctionTool>

  suspend fun executeToolCall(
    role: String,
    name: String,
    argumentsJson: String,
  ): LocalHostToolExecutionResult
}

internal class LocalHostNodesToolBridge(
  private val json: Json,
  private val invoke: suspend (command: String, paramsJson: String?) -> GatewaySession.InvokeResult,
  private val allowAdvancedRemoteCommands: () -> Boolean = { false },
  private val allowWriteRemoteCommands: () -> Boolean = { false },
) : LocalHostToolBridge {
  private data class NodesActionSpec(
    val name: String,
    val command: String,
  )

  private val allActions: List<NodesActionSpec> =
    listOf(
      NodesActionSpec(name = "notify", command = OpenClawSystemCommand.Notify.rawValue),
      NodesActionSpec(name = "camera_list", command = OpenClawCameraCommand.List.rawValue),
      NodesActionSpec(name = "camera_snap", command = OpenClawCameraCommand.Snap.rawValue),
      NodesActionSpec(name = "location_get", command = OpenClawLocationCommand.Get.rawValue),
      NodesActionSpec(name = "notifications_list", command = OpenClawNotificationsCommand.List.rawValue),
      NodesActionSpec(name = "notifications_action", command = OpenClawNotificationsCommand.Actions.rawValue),
      NodesActionSpec(name = "device_status", command = OpenClawDeviceCommand.Status.rawValue),
      NodesActionSpec(name = "device_info", command = OpenClawDeviceCommand.Info.rawValue),
      NodesActionSpec(name = "device_permissions", command = OpenClawDeviceCommand.Permissions.rawValue),
      NodesActionSpec(name = "device_health", command = OpenClawDeviceCommand.Health.rawValue),
      NodesActionSpec(name = "ui_state", command = OpenClawUiCommand.State.rawValue),
      NodesActionSpec(name = "ui_wait_for_text", command = OpenClawUiCommand.WaitForText.rawValue),
      NodesActionSpec(name = "ui_tap", command = OpenClawUiCommand.Tap.rawValue),
      NodesActionSpec(name = "ui_back", command = OpenClawUiCommand.Back.rawValue),
      NodesActionSpec(name = "ui_home", command = OpenClawUiCommand.Home.rawValue),
      NodesActionSpec(name = "contacts_search", command = OpenClawContactsCommand.Search.rawValue),
      NodesActionSpec(name = "contacts_add", command = OpenClawContactsCommand.Add.rawValue),
      NodesActionSpec(name = "calendar_events", command = OpenClawCalendarCommand.Events.rawValue),
      NodesActionSpec(name = "calendar_add", command = OpenClawCalendarCommand.Add.rawValue),
      NodesActionSpec(name = "motion_activity", command = OpenClawMotionCommand.Activity.rawValue),
      NodesActionSpec(name = "motion_pedometer", command = OpenClawMotionCommand.Pedometer.rawValue),
      NodesActionSpec(name = "photos_latest", command = OpenClawPhotosCommand.Latest.rawValue),
      NodesActionSpec(name = "sms_send", command = OpenClawSmsCommand.Send.rawValue),
      NodesActionSpec(name = "calllog_search", command = OpenClawCallLogCommand.Search.rawValue),
    )

  private val actionsByName = allActions.associateBy { it.name }

  override fun toolsForRole(role: String): List<LocalHostFunctionTool> {
    val allowedActions = supportedActionsForRole(role)
    if (allowedActions.isEmpty()) {
      return emptyList()
    }
    return listOf(
      LocalHostFunctionTool(
        name = "nodes",
        description =
          "Control Android device features on this phone. This local-host nodes tool mirrors the desktop nodes tool style, but does not require a node selector or gateway settings.",
        parameters = buildNodesToolSchema(allowedActions),
      ),
    )
  }

  override suspend fun executeToolCall(
    role: String,
    name: String,
    argumentsJson: String,
  ): LocalHostToolExecutionResult {
    if (name != "nodes") {
      return LocalHostToolExecutionResult(
        outputText = errorOutputJson(code = "UNKNOWN_TOOL", message = "UNKNOWN_TOOL: $name"),
      )
    }

    val params = parseArguments(argumentsJson)
      ?: return LocalHostToolExecutionResult(
        outputText =
          errorOutputJson(
            code = "INVALID_REQUEST",
            message = "INVALID_REQUEST: nodes arguments must be a JSON object",
          ),
      )
    val actionName = readString(params, "action").orEmpty()
    val spec = actionsByName[actionName]
      ?: return LocalHostToolExecutionResult(
        outputText =
          errorOutputJson(
            code = "INVALID_REQUEST",
            message = "INVALID_REQUEST: unsupported nodes action $actionName",
          ),
      )

    if (!isActionAllowedForRole(role = role, spec = spec)) {
      return LocalHostToolExecutionResult(
        outputText =
          errorOutputJson(
            code = "COMMAND_DISABLED",
            message = "COMMAND_DISABLED: ${spec.command} is not enabled for this session",
          ),
      )
    }

    val invokeParams = buildInvokeParams(action = actionName, params = params)
    val result = invoke(spec.command, invokeParams?.toString())
    return if (result.ok) {
      normalizeSuccessResult(action = actionName, payloadJson = result.payloadJson)
    } else {
      LocalHostToolExecutionResult(
        outputText =
          errorOutputJson(
            code = result.error?.code ?: "UNAVAILABLE",
            message = result.error?.message ?: "request failed",
          ),
      )
    }
  }

  private fun supportedActionsForRole(role: String): List<NodesActionSpec> {
    return allActions.filter { spec ->
      isActionAllowedForRole(role = role, spec = spec)
    }
  }

  private fun isActionAllowedForRole(
    role: String,
    spec: NodesActionSpec,
  ): Boolean {
    return LocalHostCommandPolicy.isCommandAllowedForRole(
      role = role,
      command = spec.command,
      allowAdvanced = allowAdvancedRemoteCommands(),
      allowWrite = allowWriteRemoteCommands(),
    )
  }

  private fun buildNodesToolSchema(
    actions: List<NodesActionSpec>,
  ): JsonObject =
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
                  actions.forEach { add(JsonPrimitive(it.name)) }
                },
              )
            },
          )
          putStringProperty("title")
          putStringProperty("body")
          putStringProperty("sound")
          putStringEnumProperty("facing", listOf("front", "back"))
          putStringProperty("deviceId")
          putStringEnumProperty("priority", listOf("passive", "active", "timeSensitive"))
          putNumberProperty("maxAgeMs")
          putNumberProperty("maxWidth")
          putNumberProperty("quality")
          putNumberProperty("locationTimeoutMs")
          putStringEnumProperty("desiredAccuracy", listOf("coarse", "balanced", "precise"))
          putStringProperty("notificationKey")
          putStringEnumProperty("notificationAction", listOf("open", "dismiss", "reply"))
          putStringProperty("notificationReplyText")
          putStringProperty("query")
          putStringProperty("text")
          putStringProperty("contentDescription")
          putStringProperty("resourceId")
          putNumberProperty("limit")
          putNumberProperty("timeoutMs")
          putNumberProperty("pollIntervalMs")
          putNumberProperty("x")
          putNumberProperty("y")
          putNumberProperty("index")
          putBooleanProperty("ignoreCase")
          putStringEnumProperty("matchMode", listOf("contains", "exact"))
          putStringProperty("packageName")
          putStringProperty("givenName")
          putStringProperty("familyName")
          putStringProperty("organizationName")
          putStringProperty("displayName")
          putStringArrayProperty("phoneNumbers")
          putStringArrayProperty("emails")
          putStringProperty("startISO")
          putStringProperty("endISO")
          putBooleanProperty("isAllDay")
          putStringProperty("location")
          putStringProperty("notes")
          putNumberProperty("calendarId")
          putStringProperty("calendarTitle")
          putStringProperty("to")
          putStringProperty("message")
          putNumberProperty("offset")
          putStringProperty("cachedName")
          putStringProperty("number")
          putNumberProperty("date")
          putNumberProperty("dateStart")
          putNumberProperty("dateEnd")
          putNumberProperty("duration")
          putNumberProperty("type")
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

  private fun buildInvokeParams(
    action: String,
    params: JsonObject,
  ): JsonObject? {
    return when (action) {
      "notify" ->
        buildJsonObject {
          put("title", JsonPrimitive(readString(params, "title") ?: ""))
          put("body", JsonPrimitive(readString(params, "body") ?: ""))
          readString(params, "sound")?.let { put("sound", JsonPrimitive(it)) }
          readString(params, "priority")?.let { priority ->
            val normalized =
              if (priority.equals("timeSensitive", ignoreCase = true)) "timesensitive" else priority
            put("priority", JsonPrimitive(normalized))
          }
        }
      "camera_list" -> buildJsonObject {}
      "camera_snap" ->
        buildJsonObject {
          copyString(params, "facing")
          copyString(params, "deviceId")
          copyNumber(params, "maxWidth")
          copyNumber(params, "quality")
        }
      "location_get" ->
        buildJsonObject {
          copyNumber(params, "maxAgeMs")
          readNumberPrimitive(params, "locationTimeoutMs")?.let { put("timeoutMs", it) }
          copyString(params, "desiredAccuracy")
        }
      "notifications_list",
      "device_status",
      "device_info",
      "device_permissions",
      "device_health",
      "ui_state",
      "ui_back",
      "ui_home" -> buildJsonObject {}
      "ui_tap" ->
        buildJsonObject {
          copyNumber(params, "x")
          copyNumber(params, "y")
          copyString(params, "text")
          copyString(params, "contentDescription")
          copyString(params, "resourceId")
          copyString(params, "packageName")
          copyNumber(params, "index")
          copyBoolean(params, "ignoreCase")
          copyString(params, "matchMode")
        }
      "ui_wait_for_text" ->
        buildJsonObject {
          copyString(params, "text")
          copyNumber(params, "timeoutMs")
          copyNumber(params, "pollIntervalMs")
          copyBoolean(params, "ignoreCase")
          copyString(params, "matchMode")
          copyString(params, "packageName")
        }
      "notifications_action" ->
        buildJsonObject {
          readString(params, "notificationKey")?.let { put("key", JsonPrimitive(it)) }
          readString(params, "notificationAction")?.let { put("action", JsonPrimitive(it)) }
          readString(params, "notificationReplyText")?.let { put("replyText", JsonPrimitive(it)) }
        }
      "contacts_search" ->
        buildJsonObject {
          copyString(params, "query")
          copyNumber(params, "limit")
        }
      "contacts_add" ->
        buildJsonObject {
          copyString(params, "givenName")
          copyString(params, "familyName")
          copyString(params, "organizationName")
          copyString(params, "displayName")
          copyStringArray(params, "phoneNumbers")
          copyStringArray(params, "emails")
        }
      "calendar_events" ->
        buildJsonObject {
          copyString(params, "startISO")
          copyString(params, "endISO")
          copyNumber(params, "limit")
        }
      "calendar_add" ->
        buildJsonObject {
          copyString(params, "title")
          copyString(params, "startISO")
          copyString(params, "endISO")
          copyBoolean(params, "isAllDay")
          copyString(params, "location")
          copyString(params, "notes")
          copyNumber(params, "calendarId")
          copyString(params, "calendarTitle")
        }
      "motion_activity" ->
        buildJsonObject {
          copyString(params, "startISO")
          copyString(params, "endISO")
          copyNumber(params, "limit")
        }
      "motion_pedometer" ->
        buildJsonObject {
          copyString(params, "startISO")
          copyString(params, "endISO")
        }
      "photos_latest" ->
        buildJsonObject {
          copyNumber(params, "limit")
          copyNumber(params, "maxWidth")
          copyNumber(params, "quality")
        }
      "sms_send" ->
        buildJsonObject {
          copyString(params, "to")
          copyString(params, "message")
        }
      "calllog_search" ->
        buildJsonObject {
          copyNumber(params, "limit")
          copyNumber(params, "offset")
          copyString(params, "cachedName")
          copyString(params, "number")
          copyNumber(params, "date")
          copyNumber(params, "dateStart")
          copyNumber(params, "dateEnd")
          copyNumber(params, "duration")
          copyNumber(params, "type")
        }
      else -> null
    }
  }

  private fun parseArguments(
    argumentsJson: String,
  ): JsonObject? {
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

  private fun normalizeSuccessResult(
    action: String,
    payloadJson: String?,
  ): LocalHostToolExecutionResult {
    val trimmed = payloadJson?.trim().orEmpty()
    if (trimmed.isEmpty()) {
      return LocalHostToolExecutionResult(
        outputText = buildJsonObject { put("ok", JsonPrimitive(true)) }.toString(),
      )
    }
    val payload =
      try {
        json.parseToJsonElement(trimmed).asObjectOrNull()
      } catch (_: Throwable) {
        null
      } ?: return LocalHostToolExecutionResult(outputText = trimmed)

    return when (action) {
      "camera_snap" -> normalizeCameraSnapResult(payload = payload)
      "photos_latest" -> normalizePhotosLatestResult(payload = payload)
      else -> LocalHostToolExecutionResult(outputText = trimmed)
    }
  }

  private fun errorOutputJson(
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

  private fun normalizeCameraSnapResult(payload: JsonObject): LocalHostToolExecutionResult {
    val base64 = (payload["base64"] as? JsonPrimitive)?.content?.trim().orEmpty()
    val mimeType = imageMimeTypeForFormat((payload["format"] as? JsonPrimitive)?.content)
    if (base64.isEmpty() || mimeType == null) {
      return LocalHostToolExecutionResult(outputText = payload.toString())
    }
    return LocalHostToolExecutionResult(
      outputText =
        buildJsonObject {
          put("ok", JsonPrimitive(true))
          put("imageCount", JsonPrimitive(1))
          payload["width"]?.let { put("width", it) }
          payload["height"]?.let { put("height", it) }
        }.toString(),
      imageInputs = listOf(LocalHostToolImageInput(mimeType = mimeType, base64 = base64)),
    )
  }

  private fun normalizePhotosLatestResult(payload: JsonObject): LocalHostToolExecutionResult {
    val photos = payload["photos"] as? JsonArray ?: return LocalHostToolExecutionResult(outputText = payload.toString())
    val imageInputs =
      photos.mapNotNull { item ->
        val obj = item.asObjectOrNull() ?: return@mapNotNull null
        val base64 = (obj["base64"] as? JsonPrimitive)?.content?.trim().orEmpty()
        val mimeType = imageMimeTypeForFormat((obj["format"] as? JsonPrimitive)?.content)
        if (base64.isEmpty() || mimeType == null) return@mapNotNull null
        LocalHostToolImageInput(mimeType = mimeType, base64 = base64)
      }
    if (imageInputs.isEmpty()) {
      return LocalHostToolExecutionResult(outputText = payload.toString())
    }
    return LocalHostToolExecutionResult(
      outputText =
        buildJsonObject {
          put("ok", JsonPrimitive(true))
          put("imageCount", JsonPrimitive(imageInputs.size))
          put(
            "photos",
            buildJsonArray {
              photos.forEach { item ->
                val obj = item.asObjectOrNull() ?: return@forEach
                add(
                  buildJsonObject {
                    obj["format"]?.let { put("format", it) }
                    obj["width"]?.let { put("width", it) }
                    obj["height"]?.let { put("height", it) }
                    obj["createdAt"]?.let { put("createdAt", it) }
                  },
                )
              }
            },
          )
        }.toString(),
      imageInputs = imageInputs,
    )
  }

  private fun imageMimeTypeForFormat(format: String?): String? {
    return when (format?.trim()?.lowercase()) {
      "jpg", "jpeg" -> "image/jpeg"
      "png" -> "image/png"
      "webp" -> "image/webp"
      else -> null
    }
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

private fun JsonObjectBuilder.putStringEnumProperty(
  name: String,
  values: List<String>,
) {
  put(
    name,
    buildJsonObject {
      put("type", JsonPrimitive("string"))
      put(
        "enum",
        buildJsonArray {
          values.forEach { add(JsonPrimitive(it)) }
        },
      )
    },
  )
}

private fun JsonObjectBuilder.putStringArrayProperty(name: String) {
  put(
    name,
    buildJsonObject {
      put("type", JsonPrimitive("array"))
      put(
        "items",
        buildJsonObject {
          put("type", JsonPrimitive("string"))
        },
      )
    },
  )
}

private fun JsonObjectBuilder.copyString(
  params: JsonObject,
  key: String,
) {
  readString(params, key)?.let { put(key, JsonPrimitive(it)) }
}

private fun JsonObjectBuilder.copyNumber(
  params: JsonObject,
  key: String,
) {
  readNumberPrimitive(params, key)?.let { put(key, it) }
}

private fun JsonObjectBuilder.copyBoolean(
  params: JsonObject,
  key: String,
) {
  readBooleanPrimitive(params, key)?.let { put(key, it) }
}

private fun JsonObjectBuilder.copyStringArray(
  params: JsonObject,
  key: String,
) {
  readStringArray(params, key)?.let { values ->
    put(
      key,
      buildJsonArray {
        values.forEach { add(JsonPrimitive(it)) }
      },
    )
  }
}

private fun readString(
  params: JsonObject,
  key: String,
): String? =
  (params[key] as? JsonPrimitive)
    ?.content
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

private fun readNumberPrimitive(
  params: JsonObject,
  key: String,
): JsonPrimitive? =
  when (val element = params[key]) {
    is JsonPrimitive ->
      run {
        val asLong = element.content.toLongOrNull()
        val asDouble = element.content.toDoubleOrNull()
        when {
          asLong != null -> JsonPrimitive(asLong)
          asDouble != null -> JsonPrimitive(asDouble)
          else -> null
        }
      }
    else -> null
  }

private fun readBooleanPrimitive(
  params: JsonObject,
  key: String,
): JsonPrimitive? =
  when (val element = params[key]) {
    is JsonPrimitive -> {
      when (element.content.trim().lowercase()) {
        "true" -> JsonPrimitive(true)
        "false" -> JsonPrimitive(false)
        else -> null
      }
    }
    else -> null
  }

private fun readStringArray(
  params: JsonObject,
  key: String,
): List<String>? {
  val array = params[key] as? JsonArray ?: return null
  return array.mapNotNull { item ->
    (item as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }
  }
}

private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject
