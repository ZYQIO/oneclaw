package com.oneclaw.shadow.tool.js.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FsBridgeTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var bridge: FsBridge

    @BeforeEach
    fun setUp() {
        bridge = FsBridge(tempDir)
    }

    // --- validatePath ---

    @Test
    fun `relative path resolves against allowedRoot`() {
        val resolved = bridge.validatePath("subdir/file.txt")
        assertEquals(File(tempDir, "subdir/file.txt").canonicalPath, resolved)
    }

    @Test
    fun `absolute path within root is allowed`() {
        val absPath = File(tempDir, "data.json").absolutePath
        val resolved = bridge.validatePath(absPath)
        assertEquals(File(tempDir, "data.json").canonicalPath, resolved)
    }

    @Test
    fun `absolute path outside root is blocked`() {
        val exception = assertThrows(SecurityException::class.java) {
            bridge.validatePath("/etc/passwd")
        }
        assertTrue(exception.message!!.contains("outside app storage"))
    }

    @Test
    fun `path traversal via dot-dot is blocked`() {
        val exception = assertThrows(SecurityException::class.java) {
            bridge.validatePath("../../../etc/passwd")
        }
        assertTrue(exception.message!!.contains("outside app storage"))
    }

    @Test
    fun `path traversal via absolute with dot-dot is blocked`() {
        val exception = assertThrows(SecurityException::class.java) {
            bridge.validatePath(tempDir.absolutePath + "/../../../etc/passwd")
        }
        assertTrue(exception.message!!.contains("outside app storage"))
    }

    @Test
    fun `root path itself is allowed`() {
        val resolved = bridge.validatePath(tempDir.absolutePath)
        assertEquals(tempDir.canonicalPath, resolved)
    }

    // --- readFile ---

    @Test
    fun `readFile returns file content`() {
        val file = File(tempDir, "hello.txt")
        file.writeText("Hello, world!")

        val content = bridge.readFile("hello.txt")
        assertEquals("Hello, world!", content)
    }

    @Test
    fun `readFile throws for missing file`() {
        assertThrows(IllegalArgumentException::class.java) {
            bridge.readFile("nonexistent.txt")
        }
    }

    @Test
    fun `readFile throws for directory`() {
        File(tempDir, "subdir").mkdirs()
        assertThrows(IllegalArgumentException::class.java) {
            bridge.readFile("subdir")
        }
    }

    @Test
    fun `readFile throws for path outside root`() {
        assertThrows(SecurityException::class.java) {
            bridge.readFile("/etc/passwd")
        }
    }

    // --- writeFile ---

    @Test
    fun `writeFile creates file with content`() {
        bridge.writeFile("output.txt", "test content")

        val file = File(tempDir, "output.txt")
        assertTrue(file.exists())
        assertEquals("test content", file.readText())
    }

    @Test
    fun `writeFile creates parent directories`() {
        bridge.writeFile("a/b/c/deep.txt", "deep content")

        val file = File(tempDir, "a/b/c/deep.txt")
        assertTrue(file.exists())
        assertEquals("deep content", file.readText())
    }

    @Test
    fun `writeFile throws for path outside root`() {
        assertThrows(SecurityException::class.java) {
            bridge.writeFile("/tmp/evil.txt", "bad")
        }
    }

    // --- appendFile ---

    @Test
    fun `appendFile appends to existing file`() {
        val file = File(tempDir, "log.txt")
        file.writeText("line1\n")

        bridge.appendFile("log.txt", "line2\n")

        assertEquals("line1\nline2\n", file.readText())
    }

    @Test
    fun `appendFile creates file if not exists`() {
        bridge.appendFile("new.txt", "first line")

        val file = File(tempDir, "new.txt")
        assertTrue(file.exists())
        assertEquals("first line", file.readText())
    }

    // --- fileExists ---

    @Test
    fun `fileExists returns true for existing file`() {
        File(tempDir, "exists.txt").writeText("hi")
        assertTrue(bridge.fileExists("exists.txt"))
    }

    @Test
    fun `fileExists returns false for missing file`() {
        assertFalse(bridge.fileExists("missing.txt"))
    }

    @Test
    fun `fileExists returns false for path outside root`() {
        assertFalse(bridge.fileExists("/etc/passwd"))
    }
}
