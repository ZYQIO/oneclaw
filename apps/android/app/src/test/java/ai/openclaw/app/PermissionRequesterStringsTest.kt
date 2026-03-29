package ai.openclaw.app

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionRequesterStringsTest {
  @Test
  fun permissionRationaleMessage_translatesToChinese() {
    assertEquals(
      "OpenClaw 需要相机、短信权限才能继续。",
      permissionRationaleMessage(
        AppLanguage.SimplifiedChinese,
        listOf(Manifest.permission.CAMERA, Manifest.permission.SEND_SMS),
      ),
    )
  }

  @Test
  fun permissionSettingsMessage_translatesToChinese() {
    assertEquals(
      "请在 Android 设置中启用麦克风权限后再继续。",
      permissionSettingsMessage(
        AppLanguage.SimplifiedChinese,
        listOf(Manifest.permission.RECORD_AUDIO),
      ),
    )
  }

  @Test
  fun permissionLabelsAndButtons_translateToChinese() {
    assertEquals("相机", permissionLabel(AppLanguage.SimplifiedChinese, Manifest.permission.CAMERA))
    assertEquals("继续", permissionContinueLabel(AppLanguage.SimplifiedChinese))
    assertEquals("稍后", permissionNotNowLabel(AppLanguage.SimplifiedChinese))
    assertEquals("在设置中启用权限", permissionSettingsTitle(AppLanguage.SimplifiedChinese))
    assertEquals("打开设置", permissionOpenSettingsLabel(AppLanguage.SimplifiedChinese))
    assertEquals("取消", permissionCancelLabel(AppLanguage.SimplifiedChinese))
  }
}
