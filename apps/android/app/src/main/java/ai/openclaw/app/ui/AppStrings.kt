package ai.openclaw.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import ai.openclaw.app.AppLanguage

private val connectedOperatorRegex = Regex("""^Connected \(operator: (.+)\)$""")
private val listeningQueuedRegex = Regex("""^Listening · (\d+) queued$""")
private val queuedWaitingRegex = Regex("""^(\d+) queued · waiting for gateway$""")
private val speechErrorRegex = Regex("""^Speech error \((\d+)\)$""")
private val gatewayErrorRegex = Regex("""^Gateway error: (.+)$""", RegexOption.IGNORE_CASE)
private val gatewayClosedRegex = Regex("""^Gateway closed: (.+)$""", RegexOption.IGNORE_CASE)
private val localHostErrorRegex = Regex("""^Local host error: (.+)$""", RegexOption.IGNORE_CASE)
private val gatewayClosedWithCodeRegex = Regex("""^gateway closed \((\d+)\): (.+)$""", RegexOption.IGNORE_CASE)
private val gatewayAuthHintRegex = Regex("""^unauthorized: gateway (token|password) (missing|mismatch) \((.+)\)$""", RegexOption.IGNORE_CASE)
private val codexRequestFailedRegex = Regex("""^OpenAI Codex request failed(?: \(([^)]+)\))?(?:\s*\|\s*(.+))?$""")
private val runtimePrefixedErrorRegex = Regex("""^([A-Z_]+):\s*(.+)$""")
private val usageLimitRegex = Regex("""^The usage limit has been reached(?:\s*\|\s*errorType=(.+))?$""", RegexOption.IGNORE_CASE)

internal val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.English }

internal fun AppLanguage.pick(english: String, simplifiedChinese: String): String {
  return when (this) {
    AppLanguage.English -> english
    AppLanguage.SimplifiedChinese -> simplifiedChinese
  }
}

internal fun AppLanguage.displayName(): String {
  return when (this) {
    AppLanguage.English -> "English"
    AppLanguage.SimplifiedChinese -> "简体中文"
  }
}

internal fun localizeConnectionStatus(
  language: AppLanguage,
  statusText: String,
): String {
  val trimmed = statusText.trim()
  localHostErrorRegex.matchEntire(trimmed)?.let { match ->
    val reason = localizeRuntimeErrorDetail(language, match.groupValues[1])
    return language.pick(trimmed, "本机 Host 错误：$reason")
  }
  gatewayErrorRegex.matchEntire(trimmed)?.let { match ->
    val reason = localizeGatewayDisconnectReason(language, match.groupValues[1])
    return language.pick(trimmed, "网关错误：$reason")
  }
  gatewayClosedRegex.matchEntire(trimmed)?.let { match ->
    val reason = localizeGatewayDisconnectReason(language, match.groupValues[1])
    return language.pick(trimmed, "网关已关闭：$reason")
  }
  return when {
    trimmed.equals("Offline", ignoreCase = true) -> language.pick("Offline", "离线")
    trimmed.equals("Connected", ignoreCase = true) -> language.pick("Connected", "已连接")
    trimmed.equals("Connected (node offline)", ignoreCase = true) ->
      language.pick("Connected (node offline)", "已连接（节点离线）")
    trimmed.equals("Connected (operator offline)", ignoreCase = true) ->
      language.pick("Connected (operator offline)", "已连接（operator 离线）")
    connectedOperatorRegex.matchEntire(trimmed) != null -> {
      val operator = connectedOperatorRegex.matchEntire(trimmed)!!.groupValues[1]
      language.pick(
        english = trimmed,
        simplifiedChinese = "已连接（operator：$operator）",
      )
    }
    trimmed.equals("Connecting…", ignoreCase = true) || trimmed.equals("Connecting...", ignoreCase = true) ->
      language.pick("Connecting…", "连接中…")
    trimmed.equals("Reconnecting", ignoreCase = true) -> language.pick("Reconnecting", "重连中")
    trimmed.equals("Starting local host…", ignoreCase = true) || trimmed.equals("Starting local host...", ignoreCase = true) ->
      language.pick("Starting local host…", "本地主机启动中…")
    trimmed.equals("Verify gateway TLS fingerprint…", ignoreCase = true) || trimmed.equals("Verify gateway TLS fingerprint...", ignoreCase = true) ->
      language.pick("Verify gateway TLS fingerprint…", "请核对网关 TLS 指纹…")
    trimmed.equals("Failed: can't read TLS fingerprint", ignoreCase = true) ->
      language.pick("Failed: can't read TLS fingerprint", "失败：无法读取 TLS 指纹")
    trimmed.equals("Failed: no cached gateway endpoint", ignoreCase = true) ->
      language.pick("Failed: no cached gateway endpoint", "失败：没有缓存的网关端点")
    trimmed.equals("Failed: invalid manual host/port", ignoreCase = true) ->
      language.pick("Failed: invalid manual host/port", "失败：手动输入的主机或端口无效")
    trimmed.startsWith("Connected to ", ignoreCase = true) ->
      language.pick(
        english = trimmed,
        simplifiedChinese = "已连接到 ${trimmed.removePrefix("Connected to ")}",
      )
    trimmed.startsWith("Failed: ", ignoreCase = true) ->
      language.pick(
        english = trimmed,
        simplifiedChinese = "失败：${trimmed.substringAfter(": ").trim()}",
      )
    else -> trimmed
  }
}

