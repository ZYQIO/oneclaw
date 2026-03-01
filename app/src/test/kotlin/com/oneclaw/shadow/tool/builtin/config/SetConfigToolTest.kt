package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.ToolResultStatus
import com.oneclaw.shadow.core.repository.SettingsRepository
import com.oneclaw.shadow.core.theme.ThemeManager
import com.oneclaw.shadow.core.theme.ThemeMode
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SetConfigToolTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var themeManager: ThemeManager
    private lateinit var tool: SetConfigTool

    @BeforeEach
    fun setup() {
        settingsRepository = mockk()
        themeManager = mockk()
        tool = SetConfigTool(settingsRepository, themeManager)
    }

    @Test
    fun `missing key returns validation error`() = runTest {
        val result = tool.execute(mapOf("value" to "dark"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("key"))
    }

    @Test
    fun `missing value returns validation error`() = runTest {
        val result = tool.execute(mapOf("key" to "theme_mode"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("value"))
    }

    @Test
    fun `empty key returns validation error`() = runTest {
        val result = tool.execute(mapOf("key" to "   ", "value" to "dark"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
    }

    @Test
    fun `empty value returns validation error`() = runTest {
        val result = tool.execute(mapOf("key" to "theme_mode", "value" to "   "))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
    }

    @Test
    fun `invalid theme_mode value returns validation error`() = runTest {
        val result = tool.execute(mapOf("key" to "theme_mode", "value" to "invalid"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("invalid") || result.errorMessage!!.contains("system"))
    }

    @Test
    fun `setting theme_mode to dark calls themeManager`() = runTest {
        coJustRun { themeManager.setThemeMode(ThemeMode.DARK) }

        val result = tool.execute(mapOf("key" to "theme_mode", "value" to "dark"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("dark"))
        coVerify { themeManager.setThemeMode(ThemeMode.DARK) }
    }

    @Test
    fun `setting theme_mode to light calls themeManager`() = runTest {
        coJustRun { themeManager.setThemeMode(ThemeMode.LIGHT) }

        val result = tool.execute(mapOf("key" to "theme_mode", "value" to "light"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        coVerify { themeManager.setThemeMode(ThemeMode.LIGHT) }
    }

    @Test
    fun `setting theme_mode to system calls themeManager`() = runTest {
        coJustRun { themeManager.setThemeMode(ThemeMode.SYSTEM) }

        val result = tool.execute(mapOf("key" to "theme_mode", "value" to "system"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        coVerify { themeManager.setThemeMode(ThemeMode.SYSTEM) }
    }

    @Test
    fun `setting custom key stores via settingsRepository`() = runTest {
        coJustRun { settingsRepository.setString("my_key", "my_value") }

        val result = tool.execute(mapOf("key" to "my_key", "value" to "my_value"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("my_key"))
        assertTrue(result.result!!.contains("my_value"))
        coVerify { settingsRepository.setString("my_key", "my_value") }
    }

    @Test
    fun `KNOWN_KEY_VALUES contains theme_mode with valid values`() {
        val allowedValues = SetConfigTool.KNOWN_KEY_VALUES["theme_mode"]
        assertTrue(allowedValues != null)
        assertTrue(allowedValues!!.contains("system"))
        assertTrue(allowedValues.contains("light"))
        assertTrue(allowedValues.contains("dark"))
    }

    @Test
    fun `definition has correct tool name`() {
        assertEquals("set_config", tool.definition.name)
    }

    @Test
    fun `definition requires key and value`() {
        val required = tool.definition.parametersSchema.required
        assertTrue(required.contains("key"))
        assertTrue(required.contains("value"))
    }
}
