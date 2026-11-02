package com.sameer.yaad.dedupe

import com.sameer.yaad.data.Bucket
import com.sameer.yaad.data.ItemEntity
import com.sameer.yaad.data.deadline
import com.sameer.yaad.data.withDeadline
import com.sameer.yaad.parse.Deadline
import com.sameer.yaad.parse.ItemKind
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListTidierTest {
    private val now = 5_000L
    private val friday = Deadline(LocalDate.of(2026, 11, 6))

    private fun item(
        id: Long,
        title: String,
        noteId: Long = 1,
        kind: ItemKind = ItemKind.TASK,
        bucket: Bucket = Bucket.TODO,
        deadline: Deadline? = null,
        updatedAt: Long = 100,
        lastNotifiedAt: Long? = null,
        duplicateOfId: Long? = null,
    ) = ItemEntity(
        id = id,
        noteId = noteId,
        kind = kind,
        title = title,
        bucket = bucket,
        deadlineSetAt = 100,
        createdAt = 100,
        updatedAt = updatedAt,
        lastNotifiedAt = lastNotifiedAt,
        duplicateCheckedAt = 100,
        duplicateOfId = duplicateOfId,
    ).withDeadline(deadline)

    /** Runs a pass and returns every item as it ends up, by id. */
    private fun settle(vararg items: ItemEntity): Map<Long, ItemEntity> {
        val pass = ListTidier.run(items.toList(), now)
        return items.associateBy { it.id } + pass.changed.associateBy { it.id }
    }

    @Test fun `a sentence about a task moves into its detail`() {
        val result = settle(item(1, "Get the AC serviced"), item(2, "It is not cooling properly", kind = ItemKind.IDEA))
        val kept = result.getValue(1)
        assertEquals("It is not cooling properly", kept.detail)
        assertEquals(Bucket.TODO, kept.bucket)
        assertEquals(now, kept.updatedAt)
        val folded = result.getValue(2)
        assertEquals(Bucket.DISCARDED, folded.bucket)
        assertEquals(1L, folded.duplicateOfId)
        assertEquals(now, folded.duplicateCheckedAt)
    }

    @Test fun `repeats in one note become one item`() {
        val pass = ListTidier.run(listOf(item(1, "Call the bank"), item(2, "Call the bank"), item(3, "Call it before five")), now)
        assertEquals(listOf(2L, 3L), pass.removed.map { it.id })
        assertEquals(listOf(1L, 1L), pass.removed.map { it.duplicateOfId })
    }

    @Test fun `the kept item is checked for duplicates again`() {
        val result = settle(item(1, "Pay the bill"), item(2, "Pay the bill for the flat"))
        assertEquals("Pay the bill for the flat", result.getValue(1).title)
        assertNull(result.getValue(1).duplicateCheckedAt)
    }

    @Test fun `a date that moves onto a task restarts its reminders`() {
        val result = settle(item(1, "Pay the electricity bill", lastNotifiedAt = 50), item(2, "The electricity bill is", deadline = friday))
        val kept = result.getValue(1)
        assertEquals(friday, kept.deadline)
        assertEquals(now, kept.deadlineSetAt)
        assertNull(kept.lastNotifiedAt)
    }

    @Test fun `items from different notes are not tidied together`() {
        val pass = ListTidier.run(listOf(item(1, "Get the AC serviced", noteId = 1), item(2, "It is not cooling properly", noteId = 2)), now)
        assertEquals(emptyList<ItemEntity>(), pass.changed)
    }

    @Test fun `an item you edited is left alone`() {
        val pass = ListTidier.run(listOf(item(1, "Call the bank", updatedAt = 300), item(2, "Call the bank")), now)
        assertEquals(emptyList<ItemEntity>(), pass.changed)
    }

    @Test fun `started and finished items are left alone`() {
        val pass = ListTidier.run(
            listOf(
                item(1, "Get the AC serviced", bucket = Bucket.IN_PROGRESS),
                item(2, "It is not cooling properly"),
                item(3, "Call the bank", bucket = Bucket.COMPLETED),
                item(4, "Call the bank"),
            ),
            now,
        )
        assertEquals(emptyList<ItemEntity>(), pass.changed)
    }

    @Test fun `an item duplicate removal merged into is still tidied`() {
        val result = settle(
            item(1, "Get the AC serviced", updatedAt = 400),
            item(2, "It is not cooling properly"),
            item(3, "Get the AC serviced", noteId = 2, bucket = Bucket.DISCARDED, updatedAt = 400, duplicateOfId = 1),
        )
        assertEquals("It is not cooling properly", result.getValue(1).detail)
        assertEquals(Bucket.DISCARDED, result.getValue(2).bucket)
    }

    @Test fun `a restored copy means you touched the item it was merged into`() {
        val pass = ListTidier.run(
            listOf(item(1, "Get the AC serviced", updatedAt = 400), item(2, "It is not cooling properly"), item(3, "Get the AC serviced", noteId = 2, updatedAt = 600)),
            now,
        )
        assertEquals(emptyList<ItemEntity>(), pass.changed)
    }

    @Test fun `a tidy note is left as it is`() {
        val pass = ListTidier.run(listOf(item(1, "Call the bank", deadline = friday), item(2, "Add a dark mode", kind = ItemKind.IDEA)), now)
        assertEquals(emptyList<ItemEntity>(), pass.changed)
    }

    @Test fun `running it twice changes nothing more`() {
        val first = settle(item(1, "Call the bank"), item(2, "Call the bank"), item(3, "The bank closes at five", kind = ItemKind.IDEA))
        val second = ListTidier.run(first.values.toList(), now + 1)
        assertEquals(emptyList<ItemEntity>(), second.changed)
    }
}
