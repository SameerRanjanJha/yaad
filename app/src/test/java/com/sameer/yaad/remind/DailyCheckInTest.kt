package com.sameer.yaad.remind

import com.sameer.yaad.data.ItemEntity
import com.sameer.yaad.data.withDeadline
import com.sameer.yaad.parse.Deadline
import com.sameer.yaad.parse.ItemKind
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyCheckInTest {
    private val today = LocalDate.of(2026, 11, 2)
    private val now: LocalDateTime = today.atTime(10, 0)

    private fun task(id: Long, title: String, due: LocalDate? = null) = ItemEntity(
        id = id,
        noteId = 1,
        kind = ItemKind.TASK,
        title = title,
        deadlineSetAt = 0,
        createdAt = id,
        updatedAt = id,
    ).withDeadline(due?.let { Deadline(it) })

    @Test fun `nothing pending means no check-in`() {
        assertNull(DailyCheckIn.summarize(emptyList(), now))
    }

    @Test fun `one task reads in the singular`() {
        assertEquals("1 task pending", DailyCheckIn.summarize(listOf(task(1, "Call mom")), now)?.title)
    }

    @Test fun `title counts tasks and what's overdue`() {
        val summary = DailyCheckIn.summarize(listOf(task(1, "Pay rent", today.minusDays(3)), task(2, "Call mom")), now)
        assertEquals("2 tasks pending, 1 overdue", summary?.title)
    }

    @Test fun `soonest deadline comes first and undated tasks last`() {
        val summary = DailyCheckIn.summarize(
            listOf(task(1, "Call mom"), task(2, "File taxes", today.plusDays(1)), task(3, "Pay rent", today.minusDays(3))),
            now,
        )
        assertEquals(
            listOf("Pay rent (overdue: Fri 30 Oct)", "File taxes (Tomorrow)", "Call mom"),
            summary?.lines,
        )
    }

    @Test fun `a long list is cut short and says how much is left`() {
        val summary = DailyCheckIn.summarize((1L..9L).map { task(it, "Task $it") }, now)
        assertEquals("9 tasks pending", summary?.title)
        assertEquals(7, summary?.lines?.size)
        assertEquals("and 3 more", summary?.lines?.last())
    }

    @Test fun `six tasks all fit`() {
        val summary = DailyCheckIn.summarize((1L..6L).map { task(it, "Task $it") }, now)
        assertEquals(6, summary?.lines?.size)
        assertEquals("Task 6", summary?.lines?.first())
    }
}
