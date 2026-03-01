package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.Provider
import com.oneclaw.shadow.core.model.ProviderType
import com.oneclaw.shadow.core.model.ToolResultStatus
import com.oneclaw.shadow.core.repository.ProviderRepository
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UpdateProviderToolTest {

    private lateinit var providerRepository: ProviderRepository
    private lateinit var tool: UpdateProviderTool

    private val existingProvider = Provider(
        id = "provider-1",
        name = "My OpenAI",
        type = ProviderType.OPENAI,
        apiBaseUrl = "https://api.openai.com/v1",
        isPreConfigured = false,
        isActive = true,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @BeforeEach
    fun setup() {
        providerRepository = mockk()
        tool = UpdateProviderTool(providerRepository)
    }

    @Test
    fun `missing provider_id returns validation error`() = runTest {
        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("provider_id"))
    }

    @Test
    fun `provider not found returns not_found error`() = runTest {
        coEvery { providerRepository.getProviderById("nonexistent") } returns null

        val result = tool.execute(mapOf("provider_id" to "nonexistent"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("not_found", result.errorType)
    }

    @Test
    fun `no changes applied returns unchanged message`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns existingProvider

        val result = tool.execute(mapOf("provider_id" to "provider-1"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("unchanged") || result.result!!.contains("No changes"))
    }

    @Test
    fun `updating name returns success with change listed`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns existingProvider
        coJustRun { providerRepository.updateProvider(any()) }

        val result = tool.execute(mapOf(
            "provider_id" to "provider-1",
            "name" to "New Name"
        ))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("name"))
        coVerify { providerRepository.updateProvider(any()) }
    }

    @Test
    fun `empty new name returns validation error`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns existingProvider

        val result = tool.execute(mapOf(
            "provider_id" to "provider-1",
            "name" to "   "
        ))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
    }

    @Test
    fun `updating api_base_url returns success`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns existingProvider
        coJustRun { providerRepository.updateProvider(any()) }

        val result = tool.execute(mapOf(
            "provider_id" to "provider-1",
            "api_base_url" to "https://new-api.example.com/v1"
        ))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("api_base_url"))
    }

    @Test
    fun `empty api_base_url returns validation error`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns existingProvider

        val result = tool.execute(mapOf(
            "provider_id" to "provider-1",
            "api_base_url" to ""
        ))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
    }

    @Test
    fun `updating is_active returns success`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns existingProvider
        coJustRun { providerRepository.updateProvider(any()) }

        val result = tool.execute(mapOf(
            "provider_id" to "provider-1",
            "is_active" to false
        ))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("is_active"))
    }

    @Test
    fun `definition has correct tool name`() {
        assertEquals("update_provider", tool.definition.name)
    }

    @Test
    fun `definition requires only provider_id`() {
        val required = tool.definition.parametersSchema.required
        assertEquals(listOf("provider_id"), required)
    }
}
