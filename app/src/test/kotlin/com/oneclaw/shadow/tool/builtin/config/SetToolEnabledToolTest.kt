package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.ToolResultStatus
import com.oneclaw.shadow.tool.engine.ToolEnabledStateStore
import com.oneclaw.shadow.tool.engine.ToolRegistry
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SetToolEnabledToolTest {

    private lateinit var toolRegistry: ToolRegistry
    private lateinit var toolEnabledStateStore: ToolEnabledStateStore
    private lateinit var tool: SetToolEnabledTool

    @BeforeEach
    fun setup() {
        toolRegistry = mockk()
        toolEnabledStateStore = mockk()
        tool = SetToolEnabledTool(toolRegistry, toolEnabledStateStore)
    }

    @Test
    fun `missing name returns validation error`() = runTest {
        val result = tool.execute(mapOf("enabled" to true))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("name"))
    }

    @Test
    fun `missing enabled returns validation error`() = runTest {
        val result = tool.execute(mapOf("name" to "my_tool"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("enabled"))
    }

    @Test
    fun `tool not found returns not_found error`() = runTest {
        every { toolRegistry.hasTool("nonexistent") } returns false

        val result = tool.execute(mapOf("name" to "nonexistent", "enabled" to true))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("not_found", result.errorType)
        assertTrue(result.errorMessage!!.contains("nonexistent"))
    }

    @Test
    fun `enabling a tool sets it enabled`() = runTest {
        every { toolRegistry.hasTool("my_tool") } returns true
        justRun { toolEnabledStateStore.setToolEnabled("my_tool", true) }

        val result = tool.execute(mapOf("name" to "my_tool", "enabled" to true))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("my_tool"))
        assertTrue(result.result!!.contains("enabled"))
        verify { toolEnabledStateStore.setToolEnabled("my_tool", true) }
    }

    @Test
    fun `disabling a tool sets it disabled`() = runTest {
        every { toolRegistry.hasTool("my_tool") } returns true
        justRun { toolEnabledStateStore.setToolEnabled("my_tool", false) }

        val result = tool.execute(mapOf("name" to "my_tool", "enabled" to false))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("disabled"))
        verify { toolEnabledStateStore.setToolEnabled("my_tool", false) }
    }

    @Test
    fun `enabling a group sets the group enabled`() = runTest {
        justRun { toolEnabledStateStore.setGroupEnabled("my_group", true) }

        val result = tool.execute(mapOf("name" to "my_group", "enabled" to true, "type" to "group"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("my_group"))
        assertTrue(result.result!!.contains("enabled"))
        verify { toolEnabledStateStore.setGroupEnabled("my_group", true) }
    }

    @Test
    fun `disabling a group sets the group disabled`() = runTest {
        justRun { toolEnabledStateStore.setGroupEnabled("my_group", false) }

        val result = tool.execute(mapOf("name" to "my_group", "enabled" to false, "type" to "group"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("disabled"))
        verify { toolEnabledStateStore.setGroupEnabled("my_group", false) }
    }

    @Test
    fun `invalid type returns validation error`() = runTest {
        val result = tool.execute(mapOf("name" to "something", "enabled" to true, "type" to "invalid"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
    }

    @Test
    fun `default type is tool when type not provided`() = runTest {
        every { toolRegistry.hasTool("my_tool") } returns true
        justRun { toolEnabledStateStore.setToolEnabled("my_tool", true) }

        val result = tool.execute(mapOf("name" to "my_tool", "enabled" to true))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        verify { toolEnabledStateStore.setToolEnabled("my_tool", true) }
    }

    @Test
    fun `definition has correct tool name`() {
        assertEquals("set_tool_enabled", tool.definition.name)
    }

    @Test
    fun `definition requires name and enabled`() {
        val required = tool.definition.parametersSchema.required
        assertTrue(required.contains("name"))
        assertTrue(required.contains("enabled"))
    }
}
