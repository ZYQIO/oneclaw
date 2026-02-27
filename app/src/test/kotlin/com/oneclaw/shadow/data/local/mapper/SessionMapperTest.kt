package com.oneclaw.shadow.data.local.mapper

import com.oneclaw.shadow.core.model.Session
import com.oneclaw.shadow.data.local.entity.SessionEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SessionMapperTest {

    @Test
    fun `entity toDomain maps all fields`() {
        val entity = SessionEntity(
            id = "session-1",
            title = "Test Session",
            currentAgentId = "agent-general-assistant",
            messageCount = 5,
            lastMessagePreview = "Hello!",
            isActive = true,
            deletedAt = null,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val domain = entity.toDomain()

        assertEquals("session-1", domain.id)
        assertEquals("Test Session", domain.title)
        assertEquals("agent-general-assistant", domain.currentAgentId)
        assertEquals(5, domain.messageCount)
        assertEquals("Hello!", domain.lastMessagePreview)
        assertEquals(true, domain.isActive)
        assertNull(domain.deletedAt)
        assertEquals(1000L, domain.createdAt)
        assertEquals(2000L, domain.updatedAt)
    }

    @Test
    fun `entity toDomain handles soft-deleted session`() {
        val entity = SessionEntity(
            id = "session-2",
            title = "Deleted Session",
            currentAgentId = "agent-1",
            messageCount = 0,
            lastMessagePreview = null,
            isActive = false,
            deletedAt = 3000L,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val domain = entity.toDomain()

        assertEquals(3000L, domain.deletedAt)
        assertNull(domain.lastMessagePreview)
    }

    @Test
    fun `domain toEntity maps all fields`() {
        val domain = Session(
            id = "session-3",
            title = "My Chat",
            currentAgentId = "agent-1",
            messageCount = 10,
            lastMessagePreview = "Latest message",
            isActive = true,
            deletedAt = null,
            createdAt = 1000L,
            updatedAt = 5000L
        )

        val entity = domain.toEntity()

        assertEquals("session-3", entity.id)
        assertEquals("My Chat", entity.title)
        assertEquals("agent-1", entity.currentAgentId)
        assertEquals(10, entity.messageCount)
        assertEquals("Latest message", entity.lastMessagePreview)
        assertEquals(true, entity.isActive)
        assertNull(entity.deletedAt)
    }

    @Test
    fun `roundtrip preserves all data`() {
        val original = Session(
            id = "session-rt",
            title = "Roundtrip Session",
            currentAgentId = "agent-general-assistant",
            messageCount = 42,
            lastMessagePreview = "A preview of the last message",
            isActive = false,
            deletedAt = 9000L,
            createdAt = 1000L,
            updatedAt = 8000L
        )

        val roundtripped = original.toEntity().toDomain()
        assertEquals(original, roundtripped)
    }

    @Test
    fun `roundtrip with null optional fields`() {
        val original = Session(
            id = "session-null",
            title = "New Session",
            currentAgentId = "agent-1",
            messageCount = 0,
            lastMessagePreview = null,
            isActive = false,
            deletedAt = null,
            createdAt = 500L,
            updatedAt = 500L
        )

        val roundtripped = original.toEntity().toDomain()
        assertEquals(original, roundtripped)
    }
}
