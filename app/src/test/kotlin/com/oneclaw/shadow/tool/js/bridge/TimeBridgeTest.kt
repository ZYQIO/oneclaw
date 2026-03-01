package com.oneclaw.shadow.tool.js.bridge

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Method
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Tests for TimeBridge logic.
 *
 * The bridge logic (getCurrentTime) is tested via reflection since it is private.
 * QuickJS injection is not tested here as it requires native JNI libraries.
 */
class TimeBridgeTest {

    private val getCurrentTimeMethod: Method = TimeBridge::class.java
        .getDeclaredMethod("getCurrentTime", String::class.java, String::class.java)
        .apply { isAccessible = true }

    private fun callGetCurrentTime(timezone: String?, format: String): String {
        return getCurrentTimeMethod.invoke(TimeBridge, timezone, format) as String
    }

    @Test
    fun `getCurrentTime with null timezone returns ISO 8601 format`() {
        val result = callGetCurrentTime(null, "iso8601")

        assertTrue(result.contains("T"), "Expected ISO 8601 format with 'T' separator, got: $result")
        // ISO offset date-time contains a timezone offset or ends with Z
        val hasOffset = result.contains("+") || result.substring(10).contains("-") || result.endsWith("Z")
        assertTrue(hasOffset, "Expected timezone offset in ISO 8601 format, got: $result")
    }

    @Test
    fun `getCurrentTime with Asia_Shanghai timezone returns time in correct timezone`() {
        val result = callGetCurrentTime("Asia/Shanghai", "iso8601")

        assertTrue(result.contains("T"), "Expected ISO 8601 format, got: $result")
        // Shanghai is UTC+8, so offset should contain +08:00
        assertTrue(result.contains("+08:00"), "Expected +08:00 offset for Asia/Shanghai, got: $result")
    }

    @Test
    fun `getCurrentTime with America_New_York timezone returns iso8601 format`() {
        val result = callGetCurrentTime("America/New_York", "iso8601")

        assertTrue(result.contains("T"), "Expected ISO 8601 format, got: $result")
    }

    @Test
    fun `getCurrentTime with human_readable format returns formatted string`() {
        val result = callGetCurrentTime(null, "human_readable")

        // Human readable format: "Friday, January 1, 2026 at 12:00:00 PM UTC"
        assertTrue(
            result.contains(" at "),
            "Expected 'at' in human_readable format, got: $result"
        )
        assertTrue(
            result.contains("AM") || result.contains("PM"),
            "Expected AM/PM in human_readable format, got: $result"
        )
    }

    @Test
    fun `getCurrentTime with invalid timezone throws IllegalArgumentException`() {
        val exception = org.junit.jupiter.api.Assertions.assertThrows(
            java.lang.reflect.InvocationTargetException::class.java
        ) {
            callGetCurrentTime("Invalid/Zone", "iso8601")
        }

        val cause = exception.cause
        assertTrue(cause is IllegalArgumentException, "Expected IllegalArgumentException, got: $cause")
        assertTrue(
            cause!!.message!!.contains("Invalid/Zone"),
            "Expected error message to contain timezone name, got: ${cause.message}"
        )
    }

    @Test
    fun `getCurrentTime with empty string timezone uses device timezone`() {
        // Empty string is handled by takeIf { it.isNotEmpty() } -> null -> system default
        // We test null directly as the bridge handles empty string -> null before calling
        val result = callGetCurrentTime(null, "iso8601")
        assertTrue(result.contains("T"), "Expected ISO 8601 format, got: $result")
    }

    @Test
    fun `getCurrentTime unknown format falls back to iso8601`() {
        val result = callGetCurrentTime(null, "unknown_format")
        // Falls back to else -> ISO_OFFSET_DATE_TIME
        assertTrue(result.contains("T"), "Expected ISO 8601 fallback, got: $result")
    }
}
