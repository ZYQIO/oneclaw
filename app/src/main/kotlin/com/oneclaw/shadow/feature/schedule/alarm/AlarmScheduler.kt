package com.oneclaw.shadow.feature.schedule.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.oneclaw.shadow.core.model.ScheduledTask

class AlarmScheduler(
    private val context: Context
) {
    companion object {
        const val EXTRA_TASK_ID = "scheduled_task_id"
        const val ACTION_TRIGGER = "com.oneclaw.shadow.SCHEDULED_TASK_TRIGGER"
    }

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleTask(task: ScheduledTask) {
        val triggerAt = task.nextTriggerAt ?: return
        val pendingIntent = createPendingIntent(task.id)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent
        )
    }

    fun cancelTask(taskId: String) {
        val pendingIntent = createPendingIntent(taskId)
        alarmManager.cancel(pendingIntent)
    }

    fun rescheduleAllEnabled(tasks: List<ScheduledTask>) {
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
