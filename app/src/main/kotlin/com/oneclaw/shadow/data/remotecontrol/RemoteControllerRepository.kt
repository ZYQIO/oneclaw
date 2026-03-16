package com.oneclaw.shadow.data.remotecontrol

import android.util.Base64
import com.oneclaw.remote.core.model.RemoteControlCommand
import com.oneclaw.remote.core.model.RemoteDevice
import com.oneclaw.remote.core.model.RemoteFileAction
import com.oneclaw.remote.core.model.RemoteFileCommand
import com.oneclaw.remote.core.model.RemoteFileEntry
import com.oneclaw.remote.core.model.RemoteSession
import com.oneclaw.remote.core.model.RemoteSnapshot
import com.oneclaw.remote.core.protocol.BrokerEnvelope
import com.oneclaw.remote.core.protocol.BrokerMessageType
import com.oneclaw.remote.core.protocol.RemoteProtocolJson
import com.oneclaw.remote.core.protocol.jsonObjectOf
import com.oneclaw.remote.core.protocol.jsonString
import com.oneclaw.remote.core.protocol.stringOrNull
import com.oneclaw.remote.core.session.RemoteBrokerClient
import com.oneclaw.remote.core.session.RemoteBrokerConnectionState
import com.oneclaw.shadow.core.repository.RemoteControllerGateway
import com.oneclaw.shadow.core.repository.SettingsRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.core.util.ErrorCode
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.OkHttpClient

