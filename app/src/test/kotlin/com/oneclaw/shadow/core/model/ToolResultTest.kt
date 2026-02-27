package com.oneclaw.shadow.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ToolResultTest {

    @Test
    fun `success factory creates SUCCESS result`() {
        val result = ToolResult.success("Operation completed.")

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertEquals("Operation completed.", result.result)
        assertNull(result.errorType)
        assertNull(result.errorMessage)
    }

    @Test
    fun `error factory creates ERROR result`() {
        val result = ToolResult.error("execution_error", "Something went wrong.")

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertNull(result.result)
        assertEquals("execution_error", result.errorType)
        assertEquals("Something went wrong.", result.errorMessage)
    }
}
