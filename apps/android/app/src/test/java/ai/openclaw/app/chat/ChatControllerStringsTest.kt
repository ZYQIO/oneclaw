package ai.openclaw.app.chat

import ai.openclaw.app.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatControllerStringsTest {
  @Test
  fun attachmentOnlyMessagePlaceholder_translatesToChinese() {
    assertEquals("见附件。", attachmentOnlyMessagePlaceholder(AppLanguage.SimplifiedChinese))
  }

  @Test
  fun attachmentOnlyMessagePlaceholder_keepsEnglishCopy() {
    assertEquals("See attached.", attachmentOnlyMessagePlaceholder(AppLanguage.English))
  }
}
