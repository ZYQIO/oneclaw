package com.oneclaw.shadow.data.git

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class GitGcWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val appGitRepository: AppGitRepository by inject()

    override suspend fun doWork(): Result {
        appGitRepository.gc()
        appGitRepository.commit("gc: repository maintenance")
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "git_gc"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<GitGcWorker>(30, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
