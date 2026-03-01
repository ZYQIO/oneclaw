package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.Provider
import com.oneclaw.shadow.core.model.ProviderType
import com.oneclaw.shadow.core.model.ToolResultStatus
import com.oneclaw.shadow.core.repository.ProviderRepository
import com.oneclaw.shadow.core.util.AppResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeleteProviderToolTest {

    private lateinit var providerRepository: ProviderRepository
    private lateinit var tool: DeleteProviderTool

    private val customProvider = Provider(
        id = "provider-1",
        name = "My OpenAI",
        type = ProviderType.OPENAI,
        apiBaseUrl = "https://api.openai.com/v1",
        isPreConfigured = false,
        isActive = true,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private val preConfiguredProvider = customProvider.copy(
        id = "provider-builtin",
        name = "Built-in Provider",
        isPreConfigured = true
    )

    @BeforeEach
    fun setup() {
        providerRepository = mockk()
        tool = DeleteProviderTool(providerRepository)
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
    fun `pre-configured provider cannot be deleted`() = runTest {
        coEvery { providerRepository.getProviderById("provider-builtin") } returns preConfiguredProvider

        val result = tool.execute(mapOf("provider_id" to "provider-builtin"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("permission_denied", result.errorType)
        assertTrue(result.errorMessage!!.contains("cannot be deleted") || result.errorMessage!!.contains("Pre-configured"))
    }

    @Test
    fun `custom provider is deleted successfully`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns customProvider
        coEvery { providerRepository.deleteProvider("provider-1") } returns AppResult.Success(Unit)

        val result = tool.execute(mapOf("provider_id" to "provider-1"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("My OpenAI"))
        assertTrue(result.result!!.contains("deleted"))
    }

    @Test
    fun `repository error during delete returns deletion_failed`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns customProvider
        coEvery { providerRepository.deleteProvider("provider-1") } returns AppResult.Error(
            message = "Database error"
        )

        val result = tool.execute(mapOf("provider_id" to "provider-1"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("deletion_failed", result.errorType)
        assertTrue(result.errorMessage!!.contains("Database error"))
    }

    @Test
    fun `definition has correct tool name`() {
        assertEquals("delete_provider", tool.definition.name)
    }

    @Test
    fun `definition requires provider_id`() {
        val required = tool.definition.parametersSchema.required
        assertTrue(required.contains("provider_id"))
    }
}
