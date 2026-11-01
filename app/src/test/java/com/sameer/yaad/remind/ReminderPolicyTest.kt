package com.sameer.yaad.remind

import com.sameer.yaad.parse.Deadline
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderPolicyTest {
    private val friday = LocalDate.of(2026, 11, 6)
    private val longAgo = LocalDateTime.of(2026, 10, 1, 12, 0)

    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 11, day, hour, minute)

    @Test fun `date only deadline fires two days, one day and that morning`() {
        assertEquals(
            listOf(at(4, 9), at(5, 9), at(6, 9)),
            ReminderPolicy.fireTimes(Deadline(friday), longAgo),
        )
    }

    @Test fun `timed deadline adds an hour before`() {
        assertEquals(
            listOf(at(4, 9), at(5, 9), at(6, 9), at(6, 16)),
            ReminderPolicy.fireTimes(Deadline(friday, LocalTime.of(17, 0)), longAgo),
        )
    }

    @Test fun `early morning deadline drops slots after it`() {
        assertEquals(
            listOf(at(4, 9), at(5, 9), at(6, 7)),
            ReminderPolicy.fireTimes(Deadline(friday, LocalTime.of(8, 0)), longAgo),
        )
    }

    @Test fun `slots before capture never fire`() {
        assertEquals(
            listOf(at(6, 9)),
            ReminderPolicy.fireTimes(Deadline(friday), at(5, 20)),
        )
    }

    @Test fun `captured too late for every slot still notifies at the deadline`() {
        assertEquals(
            listOf(at(6, 18)),
            ReminderPolicy.fireTimes(Deadline(friday), at(6, 10)),
        )
        assertEquals(
            listOf(at(6, 17)),
            ReminderPolicy.fireTimes(Deadline(friday, LocalTime.of(17, 0)), at(6, 16, 30)),
        )
    }

    @Test fun `deadline already past has nothing to fire`() {
        assertEquals(emptyList<LocalDateTime>(), ReminderPolicy.fireTimes(Deadline(friday), at(7, 10)))
    }

    @Test fun `reminder fires once it is due`() {
        val fired = ReminderPolicy.dueToFire(Deadline(friday), longAgo, null, at(4, 9, 1))
        assertEquals(at(4, 9), fired)
    }

    @Test fun `reminder does not repeat after notifying`() {
        assertNull(ReminderPolicy.dueToFire(Deadline(friday), longAgo, at(4, 9, 1), at(4, 15)))
    }

    @Test fun `phone off through two slots shows one catch-up, the latest`() {
        val fired = ReminderPolicy.dueToFire(Deadline(friday), longAgo, null, at(5, 12))
        assertEquals(at(5, 9), fired)
    }

    @Test fun `next fire looks past now`() {
        assertEquals(at(5, 9), ReminderPolicy.nextFire(Deadline(friday), longAgo, at(4, 9, 1)))
        assertNull(ReminderPolicy.nextFire(Deadline(friday), longAgo, at(6, 10)))
    }
}