private fun localizeGatewayDisconnectReason(
  language: AppLanguage,
  reasonText: String,
): String {
  val trimmed = reasonText.trim()
  gatewayClosedWithCodeRegex.matchEntire(trimmed)?.let { match ->
    val code = match.groupValues[1]
    val innerReason = localizeGatewayDisconnectReason(language, match.groupValues[2])
    return language.pick(trimmed, "网关已关闭（$code）：$innerReason")
  }
  gatewayAuthHintRegex.matchEntire(trimmed)?.let { match ->
    val kind = match.groupValues[1].lowercase()
    val state = match.groupValues[2].lowercase()
    val localizedHint = localizeGatewayAuthHint(language, match.groupValues[3])
    val localizedReason =
      when {
        kind == "token" && state == "missing" -> "缺少 gateway token"
        kind == "token" && state == "mismatch" -> "gateway token 不匹配"
        kind == "password" && state == "missing" -> "缺少 gateway password"
        else -> "gateway password 不匹配"
      }
    return language.pick(trimmed, "未授权：$localizedReason（$localizedHint）")
  }
  return when (trimmed.lowercase()) {
    "pairing required" -> language.pick(trimmed, "需要配对")
    "device identity required" -> language.pick(trimmed, "需要设备身份")
    "connect challenge timeout" -> language.pick(trimmed, "连接握手超时")
    "connect failed" -> language.pick(trimmed, "连接失败")
    "request failed" -> language.pick(trimmed, "请求失败")
    "unauthorized" -> language.pick(trimmed, "未授权")
    "unauthorized: gateway token not configured on gateway (set gateway.auth.token)" ->
      language.pick(trimmed, "未授权：gateway 尚未配置 token（请设置 gateway.auth.token）")
    "unauthorized: gateway password not configured on gateway (set gateway.auth.password)" ->
      language.pick(trimmed, "未授权：gateway 尚未配置 password（请设置 gateway.auth.password）")
    "unauthorized: bootstrap token invalid or expired (scan a fresh setup code)" ->
      language.pick(trimmed, "未授权：bootstrap token 无效或已过期（请扫描新的 setup code）")
    "unauthorized: tailscale identity missing (use tailscale serve auth or gateway token/password)" ->
      language.pick(trimmed, "未授权：缺少 Tailscale 身份（请使用 Tailscale Serve 授权，或提供 gateway token/password）")
    "unauthorized: tailscale proxy headers missing (use tailscale serve or gateway token/password)" ->
      language.pick(trimmed, "未授权：缺少 Tailscale 代理头（请使用 Tailscale Serve，或提供 gateway token/password）")
    "unauthorized: tailscale identity check failed (use tailscale serve auth or gateway token/password)" ->
      language.pick(trimmed, "未授权：Tailscale 身份校验失败（请使用 Tailscale Serve 授权，或提供 gateway token/password）")
    "unauthorized: tailscale identity mismatch (use tailscale serve auth or gateway token/password)" ->
      language.pick(trimmed, "未授权：Tailscale 身份不匹配（请使用 Tailscale Serve 授权，或提供 gateway token/password）")
    "unauthorized: too many failed authentication attempts (retry later)" ->
      language.pick(trimmed, "未授权：认证失败次数过多（请稍后重试）")
    "unauthorized: device token mismatch (rotate/reissue device token)" ->
      language.pick(trimmed, "未授权：device token 不匹配（请轮换或重新签发 device token）")
    "unauthorized: device token rejected (pair/repair this device, or provide gateway token)" ->
      language.pick(trimmed, "未授权：device token 被拒绝（请重新配对/修复此设备，或提供 gateway token）")
    else ->
      if (trimmed.startsWith("unauthorized: ", ignoreCase = true)) {
        language.pick(trimmed, "未授权：${trimmed.substringAfter(":").trim()}")
      } else {
        trimmed
      }
  }
}

