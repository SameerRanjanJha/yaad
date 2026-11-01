package com.sameer.yaad.capture

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.sameer.yaad.remind.Notifications
import com.sameer.yaad.yaad
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Records a note, with the screen locked if need be, and does nothing else. As soon as the audio
 * is safe on disk it queues the note for [AnalysisWorker] and stops.
 */
class CaptureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "yaad:capture")
            .apply { setReferenceCounted(false) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RECORD -> startRecording()
            ACTION_STOP -> CaptureSession.requestStop(StopRequest.SAVE)
            else -> if (!CaptureSession.isRecording) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (CaptureSession.isRecording) return
        wakeLock.acquire(WAKE_LOCK_LIMIT_MS)
        startForeground(Notifications.FOREGROUND_ID, Notifications.recording(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        CaptureSession.begin()
        scope.launch {
            try {
                record()
            } catch (error: Exception) {
                Log.e(TAG, "Recording failed", error)
                CaptureSession.end(CaptureState.Failed(error.message ?: "Recording failed"))
            }
            AnalysisWorker.schedule(this@CaptureService)
            wakeLock.release()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun record() {
        val repository = yaad.repository
        val file = newAudioFile()
        val noteId = repository.createRecording(file.path, System.currentTimeMillis())
        var elapsedMs = 0L

        try {
            PcmSources.open(this).use { source ->
                WavWriter(file).use { wav ->
                    val buffer = ShortArray(CHUNK_SAMPLES)
                    val autoStop = AutoStop()
                    while (CaptureSession.stopRequest == null) {
                        val read = source.read(buffer)
                        check(read >= 0) { "The microphone stopped working" }
                        if (read == 0) continue
                        wav.write(buffer, read)
                        val chunkMs = read * 1000L / SAMPLE_RATE
                        elapsedMs += chunkMs
                        val level = rms(buffer, read)
                        CaptureSession.update(elapsedMs, level)
                        if (autoStop.shouldStop(level, chunkMs)) break
                    }
                }
            }
        } catch (error: Exception) {
            if (elapsedMs < MIN_NOTE_MS) repository.discardRecording(noteId) else repository.finishRecording(noteId, elapsedMs)
            throw error
        }

        if (CaptureSession.stopRequest == StopRequest.CANCEL || elapsedMs < MIN_NOTE_MS) {
            repository.discardRecording(noteId)
            CaptureSession.end(CaptureState.Cancelled)
        } else {
            repository.finishRecording(noteId, elapsedMs)
            CaptureSession.end(CaptureState.Saved)
        }
    }

    private fun newAudioFile(): File {
        val directory = File(filesDir, "audio").apply { mkdirs() }
        return File(directory, "note-${System.currentTimeMillis()}.wav")
    }

    override fun onDestroy() {
        scope.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "YaadCapture"
        private const val CHUNK_SAMPLES = SAMPLE_RATE / 10
        private const val MIN_NOTE_MS = 500L
        private const val WAKE_LOCK_LIMIT_MS = 30 * 60_000L

        private const val ACTION_RECORD = "com.sameer.yaad.RECORD"
        const val ACTION_STOP = "com.sameer.yaad.STOP"

        fun record(context: Context) = ContextCompat.startForegroundService(context, intent(context, ACTION_RECORD))

        fun stop() = CaptureSession.requestStop(StopRequest.SAVE)

        fun cancel() = CaptureSession.requestStop(StopRequest.CANCEL)

        fun intent(context: Context, action: String): Intent =
            Intent(context, CaptureService::class.java).setAction(action)
    }
}
