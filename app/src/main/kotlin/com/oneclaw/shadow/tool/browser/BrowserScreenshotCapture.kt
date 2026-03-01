package com.oneclaw.shadow.tool.browser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Located in: tool/browser/BrowserScreenshotCapture.kt
 *
 * Captures WebView content as Bitmap images.
 */
class BrowserScreenshotCapture {

    companion object {
        private const val TAG = "BrowserScreenshotCapture"
    }

    /**
     * Capture the visible viewport of the WebView.
     */
    suspend fun captureViewport(webView: WebView): Bitmap {
        return withContext(Dispatchers.Main) {
            val width = webView.width
            val height = webView.height

            if (width <= 0 || height <= 0) {
                throw IllegalStateException("WebView has zero dimensions: ${width}x${height}")
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            webView.draw(canvas)
            bitmap
        }
    }

    /**
     * Capture the full scrollable content of the WebView.
     * Height is capped at maxHeight to prevent OOM.
     */
    suspend fun captureFullPage(webView: WebView, maxHeight: Int): Bitmap {
        return withContext(Dispatchers.Main) {
            val width = webView.width

            // Get the full content height
            val contentHeight = (webView.contentHeight * webView.scale).toInt()
                .coerceAtMost(maxHeight)
                .coerceAtLeast(webView.height)

            if (width <= 0 || contentHeight <= 0) {
                throw IllegalStateException(
                    "Invalid dimensions for full-page capture: ${width}x${contentHeight}"
                )
            }

            val bitmap = Bitmap.createBitmap(width, contentHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Save the current scroll position
            val savedScrollX = webView.scrollX
            val savedScrollY = webView.scrollY

            // Scroll to top and draw
            webView.scrollTo(0, 0)
            webView.draw(canvas)

            // Restore scroll position
            webView.scrollTo(savedScrollX, savedScrollY)

            bitmap
        }
    }
}
