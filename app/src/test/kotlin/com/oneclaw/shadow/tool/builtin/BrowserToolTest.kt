package com.oneclaw.shadow.tool.builtin

import com.oneclaw.shadow.core.model.ToolResultStatus
import com.oneclaw.shadow.tool.browser.WebViewManager
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BrowserToolTest {

    private lateinit var webViewManager: WebViewManager
    private lateinit var tool: BrowserTool

    @Before
    fun setup() {
        webViewManager = mockk()
        tool = BrowserTool(mockk(relaxed = true), webViewManager)
    }

    @Test
    fun testDefinition() {
        val def = tool.definition
        assertEquals("browser", def.name)
        val required = def.parametersSchema.required
        assertTrue("url must be required", required.contains("url"))
        assertTrue("mode must be required", required.contains("mode"))
    }

    @Test
    fun testExecute_missingUrl() = runTest {
        val result = tool.execute(mapOf("mode" to "screenshot"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertTrue(result.errorMessage!!.contains("url"))
    }

    @Test
    fun testExecute_missingMode() = runTest {
        val result = tool.execute(mapOf("url" to "https://example.com"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertTrue(result.errorMessage!!.contains("mode"))
    }

    @Test
    fun testExecute_invalidUrl() = runTest {
        val result = tool.execute(mapOf("url" to "not-a-valid-url", "mode" to "screenshot"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
    }

    @Test
    fun testExecute_invalidMode() = runTest {
        val result = tool.execute(mapOf("url" to "https://example.com", "mode" to "invalid"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("invalid"))
    }

    @Test
    fun testExecute_nonHttpScheme() = runTest {
        val result = tool.execute(mapOf("url" to "file:///etc/passwd", "mode" to "screenshot"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
    }
}
