package com.oneclaw.shadow.feature.memory

import com.oneclaw.shadow.data.local.dao.MemoryIndexDao
import com.oneclaw.shadow.feature.memory.curator.MemoryCurator
import com.oneclaw.shadow.feature.memory.embedding.EmbeddingEngine
import com.oneclaw.shadow.feature.memory.injection.MemoryInjector
import com.oneclaw.shadow.feature.memory.log.DailyLogWriter
import com.oneclaw.shadow.feature.memory.longterm.LongTermMemoryManager
import com.oneclaw.shadow.feature.memory.search.HybridSearchEngine
import com.oneclaw.shadow.feature.memory.storage.MemoryFileStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MemoryManagerCurationTest {

    private lateinit var dailyLogWriter: DailyLogWriter
    private lateinit var longTermMemoryManager: LongTermMemoryManager
    private lateinit var hybridSearchEngine: HybridSearchEngine
    private lateinit var memoryInjector: MemoryInjector
    private lateinit var memoryIndexDao: MemoryIndexDao
    private lateinit var memoryFileStorage: MemoryFileStorage
    private lateinit var embeddingEngine: EmbeddingEngine
    private lateinit var memoryCurator: MemoryCurator

    private fun buildManager(curator: MemoryCurator? = memoryCurator) = MemoryManager(
        dailyLogWriter = dailyLogWriter,
        longTermMemoryManager = longTermMemoryManager,
        hybridSearchEngine = hybridSearchEngine,
        memoryInjector = memoryInjector,
        memoryIndexDao = memoryIndexDao,
        memoryFileStorage = memoryFileStorage,
        embeddingEngine = embeddingEngine,
        memoryCurator = curator
    )

    @BeforeEach
    fun setup() {
        dailyLogWriter = mockk(relaxed = true)
        longTermMemoryManager = mockk(relaxed = true)
        hybridSearchEngine = mockk(relaxed = true)
        memoryInjector = mockk(relaxed = true)
        memoryIndexDao = mockk(relaxed = true)
        memoryFileStorage = mockk(relaxed = true)
        embeddingEngine = mockk(relaxed = true)
        memoryCurator = mockk(relaxed = true)

        every { embeddingEngine.isAvailable() } returns false
        coEvery { embeddingEngine.embed(any()) } returns null
        coEvery { memoryIndexDao.insertAll(any()) } returns Unit
        coEvery { memoryIndexDao.count() } returns 0
        coEvery { memoryIndexDao.deleteAll() } returns Unit
        coEvery { memoryFileStorage.listDailyLogDates() } returns emptyList()
        coEvery { memoryFileStorage.readMemoryFile() } returns null
    }

    @Test
    fun `curateMemory delegates to MemoryCurator and rebuilds index on success`() = runTest {
        coEvery { memoryCurator.curate() } returns true
        val manager = buildManager()

        val result = manager.curateMemory()

        assertTrue(result)
        coVerify { memoryCurator.curate() }
        coVerify { memoryIndexDao.deleteAll() }
    }

    @Test
    fun `curateMemory does not rebuild index when no changes`() = runTest {
        coEvery { memoryCurator.curate() } returns false
        val manager = buildManager()

        val result = manager.curateMemory()

        assertFalse(result)
        coVerify(exactly = 0) { memoryIndexDao.deleteAll() }
    }

    @Test
    fun `curateMemory returns false gracefully when curator is null`() = runTest {
        val manager = buildManager(curator = null)

        val result = manager.curateMemory()

        assertFalse(result)
    }
}
