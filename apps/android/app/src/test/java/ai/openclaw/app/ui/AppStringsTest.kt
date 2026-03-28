package ai.openclaw.app.ui

import ai.openclaw.app.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class AppStringsTest {
  @Test
  fun localizeConnectionStatus_translatesOperatorStatus() {
    assertEquals(
      "已连接（operator：ready）",
      localizeConnectionStatus(AppLanguage.SimplifiedChinese, "Connected (operator: ready)"),
    )
  }

  @Test
  fun localizeConnectionStatus_translatesTlsPrompt() {
    assertEquals(
      "请核对网关 TLS 指纹…",
      localizeConnectionStatus(AppLanguage.SimplifiedChinese, "Verify gateway TLS fingerprint…"),
    )
  }

  @Test
  fun localizeConnectionStatus_translatesGatewayClosedPairingRequired() {
    assertEquals(
      "网关已关闭：需要配对",
      localizeConnectionStatus(AppLanguage.SimplifiedChinese, "Gateway closed: pairing required"),
    )
  }

  @Test
  fun localizeConnectionStatus_translatesGatewayTokenMismatch() {
    assertEquals(
      "网关错误：未授权：gateway token 不匹配（提供 gateway auth token）",
      localizeConnectionStatus(
        AppLanguage.SimplifiedChinese,
        "Gateway error: unauthorized: gateway token mismatch (provide gateway auth token)",
      ),
    )
  }

  @Test
  fun localizeConnectionStatus_translatesDeviceIdentityRequirement() {
    assertEquals(
      "网关错误：需要设备身份",
      localizeConnectionStatus(AppLanguage.SimplifiedChinese, "Gateway error: device identity required"),
    )
  }

  @Test
  fun localizeRemoteAccessStatus_translatesLocalHostHint() {
    assertEquals(
      "请切换到 Local Host 以接受远程连接。",
      localizeRemoteAccessStatus(
        AppLanguage.SimplifiedChinese,
        "Switch to Local Host to accept remote connections.",
      ),
    )
  }

  @Test
  fun localizeMicCaptureStatus_translatesListeningQueueState() {
    assertEquals(
      "监听中 · 2 条排队中",
      localizeMicCaptureStatus(AppLanguage.SimplifiedChinese, "Listening · 2 queued"),
    )
  }

  @Test
  fun localizeMicCaptureStatus_translatesGatewayWaitState() {
    assertEquals(
      "3 条排队中 · 等待网关",
      localizeMicCaptureStatus(AppLanguage.SimplifiedChinese, "3 queued · waiting for gateway"),
    )
  }

  @Test
  fun localizeMicCaptureStatus_translatesSendFailure() {
    assertEquals(
      "发送失败：boom",
      localizeMicCaptureStatus(AppLanguage.SimplifiedChinese, "Send failed: boom"),
    )
  }

  @Test
  fun localizeMicCaptureStatus_translatesSpeechErrors() {
    assertEquals(
      "语音错误（7）",
      localizeMicCaptureStatus(AppLanguage.SimplifiedChinese, "Speech error (7)"),
    )
  }

  @Test
  fun localizeChatError_translatesGatewayHealthFailure() {
    assertEquals(
      "Gateway 当前不健康，无法发送。",
      localizeChatError(AppLanguage.SimplifiedChinese, "Gateway health not OK; cannot send"),
    )
  }

  @Test
  fun localizeChatError_translatesUsageLimitFailure() {
    assertEquals(
      "已达到使用额度上限（errorType=usage_limit_reached）",
      localizeChatError(
        AppLanguage.SimplifiedChinese,
        "The usage limit has been reached | errorType=usage_limit_reached",
      ),
    )
  }

  @Test
  fun localizeChatError_translatesCodexRuntimeFailure() {
    assertEquals(
      "OpenAI Codex 没有返回响应体。",
      localizeChatError(AppLanguage.SimplifiedChinese, "OpenAI Codex returned no response body"),
    )
  }

  @Test
  fun localizeChatError_translatesCodexRequestFailureMetadata() {
    assertEquals(
      "OpenAI Codex 请求失败（429; requestId=req_123）",
      localizeChatError(
        AppLanguage.SimplifiedChinese,
        "OpenAI Codex request failed (429; requestId=req_123)",
      ),
    )
  }

  @Test
  fun localizeChatError_reusesKnownGatewayTranslation() {
    assertEquals(
      "网关错误：未授权：gateway token 不匹配（提供 gateway auth token）",
      localizeChatError(
        AppLanguage.SimplifiedChinese,
        "Gateway error: unauthorized: gateway token mismatch (provide gateway auth token)",
      ),
    )
  }

  @Test
  fun onboardingTokenPlaceholder_translatesToChinese() {
    assertEquals("令牌", onboardingTokenPlaceholder(AppLanguage.SimplifiedChinese))
  }

  @Test
  fun onboardingPasswordPlaceholder_translatesToChinese() {
    assertEquals("密码", onboardingPasswordPlaceholder(AppLanguage.SimplifiedChinese))
  }

  @Test
  fun onboardingSmsPermissionTitle_translatesToChinese() {
    assertEquals("短信", onboardingSmsPermissionTitle(AppLanguage.SimplifiedChinese))
  }
}
