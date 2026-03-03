package com.oneclaw.shadow.feature.memory

import com.oneclaw.shadow.feature.memory.compaction.MemoryCompactor
import com.oneclaw.shadow.feature.memory.longterm.LongTermMemoryManager
import com.oneclaw.shadow.feature.memory.storage.MemoryFileStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MemoryCompactorTest {

    private lateinit var longTermMemoryManager: LongTermMemoryManager
    private lateinit var memoryFileStorage: MemoryFileStorage
    private lateinit var compactor: MemoryCompactor

    private val validResponse = "# Long-term Memory\n\n## Notes\n- compacted content here for test\n"

    @BeforeEach
    fun setup() {
        longTermMemoryManager = mockk(relaxed = true)
        memoryFileStorage = mockk(relaxed = true)

        // Create a spy on MemoryCompactor so we can override callLlm without
        // needing real ProviderRepository / ApiKeyStorage / ModelApiAdapterFactory
        val base = MemoryCompactor(
            longTermMemoryManager = longTermMemoryManager,
            memoryFileStorage = memoryFileStorage,
            providerRepository = mockk(relaxed = true),
            apiKeyStorage = mockk(relaxed = true),
            adapterFactory = mockk(relaxed = true)
        )
        compactor = spyk(base)
    }

    @Test
    fun `compactIfNeeded with content below threshold returns false`() = runTest {
        coEvery { longTermMemoryManager.readMemory() } returns "short content"

        val result = compactor.compactIfNeeded()

        assertFalse(result)
    }

    @Test
    fun `compactIfNeeded with content above threshold calls LLM`() = runTest {
        val longContent = "- A fact about the user".repeat(150) // > 3000 chars
        coEvery { longTermMemoryManager.readMemory() } returns longContent
        coEvery { compactor.callLlm(any()) } returns validResponse

        val result = compactor.compactIfNeeded()

        assertTrue(result)
        coVerify { longTermMemoryManager.writeMemory(validResponse) }
    }

    @Test
    fun `compact with empty LLM response keeps original content`() = runTest {
        val longContent = "- A fact about the user".repeat(150)
        coEvery { longTermMemoryManager.readMemory() } returns longContent
        coEvery { compactor.callLlm(any()) } returns ""

        val result = compactor.compactIfNeeded()

        assertFalse(result)
        coVerify(exactly = 0) { longTermMemoryManager.writeMemory(any()) }
    }

    @Test
    fun `compact with very short LLM response keeps original content`() = runTest {
        val longContent = "- A fact about the user".repeat(150)
        coEvery { longTermMemoryManager.readMemory() } returns longContent
        coEvery { compactor.callLlm(any()) } returns "tiny"

        val result = compactor.compactIfNeeded()

        assertFalse(result)
        coVerify(exactly = 0) { longTermMemoryManager.writeMemory(any()) }
    }

    @Test
    fun `forceCompact with empty memory returns false`() = runTest {
        coEvery { longTermMemoryManager.readMemory() } returns ""

        val result = compactor.forceCompact()

        assertFalse(result)
    }

    @Test
    fun `forceCompact with content calls LLM regardless of size`() = runTest {
        val smallContent = "# Memory\n\n- Small content"
        coEvery { longTermMemoryManager.readMemory() } returns smallContent
        coEvery { compactor.callLlm(any()) } returns validResponse

        val result = compactor.forceCompact()

        assertTrue(result)
        coVerify { longTermMemoryManager.writeMemory(validResponse) }
    }

    @Test
    fun `compact success overwrites memory with compacted content`() = runTest {
        val longContent = "- A fact about the user".repeat(150)
        coEvery { longTermMemoryManager.readMemory() } returns longContent
        coEvery { compactor.callLlm(any()) } returns validResponse

        compactor.compactIfNeeded()

        coVerify { longTermMemoryManager.writeMemory(validResponse) }
    }
}
