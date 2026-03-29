package ai.openclaw.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NodeForegroundServiceStringsTest {
  @Test
  fun foregroundNotificationActionLabel_translatesToChinese() {
    assertEquals("断开连接", foregroundNotificationActionLabel(AppLanguage.SimplifiedChinese))
  }

  @Test
  fun foregroundNotificationChannelMetadata_translatesToChinese() {
    assertEquals("连接", foregroundNotificationChannelName(AppLanguage.SimplifiedChinese))
    assertEquals(
      "OpenClaw 节点连接状态",
      foregroundNotificationChannelDescription(AppLanguage.SimplifiedChinese),
    )
  }

  @Test
  fun foregroundNotificationStartingText_translatesToChinese() {
    assertEquals("启动中…", foregroundNotificationStartingText(AppLanguage.SimplifiedChinese))
  }

  @Test
  fun foregroundNotificationTitle_translatesConnectedState() {
    assertEquals(
      "OpenClaw 节点 · 已连接",
      foregroundNotificationTitle(AppLanguage.SimplifiedChinese, connected = true),
    )
  }

  @Test
  fun foregroundNotificationText_translatesStatusServerAndMicState() {
    assertEquals(
      "已连接 · edge-host · 麦克风：监听中",
      foregroundNotificationText(
        language = AppLanguage.SimplifiedChinese,
        statusText = "Connected",
        serverName = "edge-host",
        micEnabled = true,
        micListening = true,
      ),
    )
  }

  @Test
  fun foregroundNotificationText_reusesKnownConnectionStatusTranslation() {
    assertEquals(
      "网关错误：未授权：gateway token 不匹配（提供 gateway auth token）",
      foregroundNotificationText(
        language = AppLanguage.SimplifiedChinese,
        statusText = "Gateway error: unauthorized: gateway token mismatch (provide gateway auth token)",
        serverName = null,
        micEnabled = false,
        micListening = false,
      ),
    )
  }
}
