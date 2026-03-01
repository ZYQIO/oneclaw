package com.oneclaw.shadow.feature.schedule.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oneclaw.shadow.core.repository.ScheduledTaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BootCompletedReceiver : BroadcastReceiver(), KoinComponent {

    private val scheduledTaskRepository: ScheduledTaskRepository by inject()
    private val alarmScheduler: AlarmScheduler by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_TIMEZONE_CHANGED
        ) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabledTasks = scheduledTaskRepository.getEnabledTasks()
                alarmScheduler.rescheduleAllEnabled(enabledTasks)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
