package ai.openclaw.app

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugLocalHostTokenExporterTest {
  @Test
  fun writeTokenSnapshot_trimsAndPersistsToken() {
    val cacheDir = createTempDirectory("openclaw-token-export.").toFile()
    val token = "  ocrt_${"a".repeat(64)}  "

    val file = DebugLocalHostTokenExporter.writeTokenSnapshot(cacheDir, token)

    assertEquals(File(cacheDir, DebugLocalHostTokenExporter.cacheFileName), file)
    assertEquals("ocrt_${"a".repeat(64)}\n", file.readText())
  }

  @Test
  fun validateToken_rejectsUnexpectedFormats() {
    val error =
      runCatching { DebugLocalHostTokenExporter.validateToken("not-a-real-token") }
        .exceptionOrNull()

    assertTrue(error is IllegalArgumentException)
    assertEquals("Invalid local-host remote access token.", error?.message)
  }
}
