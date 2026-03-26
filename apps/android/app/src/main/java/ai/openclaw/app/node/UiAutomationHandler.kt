package ai.openclaw.app.node

import android.os.SystemClock
import ai.openclaw.app.accessibility.LocalHostUiAutomationStatus
import ai.openclaw.app.accessibility.UiAutomationTapRequest
import ai.openclaw.app.accessibility.UiAutomationTapResult
import ai.openclaw.app.gateway.GatewaySession
import ai.openclaw.app.node.asObjectOrNull
import ai.openclaw.app.node.asStringOrNull
import ai.openclaw.app.node.parseJsonBooleanFlag
import ai.openclaw.app.node.parseJsonDouble
import ai.openclaw.app.node.parseJsonInt
import ai.openclaw.app.node.parseJsonParamsObject
import ai.openclaw.app.node.parseJsonString
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

private enum class UiTextMatchMode(val wireValue: String) {
  Contains("contains"),
  Exact("exact"),
  ;

  companion object {
    fun parse(raw: String?): UiTextMatchMode? {
      return when (raw?.trim()?.lowercase()) {
        null,
        "",
        "contains" -> Contains
        "exact" -> Exact
        else -> null
      }
    }
  }
}

private data class WaitForTextRequest(
  val text: String,
  val timeoutMs: Long,
  val pollIntervalMs: Long,
  val ignoreCase: Boolean,
  val matchMode: UiTextMatchMode,
  val packageName: String?,
)

private data class TapRequest(
  val x: Double?,
  val y: Double?,
  val text: String?,
  val contentDescription: String?,
  val resourceId: String?,
  val packageName: String?,
  val ignoreCase: Boolean,
  val matchMode: UiTextMatchMode,
  val index: Int,
) {
  val hasCoordinates: Boolean
    get() = x != null || y != null

  val hasSelector: Boolean
    get() = listOf(text, contentDescription, resourceId).any { !it.isNullOrEmpty() }
}

