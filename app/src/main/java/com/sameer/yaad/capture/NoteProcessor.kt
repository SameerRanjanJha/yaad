package com.sameer.yaad.capture

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.util.Log
import com.sameer.yaad.SpeechMode
import com.sameer.yaad.data.NoteEntity
import com.sameer.yaad.data.YaadRepository
import com.sameer.yaad.data.toLocalDateTime
import com.sameer.yaad.log.LogEntry
import com.sameer.yaad.log.VoiceLog
import com.sameer.yaad.parse.ParsedItem
import com.sameer.yaad.parse.TranscriptCleaner
import com.sameer.yaad.remind.Notifications
import java.io.File
import java.time.Duration
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Takes recorded notes through transcription, sorting, the log document and the buckets. */
class NoteProcessor(
    private val context: Context,
    private val repository: YaadRepository,
    private val voiceLog: VoiceLog,
    private val sorter: NoteSorter,
    private val engineFactory: () -> Transcriber,
    private val speechMode: () -> SpeechMode,
) {
    // The models can't be interrupted, so a stopped run may still be finishing when the next one starts.
    private val oneRunAtATime = Mutex()

    /** Works through the queue. Each model is loaded only for its step, since both use a lot of memory. */
    suspend fun drain() = oneRunAtATime.withLock {
        // Holding the lock means nothing here is mid-analysis, so any note marked that way was cut off.
        repository.requeueInterruptedAnalysis(MAX_ATTEMPTS)
        while (currentCoroutineContext().isActive) {
            val note = repository.nextPendingNote() ?: break
            repository.beginTranscribing(note)
            // Once a note is started it gets finished, even if the system asks this job to stop.
            withContext(NonCancellable) { processSafely(note) }
        }
        repository.pruneAudioBefore(System.currentTimeMillis() - AUDIO_RETENTION.toMillis())
    }

    private suspend fun processSafely(note: NoteEntity) {
        try {
            process(note)
        } catch (error: Exception) {
            Log.e(TAG, "Note ${note.id} failed", error)
            repository.markFailed(note.id, error.message ?: "Transcription failed")
            Notifications.showCaptureFailed(context, note.id)
        }
    }

    private suspend fun process(note: NoteEntity) {
        val transcript = note.transcript ?: transcribe(note)
        if (transcript == null) {
            repository.markEmpty(note.id)
            return
        }
        // Words saved but no items, on a repeat attempt, means the last try ended during sorting.
        if (note.transcript != null && note.attempts > 0 && analysisLastEndedInNativeCrash()) sorter.stopUsingAi()
        val items = sorter.sort(transcript, note.createdAt.toLocalDateTime(), allowAi = note.attempts < MAX_AI_ATTEMPTS)
        publish(note, transcript, items)
    }

    /**
     * Tells a real crash, which would happen on every note, apart from the system reclaiming memory,
     * which is no reason to give up on the model.
     */
    private fun analysisLastEndedInNativeCrash(): Boolean {
        val exits = context.getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(context.packageName, 0, RECENT_EXITS_TO_CHECK)
        val lastExit = exits.firstOrNull { it.processName.endsWith(ANALYSIS_PROCESS_SUFFIX) }
        return lastExit?.reason == ApplicationExitInfo.REASON_CRASH_NATIVE
    }

    /** Saves the words before sorting, so a crash while sorting doesn't mean transcribing all over again. */
    private suspend fun transcribe(note: NoteEntity): String? {
        val samples = note.audioPath?.let(::File)?.takeIf { it.exists() }?.let(WavFile::readSamples) ?: return null
        if (!SpeechGate.hasSpeech(samples)) return null
        val heard = engineFactory().use { it.transcribe(samples, speechMode()) }
        val transcript = TranscriptCleaner.clean(heard).ifEmpty { return null }
        repository.rememberTranscript(note.id, transcript)
        return transcript
    }

    private suspend fun publish(note: NoteEntity, transcript: String, items: List<ParsedItem>) {
        repository.saveTranscription(note.id, transcript, items, System.currentTimeMillis())
        // The buckets are the source of truth, so a log write failure must not fail the note.
        runCatching { voiceLog.append(LogEntry.format(note.createdAt.toLocalDateTime(), transcript, items)) }
            .onFailure { Log.e(TAG, "Could not write the log document", it) }
        Notifications.showCaptureResult(context, note.id, items)
    }

    private companion object {
        const val TAG = "YaadProcessor"
        const val MAX_ATTEMPTS = 4
        const val MAX_AI_ATTEMPTS = 2
        const val ANALYSIS_PROCESS_SUFFIX = ":analysis"
        const val RECENT_EXITS_TO_CHECK = 10
        val AUDIO_RETENTION: Duration = Duration.ofDays(30)
    }
}
