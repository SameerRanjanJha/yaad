package com.sameer.yaad.remind

import com.sameer.yaad.parse.Deadline
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * When a deadline should notify: two days before, one day before and that morning,
 * plus an hour before when a clock time was given.
 *
 * `setAt` is when the deadline was captured or last edited; slots before it never fire.
 */
object ReminderPolicy {
    private val MORNING: LocalTime = LocalTime.of(9, 0)
    private val END_OF_DAY: LocalTime = LocalTime.of(18, 0)

    fun dueMoment(deadline: Deadline): LocalDateTime =
        deadline.date.atTime(deadline.time ?: END_OF_DAY)

    fun fireTimes(deadline: Deadline, setAt: LocalDateTime): List<LocalDateTime> {
        val due = dueMoment(deadline)
        val standard = listOfNotNull(
            deadline.date.minusDays(2).atTime(MORNING),
            deadline.date.minusDays(1).atTime(MORNING),
            deadline.date.atTime(MORNING),
            deadline.time?.let { due.minusHours(1) },
        ).filter { it.isBefore(due) && it.isAfter(setAt) }.distinct().sorted()

        // Captured too close to the deadline for any standard slot: still say something once.
        return standard.ifEmpty { if (due.isAfter(setAt)) listOf(due) else emptyList() }
    }

    /** The reminder that should show now, if one came due since the item last notified. */
    fun dueToFire(
        deadline: Deadline,
        setAt: LocalDateTime,
        lastNotifiedAt: LocalDateTime?,
        now: LocalDateTime,
    ): LocalDateTime? {
        val since = lastNotifiedAt ?: setAt
        return fireTimes(deadline, setAt).lastOrNull { it.isAfter(since) && !it.isAfter(now) }
    }

    fun nextFire(deadline: Deadline, setAt: LocalDateTime, now: LocalDateTime): LocalDateTime? =
        fireTimes(deadline, setAt).firstOrNull { it.isAfter(now) }
}
