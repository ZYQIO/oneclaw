package com.oneclaw.shadow.data.local.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConvertersTest {

    private lateinit var converters: Converters

    @BeforeEach
    fun setup() {
        converters = Converters()
    }

    @Test
    fun `fromStringList converts list to JSON array`() {
        val list = listOf("read_file", "write_file", "http_request")
        val json = converters.fromStringList(list)
        assertEquals("""["read_file","write_file","http_request"]""", json)
    }

    @Test
    fun `fromStringList converts empty list to empty JSON array`() {
        val json = converters.fromStringList(emptyList())
        assertEquals("[]", json)
    }

    @Test
    fun `toStringList parses JSON array`() {
        val json = """["tool_a","tool_b"]"""
        val list = converters.toStringList(json)
        assertEquals(listOf("tool_a", "tool_b"), list)
    }

    @Test
    fun `toStringList parses empty JSON array`() {
        val list = converters.toStringList("[]")
        assertEquals(emptyList<String>(), list)
    }

    @Test
    fun `toStringList returns empty list for invalid JSON`() {
        val list = converters.toStringList("not-json")
        assertEquals(emptyList<String>(), list)
    }

    @Test
    fun `toStringList returns empty list for empty string`() {
        val list = converters.toStringList("")
        assertEquals(emptyList<String>(), list)
    }

    @Test
    fun `roundtrip preserves list data`() {
        val original = listOf("get_current_time", "read_file", "write_file", "http_request")
        val roundtripped = converters.toStringList(converters.fromStringList(original))
        assertEquals(original, roundtripped)
    }

    @Test
    fun `fromStringList handles strings with special characters`() {
        val list = listOf("tool with spaces", "tool/with/slashes", "tool\"with\"quotes")
        val json = converters.fromStringList(list)
        val restored = converters.toStringList(json)
        assertEquals(list, restored)
    }
}
