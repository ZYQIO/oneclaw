package ai.openclaw.app.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

data class LocalHostUiAutomationStatus(
  val enabled: Boolean,
  val serviceConnected: Boolean,
  val available: Boolean,
) {
  val statusText: String
    get() =
      when {
        available -> "UI automation is ready."
        enabled -> "UI automation is enabled and waiting for the service to bind."
        else -> "UI automation is off."
      }

  val detailText: String
    get() =
      when {
        available -> "OpenClaw can now expose accessibility-backed UI readiness for future cross-app control."
        enabled -> "Accessibility access is on, but OpenClaw has not yet observed a live service binding in this process."
        else -> "Enable the OpenClaw accessibility service to prepare `ui.state`, `ui.tap`, and other bounded phone-control actions."
      }

  fun toJson(): JsonObject =
    buildJsonObject {
      put("enabled", JsonPrimitive(enabled))
      put("serviceConnected", JsonPrimitive(serviceConnected))
      put("available", JsonPrimitive(available))
      put("settingsAction", JsonPrimitive(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

fun localHostUiAutomationStatusSnapshot(context: Context): LocalHostUiAutomationStatus {
  val enabled = isLocalHostUiAutomationEnabled(context)
  val serviceConnected = OpenClawAccessibilityService.isServiceConnected()
  return LocalHostUiAutomationStatus(
    enabled = enabled,
    serviceConnected = serviceConnected,
    available = enabled && serviceConnected,
  )
}

fun localHostUiAutomationActiveWindowSnapshot(): JsonObject? = OpenClawAccessibilityService.snapshotActiveWindow()

fun openLocalHostUiAutomationSettings(context: Context) {
  val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  runCatching {
    context.startActivity(intent)
  }.getOrElse {
    openAppSettings(context)
  }
}

private fun isLocalHostUiAutomationEnabled(context: Context): Boolean {
  val manager = context.getSystemService(AccessibilityManager::class.java)
  val component = OpenClawAccessibilityService.serviceComponent(context)
  val enabledServices =
    manager
      ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
      .orEmpty()

  if (
    enabledServices.any { info ->
      val serviceInfo = info.resolveInfo?.serviceInfo
      serviceInfo?.packageName == component.packageName && serviceInfo.name == component.className
    }
  ) {
    return true
  }

  val raw = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
  if (raw.isBlank()) return false
  val flattened = component.flattenToString()
  val shortFlattened = component.flattenToShortString()
  return raw.split(':').any { entry ->
    entry.equals(flattened, ignoreCase = true) || entry.equals(shortFlattened, ignoreCase = true)
  }
}

private fun openAppSettings(context: Context) {
  val intent =
    Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  context.startActivity(intent)
}
