package ai.openclaw.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import java.util.Locale
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private const val dedicatedHostRecoveryAction = "ai.openclaw.app.action.DEDICATED_HOST_RECOVERY"
private const val dedicatedHostRecoveryRequestCode = 4107
private const val dedicatedHostRecoveryDelayMs = 5_000L
private const val dedicatedHostWatchdogAction = "ai.openclaw.app.action.DEDICATED_HOST_WATCHDOG"
private const val dedicatedHostWatchdogRequestCode = 4108
internal const val dedicatedHostWatchdogIntervalMs = AlarmManager.INTERVAL_FIFTEEN_MINUTES

internal fun isDedicatedHostBatteryOptimizationIgnored(context: Context): Boolean {
  val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
  return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

internal fun requestDedicatedHostBatteryOptimizationExemption(context: Context) {
  val requestIntent =
    Intent(
      Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
      Uri.parse("package:${context.packageName}"),
    )
  runCatching {
    context.startActivity(requestIntent)
  }.getOrElse {
    context.startActivity(dedicatedHostAppSettingsIntent(context))
  }
}

internal fun openDedicatedHostAppSettings(context: Context) {
  context.startActivity(dedicatedHostAppSettingsIntent(context))
}

internal fun dedicatedHostRecentsSwipeForceStopRisk(): Boolean {
  val manufacturer = Build.MANUFACTURER?.trim().orEmpty().lowercase(Locale.US)
  val brand = Build.BRAND?.trim().orEmpty().lowercase(Locale.US)
  return manufacturer == "oppo" || brand == "oppo"
}

internal fun dedicatedHostBackgroundPolicyNote(): String? {
  if (!dedicatedHostRecentsSwipeForceStopRisk()) return null
  return "On this OPPO / ColorOS phone, swiping OpenClaw away from Recents can force-stop the app and clear its alarms. Keep the OpenClaw card locked in Recents and do not swipe it away."
}

internal fun dedicatedHostManufacturerLabel(): String {
  val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
  val brand = Build.BRAND?.trim().orEmpty()
  return when {
    manufacturer.isNotEmpty() && brand.isNotEmpty() && !manufacturer.equals(brand, ignoreCase = true) ->
      "$manufacturer / $brand"
    manufacturer.isNotEmpty() -> manufacturer
    brand.isNotEmpty() -> brand
    else -> "Android"
  }
}

private fun dedicatedHostAppSettingsIntent(context: Context) =
  Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.fromParts("package", context.packageName, null),
  ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

internal fun scheduleDedicatedHostServiceRecovery(context: Context, delayMs: Long = dedicatedHostRecoveryDelayMs) {
  val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
  val pendingIntent =
    dedicatedHostRecoveryPendingIntent(
      context = context,
      flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    ) ?: return
  alarmManager.setAndAllowWhileIdle(
    AlarmManager.ELAPSED_REALTIME_WAKEUP,
    SystemClock.elapsedRealtime() + delayMs.coerceAtLeast(0L),
    pendingIntent,
  )
}

internal fun cancelDedicatedHostServiceRecovery(context: Context) {
  val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
  val pendingIntent =
    dedicatedHostRecoveryPendingIntent(
      context = context,
      flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    ) ?: return
  alarmManager.cancel(pendingIntent)
  pendingIntent.cancel()
}

internal fun scheduleDedicatedHostWatchdog(context: Context, delayMs: Long = dedicatedHostWatchdogIntervalMs) {
  val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
  val pendingIntent =
    dedicatedHostWatchdogPendingIntent(
      context = context,
      flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    ) ?: return
  alarmManager.setAndAllowWhileIdle(
    AlarmManager.ELAPSED_REALTIME_WAKEUP,
    SystemClock.elapsedRealtime() + delayMs.coerceAtLeast(0L),
    pendingIntent,
  )
}

internal fun cancelDedicatedHostWatchdog(context: Context) {
  val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
  val pendingIntent =
    dedicatedHostWatchdogPendingIntent(
      context = context,
      flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    ) ?: return
  alarmManager.cancel(pendingIntent)
  pendingIntent.cancel()
}

internal fun dedicatedHostDeploymentStatusSnapshot(context: Context, prefs: SecurePrefs) =
  buildJsonObject {
    val dedicatedEnabled = prefs.localHostDedicatedDeploymentEnabled.value
    val onboardingCompleted = prefs.onboardingCompleted.value
    val localHostMode = prefs.gatewayConnectionMode.value == GatewayConnectionMode.LocalHost
    val batteryOptimizationIgnored = isDedicatedHostBatteryOptimizationIgnored(context)
    val recentsSwipeForceStopRisk = dedicatedHostRecentsSwipeForceStopRisk()
    val backgroundPolicyNote = dedicatedHostBackgroundPolicyNote()
    put("dedicatedEnabled", JsonPrimitive(dedicatedEnabled))
    put("onboardingCompleted", JsonPrimitive(onboardingCompleted))
    put("connectionMode", JsonPrimitive(prefs.gatewayConnectionMode.value.rawValue))
    put("keepAliveEligible", JsonPrimitive(dedicatedEnabled && onboardingCompleted && localHostMode))
    put("manufacturer", JsonPrimitive(dedicatedHostManufacturerLabel()))
    put("batteryOptimizationIgnored", JsonPrimitive(batteryOptimizationIgnored))
    put("batteryOptimizationRecommended", JsonPrimitive(dedicatedEnabled && !batteryOptimizationIgnored))
    put("recentsSwipeForceStopRisk", JsonPrimitive(recentsSwipeForceStopRisk))
    put("taskLockRecommended", JsonPrimitive(recentsSwipeForceStopRisk))
    put("remoteAccessEnabled", JsonPrimitive(prefs.localHostRemoteAccessEnabled.value))
    put("recoveryDelayMs", JsonPrimitive(dedicatedHostRecoveryDelayMs))
    put("watchdogIntervalMs", JsonPrimitive(dedicatedHostWatchdogIntervalMs))
    put("watchdogEnabled", JsonPrimitive(dedicatedEnabled && onboardingCompleted && localHostMode))
    backgroundPolicyNote?.let { put("backgroundPolicyNote", JsonPrimitive(it)) }
  }

private fun dedicatedHostRecoveryPendingIntent(context: Context, flags: Int): PendingIntent? {
  val intent =
    Intent(context, NodeForegroundService::class.java)
      .setAction(dedicatedHostRecoveryAction)
  return PendingIntent.getForegroundService(
    context,
    dedicatedHostRecoveryRequestCode,
    intent,
    flags,
  )
}

private fun dedicatedHostWatchdogPendingIntent(context: Context, flags: Int): PendingIntent? {
  val intent =
    Intent(context, NodeForegroundService::class.java)
      .setAction(dedicatedHostWatchdogAction)
  return PendingIntent.getForegroundService(
    context,
    dedicatedHostWatchdogRequestCode,
    intent,
    flags,
  )
}
