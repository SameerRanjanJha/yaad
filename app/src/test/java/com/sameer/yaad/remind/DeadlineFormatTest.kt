package com.sameer.yaad.remind

import com.sameer.yaad.parse.Deadline
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeadlineFormatTest {
    private val today = LocalDate.of(2026, 11, 4)

    @Test fun `labels read naturally`() {
        assertEquals("Today", DeadlineFormat.label(Deadline(today), today))
        assertEquals("Tomorrow", DeadlineFormat.label(Deadline(today.plusDays(1)), today))
        assertEquals("Fri 6 Nov", DeadlineFormat.label(Deadline(today.plusDays(2)), today))
        assertEquals("Tomorrow, 5:00 PM", DeadlineFormat.label(Deadline(today.plusDays(1), LocalTime.of(17, 0)), today))
    }

    @Test fun `reminder headlines count the days`() {
        assertEquals("You have this due today", DeadlineFormat.dueIn(Deadline(today), today))
        assertEquals("You have this due tomorrow", DeadlineFormat.dueIn(Deadline(today.plusDays(1)), today))
        assertEquals("You have this due in 2 days", DeadlineFormat.dueIn(Deadline(today.plusDays(2)), today))
        assertEquals("You have this due today, 5:00 PM", DeadlineFormat.dueIn(Deadline(today, LocalTime.of(17, 0)), today))
    }

    @Test fun `a date-only deadline lasts until the evening`() {
        assertFalse(DeadlineFormat.isOverdue(Deadline(today), LocalDateTime.of(2026, 11, 4, 17, 0)))
        assertTrue(DeadlineFormat.isOverdue(Deadline(today), LocalDateTime.of(2026, 11, 4, 19, 0)))
    }
}
