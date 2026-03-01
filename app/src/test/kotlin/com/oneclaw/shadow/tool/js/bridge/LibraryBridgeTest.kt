package com.oneclaw.shadow.tool.js.bridge

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.lang.reflect.Method
import org.junit.jupiter.api.io.TempDir

/**
 * Tests for LibraryBridge logic.
 *
 * Tests the source loading logic (loadLibrarySource) via reflection.
 * QuickJS injection is not tested here as it requires native JNI libraries.
 */
class LibraryBridgeTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var context: Context
    private lateinit var assetManager: AssetManager
    private lateinit var bridge: LibraryBridge

    private val loadLibrarySourceMethod: Method by lazy {
        LibraryBridge::class.java
            .getDeclaredMethod("loadLibrarySource", String::class.java)
            .apply { isAccessible = true }
    }

    private fun callLoadLibrarySource(name: String): String {
        return loadLibrarySourceMethod.invoke(bridge, name) as String
    }

    @BeforeEach
    fun setup() {
        assetManager = mockk()
        context = mockk()
        every { context.assets } returns assetManager
        every { context.filesDir } returns tempDir
    }

    @Test
    fun `loadLibrarySource loads from assets min js`() {
        val fakeSource = "/* turndown */ function TurndownService() {}"
        every { assetManager.open("js/lib/turndown.min.js") } returns
            ByteArrayInputStream(fakeSource.toByteArray())

        bridge = LibraryBridge(context)
        val result = callLoadLibrarySource("turndown")

        assertEquals(fakeSource, result)
    }

    @Test
    fun `loadLibrarySource falls back to plain js when min js not found`() {
        val fakeSource = "/* mylib */ var MyLib = {};"
        every { assetManager.open("js/lib/mylib.min.js") } throws java.io.FileNotFoundException("not found")
        every { assetManager.open("js/lib/mylib.js") } returns
            ByteArrayInputStream(fakeSource.toByteArray())

        bridge = LibraryBridge(context)
        val result = callLoadLibrarySource("mylib")

        assertEquals(fakeSource, result)
    }

    @Test
    fun `loadLibrarySource falls back to internal storage when asset not found`() {
        every { assetManager.open(any()) } throws java.io.FileNotFoundException("not found")

        // Create the internal lib file
        val internalLibDir = File(tempDir, "js/lib")
        internalLibDir.mkdirs()
        val libFile = File(internalLibDir, "customlib.min.js")
        libFile.writeText("/* custom */ var CustomLib = {};")

        bridge = LibraryBridge(context)
        val result = callLoadLibrarySource("customlib")

        assertEquals("/* custom */ var CustomLib = {};", result)
    }

    @Test
    fun `loadLibrarySource throws when library not found anywhere`() {
        every { assetManager.open(any()) } throws java.io.FileNotFoundException("not found")

        bridge = LibraryBridge(context)
        val exception = assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
            callLoadLibrarySource("nonexistent")
        }

        val cause = exception.cause
        assertTrue(cause is IllegalArgumentException, "Expected IllegalArgumentException, got: $cause")
        assertTrue(
            cause!!.message!!.contains("nonexistent"),
            "Expected error to mention library name, got: ${cause.message}"
        )
    }

    @Test
    fun `loadLibrarySource rejects invalid library name with path traversal`() {
        bridge = LibraryBridge(context)
        val exception = assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
            callLoadLibrarySource("../etc/passwd")
        }

        val cause = exception.cause
        assertTrue(cause is IllegalArgumentException, "Expected IllegalArgumentException, got: $cause")
        assertTrue(
            cause!!.message!!.contains("Invalid library name"),
            "Expected 'Invalid library name' in error, got: ${cause.message}"
        )
    }

    @Test
    fun `loadLibrarySource rejects empty string library name`() {
        bridge = LibraryBridge(context)
        val exception = assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
            callLoadLibrarySource("")
        }

        val cause = exception.cause
        assertTrue(cause is IllegalArgumentException, "Expected IllegalArgumentException, got: $cause")
    }

    @Test
    fun `loadLibrarySource caches source across calls`() {
        val fakeSource = "/* cached */"
        var openCount = 0
        every { assetManager.open("js/lib/mylib.min.js") } answers {
            openCount++
            ByteArrayInputStream(fakeSource.toByteArray())
        }

        bridge = LibraryBridge(context)
        val result1 = callLoadLibrarySource("mylib")
        val result2 = callLoadLibrarySource("mylib")

        assertEquals(fakeSource, result1)
        assertEquals(fakeSource, result2)
        assertEquals(1, openCount, "Expected asset to be opened only once due to caching")
    }

    @Test
    fun `loadLibrarySource accepts valid alphanumeric name with hyphens and underscores`() {
        val fakeSource = "/* valid lib */"
        every { assetManager.open("js/lib/my-lib_v2.min.js") } returns
            ByteArrayInputStream(fakeSource.toByteArray())

        bridge = LibraryBridge(context)
        val result = callLoadLibrarySource("my-lib_v2")

        assertEquals(fakeSource, result)
    }
}
