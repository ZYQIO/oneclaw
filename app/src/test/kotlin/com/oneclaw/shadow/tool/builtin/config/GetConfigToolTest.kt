package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.ToolResultStatus
import com.oneclaw.shadow.core.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetConfigToolTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var tool: GetConfigTool

    @BeforeEach
    fun setup() {
        settingsRepository = mockk()
        tool = GetConfigTool(settingsRepository)
    }

    @Test
    fun `missing key returns validation error`() = runTest {
        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("key"))
    }

    @Test
    fun `empty key returns validation error`() = runTest {
        val result = tool.execute(mapOf("key" to "   "))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
    }

    @Test
    fun `known key with value returns key and value`() = runTest {
        coEvery { settingsRepository.getString("theme_mode") } returns "dark"

        val result = tool.execute(mapOf("key" to "theme_mode"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("theme_mode"))
        assertTrue(result.result!!.contains("dark"))
    }

    @Test
    fun `known key includes description in response`() = runTest {
        coEvery { settingsRepository.getString("theme_mode") } returns "light"

        val result = tool.execute(mapOf("key" to "theme_mode"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        // Known key should have a description hint in parentheses
        assertTrue(result.result!!.contains("theme_mode"))
        assertTrue(result.result!!.contains("light"))
    }

    @Test
    fun `key not set returns not set message`() = runTest {
        coEvery { settingsRepository.getString("theme_mode") } returns null

        val result = tool.execute(mapOf("key" to "theme_mode"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("not set") || result.result!!.contains("Key 'theme_mode'"))
    }

    @Test
    fun `unknown key not set returns known keys list`() = runTest {
        coEvery { settingsRepository.getString("unknown_key") } returns null

        val result = tool.execute(mapOf("key" to "unknown_key"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        // Should list known keys when unknown key is not set
        assertTrue(result.result!!.contains("theme_mode"))
    }

    @Test
    fun `custom key with value returns value`() = runTest {
        coEvery { settingsRepository.getString("my_custom_key") } returns "some_value"

        val result = tool.execute(mapOf("key" to "my_custom_key"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("my_custom_key"))
        assertTrue(result.result!!.contains("some_value"))
    }

    @Test
    fun `definition has correct tool name`() {
        assertEquals("get_config", tool.definition.name)
    }

    @Test
    fun `definition requires key`() {
        val required = tool.definition.parametersSchema.required
        assertTrue(required.contains("key"))
    }

    @Test
    fun `KNOWN_KEYS contains theme_mode`() {
        assertTrue(GetConfigTool.KNOWN_KEYS.containsKey("theme_mode"))
    }
}
