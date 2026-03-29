package ai.openclaw.app.ui.chat

import ai.openclaw.app.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMarkdownStringsTest {
  @Test
  fun inlineImageContentDescription_translatesFallbackLabel() {
    assertEquals("图片", inlineImageContentDescription(AppLanguage.SimplifiedChinese, null))
  }

  @Test
  fun inlineImageUnavailableLabel_translatesToChinese() {
    assertEquals("图片不可用", inlineImageUnavailableLabel(AppLanguage.SimplifiedChinese))
  }
}
