package ai.openclaw.app.auth

import android.net.Uri
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpenAICodexOAuthApiTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun createAuthorizationFlow_usesLoopbackRedirectAndCodexFlags() {
    val api = OpenAICodexOAuthApi(json = json)

    val flow = api.createAuthorizationFlow(originator = "pi")
    val uri = Uri.parse(flow.url)

    assertEquals("https", uri.scheme)
    assertEquals("auth.openai.com", uri.host)
    assertEquals("/oauth/authorize", uri.path)
    assertEquals("code", uri.getQueryParameter("response_type"))
    assertEquals(OpenAICodexOAuthApi.defaultClientId, uri.getQueryParameter("client_id"))
    assertEquals(OpenAICodexOAuthApi.defaultRedirectUri, uri.getQueryParameter("redirect_uri"))
    assertEquals("openid profile email offline_access", uri.getQueryParameter("scope"))
    assertEquals("S256", uri.getQueryParameter("code_challenge_method"))
    assertEquals("true", uri.getQueryParameter("id_token_add_organizations"))
    assertEquals("true", uri.getQueryParameter("codex_cli_simplified_flow"))
    assertEquals("pi", uri.getQueryParameter("originator"))
    assertFalse(flow.verifier.isBlank())
    assertFalse(flow.state.isBlank())
    assertNotNull(uri.getQueryParameter("code_challenge"))
  }

  @Test
  fun exchangeAuthorizationCode_parsesCredentialAndJwtClaims() {
    val server = MockWebServer()
    server.enqueue(
      MockResponse().setResponseCode(200).setBody(
        """
          {
            "access_token":"${fakeJwt(accountId = "acct_123", email = "person@example.com")}",
            "refresh_token":"refresh-token",
            "expires_in":3600
          }
        """.trimIndent(),
      ),
    )
    server.start()

    try {
      val api =
        OpenAICodexOAuthApi(
          json = json,
          client = OkHttpClient(),
          tokenUrl = server.url("/oauth/token").toString(),
        )

      val credential = api.exchangeAuthorizationCode(code = "code-123", verifier = "verifier-123")

      assertEquals("acct_123", credential.accountId)
      assertEquals("person@example.com", credential.email)
      assertEquals("refresh-token", credential.refresh)
      assertTrue(credential.expires > System.currentTimeMillis())

      val request = server.takeRequest()
      val body = request.body.readUtf8()
      assertTrue(body.contains("grant_type=authorization_code"))
      assertTrue(body.contains("code=code-123"))
      assertTrue(body.contains("code_verifier=verifier-123"))
      assertTrue(body.contains("redirect_uri=http%3A%2F%2Flocalhost%3A1455%2Fauth%2Fcallback"))
      assertTrue(body.contains("client_id=${OpenAICodexOAuthApi.defaultClientId}"))
    } finally {
      server.shutdown()
    }
  }

  @Test
  fun parseAuthorizationInput_acceptsFullUrlAndQueryString() {
    val fromUrl =
      parseAuthorizationInput("http://localhost:1455/auth/callback?code=abc123&state=state123")
    assertEquals("abc123", fromUrl.code)
    assertEquals("state123", fromUrl.state)

    val fromQuery = parseAuthorizationInput("code=xyz789&state=state789")
    assertEquals("xyz789", fromQuery.code)
    assertEquals("state789", fromQuery.state)

    val fromHash = parseAuthorizationInput("hash-code#hash-state")
    assertEquals("hash-code", fromHash.code)
    assertEquals("hash-state", fromHash.state)
  }

  @Test
  fun refreshCredential_fallsBackToStoredAccountIdWhenJwtClaimIsMissing() {
    val server = MockWebServer()
    server.enqueue(
      MockResponse().setResponseCode(200).setBody(
        """
          {
            "access_token":"${fakeJwtWithoutAccountId(email = "next@example.com")}",
            "refresh_token":"refresh-next",
            "expires_in":3600
          }
        """.trimIndent(),
      ),
    )
    server.start()

    try {
      val api =
        OpenAICodexOAuthApi(
          json = json,
          client = OkHttpClient(),
          tokenUrl = server.url("/oauth/token").toString(),
        )

      val credential =
        api.refreshCredential(
          OpenAICodexCredential(
            access = "old-access",
            refresh = "old-refresh",
            expires = 0,
            accountId = "acct_fallback",
            email = "old@example.com",
          ),
        )

      assertEquals("acct_fallback", credential.accountId)
      assertEquals("next@example.com", credential.email)
      assertEquals("refresh-next", credential.refresh)
    } finally {
      server.shutdown()
    }
  }

  @Test
  fun exchangeAuthorizationCode_surfacesStructuredOauthFailure() {
    val server = MockWebServer()
    server.enqueue(
      MockResponse().setResponseCode(401).setBody(
        """
          {
            "error":"access_denied",
            "error_description":"The resource owner denied the request"
          }
        """.trimIndent(),
      ),
    )
    server.start()

    try {
      val api =
        OpenAICodexOAuthApi(
          json = json,
          client = OkHttpClient(),
          tokenUrl = server.url("/oauth/token").toString(),
        )

      try {
        api.exchangeAuthorizationCode(code = "code-123", verifier = "verifier-123")
        fail("Expected exchangeAuthorizationCode to throw on a 401 response")
      } catch (error: IllegalStateException) {
        assertTrue(error.message.orEmpty().contains("OpenAI Codex OAuth failed (401)"))
        assertTrue(error.message.orEmpty().contains("The resource owner denied the request"))
        assertTrue(error.message.orEmpty().contains("error=access_denied"))
      }
    } finally {
      server.shutdown()
    }
  }

  private fun fakeJwt(
    accountId: String,
    email: String,
  ): String {
    val header = base64Url("""{"alg":"HS256","typ":"JWT"}""")
    val payload =
      base64Url(
        buildJsonObject {
          put("email", JsonPrimitive(email))
          put(
            "https://api.openai.com/auth",
            buildJsonObject {
              put("chatgpt_account_id", JsonPrimitive(accountId))
            },
          )
        }.toString(),
      )
    return "$header.$payload.signature"
  }

  private fun fakeJwtWithoutAccountId(email: String): String {
    val header = base64Url("""{"alg":"HS256","typ":"JWT"}""")
    val payload =
      base64Url(
        buildJsonObject {
          put("email", JsonPrimitive(email))
        }.toString(),
      )
    return "$header.$payload.signature"
  }

  private fun base64Url(value: String): String {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
  }
}
