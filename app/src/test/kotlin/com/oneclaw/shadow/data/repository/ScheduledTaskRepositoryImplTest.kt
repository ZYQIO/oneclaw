package com.oneclaw.shadow.data.repository

import com.oneclaw.shadow.core.model.ExecutionStatus
import com.oneclaw.shadow.core.model.ScheduleType
import com.oneclaw.shadow.core.model.ScheduledTask
import com.oneclaw.shadow.data.local.dao.ScheduledTaskDao
import com.oneclaw.shadow.data.local.entity.ScheduledTaskEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ScheduledTaskRepositoryImplTest {

    private lateinit var dao: ScheduledTaskDao
    private lateinit var repository: ScheduledTaskRepositoryImpl

    private val entity = ScheduledTaskEntity(
        id = "task-1",
        name = "Morning Briefing",
        agentId = "agent-1",
        prompt = "Good morning",
        scheduleType = "DAILY",
        hour = 7,
        minute = 0,
        dayOfWeek = null,
        dateMillis = null,
        isEnabled = true,
        lastExecutionAt = null,
        lastExecutionStatus = null,
        lastExecutionSessionId = null,
        nextTriggerAt = 1000L,
        createdAt = 100L,
        updatedAt = 100L
    )

    @BeforeEach
    fun setup() {
        dao = mockk(relaxed = true)
        repository = ScheduledTaskRepositoryImpl(dao)
    }

    @Test
    fun `getAllTasks returns mapped domain models`() = runTest {
        every { dao.getAllTasks() } returns flowOf(listOf(entity))

        val tasks = repository.getAllTasks().first()

        assertEquals(1, tasks.size)
        assertEquals("task-1", tasks[0].id)
        assertEquals("Morning Briefing", tasks[0].name)
        assertEquals(ScheduleType.DAILY, tasks[0].scheduleType)
        assertEquals(7, tasks[0].hour)
    }

    @Test
    fun `getTaskById returns mapped domain model`() = runTest {
        coEvery { dao.getTaskById("task-1") } returns entity

        val task = repository.getTaskById("task-1")

        assertNotNull(task)
        assertEquals("task-1", task!!.id)
        assertEquals("Morning Briefing", task.name)
    }

    @Test
    fun `getTaskById returns null when not found`() = runTest {
        coEvery { dao.getTaskById("nonexistent") } returns null

        val task = repository.getTaskById("nonexistent")

        assertNull(task)
    }

    @Test
    fun `createTask generates id and timestamps`() = runTest {
        val entitySlot = slot<ScheduledTaskEntity>()
        coEvery { dao.insert(capture(entitySlot)) } returns Unit

        val task = ScheduledTask(
            id = "",
            name = "Test",
            agentId = "agent-1",
            prompt = "Hello",
            scheduleType = ScheduleType.DAILY,
            hour = 8,
            minute = 0,
            dayOfWeek = null,
            dateMillis = null,
            isEnabled = true,
            lastExecutionAt = null,
            lastExecutionStatus = null,
            lastExecutionSessionId = null,
            nextTriggerAt = null,
            createdAt = 0,
            updatedAt = 0
        )

        val created = repository.createTask(task)

        assert(created.id.isNotBlank())
        assert(created.createdAt > 0)
        assert(created.updatedAt > 0)
        coVerify { dao.insert(any()) }
    }

    @Test
    fun `deleteTask calls dao delete`() = runTest {
        repository.deleteTask("task-1")
        coVerify { dao.delete("task-1") }
    }

    @Test
    fun `setEnabled calls dao setEnabled`() = runTest {
        repository.setEnabled("task-1", false)
        coVerify { dao.setEnabled("task-1", false, any()) }
    }

    @Test
    fun `getEnabledTasks returns mapped domain models`() = runTest {
        coEvery { dao.getEnabledTasks() } returns listOf(entity)

        val tasks = repository.getEnabledTasks()

        assertEquals(1, tasks.size)
        assertEquals("task-1", tasks[0].id)
        assertEquals(true, tasks[0].isEnabled)
    }

    @Test
    fun `updateExecutionResult calls dao`() = runTest {
        repository.updateExecutionResult(
            id = "task-1",
            status = ExecutionStatus.SUCCESS,
            sessionId = "session-1",
            nextTriggerAt = 2000L,
            isEnabled = true
        )

        coVerify {
            dao.updateExecutionResult(
                id = "task-1",
                executionAt = any(),
                status = "SUCCESS",
                sessionId = "session-1",
                nextTriggerAt = 2000L,
                isEnabled = true,
                updatedAt = any()
            )
        }
    }
}