class RemoteControllerRepository(
    private val settingsRepository: SettingsRepository,
    okHttpClient: OkHttpClient
) : RemoteControllerGateway {

    private val json = RemoteProtocolJson.json
    private val brokerClient = RemoteBrokerClient(okHttpClient, json)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingResponses = ConcurrentHashMap<String, CompletableDeferred<BrokerEnvelope>>()

    private val _devices = MutableStateFlow<List<RemoteDevice>>(emptyList())
    override val devices: StateFlow<List<RemoteDevice>> = _devices.asStateFlow()

    private val _lastSnapshot = MutableStateFlow<RemoteSnapshot?>(null)
    override val lastSnapshot: StateFlow<RemoteSnapshot?> = _lastSnapshot.asStateFlow()

    private val _status = MutableStateFlow("Idle")
    override val status: StateFlow<String> = _status.asStateFlow()

    private val _activeSessions = MutableStateFlow<Map<String, RemoteSession>>(emptyMap())
    override val activeSessions: StateFlow<Map<String, RemoteSession>> = _activeSessions.asStateFlow()

    init {
        scope.launch {
            brokerClient.connectionState.collectLatest { state ->
                _status.value = "Broker ${state.name.lowercase()}"
                if (state == RemoteBrokerConnectionState.CONNECTED) {
                    registerController()
                    refreshDevices()
                }
            }
        }
        scope.launch {
            brokerClient.events.collectLatest { envelope ->
                handleEnvelope(envelope)
            }
        }
    }

    override suspend fun getBrokerUrl(): String =
        settingsRepository.getString(KEY_BROKER_URL) ?: DEFAULT_BROKER_URL

    override suspend fun setBrokerUrl(url: String) {
        settingsRepository.setString(KEY_BROKER_URL, url)
        brokerClient.close()
        _status.value = "Broker URL updated"
    }

    override suspend fun connect(): AppResult<Unit> {
        if (brokerClient.connectionState.value == RemoteBrokerConnectionState.CONNECTED) {
            return AppResult.Success(Unit)
        }
        val brokerUrl = getBrokerUrl()
        brokerClient.connect(brokerUrl)
        repeat(50) {
            when (brokerClient.connectionState.value) {
                RemoteBrokerConnectionState.CONNECTED -> return AppResult.Success(Unit)
                RemoteBrokerConnectionState.ERROR,
                RemoteBrokerConnectionState.CLOSED -> {
                    return AppResult.Error(
                        message = "Failed to connect to broker at $brokerUrl",
                        code = ErrorCode.NETWORK_ERROR
                    )
                }
                else -> delay(100)
            }
        }
        return AppResult.Error(
            message = "Timed out while connecting to broker at $brokerUrl",
            code = ErrorCode.TIMEOUT_ERROR
        )
    }

    override fun disconnect() {
        brokerClient.close()
        _status.value = "Disconnected"
    }

    override suspend fun pairDevice(deviceId: String, pairCode: String): AppResult<Unit> {
        val controllerId = controllerId()
        val connectResult = connect()
        if (connectResult is AppResult.Error) return connectResult
        val response = requestResponse(
            BrokerEnvelope(
                type = BrokerMessageType.PAIR_REQUEST,
                senderId = controllerId,
                targetId = deviceId,
                deviceId = deviceId,
                payload = jsonObjectOf("pairCode" to jsonString(pairCode))
            )
        )
        return response.map { Unit }
    }

    override suspend fun refreshDevices(): AppResult<List<RemoteDevice>> {
        val controllerId = controllerId()
        val connectResult = connect()
        if (connectResult is AppResult.Error) return connectResult
        val response = requestResponse(
            BrokerEnvelope(
                type = BrokerMessageType.DEVICE_LIST_REQUEST,
                senderId = controllerId
            )
        )
        return when (response) {
            is AppResult.Success -> {
                val devices = response.data.payload["devices"]?.let {
                    json.decodeFromJsonElement<List<RemoteDevice>>(it)
                }.orEmpty()
                _devices.value = devices
                AppResult.Success(devices)
            }
            is AppResult.Error -> response
        }
    }

    override suspend fun openSession(deviceId: String, source: String): AppResult<RemoteSession> {
        val controllerId = controllerId()
        val connectResult = connect()
        if (connectResult is AppResult.Error) return connectResult
        val response = requestResponse(
            BrokerEnvelope(
                type = BrokerMessageType.SESSION_OPEN,
                senderId = controllerId,
                targetId = deviceId,
                deviceId = deviceId,
                payload = jsonObjectOf("source" to jsonString(source))
            )
        )
        return when (response) {
            is AppResult.Success -> {
                val session = response.data.payload["session"]?.let {
                    json.decodeFromJsonElement<RemoteSession>(it)
                } ?: return AppResult.Error(message = "Broker did not return a session.", code = ErrorCode.UNKNOWN)
                _activeSessions.value = _activeSessions.value + (deviceId to session)
                AppResult.Success(session)
            }
            is AppResult.Error -> response
        }
    }

    override suspend fun closeSession(deviceId: String): AppResult<Unit> {
        val session = _activeSessions.value[deviceId]
            ?: return AppResult.Error(message = "No active session for device $deviceId", code = ErrorCode.NOT_FOUND)
        val response = requestResponse(
            BrokerEnvelope(
                type = BrokerMessageType.SESSION_CLOSE,
                senderId = controllerId(),
                targetId = deviceId,
                deviceId = deviceId,
                sessionId = session.sessionId
            )
        )
        return response.map {
            _activeSessions.value = _activeSessions.value - deviceId
            Unit
        }
    }

    override suspend fun requestSnapshot(deviceId: String): AppResult<RemoteSnapshot> {
        val session = requireSession(deviceId) ?: return AppResult.Error(
            message = "Open a session before requesting snapshots.",
            code = ErrorCode.VALIDATION_ERROR
        )
        val response = requestResponse(
            BrokerEnvelope(
                type = BrokerMessageType.SESSION_SNAPSHOT_REQUEST,
                senderId = controllerId(),
                targetId = deviceId,
                deviceId = deviceId,
                sessionId = session.sessionId
            ),
            timeoutMs = 20_000
        )
        return when (response) {
            is AppResult.Success -> {
                val snapshot = response.data.payload["snapshot"]?.let {
                    json.decodeFromJsonElement<RemoteSnapshot>(it)
                } ?: return AppResult.Error(message = "Snapshot missing in response.", code = ErrorCode.UNKNOWN)
                _lastSnapshot.value = snapshot
                AppResult.Success(snapshot)
            }
            is AppResult.Error -> response
        }
    }

    override suspend fun sendControl(
        deviceId: String,
        command: RemoteControlCommand,
        source: String
    ): AppResult<String> {
        val session = requireSession(deviceId) ?: return AppResult.Error(
            message = "Open a session before sending control commands.",
            code = ErrorCode.VALIDATION_ERROR
        )
        val response = requestResponse(
            BrokerEnvelope(
                type = BrokerMessageType.SESSION_CONTROL,
                senderId = controllerId(),
                targetId = deviceId,
                deviceId = deviceId,
                sessionId = session.sessionId,
                payload = jsonObjectOf(
                    "command" to json.encodeToJsonElement(command),
                    "source" to jsonString(source)
                )
            )
        )
        return when (response) {
            is AppResult.Success -> AppResult.Success(response.data.payload.stringOrNull("message") ?: "OK")
            is AppResult.Error -> response
        }
    }

    override suspend fun listFiles(deviceId: String, path: String): AppResult<List<RemoteFileEntry>> {
        val response = fileRequest(
            deviceId = deviceId,
            command = RemoteFileCommand(action = RemoteFileAction.LIST, path = path)
        )
        return when (response) {
            is AppResult.Success -> {
                val entries = response.data.payload["entries"]?.let {
                    json.decodeFromJsonElement<List<RemoteFileEntry>>(it)
                }.orEmpty()
                AppResult.Success(entries)
            }
            is AppResult.Error -> response
        }
    }

    override suspend fun pushFile(deviceId: String, localPath: String, remotePath: String): AppResult<String> {
        val file = File(localPath)
        if (!file.exists() || !file.isFile) {
            return AppResult.Error(message = "Local file does not exist: $localPath", code = ErrorCode.NOT_FOUND)
        }
        val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        val response = fileRequest(
            deviceId = deviceId,
            command = RemoteFileCommand(
                action = RemoteFileAction.UPLOAD,
                targetPath = remotePath,
                base64Data = encoded
            ),
            timeoutMs = 25_000
        )
        return when (response) {
            is AppResult.Success -> AppResult.Success(response.data.payload.stringOrNull("message") ?: "Uploaded")
            is AppResult.Error -> response
        }
    }

    override suspend fun pullFile(deviceId: String, remotePath: String, localPath: String): AppResult<String> {
        val response = fileRequest(
            deviceId = deviceId,
            command = RemoteFileCommand(action = RemoteFileAction.DOWNLOAD, path = remotePath),
            timeoutMs = 25_000
        )
        return when (response) {
            is AppResult.Success -> {
                val base64Data = response.data.payload.stringOrNull("base64Data")
                    ?: return AppResult.Error(message = "Downloaded file data missing.", code = ErrorCode.UNKNOWN)
                val target = File(localPath)
                target.parentFile?.mkdirs()
                target.writeBytes(Base64.decode(base64Data, Base64.DEFAULT))
                AppResult.Success("Saved file to ${target.absolutePath}")
            }
            is AppResult.Error -> response
        }
    }

    private suspend fun fileRequest(
        deviceId: String,
        command: RemoteFileCommand,
        timeoutMs: Long = 15_000
    ): AppResult<BrokerEnvelope> {
        val session = requireSession(deviceId) ?: return AppResult.Error(
            message = "Open a session before transferring files.",
            code = ErrorCode.VALIDATION_ERROR
        )
        return requestResponse(
            BrokerEnvelope(
                type = BrokerMessageType.SESSION_FILE_META,
                senderId = controllerId(),
                targetId = deviceId,
                deviceId = deviceId,
                sessionId = session.sessionId,
                payload = jsonObjectOf("command" to json.encodeToJsonElement(command))
            ),
            timeoutMs = timeoutMs
        )
    }

    private suspend fun requestResponse(
        envelope: BrokerEnvelope,
        timeoutMs: Long = 10_000
    ): AppResult<BrokerEnvelope> {
        val requestId = envelope.requestId ?: UUID.randomUUID().toString()
        val pending = CompletableDeferred<BrokerEnvelope>()
        pendingResponses[requestId] = pending
        val outbound = envelope.copy(requestId = requestId)
        if (!brokerClient.send(outbound)) {
            pendingResponses.remove(requestId)
            return AppResult.Error(message = "Broker socket is not available.", code = ErrorCode.NETWORK_ERROR)
        }
        return try {
            val response = withTimeout(timeoutMs) { pending.await() }
            if (response.type == BrokerMessageType.ERROR) {
                AppResult.Error(
                    message = response.payload.stringOrNull("message") ?: "Remote operation failed.",
                    code = ErrorCode.TOOL_ERROR
                )
            } else {
                AppResult.Success(response)
            }
        } catch (_: Exception) {
            AppResult.Error(message = "Timed out waiting for remote response.", code = ErrorCode.TIMEOUT_ERROR)
        } finally {
            pendingResponses.remove(requestId)
        }
    }

    private suspend fun handleEnvelope(envelope: BrokerEnvelope) {
        when (envelope.type) {
            BrokerMessageType.DEVICE_LIST_RESPONSE -> {
                val list = envelope.payload["devices"]?.let {
                    json.decodeFromJsonElement<List<RemoteDevice>>(it)
                }.orEmpty()
                _devices.value = list
            }
            BrokerMessageType.SESSION_OPENED -> {
                envelope.payload["session"]?.let {
                    val session = json.decodeFromJsonElement<RemoteSession>(it)
                    _activeSessions.value = _activeSessions.value + (session.deviceId to session)
                }
            }
            BrokerMessageType.SESSION_CLOSED -> {
                envelope.deviceId?.let { deviceId ->
                    _activeSessions.value = _activeSessions.value - deviceId
                }
            }
            BrokerMessageType.SESSION_SNAPSHOT_RESPONSE -> {
                envelope.payload["snapshot"]?.let {
                    _lastSnapshot.value = json.decodeFromJsonElement<RemoteSnapshot>(it)
                }
            }
        }
        envelope.requestId?.let { requestId ->
            pendingResponses.remove(requestId)?.complete(envelope)
        }
    }

    private suspend fun registerController() {
        brokerClient.send(
            BrokerEnvelope(
                type = BrokerMessageType.CONTROLLER_REGISTER,
                senderId = controllerId()
            )
        )
    }

    private suspend fun controllerId(): String {
        val existing = settingsRepository.getString(KEY_CONTROLLER_ID)
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val generated = "controller-${UUID.randomUUID()}"
        settingsRepository.setString(KEY_CONTROLLER_ID, generated)
        return generated
    }

    private fun requireSession(deviceId: String): RemoteSession? = _activeSessions.value[deviceId]

    companion object {
        private const val KEY_BROKER_URL = "remote_broker_url"
        private const val KEY_CONTROLLER_ID = "remote_controller_id"
        private const val DEFAULT_BROKER_URL = "ws://10.0.2.2:8080/ws"
    }
}
