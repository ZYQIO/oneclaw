package com.oneclaw.shadow.tool.builtin

import android.content.Context
import android.util.Log
import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.tool.browser.WebViewManager
import com.oneclaw.shadow.tool.engine.Tool

/**
 * Located in: tool/builtin/BrowserTool.kt
 *
 * WebView-based browser tool that renders web pages and provides
 * screenshot capture and content extraction capabilities.
 */
class BrowserTool(
    private val context: Context,
    private val webViewManager: WebViewManager
) : Tool {

    companion object {
        private const val TAG = "BrowserTool"
        private const val DEFAULT_WIDTH = 412
        private const val DEFAULT_HEIGHT = 915
        private const val DEFAULT_WAIT_SECONDS = 2.0
        private const val DEFAULT_MAX_LENGTH = 50_000
        private const val TOOL_TIMEOUT_SECONDS = 60
    }

    override val definition = ToolDefinition(
        name = "browser",
        description = "Render a web page in a browser, then take a screenshot or extract content. " +
            "Use 'screenshot' mode to capture a visual image of the page. " +
            "Use 'extract' mode to get the page content as Markdown after JavaScript rendering. " +
            "Prefer 'webfetch' for static pages; use 'browser' when content requires JavaScript rendering.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "url" to ToolParameter(
                    type = "string",
                    description = "The URL to load in the browser"
                ),
                "mode" to ToolParameter(
                    type = "string",
                    description = "Operation mode: 'screenshot' to capture an image, 'extract' to get page content as Markdown",
                    enum = listOf("screenshot", "extract")
                ),
                "width" to ToolParameter(
                    type = "integer",
                    description = "Viewport width in pixels (screenshot mode). Default: 412"
                ),
                "height" to ToolParameter(
                    type = "integer",
                    description = "Viewport height in pixels (screenshot mode). Default: 915"
                ),
                "wait_seconds" to ToolParameter(
                    type = "number",
                    description = "Seconds to wait after page load for JavaScript rendering. Default: 2"
                ),
                "full_page" to ToolParameter(
                    type = "boolean",
                    description = "Capture full scrollable page instead of just viewport (screenshot mode). Default: false"
                ),
                "max_length" to ToolParameter(
                    type = "integer",
                    description = "Maximum output length in characters (extract mode). Default: 50000"
                ),
                "javascript" to ToolParameter(
                    type = "string",
                    description = "Custom JavaScript to execute in extract mode. Must return a string."
                )
            ),
            required = listOf("url", "mode")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = TOOL_TIMEOUT_SECONDS
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val url = parameters["url"]?.toString()
            ?: return ToolResult.error("validation_error", "Parameter 'url' is required")
        val mode = parameters["mode"]?.toString()
            ?: return ToolResult.error("validation_error", "Parameter 'mode' is required")

        // Validate URL
        try {
            val parsedUrl = java.net.URL(url)
            if (parsedUrl.protocol !in listOf("http", "https")) {
                return ToolResult.error("validation_error", "Only HTTP and HTTPS URLs are supported")
            }
        } catch (e: Exception) {
            return ToolResult.error("validation_error", "Invalid URL: ${e.message}")
        }

        return when (mode) {
            "screenshot" -> executeScreenshot(url, parameters)
            "extract" -> executeExtract(url, parameters)
            else -> ToolResult.error("validation_error", "Invalid mode '$mode'. Use 'screenshot' or 'extract'")
        }
    }

    private suspend fun executeScreenshot(
        url: String,
        parameters: Map<String, Any?>
    ): ToolResult {
        val width = (parameters["width"] as? Number)?.toInt() ?: DEFAULT_WIDTH
        val height = (parameters["height"] as? Number)?.toInt() ?: DEFAULT_HEIGHT
        val waitSeconds = (parameters["wait_seconds"] as? Number)?.toDouble() ?: DEFAULT_WAIT_SECONDS
        val fullPage = parameters["full_page"] as? Boolean ?: false

        return try {
            val filePath = webViewManager.captureScreenshot(
                url = url,
                width = width,
                height = height,
                waitSeconds = waitSeconds,
                fullPage = fullPage
            )
            ToolResult.success("""{"image_path": "$filePath"}""")
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot failed for $url", e)
            ToolResult.error("screenshot_error", "Screenshot failed: ${e.message}")
        }
    }

    private suspend fun executeExtract(
        url: String,
        parameters: Map<String, Any?>
    ): ToolResult {
        val waitSeconds = (parameters["wait_seconds"] as? Number)?.toDouble() ?: DEFAULT_WAIT_SECONDS
        val maxLength = (parameters["max_length"] as? Number)?.toInt() ?: DEFAULT_MAX_LENGTH
        val customJs = parameters["javascript"]?.toString()

        return try {
            val content = webViewManager.extractContent(
                url = url,
                waitSeconds = waitSeconds,
                customJavascript = customJs,
                maxLength = maxLength
            )
            ToolResult.success(content)
        } catch (e: Exception) {
            Log.e(TAG, "Content extraction failed for $url", e)
            ToolResult.error("extraction_error", "Extraction failed: ${e.message}")
        }
    }
}
