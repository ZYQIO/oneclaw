package ai.openclaw.app

import android.content.pm.PackageManager
import android.content.Intent
import android.Manifest
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import ai.openclaw.app.ui.pick
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class PermissionRequester(
  private val activity: ComponentActivity,
  private val currentLanguage: () -> AppLanguage = { AppLanguage.English },
) {
  private val mutex = Mutex()
  private var pending: CompletableDeferred<Map<String, Boolean>>? = null

  private val launcher: ActivityResultLauncher<Array<String>> =
    activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
      val p = pending
      pending = null
      p?.complete(result)
    }

  suspend fun requestIfMissing(
    permissions: List<String>,
    timeoutMs: Long = 20_000,
  ): Map<String, Boolean> =
    mutex.withLock {
      val missing =
        permissions.filter { perm ->
          ContextCompat.checkSelfPermission(activity, perm) != PackageManager.PERMISSION_GRANTED
        }
      if (missing.isEmpty()) {
        return permissions.associateWith { true }
      }

      val needsRationale =
        missing.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
      if (needsRationale) {
        val proceed = showRationaleDialog(missing)
        if (!proceed) {
          return permissions.associateWith { perm ->
            ContextCompat.checkSelfPermission(activity, perm) == PackageManager.PERMISSION_GRANTED
          }
        }
      }

      val deferred = CompletableDeferred<Map<String, Boolean>>()
      pending = deferred
      withContext(Dispatchers.Main) {
        launcher.launch(missing.toTypedArray())
      }

      val result =
        withContext(Dispatchers.Default) {
          kotlinx.coroutines.withTimeout(timeoutMs) { deferred.await() }
        }

      // Merge: if something was already granted, treat it as granted even if launcher omitted it.
      val merged =
        permissions.associateWith { perm ->
        val nowGranted =
          ContextCompat.checkSelfPermission(activity, perm) == PackageManager.PERMISSION_GRANTED
        result[perm] == true || nowGranted
      }

      val denied =
        merged.filterValues { !it }.keys.filter {
          !ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }
      if (denied.isNotEmpty()) {
        showSettingsDialog(denied)
      }

      return merged
    }

  private suspend fun showRationaleDialog(permissions: List<String>): Boolean =
    withContext(Dispatchers.Main) {
      suspendCancellableCoroutine { cont ->
        val language = currentLanguage()
        AlertDialog.Builder(activity)
          .setTitle(permissionRationaleTitle(language))
          .setMessage(buildRationaleMessage(language, permissions))
          .setPositiveButton(permissionContinueLabel(language)) { _, _ -> cont.resume(true) }
          .setNegativeButton(permissionNotNowLabel(language)) { _, _ -> cont.resume(false) }
          .setOnCancelListener { cont.resume(false) }
          .show()
      }
    }

  private fun showSettingsDialog(permissions: List<String>) {
    val language = currentLanguage()
    AlertDialog.Builder(activity)
      .setTitle(permissionSettingsTitle(language))
      .setMessage(buildSettingsMessage(language, permissions))
      .setPositiveButton(permissionOpenSettingsLabel(language)) { _, _ ->
        val intent =
          Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", activity.packageName, null),
          )
        activity.startActivity(intent)
      }
      .setNegativeButton(permissionCancelLabel(language), null)
      .show()
  }

  private fun buildRationaleMessage(language: AppLanguage, permissions: List<String>): String {
    return permissionRationaleMessage(language, permissions)
  }

  private fun buildSettingsMessage(language: AppLanguage, permissions: List<String>): String {
    return permissionSettingsMessage(language, permissions)
  }
}

internal fun permissionContinueLabel(language: AppLanguage): String = language.pick("Continue", "继续")

internal fun permissionNotNowLabel(language: AppLanguage): String = language.pick("Not now", "稍后")

internal fun permissionRationaleTitle(language: AppLanguage): String =
  language.pick("Permission required", "需要权限")

internal fun permissionSettingsTitle(language: AppLanguage): String =
  language.pick("Enable permission in Settings", "在设置中启用权限")

internal fun permissionOpenSettingsLabel(language: AppLanguage): String =
  language.pick("Open Settings", "打开设置")

internal fun permissionCancelLabel(language: AppLanguage): String = language.pick("Cancel", "取消")

internal fun permissionLabel(
  language: AppLanguage,
  permission: String,
): String =
  when (permission) {
    Manifest.permission.CAMERA -> language.pick("Camera", "相机")
    Manifest.permission.RECORD_AUDIO -> language.pick("Microphone", "麦克风")
    Manifest.permission.SEND_SMS -> language.pick("SMS", "短信")
    else -> permission
  }

internal fun permissionRationaleMessage(
  language: AppLanguage,
  permissions: List<String>,
): String {
  val labels = permissions.map { permissionLabel(language, it) }
  return if (language == AppLanguage.SimplifiedChinese) {
    "OpenClaw 需要${labels.joinToString("、")}权限才能继续。"
  } else {
    "OpenClaw needs ${labels.joinToString(", ")} permissions to continue."
  }
}

internal fun permissionSettingsMessage(
  language: AppLanguage,
  permissions: List<String>,
): String {
  val labels = permissions.map { permissionLabel(language, it) }
  return if (language == AppLanguage.SimplifiedChinese) {
    "请在 Android 设置中启用${labels.joinToString("、")}权限后再继续。"
  } else {
    "Please enable ${labels.joinToString(", ")} in Android Settings to continue."
  }
}
