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
 *
 * Its first run also tidies the items already on the list, since those were sorted before each note was tidied.
 */
class DuplicateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.yaad
        val now = System.currentTimeMillis()
        val tidied = if (app.prefs.listTidied) emptyList() else app.repository.tidyList(now).also { app.prefs.listTidied = true }
        val removed = app.repository.removeDuplicates(now)
        when {
            tidied.isNotEmpty() -> Notifications.showListTidied(applicationContext, tidied + removed)
            removed.isNotEmpty() -> Notifications.showDuplicatesRemoved(applicationContext, removed)
        }
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
