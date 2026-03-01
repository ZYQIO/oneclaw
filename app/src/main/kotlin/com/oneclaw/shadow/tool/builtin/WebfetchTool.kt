package com.oneclaw.shadow.tool.builtin

import android.util.Log
import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.tool.engine.Tool
import com.oneclaw.shadow.tool.util.HtmlToMarkdownConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Kotlin-native webfetch tool that fetches web pages and converts
 * HTML to Markdown using Jsoup. Replaces the JS webfetch implementation.
 */
class WebfetchTool(
    private val okHttpClient: OkHttpClient
) : Tool {

    companion object {
        private const val TAG = "WebfetchTool"
        private const val DEFAULT_MAX_LENGTH = 50_000
        private const val MAX_RESPONSE_SIZE = 5 * 1024 * 1024  // 5MB
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    override val definition = ToolDefinition(
        name = "webfetch",
        description = "Fetch a web page and return its content as Markdown",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "url" to ToolParameter(
                    type = "string",
                    description = "The URL to fetch"
                ),
                "max_length" to ToolParameter(
                    type = "integer",
                    description = "Maximum output length in characters. Default: 50000"
                )
            ),
            required = listOf("url")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 30
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val url = parameters["url"]?.toString()
            ?: return ToolResult.error("validation_error", "Parameter 'url' is required")

        val maxLength = (parameters["max_length"] as? Number)?.toInt()
            ?: DEFAULT_MAX_LENGTH

        // Validate URL scheme
        val parsedUrl = try {
            java.net.URL(url)
        } catch (e: Exception) {
            return ToolResult.error("invalid_url", "Invalid URL: ${e.message}")
        }

        if (parsedUrl.protocol !in listOf("http", "https")) {
            return ToolResult.error("invalid_url", "Only HTTP and HTTPS URLs are supported")
        }

        return try {
            val response = fetchUrl(url)
            processResponse(response, maxLength)
        } catch (e: java.net.SocketTimeoutException) {
            ToolResult.error("timeout", "Request timed out: ${e.message}")
        } catch (e: java.net.UnknownHostException) {
            ToolResult.error("network_error", "DNS resolution failed: ${e.message}")
        } catch (e: java.io.IOException) {
            ToolResult.error("network_error", "Network error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error fetching $url", e)
            ToolResult.error("error", "Error: ${e.message}")
        }
    }

    private suspend fun fetchUrl(url: String): okhttp3.Response {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.5")
            .build()

        return withContext(Dispatchers.IO) {
            okHttpClient.newCall(request).execute()
        }
    }

    private fun processResponse(
        response: okhttp3.Response,
        maxLength: Int
    ): ToolResult {
        if (!response.isSuccessful) {
            val body = response.body?.string()?.take(1000) ?: ""
            return ToolResult.error(
                "http_error",
                "HTTP ${response.code}: ${response.message}\n$body"
            )
        }

        val contentType = response.header("Content-Type")?.lowercase() ?: ""
        val body = response.body?.let { responseBody ->
            // Limit response size to prevent OOM
            val source = responseBody.source()
            source.request(MAX_RESPONSE_SIZE.toLong())
            val buffer = source.buffer
            if (buffer.size > MAX_RESPONSE_SIZE) {
                buffer.snapshot(MAX_RESPONSE_SIZE).utf8()
            } else {
                responseBody.string()
            }
        } ?: return ToolResult.error("empty_response", "Empty response body")

        // Non-HTML: return raw body (truncated)
        if (!contentType.contains("text/html") && !contentType.contains("application/xhtml")) {
            return ToolResult.success(truncateText(body, maxLength))
        }

        // HTML: parse and convert to Markdown
        val markdown = HtmlToMarkdownConverter.convert(body, response.request.url.toString())
        return ToolResult.success(truncateText(markdown, maxLength))
    }

    private fun truncateText(text: String, maxLength: Int): String {
        if (maxLength <= 0 || text.length <= maxLength) return text

        // Find the last paragraph/block boundary before the limit
        val truncateAt = text.lastIndexOf("\n\n", maxLength)
        val cutoff = if (truncateAt > maxLength / 2) truncateAt else maxLength

        return text.substring(0, cutoff) + "\n\n[Content truncated at $maxLength characters]"
    }
}
