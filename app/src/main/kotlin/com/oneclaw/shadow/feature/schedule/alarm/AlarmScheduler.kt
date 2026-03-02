package com.oneclaw.shadow.feature.schedule.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.oneclaw.shadow.core.model.ScheduledTask

class AlarmScheduler(
    private val context: Context,
    private val exactAlarmHelper: ExactAlarmHelper
) {
    companion object {
        const val EXTRA_TASK_ID = "scheduled_task_id"
        const val ACTION_TRIGGER = "com.oneclaw.shadow.SCHEDULED_TASK_TRIGGER"
    }

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedules an exact alarm for the given task.
     * Returns true if the alarm was registered, false if the exact alarm
     * permission is not granted or nextTriggerAt is null.
     */
    fun scheduleTask(task: ScheduledTask): Boolean {
        val triggerAt = task.nextTriggerAt ?: return false
        if (!exactAlarmHelper.canScheduleExactAlarms()) {
            ExactAlarmEventBus.emitPermissionNeeded()
            return false
        }
        val pendingIntent = createPendingIntent(task.id)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent
        )
        return true
    }

    fun cancelTask(taskId: String) {
        val pendingIntent = createPendingIntent(taskId)
        alarmManager.cancel(pendingIntent)
    }

    fun rescheduleAllEnabled(tasks: List<ScheduledTask>) {
        if (!exactAlarmHelper.canScheduleExactAlarms()) return
        for (task in tasks) {
            if (task.isEnabled && task.nextTriggerAt != null) {
                scheduleTask(task)
            }
        }
    }

    private fun createPendingIntent(taskId: String): PendingIntent {
        val intent = Intent(context, ScheduledTaskReceiver::class.java).apply {
            action = ACTION_TRIGGER
            putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
