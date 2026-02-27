package com.oneclaw.shadow.data.local.mapper

import com.oneclaw.shadow.core.model.AiModel
import com.oneclaw.shadow.core.model.ModelSource
import com.oneclaw.shadow.core.model.Provider
import com.oneclaw.shadow.core.model.ProviderType
import com.oneclaw.shadow.data.local.entity.ModelEntity
import com.oneclaw.shadow.data.local.entity.ProviderEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProviderMapperTest {

    @Test
    fun `provider entity toDomain maps all fields`() {
        val entity = ProviderEntity(
            id = "provider-openai",
            name = "OpenAI",
            type = "OPENAI",
            apiBaseUrl = "https://api.openai.com/v1",
            isPreConfigured = true,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val domain = entity.toDomain()

        assertEquals("provider-openai", domain.id)
        assertEquals("OpenAI", domain.name)
        assertEquals(ProviderType.OPENAI, domain.type)
        assertEquals("https://api.openai.com/v1", domain.apiBaseUrl)
        assertEquals(true, domain.isPreConfigured)
        assertEquals(true, domain.isActive)
        assertEquals(1000L, domain.createdAt)
        assertEquals(2000L, domain.updatedAt)
    }

    @Test
    fun `provider domain toEntity maps all fields`() {
        val domain = Provider(
            id = "provider-anthropic",
            name = "Anthropic",
            type = ProviderType.ANTHROPIC,
            apiBaseUrl = "https://api.anthropic.com",
            isPreConfigured = true,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val entity = domain.toEntity()

        assertEquals("provider-anthropic", entity.id)
        assertEquals("Anthropic", entity.name)
        assertEquals("ANTHROPIC", entity.type)
        assertEquals("https://api.anthropic.com", entity.apiBaseUrl)
    }

    @Test
    fun `provider roundtrip preserves all provider types`() {
        for (providerType in ProviderType.entries) {
            val original = Provider(
                id = "provider-${providerType.name.lowercase()}",
                name = providerType.name,
                type = providerType,
                apiBaseUrl = "https://example.com",
                isPreConfigured = false,
                isActive = true,
                createdAt = 100L,
                updatedAt = 200L
            )
            val roundtripped = original.toEntity().toDomain()
            assertEquals(original, roundtripped, "Roundtrip failed for $providerType")
        }
    }

    @Test
    fun `model entity toDomain maps all fields`() {
        val entity = ModelEntity(
            id = "gpt-4o",
            displayName = "GPT-4o",
            providerId = "provider-openai",
            isDefault = true,
            source = "DYNAMIC"
        )

        val domain = entity.toDomain()

        assertEquals("gpt-4o", domain.id)
        assertEquals("GPT-4o", domain.displayName)
        assertEquals("provider-openai", domain.providerId)
        assertEquals(true, domain.isDefault)
        assertEquals(ModelSource.DYNAMIC, domain.source)
    }

    @Test
    fun `model entity toDomain handles null displayName`() {
        val entity = ModelEntity(
            id = "custom-model",
            displayName = null,
            providerId = "provider-1",
            isDefault = false,
            source = "MANUAL"
        )

        val domain = entity.toDomain()
        assertEquals(null, domain.displayName)
        assertEquals(ModelSource.MANUAL, domain.source)
    }

    @Test
    fun `model domain toEntity maps all fields`() {
        val domain = AiModel(
            id = "claude-sonnet-4-20250514",
            displayName = "Claude Sonnet 4",
            providerId = "provider-anthropic",
            isDefault = false,
            source = ModelSource.PRESET
        )

        val entity = domain.toEntity()

        assertEquals("claude-sonnet-4-20250514", entity.id)
        assertEquals("Claude Sonnet 4", entity.displayName)
        assertEquals("provider-anthropic", entity.providerId)
        assertEquals(false, entity.isDefault)
        assertEquals("PRESET", entity.source)
    }

    @Test
    fun `model roundtrip preserves all model sources`() {
        for (source in ModelSource.entries) {
            val original = AiModel(
                id = "model-${source.name.lowercase()}",
                displayName = "Model ${source.name}",
                providerId = "provider-test",
                isDefault = false,
                source = source
            )
            val roundtripped = original.toEntity().toDomain()
            assertEquals(original, roundtripped, "Roundtrip failed for $source")
        }
    }
}