class UiAutomationHandler(
  private val readinessSnapshot: () -> LocalHostUiAutomationStatus,
  private val activeWindowSnapshot: () -> JsonObject?,
  private val monotonicClockMs: () -> Long = { SystemClock.elapsedRealtime() },
  private val sleeper: suspend (Long) -> Unit = { delay(it) },
  private val performBackAction: () -> Boolean = {
    ai.openclaw.app.accessibility.OpenClawAccessibilityService.performGlobalBack()
  },
  private val performHomeAction: () -> Boolean = {
    ai.openclaw.app.accessibility.OpenClawAccessibilityService.performGlobalHome()
  },
  private val performTapAction: (UiAutomationTapRequest) -> UiAutomationTapResult = { request ->
    ai.openclaw.app.accessibility.OpenClawAccessibilityService.performTap(request)
  },
) {
  fun handleUiState(_paramsJson: String?): GatewaySession.InvokeResult {
    val readiness = readinessSnapshot()
    val activeWindow = activeWindowSnapshot()

    return GatewaySession.InvokeResult.ok(buildUiStatePayload(readiness, activeWindow).toString())
  }

  suspend fun handleWaitForText(paramsJson: String?): GatewaySession.InvokeResult {
    val request =
      parseWaitForTextRequest(paramsJson)
        ?: return GatewaySession.InvokeResult.error(
          code = "INVALID_REQUEST",
          message =
            "INVALID_REQUEST: expected JSON object with text and optional timeoutMs/pollIntervalMs/matchMode/ignoreCase/packageName",
        )
    if (request.text.isEmpty()) {
      return GatewaySession.InvokeResult.error(
        code = "INVALID_REQUEST",
        message = "INVALID_REQUEST: text is required",
      )
    }

    val startedAtMs = monotonicClockMs()
    val deadlineMs = startedAtMs + request.timeoutMs.coerceAtLeast(0L)
    var lastReason = "No active accessibility window is available."
    var lastSnapshot: JsonObject? = null

    while (true) {
      val readiness = readinessSnapshot()
      if (!readiness.enabled) {
        return GatewaySession.InvokeResult.error(
          code = "UI_AUTOMATION_DISABLED",
          message = "UI_AUTOMATION_DISABLED: enable the OpenClaw accessibility service first",
        )
      }

      if (!readiness.serviceConnected) {
        lastReason = "Accessibility service is enabled but not yet bound."
      } else {
        val snapshot = activeWindowSnapshot()
        if (snapshot != null) {
          lastSnapshot = snapshot
          val activePackageName = snapshot["packageName"].asStringOrNull()?.trim().orEmpty()
          if (
            request.packageName != null &&
            !request.packageName.equals(activePackageName, ignoreCase = true)
          ) {
            lastReason =
              "Active package `${activePackageName.ifEmpty { "unknown" }}` does not match `${request.packageName}` yet."
          } else {
            val matchedText = findMatchingText(snapshot = snapshot, request = request)
            if (matchedText != null) {
              val payload =
                buildJsonObject {
                  buildUiStatePayload(readiness, snapshot).forEach { (key, value) -> put(key, value) }
                  put(
                    "wait",
                    buildJsonObject {
                      put("matched", JsonPrimitive(true))
                      put("matchedText", JsonPrimitive(matchedText))
                      put("requestedText", JsonPrimitive(request.text))
                      put("matchMode", JsonPrimitive(request.matchMode.wireValue))
                      put("ignoreCase", JsonPrimitive(request.ignoreCase))
                      put("elapsedMs", JsonPrimitive(monotonicClockMs() - startedAtMs))
                    },
                  )
                }
              return GatewaySession.InvokeResult.ok(payload.toString())
            }
            lastReason = "Text `${request.text}` is not visible yet."
          }
        } else {
          lastReason = "No active accessibility window is available."
        }
      }

      val nowMs = monotonicClockMs()
      if (nowMs >= deadlineMs) {
        break
      }
      val sleepMs = minOf(request.pollIntervalMs, (deadlineMs - nowMs).coerceAtLeast(0L))
      if (sleepMs > 0L) {
        sleeper(sleepMs)
      }
    }

    val timeoutMessage =
      buildString {
        append("UI_WAIT_TIMEOUT: text `")
        append(request.text)
        append("` did not appear within ")
        append(request.timeoutMs)
        append("ms.")
        append(' ')
        append(lastReason)
        val snapshot = lastSnapshot
        if (snapshot != null) {
          val packageName = snapshot["packageName"].asStringOrNull()?.trim().orEmpty()
          if (packageName.isNotEmpty()) {
            append(" Last package: `")
            append(packageName)
            append("`.")
          }
          val visibleText = formatVisibleTextSummary(snapshot["visibleText"] as? JsonArray)
          if (visibleText.isNotEmpty()) {
            append(" Visible text: ")
            append(visibleText)
            append('.')
          }
        }
      }
    return GatewaySession.InvokeResult.error(
      code = "UI_WAIT_TIMEOUT",
      message = timeoutMessage,
    )
  }

  fun handleBack(_paramsJson: String?): GatewaySession.InvokeResult {
    return handleGlobalAction(commandName = "back", action = performBackAction)
  }

  fun handleHome(_paramsJson: String?): GatewaySession.InvokeResult {
    return handleGlobalAction(commandName = "home", action = performHomeAction)
  }

  fun handleTap(paramsJson: String?): GatewaySession.InvokeResult {
    val request =
      parseTapRequest(paramsJson)
        ?: return GatewaySession.InvokeResult.error(
          code = "INVALID_REQUEST",
          message =
            "INVALID_REQUEST: expected JSON object with x/y or text/contentDescription/resourceId and optional packageName/matchMode/ignoreCase/index",
        )
    if (request.hasCoordinates && (request.x == null || request.y == null)) {
      return GatewaySession.InvokeResult.error(
        code = "INVALID_REQUEST",
        message = "INVALID_REQUEST: ui.tap requires both x and y when using coordinate mode",
      )
    }
    if (request.hasCoordinates && request.hasSelector) {
      return GatewaySession.InvokeResult.error(
        code = "INVALID_REQUEST",
        message = "INVALID_REQUEST: ui.tap accepts either coordinates or selector fields, not both",
      )
    }
    if (!request.hasCoordinates && !request.hasSelector) {
      return GatewaySession.InvokeResult.error(
        code = "INVALID_REQUEST",
        message = "INVALID_REQUEST: ui.tap requires x/y or at least one selector field",
      )
    }
    if ((request.x ?: 0.0) < 0.0 || (request.y ?: 0.0) < 0.0) {
      return GatewaySession.InvokeResult.error(
        code = "INVALID_REQUEST",
        message = "INVALID_REQUEST: ui.tap coordinates must be non-negative",
      )
    }
    if (request.index < 0) {
      return GatewaySession.InvokeResult.error(
        code = "INVALID_REQUEST",
        message = "INVALID_REQUEST: ui.tap index must be zero or greater",
      )
    }

    val readiness = readinessSnapshot()
    if (!readiness.enabled) {
      return GatewaySession.InvokeResult.error(
        code = "UI_AUTOMATION_DISABLED",
        message = "UI_AUTOMATION_DISABLED: enable the OpenClaw accessibility service first",
      )
    }
    if (!readiness.serviceConnected) {
      return GatewaySession.InvokeResult.error(
        code = "UI_AUTOMATION_UNAVAILABLE",
        message = "UI_AUTOMATION_UNAVAILABLE: accessibility service is enabled but not yet bound",
      )
    }

    val result =
      performTapAction(
        UiAutomationTapRequest(
          x = request.x,
          y = request.y,
          text = request.text,
          contentDescription = request.contentDescription,
          resourceId = request.resourceId,
          packageName = request.packageName,
          exactMatch = request.matchMode == UiTextMatchMode.Exact,
          ignoreCase = request.ignoreCase,
          index = request.index,
        ),
      )
    if (!result.performed) {
      val errorCode = result.errorCode ?: "UI_ACTION_FAILED"
      val reason = result.reason ?: "ui.tap was not accepted by the accessibility service"
      return GatewaySession.InvokeResult.error(
        code = errorCode,
        message = "$errorCode: $reason",
      )
    }

    val payload =
      buildJsonObject {
        put("ok", JsonPrimitive(true))
        put("action", JsonPrimitive("tap"))
        put("performed", JsonPrimitive(true))
        result.strategy?.let { put("strategy", JsonPrimitive(it)) }
        result.packageName?.let { put("packageName", JsonPrimitive(it)) }
        result.matchedText?.let { put("matchedText", JsonPrimitive(it)) }
        result.matchedContentDescription?.let { put("matchedContentDescription", JsonPrimitive(it)) }
        result.resourceId?.let { put("resourceId", JsonPrimitive(it)) }
        result.x?.let { put("x", JsonPrimitive(it)) }
        result.y?.let { put("y", JsonPrimitive(it)) }
        put(
          "selector",
          buildJsonObject {
            request.text?.let { put("text", JsonPrimitive(it)) }
            request.contentDescription?.let { put("contentDescription", JsonPrimitive(it)) }
            request.resourceId?.let { put("resourceId", JsonPrimitive(it)) }
            request.packageName?.let { put("packageName", JsonPrimitive(it)) }
            put("matchMode", JsonPrimitive(request.matchMode.wireValue))
            put("ignoreCase", JsonPrimitive(request.ignoreCase))
            put("index", JsonPrimitive(request.index))
          },
        )
      }
    return GatewaySession.InvokeResult.ok(payload.toString())
  }

  private fun buildUiStatePayload(
    readiness: LocalHostUiAutomationStatus,
    activeWindow: JsonObject?,
  ): JsonObject =
    buildJsonObject {
      put("enabled", JsonPrimitive(readiness.enabled))
      put("serviceConnected", JsonPrimitive(readiness.serviceConnected))
      put("available", JsonPrimitive(readiness.available))
      put("statusText", JsonPrimitive(readiness.statusText))
      put("detailText", JsonPrimitive(readiness.detailText))
      put("activeWindowAvailable", JsonPrimitive(activeWindow != null))
      if (activeWindow != null) {
        activeWindow.forEach { (key, value) -> put(key, value) }
      } else {
        put(
          "reason",
          JsonPrimitive(
            when {
              !readiness.enabled -> "Accessibility service is not enabled."
              !readiness.serviceConnected -> "Accessibility service is enabled but not yet bound."
              else -> "No active accessibility window is available."
            },
          ),
        )
        put("nodeCount", JsonPrimitive(0))
        put("truncated", JsonPrimitive(false))
        put("visibleText", buildJsonArray {})
        put("nodes", buildJsonArray {})
      }
    }

  private fun parseWaitForTextRequest(paramsJson: String?): WaitForTextRequest? {
    val params = parseJsonParamsObject(paramsJson) ?: return null
    val matchMode = UiTextMatchMode.parse(parseJsonString(params, "matchMode")) ?: return null
    return WaitForTextRequest(
      text = parseJsonString(params, "text")?.trim().orEmpty(),
      timeoutMs = parseJsonInt(params, "timeoutMs")?.toLong()?.coerceIn(0L, 60_000L) ?: 10_000L,
      pollIntervalMs = parseJsonInt(params, "pollIntervalMs")?.toLong()?.coerceIn(50L, 2_000L) ?: 250L,
      ignoreCase = parseJsonBooleanFlag(params, "ignoreCase") ?: true,
      matchMode = matchMode,
      packageName = parseJsonString(params, "packageName")?.trim()?.ifEmpty { null },
    )
  }

  private fun parseTapRequest(paramsJson: String?): TapRequest? {
    val params = parseJsonParamsObject(paramsJson) ?: return null
    val matchMode = UiTextMatchMode.parse(parseJsonString(params, "matchMode")) ?: return null
    return TapRequest(
      x = parseJsonDouble(params, "x"),
      y = parseJsonDouble(params, "y"),
      text = parseJsonString(params, "text")?.trim()?.ifEmpty { null },
      contentDescription = parseJsonString(params, "contentDescription")?.trim()?.ifEmpty { null },
      resourceId = parseJsonString(params, "resourceId")?.trim()?.ifEmpty { null },
      packageName = parseJsonString(params, "packageName")?.trim()?.ifEmpty { null },
      ignoreCase = parseJsonBooleanFlag(params, "ignoreCase") ?: true,
      matchMode = matchMode,
      index = parseJsonInt(params, "index") ?: 0,
    )
  }

  private fun findMatchingText(
    snapshot: JsonObject,
    request: WaitForTextRequest,
  ): String? {
    val candidates = LinkedHashSet<String>()
    (snapshot["visibleText"] as? JsonArray)?.forEach { item ->
      item.asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let(candidates::add)
    }
    (snapshot["nodes"] as? JsonArray)?.forEach { item ->
      val obj = item.asObjectOrNull() ?: return@forEach
      obj["text"].asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let(candidates::add)
      obj["contentDescription"].asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let(candidates::add)
    }
    return candidates.firstOrNull { candidate ->
      when (request.matchMode) {
        UiTextMatchMode.Contains -> candidate.contains(request.text, ignoreCase = request.ignoreCase)
        UiTextMatchMode.Exact -> candidate.equals(request.text, ignoreCase = request.ignoreCase)
      }
    }
  }

  private fun formatVisibleTextSummary(visibleText: JsonArray?): String {
    val values =
      visibleText
        ?.mapNotNull { item -> item.asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() } }
        .orEmpty()
        .take(4)
    if (values.isEmpty()) return ""
    return values.joinToString(separator = ", ") { value -> "`$value`" }
  }

  private fun handleGlobalAction(
    commandName: String,
    action: () -> Boolean,
  ): GatewaySession.InvokeResult {
    val readiness = readinessSnapshot()
    if (!readiness.enabled) {
      return GatewaySession.InvokeResult.error(
        code = "UI_AUTOMATION_DISABLED",
        message = "UI_AUTOMATION_DISABLED: enable the OpenClaw accessibility service first",
      )
    }
    if (!readiness.serviceConnected) {
      return GatewaySession.InvokeResult.error(
        code = "UI_AUTOMATION_UNAVAILABLE",
        message = "UI_AUTOMATION_UNAVAILABLE: accessibility service is enabled but not yet bound",
      )
    }
    if (!action()) {
      return GatewaySession.InvokeResult.error(
        code = "UI_ACTION_FAILED",
        message = "UI_ACTION_FAILED: ui.$commandName was not accepted by the accessibility service",
      )
    }
    val payload =
      buildJsonObject {
        put("ok", JsonPrimitive(true))
        put("action", JsonPrimitive(commandName))
        put("performed", JsonPrimitive(true))
      }
    return GatewaySession.InvokeResult.ok(payload.toString())
  }
}
