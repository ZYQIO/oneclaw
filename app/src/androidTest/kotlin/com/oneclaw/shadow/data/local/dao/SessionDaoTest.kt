package com.oneclaw.shadow.data.local.dao

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.oneclaw.shadow.data.local.db.AppDatabase
import com.oneclaw.shadow.data.local.entity.MessageEntity
import com.oneclaw.shadow.data.local.entity.SessionEntity
import com.oneclaw.shadow.testutil.TestDatabaseHelper
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class SessionDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var sessionDao: SessionDao
    private lateinit var messageDao: MessageDao

    private fun createSession(
        id: String = "session-test",
        title: String = "Test Session",
        updatedAt: Long = 1000L,
        deletedAt: Long? = null
    ) = SessionEntity(
        id = id,
        title = title,
        currentAgentId = "agent-general-assistant",
        messageCount = 0,
        lastMessagePreview = null,
        isActive = false,
        deletedAt = deletedAt,
        createdAt = 1000L,
        updatedAt = updatedAt
    )

    @Before
    fun setup() {
        database = TestDatabaseHelper.createInMemoryDatabase(
            ApplicationProvider.getApplicationContext()
        )
        sessionDao = database.sessionDao()
        messageDao = database.messageDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndQuerySession() = runTest {
        val session = createSession()
        sessionDao.insert(session)

        val result = sessionDao.getSessionById("session-test")
        assertNotNull(result)
        assertEquals("Test Session", result!!.title)
    }

    @Test
    fun softDeleteSetsDeletedAt() = runTest {
        sessionDao.insert(createSession())

        sessionDao.softDelete("session-test", 5000L)

        val result = sessionDao.getSessionById("session-test")
        assertNotNull(result)
        assertEquals(5000L, result!!.deletedAt)
    }

    @Test
    fun queryExcludesSoftDeletedSessions() = runTest {
        sessionDao.insert(createSession(id = "s-1", title = "Active"))
        sessionDao.insert(createSession(id = "s-2", title = "Deleted", deletedAt = 5000L))
        sessionDao.insert(createSession(id = "s-3", title = "Active 2"))

        sessionDao.getAllSessions().test {
            val sessions = awaitItem()
            assertEquals(2, sessions.size)
            assertEquals(true, sessions.none { it.id == "s-2" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun restoreSessionClearsDeletedAt() = runTest {
        sessionDao.insert(createSession(deletedAt = 5000L))

        sessionDao.restore("session-test")

        val result = sessionDao.getSessionById("session-test")
        assertNull(result!!.deletedAt)
    }

    @Test
    fun hardDeleteRemovesSession() = runTest {
        sessionDao.insert(createSession())

        sessionDao.hardDelete("session-test")

        val result = sessionDao.getSessionById("session-test")
        assertNull(result)
    }

    @Test
    fun hardDeleteAllSoftDeletedRemovesOnlySoftDeleted() = runTest {
        sessionDao.insert(createSession(id = "s-active", title = "Active"))
        sessionDao.insert(createSession(id = "s-deleted-1", title = "Deleted 1", deletedAt = 1000L))
        sessionDao.insert(createSession(id = "s-deleted-2", title = "Deleted 2", deletedAt = 2000L))

        sessionDao.hardDeleteAllSoftDeleted()

        assertNotNull(sessionDao.getSessionById("s-active"))
        assertNull(sessionDao.getSessionById("s-deleted-1"))
        assertNull(sessionDao.getSessionById("s-deleted-2"))
    }

    @Test
    fun cascadeDeleteRemovesMessages() = runTest {
        sessionDao.insert(createSession(id = "s-cascade"))
        messageDao.insert(
            MessageEntity(
                id = "msg-1", sessionId = "s-cascade", type = "USER",
                content = "Hello", thinkingContent = null,
                toolCallId = null, toolName = null, toolInput = null,
                toolOutput = null, toolStatus = null, toolDurationMs = null,
                tokenCountInput = null, tokenCountOutput = null,
                modelId = null, providerId = null, createdAt = 1000L
            )
        )

        assertEquals(1, messageDao.getMessageCount("s-cascade"))

        sessionDao.hardDelete("s-cascade")

        assertEquals(0, messageDao.getMessageCount("s-cascade"))
    }

    @Test
    fun queryOrderedByUpdatedAtDescending() = runTest {
        sessionDao.insert(createSession(id = "s-old", title = "Old", updatedAt = 1000L))
        sessionDao.insert(createSession(id = "s-mid", title = "Mid", updatedAt = 2000L))
        sessionDao.insert(createSession(id = "s-new", title = "New", updatedAt = 3000L))

        sessionDao.getAllSessions().test {
            val sessions = awaitItem()
            assertEquals(3, sessions.size)
            assertEquals("s-new", sessions[0].id)
            assertEquals("s-mid", sessions[1].id)
            assertEquals("s-old", sessions[2].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun updateTitleUpdatesSessionTitle() = runTest {
        sessionDao.insert(createSession())

        sessionDao.updateTitle("session-test", "New Title", 5000L)

        val result = sessionDao.getSessionById("session-test")
        assertEquals("New Title", result!!.title)
        assertEquals(5000L, result.updatedAt)
    }

    @Test
    fun updateMessageStatsUpdatesCountAndPreview() = runTest {
        sessionDao.insert(createSession())

        sessionDao.updateMessageStats("session-test", 10, "Last message", 5000L)

        val result = sessionDao.getSessionById("session-test")
        assertEquals(10, result!!.messageCount)
        assertEquals("Last message", result.lastMessagePreview)
    }

    @Test
    fun updateCurrentAgentUpdatesSession() = runTest {
        sessionDao.insert(createSession())

        sessionDao.updateCurrentAgent("session-test", "agent-custom", 5000L)

        val result = sessionDao.getSessionById("session-test")
        assertEquals("agent-custom", result!!.currentAgentId)
    }

    @Test
    fun updateAgentForSessionsUpdatesBulk() = runTest {
        sessionDao.insert(createSession(id = "s-1"))
        sessionDao.insert(createSession(id = "s-2"))

        sessionDao.updateAgentForSessions("agent-general-assistant", "agent-new")

        assertEquals("agent-new", sessionDao.getSessionById("s-1")!!.currentAgentId)
        assertEquals("agent-new", sessionDao.getSessionById("s-2")!!.currentAgentId)
    }

    @Test
    fun softDeleteBatchDeletesMultiple() = runTest {
        sessionDao.insert(createSession(id = "s-1"))
        sessionDao.insert(createSession(id = "s-2"))
        sessionDao.insert(createSession(id = "s-3"))

        sessionDao.softDeleteBatch(listOf("s-1", "s-3"), 9000L)

        assertEquals(9000L, sessionDao.getSessionById("s-1")!!.deletedAt)
        assertNull(sessionDao.getSessionById("s-2")!!.deletedAt)
        assertEquals(9000L, sessionDao.getSessionById("s-3")!!.deletedAt)
    }

    @Test
    fun restoreBatchRestoresMultiple() = runTest {
        sessionDao.insert(createSession(id = "s-1", deletedAt = 5000L))
        sessionDao.insert(createSession(id = "s-2", deletedAt = 5000L))

        sessionDao.restoreBatch(listOf("s-1", "s-2"))

        assertNull(sessionDao.getSessionById("s-1")!!.deletedAt)
        assertNull(sessionDao.getSessionById("s-2")!!.deletedAt)
    }
}