private fun localizeGatewayAuthHint(
  language: AppLanguage,
  hint: String,
): String {
  val trimmed = hint.trim()
  return when (trimmed) {
    "set gateway.remote.token to match gateway.auth.token" ->
      language.pick(trimmed, "将 gateway.remote.token 设为与 gateway.auth.token 一致")
    "open the dashboard URL and paste the token in Control UI settings" ->
      language.pick(trimmed, "打开 dashboard URL，并在 Control UI 设置中粘贴 token")
    "provide gateway auth token" ->
      language.pick(trimmed, "提供 gateway auth token")
    "set gateway.remote.password to match gateway.auth.password" ->
      language.pick(trimmed, "将 gateway.remote.password 设为与 gateway.auth.password 一致")
    "enter the password in Control UI settings" ->
      language.pick(trimmed, "在 Control UI 设置中输入 password")
    "provide gateway auth password" ->
      language.pick(trimmed, "提供 gateway auth password")
    else -> trimmed
  }
}

internal fun localizeRemoteAccessStatus(
  language: AppLanguage,
  statusText: String,
): String {
  val trimmed = statusText.trim()
  return when {
    trimmed == "Remote access is off." -> language.pick(trimmed, "远程访问已关闭。")
    trimmed == "Remote access stopped." -> language.pick(trimmed, "远程访问已停止。")
    trimmed == "Switch to Local Host to accept remote connections." ->
      language.pick(trimmed, "请切换到 Local Host 以接受远程连接。")
    trimmed == "Remote access port must be between 1 and 65535." ->
      language.pick(trimmed, "远程访问端口必须在 1 到 65535 之间。")
    trimmed == "Remote access token is missing." ->
      language.pick(trimmed, "缺少远程访问 token。")
    trimmed.startsWith("Remote access ready at ") ->
      language.pick(
        english = trimmed,
        simplifiedChinese = "远程访问已就绪：${trimmed.removePrefix("Remote access ready at ")}",
      )
    trimmed.startsWith("Remote access is listening on port ") ->
      language.pick(
        english = trimmed,
        simplifiedChinese = "远程访问正在监听端口 ${trimmed.substringAfterLast(" ").removeSuffix(".")}。",
      )
    trimmed.startsWith("Remote access failed to start: ") ->
      language.pick(
        english = trimmed,
        simplifiedChinese = "远程访问启动失败：${localizeRemoteAccessFailureReason(language, trimmed.removePrefix("Remote access failed to start: "))}",
      )
    else -> trimmed
  }
}

