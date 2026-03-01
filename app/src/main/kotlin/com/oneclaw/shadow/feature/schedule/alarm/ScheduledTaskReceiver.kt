package com.oneclaw.shadow.feature.schedule.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.oneclaw.shadow.feature.schedule.worker.ScheduledTaskWorker

class ScheduledTaskReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_TRIGGER) return

        val taskId = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_ID) ?: return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ScheduledTaskWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(ScheduledTaskWorker.KEY_TASK_ID to taskId))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "scheduled_task_$taskId",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
}
