package com.sameer.yaad.data

import com.sameer.yaad.parse.Deadline
import com.sameer.yaad.parse.ItemKind
import com.sameer.yaad.parse.ParsedItem
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RereadingTest {
    private val now = 9_000L
    private val friday = Deadline(LocalDate.of(2026, 11, 6))

    private fun note(id: Long, state: NoteState = NoteState.DONE) =
        NoteEntity(id = id, createdAt = 100, audioPath = "/audio/$id.wav", transcript = "words", state = state)

    private fun item(
        id: Long,
        noteId: Long = 1,
        title: String = "Call the bank",
        bucket: Bucket = Bucket.TODO,
        updatedAt: Long = 100,
        duplicateOfId: Long? = null,
    ) = ItemEntity(
        id = id,
        noteId = noteId,
        kind = ItemKind.TASK,
        title = title,
        bucket = bucket,
        deadlineSetAt = 100,
        createdAt = 100,
        updatedAt = updatedAt,
        duplicateCheckedAt = 100,
        duplicateOfId = duplicateOfId,
    )

    private fun queued(notes: List<NoteEntity>, items: List<ItemEntity>, canReread: (NoteEntity) -> Boolean = { true }) =
        Rereading.notesToReread(notes, items, canReread).map { it.id }

    @Test fun `a note nobody touched is read again`() {
        assertEquals(listOf(1L), queued(listOf(note(1)), listOf(item(1), item(2, title = "Buy milk"))))
    }

    @Test fun `a note with anything you finished, started, edited or discarded is left alone`() {
        val notes = listOf(note(1), note(2), note(3), note(4))
        val items = listOf(
            item(1, noteId = 1), item(2, noteId = 1, bucket = Bucket.COMPLETED, updatedAt = 300),
            item(3, noteId = 2, bucket = Bucket.IN_PROGRESS, updatedAt = 300),
            item(4, noteId = 3, updatedAt = 300),
            item(5, noteId = 4, bucket = Bucket.DISCARDED, updatedAt = 300),
        )
        assertEquals(emptyList<Long>(), queued(notes, items))
    }

    @Test fun `repeats the app merged away don't count as touched`() {
        val items = listOf(item(1, updatedAt = 400), item(2, bucket = Bucket.DISCARDED, updatedAt = 400, duplicateOfId = 1))
        assertEquals(listOf(1L), queued(listOf(note(1)), items))
    }

    @Test fun `a note whose item took in a repeat from another note is left alone`() {
        val items = listOf(item(1, noteId = 1, updatedAt = 400), item(2, noteId = 2, bucket = Bucket.DISCARDED, updatedAt = 400, duplicateOfId = 1))
        assertEquals(listOf(2L), queued(listOf(note(1), note(2)), items))
    }

    @Test fun `notes still waiting, empty or without their recording are left alone`() {
        val notes = listOf(note(1, NoteState.PENDING), note(2, NoteState.EMPTY), note(3))
        val items = listOf(item(1, noteId = 1), item(3, noteId = 3))
        assertEquals(emptyList<Long>(), queued(notes, items) { it.id != 3L })
    }

    @Test fun `a new reading moves the old items to Discarded and adds the new ones`() {
        val swap = Rereading.swap(1, listOf(ParsedItem(ItemKind.TASK, "Call the bank", deadline = friday)), listOf(item(1), item(2, title = "The bank closes at five")), now)!!
        assertEquals(listOf(1L, 2L), swap.replaced.map { it.id })
        assertTrue(swap.replaced.all { it.bucket == Bucket.DISCARDED && it.replacedAt == now })
        val added = swap.added.single()
        assertEquals(1L, added.noteId)
        assertEquals(friday, added.deadline)
        assertEquals(now, added.deadlineSetAt)
    }

    @Test fun `the same reading changes nothing`() {
        val swap = Rereading.swap(1, listOf(ParsedItem(ItemKind.TASK, "Call the bank")), listOf(item(1)), now)!!
        assertEquals(emptyList<ItemEntity>(), swap.replaced)
        assertEquals(emptyList<ItemEntity>(), swap.added)
    }

    @Test fun `items the app had already merged away stay as they are`() {
        val items = listOf(item(1, updatedAt = 400), item(2, bucket = Bucket.DISCARDED, updatedAt = 400, duplicateOfId = 1))
        val swap = Rereading.swap(1, listOf(ParsedItem(ItemKind.TASK, "Call the bank about the loan")), items, now)!!
        assertEquals(listOf(1L), swap.replaced.map { it.id })
    }

    @Test fun `an item touched while the note waited keeps the old reading`() {
        assertNull(Rereading.swap(1, listOf(ParsedItem(ItemKind.TASK, "Pay rent")), listOf(item(1, bucket = Bucket.COMPLETED, updatedAt = 500)), now))
    }
}
