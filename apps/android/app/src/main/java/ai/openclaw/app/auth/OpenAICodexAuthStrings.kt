package ai.openclaw.app.auth

import ai.openclaw.app.AppLanguage

private val codexOauthFailureRegex =
  Regex("""^OpenAI Codex OAuth failed \(([^)]+)\)(?:\s*\|\s*(.+))?$""")

private fun pick(
  language: AppLanguage,
  english: String,
  simplifiedChinese: String,
): String {
  return when (language) {
    AppLanguage.English -> english
    AppLanguage.SimplifiedChinese -> simplifiedChinese
  }
}

internal fun translateOpenAICodexMessage(
  language: AppLanguage,
  message: String,
): String {
  val trimmed = message.trim()
  codexOauthFailureRegex.matchEntire(trimmed)?.let { match ->
    val status = match.groupValues[1]
    val detailText = match.groupValues.getOrNull(2)?.trim().orEmpty()
    val localizedPrefix = pick(language, trimmed, "OpenAI Codex OAuth 失败（$status）")
    if (detailText.isEmpty()) return localizedPrefix
    return pick(
      language,
      trimmed,
      "$localizedPrefix | ${localizeOpenAICodexAuthDetailSegments(language, detailText)}",
    )
  }

  return when (trimmed) {
    "Browser opened. Finish sign-in there. OpenClaw should return automatically after the callback. If it doesn't, use Return to OpenClaw in the browser page or paste the redirect URL or code below." ->
      pick(
        language,
        trimmed,
        "浏览器已打开，请在浏览器中完成登录。回调后 OpenClaw 应该会自动返回。如果没有返回，请在浏览器页点击“返回 OpenClaw”或在下方粘贴重定向 URL 或代码。",
      )
    "Couldn't bind the localhost callback. Finish sign-in in the browser, then paste the redirect URL or code below." ->
      pick(
        language,
        trimmed,
        "无法绑定本地回调。请在浏览器中完成登录，然后粘贴重定向 URL 或代码。",
      )
    "No browser is available for OpenAI sign-in." ->
      pick(language, trimmed, "当前没有可用的浏览器来完成 OpenAI 登录。")
    "Failed to open the OpenAI sign-in page." ->
      pick(language, trimmed, "无法打开 OpenAI 登录页面。")
    "No OpenAI sign-in is currently running." ->
      pick(language, trimmed, "当前没有正在进行的 OpenAI 登录。")
    "State mismatch. Start sign-in again and retry." ->
      pick(language, trimmed, "state 不匹配，请重新开始登录后再试。")
    "Paste the redirect URL or authorization code from the browser." ->
      pick(language, trimmed, "请粘贴浏览器中的重定向 URL 或授权码。")
    "Exchanging authorization code…" ->
      pick(language, trimmed, "正在交换授权码…")
    "OpenAI Codex is connected." ->
      pick(language, trimmed, "OpenAI Codex 已连接。")
    "OpenAI sign-in failed." ->
      pick(language, trimmed, "OpenAI 登录失败。")
    "OpenAI authentication completed. You can close this window." ->
      pick(language, trimmed, "OpenAI 授权已完成。你可以关闭这个窗口。")
    "Only loopback callbacks are allowed." ->
      pick(language, trimmed, "只允许 loopback 回调。")
    "Callback route not found." ->
      pick(language, trimmed, "未找到回调路由。")
    "State mismatch." ->
      pick(language, trimmed, "state 不匹配。")
    "Missing authorization code." ->
      pick(language, trimmed, "缺少授权码。")
    "OpenAI Codex OAuth returned invalid JSON" ->
      pick(language, trimmed, "OpenAI Codex OAuth 返回了无效 JSON。")
    "OpenAI Codex OAuth response was missing required fields" ->
      pick(language, trimmed, "OpenAI Codex OAuth 响应缺少必需字段。")
    "Failed to extract accountId from token" ->
      pick(language, trimmed, "无法从 token 中提取 accountId。")
    else ->
      when {
        trimmed.startsWith("Invalid OpenAI authorize URL: ") ->
          pick(
            language,
            trimmed,
            "OpenAI authorize URL 无效：${trimmed.removePrefix("Invalid OpenAI authorize URL: ")}",
          )
        else -> message
      }
  }
}

private fun localizeOpenAICodexAuthDetailSegments(
  language: AppLanguage,
  detailText: String,
): String {
  return detailText
    .split("|")
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .joinToString(separator = " | ") { segment ->
      localizeOpenAICodexAuthDetailSegment(language, segment)
    }
}

private fun localizeOpenAICodexAuthDetailSegment(
  language: AppLanguage,
  segment: String,
): String {
  return when {
    segment.startsWith("error=") ->
      pick(
        language,
        segment,
        "error=${localizeOpenAICodexAuthDetailValue(language, segment.substringAfter("="))}",
      )
    segment.startsWith("message=") ->
      pick(
        language,
        segment,
        "message=${localizeOpenAICodexAuthDetailValue(language, segment.substringAfter("="))}",
      )
    segment.startsWith("body=") ->
      pick(language, segment, "响应正文=${segment.substringAfter("=")}")
    else -> localizeOpenAICodexAuthDetailValue(language, segment)
  }
}

private fun localizeOpenAICodexAuthDetailValue(
  language: AppLanguage,
  raw: String,
): String {
  val trimmed = raw.trim()
  return when (trimmed) {
    "access_denied" -> pick(language, trimmed, "访问被拒绝")
    "invalid_client" -> pick(language, trimmed, "客户端无效")
    "invalid_grant" -> pick(language, trimmed, "授权码或刷新令牌无效")
    "invalid_request" -> pick(language, trimmed, "请求无效")
    "invalid_scope" -> pick(language, trimmed, "请求的 scope 无效")
    "server_error" -> pick(language, trimmed, "服务器错误")
    "temporarily_unavailable" -> pick(language, trimmed, "服务暂时不可用")
    "unauthorized_client" -> pick(language, trimmed, "客户端未获授权")
    "unsupported_grant_type" -> pick(language, trimmed, "不支持的 grant_type")
    "unsupported_response_type" -> pick(language, trimmed, "不支持的 response_type")
    "Bad Request" -> pick(language, trimmed, "请求无效")
    "Forbidden" -> pick(language, trimmed, "已拒绝")
    "Internal Server Error" -> pick(language, trimmed, "服务器内部错误")
    "Too Many Requests" -> pick(language, trimmed, "请求过多")
    "Unauthorized" -> pick(language, trimmed, "未授权")
    else -> translateOpenAICodexMessage(language, trimmed).takeIf { it != trimmed } ?: trimmed
  }
}

internal fun localizeOpenAICodexAuthCopy(message: String): String? {
  val localized = translateOpenAICodexMessage(AppLanguage.SimplifiedChinese, message)
  return localized.takeIf { it != message }
}
