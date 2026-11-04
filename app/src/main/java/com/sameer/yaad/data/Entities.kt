package com.sameer.yaad.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sameer.yaad.parse.Deadline
import com.sameer.yaad.parse.ItemKind
import com.sameer.yaad.parse.ParsedItem
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

enum class Bucket(val label: String) {
    TODO("To do"),
    IN_PROGRESS("In progress"),
    COMPLETED("Completed"),
    DISCARDED("Discarded"),
}

enum class NoteState { RECORDING, PENDING, TRANSCRIBING, DONE, EMPTY, FAILED }

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val audioPath: String?,
    val durationMs: Long = 0,
    val transcript: String? = null,
    val state: NoteState,
    val attempts: Int = 0,
    val error: String? = null,
    /** Queued to be read again by a better speech model or sorter. Its items stay until the new reading replaces them. */
    @ColumnInfo(defaultValue = "0") val reread: Boolean = false,
)

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId"), Index("kind", "bucket")],
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val kind: ItemKind,
    val title: String,
    val detail: String? = null,
    val bucket: Bucket = Bucket.TODO,
    // Stored as wall-clock values so "Friday 5 PM" stays that even if the phone changes time zone.
    val deadlineEpochDay: Long? = null,
    val deadlineMinuteOfDay: Int? = null,
    val deadlineSetAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    val lastNotifiedAt: Long? = null,
    val duplicateCheckedAt: Long? = null,
    val duplicateOfId: Long? = null,
    /** When a new reading of its note replaced it, which also moved it to Discarded. */
    val replacedAt: Long? = null,
)

val ItemEntity.deadline: Deadline?
    get() = deadlineEpochDay?.let { day ->
        Deadline(LocalDate.ofEpochDay(day), deadlineMinuteOfDay?.let { LocalTime.ofSecondOfDay(it * 60L) })
    }

fun ItemEntity.withDeadline(deadline: Deadline?): ItemEntity = copy(
    deadlineEpochDay = deadline?.date?.toEpochDay(),
    deadlineMinuteOfDay = deadline?.time?.let { it.hour * 60 + it.minute },
)

fun ItemEntity.toParsedItem() = ParsedItem(kind, title, detail, deadline)

fun ParsedItem.toEntity(noteId: Long, now: Long): ItemEntity = ItemEntity(
    noteId = noteId,
    kind = kind,
    title = title,
    detail = detail,
    deadlineSetAt = now,
    createdAt = now,
    updatedAt = now,
).withDeadline(deadline)

/**
 * In To do and untouched since it was sorted, apart from the app merging a repeat into it, which
 * leaves the repeat with the same update time.
 */
fun ItemEntity.isAsSorted(items: List<ItemEntity>): Boolean =
    bucket == Bucket.TODO && (updatedAt == createdAt || items.any { it.duplicateOfId == id && it.updatedAt == updatedAt })

fun Long.toLocalDateTime(): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

fun LocalDateTime.toEpochMillis(): Long =
    atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
