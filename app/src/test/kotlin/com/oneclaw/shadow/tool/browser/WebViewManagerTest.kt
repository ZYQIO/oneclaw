package com.oneclaw.shadow.tool.browser

import android.app.Application
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WebViewManagerTest {

    private lateinit var screenshotCapture: BrowserScreenshotCapture
    private lateinit var contentExtractor: BrowserContentExtractor
    private lateinit var webViewManager: WebViewManager

    @Before
    fun setup() {
        screenshotCapture = mockk(relaxed = true)
        contentExtractor = mockk(relaxed = true)
        webViewManager = WebViewManager(
            RuntimeEnvironment.getApplication(),
            screenshotCapture,
            contentExtractor
        )
    }

    @Test
    fun testDestroy_whenNoWebView_doesNotCrash() {
        // destroyWebView when no webview is created should be a no-op
        webViewManager.destroyWebView()
        // If we reach here, no crash occurred
        assertNotNull(webViewManager)
    }
}
