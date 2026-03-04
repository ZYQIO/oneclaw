package com.oneclaw.shadow.feature.memory.curator

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * WorkManager worker that runs daily memory curation.
 * Uses KoinComponent for dependency injection since WorkManager
 * workers cannot use constructor injection with Koin.
 */
class MemoryCurationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val memoryCurator: MemoryCurator by inject()

    companion object {
        const val WORK_NAME = "memory_curation_daily"
        private const val TAG = "MemoryCurationWorker"
        private const val MAX_RETRY_COUNT = 3
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting daily memory curation")
        return try {
            val updated = memoryCurator.curate()
            Log.d(TAG, "Memory curation completed. Updated: $updated")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Memory curation failed", e)
            if (runAttemptCount < MAX_RETRY_COUNT) {
                Result.retry()
            } else {
                Log.w(TAG, "Memory curation failed after $runAttemptCount attempts, giving up")
                Result.failure()
            }
        }
    }
}
