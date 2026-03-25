package ai.openclaw.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

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
}
