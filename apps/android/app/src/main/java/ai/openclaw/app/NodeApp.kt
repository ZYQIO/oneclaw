package ai.openclaw.app

import android.app.Application
import android.os.StrictMode
import ai.openclaw.app.auth.OpenAICodexAuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class NodeApp : Application() {
  val prefs: SecurePrefs by lazy { SecurePrefs(this) }
  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  internal val openAICodexAuthManager: OpenAICodexAuthManager by lazy {
    OpenAICodexAuthManager(
      appContext = this,
      prefs = prefs,
      scope = appScope,
    )
  }

  @Volatile private var runtimeInstance: NodeRuntime? = null

  fun ensureRuntime(): NodeRuntime {
    runtimeInstance?.let { return it }
    return synchronized(this) {
      runtimeInstance ?: NodeRuntime(this, prefs).also { runtimeInstance = it }
    }
  }

  fun peekRuntime(): NodeRuntime? = runtimeInstance

  override fun onCreate() {
    super.onCreate()
    if (BuildConfig.DEBUG) {
      StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy.Builder()
          .detectAll()
          .penaltyLog()
          .build(),
      )
      StrictMode.setVmPolicy(
        StrictMode.VmPolicy.Builder()
          .detectAll()
          .penaltyLog()
          .build(),
      )
    }
  }
}
