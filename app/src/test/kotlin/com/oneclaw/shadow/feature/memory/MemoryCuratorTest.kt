package com.oneclaw.shadow.feature.memory

import com.oneclaw.shadow.feature.memory.compaction.MemoryCompactor
import com.oneclaw.shadow.feature.memory.curator.MemoryCurator
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
import java.time.LocalDate

class MemoryCuratorTest {

    private lateinit var memoryFileStorage: MemoryFileStorage
    private lateinit var longTermMemoryManager: LongTermMemoryManager
    private lateinit var memoryCompactor: MemoryCompactor
    private lateinit var curator: MemoryCurator

    private val validCurationResponse = """
        # Long-term Memory

        ## User Profile
        - Software engineer specializing in Android
        - Name: Ben

        ## Preferences
        - Prefers dark mode
        - Uses Kotlin exclusively
    """.trimIndent()

    @BeforeEach
    fun setup() {
        memoryFileStorage = mockk(relaxed = true)
        longTermMemoryManager = mockk(relaxed = true)
        memoryCompactor = mockk(relaxed = true)

        val base = MemoryCurator(
            memoryFileStorage = memoryFileStorage,
            longTermMemoryManager = longTermMemoryManager,
            memoryCompactor = memoryCompactor,
            providerRepository = mockk(relaxed = true),
            apiKeyStorage = mockk(relaxed = true),
            adapterFactory = mockk(relaxed = true)
        )
        curator = spyk(base)
    }

    // --- curate() tests ---

    @Test
    fun `curate with no recent daily logs returns false`() = runTest {
        coEvery { memoryFileStorage.readDailyLog(any()) } returns null

        val result = curator.curate()

        assertFalse(result)
        coVerify(exactly = 0) { longTermMemoryManager.writeMemory(any()) }
    }

    @Test
    fun `curate with daily logs and valid response updates MEMORY md`() = runTest {
        val today = LocalDate.now()
        coEvery { memoryFileStorage.readDailyLog(today.toString()) } returns "- Discussed project setup"
        coEvery { longTermMemoryManager.readMemory() } returns "# Long-term Memory\n\n## Notes\n- Old note"
        coEvery { curator.callLlm(any()) } returns validCurationResponse

        val result = curator.curate()

        assertTrue(result)
        coVerify { longTermMemoryManager.writeMemory(validCurationResponse) }
    }

    @Test
    fun `curate with NO_CHANGES response does not update MEMORY md`() = runTest {
        val today = LocalDate.now()
        coEvery { memoryFileStorage.readDailyLog(today.toString()) } returns "- Quick chat about settings"
        coEvery { longTermMemoryManager.readMemory() } returns "# Long-term Memory\n\n## Notes\n- Old note"
        coEvery { curator.callLlm(any()) } returns "NO_CHANGES"

        val result = curator.curate()

        assertFalse(result)
        coVerify(exactly = 0) { longTermMemoryManager.writeMemory(any()) }
    }

    @Test
    fun `curate with empty LLM response does not update MEMORY md`() = runTest {
        val today = LocalDate.now()
        coEvery { memoryFileStorage.readDailyLog(today.toString()) } returns "- Some log"
        coEvery { longTermMemoryManager.readMemory() } returns "existing memory"
        coEvery { curator.callLlm(any()) } returns ""

        val result = curator.curate()

        assertFalse(result)
        coVerify(exactly = 0) { longTermMemoryManager.writeMemory(any()) }
    }

    @Test
    fun `curate with suspiciously short response does not update MEMORY md`() = runTest {
        val today = LocalDate.now()
        coEvery { memoryFileStorage.readDailyLog(today.toString()) } returns "- Some log"
        coEvery { longTermMemoryManager.readMemory() } returns "existing memory"
        coEvery { curator.callLlm(any()) } returns "short"

        val result = curator.curate()

        assertFalse(result)
        coVerify(exactly = 0) { longTermMemoryManager.writeMemory(any()) }
    }

    @Test
    fun `curate with response missing header does not update MEMORY md`() = runTest {
        val today = LocalDate.now()
        coEvery { memoryFileStorage.readDailyLog(today.toString()) } returns "- Some log"
        coEvery { longTermMemoryManager.readMemory() } returns "existing memory"
        coEvery { curator.callLlm(any()) } returns "This is a response without the proper header and is long enough to pass length check."

        val result = curator.curate()

        assertFalse(result)
        coVerify(exactly = 0) { longTermMemoryManager.writeMemory(any()) }
    }

    @Test
    fun `curate with null LLM response returns false`() = runTest {
        val today = LocalDate.now()
        coEvery { memoryFileStorage.readDailyLog(today.toString()) } returns "- Some log"
        coEvery { longTermMemoryManager.readMemory() } returns "existing memory"
        coEvery { curator.callLlm(any()) } returns null

        val result = curator.curate()

        assertFalse(result)
    }

