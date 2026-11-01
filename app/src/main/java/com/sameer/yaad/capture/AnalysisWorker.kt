package com.sameer.yaad.capture

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.sameer.yaad.yaad
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

private const val MESSAGE_ANALYSE = 1
private const val MESSAGE_FINISHED = 2

/**
 * Turns recorded notes into tasks and ideas, in its own process (":analysis"). The speech and
 * language models use a lot of memory and run native code, so if one of them crashes or the
 * system kills them, a recording in progress carries on untouched.
 */
class AnalysisService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val requests = Messenger(
        Handler(Looper.getMainLooper()) { request ->
            val caller = request.replyTo
            scope.launch {
                yaad.noteProcessor.drain()
                caller.send(Message.obtain(null, MESSAGE_FINISHED))
            }
            true
        },
    )

    override fun onBind(intent: Intent?): IBinder = requests.binder
}

/**
 * Keeps the queue from the main process and watches over [AnalysisService]. WorkManager holds on to
 * this job, so waiting notes survive reboots and never need the app opened. If the analysis process
 * dies, the job runs again, which picks up the interrupted note.
 */
class AnalysisWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when {
        analyseInOtherProcess() -> Result.success()
        runAttemptCount < MAX_RETRIES -> Result.retry()
        else -> Result.failure()
    }

    private suspend fun analyseInOtherProcess(): Boolean = suspendCancellableCoroutine { result ->
        val connection = object : ServiceConnection {
            private fun finish(succeeded: Boolean) {
                if (!result.isActive) return
                applicationContext.unbindService(this)
                result.resume(succeeded)
            }

            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val replies = Handler(Looper.getMainLooper()) {
                    finish(succeeded = true)
                    true
                }
                val request = Message.obtain(null, MESSAGE_ANALYSE).apply { replyTo = Messenger(replies) }
                Messenger(binder).send(request)
            }

            override fun onServiceDisconnected(name: ComponentName) = finish(succeeded = false)

            override fun onBindingDied(name: ComponentName) = finish(succeeded = false)
        }
        val service = Intent(applicationContext, AnalysisService::class.java)
        if (!applicationContext.bindService(service, connection, Context.BIND_AUTO_CREATE)) {
            result.resume(false)
            return@suspendCancellableCoroutine
        }
        result.invokeOnCancellation { applicationContext.unbindService(connection) }
    }

    companion object {
        private const val WORK_NAME = "analysis"
        private const val MAX_RETRIES = 8

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<AnalysisWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
            // Appending guarantees one more run after any in progress, so a note saved just as a run finishes isn't missed.
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
