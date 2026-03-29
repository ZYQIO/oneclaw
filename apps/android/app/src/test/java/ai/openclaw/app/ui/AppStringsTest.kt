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
  fun localizeConnectionStatus_translatesLocalHostPermissionDenied() {
    assertEquals(
      "本机 Host 错误：权限被拒绝",
      localizeConnectionStatus(AppLanguage.SimplifiedChinese, "Local host error: Permission denied"),
    )
  }

  @Test
  fun localizeConnectionStatus_translatesLocalHostUnavailableError() {
    assertEquals(
      "本机 Host 错误：不可用：未连接",
      localizeConnectionStatus(AppLanguage.SimplifiedChinese, "Local host error: UNAVAILABLE: not connected"),
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
  fun localizeChatError_translatesUnavailablePrefixError() {
    assertEquals(
      "不可用：未连接",
      localizeChatError(AppLanguage.SimplifiedChinese, "UNAVAILABLE: not connected"),
    )
  }

  @Test
  fun localizeChatError_translatesInvalidRequestPrefixError() {
    assertEquals(
      "请求无效：需要提供消息或附件",
      localizeChatError(
        AppLanguage.SimplifiedChinese,
        "INVALID_REQUEST: message or attachment required",
      ),
    )
  }

  @Test
  fun localizeChatError_translatesUiAutomationUnavailablePrefixError() {
    assertEquals(
      "UI 自动化不可用：无障碍服务已启用，但尚未绑定",
      localizeChatError(
        AppLanguage.SimplifiedChinese,
        "UI_AUTOMATION_UNAVAILABLE: accessibility service is enabled but not yet bound",
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
  fun localizeChatError_translatesNotificationAccessDisabled() {
    assertEquals(
      "通知访问未启用：请在系统设置中启用通知访问权限",
      localizeChatError(
        AppLanguage.SimplifiedChinese,
        "NOTIFICATIONS_DISABLED: enable notification access in system Settings",
      ),
    )
  }

  @Test
  fun localizeChatError_translatesNotificationListenerUnavailable() {
    assertEquals(
      "通知不可用：通知监听服务未连接",
      localizeChatError(
        AppLanguage.SimplifiedChinese,
        "NOTIFICATIONS_UNAVAILABLE: notification listener not connected",
      ),
    )
  }

  @Test
  fun localizeChatError_translatesNotificationNotFound() {
    assertEquals(
      "未找到通知：未找到通知 key",
      localizeChatError(
        AppLanguage.SimplifiedChinese,
        "NOTIFICATION_NOT_FOUND: notification key not found",
      ),
    )
  }

  @Test
  fun localizeChatError_translatesNotificationNotClearable() {
    assertEquals(
      "通知不可清除：通知正在进行中或受系统保护",
      localizeChatError(
        AppLanguage.SimplifiedChinese,
        "NOTIFICATION_NOT_CLEARABLE: notification is ongoing or protected",
      ),
    )
  }

  @Test
  fun localizeChatError_translatesNotificationDismissFailure() {
    assertEquals(
      "操作失败：清除失败",
      localizeChatError(AppLanguage.SimplifiedChinese, "ACTION_FAILED: dismiss failed"),
    )
  }

  @Test
  fun localizeChatError_translatesNotificationReplyUnavailable() {
    assertEquals(
      "操作不可用：通知没有可回复的操作",
      localizeChatError(
        AppLanguage.SimplifiedChinese,
        "ACTION_UNAVAILABLE: notification has no reply action",
      ),
    )
  }

  @Test
  fun localizeChatError_translatesNotificationReplyTextRequirement() {
    assertEquals(
      "请求无效：回复操作需要提供 replyText",
      localizeChatError(
        AppLanguage.SimplifiedChinese,
        "INVALID_REQUEST: replyText required for reply action",
      ),
    )
  }

  @Test
  fun localizeChatError_translatesSystemNotificationAuthorization() {
    assertEquals(
      "未授权：通知权限",
      localizeChatError(AppLanguage.SimplifiedChinese, "NOT_AUTHORIZED: notifications"),
    )
  }

  @Test
  fun localizeChatError_translatesSystemNotificationPostFailure() {
    assertEquals(
      "通知失败：发送失败",
      localizeChatError(
        AppLanguage.SimplifiedChinese,
        "NOTIFICATION_FAILED: notification post failed",
      ),
    )
  }

  @Test
  fun localizeChatError_translatesCameraPermissionRequirement() {
    assertEquals(
      "需要相机权限：请授予相机权限",
      localizeChatError(
        AppLanguage.SimplifiedChinese,
        "CAMERA_PERMISSION_REQUIRED: grant Camera permission",
      ),
    )
  }

  @Test
  fun localizeChatError_translatesMicrophonePermissionRequirement() {
    assertEquals(
      "需要麦克风权限：请授予麦克风权限",
      localizeChatError(
        AppLanguage.SimplifiedChinese,
        "MIC_PERMISSION_REQUIRED: grant Microphone permission",
      ),
    )
  }

  @Test
  fun localizeChatError_translatesCameraNotReady() {
    assertEquals(
      "不可用：相机尚未就绪",
      localizeChatError(AppLanguage.SimplifiedChinese, "UNAVAILABLE: camera not ready"),
    )
  }

  @Test
  fun localizeChatError_translatesCameraSnapFailure() {
    assertEquals(
      "不可用：相机拍照失败",
      localizeChatError(AppLanguage.SimplifiedChinese, "UNAVAILABLE: camera snap failed"),
    )
  }

  @Test
  fun localizeChatError_translatesCameraDecodeFailure() {
    assertEquals(
      "不可用：无法解码拍摄图像",
      localizeChatError(
        AppLanguage.SimplifiedChinese,
        "UNAVAILABLE: failed to decode captured image",
      ),
    )
  }

  @Test
  fun localizeChatError_translatesCameraEncodeFailure() {
    assertEquals(
      "不可用：无法编码 JPEG",
      localizeChatError(
        AppLanguage.SimplifiedChinese,
        "UNAVAILABLE: failed to encode JPEG",
      ),
    )
  }

  @Test
  fun localizeChatError_translatesCameraClipFinalizeTimeout() {
    assertEquals(
      "不可用：相机视频收尾超时",
      localizeChatError(
        AppLanguage.SimplifiedChinese,
        "UNAVAILABLE: camera clip finalize timed out",
      ),
    )
  }

  @Test
  fun localizeChatError_translatesCameraClipFailure() {
    assertEquals(
      "不可用：相机视频录制失败（error=ERROR_RECORDER_ERROR）",
      localizeChatError(
        AppLanguage.SimplifiedChinese,
        "UNAVAILABLE: camera clip failed (error=ERROR_RECORDER_ERROR)",
      ),
    )
  }

  @Test
  fun localizeChatError_translatesCameraTooLargeCode() {
    assertEquals(
      "相机内容过大：6291456 bytes > 5242880 bytes",
      localizeChatError(
        AppLanguage.SimplifiedChinese,
        "CAMERA_TOO_LARGE: 6291456 bytes > 5242880 bytes",
      ),
    )
  }

  @Test
  fun localizeVoiceConversationText_translatesDefaultFailure() {
    assertEquals(
      "语音请求失败。",
      localizeVoiceConversationText(AppLanguage.SimplifiedChinese, "Voice request failed"),
    )
  }

  @Test
  fun localizeVoiceConversationText_reusesChatErrorTranslation() {
    assertEquals(
      "不可用：未连接",
      localizeVoiceConversationText(AppLanguage.SimplifiedChinese, "UNAVAILABLE: not connected"),
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
  fun onboardingGatewaySummaryLabel_translatesToChinese() {
    assertEquals("网关", onboardingGatewaySummaryLabel(AppLanguage.SimplifiedChinese))
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

  @Test
  fun localizeTalkModeStatus_translatesSpeechRecognizerNetworkError() {
    assertEquals(
      "网络错误",
      localizeTalkModeStatus(AppLanguage.SimplifiedChinese, "Network error"),
    )
  }

  @Test
  fun localizeTalkModeStatus_translatesSpeechRecognizerErrorCode() {
    assertEquals(
      "语音错误（11）",
      localizeTalkModeStatus(AppLanguage.SimplifiedChinese, "Speech error (11)"),
    )
  }

  @Test
  fun localizeChatError_translatesUnknownCameraDeviceId() {
    assertEquals(
      "请求无效：未知相机 camera deviceId 'rear-2'",
      localizeChatError(
        AppLanguage.SimplifiedChinese,
        "INVALID_REQUEST: unknown camera deviceId 'rear-2'",
      ),
    )
  }
}
