package ai.openclaw.app

enum class AppLanguage(val rawValue: String) {
  English("en"),
  SimplifiedChinese("zh-CN"),
  ;

  companion object {
    fun fromRawValue(raw: String?): AppLanguage {
      val normalized = raw?.trim().orEmpty()
      return when {
        normalized.equals(SimplifiedChinese.rawValue, ignoreCase = true) -> SimplifiedChinese
        normalized.equals("zh", ignoreCase = true) -> SimplifiedChinese
        normalized.equals("zh_cn", ignoreCase = true) -> SimplifiedChinese
        else -> English
      }
    }
  }
}
