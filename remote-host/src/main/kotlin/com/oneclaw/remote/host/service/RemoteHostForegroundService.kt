package com.oneclaw.remote.host.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.oneclaw.remote.core.capture.PrivilegedCaptureEngine
import com.oneclaw.remote.core.file.AppFileTransferBackend
import com.oneclaw.remote.core.input.PrivilegedInputInjector
import com.oneclaw.remote.core.model.RemoteCapabilities
import com.oneclaw.remote.core.model.RemoteControlCommand
import com.oneclaw.remote.core.model.RemoteDevice
import com.oneclaw.remote.core.model.RemoteFileAction
import com.oneclaw.remote.core.model.RemoteFileCommand
import com.oneclaw.remote.core.model.RemoteMode
import com.oneclaw.remote.core.protocol.BrokerEnvelope
import com.oneclaw.remote.core.protocol.BrokerMessageType
import com.oneclaw.remote.core.protocol.RemoteProtocolJson
import com.oneclaw.remote.core.protocol.jsonObjectOf
import com.oneclaw.remote.core.protocol.jsonString
import com.oneclaw.remote.core.protocol.stringOrNull
import com.oneclaw.remote.core.session.RemoteBrokerClient
import com.oneclaw.remote.core.session.RemoteBrokerConnectionState
import com.oneclaw.remote.host.storage.HostPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient

class RemoteHostForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = RemoteProtocolJson.json
    private lateinit var preferences: HostPreferencesStore
    private lateinit var brokerClient: RemoteBrokerClient
    private lateinit var inputBackend: PrivilegedInputInjector
    private lateinit var captureBackend: PrivilegedCaptureEngine
    private lateinit var fileBackend: AppFileTransferBackend
    private var heartbeatJob: Job? = null
    private val activeSessionIds = linkedSetOf<String>()
    private var cachedRemoteMode: RemoteMode = RemoteMode.COMPATIBILITY
    private var cachedRootAvailable: Boolean = false

    override fun onCreate() {
        super.onCreate()
        preferences = HostPreferencesStore(this)
        brokerClient = RemoteBrokerClient(OkHttpClient.Builder().build(), json)
        inputBackend = PrivilegedInputInjector()
        captureBackend = PrivilegedCaptureEngine()
        fileBackend = AppFileTransferBackend(this)
        createNotificationChannel()

        scope.launch {
            brokerClient.connectionState.collectLatest { state ->
                HostRuntimeState.update { snapshot ->
                    snapshot.copy(
                        serviceRunning = true,
                        connectionState = state.name.lowercase(),
                        remoteMode = cachedRemoteMode,
                        rootAvailable = cachedRootAvailable,
                        lastEvent = "Broker state: ${state.name.lowercase()}"
                    )
                }
                updateNotification(state)
                if (state == RemoteBrokerConnectionState.CONNECTED) {
                    registerDevice()
                    startHeartbeat()
                }
            }
        }

        scope.launch {
            brokerClient.events.collectLatest { envelope ->
                when (envelope.type) {
                    BrokerMessageType.SESSION_OPEN -> handleSessionOpened(envelope)
                    BrokerMessageType.SESSION_CLOSE -> handleSessionClosed(envelope)
                    BrokerMessageType.PAIR_CONFIRM -> {
                        HostRuntimeState.update { snapshot ->
                            snapshot.copy(lastEvent = "Paired with controller ${envelope.senderId.orEmpty()}")
                        }
                    }
                    BrokerMessageType.SESSION_CONTROL -> handleControl(envelope)
                    BrokerMessageType.SESSION_SNAPSHOT_REQUEST -> handleSnapshot(envelope)
                    BrokerMessageType.SESSION_FILE_META -> handleFileCommand(envelope)
                    else -> Unit
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                HostRuntimeState.update { snapshot ->
                    snapshot.copy(serviceRunning = true, lastEvent = "Starting foreground service")
                }
                startForeground(NOTIFICATION_ID, buildNotification("Starting"))
                brokerClient.connect(preferences.brokerUrl())
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        heartbeatJob?.cancel()
        brokerClient.close()
        HostRuntimeState.update { HostRuntimeSnapshot() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun registerDevice() {
        val metrics = resources.displayMetrics
        val rootAvailable = inputBackend.isAvailable() && captureBackend.isAvailable()
        val mode = if (rootAvailable) RemoteMode.ROOT else RemoteMode.COMPATIBILITY
        cachedRootAvailable = rootAvailable
        cachedRemoteMode = mode
        val device = RemoteDevice(
            deviceId = preferences.deviceId(),
            name = preferences.deviceName(),
            mode = mode,
            capabilities = RemoteCapabilities(
                video = rootAvailable,
                touch = rootAvailable,
                keyboard = rootAvailable,
                fileTransfer = true,
                unattended = rootAvailable,
                agentControl = preferences.allowAgentControl()
            ),
            lastSeen = System.currentTimeMillis(),
            online = true,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels
        )
        brokerClient.send(
            BrokerEnvelope(
                type = BrokerMessageType.DEVICE_REGISTER,
                senderId = device.deviceId,
                deviceId = device.deviceId,
                payload = jsonObjectOf(
                    "device" to json.encodeToJsonElement(device),
                    "pairCode" to jsonString(preferences.pairCode())
                )
            )
        )
        HostRuntimeState.update { snapshot ->
            snapshot.copy(
                remoteMode = mode,
                rootAvailable = rootAvailable,
                lastEvent = "Registered device ${device.name}"
            )
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                val heartbeatAt = System.currentTimeMillis()
                brokerClient.send(
                    BrokerEnvelope(
                        type = BrokerMessageType.DEVICE_HEARTBEAT,
                        senderId = preferences.deviceId(),
                        deviceId = preferences.deviceId(),
                        payload = jsonObjectOf(
                            "allowAgentControl" to json.encodeToJsonElement(preferences.allowAgentControl())
                        )
                    )
                )
                HostRuntimeState.update { snapshot ->
                    snapshot.copy(
                        lastHeartbeatAt = heartbeatAt,
                        lastEvent = "Heartbeat sent"
                    )
                }
                delay(15_000)
            }
        }
    }

    private fun handleSessionOpened(envelope: BrokerEnvelope) {
        envelope.sessionId?.let(activeSessionIds::add)
        HostRuntimeState.update { snapshot ->
            snapshot.copy(
                activeSessionCount = activeSessionIds.size,
                lastEvent = "Session opened by ${envelope.senderId.orEmpty()}"
            )
        }
        updateNotification(brokerClient.connectionState.value)
    }

    private fun handleSessionClosed(envelope: BrokerEnvelope) {
        envelope.sessionId?.let(activeSessionIds::remove)
        HostRuntimeState.update { snapshot ->
            snapshot.copy(
                activeSessionCount = activeSessionIds.size,
                lastEvent = "Session closed"
            )
        }
        updateNotification(brokerClient.connectionState.value)
    }

    private suspend fun handleControl(envelope: BrokerEnvelope) {
        val source = envelope.payload.stringOrNull("source")
        if (source == "agent" && !preferences.allowAgentControl()) {
            replyError(envelope, "Agent control is disabled on this device.")
            return
        }
        val commandElement: JsonElement = envelope.payload["command"] ?: run {
            replyError(envelope, "Missing command.")
            return
        }
        val command = runCatching {
            json.decodeFromJsonElement<RemoteControlCommand>(commandElement)
        }.getOrElse {
            replyError(envelope, "Invalid control command: ${it.message}")
            return
        }
        val result = inputBackend.inject(command)
        HostRuntimeState.update { snapshot ->
            snapshot.copy(lastEvent = "Control command: ${command.action.name.lowercase()}")
        }
        val responseType = if (result.isSuccess) BrokerMessageType.SESSION_CONTROL_ACK else BrokerMessageType.ERROR
        brokerClient.send(
            BrokerEnvelope(
                type = responseType,
                requestId = envelope.requestId,
                senderId = preferences.deviceId(),
                targetId = envelope.senderId,
                deviceId = envelope.deviceId,
                sessionId = envelope.sessionId,
                payload = jsonObjectOf(
                    "message" to jsonString(result.value ?: result.error.orEmpty())
                )
            )
        )
    }

    private suspend fun handleSnapshot(envelope: BrokerEnvelope) {
        val snapshotResult = captureBackend.snapshot()
        if (!snapshotResult.isSuccess) {
            replyError(envelope, snapshotResult.error ?: "Snapshot failed.")
            return
        }
        val snapshot = snapshotResult.value ?: return replyError(envelope, "Snapshot missing.")
        HostRuntimeState.update { runtime ->
            runtime.copy(lastEvent = "Snapshot captured")
        }
        brokerClient.send(
            BrokerEnvelope(
                type = BrokerMessageType.SESSION_SNAPSHOT_RESPONSE,
                requestId = envelope.requestId,
                senderId = preferences.deviceId(),
                targetId = envelope.senderId,
                deviceId = envelope.deviceId,
                sessionId = envelope.sessionId,
                payload = jsonObjectOf(
                    "snapshot" to json.encodeToJsonElement(snapshot)
                )
            )
        )
    }

    private suspend fun handleFileCommand(envelope: BrokerEnvelope) {
        val commandElement: JsonElement = envelope.payload["command"] ?: run {
            replyError(envelope, "Missing file command.")
            return
        }
        val command = runCatching {
            json.decodeFromJsonElement<RemoteFileCommand>(commandElement)
        }.getOrElse {
            replyError(envelope, "Invalid file command: ${it.message}")
            return
        }
        val payload = when (command.action) {
            RemoteFileAction.LIST -> {
                val result = fileBackend.list(command.path)
                if (!result.isSuccess) {
                    replyError(envelope, result.error ?: "List failed.")
                    return
                }
                jsonObjectOf("entries" to json.encodeToJsonElement(result.value.orEmpty()))
            }
            RemoteFileAction.UPLOAD -> {
                val result = fileBackend.upload(command)
                if (!result.isSuccess) {
                    replyError(envelope, result.error ?: "Upload failed.")
                    return
                }
                jsonObjectOf("message" to jsonString(result.value.orEmpty()))
            }
            RemoteFileAction.DOWNLOAD -> {
                val result = fileBackend.download(command.path)
                if (!result.isSuccess) {
                    replyError(envelope, result.error ?: "Download failed.")
                    return
                }
                jsonObjectOf(
                    "base64Data" to jsonString(result.value.orEmpty()),
                    "path" to jsonString(command.path.orEmpty())
                )
            }
            RemoteFileAction.DELETE -> {
                val result = fileBackend.delete(command.path)
                if (!result.isSuccess) {
                    replyError(envelope, result.error ?: "Delete failed.")
                    return
                }
                jsonObjectOf("message" to jsonString(result.value.orEmpty()))
            }
            RemoteFileAction.MKDIR -> {
                val result = fileBackend.mkdir(command.path)
                if (!result.isSuccess) {
                    replyError(envelope, result.error ?: "mkdir failed.")
                    return
                }
                jsonObjectOf("message" to jsonString(result.value.orEmpty()))
            }
        }
        brokerClient.send(
            BrokerEnvelope(
                type = BrokerMessageType.SESSION_FILE_RESPONSE,
                requestId = envelope.requestId,
                senderId = preferences.deviceId(),
                targetId = envelope.senderId,
                deviceId = envelope.deviceId,
                sessionId = envelope.sessionId,
                payload = payload
            )
        )
        HostRuntimeState.update { snapshot ->
            snapshot.copy(lastEvent = "File command: ${command.action.name.lowercase()}")
        }
    }

    private fun replyError(envelope: BrokerEnvelope, message: String) {
        brokerClient.send(
            BrokerEnvelope(
                type = BrokerMessageType.ERROR,
                requestId = envelope.requestId,
                senderId = preferences.deviceId(),
                targetId = envelope.senderId,
                deviceId = envelope.deviceId,
                sessionId = envelope.sessionId,
                payload = jsonObjectOf("message" to jsonString(message))
            )
        )
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Remote Host",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun buildNotification(stateLabel: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Remote Host")
            .setContentText(
                "Broker: ${preferences.brokerUrl()} | $stateLabel | sessions=${activeSessionIds.size} | mode=${cachedRemoteMode.name.lowercase()}"
            )
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(true)
            .build()

    private fun updateNotification(state: RemoteBrokerConnectionState) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(state.name.lowercase()))
    }

    companion object {
        const val ACTION_START = "com.oneclaw.remote.host.START"
        const val ACTION_STOP = "com.oneclaw.remote.host.STOP"

        private const val CHANNEL_ID = "remote_host_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
