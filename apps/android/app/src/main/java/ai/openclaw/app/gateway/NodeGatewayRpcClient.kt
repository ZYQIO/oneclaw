package ai.openclaw.app.gateway

interface NodeGatewayRpcClient : GatewayRpcClient {
  suspend fun refreshNodeCanvasCapability(timeoutMs: Long = 8_000): Boolean
}
