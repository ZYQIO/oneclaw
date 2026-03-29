package ai.openclaw.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import ai.openclaw.app.node.Quad
import ai.openclaw.app.ui.localizeConnectionStatus
import ai.openclaw.app.ui.pick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NodeForegroundService : Service() {
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var notificationJob: Job? = null
  private var keepAliveJob: Job? = null
  private var didStartForeground = false

  override fun onCreate() {
    super.onCreate()
    cancelDedicatedHostServiceRecovery(applicationContext)
    LocalHostDedicatedDeploymentManager.scheduleWatchdogIfNeeded(applicationContext)

    val app = application as NodeApp
    val initialLanguage = app.prefs.appLanguage.value
    ensureChannel(language = initialLanguage)
    val initial =
      buildNotification(
        language = initialLanguage,
        title = foregroundNotificationTitle(initialLanguage, connected = false),
        text = foregroundNotificationStartingText(initialLanguage),
      )
    startForegroundWithTypes(notification = initial)
    val runtime =
      when {
        app.prefs.onboardingCompleted.value -> app.ensureRuntime()
        else -> app.peekRuntime()
      }
    if (runtime == null) {
      stopSelf()
      return
    }
    notificationJob =
      scope.launch {
        val languageAndStatus =
          combine(
            app.prefs.appLanguage,
            runtime.statusText,
          ) { language, status -> NotificationLanguageStatus(language, status) }
        combine(
          languageAndStatus,
          runtime.serverName,
          runtime.isConnected,
          runtime.micEnabled,
          runtime.micIsListening,
        ) { languageStatus, server, connected, micEnabled, micListening ->
          NotificationState(
            language = languageStatus.language,
            status = languageStatus.status,
            server = server,
            connected = connected,
            micEnabled = micEnabled,
            micListening = micListening,
          )
        }.collect { state ->
          val title = foregroundNotificationTitle(state.language, state.connected)
          val text =
            foregroundNotificationText(
              language = state.language,
              statusText = state.status,
              serverName = state.server,
              micEnabled = state.micEnabled,
              micListening = state.micListening,
            )

          ensureChannel(language = state.language)
          startForegroundWithTypes(
            notification = buildNotification(language = state.language, title = title, text = text),
          )
        }
      }
    keepAliveJob =
      scope.launch {
        combine(
          app.prefs.localHostDedicatedDeploymentEnabled,
          app.prefs.onboardingCompleted,
          app.prefs.gatewayConnectionMode,
          runtime.isConnected,
        ) { dedicatedEnabled, onboardingCompleted, connectionMode, connected ->
          Quad(dedicatedEnabled, onboardingCompleted, connectionMode, connected)
        }.collectLatest { (dedicatedEnabled, onboardingCompleted, connectionMode, connected) ->
          if (!dedicatedEnabled || !onboardingCompleted || connectionMode != GatewayConnectionMode.LocalHost || connected) {
            return@collectLatest
          }
          delay(dedicatedReconnectDelayMs)
          if (
            app.prefs.localHostDedicatedDeploymentEnabled.value &&
            app.prefs.onboardingCompleted.value &&
            app.prefs.gatewayConnectionMode.value == GatewayConnectionMode.LocalHost &&
            !runtime.isConnected.value
          ) {
            runtime.setForeground(false)
            runtime.refreshGatewayConnection()
          }
        }
      }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        (application as NodeApp).peekRuntime()?.disconnect()
        stopSelf()
        return START_NOT_STICKY
      }
    }
    LocalHostDedicatedDeploymentManager.scheduleWatchdogIfNeeded(applicationContext)
    // Keep running; connection is managed by NodeRuntime (auto-reconnect + manual).
    return START_STICKY
  }

  override fun onDestroy() {
    notificationJob?.cancel()
    keepAliveJob?.cancel()
    scope.cancel()
    LocalHostDedicatedDeploymentManager.scheduleServiceRecoveryIfNeeded(applicationContext)
    super.onDestroy()
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    LocalHostDedicatedDeploymentManager.scheduleServiceRecoveryIfNeeded(applicationContext)
    super.onTaskRemoved(rootIntent)
  }

  override fun onBind(intent: Intent?) = null

  private fun ensureChannel(language: AppLanguage) {
    val mgr = getSystemService(NotificationManager::class.java)
    val channel =
      NotificationChannel(
        CHANNEL_ID,
        foregroundNotificationChannelName(language),
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = foregroundNotificationChannelDescription(language)
        setShowBadge(false)
      }
    mgr.createNotificationChannel(channel)
  }

  private fun buildNotification(language: AppLanguage, title: String, text: String): Notification {
    val launchIntent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val launchPending =
      PendingIntent.getActivity(
        this,
        1,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val stopIntent = Intent(this, NodeForegroundService::class.java).setAction(ACTION_STOP)
    val stopPending =
      PendingIntent.getService(
        this,
        2,
        stopIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(title)
      .setContentText(text)
      .setContentIntent(launchPending)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      .addAction(0, foregroundNotificationActionLabel(language), stopPending)
      .build()
  }

  private fun updateNotification(notification: Notification) {
    val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    mgr.notify(NOTIFICATION_ID, notification)
  }

  private fun startForegroundWithTypes(notification: Notification) {
    if (didStartForeground) {
      updateNotification(notification)
      return
    }
    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    didStartForeground = true
  }

  companion object {
    private const val CHANNEL_ID = "connection"
    private const val NOTIFICATION_ID = 1
    private const val dedicatedReconnectDelayMs = 3_000L

    private const val ACTION_STOP = "ai.openclaw.app.action.STOP"

    fun start(context: Context) {
      val intent = Intent(context, NodeForegroundService::class.java)
      context.startForegroundService(intent)
    }

    fun stop(context: Context) {
      val intent = Intent(context, NodeForegroundService::class.java).setAction(ACTION_STOP)
      context.startService(intent)
    }
  }
}

internal fun foregroundNotificationActionLabel(language: AppLanguage): String =
  language.pick("Disconnect", "断开连接")

internal fun foregroundNotificationChannelDescription(language: AppLanguage): String =
  language.pick("OpenClaw node connection status", "OpenClaw 节点连接状态")

internal fun foregroundNotificationChannelName(language: AppLanguage): String =
  language.pick("Connection", "连接")

internal fun foregroundNotificationMicSuffix(
  language: AppLanguage,
  micEnabled: Boolean,
  micListening: Boolean,
): String {
  if (!micEnabled) return ""
  return if (micListening) {
    language.pick(" · Mic: Listening", " · 麦克风：监听中")
  } else {
    language.pick(" · Mic: Pending", " · 麦克风：待命")
  }
}

internal fun foregroundNotificationStartingText(language: AppLanguage): String =
  language.pick("Starting…", "启动中…")

internal fun foregroundNotificationText(
  language: AppLanguage,
  statusText: String,
  serverName: String?,
  micEnabled: Boolean,
  micListening: Boolean,
): String {
  val localizedStatus = localizeConnectionStatus(language, statusText)
  val base = serverName?.let { "$localizedStatus · $it" } ?: localizedStatus
  return base + foregroundNotificationMicSuffix(language, micEnabled, micListening)
}

internal fun foregroundNotificationTitle(
  language: AppLanguage,
  connected: Boolean,
): String {
  return if (connected) {
    language.pick("OpenClaw Node · Connected", "OpenClaw 节点 · 已连接")
  } else {
    language.pick("OpenClaw Node", "OpenClaw 节点")
  }
}

private data class NotificationLanguageStatus(
  val language: AppLanguage,
  val status: String,
)

private data class NotificationState(
  val language: AppLanguage,
  val status: String,
  val server: String?,
  val connected: Boolean,
  val micEnabled: Boolean,
  val micListening: Boolean,
)
