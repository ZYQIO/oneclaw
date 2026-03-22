package ai.openclaw.app

enum class GatewayConnectionMode(val rawValue: String) {
  RemoteGateway("remoteGateway"),
  LocalHost("localHost");

  companion object {
    fun fromRawValue(raw: String?): GatewayConnectionMode {
      return entries.firstOrNull { it.rawValue == raw?.trim() } ?: RemoteGateway
    }
  }
}