private fun localizeRemoteAccessFailureReason(
  language: AppLanguage,
  reasonText: String,
): String {
  val trimmed = reasonText.trim()
  return when {
    trimmed.equals("Permission denied", ignoreCase = true) ->
      language.pick(trimmed, "权限被拒绝")
    trimmed.equals("Address already in use", ignoreCase = true) ->
      language.pick(trimmed, "地址已被占用")
    trimmed.equals("Cannot assign requested address", ignoreCase = true) ->
      language.pick(trimmed, "无法分配请求的地址")
    trimmed.startsWith("bind failed: ", ignoreCase = true) ->
      language.pick(
        trimmed,
        "绑定失败：${localizeRemoteAccessFailureReason(language, trimmed.substringAfter(":").trim())}",
      )
    else -> trimmed
  }
}

private fun localizeSpeechRecognizerStatus(
  language: AppLanguage,
  statusText: String,
): String? {
  val trimmed = statusText.trim()
  speechErrorRegex.matchEntire(trimmed)?.let { match ->
    val errorCode = match.groupValues[1]
    return language.pick(
      english = trimmed,
      simplifiedChinese = "语音错误（$errorCode）",
    )
  }
  return when {
    trimmed.equals("Speech recognizer unavailable", ignoreCase = true) ->
      language.pick("Speech recognizer unavailable", "语音识别器不可用")
    trimmed.equals("Microphone permission required", ignoreCase = true) ->
      language.pick("Microphone permission required", "需要麦克风权限")
    trimmed.equals("Audio error", ignoreCase = true) ->
      language.pick("Audio error", "音频错误")
    trimmed.equals("Client error", ignoreCase = true) ->
      language.pick("Client error", "客户端错误")
    trimmed.equals("Network error", ignoreCase = true) ->
      language.pick("Network error", "网络错误")
    trimmed.equals("Network timeout", ignoreCase = true) ->
      language.pick("Network timeout", "网络超时")
    trimmed.equals("Recognizer busy", ignoreCase = true) ->
      language.pick("Recognizer busy", "识别器忙碌")
    trimmed.equals("Server error", ignoreCase = true) ->
      language.pick("Server error", "服务端错误")
    trimmed.equals("Language not supported on this device", ignoreCase = true) ->
      language.pick("Language not supported on this device", "此设备不支持当前语言")
    trimmed.equals("Language unavailable on this device", ignoreCase = true) ->
      language.pick("Language unavailable on this device", "此设备当前语言暂不可用")
    trimmed.equals("Speech service disconnected", ignoreCase = true) ->
      language.pick("Speech service disconnected", "语音服务已断开")
    trimmed.equals("Speech requests limited; retrying", ignoreCase = true) ->
      language.pick("Speech requests limited; retrying", "语音请求受限；正在重试")
    else -> null
  }
}

internal fun localizeMicCaptureStatus(
  language: AppLanguage,
  statusText: String,
): String {
  val trimmed = statusText.trim()
  listeningQueuedRegex.matchEntire(trimmed)?.let { match ->
    val queuedCount = match.groupValues[1]
    return language.pick(
      english = trimmed,
      simplifiedChinese = "监听中 · $queuedCount 条排队中",
    )
  }
  queuedWaitingRegex.matchEntire(trimmed)?.let { match ->
    val queuedCount = match.groupValues[1]
    return language.pick(
      english = trimmed,
      simplifiedChinese = "$queuedCount 条排队中 · 等待网关",
    )
  }
  localizeSpeechRecognizerStatus(language, trimmed)?.let { return it }
  return when {
    trimmed.equals("Mic off", ignoreCase = true) -> language.pick("Mic off", "麦克风关闭")
    trimmed.equals("Mic off · sending…", ignoreCase = true) || trimmed.equals("Mic off · sending...", ignoreCase = true) ->
      language.pick("Mic off · sending…", "麦克风关闭 · 发送中…")
    trimmed.equals("Listening", ignoreCase = true) -> language.pick("Listening", "监听中")
    trimmed.equals("Listening · sending queued voice", ignoreCase = true) ->
      language.pick("Listening · sending queued voice", "监听中 · 正在发送排队语音")
    trimmed.equals("Sending queued voice", ignoreCase = true) ->
      language.pick("Sending queued voice", "正在发送排队语音")
    trimmed.equals("Voice reply timed out; retrying queued turn", ignoreCase = true) ->
      language.pick("Voice reply timed out; retrying queued turn", "语音回复超时；正在重试排队轮次")
    trimmed.startsWith("Start failed: ", ignoreCase = true) ->
      language.pick(
        english = trimmed,
        simplifiedChinese = "启动失败：${trimmed.removePrefix("Start failed: ").trim()}",
      )
    trimmed.startsWith("Send failed: ", ignoreCase = true) ->
      language.pick(
        english = trimmed,
        simplifiedChinese = "发送失败：${trimmed.removePrefix("Send failed: ").trim()}",
      )
    else -> trimmed
  }
}

