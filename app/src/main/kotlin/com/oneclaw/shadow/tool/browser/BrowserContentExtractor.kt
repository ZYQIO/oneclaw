package com.oneclaw.shadow.tool.browser

import android.content.Context
import android.util.Log
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Located in: tool/browser/BrowserContentExtractor.kt
 *
 * Extracts content from a rendered WebView page using evaluateJavascript().
 * Uses Turndown (loaded into the WebView context) for HTML-to-Markdown conversion.
 */
class BrowserContentExtractor(private val context: Context) {

    companion object {
        private const val TAG = "BrowserContentExtractor"
    }

    // Built-in extraction script loaded from assets
    private val extractionScript: String by lazy {
        context.assets.open("js/browser/extract.js")
            .bufferedReader().use { it.readText() }
    }

    // Turndown library source loaded from assets
    private val turndownSource: String by lazy {
        context.assets.open("js/lib/turndown.min.js")
            .bufferedReader().use { it.readText() }
    }

    /**
     * Extract page content as Markdown using the built-in extraction script.
     * Injects Turndown into the page context for DOM-based HTML-to-Markdown conversion.
     */
    suspend fun extractAsMarkdown(webView: WebView): String {
        return withContext(Dispatchers.Main) {
            // First inject Turndown into the page context
            evaluateJs(webView, turndownSource)

            // Then run the extraction script
            val result = evaluateJs(webView, extractionScript)

            // The extraction script returns a JSON-encoded string
            result.let {
                // evaluateJavascript returns JSON-encoded strings (with surrounding quotes)
                if (it.startsWith("\"") && it.endsWith("\"")) {
                    // Unescape the JSON string
                    kotlinx.serialization.json.Json.decodeFromString<String>(it)
                } else if (it == "null" || it.isBlank()) {
                    ""
                } else {
                    it
                }
            }
        }
    }

    /**
     * Execute custom JavaScript in the WebView and return the result.
     */
    suspend fun executeCustomJs(webView: WebView, javascript: String): String {
        return withContext(Dispatchers.Main) {
            val wrappedJs = """
                (function() {
                    try {
                        var result = (function() { $javascript })();
                        return (typeof result === 'string') ? result : JSON.stringify(result);
                    } catch(e) {
                        return 'JavaScript error: ' + e.message;
                    }
                })()
            """.trimIndent()

            val result = evaluateJs(webView, wrappedJs)

            if (result.startsWith("\"") && result.endsWith("\"")) {
                kotlinx.serialization.json.Json.decodeFromString<String>(result)
            } else if (result == "null") {
                ""
            } else {
                result
            }
        }
    }

    /**
     * Evaluate JavaScript in the WebView and suspend until result is available.
     */
    private suspend fun evaluateJs(webView: WebView, script: String): String {
        return suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(script) { result ->
                if (continuation.isActive) {
                    continuation.resume(result ?: "null") {}
                }
            }
        }
    }
}
