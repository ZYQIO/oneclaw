package com.oneclaw.shadow.core.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AppResultTest {

    @Test
    fun `Success isSuccess returns true`() {
        val result: AppResult<String> = AppResult.Success("data")
        assertTrue(result.isSuccess)
        assertFalse(result.isError)
    }

    @Test
    fun `Error isError returns true`() {
        val result: AppResult<String> = AppResult.Error(message = "failed")
        assertTrue(result.isError)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `Success getOrNull returns data`() {
        val result: AppResult<String> = AppResult.Success("hello")
        assertEquals("hello", result.getOrNull())
    }

    @Test
    fun `Error getOrNull returns null`() {
        val result: AppResult<String> = AppResult.Error(message = "failed")
        assertNull(result.getOrNull())
    }

    @Test
    fun `Success getOrThrow returns data`() {
        val result: AppResult<Int> = AppResult.Success(42)
        assertEquals(42, result.getOrThrow())
    }

    @Test
    fun `Error getOrThrow throws exception`() {
        val original = IllegalStateException("boom")
        val result: AppResult<Int> = AppResult.Error(exception = original, message = "failed")
        val thrown = assertThrows<IllegalStateException> { result.getOrThrow() }
        assertEquals("boom", thrown.message)
    }

    @Test
    fun `Error getOrThrow throws RuntimeException when no exception provided`() {
        val result: AppResult<Int> = AppResult.Error(message = "no exception")
        val thrown = assertThrows<RuntimeException> { result.getOrThrow() }
        assertEquals("no exception", thrown.message)
    }

    @Test
    fun `Success map transforms data`() {
        val result: AppResult<Int> = AppResult.Success(10)
        val mapped = result.map { it * 2 }
        assertEquals(20, mapped.getOrNull())
    }

    @Test
    fun `Error map propagates error`() {
        val result: AppResult<Int> = AppResult.Error(message = "failed", code = ErrorCode.NETWORK_ERROR)
        val mapped = result.map { it * 2 }
        assertTrue(mapped.isError)
        val error = mapped as AppResult.Error
        assertEquals("failed", error.message)
        assertEquals(ErrorCode.NETWORK_ERROR, error.code)
    }

    @Test
    fun `Error default code is UNKNOWN`() {
        val result = AppResult.Error(message = "test")
        assertEquals(ErrorCode.UNKNOWN, result.code)
    }

    @Test
    fun `Success with Unit data`() {
        val result: AppResult<Unit> = AppResult.Success(Unit)
        assertTrue(result.isSuccess)
        assertEquals(Unit, result.getOrNull())
    }
}
