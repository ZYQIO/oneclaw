package com.oneclaw.shadow.feature.memory.curator

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Manages the WorkManager schedule for daily memory curation.
 * Stores the user's preferred curation time in SharedPreferences.
 */
class CurationScheduler(
    private val context: Context
) {
    companion object {
        private const val TAG = "CurationScheduler"
        private const val PREFS_NAME = "memory_curation_prefs"
        private const val KEY_HOUR = "curation_hour"
        private const val KEY_MINUTE = "curation_minute"
        const val DEFAULT_HOUR = 3
        const val DEFAULT_MINUTE = 0
    }

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Get the currently configured curation time.
     */
    fun getCurationTime(): Pair<Int, Int> {
        val hour = prefs.getInt(KEY_HOUR, DEFAULT_HOUR)
        val minute = prefs.getInt(KEY_MINUTE, DEFAULT_MINUTE)
        return hour to minute
    }

    /**
     * Set the curation time and reschedule the WorkManager task.
     */
    fun setCurationTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .apply()
        scheduleCuration(hour, minute)
    }

    /**
     * Schedule (or reschedule) the daily curation task.
     * Should be called at app startup and whenever the user changes the time.
     */
    fun scheduleCuration(
        hour: Int = prefs.getInt(KEY_HOUR, DEFAULT_HOUR),
        minute: Int = prefs.getInt(KEY_MINUTE, DEFAULT_MINUTE)
    ) {
        val initialDelay = calculateInitialDelay(hour, minute)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<MemoryCurationWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MemoryCurationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        Log.d(
            TAG,
            "Curation scheduled at %02d:%02d (initial delay: %d min)".format(
                hour, minute, initialDelay / 1000 / 60
            )
        )
    }

    /**
     * Cancel the curation schedule.
     */
    fun cancelCuration() {
        WorkManager.getInstance(context).cancelUniqueWork(MemoryCurationWorker.WORK_NAME)
    }

    /**
     * Calculate the delay in milliseconds from now until the next occurrence
     * of the target time.
     */
    internal fun calculateInitialDelay(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        val targetToday = now.toLocalDate().atTime(LocalTime.of(hour, minute))
        val target = if (now.isBefore(targetToday)) {
            targetToday
        } else {
            targetToday.plusDays(1)
        }
        return Duration.between(now, target).toMillis()
    }
}
