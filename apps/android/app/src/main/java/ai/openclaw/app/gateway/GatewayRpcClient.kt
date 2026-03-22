package ai.openclaw.app.gateway

interface GatewayRpcClient {
  suspend fun request(method: String, paramsJson: String?, timeoutMs: Long = 15_000): String

  suspend fun sendNodeEvent(event: String, payloadJson: String?): Boolean

  fun currentCanvasHostUrl(): String?

  fun currentMainSessionKey(): String?
}
