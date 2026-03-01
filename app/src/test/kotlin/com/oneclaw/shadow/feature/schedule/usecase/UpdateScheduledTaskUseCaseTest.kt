package com.oneclaw.shadow.feature.schedule.usecase

import com.oneclaw.shadow.core.model.ScheduleType
import com.oneclaw.shadow.core.model.ScheduledTask
import com.oneclaw.shadow.core.repository.ScheduledTaskRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.feature.schedule.alarm.AlarmScheduler
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UpdateScheduledTaskUseCaseTest {

    private lateinit var repository: ScheduledTaskRepository
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var useCase: UpdateScheduledTaskUseCase

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        alarmScheduler = mockk(relaxed = true)
        useCase = UpdateScheduledTaskUseCase(repository, alarmScheduler)
    }

    private fun createTask(
        id: String = "task-1",
        name: String = "Test Task",
        prompt: String = "Hello world",
        scheduleType: ScheduleType = ScheduleType.DAILY,
        hour: Int = 8,
        minute: Int = 0,
        isEnabled: Boolean = true
    ) = ScheduledTask(
        id = id,
        name = name,
        agentId = "agent-1",
        prompt = prompt,
        scheduleType = scheduleType,
        hour = hour,
        minute = minute,
        dayOfWeek = null,
        dateMillis = null,
        isEnabled = isEnabled,
        lastExecutionAt = null,
        lastExecutionStatus = null,
        lastExecutionSessionId = null,
        nextTriggerAt = null,
        createdAt = 100L,
        updatedAt = 100L
    )

    @Test
    fun `returns error when name is blank`() = runTest {
        val result = useCase(createTask(name = ""))
        assertTrue(result is AppResult.Error)
        assertTrue((result as AppResult.Error).message.contains("name"))
    }

    @Test
    fun `returns error when prompt is blank`() = runTest {
        val result = useCase(createTask(prompt = ""))
        assertTrue(result is AppResult.Error)
        assertTrue((result as AppResult.Error).message.contains("Prompt"))
    }

    @Test
    fun `cancels old alarm, updates task, schedules new alarm when enabled`() = runTest {
        val task = createTask(isEnabled = true)

        val result = useCase(task)

        assertTrue(result is AppResult.Success)
        verify { alarmScheduler.cancelTask("task-1") }
        coVerify { repository.updateTask(any()) }
        verify { alarmScheduler.scheduleTask(any()) }
    }

    @Test
    fun `cancels old alarm, updates task without scheduling when disabled`() = runTest {
        val task = createTask(isEnabled = false)

        val result = useCase(task)

        assertTrue(result is AppResult.Success)
        verify { alarmScheduler.cancelTask("task-1") }
        coVerify { repository.updateTask(any()) }
        verify(exactly = 0) { alarmScheduler.scheduleTask(any()) }
    }

    @Test
    fun `sets nextTriggerAt to null when disabled`() = runTest {
        val task = createTask(isEnabled = false)

        useCase(task)

        coVerify {
            repository.updateTask(
                match { it.nextTriggerAt == null }
            )
        }
    }
}
