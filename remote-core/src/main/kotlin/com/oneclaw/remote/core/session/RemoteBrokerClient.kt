package com.oneclaw.remote.core.session

import com.oneclaw.remote.core.protocol.BrokerEnvelope
import com.oneclaw.remote.core.protocol.RemoteProtocolJson
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

enum class RemoteBrokerConnectionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    CLOSED,
    ERROR
}

class RemoteBrokerClient(
    private val okHttpClient: OkHttpClient,
    private val protocolJson: kotlinx.serialization.json.Json = RemoteProtocolJson.json
) {
    private val _connectionState = MutableStateFlow(RemoteBrokerConnectionState.IDLE)
    val connectionState: StateFlow<RemoteBrokerConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<BrokerEnvelope>(extraBufferCapacity = 64)
    val events: SharedFlow<BrokerEnvelope> = _events.asSharedFlow()

    private var webSocket: WebSocket? = null

    fun connect(url: String) {
        if (_connectionState.value == RemoteBrokerConnectionState.CONNECTED ||
            _connectionState.value == RemoteBrokerConnectionState.CONNECTING
        ) {
            return
        }
        _connectionState.value = RemoteBrokerConnectionState.CONNECTING
        webSocket = okHttpClient.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    _connectionState.value = RemoteBrokerConnectionState.CONNECTED
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching {
                        protocolJson.decodeFromString(BrokerEnvelope.serializer(), text)
                    }.getOrNull()?.let { envelope ->
                        _events.tryEmit(envelope)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _connectionState.value = RemoteBrokerConnectionState.CLOSED
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    _connectionState.value = RemoteBrokerConnectionState.ERROR
                }
            }
        )
    }

    fun send(envelope: BrokerEnvelope): Boolean {
        val socket = webSocket ?: return false
        return socket.send(protocolJson.encodeToString(BrokerEnvelope.serializer(), envelope))
    }

    fun close(code: Int = 1000, reason: String = "client closing") {
        webSocket?.close(code, reason)
        webSocket = null
        _connectionState.value = RemoteBrokerConnectionState.CLOSED
    }
}
