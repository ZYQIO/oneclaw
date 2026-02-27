package com.oneclaw.shadow.tool.engine

import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ToolRegistryTest {

    private lateinit var registry: ToolRegistry

    private fun createToolDefinition(name: String): ToolDefinition = ToolDefinition(
        name = name,
        description = "Test tool: $name",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "input" to ToolParameter(type = "string", description = "test input")
            ),
            required = listOf("input")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 30
    )

    @BeforeEach
    fun setup() {
        registry = ToolRegistry()
    }

    @Test
    fun `registers and retrieves tool by name`() {
        val tool = createToolDefinition("test_tool")
        registry.register("test_tool", tool)

        val retrieved = registry.getToolDefinition("test_tool")
        assertEquals(tool, retrieved)
    }

    @Test
    fun `getToolDefinition returns null for unknown name`() {
        assertNull(registry.getToolDefinition("nonexistent"))
    }

    @Test
    fun `getAllToolDefinitions returns all registered tools`() {
        val tool1 = createToolDefinition("tool_a")
        val tool2 = createToolDefinition("tool_b")
        val tool3 = createToolDefinition("tool_c")

        registry.register("tool_a", tool1)
        registry.register("tool_b", tool2)
        registry.register("tool_c", tool3)

        val all = registry.getAllToolDefinitions()
        assertEquals(3, all.size)
        assertTrue(all.contains(tool1))
        assertTrue(all.contains(tool2))
        assertTrue(all.contains(tool3))
    }

    @Test
    fun `getToolsByIds returns matching tools`() {
        val tool1 = createToolDefinition("tool_a")
        val tool2 = createToolDefinition("tool_b")
        val tool3 = createToolDefinition("tool_c")

        registry.register("tool_a", tool1)
        registry.register("tool_b", tool2)
        registry.register("tool_c", tool3)

        val selected = registry.getToolsByIds(listOf("tool_a", "tool_c"))
        assertEquals(2, selected.size)
        assertTrue(selected.contains(tool1))
        assertTrue(selected.contains(tool3))
    }

    @Test
    fun `getToolsByIds skips unknown IDs`() {
        val tool = createToolDefinition("tool_a")
        registry.register("tool_a", tool)

        val selected = registry.getToolsByIds(listOf("tool_a", "unknown"))
        assertEquals(1, selected.size)
        assertEquals(tool, selected[0])
    }

    @Test
    fun `hasTools returns false when empty`() {
        assertFalse(registry.hasTools())
    }

    @Test
    fun `hasTools returns true when tools registered`() {
        registry.register("tool_a", createToolDefinition("tool_a"))
        assertTrue(registry.hasTools())
    }

    @Test
    fun `registering same name overwrites previous`() {
        val tool1 = createToolDefinition("tool_a")
        val tool2 = ToolDefinition(
            name = "tool_a",
            description = "Updated tool",
            parametersSchema = ToolParametersSchema(properties = emptyMap()),
            requiredPermissions = emptyList(),
            timeoutSeconds = 60
        )

        registry.register("tool_a", tool1)
        registry.register("tool_a", tool2)

        val retrieved = registry.getToolDefinition("tool_a")
        assertEquals("Updated tool", retrieved?.description)
        assertEquals(1, registry.getAllToolDefinitions().size)
    }
}
