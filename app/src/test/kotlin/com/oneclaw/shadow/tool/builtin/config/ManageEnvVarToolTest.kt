package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.ToolResultStatus
import com.oneclaw.shadow.tool.js.EnvironmentVariableStore
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ManageEnvVarToolTest {

    private lateinit var envVarStore: EnvironmentVariableStore
    private lateinit var tool: ManageEnvVarTool

    @BeforeEach
    fun setup() {
        envVarStore = mockk()
        tool = ManageEnvVarTool(envVarStore)
    }

    @Test
    fun `missing action returns validation error`() = runTest {
        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("action"))
    }

    @Test
    fun `invalid action returns validation error`() = runTest {
        val result = tool.execute(mapOf("action" to "invalid"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("list") || result.errorMessage!!.contains("set"))
    }

    @Test
    fun `list with empty store returns no variables message`() = runTest {
        every { envVarStore.getKeys() } returns emptyList()

        val result = tool.execute(mapOf("action" to "list"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("No environment variables"))
    }

    @Test
    fun `list with variables shows keys but not values`() = runTest {
        every { envVarStore.getKeys() } returns listOf("API_KEY", "BASE_URL")

        val result = tool.execute(mapOf("action" to "list"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("API_KEY"))
        assertTrue(result.result!!.contains("BASE_URL"))
        // Values should be masked
        assertTrue(result.result!!.contains("****"))
    }

    @Test
    fun `list shows count`() = runTest {
        every { envVarStore.getKeys() } returns listOf("KEY1", "KEY2", "KEY3")

        val result = tool.execute(mapOf("action" to "list"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("3"))
    }

    @Test
    fun `set missing key returns validation error`() = runTest {
        val result = tool.execute(mapOf("action" to "set", "value" to "some_value"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("key"))
    }

    @Test
    fun `set missing value returns validation error`() = runTest {
        val result = tool.execute(mapOf("action" to "set", "key" to "MY_KEY"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("value"))
    }

    @Test
    fun `set variable succeeds`() = runTest {
        justRun { envVarStore.set("MY_KEY", "my_value") }

        val result = tool.execute(mapOf("action" to "set", "key" to "MY_KEY", "value" to "my_value"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("MY_KEY"))
        verify { envVarStore.set("MY_KEY", "my_value") }
    }

    @Test
    fun `set allows empty string value`() = runTest {
        justRun { envVarStore.set("MY_KEY", "") }

        val result = tool.execute(mapOf("action" to "set", "key" to "MY_KEY", "value" to ""))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
    }

    @Test
    fun `delete missing key returns validation error`() = runTest {
        val result = tool.execute(mapOf("action" to "delete"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("key"))
    }

    @Test
    fun `delete variable succeeds`() = runTest {
        justRun { envVarStore.delete("MY_KEY") }

        val result = tool.execute(mapOf("action" to "delete", "key" to "MY_KEY"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("MY_KEY"))
        assertTrue(result.result!!.contains("deleted"))
        verify { envVarStore.delete("MY_KEY") }
    }

    @Test
    fun `action is case-insensitive`() = runTest {
        every { envVarStore.getKeys() } returns emptyList()

        val result = tool.execute(mapOf("action" to "LIST"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
    }

    @Test
    fun `definition has correct tool name`() {
        assertEquals("manage_env_var", tool.definition.name)
    }

    @Test
    fun `definition requires action`() {
        val required = tool.definition.parametersSchema.required
        assertTrue(required.contains("action"))
    }
}
