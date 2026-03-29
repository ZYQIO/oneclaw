package ai.openclaw.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import java.io.File

internal object DebugLocalHostTokenExporter {
  const val exportAction: String = "ai.openclaw.app.action.EXPORT_LOCAL_HOST_TOKEN"
  const val cacheFileName: String = "debug-local-host-token.txt"
  const val receiverPermission: String = android.Manifest.permission.DUMP
  const val resultOk: Int = 1
  const val resultError: Int = 0

  private val tokenRegex = Regex("""^ocrt_[0-9a-f]{64}$""")

  fun register(context: Context, prefsProvider: () -> SecurePrefs): BroadcastReceiver {
    val receiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          if (intent.action != exportAction) return
          try {
            val token = validateToken(prefsProvider().localHostRemoteAccessToken.value)
            val file = writeTokenSnapshot(context.cacheDir, token)
            setResultCode(resultOk)
            setResultData(file.absolutePath)
          } catch (error: IllegalArgumentException) {
            setResultCode(resultError)
            setResultData(error.message ?: "Failed to export local-host token.")
          }
        }
      }
    val filter = IntentFilter(exportAction)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.registerReceiver(
        receiver,
        filter,
        receiverPermission,
        null,
        Context.RECEIVER_EXPORTED,
      )
    } else {
      @Suppress("DEPRECATION")
      context.registerReceiver(receiver, filter, receiverPermission, null)
    }
    return receiver
  }

  fun writeTokenSnapshot(cacheDir: File, token: String): File {
    val normalized = validateToken(token)
    cacheDir.mkdirs()
    val file = File(cacheDir, cacheFileName)
    file.writeText("$normalized\n")
    return file
  }

  fun validateToken(token: String): String {
    val trimmed = token.trim()
    require(tokenRegex.matches(trimmed)) { "Invalid local-host remote access token." }
    return trimmed
  }
}
