package ai.openclaw.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import ai.openclaw.app.AppLanguage

private val connectedOperatorRegex = Regex("""^Connected \(operator: (.+)\)$""")
private val listeningQueuedRegex = Regex("""^Listening · (\d+) queued$""")
private val queuedWaitingRegex = Regex("""^(\d+) queued · waiting for gateway$""")
private val speechErrorRegex = Regex("""^Speech error \((\d+)\)$""")
private val gatewayErrorRegex = Regex("""^Gateway error: (.+)$""", RegexOption.IGNORE_CASE)
private val gatewayClosedRegex = Regex("""^Gateway closed: (.+)$""", RegexOption.IGNORE_CASE)
private val gatewayClosedWithCodeRegex = Regex("""^gateway closed \((\d+)\): (.+)$""", RegexOption.IGNORE_CASE)
private val gatewayAuthHintRegex = Regex("""^unauthorized: gateway (token|password) (missing|mismatch) \((.+)\)$""", RegexOption.IGNORE_CASE)

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
        simplifiedChinese = "远程访问启动失败：${trimmed.removePrefix("Remote access failed to start: ")}",
      )
    else -> trimmed
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
  speechErrorRegex.matchEntire(trimmed)?.let { match ->
    val errorCode = match.groupValues[1]
    return language.pick(
      english = trimmed,
      simplifiedChinese = "语音错误（$errorCode）",
    )
  }
  return when {
    trimmed.equals("Mic off", ignoreCase = true) -> language.pick("Mic off", "麦克风关闭")
    trimmed.equals("Mic off · sending…", ignoreCase = true) || trimmed.equals("Mic off · sending...", ignoreCase = true) ->
      language.pick("Mic off · sending…", "麦克风关闭 · 发送中…")
    trimmed.equals("Listening", ignoreCase = true) -> language.pick("Listening", "监听中")
    trimmed.equals("Listening · sending queued voice", ignoreCase = true) ->
      language.pick("Listening · sending queued voice", "监听中 · 正在发送排队语音")
    trimmed.equals("Sending queued voice", ignoreCase = true) ->
      language.pick("Sending queued voice", "正在发送排队语音")
    trimmed.equals("Speech recognizer unavailable", ignoreCase = true) ->
      language.pick("Speech recognizer unavailable", "语音识别器不可用")
    trimmed.equals("Microphone permission required", ignoreCase = true) ->
      language.pick("Microphone permission required", "需要麦克风权限")
    trimmed.equals("Voice reply timed out; retrying queued turn", ignoreCase = true) ->
      language.pick("Voice reply timed out; retrying queued turn", "语音回复超时；正在重试排队轮次")
    trimmed.equals("Audio error", ignoreCase = true) -> language.pick("Audio error", "音频错误")
    trimmed.equals("Client error", ignoreCase = true) -> language.pick("Client error", "客户端错误")
    trimmed.equals("Network error", ignoreCase = true) -> language.pick("Network error", "网络错误")
    trimmed.equals("Network timeout", ignoreCase = true) -> language.pick("Network timeout", "网络超时")
    trimmed.equals("Recognizer busy", ignoreCase = true) -> language.pick("Recognizer busy", "识别器忙碌")
    trimmed.equals("Server error", ignoreCase = true) -> language.pick("Server error", "服务端错误")
    trimmed.equals("Language not supported on this device", ignoreCase = true) ->
      language.pick("Language not supported on this device", "此设备不支持当前语言")
    trimmed.equals("Language unavailable on this device", ignoreCase = true) ->
      language.pick("Language unavailable on this device", "此设备当前语言暂不可用")
    trimmed.equals("Speech service disconnected", ignoreCase = true) ->
      language.pick("Speech service disconnected", "语音服务已断开")
    trimmed.equals("Speech requests limited; retrying", ignoreCase = true) ->
      language.pick("Speech requests limited; retrying", "语音请求受限；正在重试")
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
