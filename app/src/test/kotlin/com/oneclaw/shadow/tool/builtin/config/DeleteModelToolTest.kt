package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.AiModel
import com.oneclaw.shadow.core.model.ModelSource
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

class DeleteModelToolTest {

    private lateinit var providerRepository: ProviderRepository
    private lateinit var tool: DeleteModelTool

    private val provider = Provider(
        id = "provider-1",
        name = "My OpenAI",
        type = ProviderType.OPENAI,
        apiBaseUrl = "https://api.openai.com/v1",
        isPreConfigured = false,
        isActive = true,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private val manualModel = AiModel(
        id = "gpt-4-turbo",
        displayName = "GPT-4 Turbo",
        providerId = "provider-1",
        isDefault = false,
        source = ModelSource.MANUAL
    )

    private val dynamicModel = AiModel(
        id = "gpt-4o",
        displayName = "GPT-4o",
        providerId = "provider-1",
        isDefault = false,
        source = ModelSource.DYNAMIC
    )

    private val presetModel = AiModel(
        id = "gpt-3.5-turbo",
        displayName = "GPT-3.5 Turbo",
        providerId = "provider-1",
        isDefault = false,
        source = ModelSource.PRESET
    )

    @BeforeEach
    fun setup() {
        providerRepository = mockk()
        tool = DeleteModelTool(providerRepository)
    }

    @Test
    fun `missing provider_id returns validation error`() = runTest {
        val result = tool.execute(mapOf("model_id" to "gpt-4-turbo"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("provider_id"))
    }

    @Test
    fun `missing model_id returns validation error`() = runTest {
        val result = tool.execute(mapOf("provider_id" to "provider-1"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("model_id"))
    }

    @Test
    fun `provider not found returns not_found error`() = runTest {
        coEvery { providerRepository.getProviderById("nonexistent") } returns null

        val result = tool.execute(mapOf("provider_id" to "nonexistent", "model_id" to "gpt-4-turbo"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("not_found", result.errorType)
    }

    @Test
    fun `model not found returns not_found error`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns provider
        coEvery { providerRepository.getModelsForProvider("provider-1") } returns emptyList()

        val result = tool.execute(mapOf("provider_id" to "provider-1", "model_id" to "gpt-4-turbo"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("not_found", result.errorType)
        assertTrue(result.errorMessage!!.contains("gpt-4-turbo"))
    }

    @Test
    fun `dynamic model cannot be deleted`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns provider
        coEvery { providerRepository.getModelsForProvider("provider-1") } returns listOf(dynamicModel)

        val result = tool.execute(mapOf("provider_id" to "provider-1", "model_id" to "gpt-4o"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("permission_denied", result.errorType)
        assertTrue(result.errorMessage!!.contains("DYNAMIC") || result.errorMessage!!.contains("MANUAL"))
    }

    @Test
    fun `preset model cannot be deleted`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns provider
        coEvery { providerRepository.getModelsForProvider("provider-1") } returns listOf(presetModel)

        val result = tool.execute(mapOf("provider_id" to "provider-1", "model_id" to "gpt-3.5-turbo"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("permission_denied", result.errorType)
    }

    @Test
    fun `manual model is deleted successfully`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns provider
        coEvery { providerRepository.getModelsForProvider("provider-1") } returns listOf(manualModel)
        coEvery { providerRepository.deleteManualModel("provider-1", "gpt-4-turbo") } returns AppResult.Success(Unit)

        val result = tool.execute(mapOf("provider_id" to "provider-1", "model_id" to "gpt-4-turbo"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("GPT-4 Turbo") || result.result!!.contains("gpt-4-turbo"))
        assertTrue(result.result!!.contains("deleted"))
    }

    @Test
    fun `repository error returns deletion_failed error`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns provider
        coEvery { providerRepository.getModelsForProvider("provider-1") } returns listOf(manualModel)
        coEvery { providerRepository.deleteManualModel(any(), any()) } returns AppResult.Error(
            message = "Database error"
        )

        val result = tool.execute(mapOf("provider_id" to "provider-1", "model_id" to "gpt-4-turbo"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("deletion_failed", result.errorType)
    }

    @Test
    fun `definition has correct tool name`() {
        assertEquals("delete_model", tool.definition.name)
    }
}
