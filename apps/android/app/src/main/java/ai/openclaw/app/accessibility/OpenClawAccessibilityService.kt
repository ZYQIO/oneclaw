package ai.openclaw.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

data class UiAutomationTapRequest(
  val x: Double? = null,
  val y: Double? = null,
  val text: String? = null,
  val contentDescription: String? = null,
  val resourceId: String? = null,
  val packageName: String? = null,
  val exactMatch: Boolean = false,
  val ignoreCase: Boolean = true,
  val index: Int = 0,
)

data class UiAutomationTapResult(
  val performed: Boolean,
  val errorCode: String? = null,
  val strategy: String? = null,
  val reason: String? = null,
  val packageName: String? = null,
  val matchedText: String? = null,
  val matchedContentDescription: String? = null,
  val resourceId: String? = null,
  val x: Double? = null,
  val y: Double? = null,
)

data class UiAutomationInputTextRequest(
  val value: String,
  val text: String? = null,
  val contentDescription: String? = null,
  val resourceId: String? = null,
  val packageName: String? = null,
  val exactMatch: Boolean = false,
  val ignoreCase: Boolean = true,
  val index: Int = 0,
) {
  val hasSelector: Boolean
    get() = listOf(text, contentDescription, resourceId).any { !it.isNullOrEmpty() }
}

data class UiAutomationInputTextResult(
  val performed: Boolean,
  val errorCode: String? = null,
  val strategy: String? = null,
  val reason: String? = null,
  val packageName: String? = null,
  val matchedText: String? = null,
  val matchedContentDescription: String? = null,
  val resourceId: String? = null,
  val valueLength: Int? = null,
)

class OpenClawAccessibilityService : AccessibilityService() {
  override fun onServiceConnected() {
    super.onServiceConnected()
    activeService = this
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    // The first slice only establishes a bound, user-enabled service surface.
  }

  override fun onInterrupt() {
    // No-op for the initial readiness-only slice.
  }

  override fun onUnbind(intent: android.content.Intent?): Boolean {
    if (activeService === this) {
      activeService = null
    }
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    if (activeService === this) {
      activeService = null
    }
    super.onDestroy()
  }

  companion object {
    private const val maxUiStateNodes = 60
    private const val maxVisibleTextItems = 12
    private const val maxTextChars = 160

    @Volatile private var activeService: OpenClawAccessibilityService? = null

    fun isServiceConnected(): Boolean = activeService != null

    fun serviceComponent(context: Context): ComponentName =
      ComponentName(context, OpenClawAccessibilityService::class.java)

    fun snapshotActiveWindow(): JsonObject? = activeService?.snapshotActiveWindowInternal()

    fun performGlobalBack(): Boolean =
      activeService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) == true

