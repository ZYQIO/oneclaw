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
  fun localizeRemoteAccessStatus_translatesPermissionDeniedFailure() {
    assertEquals(
      "远程访问启动失败：权限被拒绝",
      localizeRemoteAccessStatus(
        AppLanguage.SimplifiedChinese,
        "Remote access failed to start: Permission denied",
      ),
    )
  }

  @Test
  fun localizeRemoteAccessStatus_translatesBindFailureReason() {
    assertEquals(
      "远程访问启动失败：绑定失败：地址已被占用",
      localizeRemoteAccessStatus(
        AppLanguage.SimplifiedChinese,
        "Remote access failed to start: bind failed: Address already in use",
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
  fun localizeChatError_translatesStructuredCodexFailureSegments() {
    assertEquals(
      "OpenAI Codex 请求失败（400） | 必须提供 instructions。 | errorType=invalid_request_error | message=请求无效",
      localizeChatError(
        AppLanguage.SimplifiedChinese,
        "OpenAI Codex request failed (400) | instructions must be set | errorType=invalid_request_error | message=Bad Request",
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
  fun gatewaySetupCodeLabel_translatesToChinese() {
    assertEquals("设置码", gatewaySetupCodeLabel(AppLanguage.SimplifiedChinese))
  }

  @Test
  fun gatewayHostLabel_translatesToChinese() {
    assertEquals("主机", gatewayHostLabel(AppLanguage.SimplifiedChinese))
  }

  @Test
  fun gatewayTokenLabel_translatesToChinese() {
    assertEquals("令牌（可选）", gatewayTokenLabel(AppLanguage.SimplifiedChinese))
  }

  @Test
  fun gatewayPasswordLabel_translatesToChinese() {
    assertEquals("密码（可选）", gatewayPasswordLabel(AppLanguage.SimplifiedChinese))
  }

  @Test
  fun gatewayAdvancedControlsDescription_translatesToChinese() {
    assertEquals(
      "设置码、端点、TLS、令牌、密码和引导流程。",
      gatewayAdvancedControlsDescription(AppLanguage.SimplifiedChinese),
    )
  }

  @Test
  fun gatewayAdvancedSetupHint_translatesToChinese() {
    assertEquals(
      "粘贴设置码，或手动输入主机/端口。",
      gatewayAdvancedSetupHint(AppLanguage.SimplifiedChinese),
    )
  }

  @Test
  fun onboardingSmsPermissionTitle_translatesToChinese() {
    assertEquals("短信", onboardingSmsPermissionTitle(AppLanguage.SimplifiedChinese))
  }

  @Test
  fun localizeTalkModeStatus_translatesSystemSpeakingState() {
    assertEquals(
      "正在说话（系统）…",
      localizeTalkModeStatus(AppLanguage.SimplifiedChinese, "Speaking (System)…"),
    )
  }

  @Test
  fun localizeTalkModeStatus_translatesTalkFailureReason() {
    assertEquals(
      "对话失败：系统 TTS 不可用",
      localizeTalkModeStatus(AppLanguage.SimplifiedChinese, "Talk failed: system TTS unavailable"),
    )
  }

  @Test
  fun localizeTalkModeStatus_reusesChatErrorTranslation() {
    assertEquals(
      "播放失败：OpenAI Codex 请求失败（429）",
      localizeTalkModeStatus(
        AppLanguage.SimplifiedChinese,
        "Speak failed: OpenAI Codex request failed (429)",
      ),
    )
  }

  @Test
  fun localizeTalkModeStatus_translatesGatewayDisconnectedState() {
    assertEquals(
      "Gateway 未连接",
      localizeTalkModeStatus(AppLanguage.SimplifiedChinese, "Gateway not connected"),
    )
  }
}
