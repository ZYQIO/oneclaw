package com.oneclaw.shadow.data.local.mapper

import com.oneclaw.shadow.core.model.Agent
import com.oneclaw.shadow.data.local.entity.AgentEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AgentMapperTest {

    @Test
    fun `entity toDomain maps all fields correctly`() {
        val entity = AgentEntity(
            id = "agent-1",
            name = "Test Agent",
            description = "A test agent",
            systemPrompt = "You are helpful.",
            toolIds = "[]",
            preferredProviderId = "provider-1",
            preferredModelId = "model-1",
            isBuiltIn = false,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val domain = entity.toDomain()

        assertEquals("agent-1", domain.id)
        assertEquals("Test Agent", domain.name)
        assertEquals("A test agent", domain.description)
        assertEquals("You are helpful.", domain.systemPrompt)
        assertEquals("provider-1", domain.preferredProviderId)
        assertEquals("model-1", domain.preferredModelId)
        assertEquals(false, domain.isBuiltIn)
        assertEquals(1000L, domain.createdAt)
        assertEquals(2000L, domain.updatedAt)
    }

    @Test
    fun `entity toDomain handles null optional fields`() {
        val entity = AgentEntity(
            id = "agent-2",
            name = "Minimal",
            description = null,
            systemPrompt = "prompt",
            toolIds = "[]",
            preferredProviderId = null,
            preferredModelId = null,
            isBuiltIn = true,
            createdAt = 500L,
            updatedAt = 500L
        )

        val domain = entity.toDomain()

        assertEquals(null, domain.description)
        assertEquals(null, domain.preferredProviderId)
        assertEquals(null, domain.preferredModelId)
        assertEquals(true, domain.isBuiltIn)
    }

    @Test
    fun `domain toEntity always writes empty tool_ids`() {
        val domain = Agent(
            id = "agent-1",
            name = "Test Agent",
            description = "desc",
            systemPrompt = "prompt",
            preferredProviderId = "p-1",
            preferredModelId = "m-1",
            isBuiltIn = false,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val entity = domain.toEntity()

        assertEquals("agent-1", entity.id)
        assertEquals("Test Agent", entity.name)
        assertEquals("desc", entity.description)
        assertEquals("prompt", entity.systemPrompt)
        assertEquals("[]", entity.toolIds)
        assertEquals("p-1", entity.preferredProviderId)
        assertEquals("m-1", entity.preferredModelId)
        assertEquals(false, entity.isBuiltIn)
        assertEquals(1000L, entity.createdAt)
        assertEquals(2000L, entity.updatedAt)
    }

    @Test
    fun `roundtrip domain to entity and back preserves data`() {
        val original = Agent(
            id = "agent-roundtrip",
            name = "Roundtrip Agent",
            description = "testing roundtrip",
            systemPrompt = "Be helpful.",
            preferredProviderId = null,
            preferredModelId = null,
            isBuiltIn = true,
            createdAt = 5000L,
            updatedAt = 6000L
        )

        val roundtripped = original.toEntity().toDomain()

        assertEquals(original, roundtripped)
    }
}
