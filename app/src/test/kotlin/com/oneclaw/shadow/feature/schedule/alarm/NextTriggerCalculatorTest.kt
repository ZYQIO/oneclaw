package com.oneclaw.shadow.feature.schedule.alarm

import com.oneclaw.shadow.core.model.ScheduleType
import com.oneclaw.shadow.core.model.ScheduledTask
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class NextTriggerCalculatorTest {

    private val zone = ZoneId.systemDefault()

    private fun createTask(
        scheduleType: ScheduleType = ScheduleType.DAILY,
        hour: Int = 8,
        minute: Int = 0,
        dayOfWeek: Int? = null,
        dateMillis: Long? = null
    ) = ScheduledTask(
        id = "test-id",
        name = "Test",
        agentId = "agent-1",
        prompt = "Hello",
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
        createdAt = 0,
        updatedAt = 0
    )

    @Test
    fun `daily - returns today if time is in the future`() {
        val now = ZonedDateTime.of(LocalDate.of(2026, 3, 1), LocalTime.of(6, 0), zone)
        val nowMillis = now.toInstant().toEpochMilli()

        val task = createTask(hour = 8, minute = 30)
        val result = NextTriggerCalculator.calculate(task, nowMillis)

        assertNotNull(result)
        val resultTime = Instant.ofEpochMilli(result!!).atZone(zone)
        assertEquals(8, resultTime.hour)
        assertEquals(30, resultTime.minute)
        assertEquals(now.toLocalDate(), resultTime.toLocalDate())
    }

    @Test
    fun `daily - returns tomorrow if time has passed`() {
        val now = ZonedDateTime.of(LocalDate.of(2026, 3, 1), LocalTime.of(10, 0), zone)
        val nowMillis = now.toInstant().toEpochMilli()

        val task = createTask(hour = 8, minute = 0)
        val result = NextTriggerCalculator.calculate(task, nowMillis)

        assertNotNull(result)
        val resultTime = Instant.ofEpochMilli(result!!).atZone(zone)
        assertEquals(8, resultTime.hour)
        assertEquals(0, resultTime.minute)
        assertEquals(now.toLocalDate().plusDays(1), resultTime.toLocalDate())
    }

    @Test
    fun `one-time - returns trigger time if in the future`() {
        val futureDate = LocalDate.of(2026, 4, 15)
        val dateMillis = futureDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val now = ZonedDateTime.of(LocalDate.of(2026, 3, 1), LocalTime.of(10, 0), zone)
        val nowMillis = now.toInstant().toEpochMilli()

        val task = createTask(
            scheduleType = ScheduleType.ONE_TIME,
            hour = 14,
            minute = 30,
            dateMillis = dateMillis
        )
        val result = NextTriggerCalculator.calculate(task, nowMillis)

        assertNotNull(result)
        val resultTime = Instant.ofEpochMilli(result!!).atZone(zone)
        assertEquals(14, resultTime.hour)
        assertEquals(30, resultTime.minute)
        assertEquals(futureDate, resultTime.toLocalDate())
    }

    @Test
    fun `one-time - returns null if time has passed`() {
        val pastDate = LocalDate.of(2026, 1, 1)
        val dateMillis = pastDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val now = ZonedDateTime.of(LocalDate.of(2026, 3, 1), LocalTime.of(10, 0), zone)
        val nowMillis = now.toInstant().toEpochMilli()

        val task = createTask(
            scheduleType = ScheduleType.ONE_TIME,
            hour = 8,
            minute = 0,
            dateMillis = dateMillis
        )
        val result = NextTriggerCalculator.calculate(task, nowMillis)

        assertNull(result)
    }

    @Test
    fun `one-time - returns null if dateMillis is null`() {
        val task = createTask(scheduleType = ScheduleType.ONE_TIME, dateMillis = null)
        val result = NextTriggerCalculator.calculate(task)
        assertNull(result)
    }

    @Test
    fun `weekly - returns today if correct day and time in future`() {
        // Find a Monday
        var date = LocalDate.of(2026, 3, 2) // Monday
        while (date.dayOfWeek != DayOfWeek.MONDAY) {
            date = date.plusDays(1)
        }
        val now = ZonedDateTime.of(date, LocalTime.of(6, 0), zone)
        val nowMillis = now.toInstant().toEpochMilli()

        val task = createTask(
            scheduleType = ScheduleType.WEEKLY,
            hour = 9,
            minute = 0,
            dayOfWeek = 1 // Monday
        )
        val result = NextTriggerCalculator.calculate(task, nowMillis)

        assertNotNull(result)
        val resultTime = Instant.ofEpochMilli(result!!).atZone(zone)
        assertEquals(DayOfWeek.MONDAY, resultTime.dayOfWeek)
        assertEquals(9, resultTime.hour)
        assertEquals(date, resultTime.toLocalDate())
    }

    @Test
    fun `weekly - returns next week if correct day but time passed`() {
        var date = LocalDate.of(2026, 3, 2)
        while (date.dayOfWeek != DayOfWeek.MONDAY) {
            date = date.plusDays(1)
        }
        val now = ZonedDateTime.of(date, LocalTime.of(12, 0), zone)
        val nowMillis = now.toInstant().toEpochMilli()

        val task = createTask(
            scheduleType = ScheduleType.WEEKLY,
            hour = 9,
            minute = 0,
            dayOfWeek = 1 // Monday
        )
        val result = NextTriggerCalculator.calculate(task, nowMillis)

        assertNotNull(result)
        val resultTime = Instant.ofEpochMilli(result!!).atZone(zone)
        assertEquals(DayOfWeek.MONDAY, resultTime.dayOfWeek)
        assertEquals(9, resultTime.hour)
        // Should be next Monday, 7 days later
        assertEquals(date.plusDays(7), resultTime.toLocalDate())
    }

    @Test
    fun `weekly - returns next occurrence of target day`() {
        // Use a Wednesday
        var date = LocalDate.of(2026, 3, 4)
        while (date.dayOfWeek != DayOfWeek.WEDNESDAY) {
            date = date.plusDays(1)
        }
        val now = ZonedDateTime.of(date, LocalTime.of(10, 0), zone)
        val nowMillis = now.toInstant().toEpochMilli()

        val task = createTask(
            scheduleType = ScheduleType.WEEKLY,
            hour = 9,
            minute = 0,
            dayOfWeek = 5 // Friday
        )
        val result = NextTriggerCalculator.calculate(task, nowMillis)

        assertNotNull(result)
        val resultTime = Instant.ofEpochMilli(result!!).atZone(zone)
        assertEquals(DayOfWeek.FRIDAY, resultTime.dayOfWeek)
        assertTrue(resultTime.isAfter(now))
    }

    @Test
    fun `result is always in the future`() {
        val now = System.currentTimeMillis()
        val task = createTask(scheduleType = ScheduleType.DAILY, hour = 12, minute = 0)
        val result = NextTriggerCalculator.calculate(task, now)

        assertNotNull(result)
        assertTrue(result!! > now)
    }
}
