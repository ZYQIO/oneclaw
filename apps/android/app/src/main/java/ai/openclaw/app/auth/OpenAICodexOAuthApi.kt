package ai.openclaw.app.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class OpenAICodexAuthorizationFlow(
  val verifier: String,
  val state: String,
  val url: String,
)

class OpenAICodexOAuthApi(
  private val json: Json,
  private val client: OkHttpClient = OkHttpClient(),
  private val authorizeUrl: String = defaultAuthorizeUrl,
  private val tokenUrl: String = defaultTokenUrl,
  private val redirectUri: String = defaultRedirectUri,
  private val clientId: String = defaultClientId,
  private val secureRandom: SecureRandom = SecureRandom(),
  private val clock: () -> Long = System::currentTimeMillis,
) {
  companion object {
    const val defaultClientId: String = "app_EMoamEEZ73f0CkXaXp7hrann"
    const val defaultAuthorizeUrl: String = "https://auth.openai.com/oauth/authorize"
    const val defaultTokenUrl: String = "https://auth.openai.com/oauth/token"
    const val defaultRedirectUri: String = "http://localhost:1455/auth/callback"
    private const val scope = "openid profile email offline_access"
    private const val jwtClaimPath = "https://api.openai.com/auth"
  }

  fun createAuthorizationFlow(originator: String = "pi"): OpenAICodexAuthorizationFlow {
    val verifier = randomBase64Url(32)
    val challenge = sha256Base64Url(verifier)
    val state = randomHex(16)
    val baseUrl =
      authorizeUrl.toHttpUrlOrNull()
        ?: throw IllegalStateException("Invalid OpenAI authorize URL: $authorizeUrl")
    val url =
      baseUrl
        .newBuilder()
        .query(null)
        .addQueryParameter("response_type", "code")
        .addQueryParameter("client_id", clientId)
        .addQueryParameter("redirect_uri", redirectUri)
        .addQueryParameter("scope", scope)
        .addQueryParameter("code_challenge", challenge)
        .addQueryParameter("code_challenge_method", "S256")
        .addQueryParameter("state", state)
        .addQueryParameter("id_token_add_organizations", "true")
        .addQueryParameter("codex_cli_simplified_flow", "true")
        .addQueryParameter("originator", originator)
        .build()
        .toString()

    return OpenAICodexAuthorizationFlow(
      verifier = verifier,
      state = state,
      url = url,
    )
  }

  fun exchangeAuthorizationCode(
    code: String,
    verifier: String,
  ): OpenAICodexCredential {
    val formBody =
      FormBody.Builder()
        .add("grant_type", "authorization_code")
        .add("client_id", clientId)
        .add("code", code)
        .add("code_verifier", verifier)
        .add("redirect_uri", redirectUri)
        .build()

    val request =
      Request.Builder()
        .url(tokenUrl)
        .post(formBody)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .build()

    client.newCall(request).execute().use { response ->
      val body = response.body?.string().orEmpty()
      if (!response.isSuccessful) {
        throw IllegalStateException(parseOAuthError(response.code, body))
      }
      return parseTokenResponse(body)
    }
  }

  fun refreshCredential(current: OpenAICodexCredential): OpenAICodexCredential {
    val formBody =
      FormBody.Builder()
        .add("grant_type", "refresh_token")
        .add("refresh_token", current.refresh)
        .add("client_id", clientId)
        .build()
    val request =
      Request.Builder()
        .url(tokenUrl)
        .post(formBody)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .build()

    client.newCall(request).execute().use { response ->
      val body = response.body?.string().orEmpty()
      if (!response.isSuccessful) {
        throw IllegalStateException(parseOAuthError(response.code, body))
      }
      return parseTokenResponse(
        body = body,
        fallbackAccountId = current.accountId,
        fallbackEmail = current.email,
      )
    }
  }

  fun extractAccountId(accessToken: String): String? {
    val payload = decodeJwtPayload(accessToken) ?: return null
    val auth = payload[jwtClaimPath].asObjectOrNull() ?: return null
    return auth["chatgpt_account_id"].asStringOrNull()?.takeIf { it.isNotBlank() }
  }

  fun extractEmail(accessToken: String): String? {
    val payload = decodeJwtPayload(accessToken) ?: return null
    val topLevel = payload["email"].asStringOrNull()?.takeIf { it.isNotBlank() }
    if (topLevel != null) return topLevel
    val auth = payload[jwtClaimPath].asObjectOrNull()
    return auth?.get("email").asStringOrNull()?.takeIf { it.isNotBlank() }
  }

  private fun parseTokenResponse(
    body: String,
    fallbackAccountId: String? = null,
    fallbackEmail: String? = null,
  ): OpenAICodexCredential {
    val root =
      json.parseToJsonElement(body) as? JsonObject
        ?: throw IllegalStateException("OpenAI Codex OAuth returned invalid JSON")
    val access = root["access_token"].asStringOrNull().orEmpty()
    val refresh = root["refresh_token"].asStringOrNull().orEmpty()
    val expiresIn = root["expires_in"].asLongOrNull()
    if (access.isEmpty() || refresh.isEmpty() || expiresIn == null) {
      throw IllegalStateException("OpenAI Codex OAuth response was missing required fields")
    }
    val accountId =
      extractAccountId(access)
        ?: fallbackAccountId?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("Failed to extract accountId from token")
    return OpenAICodexCredential(
      access = access,
      refresh = refresh,
      expires = clock() + expiresIn * 1000,
      accountId = accountId,
      email = extractEmail(access) ?: fallbackEmail,
    )
  }

  private fun parseOAuthError(statusCode: Int, body: String): String {
    if (body.isBlank()) {
      return "OpenAI Codex OAuth failed ($statusCode)"
    }
    return try {
      val root = json.parseToJsonElement(body) as? JsonObject
      val error = root?.get("error").asObjectOrNull()
      error?.get("message").asStringOrNull()
        ?: root?.get("message").asStringOrNull()
        ?: "OpenAI Codex OAuth failed ($statusCode)"
    } catch (_: Throwable) {
      body.take(200)
    }
  }

  private fun randomBase64Url(byteCount: Int): String {
    val bytes = ByteArray(byteCount)
    secureRandom.nextBytes(bytes)
    return bytes.toBase64Url()
  }

  private fun randomHex(byteCount: Int): String {
    val bytes = ByteArray(byteCount)
    secureRandom.nextBytes(bytes)
    return bytes.joinToString(separator = "") { "%02x".format(it) }
  }

  private fun sha256Base64Url(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(value.toByteArray(Charsets.UTF_8)).toBase64Url()
  }

  private fun decodeJwtPayload(value: String): JsonObject? {
    val parts = value.split(".")
    if (parts.size != 3) return null
    val rawPayload = parts[1]
    val payload =
      when (rawPayload.length % 4) {
        2 -> "$rawPayload=="
        3 -> "$rawPayload="
        else -> rawPayload
      }
    return try {
      val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
      json.parseToJsonElement(String(decoded, Charsets.UTF_8)) as? JsonObject
    } catch (_: Throwable) {
      null
    }
  }
}

private fun ByteArray.toBase64Url(): String {
  return Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

private fun kotlinx.serialization.json.JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

private fun kotlinx.serialization.json.JsonElement?.asStringOrNull(): String? =
  when (this) {
    is JsonPrimitive -> content
    else -> null
  }

private fun kotlinx.serialization.json.JsonElement?.asLongOrNull(): Long? =
  when (this) {
    is JsonPrimitive -> content.toLongOrNull()
    else -> null
  }
