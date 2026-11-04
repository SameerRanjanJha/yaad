package com.sameer.yaad.data

import com.sameer.yaad.parse.ParsedItem

/**
 * Reads old notes again when the speech model or the sorter improves, so the list gets better too,
 * not only new notes. A note qualifies only while every item from it is as the app left it, so a
 * new reading never undoes anything the user did. Replaced items go to Discarded, where they can be restored.
 */
internal object Rereading {
    class Swap(val replaced: List<ItemEntity>, val added: List<ItemEntity>)

    fun notesToReread(notes: List<NoteEntity>, items: List<ItemEntity>, canReread: (NoteEntity) -> Boolean): List<NoteEntity> {
        val noteOfItem = items.associate { it.id to it.noteId }
        // Replacing an item that a repeat from another note was merged into would lose what that note added.
        val tookInOtherNotes = items.mapNotNull { copy -> noteOfItem[copy.duplicateOfId]?.takeIf { it != copy.noteId } }.toSet()
        val byNote = items.groupBy { it.noteId }
        return notes.filter { note ->
            val own = byNote[note.id].orEmpty()
            note.state == NoteState.DONE && note.transcript != null && own.isNotEmpty() &&
                note.id !in tookInOtherNotes && own.all { it.isLeftByApp(items) } && canReread(note)
        }
    }

    /** Null when an item was touched after the note was queued, which keeps the old reading. */
    fun swap(noteId: Long, fresh: List<ParsedItem>, items: List<ItemEntity>, now: Long): Swap? {
        val own = items.filter { it.noteId == noteId }
        if (!own.all { it.isLeftByApp(items) }) return null
        val open = own.filter { it.bucket == Bucket.TODO }.sortedBy { it.id }
        if (open.map { it.toParsedItem() } == fresh) return Swap(emptyList(), emptyList())
        return Swap(
            replaced = open.map { it.copy(bucket = Bucket.DISCARDED, replacedAt = now, updatedAt = now, duplicateCheckedAt = now) },
            added = fresh.map { it.toEntity(noteId, now) },
        )
    }

    private fun ItemEntity.isLeftByApp(items: List<ItemEntity>) =
        isAsSorted(items) || (bucket == Bucket.DISCARDED && (duplicateOfId != null || replacedAt != null))
}
