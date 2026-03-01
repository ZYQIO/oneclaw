package com.oneclaw.shadow.feature.schedule.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oneclaw.shadow.core.repository.ScheduledTaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ExactAlarmPermissionStateReceiver : BroadcastReceiver(), KoinComponent {

    private val scheduledTaskRepository: ScheduledTaskRepository by inject()
    private val alarmScheduler: AlarmScheduler by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tasks = scheduledTaskRepository.getAllTasks().first()
                alarmScheduler.rescheduleAllEnabled(tasks)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
