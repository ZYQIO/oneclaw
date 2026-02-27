package com.oneclaw.shadow.data.local.mapper

import com.oneclaw.shadow.core.model.Message
import com.oneclaw.shadow.core.model.MessageType
import com.oneclaw.shadow.core.model.ToolCallStatus
import com.oneclaw.shadow.data.local.entity.MessageEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MessageMapperTest {

    @Test
    fun `entity toDomain maps USER message correctly`() {
        val entity = MessageEntity(
            id = "msg-1",
            sessionId = "session-1",
            type = "USER",
            content = "Hello",
            thinkingContent = null,
            toolCallId = null,
            toolName = null,
            toolInput = null,
            toolOutput = null,
            toolStatus = null,
            toolDurationMs = null,
            tokenCountInput = null,
            tokenCountOutput = null,
            modelId = null,
            providerId = null,
            createdAt = 1000L
        )

        val domain = entity.toDomain()

        assertEquals("msg-1", domain.id)
        assertEquals("session-1", domain.sessionId)
        assertEquals(MessageType.USER, domain.type)
        assertEquals("Hello", domain.content)
        assertNull(domain.thinkingContent)
        assertNull(domain.toolCallId)
        assertEquals(1000L, domain.createdAt)
    }

    @Test
    fun `entity toDomain maps AI_RESPONSE with thinking content`() {
        val entity = MessageEntity(
            id = "msg-2",
            sessionId = "session-1",
            type = "AI_RESPONSE",
            content = "The answer is 4.",
            thinkingContent = "Let me calculate 2+2...",
            toolCallId = null,
            toolName = null,
            toolInput = null,
            toolOutput = null,
            toolStatus = null,
            toolDurationMs = null,
            tokenCountInput = 10,
            tokenCountOutput = 20,
            modelId = "claude-sonnet-4-20250514",
            providerId = "provider-anthropic",
            createdAt = 2000L
        )

        val domain = entity.toDomain()

        assertEquals(MessageType.AI_RESPONSE, domain.type)
        assertEquals("Let me calculate 2+2...", domain.thinkingContent)
        assertEquals(10, domain.tokenCountInput)
        assertEquals(20, domain.tokenCountOutput)
        assertEquals("claude-sonnet-4-20250514", domain.modelId)
        assertEquals("provider-anthropic", domain.providerId)
    }

    @Test
    fun `entity toDomain maps TOOL_CALL message`() {
        val entity = MessageEntity(
            id = "msg-3",
            sessionId = "session-1",
            type = "TOOL_CALL",
            content = "",
            thinkingContent = null,
            toolCallId = "call_123",
            toolName = "get_current_time",
            toolInput = """{"timezone":"UTC"}""",
            toolOutput = null,
            toolStatus = "PENDING",
            toolDurationMs = null,
            tokenCountInput = null,
            tokenCountOutput = null,
            modelId = null,
            providerId = null,
            createdAt = 3000L
        )

        val domain = entity.toDomain()

        assertEquals(MessageType.TOOL_CALL, domain.type)
        assertEquals("call_123", domain.toolCallId)
        assertEquals("get_current_time", domain.toolName)
        assertEquals("""{"timezone":"UTC"}""", domain.toolInput)
        assertEquals(ToolCallStatus.PENDING, domain.toolStatus)
    }

    @Test
    fun `entity toDomain maps TOOL_RESULT message`() {
        val entity = MessageEntity(
            id = "msg-4",
            sessionId = "session-1",
            type = "TOOL_RESULT",
            content = "",
            thinkingContent = null,
            toolCallId = "call_123",
            toolName = "get_current_time",
            toolInput = null,
            toolOutput = "2026-02-27T15:30:00Z",
            toolStatus = "SUCCESS",
            toolDurationMs = 50L,
            tokenCountInput = null,
            tokenCountOutput = null,
            modelId = null,
            providerId = null,
            createdAt = 4000L
        )

        val domain = entity.toDomain()

        assertEquals(MessageType.TOOL_RESULT, domain.type)
        assertEquals("2026-02-27T15:30:00Z", domain.toolOutput)
        assertEquals(ToolCallStatus.SUCCESS, domain.toolStatus)
        assertEquals(50L, domain.toolDurationMs)
    }

    @Test
    fun `domain toEntity maps all fields correctly`() {
        val domain = Message(
            id = "msg-5",
            sessionId = "session-2",
            type = MessageType.ERROR,
            content = "API call failed.",
            thinkingContent = null,
            toolCallId = null,
            toolName = null,
            toolInput = null,
            toolOutput = null,
            toolStatus = null,
            toolDurationMs = null,
            tokenCountInput = null,
            tokenCountOutput = null,
            modelId = null,
            providerId = null,
            createdAt = 5000L
        )

        val entity = domain.toEntity()

        assertEquals("msg-5", entity.id)
        assertEquals("session-2", entity.sessionId)
        assertEquals("ERROR", entity.type)
        assertEquals("API call failed.", entity.content)
        assertNull(entity.toolStatus)
    }

    @Test
    fun `roundtrip preserves all message types`() {
        for (messageType in MessageType.entries) {
            val original = Message(
                id = "msg-${messageType.name}",
                sessionId = "session-rt",
                type = messageType,
                content = "content for ${messageType.name}",
                thinkingContent = null,
                toolCallId = null,
                toolName = null,
                toolInput = null,
                toolOutput = null,
                toolStatus = null,
                toolDurationMs = null,
                tokenCountInput = null,
                tokenCountOutput = null,
                modelId = null,
                providerId = null,
                createdAt = 1000L
            )
            val roundtripped = original.toEntity().toDomain()
            assertEquals(original, roundtripped, "Roundtrip failed for $messageType")
        }
    }

    @Test
    fun `roundtrip preserves all tool call statuses`() {
        for (status in ToolCallStatus.entries) {
            val original = Message(
                id = "msg-status-${status.name}",
                sessionId = "session-rt",
                type = MessageType.TOOL_CALL,
                content = "",
                thinkingContent = null,
                toolCallId = "call_1",
                toolName = "test_tool",
                toolInput = "{}",
                toolOutput = null,
                toolStatus = status,
                toolDurationMs = null,
                tokenCountInput = null,
                tokenCountOutput = null,
                modelId = null,
                providerId = null,
                createdAt = 1000L
            )
            val roundtripped = original.toEntity().toDomain()
            assertEquals(original, roundtripped, "Roundtrip failed for status $status")
        }
    }
}
