package com.oneclaw.shadow.feature.schedule.usecase

import com.oneclaw.shadow.core.model.ScheduleType
import com.oneclaw.shadow.core.model.ScheduledTask
import com.oneclaw.shadow.core.repository.ScheduledTaskRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.feature.schedule.alarm.AlarmScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CreateScheduledTaskUseCaseTest {

    private lateinit var repository: ScheduledTaskRepository
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var useCase: CreateScheduledTaskUseCase

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        alarmScheduler = mockk(relaxed = true)
        useCase = CreateScheduledTaskUseCase(repository, alarmScheduler)
    }

    private fun createTask(
        name: String = "Test Task",
        prompt: String = "Hello world",
        scheduleType: ScheduleType = ScheduleType.DAILY,
        hour: Int = 8,
        minute: Int = 0
    ) = ScheduledTask(
        id = "",
        name = name,
        agentId = "agent-1",
        prompt = prompt,
        scheduleType = scheduleType,
        hour = hour,
        minute = minute,
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
    fun `creates task and schedules alarm on success`() = runTest {
        val task = createTask()
        coEvery { repository.createTask(any()) } answers {
            firstArg<ScheduledTask>().copy(id = "created-id")
        }

        val result = useCase(task)

        assertTrue(result is AppResult.Success)
        coVerify { repository.createTask(any()) }
        verify { alarmScheduler.scheduleTask(any()) }
    }

    @Test
    fun `returns error for past one-time date`() = runTest {
        val task = createTask(scheduleType = ScheduleType.ONE_TIME).copy(
            dateMillis = 1000L // far in the past
        )

        val result = useCase(task)

        assertTrue(result is AppResult.Error)
        assertTrue((result as AppResult.Error).message.contains("past"))
    }
}
