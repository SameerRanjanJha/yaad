package com.sameer.yaad.dedupe

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sameer.yaad.remind.Notifications
import com.sameer.yaad.yaad

/**
 * The step after recording and analysis. It's a job of its own, so a delay here never holds up the
 * next note. It loads no models and only touches the database, so it runs in the main process.
 */
class DuplicateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val removed = applicationContext.yaad.repository.removeDuplicates(System.currentTimeMillis())
        if (removed.isNotEmpty()) Notifications.showDuplicatesRemoved(applicationContext, removed)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "duplicates"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<DuplicateWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            // Appending guarantees one more run after any in progress, so an item saved just as a run finishes isn't missed.
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