    fun performGlobalHome(): Boolean =
      activeService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) == true

    fun performTap(request: UiAutomationTapRequest): UiAutomationTapResult =
      activeService?.performTapInternal(request)
        ?: UiAutomationTapResult(
          performed = false,
          errorCode = "UI_AUTOMATION_UNAVAILABLE",
          reason = "Accessibility service is enabled but not yet bound.",
        )

    fun performInputText(request: UiAutomationInputTextRequest): UiAutomationInputTextResult =
      activeService?.performInputTextInternal(request)
        ?: UiAutomationInputTextResult(
          performed = false,
          errorCode = "UI_AUTOMATION_UNAVAILABLE",
          reason = "Accessibility service is enabled but not yet bound.",
        )
  }

  private fun snapshotActiveWindowInternal(): JsonObject? {
    val root = rootInActiveWindow ?: return null
    var packageName: String? = null
    val visibleText = LinkedHashSet<String>()
    val nodes = mutableListOf<JsonObject>()
    var truncated = false
    val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
    queue.addLast(root to 0)

    while (queue.isNotEmpty()) {
      val (node, depth) = queue.removeFirst()
      if (packageName == null) {
        packageName = node.packageName?.toString()?.trim()?.takeIf { it.isNotEmpty() }
      }

      if (nodes.size >= maxUiStateNodes) {
        truncated = true
        continue
      }

      val text = normalizeNodeText(node.text?.toString())
      val contentDescription = normalizeNodeText(node.contentDescription?.toString())
      if (visibleText.size < maxVisibleTextItems) {
        text?.let(visibleText::add)
        contentDescription?.let(visibleText::add)
      }

      nodes +=
        buildJsonObject {
          put("depth", JsonPrimitive(depth))
          put("className", JsonPrimitive(node.className?.toString().orEmpty()))
          text?.let { put("text", JsonPrimitive(it)) }
          contentDescription?.let { put("contentDescription", JsonPrimitive(it)) }
          node.viewIdResourceName?.trim()?.takeIf { it.isNotEmpty() }?.let {
            put("resourceId", JsonPrimitive(it))
          }
          put("clickable", JsonPrimitive(node.isClickable))
          put("enabled", JsonPrimitive(node.isEnabled))
          put("focused", JsonPrimitive(node.isFocused))
          put("selected", JsonPrimitive(node.isSelected))
          put("editable", JsonPrimitive(node.isEditable))
          put("bounds", nodeBoundsJson(node))
        }

      if (nodes.size < maxUiStateNodes) {
        for (index in 0 until node.childCount) {
          node.getChild(index)?.let { child ->
            queue.addLast(child to depth + 1)
          }
        }
      } else if (node.childCount > 0) {
        truncated = true
      }
    }

    return buildJsonObject {
      packageName?.let { put("packageName", JsonPrimitive(it)) }
      put("nodeCount", JsonPrimitive(nodes.size))
      put("truncated", JsonPrimitive(truncated))
      put(
        "visibleText",
        buildJsonArray {
          visibleText.forEach { entry ->
            add(JsonPrimitive(entry))
          }
        },
      )
      put(
        "nodes",
        buildJsonArray {
          nodes.forEach { add(it) }
        },
      )
    }
  }

  private fun nodeBoundsJson(node: AccessibilityNodeInfo): JsonObject {
    val rect = Rect()
    node.getBoundsInScreen(rect)
    return buildJsonObject {
      put("left", JsonPrimitive(rect.left))
      put("top", JsonPrimitive(rect.top))
      put("right", JsonPrimitive(rect.right))
      put("bottom", JsonPrimitive(rect.bottom))
    }
  }

  private fun normalizeNodeText(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    return if (trimmed.length <= maxTextChars) trimmed else "${trimmed.take(maxTextChars - 3)}..."
  }

  private fun performTapInternal(request: UiAutomationTapRequest): UiAutomationTapResult {
    if (request.x != null && request.y != null) {
      return performCoordinateTap(request = request)
    }

    val root = rootInActiveWindow
      ?: return UiAutomationTapResult(
        performed = false,
        errorCode = "UI_TARGET_UNAVAILABLE",
        reason = "No active accessibility window is available for selector-based tap.",
      )
    val activePackageName = root.packageName?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    if (
      request.packageName != null &&
      !request.packageName.equals(activePackageName, ignoreCase = true)
    ) {
      return UiAutomationTapResult(
        performed = false,
        errorCode = "UI_TARGET_MISMATCH",
        reason =
          "Active package `${activePackageName ?: "unknown"}` does not match `${request.packageName}`.",
        packageName = activePackageName,
      )
    }

    val matchedNode =
      findMatchingNode(
        root = root,
        index = request.index,
      ) { node ->
        matchesNodeSelector(
          node = node,
          text = request.text,
          contentDescription = request.contentDescription,
          resourceId = request.resourceId,
          exactMatch = request.exactMatch,
          ignoreCase = request.ignoreCase,
        )
      }
      ?: return UiAutomationTapResult(
        performed = false,
        errorCode = "UI_TARGET_NOT_FOUND",
        reason = "No matching accessibility node was found for the requested tap selector.",
        packageName = activePackageName,
      )

    val tapCenter = nodeCenter(matchedNode)
    val clickTarget = findClickableAncestor(matchedNode) ?: matchedNode
    if (clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
      return buildTapSuccessResult(
        strategy = "node_click",
        packageName = activePackageName,
        node = matchedNode,
        center = tapCenter,
      )
    }
    if (tapCenter != null && dispatchTapGesture(tapCenter.first, tapCenter.second)) {
      return buildTapSuccessResult(
        strategy = "gesture_tap",
        packageName = activePackageName,
        node = matchedNode,
        center = tapCenter,
      )
    }
    return UiAutomationTapResult(
      performed = false,
      errorCode = "UI_ACTION_FAILED",
      reason = "Matched node rejected both accessibility click and fallback tap gesture.",
      packageName = activePackageName,
    )
  }

  private fun performCoordinateTap(request: UiAutomationTapRequest): UiAutomationTapResult {
    val activePackageName = rootInActiveWindow?.packageName?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    if (
      request.packageName != null &&
      !request.packageName.equals(activePackageName, ignoreCase = true)
    ) {
      return UiAutomationTapResult(
        performed = false,
        errorCode = "UI_TARGET_MISMATCH",
        reason =
          "Active package `${activePackageName ?: "unknown"}` does not match `${request.packageName}`.",
        packageName = activePackageName,
      )
    }
    val x = request.x ?: return UiAutomationTapResult(performed = false, errorCode = "INVALID_REQUEST", reason = "Tap coordinate x is missing.")
    val y = request.y ?: return UiAutomationTapResult(performed = false, errorCode = "INVALID_REQUEST", reason = "Tap coordinate y is missing.")
    if (!dispatchTapGesture(x = x, y = y)) {
      return UiAutomationTapResult(
        performed = false,
        errorCode = "UI_ACTION_FAILED",
        reason = "Accessibility service rejected the tap gesture.",
        packageName = activePackageName,
        x = x,
        y = y,
      )
    }
    return UiAutomationTapResult(
      performed = true,
      strategy = "coordinate_tap",
      packageName = activePackageName,
      x = x,
      y = y,
    )
  }

  private fun performInputTextInternal(
    request: UiAutomationInputTextRequest,
  ): UiAutomationInputTextResult {
    val root = rootInActiveWindow
      ?: return UiAutomationInputTextResult(
        performed = false,
        errorCode = "UI_TARGET_UNAVAILABLE",
        reason = "No active accessibility window is available for ui.inputText.",
      )
    val activePackageName = root.packageName?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    if (
      request.packageName != null &&
      !request.packageName.equals(activePackageName, ignoreCase = true)
    ) {
      return UiAutomationInputTextResult(
        performed = false,
        errorCode = "UI_TARGET_MISMATCH",
        reason =
          "Active package `${activePackageName ?: "unknown"}` does not match `${request.packageName}`.",
        packageName = activePackageName,
      )
    }

    val target =
      resolveInputTextTarget(root = root, request = request)
        ?: return UiAutomationInputTextResult(
          performed = false,
          errorCode = "UI_TARGET_NOT_FOUND",
          reason = "No editable accessibility node is available for ui.inputText.",
          packageName = activePackageName,
        )

    if (!target.node.isEnabled) {
      return UiAutomationInputTextResult(
        performed = false,
        errorCode = "UI_TARGET_DISABLED",
        reason = "The selected editable accessibility node is disabled.",
        packageName = activePackageName,
        matchedText = normalizeNodeText(target.node.text?.toString()),
        matchedContentDescription = normalizeNodeText(target.node.contentDescription?.toString()),
        resourceId = target.node.viewIdResourceName?.trim()?.takeIf { it.isNotEmpty() },
      )
    }

    if (!target.node.isFocused) {
      target.node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    }

    val arguments =
      Bundle().apply {
        putCharSequence(
          AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
          request.value,
        )
      }
    if (!target.node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
      return UiAutomationInputTextResult(
        performed = false,
        errorCode = "UI_ACTION_FAILED",
        reason = "The selected editable node rejected ACTION_SET_TEXT.",
        strategy = target.strategy,
        packageName = activePackageName,
        matchedText = normalizeNodeText(target.node.text?.toString()),
        matchedContentDescription = normalizeNodeText(target.node.contentDescription?.toString()),
        resourceId = target.node.viewIdResourceName?.trim()?.takeIf { it.isNotEmpty() },
        valueLength = request.value.length,
      )
    }

    return UiAutomationInputTextResult(
      performed = true,
      strategy = target.strategy,
      packageName = activePackageName,
      matchedText = normalizeNodeText(target.node.text?.toString()),
      matchedContentDescription = normalizeNodeText(target.node.contentDescription?.toString()),
      resourceId = target.node.viewIdResourceName?.trim()?.takeIf { it.isNotEmpty() },
      valueLength = request.value.length,
    )
  }

  private fun buildTapSuccessResult(
    strategy: String,
    packageName: String?,
    node: AccessibilityNodeInfo,
    center: Pair<Double, Double>?,
  ): UiAutomationTapResult =
    UiAutomationTapResult(
      performed = true,
      strategy = strategy,
      packageName = packageName,
      matchedText = normalizeNodeText(node.text?.toString()),
      matchedContentDescription = normalizeNodeText(node.contentDescription?.toString()),
      resourceId = node.viewIdResourceName?.trim()?.takeIf { it.isNotEmpty() },
      x = center?.first,
      y = center?.second,
    )

  private data class EditableTargetSelection(
    val node: AccessibilityNodeInfo,
    val strategy: String,
  )

  private fun resolveInputTextTarget(
    root: AccessibilityNodeInfo,
    request: UiAutomationInputTextRequest,
  ): EditableTargetSelection? {
    if (request.hasSelector) {
      val matchedNode =
        findMatchingNode(
          root = root,
          index = request.index,
        ) { node ->
          node.isEditable &&
            matchesNodeSelector(
              node = node,
              text = request.text,
              contentDescription = request.contentDescription,
              resourceId = request.resourceId,
              exactMatch = request.exactMatch,
              ignoreCase = request.ignoreCase,
            )
        }
      return matchedNode?.let { EditableTargetSelection(node = it, strategy = "selector_editable") }
    }

    val focusedEditable =
      findFirstMatchingNode(root) { node ->
        node.isEditable && (node.isFocused || node.isAccessibilityFocused)
      }
    if (focusedEditable != null) {
      return EditableTargetSelection(node = focusedEditable, strategy = "focused_editable")
    }

    val firstEditable =
      findFirstMatchingNode(root) { node ->
        node.isEditable
      } ?: return null
    val secondEditable =
      findMatchingNode(
        root = root,
        index = 1,
      ) { node ->
        node.isEditable
      }
    return if (secondEditable == null) {
      EditableTargetSelection(node = firstEditable, strategy = "single_editable")
    } else {
      null
    }
  }

  private fun findMatchingNode(
    root: AccessibilityNodeInfo,
    index: Int,
    predicate: (AccessibilityNodeInfo) -> Boolean,
  ): AccessibilityNodeInfo? {
    var matchIndex = 0
    val queue = ArrayDeque<AccessibilityNodeInfo>()
    queue.addLast(root)

    while (queue.isNotEmpty()) {
      val node = queue.removeFirst()
      if (predicate(node)) {
        if (matchIndex == index) {
          return node
        }
        matchIndex += 1
      }
      for (childIndex in 0 until node.childCount) {
        node.getChild(childIndex)?.let(queue::addLast)
      }
    }

    return null
  }

  private fun findFirstMatchingNode(
    root: AccessibilityNodeInfo,
    predicate: (AccessibilityNodeInfo) -> Boolean,
  ): AccessibilityNodeInfo? = findMatchingNode(root = root, index = 0, predicate = predicate)

  private fun matchesTapSelector(
    node: AccessibilityNodeInfo,
    request: UiAutomationTapRequest,
  ): Boolean {
    return matchesNodeSelector(
      node = node,
      text = request.text,
      contentDescription = request.contentDescription,
      resourceId = request.resourceId,
      exactMatch = request.exactMatch,
      ignoreCase = request.ignoreCase,
    )
  }

  private fun matchesNodeSelector(
    node: AccessibilityNodeInfo,
    text: String?,
    contentDescription: String?,
    resourceId: String?,
    exactMatch: Boolean,
    ignoreCase: Boolean,
  ): Boolean {
    return selectorFieldMatches(
      candidate = node.text?.toString(),
      query = text,
      exactMatch = exactMatch,
      ignoreCase = ignoreCase,
    ) &&
      selectorFieldMatches(
        candidate = node.contentDescription?.toString(),
        query = contentDescription,
        exactMatch = exactMatch,
        ignoreCase = ignoreCase,
      ) &&
      selectorFieldMatches(
        candidate = node.viewIdResourceName,
        query = resourceId,
        exactMatch = exactMatch,
        ignoreCase = ignoreCase,
      )
  }

  private fun selectorFieldMatches(
    candidate: String?,
    query: String?,
    exactMatch: Boolean,
    ignoreCase: Boolean,
  ): Boolean {
    val expected = query?.trim()?.takeIf { it.isNotEmpty() } ?: return true
    val actual = candidate?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    return if (exactMatch) {
      actual.equals(expected, ignoreCase = ignoreCase)
    } else {
      actual.contains(expected, ignoreCase = ignoreCase)
    }
  }

  private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
    var current: AccessibilityNodeInfo? = node
    while (current != null) {
      if (current.isClickable) {
        return current
      }
      current = current.parent
    }
    return null
  }

  private fun nodeCenter(node: AccessibilityNodeInfo): Pair<Double, Double>? {
    val rect = Rect()
    node.getBoundsInScreen(rect)
    if (rect.isEmpty) {
      return null
    }
    return rect.exactCenterX().toDouble() to rect.exactCenterY().toDouble()
  }

  private fun dispatchTapGesture(
    x: Double,
    y: Double,
  ): Boolean {
    val path =
      Path().apply {
        moveTo(x.toFloat(), y.toFloat())
      }
    val gesture =
      GestureDescription.Builder()
        .addStroke(GestureDescription.StrokeDescription(path, 0L, 50L))
        .build()
    return dispatchGesture(gesture, null, null)
  }
}
