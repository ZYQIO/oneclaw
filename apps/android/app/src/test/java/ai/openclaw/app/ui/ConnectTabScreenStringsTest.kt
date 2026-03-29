package ai.openclaw.app.ui

import ai.openclaw.app.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectTabScreenStringsTest {
  @Test
  fun translateKnownCodexMessage_translatesOauthStatusCodeFailure() {
    assertEquals(
      "OpenAI Codex OAuth 失败（401）",
      translateKnownCodexMessage(AppLanguage.SimplifiedChinese, "OpenAI Codex OAuth failed (401)"),
    )
  }

  @Test
  fun translateKnownCodexMessage_translatesInvalidAuthorizeUrl() {
    assertEquals(
      "OpenAI authorize URL 无效：https://example.com/oauth",
      translateKnownCodexMessage(
        AppLanguage.SimplifiedChinese,
        "Invalid OpenAI authorize URL: https://example.com/oauth",
      ),
    )
  }

  @Test
  fun translateKnownCodexMessage_translatesOpenPageFailure() {
    assertEquals(
      "无法打开 OpenAI 登录页面。",
      translateKnownCodexMessage(AppLanguage.SimplifiedChinese, "Failed to open the OpenAI sign-in page."),
    )
  }

  @Test
  fun translateKnownCodexMessage_translatesStructuredOauthFailure() {
    assertEquals(
      "OpenAI Codex OAuth 失败（401） | 访问被拒绝 | error=访问被拒绝 | message=请求无效",
      translateKnownCodexMessage(
        AppLanguage.SimplifiedChinese,
        "OpenAI Codex OAuth failed (401) | access_denied | error=access_denied | message=Bad Request",
      ),
    )
  }
}
