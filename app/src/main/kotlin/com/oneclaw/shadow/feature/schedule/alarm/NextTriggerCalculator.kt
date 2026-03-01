package com.oneclaw.shadow.feature.schedule.alarm

import com.oneclaw.shadow.core.model.ScheduleType
import com.oneclaw.shadow.core.model.ScheduledTask
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

object NextTriggerCalculator {

    fun calculate(task: ScheduledTask, now: Long = System.currentTimeMillis()): Long? {
        val zone = ZoneId.systemDefault()
        val nowZoned = Instant.ofEpochMilli(now).atZone(zone)
        val time = LocalTime.of(task.hour, task.minute)

        return when (task.scheduleType) {
            ScheduleType.ONE_TIME -> calculateOneTime(task, time, nowZoned, zone)
            ScheduleType.DAILY -> calculateDaily(time, nowZoned, zone)
            ScheduleType.WEEKLY -> calculateWeekly(task, time, nowZoned, zone)
        }
    }

    private fun calculateOneTime(
        task: ScheduledTask,
        time: LocalTime,
        now: ZonedDateTime,
        zone: ZoneId
    ): Long? {
        val dateMillis = task.dateMillis ?: return null
        val date = Instant.ofEpochMilli(dateMillis).atZone(zone).toLocalDate()
        val trigger = ZonedDateTime.of(date, time, zone)
        return if (trigger.isAfter(now)) trigger.toInstant().toEpochMilli() else null
    }

    private fun calculateDaily(
        time: LocalTime,
        now: ZonedDateTime,
        zone: ZoneId
    ): Long {
        val todayTrigger = ZonedDateTime.of(now.toLocalDate(), time, zone)
        return if (todayTrigger.isAfter(now)) {
            todayTrigger.toInstant().toEpochMilli()
        } else {
            ZonedDateTime.of(now.toLocalDate().plusDays(1), time, zone)
                .toInstant().toEpochMilli()
        }
    }

    private fun calculateWeekly(
        task: ScheduledTask,
        time: LocalTime,
        now: ZonedDateTime,
        zone: ZoneId
    ): Long {
        val targetDay = DayOfWeek.of(task.dayOfWeek ?: 1)
        val todayTrigger = ZonedDateTime.of(now.toLocalDate(), time, zone)

        if (now.dayOfWeek == targetDay && todayTrigger.isAfter(now)) {
            return todayTrigger.toInstant().toEpochMilli()
        }

        val nextDate = now.toLocalDate().with(TemporalAdjusters.next(targetDay))
        return ZonedDateTime.of(nextDate, time, zone).toInstant().toEpochMilli()
    }
}
