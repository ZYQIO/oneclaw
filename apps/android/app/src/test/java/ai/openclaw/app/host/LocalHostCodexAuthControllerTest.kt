package ai.openclaw.app.host

import android.content.Context
import ai.openclaw.app.SecurePrefs
import ai.openclaw.app.auth.OpenAICodexCredential
import ai.openclaw.app.auth.OpenAICodexOAuthApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LocalHostCodexAuthControllerTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun statusSnapshot_masksSensitiveMetadata() {
    val context = RuntimeEnvironment.getApplication()
    val prefs = securePrefs(context, name = "openclaw.node.secure.test.localhost.auth.status")
    prefs.saveOpenAICodexCredential(
      OpenAICodexCredential(
        access = "access-token",
        refresh = "refresh-token",
        expires = 120_000L,
        accountId = "acct_123",
        email = "person@example.com",
      ),
    )
    val controller =
      LocalHostCodexAuthController(
        prefs = prefs,
        oauthApi = OpenAICodexOAuthApi(json = json),
        clock = { 60_000L },
      )

    val snapshot = controller.statusSnapshot()

    assertEquals("openai-codex", snapshot.getValue("provider").jsonPrimitive.content)
    assertEquals(true, snapshot.getValue("configured").jsonPrimitive.boolean)
    assertEquals("p***n@example.com", snapshot.getValue("emailHint").jsonPrimitive.content)
    assertEquals(60_000L, snapshot.getValue("expiresInMs").jsonPrimitive.long)
    assertEquals(false, snapshot.getValue("expired").jsonPrimitive.boolean)
    assertEquals(false, snapshot.getValue("refreshRecommended").jsonPrimitive.boolean)
  }

  @Test
  fun refreshSnapshot_updatesStoredCredentialAndReturnsNewExpiry() =
    runTest {
      val server = MockWebServer()
      server.enqueue(
        MockResponse()
          .setResponseCode(200)
          .addHeader("Content-Type", "application/json")
          .setBody(
            """
            {"access_token":"${fakeJwt(accountId = "acct_new", email = "person@example.com")}","refresh_token":"refresh-new","expires_in":3600}
            """.trimIndent(),
          ),
      )
      server.start()

      try {
        val context = RuntimeEnvironment.getApplication()
        val prefs = securePrefs(context, name = "openclaw.node.secure.test.localhost.auth.refresh")
        prefs.saveOpenAICodexCredential(
          OpenAICodexCredential(
            access = fakeJwt(accountId = "acct_old", email = "person@example.com"),
            refresh = "refresh-old",
            expires = 60_000L,
            accountId = "acct_old",
            email = "person@example.com",
          ),
        )
        val controller =
          LocalHostCodexAuthController(
            prefs = prefs,
            oauthApi =
              OpenAICodexOAuthApi(
                json = json,
                client = OkHttpClient(),
                tokenUrl = server.url("/oauth/token").toString(),
                clock = { 100_000L },
              ),
            clock = { 100_000L },
          )

        val snapshot = controller.refreshSnapshot()
        val stored = prefs.loadOpenAICodexCredential()

        assertEquals(true, snapshot.getValue("refreshed").jsonPrimitive.boolean)
        assertEquals(60_000L, snapshot.getValue("previousExpiresAt").jsonPrimitive.long)
        assertEquals(3_700_000L, snapshot.getValue("expiresAt").jsonPrimitive.long)
        assertEquals(false, snapshot.getValue("refreshRecommended").jsonPrimitive.boolean)
        assertEquals("refresh-new", stored?.refresh)
        assertEquals("acct_new", stored?.accountId)
        assertTrue((stored?.expires ?: 0L) > 100_000L)

        val request = server.takeRequest()
        val requestBody = request.body.readUtf8()
        assertEquals("/oauth/token", request.path)
        assertTrue(requestBody.contains("grant_type=refresh_token"))
        assertTrue(requestBody.contains("refresh_token=refresh-old"))
      } finally {
        server.shutdown()
      }
    }

  @Test
  fun importSnapshot_persistsCredentialAndReturnsImportMetadata() {
    val context = RuntimeEnvironment.getApplication()
    val prefs = securePrefs(context, name = "openclaw.node.secure.test.localhost.auth.import")
    prefs.saveOpenAICodexCredential(
      OpenAICodexCredential(
        access = "access-old",
        refresh = "refresh-old",
        expires = 45_000L,
        accountId = "acct_old",
        email = "old@example.com",
      ),
    )
    val controller =
      LocalHostCodexAuthController(
        prefs = prefs,
        oauthApi = OpenAICodexOAuthApi(json = json),
        clock = { 30_000L },
      )

    val snapshot =
      controller.importSnapshot(
        credential =
          OpenAICodexCredential(
            access = " access-new ",
            refresh = " refresh-new ",
            expires = 180_000L,
            accountId = " acct_new ",
            email = " person@example.com ",
          ),
        source = "desktop-sync",
      )
    val stored = prefs.loadOpenAICodexCredential()

    assertEquals(true, snapshot.getValue("imported").jsonPrimitive.boolean)
    assertEquals(false, snapshot.getValue("refreshed").jsonPrimitive.boolean)
    assertEquals("desktop-sync", snapshot.getValue("source").jsonPrimitive.content)
    assertEquals(45_000L, snapshot.getValue("previousExpiresAt").jsonPrimitive.long)
    assertEquals("p***n@example.com", snapshot.getValue("emailHint").jsonPrimitive.content)
    assertEquals(180_000L, snapshot.getValue("expiresAt").jsonPrimitive.long)
    assertEquals("access-new", stored?.access)
    assertEquals("refresh-new", stored?.refresh)
    assertEquals("acct_new", stored?.accountId)
    assertEquals("person@example.com", stored?.email)
  }

  private fun securePrefs(context: Context, name: String): SecurePrefs {
    val securePrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    securePrefs.edit().clear().commit()
    return SecurePrefs(context, securePrefsOverride = securePrefs)
  }

  private fun fakeJwt(
    accountId: String,
    email: String,
  ): String {
    val payload =
      """{"email":"$email","https://api.openai.com/auth":{"chatgpt_account_id":"$accountId"}}"""
    val header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
    val encodedPayload =
      java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
    return "$header.$encodedPayload.signature"
  }
}
