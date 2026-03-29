package ai.openclaw.app.auth

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import ai.openclaw.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OpenAICodexAuthManagerTest {
  @Test
  fun localizeOpenAICodexAuthCopy_translatesLoginStatusCopy() {
    assertEquals(
      "浏览器已打开，请在浏览器中完成登录。回调后 OpenClaw 应该会自动返回。如果没有返回，请在浏览器页点击“返回 OpenClaw”或在下方粘贴重定向 URL 或代码。",
      localizeOpenAICodexAuthCopy(
        "Browser opened. Finish sign-in there. OpenClaw should return automatically after the callback. If it doesn't, use Return to OpenClaw in the browser page or paste the redirect URL or code below.",
      ),
    )
  }

  @Test
  fun localizeOpenAICodexAuthCopy_translatesAuthFailureCopy() {
    assertEquals(
      "当前没有可用的浏览器来完成 OpenAI 登录。",
      localizeOpenAICodexAuthCopy("No browser is available for OpenAI sign-in."),
    )
    assertEquals(
      "正在交换授权码…",
      localizeOpenAICodexAuthCopy("Exchanging authorization code…"),
    )
    assertEquals(
      "OpenAI Codex 已连接。",
      localizeOpenAICodexAuthCopy("OpenAI Codex is connected."),
    )
  }

  @Test
  fun oauthSuccessHtml_includesReturnToOpenClawCta() {
    val html = oauthSuccessHtml("OpenAI authentication completed. You can close this window.")

    assertTrue(html.contains("Return to OpenClaw / 返回 OpenClaw"))
    assertTrue(html.contains("OpenClaw should reopen automatically"))
    assertTrue(html.contains("OpenAI 授权已完成。你可以关闭这个窗口。"))
    assertTrue(html.contains(buildOpenAICodexAppReturnUri()))
  }

  @Test
  fun oauthErrorHtml_includesLocalizedMissingAuthorizationCodeMessage() {
    val html = oauthErrorHtml("Missing authorization code.")

    assertTrue(html.contains("Missing authorization code."))
    assertTrue(html.contains("缺少授权码。"))
  }

  @Test
  fun oauthErrorHtml_includesLocalizedLoopbackOnlyMessage() {
    val html = oauthErrorHtml("Only loopback callbacks are allowed.")

    assertTrue(html.contains("Only loopback callbacks are allowed."))
    assertTrue(html.contains("只允许 loopback 回调。"))
  }

  @Test
  fun oauthErrorHtml_includesLocalizedCallbackRouteNotFoundMessage() {
    val html = oauthErrorHtml("Callback route not found.")

    assertTrue(html.contains("Callback route not found."))
    assertTrue(html.contains("未找到回调路由。"))
  }

  @Test
  fun mainActivity_resolvesOpenClawAuthCallbackDeepLink() {
    val context = RuntimeEnvironment.getApplication()
    val intent =
      Intent(Intent.ACTION_VIEW, Uri.parse(openAICodexAppCallbackUri)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        addCategory(Intent.CATEGORY_DEFAULT)
      }

    val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)

    assertNotNull(resolved)
    assertEquals(MainActivity::class.java.name, resolved?.activityInfo?.name)
  }
}
