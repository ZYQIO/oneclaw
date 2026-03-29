package ai.openclaw.app.host

import ai.openclaw.app.SecurePrefs
import ai.openclaw.app.auth.OpenAICodexCredential
import ai.openclaw.app.auth.OpenAICodexOAuthApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class LocalHostCodexAuthController(
  private val prefs: SecurePrefs,
  private val oauthApi: OpenAICodexOAuthApi,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  private val refreshLock = Mutex()

  fun statusSnapshot(): JsonObject {
    return credentialSnapshot(
      credential = prefs.loadOpenAICodexCredential(),
      refreshed = false,
      imported = false,
      previousExpiresAt = null,
      source = null,
      nowMs = clock(),
    )
  }

  fun importSnapshot(
    credential: OpenAICodexCredential,
    source: String? = null,
  ): JsonObject {
    val existing = prefs.loadOpenAICodexCredential()
    val normalized = normalizeCredential(credential)
    prefs.saveOpenAICodexCredential(normalized)
    return credentialSnapshot(
      credential = normalized,
      refreshed = false,
      imported = true,
      previousExpiresAt = existing?.expires,
      source = source?.trim()?.takeIf { it.isNotEmpty() },
      nowMs = clock(),
    )
  }

  suspend fun refreshSnapshot(): JsonObject =
    refreshLock.withLock {
      val existing = prefs.loadOpenAICodexCredential()
        ?: throw IllegalStateException("OpenAI Codex login required")
      val refreshed =
        withContext(Dispatchers.IO) {
          oauthApi.refreshCredential(existing)
        }
      prefs.saveOpenAICodexCredential(refreshed)
      credentialSnapshot(
        credential = refreshed,
        refreshed = true,
        imported = false,
        previousExpiresAt = existing.expires,
        source = null,
        nowMs = clock(),
      )
    }

  private fun credentialSnapshot(
    credential: OpenAICodexCredential?,
    refreshed: Boolean,
    imported: Boolean,
    previousExpiresAt: Long?,
    source: String?,
    nowMs: Long,
  ): JsonObject {
    return buildJsonObject {
      put("provider", JsonPrimitive("openai-codex"))
      put("configured", JsonPrimitive(credential != null))
      put("refreshed", JsonPrimitive(refreshed))
      put("imported", JsonPrimitive(imported))
      previousExpiresAt?.let { put("previousExpiresAt", JsonPrimitive(it)) }
      source?.let { put("source", JsonPrimitive(it)) }
      credential?.let { current ->
        put("accountIdPresent", JsonPrimitive(current.accountId.isNotBlank()))
        current.email
          ?.trim()
          ?.takeIf { it.isNotEmpty() }
          ?.let { put("emailHint", JsonPrimitive(maskEmail(it))) }
        put("expiresAt", JsonPrimitive(current.expires))
        put("expiresInMs", JsonPrimitive(current.expires - nowMs))
        put("expired", JsonPrimitive(current.expires <= nowMs))
        put("refreshRecommended", JsonPrimitive(current.expires <= nowMs + 30_000L))
      }
    }
  }

  private fun normalizeCredential(credential: OpenAICodexCredential): OpenAICodexCredential {
    val access = credential.access.trim()
    val refresh = credential.refresh.trim()
    val accountId = credential.accountId.trim()
    if (access.isEmpty()) throw IllegalStateException("access is required")
    if (refresh.isEmpty()) throw IllegalStateException("refresh is required")
    if (accountId.isEmpty()) throw IllegalStateException("accountId is required")
    if (credential.expires <= 0L) throw IllegalStateException("expires must be a positive timestamp")
    return OpenAICodexCredential(
      access = access,
      refresh = refresh,
      expires = credential.expires,
      accountId = accountId,
      email = credential.email?.trim()?.takeIf { it.isNotEmpty() },
    )
  }

  private fun maskEmail(value: String): String {
    val atIndex = value.indexOf('@')
    if (atIndex <= 0 || atIndex >= value.length - 1) return "***"
    val local = value.substring(0, atIndex)
    val domain = value.substring(atIndex + 1)
    val maskedLocal =
      when {
        local.length <= 1 -> "${local.first()}***"
        else -> "${local.first()}***${local.last()}"
      }
    return "$maskedLocal@$domain"
  }
}
