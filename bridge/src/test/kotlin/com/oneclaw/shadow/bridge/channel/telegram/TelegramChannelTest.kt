package com.oneclaw.shadow.bridge.channel.telegram

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TelegramChannelTest {

    @Test
    fun `photoMessage_withCaption_extractsCaption`() {
        val message = buildJsonObject {
            putJsonArray("photo") {
                add(buildJsonObject { put("file_id", "abc123"); put("file_size", 1024) })
            }
            put("caption", "What is this?")
        }
        assertEquals("What is this?", TelegramChannel.extractText(message))
    }

    @Test
    fun `photoMessage_withoutCaption_textIsEmpty`() {
        val message = buildJsonObject {
            putJsonArray("photo") {
                add(buildJsonObject { put("file_id", "abc123"); put("file_size", 1024) })
            }
        }
        assertEquals("", TelegramChannel.extractText(message))
    }

    @Test
    fun `textMessage_noChange_readsTextField`() {
        val message = buildJsonObject {
            put("text", "Hello there")
        }
        assertEquals("Hello there", TelegramChannel.extractText(message))
    }

    @Test
    fun `textMessage_blankText_fallsBackToCaption`() {
        val message = buildJsonObject {
            put("text", "   ")
            put("caption", "caption fallback")
        }
        assertEquals("caption fallback", TelegramChannel.extractText(message))
    }

    @Test
    fun `emptyMessage_returnsEmptyString`() {
        val message = buildJsonObject {}
        assertEquals("", TelegramChannel.extractText(message))
    }
}
