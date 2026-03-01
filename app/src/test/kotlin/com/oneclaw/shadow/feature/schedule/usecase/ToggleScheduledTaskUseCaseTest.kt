package com.oneclaw.shadow.feature.schedule.usecase

import com.oneclaw.shadow.core.model.ScheduleType
import com.oneclaw.shadow.core.model.ScheduledTask
import com.oneclaw.shadow.core.repository.ScheduledTaskRepository
import com.oneclaw.shadow.feature.schedule.alarm.AlarmScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ToggleScheduledTaskUseCaseTest {

    private lateinit var repository: ScheduledTaskRepository
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var useCase: ToggleScheduledTaskUseCase

    // A daily task always produces a future next trigger time, so it is safe for enable tests.
    private val dailyTask = ScheduledTask(
        id = "task-1",
        name = "Daily Task",
        agentId = "agent-1",
        prompt = "Run daily",
        scheduleType = ScheduleType.DAILY,
        hour = 23,
        minute = 59,
        dayOfWeek = null,
        dateMillis = null,
        isEnabled = false,
        lastExecutionAt = null,
        lastExecutionStatus = null,
        lastExecutionSessionId = null,
        nextTriggerAt = null,
        createdAt = 100L,
        updatedAt = 100L
    )

    // A one-time task with a date far in the past — NextTriggerCalculator returns null.
    private val pastOneTimeTask = ScheduledTask(
        id = "task-2",
        name = "Past One-Time Task",
        agentId = "agent-1",
        prompt = "Run once",
        scheduleType = ScheduleType.ONE_TIME,
        hour = 8,
        minute = 0,
        dayOfWeek = null,
        dateMillis = 1000L, // epoch millisecond far in the past
        isEnabled = false,
        lastExecutionAt = null,
        lastExecutionStatus = null,
        lastExecutionSessionId = null,
        nextTriggerAt = null,
        createdAt = 100L,
        updatedAt = 100L
    )

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        alarmScheduler = mockk(relaxed = true)
        useCase = ToggleScheduledTaskUseCase(repository, alarmScheduler)
    }

    @Test
    fun `enabling calculates next trigger and schedules alarm`() = runTest {
        coEvery { repository.getTaskById("task-1") } returns dailyTask

        useCase("task-1", true)

        coVerify {
            repository.updateTask(
                match { it.isEnabled && it.nextTriggerAt != null }
            )
        }
        verify { alarmScheduler.scheduleTask(any()) }
    }

    @Test
    fun `disabling cancels alarm and sets enabled to false`() = runTest {
        coEvery { repository.getTaskById("task-1") } returns dailyTask.copy(isEnabled = true)

        useCase("task-1", false)

        verify { alarmScheduler.cancelTask("task-1") }
        coVerify { repository.setEnabled("task-1", false) }
    }

    @Test
    fun `does nothing if task not found`() = runTest {
        coEvery { repository.getTaskById("missing-id") } returns null

        useCase("missing-id", true)

        coVerify(exactly = 0) { repository.updateTask(any()) }
        verify(exactly = 0) { alarmScheduler.scheduleTask(any()) }
        verify(exactly = 0) { alarmScheduler.cancelTask(any()) }
    }

    @Test
    fun `enabling does nothing if next trigger time is null (past one-time)`() = runTest {
        coEvery { repository.getTaskById("task-2") } returns pastOneTimeTask

        useCase("task-2", true)

        coVerify(exactly = 0) { repository.updateTask(any()) }
        verify(exactly = 0) { alarmScheduler.scheduleTask(any()) }
    }
}
