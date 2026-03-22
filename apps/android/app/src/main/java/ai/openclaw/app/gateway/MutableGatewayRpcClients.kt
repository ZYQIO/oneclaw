package ai.openclaw.app.gateway

class MutableGatewayRpcClient(
  private val unavailableMessage: String = "not connected",
) : GatewayRpcClient {
  @Volatile private var delegate: GatewayRpcClient? = null

  fun setDelegate(value: GatewayRpcClient?) {
    delegate = value
  }

  override suspend fun request(method: String, paramsJson: String?, timeoutMs: Long): String {
    return requireDelegate().request(method, paramsJson, timeoutMs)
  }

  override suspend fun sendNodeEvent(event: String, payloadJson: String?): Boolean {
    return requireDelegate().sendNodeEvent(event, payloadJson)
  }

  override fun currentCanvasHostUrl(): String? = delegate?.currentCanvasHostUrl()

  override fun currentMainSessionKey(): String? = delegate?.currentMainSessionKey()

  private fun requireDelegate(): GatewayRpcClient {
    return delegate ?: throw IllegalStateException(unavailableMessage)
  }
}

class MutableNodeGatewayRpcClient(
  private val unavailableMessage: String = "not connected",
) : NodeGatewayRpcClient {
  @Volatile private var delegate: NodeGatewayRpcClient? = null

  fun setDelegate(value: NodeGatewayRpcClient?) {
    delegate = value
  }

  override suspend fun request(method: String, paramsJson: String?, timeoutMs: Long): String {
    return requireDelegate().request(method, paramsJson, timeoutMs)
  }

  override suspend fun sendNodeEvent(event: String, payloadJson: String?): Boolean {
    return requireDelegate().sendNodeEvent(event, payloadJson)
  }

  override fun currentCanvasHostUrl(): String? = delegate?.currentCanvasHostUrl()

  override fun currentMainSessionKey(): String? = delegate?.currentMainSessionKey()

  override suspend fun refreshNodeCanvasCapability(timeoutMs: Long): Boolean {
    return requireDelegate().refreshNodeCanvasCapability(timeoutMs)
  }

  private fun requireDelegate(): NodeGatewayRpcClient {
    return delegate ?: throw IllegalStateException(unavailableMessage)
  }
}