internal fun localizeThinkingLevel(
  language: AppLanguage,
  raw: String,
): String {
  return when (raw.trim().lowercase()) {
    "low" -> language.pick("Low", "低")
    "medium" -> language.pick("Medium", "中")
    "high" -> language.pick("High", "高")
    else -> language.pick("Off", "关闭")
  }
}

internal fun localizeOnboardingError(
  language: AppLanguage,
  message: String,
): String {
  val trimmed = message.trim()
  return when (trimmed) {
    "QR code did not contain a valid setup code." ->
      language.pick(trimmed, "二维码里没有有效的 setup code。")
    "Scan QR code first, or use Advanced setup." ->
      language.pick(trimmed, "请先扫描二维码，或改用高级设置。")
    "Setup code has invalid gateway URL." ->
      language.pick(trimmed, "setup code 中的 gateway URL 无效。")
    "Manual endpoint is invalid." ->
      language.pick(trimmed, "手动输入的端点无效。")
    "Invalid gateway URL." ->
      language.pick(trimmed, "gateway URL 无效。")
    "Google Code Scanner could not start. Update Google Play services or use the setup code manually." ->
      language.pick(trimmed, "Google Code Scanner 无法启动。请更新 Google Play 服务，或改为手动输入 setup code。")
    else -> trimmed
  }
}

internal fun onboardingTokenPlaceholder(language: AppLanguage): String =
  language.pick("token", "令牌")

internal fun onboardingPasswordPlaceholder(language: AppLanguage): String =
  language.pick("password", "密码")

internal fun gatewaySetupCodeLabel(language: AppLanguage): String =
  language.pick("Setup Code", "设置码")

internal fun gatewaySetupCodePlaceholder(language: AppLanguage): String =
  language.pick("Paste setup code", "粘贴设置码")

internal fun gatewayHostLabel(language: AppLanguage): String =
  language.pick("Host", "主机")

internal fun gatewayTokenLabel(language: AppLanguage): String =
  language.pick("Token (optional)", "令牌（可选）")

internal fun gatewayPasswordLabel(language: AppLanguage): String =
  language.pick("Password (optional)", "密码（可选）")

internal fun gatewayAdvancedControlsDescription(language: AppLanguage): String =
  language.pick(
    "Setup code, endpoint, TLS, token, password, onboarding.",
    "设置码、端点、TLS、令牌、密码和引导流程。",
  )

internal fun gatewayAdvancedSetupHint(language: AppLanguage): String =
  language.pick("Paste setup code or enter host/port manually.", "粘贴设置码，或手动输入主机/端口。")

internal fun onboardingSmsPermissionTitle(language: AppLanguage): String =
  language.pick("SMS", "短信")

