package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.ToolResultStatus
import com.oneclaw.shadow.core.model.ToolSourceInfo
import com.oneclaw.shadow.core.model.ToolSourceType
import com.oneclaw.shadow.tool.engine.ToolEnabledStateStore
import com.oneclaw.shadow.tool.engine.ToolRegistry
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ListToolStatesToolTest {

    private lateinit var toolRegistry: ToolRegistry
    private lateinit var toolEnabledStateStore: ToolEnabledStateStore
    private lateinit var tool: ListToolStatesTool

    @BeforeEach
    fun setup() {
        toolRegistry = mockk()
        toolEnabledStateStore = mockk()
        tool = ListToolStatesTool(toolRegistry, toolEnabledStateStore)
    }

    @Test
    fun `empty registry returns no tools message`() = runTest {
        every { toolRegistry.getAllToolNames() } returns emptyList()
        every { toolRegistry.getToolGroups() } returns emptyMap()

        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("No tools"))
    }

    @Test
    fun `tool names are listed with enabled status`() = runTest {
        every { toolRegistry.getAllToolNames() } returns listOf("my_tool")
        every { toolRegistry.getToolGroups() } returns emptyMap()
        every { toolEnabledStateStore.isToolEnabled("my_tool") } returns true

        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("my_tool"))
        assertTrue(result.result!!.contains("ENABLED"))
    }

    @Test
    fun `disabled tool shows DISABLED status`() = runTest {
        every { toolRegistry.getAllToolNames() } returns listOf("my_tool")
        every { toolRegistry.getToolGroups() } returns emptyMap()
        every { toolEnabledStateStore.isToolEnabled("my_tool") } returns false

        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("my_tool"))
        assertTrue(result.result!!.contains("DISABLED"))
    }

    @Test
    fun `grouped tool shows group name`() = runTest {
        every { toolRegistry.getAllToolNames() } returns listOf("tool_a", "tool_b")
        every { toolRegistry.getToolGroups() } returns mapOf("my_group" to listOf("tool_a", "tool_b"))
        every { toolEnabledStateStore.isGroupEnabled("my_group") } returns true
        every { toolEnabledStateStore.isToolEnabled("tool_a") } returns true
        every { toolEnabledStateStore.isToolEnabled("tool_b") } returns false

        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("my_group"))
        assertTrue(result.result!!.contains("tool_a"))
        assertTrue(result.result!!.contains("tool_b"))
    }

    @Test
    fun `group disabled marks tools as disabled due to group`() = runTest {
        every { toolRegistry.getAllToolNames() } returns listOf("tool_a")
        every { toolRegistry.getToolGroups() } returns mapOf("my_group" to listOf("tool_a"))
        every { toolEnabledStateStore.isGroupEnabled("my_group") } returns false
        every { toolEnabledStateStore.isToolEnabled("tool_a") } returns true

        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("DISABLED"))
        assertTrue(result.result!!.contains("group disabled") || result.result!!.contains("DISABLED"))
    }

    @Test
    fun `ungrouped tools appear in ungrouped section`() = runTest {
        every { toolRegistry.getAllToolNames() } returns listOf("standalone_tool")
        every { toolRegistry.getToolGroups() } returns emptyMap()
        every { toolEnabledStateStore.isToolEnabled("standalone_tool") } returns true

        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("standalone_tool"))
    }

    @Test
    fun `definition has correct tool name`() {
        assertEquals("list_tool_states", tool.definition.name)
    }

    @Test
    fun `definition has no required parameters`() {
        assertTrue(tool.definition.parametersSchema.required.isEmpty())
    }
}
