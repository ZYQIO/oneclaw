package com.oneclaw.shadow.data.local.dao

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.oneclaw.shadow.data.local.db.AppDatabase
import com.oneclaw.shadow.data.local.entity.AgentEntity
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
class AgentDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var agentDao: AgentDao

    private fun createAgent(
        id: String = "agent-test",
        name: String = "Test Agent",
        isBuiltIn: Boolean = false
    ) = AgentEntity(
        id = id,
        name = name,
        description = "A test agent",
        systemPrompt = "You are helpful.",
        toolIds = """["read_file","write_file"]""",
        preferredProviderId = null,
        preferredModelId = null,
        isBuiltIn = isBuiltIn,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Before
    fun setup() {
        database = TestDatabaseHelper.createInMemoryDatabase(
            ApplicationProvider.getApplicationContext()
        )
        agentDao = database.agentDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndQueryAgent() = runTest {
        val agent = createAgent()
        agentDao.insert(agent)

        val result = agentDao.getAgentById("agent-test")
        assertNotNull(result)
        assertEquals("Test Agent", result!!.name)
        assertEquals("""["read_file","write_file"]""", result.toolIds)
    }

    @Test
    fun updateAgent() = runTest {
        val agent = createAgent()
        agentDao.insert(agent)

        val updated = agent.copy(name = "Updated Agent", updatedAt = 2000L)
        agentDao.update(updated)

        val result = agentDao.getAgentById("agent-test")
        assertEquals("Updated Agent", result!!.name)
        assertEquals(2000L, result.updatedAt)
    }

    @Test
    fun deleteCustomAgent() = runTest {
        val agent = createAgent(isBuiltIn = false)
        agentDao.insert(agent)
        val deleted = agentDao.deleteCustomAgent("agent-test")

        assertEquals(1, deleted)
        val result = agentDao.getAgentById("agent-test")
        assertNull(result)
    }

    @Test
    fun deleteCustomAgent_doesNotDeleteBuiltIn() = runTest {
        val agent = createAgent(isBuiltIn = true)
        agentDao.insert(agent)
        val deleted = agentDao.deleteCustomAgent("agent-test")

        assertEquals(0, deleted)
        val result = agentDao.getAgentById("agent-test")
        assertNotNull(result)
    }

    @Test
    fun queryBuiltInAgents() = runTest {
        agentDao.insert(createAgent(id = "agent-1", name = "Custom", isBuiltIn = false))
        agentDao.insert(createAgent(id = "agent-2", name = "Built-in 1", isBuiltIn = true))
        agentDao.insert(createAgent(id = "agent-3", name = "Built-in 2", isBuiltIn = true))

        val builtIn = agentDao.getBuiltInAgents()
        assertEquals(2, builtIn.size)
        assertEquals(true, builtIn.all { it.isBuiltIn })
    }

    @Test
    fun getAllAgentsFlowUpdatesOnInsert() = runTest {
        agentDao.getAllAgents().test {
            val initial = awaitItem()
            assertEquals(0, initial.size)

            agentDao.insert(createAgent(id = "agent-1", name = "Agent 1"))
            val afterFirst = awaitItem()
            assertEquals(1, afterFirst.size)

            agentDao.insert(createAgent(id = "agent-2", name = "Agent 2"))
            val afterSecond = awaitItem()
            assertEquals(2, afterSecond.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getAgentByIdReturnsNullForNonExistent() = runTest {
        val result = agentDao.getAgentById("nonexistent")
        assertNull(result)
    }
}
