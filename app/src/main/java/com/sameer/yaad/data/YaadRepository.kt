package com.sameer.yaad.data

import androidx.room.withTransaction
import com.sameer.yaad.dedupe.DuplicateRemover
import com.sameer.yaad.dedupe.ListTidier
import com.sameer.yaad.parse.Deadline
import com.sameer.yaad.parse.ItemKind
import com.sameer.yaad.parse.ParsedItem
import java.io.File
import kotlinx.coroutines.flow.Flow

class YaadRepository(
    private val db: YaadDatabase,
    private val onItemsChanged: suspend () -> Unit,
) {
    private val notes = db.notes()
    private val items = db.items()

    fun observeItems(kind: ItemKind): Flow<List<ItemEntity>> = items.observeByKind(kind)
    fun observeNotes(): Flow<List<NoteEntity>> = notes.observeAll()
    fun observeProcessingCount(): Flow<Int> = notes.observeProcessingCount()

    suspend fun note(id: Long): NoteEntity? = notes.byId(id)
    suspend fun nextPendingNote(): NoteEntity? = notes.nextPending()
    suspend fun hasUnfinishedNotes(): Boolean = notes.hasUnfinished()

    suspend fun createRecording(audioPath: String, now: Long): Long =
        notes.insert(NoteEntity(createdAt = now, audioPath = audioPath, state = NoteState.RECORDING))

    suspend fun finishRecording(id: Long, durationMs: Long) {
        val note = notes.byId(id) ?: return
        notes.update(note.copy(durationMs = durationMs, state = NoteState.PENDING))
    }

    suspend fun discardRecording(id: Long) {
        notes.byId(id)?.audioPath?.let { File(it).delete() }
        notes.delete(id)
    }

    /** Counts the attempt first, so a note that crashes the model can't retry forever. */
    suspend fun beginTranscribing(note: NoteEntity) {
        notes.update(note.copy(state = NoteState.TRANSCRIBING, attempts = note.attempts + 1))
    }

    suspend fun rememberTranscript(noteId: Long, transcript: String) {
        val note = notes.byId(noteId) ?: return
        notes.update(note.copy(transcript = transcript))
    }

    suspend fun saveTranscription(noteId: Long, transcript: String, parsed: List<ParsedItem>, now: Long) {
        db.withTransaction {
            val note = notes.byId(noteId) ?: return@withTransaction
            // A run the system stopped can still finish after its retry, and must not add the items twice.
            if (note.state == NoteState.DONE) return@withTransaction
            notes.update(note.copy(transcript = transcript, state = NoteState.DONE, error = null))
            items.insertAll(parsed.map { it.toEntity(noteId, now) })
        }
        onItemsChanged()
    }

    suspend fun markEmpty(noteId: Long) {
        val note = notes.byId(noteId) ?: return
        notes.update(note.copy(state = NoteState.EMPTY, error = null))
    }

    suspend fun markFailed(noteId: Long, error: String) {
        val note = notes.byId(noteId) ?: return
        notes.update(note.copy(state = NoteState.FAILED, error = error))
    }

    suspend fun retryNote(noteId: Long) {
        val note = notes.byId(noteId) ?: return
        notes.update(note.copy(state = NoteState.PENDING, attempts = 0, error = null))
    }

    /**
     * Queues recordings cut off by a crash or a killed process. Their audio is on disk. Only ones
     * from before `processStart` qualify: a recording only runs in this process, so those are dead,
     * while a newer one may be in progress right now.
     */
    suspend fun recoverCutOffRecordings(processStart: Long, durationOf: (File) -> Long) {
        for (note in notes.recordingsStartedBefore(processStart)) {
            val file = note.audioPath?.let(::File)
            val duration = file?.takeIf { it.exists() }?.let(durationOf) ?: 0
            if (duration > 0) finishRecording(note.id, duration) else discardRecording(note.id)
        }
    }

    suspend fun requeueInterruptedAnalysis(maxAttempts: Int) {
        for (note in notes.inAnalysis()) {
            if (note.attempts >= maxAttempts) {
                markFailed(note.id, "The speech model stopped unexpectedly")
            } else {
                notes.update(note.copy(state = NoteState.PENDING))
            }
        }
    }

    suspend fun pruneAudioBefore(before: Long) {
        for (note in notes.doneWithAudioBefore(before)) {
            note.audioPath?.let { File(it).delete() }
            notes.update(note.copy(audioPath = null))
        }
    }

    suspend fun moveItem(id: Long, bucket: Bucket, now: Long) {
        val item = items.byId(id) ?: return
        items.update(
            item.copy(
                bucket = bucket,
                updatedAt = now,
                completedAt = if (bucket == Bucket.COMPLETED) now else null,
                duplicateOfId = null,
            ),
        )
        onItemsChanged()
    }

    suspend fun hasItemsToCheckForDuplicates(): Boolean = items.hasUncheckedForDuplicates()

    /** Returns the items it folded into others. */
    suspend fun tidyList(now: Long): List<ItemEntity> {
        val removed = db.withTransaction {
            val pass = ListTidier.run(items.all(), now)
            pass.changed.forEach { items.update(it) }
            pass.removed
        }
        if (removed.isNotEmpty()) onItemsChanged()
        return removed
    }

    /** Returns the copies it took off the list. */
    suspend fun removeDuplicates(now: Long): List<ItemEntity> {
        val removed = db.withTransaction {
            val pass = DuplicateRemover.run(items.open(), now)
            pass.changed.forEach { items.update(it) }
            items.markFinishedAsChecked(now)
            pass.removed
        }
        if (removed.isNotEmpty()) onItemsChanged()
        return removed
    }

    suspend fun saveEdits(id: Long, title: String, detail: String?, kind: ItemKind, bucket: Bucket, deadline: Deadline?, now: Long) {
        val item = items.byId(id) ?: return
        val deadlineChanged = item.deadline != deadline
        val edited = item.withDeadline(deadline).copy(
            title = title,
            detail = detail,
            kind = kind,
            bucket = bucket,
            updatedAt = now,
            completedAt = if (bucket == Bucket.COMPLETED) item.completedAt ?: now else null,
            deadlineSetAt = if (deadlineChanged) now else item.deadlineSetAt,
            lastNotifiedAt = if (deadlineChanged) null else item.lastNotifiedAt,
            duplicateOfId = if (bucket == item.bucket) item.duplicateOfId else null,
        )
        items.update(edited)
        onItemsChanged()
    }

    private fun ParsedItem.toEntity(noteId: Long, now: Long): ItemEntity = ItemEntity(
        noteId = noteId,
        kind = kind,
        title = title,
        detail = detail,
        deadlineSetAt = now,
        createdAt = now,
        updatedAt = now,
    ).withDeadline(deadline)
}
