package ai.openclaw.app.auth

import kotlinx.serialization.Serializable

@Serializable
data class OpenAICodexCredential(
  val access: String,
  val refresh: String,
  val expires: Long,
  val accountId: String,
  val email: String? = null,
)