internal fun localizeTalkModeStatus(
  language: AppLanguage,
  statusText: String,
): String {
  val trimmed = statusText.trim()
  localizeSpeechRecognizerStatus(language, trimmed)?.let { localized ->
    if (!trimmed.equals("Listening", ignoreCase = true)) {
      return localized
    }
  }
  return when {
    trimmed.equals("Off", ignoreCase = true) -> language.pick("Off", "关闭")
    trimmed.equals("Listening", ignoreCase = true) -> language.pick("Listening", "监听中")
    trimmed.equals("Thinking…", ignoreCase = true) || trimmed.equals("Thinking...", ignoreCase = true) ->
      language.pick("Thinking…", "思考中…")
    trimmed.equals("Speaking…", ignoreCase = true) || trimmed.equals("Speaking...", ignoreCase = true) ->
      language.pick("Speaking…", "正在说话…")
    trimmed.equals("Speaking (System)…", ignoreCase = true) || trimmed.equals("Speaking (System)...", ignoreCase = true) ->
      language.pick("Speaking (System)…", "正在说话（系统）…")
    trimmed.equals("Ready", ignoreCase = true) -> language.pick("Ready", "就绪")
    trimmed.equals("No reply", ignoreCase = true) -> language.pick("No reply", "没有回复")
    trimmed.equals("Gateway not connected", ignoreCase = true) ->
      language.pick("Gateway not connected", "Gateway 未连接")
    trimmed.startsWith("Start failed: ", ignoreCase = true) ->
      language.pick(
        english = trimmed,
        simplifiedChinese = "启动失败：${localizeTalkModeReason(language, trimmed.substringAfter(":").trim())}",
      )
    trimmed.startsWith("Talk failed: ", ignoreCase = true) ->
      language.pick(
        english = trimmed,
        simplifiedChinese = "对话失败：${localizeTalkModeReason(language, trimmed.substringAfter(":").trim())}",
      )
    trimmed.startsWith("Speak failed: ", ignoreCase = true) ->
      language.pick(
        english = trimmed,
        simplifiedChinese = "播放失败：${localizeTalkModeReason(language, trimmed.substringAfter(":").trim())}",
      )
    else -> trimmed
  }
}

private fun localizeTalkModeReason(
  language: AppLanguage,
  reasonText: String,
): String {
  val trimmed = reasonText.trim()
  localizeChatError(language, trimmed).takeIf { it != trimmed }?.let { return it }
  return when {
    trimmed.equals("system TTS unavailable", ignoreCase = true) ->
      language.pick(trimmed, "系统 TTS 不可用")
    trimmed.equals("AudioTrack init failed", ignoreCase = true) ->
      language.pick(trimmed, "AudioTrack 初始化失败")
    trimmed.startsWith("AudioTrack buffer size invalid: ", ignoreCase = true) ->
      language.pick(
        english = trimmed,
        simplifiedChinese = "AudioTrack 缓冲区大小无效：${trimmed.substringAfter(":").trim()}",
      )
    trimmed.startsWith("AudioTrack write failed: ", ignoreCase = true) ->
      language.pick(
        english = trimmed,
        simplifiedChinese = "AudioTrack 写入失败：${trimmed.substringAfter(":").trim()}",
      )
    trimmed.startsWith("ElevenLabs failed: ", ignoreCase = true) ->
      language.pick(
        english = trimmed,
        simplifiedChinese = "ElevenLabs 失败：${trimmed.substringAfter(":").trim()}",
      )
    trimmed.startsWith("ElevenLabs voices failed: ", ignoreCase = true) ->
      language.pick(
        english = trimmed,
        simplifiedChinese = "ElevenLabs 音色列表获取失败：${trimmed.substringAfter(":").trim()}",
      )
    else -> trimmed
  }
}

private fun localizeRuntimePrefixedError(
  language: AppLanguage,
  message: String,
): String? {
  val trimmed = message.trim()
  val match = runtimePrefixedErrorRegex.matchEntire(trimmed) ?: return null
  val code = match.groupValues[1]
  val detail = match.groupValues[2]
  val localizedCode = localizeRuntimeErrorCode(language, code)
  val localizedDetail = localizeRuntimeErrorDetail(language, detail)
  if (localizedCode == code && localizedDetail == detail.trim()) {
    return null
  }
  return language.pick(trimmed, "$localizedCode：$localizedDetail")
}

