package com.oneclaw.shadow.tool.builtin.remote

import com.oneclaw.remote.core.model.RemoteCapabilities
import com.oneclaw.remote.core.model.RemoteDevice
import com.oneclaw.remote.core.model.RemoteMode
import com.oneclaw.shadow.core.repository.RemoteControllerGateway
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.core.model.ToolResultStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RemoteToolsTest {

    private lateinit var gateway: RemoteControllerGateway

    private val sampleDevice = RemoteDevice(
        deviceId = "device-1",
        name = "Test Pixel",
        mode = RemoteMode.ROOT,
        capabilities = RemoteCapabilities(
            video = true,
            touch = true,
            keyboard = true,
            fileTransfer = true,
            unattended = true,
            agentControl = true
        ),
        lastSeen = 1234L,
        online = true,
        screenWidth = 1080,
        screenHeight = 2400
    )

    @BeforeEach
    fun setUp() {
        gateway = mockk()
        every { gateway.devices } returns MutableStateFlow(listOf(sampleDevice))
        every { gateway.lastSnapshot } returns MutableStateFlow(null)
        every { gateway.status } returns MutableStateFlow("Connected")
        every { gateway.activeSessions } returns MutableStateFlow(emptyMap())
        coEvery { gateway.connect() } returns AppResult.Success(Unit)
    }

    @Test
    fun `remote_list_devices prints discovered devices`() = runTest {
        coEvery { gateway.refreshDevices() } returns AppResult.Success(listOf(sampleDevice))
        val tool = RemoteListDevicesTool(gateway)

        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("device-1"))
        assertTrue(result.result!!.contains("Test Pixel"))
    }

    @Test
    fun `remote_open_session validates device_id`() = runTest {
        val tool = RemoteOpenSessionTool(gateway)

        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
    }
}
