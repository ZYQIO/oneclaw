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