private fun localizeRuntimeErrorCode(
  language: AppLanguage,
  code: String,
): String {
  val trimmed = code.trim()
  return when (trimmed) {
    "ACTION_UNAVAILABLE" -> language.pick(trimmed, "操作不可用")
    "A2UI_HOST_UNAVAILABLE" -> language.pick(trimmed, "A2UI Host 不可用")
    "CALENDAR_UNAVAILABLE" -> language.pick(trimmed, "日历不可用")
    "CALL_LOG_UNAVAILABLE" -> language.pick(trimmed, "通话记录不可用")
    "CONTACTS_UNAVAILABLE" -> language.pick(trimmed, "联系人不可用")
    "INVALID_REQUEST" -> language.pick(trimmed, "请求无效")
    "LOCATION_BACKGROUND_UNAVAILABLE" -> language.pick(trimmed, "后台定位不可用")
    "LOCATION_UNAVAILABLE" -> language.pick(trimmed, "位置不可用")
    "MOTION_UNAVAILABLE" -> language.pick(trimmed, "运动不可用")
    "NODE_BACKGROUND_UNAVAILABLE" -> language.pick(trimmed, "后台节点不可用")
    "NOTIFICATIONS_UNAVAILABLE" -> language.pick(trimmed, "通知不可用")
    "PEDOMETER_UNAVAILABLE" -> language.pick(trimmed, "计步器不可用")
    "PHOTOS_UNAVAILABLE" -> language.pick(trimmed, "照片不可用")
    "SMS_UNAVAILABLE" -> language.pick(trimmed, "短信不可用")
    "UI_AUTOMATION_UNAVAILABLE" -> language.pick(trimmed, "UI 自动化不可用")
    "UNAVAILABLE" -> language.pick(trimmed, "不可用")
    else -> trimmed
  }
}

private fun localizeRuntimeErrorDetail(
  language: AppLanguage,
  detail: String,
): String {
  val trimmed = detail.trim()
  localizeConnectionStatus(language, trimmed).takeIf { it != trimmed }?.let { return it }
  localizeRemoteAccessStatus(language, trimmed).takeIf { it != trimmed }?.let { return it }
  localizeRemoteAccessFailureReason(language, trimmed).takeIf { it != trimmed }?.let { return it }
  translateKnownCodexMessage(language, trimmed).takeIf { it != trimmed }?.let { return it }
  localizeRuntimePrefixedError(language, trimmed)?.let { return it }
  return when (trimmed) {
    "A2UI host not reachable" -> language.pick(trimmed, "A2UI Host 无法访问")
    "accessibility service is enabled but not yet bound" ->
      language.pick(trimmed, "无障碍服务已启用，但尚未绑定")
    "canvas unavailable" -> language.pick(trimmed, "Canvas 不可用")
    "message or attachment required" -> language.pick(trimmed, "需要提供消息或附件")
    "not connected" -> language.pick(trimmed, "未连接")
    "paramsJSON required" -> language.pick(trimmed, "需要提供 paramsJSON")
    "Permission denied" -> language.pick(trimmed, "权限被拒绝")
    "request failed" -> language.pick(trimmed, "请求失败")
    "runId required" -> language.pick(trimmed, "需要提供 runId")
    "SMS not available on this device" -> language.pick(trimmed, "此设备不支持短信")
    "unknown command" -> language.pick(trimmed, "未知命令")
    else ->
      when {
        trimmed.startsWith("expected JSON object with ", ignoreCase = true) ->
          language.pick(
            trimmed,
            "需要包含 ${trimmed.removePrefix("expected JSON object with ").trim()} 的 JSON 对象",
          )
        trimmed.equals("expected JSON object", ignoreCase = true) ->
          language.pick(trimmed, "需要 JSON 对象")
        else -> trimmed
      }
  }
}

