package ai.openclaw.app.gateway

import ai.openclaw.app.host.LocalHostRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LocalGatewaySession(
  private val scope: CoroutineScope,
  private val runtime: LocalHostRuntime,
  private val role: String,
  private val onConnected: (serverName: String?, remoteAddress: String?, mainSessionKey: String?) -> Unit,
  private val onDisconnected: (message: String) -> Unit,
  private val onEvent: (event: String, payloadJson: String?) -> Unit,
) : NodeGatewayRpcClient {
  @Volatile private var isConnected = false
  @Volatile private var canvasHostUrl: String? = null
  @Volatile private var mainSessionKey: String? = null
  private var clientId: String? = null
  private var connectJob: Job? = null

  fun connect() {
    if (isConnected) return
    connectJob?.cancel()
    connectJob =
      scope.launch(Dispatchers.IO) {
        try {
          val snapshot = runtime.registerClient(role = role, onEvent = onEvent)
          clientId = snapshot.clientId
          canvasHostUrl = snapshot.canvasHostUrl
          mainSessionKey = snapshot.mainSessionKey
          isConnected = true
          onConnected(snapshot.serverName, snapshot.remoteAddress, snapshot.mainSessionKey)
        } catch (err: Throwable) {
          isConnected = false
          onDisconnected("Local host error: ${err.message ?: err::class.java.simpleName}")
        }
      }
  }

  fun disconnect() {
    val activeClientId = clientId
    clientId = null
    connectJob?.cancel()
    connectJob = null
    canvasHostUrl = null
    mainSessionKey = null
    isConnected = false
    if (activeClientId != null) {
      runtime.unregisterClient(activeClientId)
    }
    onDisconnected("Offline")
  }

  fun reconnect() {
    disconnect()
    connect()
  }

  override suspend fun request(method: String, paramsJson: String?, timeoutMs: Long): String {
    if (!isConnected) throw IllegalStateException("not connected")
    return runtime.request(role = role, method = method, paramsJson = paramsJson, timeoutMs = timeoutMs)
  }

  override suspend fun sendNodeEvent(event: String, payloadJson: String?): Boolean {
    if (!isConnected) return false
    return runtime.handleNodeEvent(role = role, event = event, payloadJson = payloadJson)
  }

  override fun currentCanvasHostUrl(): String? = canvasHostUrl

  override fun currentMainSessionKey(): String? = mainSessionKey

  override suspend fun refreshNodeCanvasCapability(timeoutMs: Long): Boolean {
    if (!isConnected) return false
    val refreshed = runtime.refreshNodeCanvasCapability()
    canvasHostUrl = refreshed
    return refreshed != null
  }
}
