package com.oneclaw.shadow.tool.builtin

import com.oneclaw.shadow.core.model.ToolResultStatus
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WebfetchToolTest {

    private lateinit var server: MockWebServer
    private lateinit var tool: WebfetchTool

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        tool = WebfetchTool(OkHttpClient())
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun testDefinition() {
        assertEquals("webfetch", tool.definition.name)
        assertTrue(tool.definition.parametersSchema.required.contains("url"))
        assertTrue(tool.definition.parametersSchema.properties.containsKey("url"))
        assertTrue(tool.definition.parametersSchema.properties.containsKey("max_length"))
    }

    @Test
    fun testExecute_missingUrl() = runTest {
        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertTrue(result.errorMessage!!.contains("url", ignoreCase = true))
    }

    @Test
    fun testExecute_invalidUrl() = runTest {
        val result = tool.execute(mapOf("url" to "not a valid url!!!"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("invalid_url", result.errorType)
    }

    @Test
    fun testExecute_nonHttpScheme() = runTest {
        val result = tool.execute(mapOf("url" to "file:///etc/passwd"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("invalid_url", result.errorType)
        assertTrue(result.errorMessage!!.contains("HTTP", ignoreCase = true))
    }

    @Test
    fun testExecute_httpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))

        val url = server.url("/missing").toString()
        val result = tool.execute(mapOf("url" to url))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("http_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("404"))
    }

    @Test
    fun testExecute_success_html() = runTest {
        val html = """
            <html>
            <head><title>Test Page</title></head>
            <body>
            <article>
            <h1>Hello World</h1>
            <p>This is test content.</p>
            </article>
            </body>
            </html>
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(html)
        )

        val url = server.url("/page").toString()
        val result = tool.execute(mapOf("url" to url))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("Hello World"))
        assertTrue(result.result!!.contains("This is test content"))
    }

    @Test
    fun testExecute_success_nonHtml() = runTest {
        val textContent = "This is plain text content, not HTML."

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/plain; charset=utf-8")
                .setBody(textContent)
        )

        val url = server.url("/text").toString()
        val result = tool.execute(mapOf("url" to url))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertEquals(textContent, result.result)
    }

    @Test
    fun testExecute_truncation() = runTest {
        // Create content that is longer than the default max_length (50000)
        val longContent = "A".repeat(60_000)
        val html = "<html><body><p>$longContent</p></body></html>"

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(html)
        )

        val url = server.url("/long").toString()
        val result = tool.execute(mapOf("url" to url))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("[Content truncated at"))
        assertTrue(result.result!!.length < 60_000)
    }

    @Test
    fun testExecute_customMaxLength() = runTest {
        val content = "Hello World! ".repeat(100)
        val html = "<html><body><p>$content</p></body></html>"

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(html)
        )

        val url = server.url("/content").toString()
        val result = tool.execute(mapOf("url" to url, "max_length" to 100))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("[Content truncated at 100 characters]"))
    }
}
