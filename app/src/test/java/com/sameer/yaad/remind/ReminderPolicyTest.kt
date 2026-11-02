package com.sameer.yaad.remind

import com.sameer.yaad.parse.Deadline
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPolicyTest {
    private val friday = LocalDate.of(2026, 11, 6)
    private val longAgo = LocalDateTime.of(2026, 10, 1, 12, 0)

    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 11, day, hour, minute)

    @Test fun `date only deadline fires the day before and that morning`() {
        assertEquals(
            listOf(at(5, 9), at(6, 9)),
            ReminderPolicy.fireTimes(Deadline(friday), longAgo),
        )
    }

    @Test fun `timed deadline fires the same two times`() {
        assertEquals(
            listOf(at(5, 9), at(6, 9)),
            ReminderPolicy.fireTimes(Deadline(friday, LocalTime.of(17, 0)), longAgo),
        )
    }

    @Test fun `deadline before the morning slot fires an hour ahead instead`() {
        assertEquals(
            listOf(at(5, 9), at(6, 7)),
            ReminderPolicy.fireTimes(Deadline(friday, LocalTime.of(8, 0)), longAgo),
        )
        assertEquals(
            listOf(at(5, 9), at(6, 8)),
            ReminderPolicy.fireTimes(Deadline(friday, LocalTime.of(9, 0)), longAgo),
        )
    }

    @Test fun `slots before capture never fire`() {
        assertEquals(
            listOf(at(6, 9)),
            ReminderPolicy.fireTimes(Deadline(friday), at(5, 20)),
        )
    }

    @Test fun `captured too late for both slots still notifies once`() {
        assertEquals(
            listOf(at(6, 18)),
            ReminderPolicy.fireTimes(Deadline(friday), at(6, 10)),
        )
        assertEquals(
            listOf(at(6, 16)),
            ReminderPolicy.fireTimes(Deadline(friday, LocalTime.of(17, 0)), at(6, 10)),
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
        val fired = ReminderPolicy.dueToFire(Deadline(friday), longAgo, null, at(5, 9, 1))
        assertEquals(at(5, 9), fired)
    }

    @Test fun `reminder does not repeat after notifying`() {
        assertNull(ReminderPolicy.dueToFire(Deadline(friday), longAgo, at(5, 9, 1), at(5, 15)))
    }

    @Test fun `phone off through both slots shows one catch-up, the latest`() {
        val fired = ReminderPolicy.dueToFire(Deadline(friday), longAgo, null, at(6, 12))
        assertEquals(at(6, 9), fired)
    }

    @Test fun `next fire looks past now`() {
        assertEquals(at(6, 9), ReminderPolicy.nextFire(Deadline(friday), longAgo, at(5, 9, 1)))
        assertNull(ReminderPolicy.nextFire(Deadline(friday), longAgo, at(6, 10)))
    }

    @Test fun `check-in waits for its time`() {
        assertFalse(ReminderPolicy.checkInDue(friday.minusDays(1), at(6, 9, 59)))
        assertTrue(ReminderPolicy.checkInDue(friday.minusDays(1), at(6, 10)))
    }

    @Test fun `check-in happens once a day`() {
        assertFalse(ReminderPolicy.checkInDue(friday, at(6, 10, 1)))
        assertFalse(ReminderPolicy.checkInDue(friday, at(6, 23)))
    }

    @Test fun `check-in runs late when the phone was off`() {
        assertTrue(ReminderPolicy.checkInDue(friday.minusDays(3), at(6, 15)))
    }

    @Test fun `first check-in needs no earlier one`() {
        assertTrue(ReminderPolicy.checkInDue(null, at(6, 10)))
        assertFalse(ReminderPolicy.checkInDue(null, at(6, 8)))
    }

    @Test fun `clock set back doesn't repeat a check-in`() {
        assertFalse(ReminderPolicy.checkInDue(friday, at(5, 11)))
    }

    @Test fun `next check-in is today until its time has passed`() {
        assertEquals(at(6, 10), ReminderPolicy.nextCheckIn(at(6, 9)))
        assertEquals(at(7, 10), ReminderPolicy.nextCheckIn(at(6, 10)))
        assertEquals(at(7, 10), ReminderPolicy.nextCheckIn(at(6, 15)))
    }
}
