package com.sameer.yaad.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.sameer.yaad.parse.ItemKind
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun byId(id: Long): NoteEntity?

    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT COUNT(*) FROM notes WHERE state IN ('PENDING', 'TRANSCRIBING') AND reread = :reread")
    fun observeProcessingCount(reread: Boolean): Flow<Int>

    // A note just recorded goes ahead of old notes being read again.
    @Query("SELECT * FROM notes WHERE state = 'PENDING' ORDER BY reread, createdAt LIMIT 1")
    suspend fun nextPending(): NoteEntity?

    @Query("SELECT * FROM notes")
    suspend fun all(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE state = 'RECORDING' AND createdAt < :before")
    suspend fun recordingsStartedBefore(before: Long): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE state = 'TRANSCRIBING'")
    suspend fun inAnalysis(): List<NoteEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM notes WHERE state IN ('PENDING', 'TRANSCRIBING'))")
    suspend fun hasUnfinished(): Boolean

    @Query("SELECT * FROM notes WHERE state = 'DONE' AND audioPath IS NOT NULL AND createdAt < :before")
    suspend fun doneWithAudioBefore(before: Long): List<NoteEntity>
}

@Dao
interface ItemDao {
    @Insert
    suspend fun insertAll(items: List<ItemEntity>)

    @Update
    suspend fun update(item: ItemEntity)

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun byId(id: Long): ItemEntity?

    @Query("SELECT * FROM items WHERE kind = :kind")
    fun observeByKind(kind: ItemKind): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE bucket IN ('TODO', 'IN_PROGRESS') AND deadlineEpochDay IS NOT NULL")
    suspend fun openWithDeadline(): List<ItemEntity>

    @Query("UPDATE items SET lastNotifiedAt = :at WHERE id = :id")
    suspend fun markNotified(id: Long, at: Long)

    @Query("SELECT * FROM items WHERE kind = 'TASK' AND bucket IN ('TODO', 'IN_PROGRESS')")
    suspend fun openTasks(): List<ItemEntity>

    @Query("SELECT * FROM items WHERE bucket IN ('TODO', 'IN_PROGRESS')")
    suspend fun open(): List<ItemEntity>

    @Query("SELECT * FROM items")
    suspend fun all(): List<ItemEntity>

    @Query("SELECT * FROM items WHERE noteId = :noteId")
    suspend fun byNote(noteId: Long): List<ItemEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM items WHERE duplicateCheckedAt IS NULL)")
    suspend fun hasUncheckedForDuplicates(): Boolean

    // Saying something again after finishing it is new work, so finished items are never compared.
    @Query("UPDATE items SET duplicateCheckedAt = :at WHERE duplicateCheckedAt IS NULL AND bucket IN ('COMPLETED', 'DISCARDED')")
    suspend fun markFinishedAsChecked(at: Long)
}
