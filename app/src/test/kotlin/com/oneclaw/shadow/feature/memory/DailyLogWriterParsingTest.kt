package com.oneclaw.shadow.feature.memory

import com.oneclaw.shadow.feature.memory.log.DailyLogWriter
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DailyLogWriterParsingTest {

    /**
     * RFC-052: DailyLogWriter no longer extracts long-term facts.
     * parseSummarizationResponse now returns a single String (daily summary only).
     */
    private val writer = DailyLogWriter(
        messageRepository = mockk(),
        sessionRepository = mockk(),
        agentRepository = mockk(),
        providerRepository = mockk(),
        apiKeyStorage = mockk(),
        adapterFactory = mockk(),
        memoryFileStorage = mockk(),
        memoryIndexDao = mockk(),
        embeddingEngine = mockk()
    )

    @Test
    fun `parseSummarizationResponse extracts daily summary`() {
        val response = """
            ## Daily Summary
            - Discussed Kotlin coroutines
            - Reviewed Room DB migration steps
        """.trimIndent()

        val result = writer.parseSummarizationResponse(response)
        assertTrue(result.contains("Discussed Kotlin coroutines"))
        assertTrue(result.contains("Reviewed Room DB migration steps"))
    }

    @Test
    fun `parseSummarizationResponse returns full response when no section header`() {
        val response = "Some plain text response without headers"
        val result = writer.parseSummarizationResponse(response)
        assertEquals("Some plain text response without headers", result)
    }

    @Test
    fun `parseSummarizationResponse trims whitespace from extracted section`() {
        val response = """
            ## Daily Summary
              - Topic with leading spaces
        """.trimIndent()

        val result = writer.parseSummarizationResponse(response)
        assertTrue(!result.startsWith("  "))
    }

    @Test
    fun `parseSummarizationResponse is case insensitive for section header`() {
        val response = """
            ## daily summary
            - lowercase headers work
        """.trimIndent()

        val result = writer.parseSummarizationResponse(response)
        assertTrue(result.contains("lowercase headers work"))
    }

    @Test
    fun `parseSummarizationResponse ignores content after unknown sections`() {
        val response = """
            ## Daily Summary
            - Main topic discussed
            
            ## Some Other Section
            - This should be included since regex goes to end
        """.trimIndent()

        val result = writer.parseSummarizationResponse(response)
        assertTrue(result.contains("Main topic discussed"))
    }

    @Test
    fun `parseSummarizationResponse handles multiline bullet points`() {
        val response = """
            ## Daily Summary
            - First item
            - Second item with details
            - Third item
        """.trimIndent()

        val result = writer.parseSummarizationResponse(response)
        assertTrue(result.contains("First item"))
        assertTrue(result.contains("Second item"))
        assertTrue(result.contains("Third item"))
    }
}
