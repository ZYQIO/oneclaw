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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class MessageDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var messageDao: MessageDao
    private lateinit var sessionDao: SessionDao

    private fun createMessage(
        id: String = "msg-1",
        sessionId: String = "session-test",
        type: String = "USER",
        content: String = "Hello",
        createdAt: Long = 1000L
    ) = MessageEntity(
        id = id,
        sessionId = sessionId,
        type = type,
        content = content,
        thinkingContent = null,
        toolCallId = null,
        toolName = null,
        toolInput = null,
        toolOutput = null,
        toolStatus = null,
        toolDurationMs = null,
        tokenCountInput = null,
        tokenCountOutput = null,
        modelId = null,
        providerId = null,
        createdAt = createdAt
    )

    @Before
    fun setup() {
        database = TestDatabaseHelper.createInMemoryDatabase(
            ApplicationProvider.getApplicationContext()
        )
        messageDao = database.messageDao()
        sessionDao = database.sessionDao()

        kotlinx.coroutines.runBlocking {
            sessionDao.insert(
                SessionEntity(
                    id = "session-test",
                    title = "Test Session",
                    currentAgentId = "agent-general-assistant",
                    messageCount = 0,
                    lastMessagePreview = null,
                    isActive = false,
                    deletedAt = null,
                    createdAt = 1000L,
                    updatedAt = 1000L
                )
            )
        }
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndQueryMessagesForSession() = runTest {
        messageDao.insert(createMessage(id = "msg-1", createdAt = 1000L))
        messageDao.insert(createMessage(id = "msg-2", createdAt = 2000L))

        messageDao.getMessagesForSession("session-test").test {
            val messages = awaitItem()
            assertEquals(2, messages.size)
            assertEquals("msg-1", messages[0].id)
            assertEquals("msg-2", messages[1].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteSingleMessage() = runTest {
        messageDao.insert(createMessage(id = "msg-1"))
        messageDao.insert(createMessage(id = "msg-2"))

        messageDao.delete("msg-1")

        val snapshot = messageDao.getMessagesSnapshot("session-test")
        assertEquals(1, snapshot.size)
        assertEquals("msg-2", snapshot[0].id)
    }

    @Test
    fun deleteAllMessagesForSession() = runTest {
        messageDao.insert(createMessage(id = "msg-1"))
        messageDao.insert(createMessage(id = "msg-2"))
        messageDao.insert(createMessage(id = "msg-3"))

        messageDao.deleteForSession("session-test")

        val count = messageDao.getMessageCount("session-test")
        assertEquals(0, count)
    }

    @Test
    fun getMessageCountReturnsCorrectCount() = runTest {
        assertEquals(0, messageDao.getMessageCount("session-test"))

        messageDao.insert(createMessage(id = "msg-1"))
        messageDao.insert(createMessage(id = "msg-2"))

        assertEquals(2, messageDao.getMessageCount("session-test"))
    }

    @Test
    fun getMessagesSnapshotReturnsNonReactiveList() = runTest {
        messageDao.insert(createMessage(id = "msg-1", createdAt = 1000L))
        messageDao.insert(createMessage(id = "msg-2", createdAt = 2000L))

        val snapshot = messageDao.getMessagesSnapshot("session-test")
        assertEquals(2, snapshot.size)
        assertEquals("msg-1", snapshot[0].id)
        assertEquals("msg-2", snapshot[1].id)
    }

    @Test
    fun updateMessage() = runTest {
        messageDao.insert(createMessage(id = "msg-1", content = "Original"))

        val updated = createMessage(id = "msg-1", content = "Updated")
        messageDao.update(updated)

        val snapshot = messageDao.getMessagesSnapshot("session-test")
        assertEquals("Updated", snapshot[0].content)
    }

    @Test
    fun messagesFromDifferentSessionsAreIsolated() = runTest {
        sessionDao.insert(
            SessionEntity(
                id = "session-2",
                title = "Other Session",
                currentAgentId = "agent-general-assistant",
                messageCount = 0,
                lastMessagePreview = null,
                isActive = false,
                deletedAt = null,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )

        messageDao.insert(createMessage(id = "msg-s1", sessionId = "session-test"))
        messageDao.insert(createMessage(id = "msg-s2", sessionId = "session-2"))

        val s1Messages = messageDao.getMessagesSnapshot("session-test")
        val s2Messages = messageDao.getMessagesSnapshot("session-2")

        assertEquals(1, s1Messages.size)
        assertEquals("msg-s1", s1Messages[0].id)
        assertEquals(1, s2Messages.size)
        assertEquals("msg-s2", s2Messages[0].id)
    }
}
