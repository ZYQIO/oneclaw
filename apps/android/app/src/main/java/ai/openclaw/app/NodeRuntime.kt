package ai.openclaw.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import ai.openclaw.app.accessibility.localHostUiAutomationActiveWindowSnapshot
import ai.openclaw.app.accessibility.localHostUiAutomationStatusSnapshot
import ai.openclaw.app.chat.ChatController
import ai.openclaw.app.chat.ChatMessage
import ai.openclaw.app.chat.ChatPendingToolCall
import ai.openclaw.app.chat.ChatSessionEntry
import ai.openclaw.app.chat.OutgoingAttachment
import ai.openclaw.app.gateway.DeviceAuthStore
import ai.openclaw.app.gateway.DeviceIdentityStore
import ai.openclaw.app.gateway.GatewayDiscovery
import ai.openclaw.app.gateway.GatewayEndpoint
import ai.openclaw.app.gateway.LocalGatewaySession
import ai.openclaw.app.gateway.MutableGatewayRpcClient
import ai.openclaw.app.gateway.MutableNodeGatewayRpcClient
import ai.openclaw.app.gateway.GatewaySession
import ai.openclaw.app.gateway.probeGatewayTlsFingerprint
import ai.openclaw.app.host.CompositeLocalHostToolBridge
import ai.openclaw.app.host.LocalHostNodesToolBridge
import ai.openclaw.app.host.LocalHostRemoteAccessServer
import ai.openclaw.app.host.LocalHostRuntime
import ai.openclaw.app.host.LocalHostWorkspaceToolBridge
import ai.openclaw.app.host.OpenAICodexResponsesClient
import ai.openclaw.app.node.*
import ai.openclaw.app.protocol.OpenClawCanvasA2UIAction
import ai.openclaw.app.voice.MicCaptureManager
import ai.openclaw.app.voice.TalkModeManager
import ai.openclaw.app.voice.VoiceConversationEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class NodeRuntime(
  context: Context,
  val prefs: SecurePrefs = SecurePrefs(context.applicationContext),
) {
  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val deviceAuthStore = DeviceAuthStore(prefs)
  val canvas = CanvasController()
  val camera = CameraCaptureManager(appContext)
  val location = LocationCaptureManager(appContext)
  val sms = SmsManager(appContext)
  private val json = Json { ignoreUnknownKeys = true }
  private val operatorRpcClient = MutableGatewayRpcClient()
  private val nodeRpcClient = MutableNodeGatewayRpcClient()

  private val externalAudioCaptureActive = MutableStateFlow(false)

  private val discovery = GatewayDiscovery(appContext, scope = scope)
  val gateways: StateFlow<List<GatewayEndpoint>> = discovery.gateways
  val discoveryStatusText: StateFlow<String> = discovery.statusText

  private val identityStore = DeviceIdentityStore(appContext)
  private var connectedEndpoint: GatewayEndpoint? = null

  private val cameraHandler: CameraHandler = CameraHandler(
    appContext = appContext,
    camera = camera,
    externalAudioCaptureActive = externalAudioCaptureActive,
    showCameraHud = ::showCameraHud,
    triggerCameraFlash = ::triggerCameraFlash,
    invokeErrorFromThrowable = { invokeErrorFromThrowable(it) },
  )

  private val debugHandler: DebugHandler = DebugHandler(
    appContext = appContext,
    identityStore = identityStore,
  )

  private val locationHandler: LocationHandler = LocationHandler(
    appContext = appContext,
    location = location,
    json = json,
    isForeground = { _isForeground.value },
    locationPreciseEnabled = { locationPreciseEnabled.value },
  )

  private val deviceHandler: DeviceHandler = DeviceHandler(
    appContext = appContext,
  )

  private val notificationsHandler: NotificationsHandler = NotificationsHandler(
    appContext = appContext,
  )

  private val systemHandler: SystemHandler = SystemHandler(
    appContext = appContext,
  )

  private val uiAutomationHandler: UiAutomationHandler = UiAutomationHandler(
    readinessSnapshot = { localHostUiAutomationStatusSnapshot(appContext) },
    activeWindowSnapshot = { localHostUiAutomationActiveWindowSnapshot() },
    launchAppAction = { packageName ->
      UiAutomationHandler.launchAppFromContext(
        appContext = appContext,
        packageName = packageName,
      )
    },
  )

  private val photosHandler: PhotosHandler = PhotosHandler(
    appContext = appContext,
  )

  private val contactsHandler: ContactsHandler = ContactsHandler(
    appContext = appContext,
  )

  private val calendarHandler: CalendarHandler = CalendarHandler(
    appContext = appContext,
  )

  private val callLogHandler: CallLogHandler = CallLogHandler(
    appContext = appContext,
  )

  private val motionHandler: MotionHandler = MotionHandler(
    appContext = appContext,
  )

  private val smsHandlerImpl: SmsHandler = SmsHandler(
    sms = sms,
  )

  private val a2uiHandler: A2UIHandler = A2UIHandler(
    canvas = canvas,
    json = json,
    getNodeCanvasHostUrl = { nodeRpcClient.currentCanvasHostUrl() },
    getOperatorCanvasHostUrl = { operatorRpcClient.currentCanvasHostUrl() },
  )

  private val connectionManager: ConnectionManager = ConnectionManager(
    prefs = prefs,
    cameraEnabled = { cameraEnabled.value },
    locationMode = { locationMode.value },
    voiceWakeMode = { VoiceWakeMode.Off },
    motionActivityAvailable = { motionHandler.isActivityAvailable() },
    motionPedometerAvailable = { motionHandler.isPedometerAvailable() },
    smsAvailable = { sms.canSendSms() },
    hasRecordAudioPermission = { hasRecordAudioPermission() },
    manualTls = { manualTls.value },
  )

  private val invokeDispatcher: InvokeDispatcher = InvokeDispatcher(
    canvas = canvas,
    cameraHandler = cameraHandler,
    locationHandler = locationHandler,
    deviceHandler = deviceHandler,
    notificationsHandler = notificationsHandler,
    systemHandler = systemHandler,
    uiAutomationHandler = uiAutomationHandler,
    photosHandler = photosHandler,
    contactsHandler = contactsHandler,
    calendarHandler = calendarHandler,
    motionHandler = motionHandler,
    smsHandler = smsHandlerImpl,
    a2uiHandler = a2uiHandler,
    debugHandler = debugHandler,
    callLogHandler = callLogHandler,
    isForeground = { _isForeground.value },
    cameraEnabled = { cameraEnabled.value },
    locationEnabled = { locationMode.value != LocationMode.Off },
    smsAvailable = { sms.canSendSms() },
    debugBuild = { BuildConfig.DEBUG },
    refreshNodeCanvasCapability = { nodeRpcClient.refreshNodeCanvasCapability() },
    onCanvasA2uiPush = {
      _canvasA2uiHydrated.value = true
      _canvasRehydratePending.value = false
      _canvasRehydrateErrorText.value = null
    },
    onCanvasA2uiReset = { _canvasA2uiHydrated.value = false },
    motionActivityAvailable = { motionHandler.isActivityAvailable() },
    motionPedometerAvailable = { motionHandler.isPedometerAvailable() },
  )

  private val localHostNodesToolBridge: LocalHostNodesToolBridge = LocalHostNodesToolBridge(
    json = json,
    invoke = { command, paramsJson ->
      invokeDispatcher.handleInvoke(command, paramsJson)
    },
    allowAdvancedRemoteCommands = {
      prefs.localHostRemoteAccessAdvancedCommandsEnabled.value
    },
    allowWriteRemoteCommands = {
      prefs.localHostRemoteAccessWriteCommandsEnabled.value
    },
  )

  private val localHostWorkspaceToolBridge: LocalHostWorkspaceToolBridge = LocalHostWorkspaceToolBridge(
    json = json,
    workspaceRoot = File(appContext.filesDir, "openclaw/local-host-workspace"),
    allowWriteRemoteActions = {
      prefs.localHostRemoteAccessWriteCommandsEnabled.value
    },
  )

  private val localHostCompositeToolBridge: CompositeLocalHostToolBridge = CompositeLocalHostToolBridge(
    listOf(
      localHostNodesToolBridge,
      localHostWorkspaceToolBridge,
    ),
  )

  private val localHostRuntime: LocalHostRuntime = LocalHostRuntime(
    scope = scope,
    prefs = prefs,
    json = json,
    deploymentStatusProvider = {
      dedicatedHostDeploymentStatusSnapshot(appContext, prefs)
    },
    uiAutomationStatusProvider = {
      localHostUiAutomationStatusSnapshot(appContext).toJson()
    },
    codexClient =
      OpenAICodexResponsesClient(
        prefs = prefs,
        json = json,
        toolBridge = localHostCompositeToolBridge,
      ),
  )

  private val localHostRemoteAccessServer: LocalHostRemoteAccessServer = LocalHostRemoteAccessServer(
    scope = scope,
    json = json,
    handleLocalHostRequest = { method, paramsJson, timeoutMs ->
      localHostRuntime.request(
        role = "remote-operator",
        method = method,
        paramsJson = paramsJson,
        timeoutMs = timeoutMs,
      )
    },
    handleInvoke = { command, paramsJson ->
      invokeDispatcher.handleInvoke(command, paramsJson)
    },
    registerEventClient = { onEvent ->
      localHostRuntime.registerClient(
        role = "remote-access-events",
        onEvent = onEvent,
      ).clientId
    },
    unregisterEventClientFn = { clientId ->
      localHostRuntime.unregisterClient(clientId)
    },
    allowAdvancedInvokeCommands = {
      prefs.localHostRemoteAccessAdvancedCommandsEnabled.value
    },
    allowWriteInvokeCommands = {
      prefs.localHostRemoteAccessWriteCommandsEnabled.value
    },
    statusSnapshotProvider = {
      localHostRuntime.statusSnapshot()
    },
    codexAuthStatusProvider = {
      localHostRuntime.codexAuthStatusSnapshot()
    },
    refreshCodexAuth = {
      localHostRuntime.refreshCodexAuthSnapshot()
    },
  )

  private enum class ConnectionBackend {
    Remote,
    Local,
  }

  private data class RemoteAccessConfigSnapshot(
    val mode: GatewayConnectionMode,
    val enabled: Boolean,
    val port: Int,
    val token: String,
  )

  data class GatewayTrustPrompt(
    val endpoint: GatewayEndpoint,
    val fingerprintSha256: String,
  )

  private val _isConnected = MutableStateFlow(false)
  val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
  private val _nodeConnected = MutableStateFlow(false)
  val nodeConnected: StateFlow<Boolean> = _nodeConnected.asStateFlow()

  private val _statusText = MutableStateFlow("Offline")
  val statusText: StateFlow<String> = _statusText.asStateFlow()

  private val _pendingGatewayTrust = MutableStateFlow<GatewayTrustPrompt?>(null)
  val pendingGatewayTrust: StateFlow<GatewayTrustPrompt?> = _pendingGatewayTrust.asStateFlow()

  private val _mainSessionKey = MutableStateFlow("main")
  val mainSessionKey: StateFlow<String> = _mainSessionKey.asStateFlow()

  private val _localHostRemoteAccessStatusText = MutableStateFlow("Remote access is off.")
  val localHostRemoteAccessStatusText: StateFlow<String> = _localHostRemoteAccessStatusText.asStateFlow()

  private val _localHostRemoteAccessUrl = MutableStateFlow<String?>(null)
  val localHostRemoteAccessUrl: StateFlow<String?> = _localHostRemoteAccessUrl.asStateFlow()

  private val cameraHudSeq = AtomicLong(0)
  private val _cameraHud = MutableStateFlow<CameraHudState?>(null)
  val cameraHud: StateFlow<CameraHudState?> = _cameraHud.asStateFlow()

  private val _cameraFlashToken = MutableStateFlow(0L)
  val cameraFlashToken: StateFlow<Long> = _cameraFlashToken.asStateFlow()

  private val _canvasA2uiHydrated = MutableStateFlow(false)
  val canvasA2uiHydrated: StateFlow<Boolean> = _canvasA2uiHydrated.asStateFlow()
  private val _canvasRehydratePending = MutableStateFlow(false)
  val canvasRehydratePending: StateFlow<Boolean> = _canvasRehydratePending.asStateFlow()
  private val _canvasRehydrateErrorText = MutableStateFlow<String?>(null)
  val canvasRehydrateErrorText: StateFlow<String?> = _canvasRehydrateErrorText.asStateFlow()

  private val _serverName = MutableStateFlow<String?>(null)
  val serverName: StateFlow<String?> = _serverName.asStateFlow()

  private val _remoteAddress = MutableStateFlow<String?>(null)
  val remoteAddress: StateFlow<String?> = _remoteAddress.asStateFlow()

  private val _seamColorArgb = MutableStateFlow(DEFAULT_SEAM_COLOR_ARGB)
  val seamColorArgb: StateFlow<Long> = _seamColorArgb.asStateFlow()

  private val _isForeground = MutableStateFlow(false)
  val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

  private var gatewayDefaultAgentId: String? = null
  private var gatewayAgents: List<GatewayAgentSummary> = emptyList()
  private var didAutoRequestCanvasRehydrate = false
  private val canvasRehydrateSeq = AtomicLong(0)
  private var operatorConnected = false
  private var operatorStatusText: String = "Offline"
  private var nodeStatusText: String = "Offline"
  @Volatile private var activeConnectionBackend: ConnectionBackend = ConnectionBackend.Remote

  private val operatorSession =
    GatewaySession(
      scope = scope,
      identityStore = identityStore,
      deviceAuthStore = deviceAuthStore,
      onConnected = { name, remote, mainSessionKey ->
        handleOperatorConnected(ConnectionBackend.Remote, name, remote, mainSessionKey)
      },
      onDisconnected = { message ->
        handleOperatorDisconnected(ConnectionBackend.Remote, message)
      },
      onEvent = { event, payloadJson -> handleGatewayEvent(event, payloadJson) },
    )

  private val nodeSession =
    GatewaySession(
      scope = scope,
      identityStore = identityStore,
      deviceAuthStore = deviceAuthStore,
      onConnected = { _, _, _ -> handleNodeConnected(ConnectionBackend.Remote) },
      onDisconnected = { message -> handleNodeDisconnected(ConnectionBackend.Remote, message) },
      onEvent = { _, _ -> },
      onInvoke = { req ->
        invokeDispatcher.handleInvoke(req.command, req.paramsJson)
      },
      onTlsFingerprint = { stableId, fingerprint ->
        prefs.saveGatewayTlsFingerprint(stableId, fingerprint)
      },
    )

  private val localOperatorSession =
    LocalGatewaySession(
      scope = scope,
      runtime = localHostRuntime,
      role = "operator",
      onConnected = { name, remote, mainSessionKey ->
        handleOperatorConnected(ConnectionBackend.Local, name, remote, mainSessionKey)
      },
      onDisconnected = { message ->
        handleOperatorDisconnected(ConnectionBackend.Local, message)
      },
      onEvent = { event, payloadJson -> handleGatewayEvent(event, payloadJson) },
    )

  private val localNodeSession =
    LocalGatewaySession(
      scope = scope,
      runtime = localHostRuntime,
      role = "node",
      onConnected = { _, _, _ -> handleNodeConnected(ConnectionBackend.Local) },
      onDisconnected = { message -> handleNodeDisconnected(ConnectionBackend.Local, message) },
      onEvent = { _, _ -> },
    )

  init {
    operatorRpcClient.setDelegate(operatorSession)
    nodeRpcClient.setDelegate(nodeSession)
    DeviceNotificationListenerService.setNodeEventSink { event, payloadJson ->
      scope.launch {
        nodeRpcClient.sendNodeEvent(event = event, payloadJson = payloadJson)
      }
    }
  }

  private val chat: ChatController =
    ChatController(
      scope = scope,
      session = operatorRpcClient,
      json = json,
      supportsChatSubscribe = false,
    )
  private val voiceReplySpeakerLazy: Lazy<TalkModeManager> = lazy {
    // Reuse the existing TalkMode speech engine (ElevenLabs + deterministic system-TTS fallback)
    // without enabling the legacy talk capture loop.
    TalkModeManager(
      context = appContext,
      scope = scope,
      session = operatorRpcClient,
      supportsChatSubscribe = false,
      isConnected = { operatorConnected },
    ).also { speaker ->
      speaker.setPlaybackEnabled(prefs.speakerEnabled.value)
    }
  }
  private val voiceReplySpeaker: TalkModeManager
    get() = voiceReplySpeakerLazy.value

  private val micCapture: MicCaptureManager by lazy {
    MicCaptureManager(
      context = appContext,
      scope = scope,
      sendToGateway = { message, onRunIdKnown ->
        val idempotencyKey = UUID.randomUUID().toString()
        // Notify MicCaptureManager of the idempotency key *before* the network
        // call so pendingRunId is set before any chat events can arrive.
        onRunIdKnown(idempotencyKey)
        val params =
          buildJsonObject {
            put("sessionKey", JsonPrimitive(resolveMainSessionKey()))
            put("message", JsonPrimitive(message))
            put("thinking", JsonPrimitive(chatThinkingLevel.value))
            put("timeoutMs", JsonPrimitive(30_000))
            put("idempotencyKey", JsonPrimitive(idempotencyKey))
          }
        val response = operatorRpcClient.request("chat.send", params.toString())
        parseChatSendRunId(response) ?: idempotencyKey
      },
      speakAssistantReply = { text ->
        // Skip if TalkModeManager is handling TTS (ttsOnAllResponses) to avoid
        // double-speaking the same assistant reply from both pipelines.
        if (!talkMode.ttsOnAllResponses) {
          voiceReplySpeaker.speakAssistantReply(text)
        }
      },
    )
  }

  val micStatusText: StateFlow<String>
    get() = micCapture.statusText

  val talkStatusText: StateFlow<String>
    get() = talkMode.statusText

  val micLiveTranscript: StateFlow<String?>
    get() = micCapture.liveTranscript

  val micIsListening: StateFlow<Boolean>
    get() = micCapture.isListening

  val micEnabled: StateFlow<Boolean>
    get() = micCapture.micEnabled

  val micCooldown: StateFlow<Boolean>
    get() = micCapture.micCooldown

  val micQueuedMessages: StateFlow<List<String>>
    get() = micCapture.queuedMessages

  val micConversation: StateFlow<List<VoiceConversationEntry>>
    get() = micCapture.conversation

  val micInputLevel: StateFlow<Float>
    get() = micCapture.inputLevel

  val micIsSending: StateFlow<Boolean>
    get() = micCapture.isSending

  private val talkMode: TalkModeManager by lazy {
    TalkModeManager(
      context = appContext,
      scope = scope,
      session = operatorRpcClient,
      supportsChatSubscribe = true,
      isConnected = { operatorConnected },
    )
  }

  private fun handleOperatorConnected(
    backend: ConnectionBackend,
    name: String?,
    remote: String?,
    mainSessionKey: String?,
  ) {
    if (backend != activeConnectionBackend) return
    operatorConnected = true
    operatorStatusText = "Connected"
    _serverName.value = name
    _remoteAddress.value = remote
    _seamColorArgb.value = DEFAULT_SEAM_COLOR_ARGB
    applyMainSessionKey(mainSessionKey)
    updateStatus()
    micCapture.onGatewayConnectionChanged(true)
    scope.launch {
      refreshHomeCanvasOverviewIfConnected()
      if (voiceReplySpeakerLazy.isInitialized()) {
        voiceReplySpeaker.refreshConfig()
      }
    }
  }

  private fun handleOperatorDisconnected(
    backend: ConnectionBackend,
    message: String,
  ) {
    if (backend != activeConnectionBackend) return
    resetOperatorState(message)
    updateStatus()
  }

  private fun handleNodeConnected(backend: ConnectionBackend) {
    if (backend != activeConnectionBackend) return
    _nodeConnected.value = true
    nodeStatusText = "Connected"
    didAutoRequestCanvasRehydrate = false
    _canvasA2uiHydrated.value = false
    _canvasRehydratePending.value = false
    _canvasRehydrateErrorText.value = null
    updateStatus()
    showLocalCanvasOnConnect()
  }

  private fun handleNodeDisconnected(
    backend: ConnectionBackend,
    message: String,
  ) {
    if (backend != activeConnectionBackend) return
    resetNodeState(message)
    updateStatus()
  }

  private fun resetOperatorState(message: String) {
    operatorConnected = false
    operatorStatusText = message
    _serverName.value = null
    _remoteAddress.value = null
    _seamColorArgb.value = DEFAULT_SEAM_COLOR_ARGB
    if (!isCanonicalMainSessionKey(_mainSessionKey.value)) {
      _mainSessionKey.value = "main"
    }
    chat.applyMainSessionKey(resolveMainSessionKey())
    chat.onDisconnected(message)
    micCapture.onGatewayConnectionChanged(false)
  }

  private fun resetNodeState(message: String) {
    _nodeConnected.value = false
    nodeStatusText = message
    didAutoRequestCanvasRehydrate = false
    _canvasA2uiHydrated.value = false
    _canvasRehydratePending.value = false
    _canvasRehydrateErrorText.value = null
    showLocalCanvasOnDisconnect()
  }

  private fun prepareBackendSwitch(
    backend: ConnectionBackend,
    operatorMessage: String,
    nodeMessage: String,
  ) {
    activeConnectionBackend = backend
    resetOperatorState(operatorMessage)
    resetNodeState(nodeMessage)
    updateStatus()
  }

  private fun applyMainSessionKey(candidate: String?) {
    val trimmed = normalizeMainKey(candidate) ?: return
    if (isCanonicalMainSessionKey(_mainSessionKey.value)) return
    if (_mainSessionKey.value == trimmed) return
    _mainSessionKey.value = trimmed
    talkMode.setMainSessionKey(trimmed)
    chat.applyMainSessionKey(trimmed)
    updateHomeCanvasState()
  }

  private fun updateStatus() {
    _isConnected.value = operatorConnected
    val operator = operatorStatusText.trim()
    val node = nodeStatusText.trim()
    _statusText.value =
      when {
        operatorConnected && _nodeConnected.value -> "Connected"
        operatorConnected && !_nodeConnected.value -> "Connected (node offline)"
        !operatorConnected && _nodeConnected.value ->
          if (operator.isNotEmpty() && operator != "Offline") {
            "Connected (operator: $operator)"
          } else {
            "Connected (operator offline)"
          }
        operator.isNotBlank() && operator != "Offline" -> operator
        else -> node
      }
    updateHomeCanvasState()
  }

  private fun resolveMainSessionKey(): String {
    val trimmed = _mainSessionKey.value.trim()
    return if (trimmed.isEmpty()) "main" else trimmed
  }

  private fun showLocalCanvasOnConnect() {
    _canvasA2uiHydrated.value = false
    _canvasRehydratePending.value = false
    _canvasRehydrateErrorText.value = null
    canvas.navigate("")
  }

  private fun showLocalCanvasOnDisconnect() {
    _canvasA2uiHydrated.value = false
    _canvasRehydratePending.value = false
    _canvasRehydrateErrorText.value = null
    canvas.navigate("")
  }

  fun refreshHomeCanvasOverviewIfConnected() {
    if (!operatorConnected) {
      updateHomeCanvasState()
      return
    }
    scope.launch {
      refreshBrandingFromGateway()
      refreshAgentsFromGateway()
    }
  }

  fun requestCanvasRehydrate(source: String = "manual", force: Boolean = true) {
    scope.launch {
      if (!_nodeConnected.value) {
        _canvasRehydratePending.value = false
        _canvasRehydrateErrorText.value = "Node offline. Reconnect and retry."
        return@launch
      }
      if (!force && didAutoRequestCanvasRehydrate) return@launch
      didAutoRequestCanvasRehydrate = true
      val requestId = canvasRehydrateSeq.incrementAndGet()
      _canvasRehydratePending.value = true
      _canvasRehydrateErrorText.value = null

      val sessionKey = resolveMainSessionKey()
      val prompt =
        "Restore canvas now for session=$sessionKey source=$source. " +
          "If existing A2UI state exists, replay it immediately. " +
          "If not, create and render a compact mobile-friendly dashboard in Canvas."
      val sent =
        nodeRpcClient.sendNodeEvent(
          event = "agent.request",
          payloadJson =
            buildJsonObject {
              put("message", JsonPrimitive(prompt))
              put("sessionKey", JsonPrimitive(sessionKey))
              put("thinking", JsonPrimitive("low"))
              put("deliver", JsonPrimitive(false))
            }.toString(),
        )
      if (!sent) {
        if (!force) {
          didAutoRequestCanvasRehydrate = false
        }
        if (canvasRehydrateSeq.get() == requestId) {
          _canvasRehydratePending.value = false
          _canvasRehydrateErrorText.value = "Failed to request restore. Tap to retry."
        }
        Log.w("OpenClawCanvas", "canvas rehydrate request failed ($source): transport unavailable")
        return@launch
      }
      scope.launch {
        delay(20_000)
        if (canvasRehydrateSeq.get() != requestId) return@launch
        if (!_canvasRehydratePending.value) return@launch
        if (_canvasA2uiHydrated.value) return@launch
        _canvasRehydratePending.value = false
        _canvasRehydrateErrorText.value = "No canvas update yet. Tap to retry."
      }
    }
  }

  val instanceId: StateFlow<String> = prefs.instanceId
  val displayName: StateFlow<String> = prefs.displayName
  val cameraEnabled: StateFlow<Boolean> = prefs.cameraEnabled
  val locationMode: StateFlow<LocationMode> = prefs.locationMode
  val locationPreciseEnabled: StateFlow<Boolean> = prefs.locationPreciseEnabled
  val preventSleep: StateFlow<Boolean> = prefs.preventSleep
  val manualEnabled: StateFlow<Boolean> = prefs.manualEnabled
  val manualHost: StateFlow<String> = prefs.manualHost
  val manualPort: StateFlow<Int> = prefs.manualPort
  val manualTls: StateFlow<Boolean> = prefs.manualTls
  val gatewayToken: StateFlow<String> = prefs.gatewayToken
  val onboardingCompleted: StateFlow<Boolean> = prefs.onboardingCompleted
  fun setGatewayToken(value: String) = prefs.setGatewayToken(value)
  fun setGatewayBootstrapToken(value: String) = prefs.setGatewayBootstrapToken(value)
  fun setGatewayPassword(value: String) = prefs.setGatewayPassword(value)
  fun setOnboardingCompleted(value: Boolean) = prefs.setOnboardingCompleted(value)
  val lastDiscoveredStableId: StateFlow<String> = prefs.lastDiscoveredStableId
  val canvasDebugStatusEnabled: StateFlow<Boolean> = prefs.canvasDebugStatusEnabled

  private var didAutoConnect = false

  val chatSessionKey: StateFlow<String> = chat.sessionKey
  val chatSessionId: StateFlow<String?> = chat.sessionId
  val chatMessages: StateFlow<List<ChatMessage>> = chat.messages
  val chatError: StateFlow<String?> = chat.errorText
  val chatHealthOk: StateFlow<Boolean> = chat.healthOk
  val chatThinkingLevel: StateFlow<String> = chat.thinkingLevel
  val chatStreamingAssistantText: StateFlow<String?> = chat.streamingAssistantText
  val chatPendingToolCalls: StateFlow<List<ChatPendingToolCall>> = chat.pendingToolCalls
  val chatSessions: StateFlow<List<ChatSessionEntry>> = chat.sessions
  val pendingRunCount: StateFlow<Int> = chat.pendingRunCount

  init {
    if (prefs.voiceWakeMode.value != VoiceWakeMode.Off) {
      prefs.setVoiceWakeMode(VoiceWakeMode.Off)
    }

    scope.launch {
      prefs.loadGatewayToken()
    }

    scope.launch {
      localHostRemoteAccessServer.state.collect { state ->
        _localHostRemoteAccessStatusText.value = state.statusText
        _localHostRemoteAccessUrl.value = state.listenUrl
      }
    }

    scope.launch {
      prefs.talkEnabled.collect { enabled ->
        // MicCaptureManager handles STT + send to gateway.
        // TalkModeManager plays TTS on assistant responses.
        micCapture.setMicEnabled(enabled)
        if (enabled) {
          // Mic on = user is on voice screen and wants TTS responses.
          talkMode.ttsOnAllResponses = true
          scope.launch { talkMode.ensureChatSubscribed() }
        }
        externalAudioCaptureActive.value = enabled
      }
    }

    scope.launch {
      combine(
        prefs.gatewayConnectionMode,
        prefs.localHostRemoteAccessEnabled,
        prefs.localHostRemoteAccessPort,
        prefs.localHostRemoteAccessToken,
      ) { mode, enabled, port, token ->
        RemoteAccessConfigSnapshot(
          mode = mode,
          enabled = enabled,
          port = port,
          token = token,
        )
      }.distinctUntilChanged()
        .collect { config ->
          when {
            !config.enabled ->
              localHostRemoteAccessServer.stop()
            config.mode != GatewayConnectionMode.LocalHost ->
              localHostRemoteAccessServer.stop(statusText = "Switch to Local Host to accept remote connections.")
            else ->
              localHostRemoteAccessServer.start(
                port = config.port,
                token = config.token,
              )
          }
        }
    }

    scope.launch(Dispatchers.Default) {
      gateways.collect { list ->
        if (prefs.gatewayConnectionMode.value == GatewayConnectionMode.LocalHost) {
          if (didAutoConnect) return@collect
          if (_isConnected.value) return@collect
          didAutoConnect = true
          connectLocalHost()
          return@collect
        }

        if (list.isNotEmpty()) {
          // Security: don't let an unauthenticated discovery feed continuously steer autoconnect.
          // UX parity with iOS: only set once when unset.
          if (lastDiscoveredStableId.value.trim().isEmpty()) {
            prefs.setLastDiscoveredStableId(list.first().stableId)
          }
        }

        if (didAutoConnect) return@collect
        if (_isConnected.value) return@collect

        if (manualEnabled.value) {
          val host = manualHost.value.trim()
          val port = manualPort.value
          if (host.isNotEmpty() && port in 1..65535) {
            // Security: autoconnect only to previously trusted gateways (stored TLS pin).
            if (!manualTls.value) return@collect
            val stableId = GatewayEndpoint.manual(host = host, port = port).stableId
            val storedFingerprint = prefs.loadGatewayTlsFingerprint(stableId)?.trim().orEmpty()
            if (storedFingerprint.isEmpty()) return@collect

            didAutoConnect = true
            connect(GatewayEndpoint.manual(host = host, port = port))
          }
          return@collect
        }

        val targetStableId = lastDiscoveredStableId.value.trim()
        if (targetStableId.isEmpty()) return@collect
        val target = list.firstOrNull { it.stableId == targetStableId } ?: return@collect

        // Security: autoconnect only to previously trusted gateways (stored TLS pin).
        val storedFingerprint = prefs.loadGatewayTlsFingerprint(target.stableId)?.trim().orEmpty()
        if (storedFingerprint.isEmpty()) return@collect

        didAutoConnect = true
        connect(target)
      }
    }

    scope.launch {
      combine(
        canvasDebugStatusEnabled,
        statusText,
        serverName,
        remoteAddress,
      ) { debugEnabled, status, server, remote ->
        Quad(debugEnabled, status, server, remote)
      }.distinctUntilChanged()
        .collect { (debugEnabled, status, server, remote) ->
          canvas.setDebugStatusEnabled(debugEnabled)
          if (!debugEnabled) return@collect
          canvas.setDebugStatus(status, server ?: remote)
        }
    }

    updateHomeCanvasState()
  }

  fun setForeground(value: Boolean) {
    _isForeground.value = value
    if (!value) {
      stopActiveVoiceSession()
    }
  }

  fun setDisplayName(value: String) {
    prefs.setDisplayName(value)
  }

  fun setCameraEnabled(value: Boolean) {
    prefs.setCameraEnabled(value)
  }

  fun setLocationMode(mode: LocationMode) {
    prefs.setLocationMode(mode)
  }

  fun setLocationPreciseEnabled(value: Boolean) {
    prefs.setLocationPreciseEnabled(value)
  }

  fun setPreventSleep(value: Boolean) {
    prefs.setPreventSleep(value)
  }

  fun setManualEnabled(value: Boolean) {
    prefs.setManualEnabled(value)
  }

  fun setManualHost(value: String) {
    prefs.setManualHost(value)
  }

  fun setManualPort(value: Int) {
    prefs.setManualPort(value)
  }

  fun setManualTls(value: Boolean) {
    prefs.setManualTls(value)
  }

  fun setCanvasDebugStatusEnabled(value: Boolean) {
    prefs.setCanvasDebugStatusEnabled(value)
  }

  fun setVoiceScreenActive(active: Boolean) {
    if (!active) {
      stopActiveVoiceSession()
    }
    // Don't re-enable on active=true; mic toggle drives that
  }

  fun setMicEnabled(value: Boolean) {
    prefs.setTalkEnabled(value)
    if (value) {
      // Tapping mic on interrupts any active TTS (barge-in)
      talkMode.stopTts()
      talkMode.ttsOnAllResponses = true
      scope.launch { talkMode.ensureChatSubscribed() }
    }
    micCapture.setMicEnabled(value)
    externalAudioCaptureActive.value = value
  }

  val speakerEnabled: StateFlow<Boolean>
    get() = prefs.speakerEnabled

  fun setSpeakerEnabled(value: Boolean) {
    prefs.setSpeakerEnabled(value)
    if (voiceReplySpeakerLazy.isInitialized()) {
      voiceReplySpeaker.setPlaybackEnabled(value)
    }
    // Keep TalkMode in sync so speaker mute works when ttsOnAllResponses is active.
    talkMode.setPlaybackEnabled(value)
  }

  private fun stopActiveVoiceSession() {
    talkMode.ttsOnAllResponses = false
    talkMode.stopTts()
    micCapture.setMicEnabled(false)
    prefs.setTalkEnabled(false)
    externalAudioCaptureActive.value = false
  }

  fun refreshGatewayConnection() {
    if (prefs.gatewayConnectionMode.value == GatewayConnectionMode.LocalHost) {
      connectLocalHost(forceReconnect = true)
      return
    }
    prepareBackendSwitch(
      backend = ConnectionBackend.Remote,
      operatorMessage = "Connecting…",
      nodeMessage = "Connecting…",
    )
    operatorRpcClient.setDelegate(operatorSession)
    nodeRpcClient.setDelegate(nodeSession)
    localOperatorSession.disconnect()
    localNodeSession.disconnect()
    val endpoint =
      connectedEndpoint ?: run {
        _statusText.value = "Failed: no cached gateway endpoint"
        return
      }
    val token = prefs.loadGatewayToken()
    val bootstrapToken = prefs.loadGatewayBootstrapToken()
    val password = prefs.loadGatewayPassword()
    val tls = connectionManager.resolveTlsParams(endpoint)
    operatorSession.connect(
      endpoint,
      token,
      bootstrapToken,
      password,
      connectionManager.buildOperatorConnectOptions(),
      tls,
    )
    nodeSession.connect(
      endpoint,
      token,
      bootstrapToken,
      password,
      connectionManager.buildNodeConnectOptions(),
      tls,
    )
    operatorSession.reconnect()
    nodeSession.reconnect()
  }

  fun connect(endpoint: GatewayEndpoint) {
    if (prefs.gatewayConnectionMode.value == GatewayConnectionMode.LocalHost) {
      connectLocalHost()
      return
    }
    val tls = connectionManager.resolveTlsParams(endpoint)
    if (tls?.required == true && tls.expectedFingerprint.isNullOrBlank()) {
      // First-time TLS: capture fingerprint, ask user to verify out-of-band, then store and connect.
      _statusText.value = "Verify gateway TLS fingerprint…"
      scope.launch {
        val fp = probeGatewayTlsFingerprint(endpoint.host, endpoint.port) ?: run {
          _statusText.value = "Failed: can't read TLS fingerprint"
          return@launch
        }
        _pendingGatewayTrust.value = GatewayTrustPrompt(endpoint = endpoint, fingerprintSha256 = fp)
      }
      return
    }

    prepareBackendSwitch(
      backend = ConnectionBackend.Remote,
      operatorMessage = "Connecting…",
      nodeMessage = "Connecting…",
    )
    operatorRpcClient.setDelegate(operatorSession)
    nodeRpcClient.setDelegate(nodeSession)
    localOperatorSession.disconnect()
    localNodeSession.disconnect()
    connectedEndpoint = endpoint
    val token = prefs.loadGatewayToken()
    val bootstrapToken = prefs.loadGatewayBootstrapToken()
    val password = prefs.loadGatewayPassword()
    operatorSession.connect(
      endpoint,
      token,
      bootstrapToken,
      password,
      connectionManager.buildOperatorConnectOptions(),
      tls,
    )
    nodeSession.connect(
      endpoint,
      token,
      bootstrapToken,
      password,
      connectionManager.buildNodeConnectOptions(),
      tls,
    )
  }

  private fun connectLocalHost(forceReconnect: Boolean = false) {
    prepareBackendSwitch(
      backend = ConnectionBackend.Local,
      operatorMessage = "Starting local host…",
      nodeMessage = "Starting local host…",
    )
    connectedEndpoint = GatewayEndpoint.manual(host = "127.0.0.1", port = 18789)
    _pendingGatewayTrust.value = null
    operatorRpcClient.setDelegate(localOperatorSession)
    nodeRpcClient.setDelegate(localNodeSession)
    operatorSession.disconnect()
    nodeSession.disconnect()
    if (forceReconnect) {
      localOperatorSession.reconnect()
      localNodeSession.reconnect()
    } else {
      localOperatorSession.connect()
      localNodeSession.connect()
    }
  }

  fun acceptGatewayTrustPrompt() {
    val prompt = _pendingGatewayTrust.value ?: return
    _pendingGatewayTrust.value = null
    prefs.saveGatewayTlsFingerprint(prompt.endpoint.stableId, prompt.fingerprintSha256)
    connect(prompt.endpoint)
  }

  fun declineGatewayTrustPrompt() {
    _pendingGatewayTrust.value = null
    _statusText.value = "Offline"
  }

  private fun hasRecordAudioPermission(): Boolean {
    return (
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
      )
  }

  fun connectManual() {
    if (prefs.gatewayConnectionMode.value == GatewayConnectionMode.LocalHost) {
      connectLocalHost()
      return
    }
    val host = manualHost.value.trim()
    val port = manualPort.value
    if (host.isEmpty() || port <= 0 || port > 65535) {
      _statusText.value = "Failed: invalid manual host/port"
      return
    }
    connect(GatewayEndpoint.manual(host = host, port = port))
  }

  fun disconnect() {
    connectedEndpoint = null
    _pendingGatewayTrust.value = null
    if (activeConnectionBackend == ConnectionBackend.Local) {
      localOperatorSession.disconnect()
      localNodeSession.disconnect()
      return
    }
    operatorSession.disconnect()
    nodeSession.disconnect()
  }

  fun handleCanvasA2UIActionFromWebView(payloadJson: String) {
    scope.launch {
      val trimmed = payloadJson.trim()
      if (trimmed.isEmpty()) return@launch

      val root =
        try {
          json.parseToJsonElement(trimmed).asObjectOrNull() ?: return@launch
        } catch (_: Throwable) {
          return@launch
        }

      val userActionObj = (root["userAction"] as? JsonObject) ?: root
      val actionId = (userActionObj["id"] as? JsonPrimitive)?.content?.trim().orEmpty().ifEmpty {
        java.util.UUID.randomUUID().toString()
      }
      val name = OpenClawCanvasA2UIAction.extractActionName(userActionObj) ?: return@launch

      val surfaceId =
        (userActionObj["surfaceId"] as? JsonPrimitive)?.content?.trim().orEmpty().ifEmpty { "main" }
      val sourceComponentId =
        (userActionObj["sourceComponentId"] as? JsonPrimitive)?.content?.trim().orEmpty().ifEmpty { "-" }
      val contextJson = (userActionObj["context"] as? JsonObject)?.toString()

      val sessionKey = resolveMainSessionKey()
      val message =
        OpenClawCanvasA2UIAction.formatAgentMessage(
          actionName = name,
          sessionKey = sessionKey,
          surfaceId = surfaceId,
          sourceComponentId = sourceComponentId,
          host = displayName.value,
          instanceId = instanceId.value.lowercase(),
          contextJson = contextJson,
        )

      val connected = _nodeConnected.value
      var error: String? = null
      if (connected) {
        val sent =
          nodeRpcClient.sendNodeEvent(
            event = "agent.request",
            payloadJson =
              buildJsonObject {
                put("message", JsonPrimitive(message))
                put("sessionKey", JsonPrimitive(sessionKey))
                put("thinking", JsonPrimitive("low"))
                put("deliver", JsonPrimitive(false))
                put("key", JsonPrimitive(actionId))
              }.toString(),
          )
        if (!sent) {
          error = "send failed"
        }
      } else {
        error = "gateway not connected"
      }

      try {
        canvas.eval(
          OpenClawCanvasA2UIAction.jsDispatchA2UIActionStatus(
            actionId = actionId,
            ok = connected && error == null,
            error = error,
          ),
        )
      } catch (_: Throwable) {
        // ignore
      }
    }
  }

  fun loadChat(sessionKey: String) {
    val key = sessionKey.trim().ifEmpty { resolveMainSessionKey() }
    chat.load(key)
  }

  fun refreshChat() {
    chat.refresh()
  }

  fun refreshChatSessions(limit: Int? = null) {
    chat.refreshSessions(limit = limit)
  }

  fun setChatThinkingLevel(level: String) {
    chat.setThinkingLevel(level)
  }

  fun switchChatSession(sessionKey: String) {
    chat.switchSession(sessionKey)
  }

  fun abortChat() {
    chat.abort()
  }

  fun sendChat(message: String, thinking: String, attachments: List<OutgoingAttachment>) {
    chat.sendMessage(message = message, thinkingLevel = thinking, attachments = attachments)
  }

  private fun handleGatewayEvent(event: String, payloadJson: String?) {
    micCapture.handleGatewayEvent(event, payloadJson)
    talkMode.handleGatewayEvent(event, payloadJson)
    chat.handleGatewayEvent(event, payloadJson)
  }

  private fun parseChatSendRunId(response: String): String? {
    return try {
      val root = json.parseToJsonElement(response).asObjectOrNull() ?: return null
      root["runId"].asStringOrNull()
    } catch (_: Throwable) {
      null
    }
  }

  private suspend fun refreshBrandingFromGateway() {
    if (!_isConnected.value) return
    try {
      val res = operatorRpcClient.request("config.get", "{}")
      val root = json.parseToJsonElement(res).asObjectOrNull()
      val config = root?.get("config").asObjectOrNull()
      val ui = config?.get("ui").asObjectOrNull()
      val raw = ui?.get("seamColor").asStringOrNull()?.trim()
      val sessionCfg = config?.get("session").asObjectOrNull()
      val mainKey = normalizeMainKey(sessionCfg?.get("mainKey").asStringOrNull())
      applyMainSessionKey(mainKey)

      val parsed = parseHexColorArgb(raw)
      _seamColorArgb.value = parsed ?: DEFAULT_SEAM_COLOR_ARGB
      updateHomeCanvasState()
    } catch (_: Throwable) {
      // ignore
    }
  }

  private suspend fun refreshAgentsFromGateway() {
    if (!operatorConnected) return
    try {
      val res = operatorRpcClient.request("agents.list", "{}")
      val root = json.parseToJsonElement(res).asObjectOrNull() ?: return
      val defaultAgentId = root["defaultId"].asStringOrNull()?.trim().orEmpty()
      val mainKey = normalizeMainKey(root["mainKey"].asStringOrNull())
      val agents =
        (root["agents"] as? JsonArray)?.mapNotNull { item ->
          val obj = item.asObjectOrNull() ?: return@mapNotNull null
          val id = obj["id"].asStringOrNull()?.trim().orEmpty()
          if (id.isEmpty()) return@mapNotNull null
          val name = obj["name"].asStringOrNull()?.trim()
          val emoji = obj["identity"].asObjectOrNull()?.get("emoji").asStringOrNull()?.trim()
          GatewayAgentSummary(
            id = id,
            name = name?.takeIf { it.isNotEmpty() },
            emoji = emoji?.takeIf { it.isNotEmpty() },
          )
        } ?: emptyList()

      gatewayDefaultAgentId = defaultAgentId.ifEmpty { null }
      gatewayAgents = agents
      applyMainSessionKey(mainKey)
      updateHomeCanvasState()
    } catch (_: Throwable) {
      // ignore
    }
  }

  private fun updateHomeCanvasState() {
    val payload =
      try {
        json.encodeToString(makeHomeCanvasPayload())
      } catch (_: Throwable) {
        null
      }
    canvas.updateHomeCanvasState(payload)
  }

  private fun makeHomeCanvasPayload(): HomeCanvasPayload {
    val state = resolveHomeCanvasGatewayState()
    val gatewayName = normalized(_serverName.value)
    val gatewayAddress = normalized(_remoteAddress.value)
    val gatewayLabel = gatewayName ?: gatewayAddress ?: "Gateway"
    val activeAgentId = resolveActiveAgentId()
    val agents = homeCanvasAgents(activeAgentId)

    return when (state) {
      HomeCanvasGatewayState.Connected ->
        HomeCanvasPayload(
          gatewayState = "connected",
          eyebrow = "Connected to $gatewayLabel",
          title = "Your agents are ready",
          subtitle =
            "This phone stays dormant until the gateway needs it, then wakes, syncs, and goes back to sleep.",
          gatewayLabel = gatewayLabel,
          activeAgentName = resolveActiveAgentName(activeAgentId),
          activeAgentBadge = agents.firstOrNull { it.isActive }?.badge ?: "OC",
          activeAgentCaption = "Selected on this phone",
          agentCount = agents.size,
          agents = agents.take(6),
          footer = "The overview refreshes on reconnect and when this screen opens.",
        )
      HomeCanvasGatewayState.Connecting ->
        HomeCanvasPayload(
          gatewayState = "connecting",
          eyebrow = "Reconnecting",
          title = "OpenClaw is syncing back up",
          subtitle =
            "The gateway session is coming back online. Agent shortcuts should settle automatically in a moment.",
          gatewayLabel = gatewayLabel,
          activeAgentName = resolveActiveAgentName(activeAgentId),
          activeAgentBadge = "OC",
          activeAgentCaption = "Gateway session in progress",
          agentCount = agents.size,
          agents = agents.take(4),
          footer = "If the gateway is reachable, reconnect should complete without intervention.",
        )
      HomeCanvasGatewayState.Error, HomeCanvasGatewayState.Offline ->
        HomeCanvasPayload(
          gatewayState = if (state == HomeCanvasGatewayState.Error) "error" else "offline",
          eyebrow = "Welcome to OpenClaw",
          title = "Your phone stays quiet until it is needed",
          subtitle =
            "Pair this device to your gateway to wake it only for real work, keep a live agent overview handy, and avoid battery-draining background loops.",
          gatewayLabel = gatewayLabel,
          activeAgentName = "Main",
          activeAgentBadge = "OC",
          activeAgentCaption = "Connect to load your agents",
          agentCount = agents.size,
          agents = agents.take(4),
          footer = "When connected, the gateway can wake the phone with a silent push instead of holding an always-on session.",
        )
    }
  }

  private fun resolveHomeCanvasGatewayState(): HomeCanvasGatewayState {
    val lower = _statusText.value.trim().lowercase()
    return when {
      _isConnected.value -> HomeCanvasGatewayState.Connected
      lower.contains("connecting") || lower.contains("reconnecting") -> HomeCanvasGatewayState.Connecting
      lower.contains("error") || lower.contains("failed") -> HomeCanvasGatewayState.Error
      else -> HomeCanvasGatewayState.Offline
    }
  }

  private fun resolveActiveAgentId(): String {
    val mainKey = _mainSessionKey.value.trim()
    if (mainKey.startsWith("agent:")) {
      val agentId = mainKey.removePrefix("agent:").substringBefore(':').trim()
      if (agentId.isNotEmpty()) return agentId
    }
    return gatewayDefaultAgentId?.trim().orEmpty()
  }

  private fun resolveActiveAgentName(activeAgentId: String): String {
    if (activeAgentId.isNotEmpty()) {
      gatewayAgents.firstOrNull { it.id == activeAgentId }?.let { agent ->
        return normalized(agent.name) ?: agent.id
      }
      return activeAgentId
    }
    return gatewayAgents.firstOrNull()?.let { normalized(it.name) ?: it.id } ?: "Main"
  }

  private fun homeCanvasAgents(activeAgentId: String): List<HomeCanvasAgentCard> {
    val defaultAgentId = gatewayDefaultAgentId?.trim().orEmpty()
    return gatewayAgents
      .map { agent ->
        val isActive = activeAgentId.isNotEmpty() && agent.id == activeAgentId
        val isDefault = defaultAgentId.isNotEmpty() && agent.id == defaultAgentId
        HomeCanvasAgentCard(
          id = agent.id,
          name = normalized(agent.name) ?: agent.id,
          badge = homeCanvasBadge(agent),
          caption =
            when {
              isActive -> "Active on this phone"
              isDefault -> "Default agent"
              else -> "Ready"
            },
          isActive = isActive,
        )
      }.sortedWith(compareByDescending<HomeCanvasAgentCard> { it.isActive }.thenBy { it.name.lowercase() })
  }

  private fun homeCanvasBadge(agent: GatewayAgentSummary): String {
    val emoji = normalized(agent.emoji)
    if (emoji != null) return emoji
    val initials =
      (normalized(agent.name) ?: agent.id)
        .split(' ', '-', '_')
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { token -> token.firstOrNull()?.uppercaseChar()?.toString() }
        .joinToString("")
    return if (initials.isNotEmpty()) initials else "OC"
  }

  private fun normalized(value: String?): String? {
    val trimmed = value?.trim().orEmpty()
    return trimmed.ifEmpty { null }
  }

  private fun triggerCameraFlash() {
    // Token is used as a pulse trigger; value doesn't matter as long as it changes.
    _cameraFlashToken.value = SystemClock.elapsedRealtimeNanos()
  }

  private fun showCameraHud(message: String, kind: CameraHudKind, autoHideMs: Long? = null) {
    val token = cameraHudSeq.incrementAndGet()
    _cameraHud.value = CameraHudState(token = token, kind = kind, message = message)

    if (autoHideMs != null && autoHideMs > 0) {
      scope.launch {
        delay(autoHideMs)
        if (_cameraHud.value?.token == token) _cameraHud.value = null
      }
    }
  }

}

private enum class HomeCanvasGatewayState {
  Connected,
  Connecting,
  Error,
  Offline,
}

private data class GatewayAgentSummary(
  val id: String,
  val name: String?,
  val emoji: String?,
)

@Serializable
private data class HomeCanvasPayload(
  val gatewayState: String,
  val eyebrow: String,
  val title: String,
  val subtitle: String,
  val gatewayLabel: String,
  val activeAgentName: String,
  val activeAgentBadge: String,
  val activeAgentCaption: String,
  val agentCount: Int,
  val agents: List<HomeCanvasAgentCard>,
  val footer: String,
)

@Serializable
private data class HomeCanvasAgentCard(
  val id: String,
  val name: String,
  val badge: String,
  val caption: String,
  val isActive: Boolean,
)