    @Test
    fun `curate runs compaction after successful update`() = runTest {
        val today = LocalDate.now()
        coEvery { memoryFileStorage.readDailyLog(today.toString()) } returns "- Some log"
        coEvery { longTermMemoryManager.readMemory() } returns "# Long-term Memory\n\n## Notes\n- Old"
        coEvery { curator.callLlm(any()) } returns validCurationResponse

        curator.curate()

        coVerify { memoryCompactor.compactIfNeeded() }
    }

    // --- consolidateYesterdayLog() tests ---

    @Test
    fun `consolidateYesterdayLog with no yesterday log does nothing`() = runTest {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1).toString()
        coEvery { memoryFileStorage.readDailyLog(yesterday) } returns null

        curator.consolidateYesterdayLog(today)

        coVerify(exactly = 0) { curator.callLlm(any()) }
        coVerify(exactly = 0) { memoryFileStorage.writeDailyLog(any(), any()) }
    }

    @Test
    fun `consolidateYesterdayLog with single block skips consolidation`() = runTest {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1).toString()
        coEvery { memoryFileStorage.readDailyLog(yesterday) } returns "# Daily Log\n- Single block content"

        curator.consolidateYesterdayLog(today)

        coVerify(exactly = 0) { curator.callLlm(any()) }
        coVerify(exactly = 0) { memoryFileStorage.writeDailyLog(any(), any()) }
    }

    @Test
    fun `consolidateYesterdayLog with multiple blocks calls LLM and overwrites`() = runTest {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1).toString()
        val multiBlockLog = "Block 1 content\n---\nBlock 2 content\n---\nBlock 3 content"
        val consolidatedResponse = "# Daily Log - $yesterday\n- Consolidated summary"

        coEvery { memoryFileStorage.readDailyLog(yesterday) } returns multiBlockLog
        coEvery { curator.callLlm(any()) } returns consolidatedResponse

        curator.consolidateYesterdayLog(today)

        coVerify { memoryFileStorage.writeDailyLog(yesterday, consolidatedResponse) }
    }

    @Test
    fun `consolidateYesterdayLog with short LLM response preserves original`() = runTest {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1).toString()
        val multiBlockLog = "Block 1\n---\nBlock 2"

        coEvery { memoryFileStorage.readDailyLog(yesterday) } returns multiBlockLog
        coEvery { curator.callLlm(any()) } returns "tiny"

        curator.consolidateYesterdayLog(today)

        coVerify(exactly = 0) { memoryFileStorage.writeDailyLog(any(), any()) }
    }

    @Test
    fun `consolidateYesterdayLog with null LLM response preserves original`() = runTest {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1).toString()
        val multiBlockLog = "Block 1\n---\nBlock 2"

        coEvery { memoryFileStorage.readDailyLog(yesterday) } returns multiBlockLog
        coEvery { curator.callLlm(any()) } returns null

        curator.consolidateYesterdayLog(today)

        coVerify(exactly = 0) { memoryFileStorage.writeDailyLog(any(), any()) }
    }

    // --- Prompt building tests ---

    @Test
    fun `buildConsolidationPrompt includes date and raw log content`() {
        val prompt = curator.buildConsolidationPrompt("2026-03-03", "Block 1\n---\nBlock 2")

        assertTrue(prompt.contains("2026-03-03"))
        assertTrue(prompt.contains("Block 1"))
        assertTrue(prompt.contains("Block 2"))
        assertTrue(prompt.contains("# Daily Log - 2026-03-03"))
    }

    @Test
    fun `buildCurationPrompt includes current memory recent logs and today date`() {
        val prompt = curator.buildCurationPrompt(
            currentMemory = "# Long-term Memory\n\n- User likes Kotlin",
            recentDailyLogs = "--- 2026-03-04 ---\n- Discussed testing",
            todayDate = "2026-03-04"
        )

        assertTrue(prompt.contains("User likes Kotlin"))
        assertTrue(prompt.contains("Discussed testing"))
        assertTrue(prompt.contains("2026-03-04"))
        assertTrue(prompt.contains("NO_CHANGES"))
        assertTrue(prompt.contains("Habits/Routines"))
        assertTrue(prompt.contains("max 10"))
        assertTrue(prompt.contains("expired"))
    }

    @Test
    fun `buildCurationPrompt references correct lookback days`() {
        val prompt = curator.buildCurationPrompt("memory", "logs", "2026-03-04")
        assertTrue(prompt.contains("last ${MemoryCurator.LOOKBACK_DAYS} days"))
    }
}
