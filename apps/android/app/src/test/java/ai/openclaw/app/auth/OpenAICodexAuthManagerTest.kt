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

    assertTrue(html.contains("Return to OpenClaw"))
    assertTrue(html.contains("OpenClaw should reopen automatically"))
    assertTrue(html.contains(buildOpenAICodexAppReturnUri()))
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
