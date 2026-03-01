package com.oneclaw.shadow.tool.browser

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Located in: tool/browser/WebViewManager.kt
 *
 * Manages an off-screen WebView instance for page rendering,
 * screenshot capture, and content extraction.
 *
 * Key design decisions:
 * - WebView is created lazily on first use
 * - WebView is reused across tool calls within the same session
 * - WebView state is cleaned between uses (cookies, cache, history)
 * - WebView is destroyed after 5 minutes of inactivity
 * - All WebView operations happen on the main thread (Android requirement)
 */
class WebViewManager(
    private val context: Context,
    private val screenshotCapture: BrowserScreenshotCapture,
    private val contentExtractor: BrowserContentExtractor
) {
    companion object {
        private const val TAG = "WebViewManager"
        private const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L  // 5 minutes
        private const val MAX_FULL_PAGE_HEIGHT = 10_000  // pixels
        private const val DEFAULT_WIDTH = 412
        private const val DEFAULT_HEIGHT = 915
    }

    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var idleCleanupRunnable: Runnable? = null

    /**
     * Load a URL, wait for rendering, and capture a screenshot.
     * Returns the file path to the saved PNG.
     */
    suspend fun captureScreenshot(
        url: String,
        width: Int,
        height: Int,
        waitSeconds: Double,
        fullPage: Boolean
    ): String {
        val wv = getOrCreateWebView(width, height)

        // Load URL and wait for page load
        loadUrlAndWait(wv, url)

        // Wait for JavaScript rendering
        delay((waitSeconds * 1000).toLong())

        // Capture screenshot
        val bitmap = if (fullPage) {
            screenshotCapture.captureFullPage(wv, MAX_FULL_PAGE_HEIGHT)
        } else {
            screenshotCapture.captureViewport(wv)
        }

        // Save to file
        val file = File(context.cacheDir, "browser_screenshot_${System.currentTimeMillis()}.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()

        // Schedule cleanup
        scheduleIdleCleanup()
        resetWebView(wv)

        return file.absolutePath
    }

    /**
     * Load a URL, wait for rendering, and extract page content.
     * Returns the extracted text (Markdown or custom JS result).
     */
    suspend fun extractContent(
        url: String,
        waitSeconds: Double,
        customJavascript: String?,
        maxLength: Int
    ): String {
        val wv = getOrCreateWebView(DEFAULT_WIDTH, DEFAULT_HEIGHT)

        // Load URL and wait for page load
        loadUrlAndWait(wv, url)

        // Wait for JavaScript rendering
        delay((waitSeconds * 1000).toLong())

        // Extract content
        val content = if (customJavascript != null) {
            contentExtractor.executeCustomJs(wv, customJavascript)
        } else {
            contentExtractor.extractAsMarkdown(wv)
        }

        // Truncate if needed
        val result = if (maxLength > 0 && content.length > maxLength) {
            val truncateAt = content.lastIndexOf("\n\n", maxLength)
            val cutoff = if (truncateAt > maxLength / 2) truncateAt else maxLength
            content.substring(0, cutoff) + "\n\n[Content truncated at $maxLength characters]"
        } else {
            content
        }

        // Schedule cleanup
        scheduleIdleCleanup()
        resetWebView(wv)

        return result
    }

    /**
     * Get or create the off-screen WebView on the main thread.
     */
    private suspend fun getOrCreateWebView(width: Int, height: Int): WebView {
        return withContext(Dispatchers.Main) {
            webView?.also {
                it.layoutParams = ViewGroup.LayoutParams(width, height)
                it.layout(0, 0, width, height)
            } ?: run {
                val wv = WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(width, height)
                    layout(0, 0, width, height)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        // Disable zoom controls for clean rendering
                        builtInZoomControls = false
                        displayZoomControls = false
                        // Allow mixed content for pages with mixed HTTP/HTTPS resources
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        // Set a reasonable user agent
                        userAgentString = "Mozilla/5.0 (Linux; Android 14) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/120.0.0.0 Mobile Safari/537.36"
                    }

                    // Block navigation to non-HTTP schemes
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val scheme = request?.url?.scheme?.lowercase()
                            return scheme !in listOf("http", "https")
                        }
                    }
                }
                webView = wv
                wv
            }
        }
    }

    /**
     * Load a URL and suspend until onPageFinished fires.
     */
    private suspend fun loadUrlAndWait(wv: WebView, url: String) {
        suspendCancellableCoroutine<Unit> { continuation ->
            mainHandler.post {
                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (continuation.isActive) {
                            continuation.resume(Unit) {}
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        // Only handle main frame errors
                        if (request?.isForMainFrame == true && continuation.isActive) {
                            continuation.resumeWithException(
                                IOException("Page load error: ${error?.description}")
                            )
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val scheme = request?.url?.scheme?.lowercase()
                        return scheme !in listOf("http", "https")
                    }
                }
                wv.loadUrl(url)
            }
        }
    }

    /**
     * Reset WebView state between uses.
     */
    private suspend fun resetWebView(wv: WebView) {
        withContext(Dispatchers.Main) {
            wv.loadUrl("about:blank")
            wv.clearHistory()
            wv.clearCache(false)
            CookieManager.getInstance().removeAllCookies(null)
        }
    }

    /**
     * Schedule WebView destruction after idle timeout.
     */
    private fun scheduleIdleCleanup() {
        idleCleanupRunnable?.let { mainHandler.removeCallbacks(it) }
        idleCleanupRunnable = Runnable { destroyWebView() }.also {
            mainHandler.postDelayed(it, IDLE_TIMEOUT_MS)
        }
    }

    /**
     * Destroy the WebView and free memory.
     */
    fun destroyWebView() {
        mainHandler.post {
            webView?.let { wv ->
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.clearHistory()
                wv.clearCache(true)
                wv.removeAllViews()
                wv.destroy()
                Log.d(TAG, "WebView destroyed")
            }
            webView = null
        }
    }
}
