package com.sameer.yaad.dedupe

import com.sameer.yaad.data.Bucket
import com.sameer.yaad.data.ItemEntity
import com.sameer.yaad.data.deadline
import com.sameer.yaad.data.withDeadline
import com.sameer.yaad.parse.Deadline
import com.sameer.yaad.parse.ItemKind
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuplicateRemoverTest {
    private val now = 5_000L
    private val friday = LocalDate.of(2026, 11, 6)
    private val monday = LocalDate.of(2026, 11, 9)

    private fun item(
        id: Long,
        title: String,
        kind: ItemKind = ItemKind.TASK,
        bucket: Bucket = Bucket.TODO,
        deadline: Deadline? = null,
        detail: String? = null,
        checkedAt: Long? = null,
        lastNotifiedAt: Long? = null,
    ) = ItemEntity(
        id = id,
        noteId = 1,
        kind = kind,
        title = title,
        detail = detail,
        bucket = bucket,
        deadlineSetAt = 100,
        createdAt = 100,
        updatedAt = 100,
        lastNotifiedAt = lastNotifiedAt,
        duplicateCheckedAt = checkedAt,
    ).withDeadline(deadline)

    /** Runs a pass and returns every item as it ends up, by id. */
    private fun settle(vararg items: ItemEntity): Map<Long, ItemEntity> {
        val pass = DuplicateRemover.run(items.toList(), now)
        return items.associateBy { it.id } + pass.changed.associateBy { it.id }
    }

    private fun assertDuplicates(first: String, second: String) {
        val result = settle(item(1, first), item(2, second))
        assertEquals("'$first' and '$second' should be one item", Bucket.DISCARDED, result.getValue(2).bucket)
    }

    private fun assertDifferent(first: String, second: String) {
        val result = settle(item(1, first), item(2, second))
        assertEquals("'$first' and '$second' should stay apart", Bucket.TODO, result.getValue(2).bucket)
    }

    @Test fun `a repeat goes to Discarded and points at the original`() {
        val pass = DuplicateRemover.run(listOf(item(1, "Call mom"), item(2, "Call mom")), now)
        val copy = pass.removed.single()
        assertEquals(2L, copy.id)
        assertEquals(Bucket.DISCARDED, copy.bucket)
        assertEquals(1L, copy.duplicateOfId)
        assertEquals(now, copy.updatedAt)
    }

    @Test fun `different wording of one thing is a duplicate`() {
        assertDuplicates("Call mom", "Call my mother")
        assertDuplicates("Call mom", "Ring mum")
        assertDuplicates("Send the report to Rahul", "Send Rahul the report")
        assertDuplicates("Buy milk", "Get milk")
        assertDuplicates("Pay the bills", "Pay bill")
        assertDuplicates("Call the bank.", "call the Bank")
        assertDuplicates("Meet Rahul", "Meeting with Rahul")
    }

    @Test fun `different things stay on the list`() {
        assertDifferent("Call mom", "Call dad")
        assertDifferent("Call mom", "Email mom")
        assertDifferent("Pay rent", "Pay electricity bill")
        assertDifferent("Call Priyanka", "Call Priyansh")
        assertDifferent("Pay 500 to Rahul", "Pay 600 to Rahul")
        assertDifferent("Turn on the AC", "Turn off the AC")
    }

    @Test fun `action synonyms apply only to the action`() {
        assertDifferent("Buy a phone", "Buy a ring")
    }

    @Test fun `a title that changes the meaning is not a duplicate`() {
        assertDifferent("Call mom", "Don't call mom")
        assertDifferent("Book flight to Delhi", "Book flight from Delhi")
        assertDifferent("Call mom", "Call mom’s doctor")
    }

    @Test fun `a title that only shares a little with a longer one stays`() {
        assertDifferent("Pay rent", "Pay rent agreement fee")
        assertDifferent("Milk", "Buy milk")
    }

    @Test fun `titles with nothing to compare stay`() {
        assertDifferent("To do", "For this")
    }

    @Test fun `the fuller title is kept, on the older item`() {
        val result = settle(item(1, "Call mom"), item(2, "Call mom about the loan"))
        assertEquals("Call mom about the loan", result.getValue(1).title)
        assertEquals(Bucket.DISCARDED, result.getValue(2).bucket)
    }

    @Test fun `the older title stays when the new one adds nothing`() {
        val result = settle(item(1, "Call my mother"), item(2, "Call mom"))
        assertEquals("Call my mother", result.getValue(1).title)
    }

    @Test fun `details from both are kept`() {
        val result = settle(item(1, "Call mom", detail = "About the loan."), item(2, "Call mom", detail = "Ask for the papers."))
        assertEquals("About the loan. Ask for the papers.", result.getValue(1).detail)
    }

    @Test fun `different dates are separate tasks`() {
        val result = settle(item(1, "Pay rent", deadline = Deadline(friday)), item(2, "Pay rent", deadline = Deadline(monday)))
        assertEquals(Bucket.TODO, result.getValue(2).bucket)
    }

    @Test fun `different times on one day are separate tasks`() {
        val result = settle(
            item(1, "Call Rahul", deadline = Deadline(friday, LocalTime.of(10, 0))),
            item(2, "Call Rahul", deadline = Deadline(friday, LocalTime.of(17, 0))),
        )
        assertEquals(Bucket.TODO, result.getValue(2).bucket)
    }

    @Test fun `a deadline on the copy moves to the original and restarts its reminders`() {
        val result = settle(item(1, "Pay rent", lastNotifiedAt = 50), item(2, "Pay rent", deadline = Deadline(friday)))
        val kept = result.getValue(1)
        assertEquals(Deadline(friday), kept.deadline)
        assertEquals(now, kept.deadlineSetAt)
        assertNull(kept.lastNotifiedAt)
    }

    @Test fun `a time on the copy is added to the same date`() {
        val result = settle(item(1, "Pay rent", deadline = Deadline(friday)), item(2, "Pay rent", deadline = Deadline(friday, LocalTime.of(17, 0))))
        assertEquals(Deadline(friday, LocalTime.of(17, 0)), result.getValue(1).deadline)
    }

    @Test fun `an unchanged deadline keeps its reminder history`() {
        val result = settle(item(1, "Pay rent", deadline = Deadline(friday), lastNotifiedAt = 50), item(2, "Pay rent"))
        val kept = result.getValue(1)
        assertEquals(100L, kept.deadlineSetAt)
        assertEquals(50L, kept.lastNotifiedAt)
    }

    @Test fun `an idea said again as a task becomes a task`() {
        val result = settle(item(1, "Add dark mode", kind = ItemKind.IDEA), item(2, "Add dark mode", deadline = Deadline(friday)))
        val kept = result.getValue(1)
        assertEquals(ItemKind.TASK, kept.kind)
        assertEquals(Deadline(friday), kept.deadline)
        assertEquals(Bucket.DISCARDED, result.getValue(2).bucket)
    }

    @Test fun `a task and an idea need identical wording to match`() {
        val result = settle(item(1, "Add dark mode", kind = ItemKind.IDEA), item(2, "Add dark mode to the settings"))
        assertEquals(Bucket.TODO, result.getValue(2).bucket)
    }

    @Test fun `being in progress survives a merge`() {
        val result = settle(item(1, "Call mom"), item(2, "Call mom", bucket = Bucket.IN_PROGRESS))
        assertEquals(Bucket.IN_PROGRESS, result.getValue(1).bucket)
    }

    @Test fun `several repeats all merge into the oldest`() {
        val result = settle(item(1, "Call mom"), item(2, "Call mom"), item(3, "Call my mother"))
        assertEquals(Bucket.TODO, result.getValue(1).bucket)
        assertEquals(1L, result.getValue(2).duplicateOfId)
        assertEquals(1L, result.getValue(3).duplicateOfId)
    }

    @Test fun `two items already checked are left alone, so a restored item stays`() {
        val pass = DuplicateRemover.run(listOf(item(1, "Call mom", checkedAt = 10), item(2, "Call mom", checkedAt = 10)), now)
        assertEquals(emptyList<ItemEntity>(), pass.changed)
    }

    @Test fun `a new repeat still merges into a checked item`() {
        val result = settle(item(1, "Call mom", checkedAt = 10), item(2, "Call mom"))
        assertEquals(Bucket.DISCARDED, result.getValue(2).bucket)
        assertEquals(1L, result.getValue(2).duplicateOfId)
    }

    @Test fun `an item with no match is marked as checked`() {
        val pass = DuplicateRemover.run(listOf(item(1, "Call mom")), now)
        assertEquals(now, pass.changed.single().duplicateCheckedAt)
        assertEquals(emptyList<ItemEntity>(), pass.removed)
    }
}
