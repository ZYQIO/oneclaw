package com.oneclaw.shadow.screenshot

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.oneclaw.remote.core.model.RemoteCapabilities
import com.oneclaw.remote.core.model.RemoteDevice
import com.oneclaw.remote.core.model.RemoteMode
import com.oneclaw.remote.core.model.RemoteSession
import com.oneclaw.shadow.feature.remote.RemoteControlScreenContent
import com.oneclaw.shadow.feature.remote.RemoteControlUiState
import com.oneclaw.shadow.ui.theme.OneClawTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-440dpi", application = Application::class)
class RemoteControlScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun remoteControlScreen() {
        val device = RemoteDevice(
            deviceId = "device-1",
            name = "Desk Phone",
            mode = RemoteMode.ROOT,
            capabilities = RemoteCapabilities(
                video = true,
                touch = true,
                keyboard = true,
                fileTransfer = true,
                unattended = true,
                agentControl = true
            ),
            lastSeen = 1_700_000_000_000,
            online = true,
            screenWidth = 1080,
            screenHeight = 2400
        )
        val session = RemoteSession(
            sessionId = "session-1",
            deviceId = "device-1",
            controllerId = "controller-1",
            mode = RemoteMode.ROOT,
            startedAt = 1_700_000_000_000,
            leaseExpiresAt = 1_700_000_180_000
        )
        composeRule.setContent {
            OneClawTheme(darkTheme = false, dynamicColor = false) {
                RemoteControlScreenContent(
                    uiState = RemoteControlUiState(
                        brokerUrl = "ws://10.0.2.2:8080/ws",
                        pairCode = "ABCD1234",
                        inputText = "hello remote",
                        devices = listOf(device),
                        activeSessions = mapOf(device.deviceId to session),
                        status = "Connected",
                        selectedDeviceId = device.deviceId
                    ),
                    onNavigateBack = {},
                    onBrokerUrlChange = {},
                    onPairCodeChange = {},
                    onInputTextChange = {},
                    onConnect = {},
                    onDisconnect = {},
                    onRefreshDevices = {},
                    onSelectDevice = {},
                    onPairSelectedDevice = {},
                    onOpenSession = {},
                    onCloseSession = {},
                    onSnapshot = {},
                    onHome = {},
                    onBack = {},
                    onTapCenter = {},
                    onSendText = {}
                )
            }
        }
        composeRule.onRoot()
            .captureRoboImage("../docs/testing/reports/screenshots/RemoteControlScreen.png")
    }
}
