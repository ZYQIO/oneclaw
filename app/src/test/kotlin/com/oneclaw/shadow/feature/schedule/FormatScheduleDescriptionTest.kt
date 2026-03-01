package com.oneclaw.shadow.feature.schedule

import com.oneclaw.shadow.core.model.ScheduleType
import com.oneclaw.shadow.core.model.ScheduledTask
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

class FormatScheduleDescriptionTest {

    private fun createTask(
        scheduleType: ScheduleType = ScheduleType.DAILY,
        hour: Int = 8,
        minute: Int = 0,
        dayOfWeek: Int? = null,
        dateMillis: Long? = null
    ) = ScheduledTask(
        id = "task-1",
        name = "Test",
        agentId = "agent-1",
        prompt = "prompt",
        scheduleType = scheduleType,
        hour = hour,
        minute = minute,
        dayOfWeek = dayOfWeek,
        dateMillis = dateMillis,
        isEnabled = true,
        lastExecutionAt = null,
        lastExecutionStatus = null,
        lastExecutionSessionId = null,
        nextTriggerAt = null,
        createdAt = 100L,
        updatedAt = 100L
    )

    @Test
    fun `daily format`() {
        val task = createTask(scheduleType = ScheduleType.DAILY, hour = 8, minute = 5)
        val description = formatScheduleDescription(task)
        assertEquals("Daily at 08:05", description)
    }

    @Test
    fun `one-time format`() {
        val task = createTask(scheduleType = ScheduleType.ONE_TIME, hour = 14, minute = 30)
        val description = formatScheduleDescription(task)
        assertEquals("One-time at 14:30", description)
    }

    @Test
    fun `weekly format`() {
        // DayOfWeek.WEDNESDAY is 3
        val task = createTask(scheduleType = ScheduleType.WEEKLY, hour = 10, minute = 0, dayOfWeek = 3)
        val description = formatScheduleDescription(task)
        val expectedDayName = DayOfWeek.WEDNESDAY.getDisplayName(TextStyle.FULL, Locale.getDefault())
        assertEquals("Every $expectedDayName at 10:00", description)
    }

    @Test
    fun `weekly format falls back to Monday when dayOfWeek is null`() {
        val task = createTask(scheduleType = ScheduleType.WEEKLY, hour = 7, minute = 45, dayOfWeek = null)
        val description = formatScheduleDescription(task)
        assertTrue(description.startsWith("Every Monday at 07:45"))
    }
}
