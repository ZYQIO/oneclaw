package com.oneclaw.shadow.feature.schedule.alarm

import android.app.AlarmManager
import android.app.Application
import com.oneclaw.shadow.core.model.ScheduleType
import com.oneclaw.shadow.core.model.ScheduledTask
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Unit tests for AlarmScheduler permission-check behavior added in RFC-037.
 * Uses Robolectric to provide a real Android Context / AlarmManager environment.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AlarmSchedulerPermissionTest {

    private lateinit var exactAlarmHelper: ExactAlarmHelper
    private lateinit var scheduler: AlarmScheduler

    private val task = ScheduledTask(
        id = "task-1",
        name = "Test Task",
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
        nextTriggerAt = System.currentTimeMillis() + 60_000L,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Before
    fun setup() {
        exactAlarmHelper = mockk(relaxed = true)
        val context = RuntimeEnvironment.getApplication()
        scheduler = AlarmScheduler(context, exactAlarmHelper)
    }

    @Test
    fun `scheduleTask returns false when exact alarm permission is denied`() {
        every { exactAlarmHelper.canScheduleExactAlarms() } returns false

        val result = scheduler.scheduleTask(task)

        assertFalse(result)
    }

    @Test
    fun `scheduleTask returns true when permission is granted`() {
        every { exactAlarmHelper.canScheduleExactAlarms() } returns true

        val result = scheduler.scheduleTask(task)

        assertTrue(result)
    }

    @Test
    fun `scheduleTask returns false when nextTriggerAt is null`() {
        every { exactAlarmHelper.canScheduleExactAlarms() } returns true
        val taskWithoutTrigger = task.copy(nextTriggerAt = null)

        val result = scheduler.scheduleTask(taskWithoutTrigger)

        assertFalse(result)
    }

    @Test
    fun `rescheduleAllEnabled does nothing when permission is denied`() {
        every { exactAlarmHelper.canScheduleExactAlarms() } returns false
        val context = RuntimeEnvironment.getApplication()
        val shadowAlarmManager = shadowOf(
            context.getSystemService(android.content.Context.ALARM_SERVICE) as AlarmManager
        )

        scheduler.rescheduleAllEnabled(listOf(task))

        // No alarms should be scheduled
        assertTrue(shadowAlarmManager.scheduledAlarms.isEmpty())
    }

    @Test
    fun `rescheduleAllEnabled schedules enabled tasks when permission is granted`() {
        every { exactAlarmHelper.canScheduleExactAlarms() } returns true
        val context = RuntimeEnvironment.getApplication()
        val shadowAlarmManager = shadowOf(
            context.getSystemService(android.content.Context.ALARM_SERVICE) as AlarmManager
        )

        val tasks = listOf(
            task,
            task.copy(id = "task-2", isEnabled = false),
            task.copy(id = "task-3", isEnabled = true)
        )

        scheduler.rescheduleAllEnabled(tasks)

        // Only 2 enabled tasks should be scheduled
        assertTrue(shadowAlarmManager.scheduledAlarms.size == 2)
    }
}
