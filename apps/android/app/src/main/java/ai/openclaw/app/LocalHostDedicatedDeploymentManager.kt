package ai.openclaw.app

import android.content.Context

internal object LocalHostDedicatedDeploymentManager {
  fun shouldKeepAlive(context: Context): Boolean {
    val plainPrefs =
      context.applicationContext.getSharedPreferences(
        SecurePrefs.plainPrefsName,
        Context.MODE_PRIVATE,
      )
    val dedicatedEnabled = plainPrefs.getBoolean(SecurePrefs.localHostDedicatedDeploymentEnabledKey, false)
    val onboardingCompleted = plainPrefs.getBoolean(SecurePrefs.onboardingCompletedKey, false)
    val connectionMode = plainPrefs.getString(SecurePrefs.gatewayConnectionModeKey, null)
    return dedicatedEnabled &&
      onboardingCompleted &&
      GatewayConnectionMode.fromRawValue(connectionMode) == GatewayConnectionMode.LocalHost
  }

  fun ensureServiceIfNeeded(context: Context) {
    if (!shouldKeepAlive(context)) return
    cancelDedicatedHostServiceRecovery(context.applicationContext)
    NodeForegroundService.start(context.applicationContext)
  }

  fun reconcileKeepAlive(context: Context) {
    val appContext = context.applicationContext
    if (shouldKeepAlive(appContext)) {
      ensureServiceIfNeeded(appContext)
    } else {
      cancelDedicatedHostServiceRecovery(appContext)
      cancelDedicatedHostWatchdog(appContext)
    }
  }

  fun scheduleServiceRecoveryIfNeeded(context: Context) {
    val appContext = context.applicationContext
    if (!shouldKeepAlive(appContext)) {
      cancelDedicatedHostServiceRecovery(appContext)
      cancelDedicatedHostWatchdog(appContext)
      return
    }
    scheduleDedicatedHostServiceRecovery(appContext)
  }

  fun scheduleWatchdogIfNeeded(context: Context) {
    val appContext = context.applicationContext
    if (!shouldKeepAlive(appContext)) {
      cancelDedicatedHostWatchdog(appContext)
      return
    }
    scheduleDedicatedHostWatchdog(appContext)
  }
}