internal fun localizeChatError(
  language: AppLanguage,
  message: String,
): String {
  val trimmed = message.trim()
  if (trimmed.isEmpty()) return message

  localizeConnectionStatus(language, trimmed).takeIf { it != trimmed }?.let { return it }
  localizeRemoteAccessStatus(language, trimmed).takeIf { it != trimmed }?.let { return it }
  translateKnownCodexMessage(language, trimmed).takeIf { it != trimmed }?.let { return it }
  localizeRuntimePrefixedError(language, trimmed)?.let { return it }

  codexRequestFailedRegex.matchEntire(trimmed)?.let { match ->
    val statusMetadata = match.groupValues.getOrNull(1)?.trim().orEmpty()
    val detailText = match.groupValues.getOrNull(2)?.trim().orEmpty()
    val localizedPrefix =
      if (statusMetadata.isNotEmpty()) {
        "OpenAI Codex 请求失败（$statusMetadata）"
      } else {
        "OpenAI Codex 请求失败"
      }
    if (detailText.isEmpty()) {
      return language.pick(trimmed, localizedPrefix)
    }
    val localizedDetail =
      detailText
        .split("|")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(separator = " | ") { segment ->
          when {
            segment.startsWith("message=") ->
              "message=${localizeChatErrorSegmentValue(language, segment.substringAfter("="))}"
            segment.startsWith("body=") ->
              "响应正文=${segment.substringAfter("=")}"
            else -> localizeChatErrorSegmentValue(language, segment)
          }
        }
    return language.pick(trimmed, "$localizedPrefix | $localizedDetail")
  }
  usageLimitRegex.matchEntire(trimmed)?.let { match ->
    val errorType = match.groupValues.getOrNull(1)?.trim().orEmpty()
    return language.pick(
      trimmed,
      if (errorType.isNotEmpty()) "已达到使用额度上限（errorType=$errorType）" else "已达到使用额度上限。",
    )
  }

  return when (trimmed) {
    "Gateway health not OK; cannot send" ->
      language.pick(trimmed, "Gateway 当前不健康，无法发送。")
    "Event stream interrupted; try refreshing." ->
      language.pick(trimmed, "事件流已中断；请刷新后重试。")
    "Chat failed" ->
      language.pick(trimmed, "聊天失败。")
    "Timed out waiting for a reply; try again or refresh." ->
      language.pick(trimmed, "等待回复超时；请重试或刷新。")
    "OpenAI Codex login required" ->
      language.pick(trimmed, "需要 OpenAI Codex 登录。")
    "OpenAI Codex credential is missing accountId" ->
      language.pick(trimmed, "OpenAI Codex 凭证缺少 accountId。")
    "OpenAI Codex exceeded the local-host tool turn limit" ->
      language.pick(trimmed, "OpenAI Codex 超出了本机 Host 工具轮次上限。")
    "OpenAI Codex returned no final assistant text" ->
      language.pick(trimmed, "OpenAI Codex 没有返回最终 assistant 文本。")
    "OpenAI Codex returned no response body" ->
      language.pick(trimmed, "OpenAI Codex 没有返回响应体。")
    "OpenAI Codex request failed" ->
      language.pick(trimmed, "OpenAI Codex 请求失败。")
    "OpenAI Codex request was rate limited" ->
      language.pick(trimmed, "OpenAI Codex 请求触发了速率限制。")
    else -> trimmed
  }
}

internal fun localizeVoiceConversationText(
  language: AppLanguage,
  text: String,
): String {
  val trimmed = text.trim()
  if (trimmed.isEmpty()) return text
  localizeChatError(language, trimmed).takeIf { it != trimmed }?.let { return it }
  return when (trimmed) {
    "Response aborted" -> language.pick(trimmed, "响应已中止。")
    "Voice request failed" -> language.pick(trimmed, "语音请求失败。")
    else -> text
  }
}

private fun localizeChatErrorSegmentValue(
  language: AppLanguage,
  raw: String,
): String {
  val trimmed = raw.trim()
  return when (trimmed) {
    "Bad Request" -> language.pick(trimmed, "请求无效")
    "Forbidden" -> language.pick(trimmed, "已拒绝")
    "instructions must be set" -> language.pick(trimmed, "必须提供 instructions。")
    "Internal Server Error" -> language.pick(trimmed, "服务器内部错误")
    "The usage limit has been reached" -> language.pick(trimmed, "已达到使用额度上限")
    "Too Many Requests" -> language.pick(trimmed, "请求过多")
    "Unauthorized" -> language.pick(trimmed, "未授权")
    else -> translateKnownCodexMessage(language, trimmed).takeIf { it != trimmed } ?: trimmed
  }
}
