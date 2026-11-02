package com.sameer.yaad.remind

import com.sameer.yaad.parse.Deadline
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * When things notify: a deadline the morning before and on the day itself, and the list of
 * pending tasks once a day.
 *
 * `setAt` is when the deadline was captured or last edited; slots before it never fire.
 */
object ReminderPolicy {
    private val MORNING: LocalTime = LocalTime.of(9, 0)
    private val END_OF_DAY: LocalTime = LocalTime.of(18, 0)

    // After the morning's deadline reminders, so the two don't arrive as one pile.
    private val DAILY_CHECK_IN: LocalTime = LocalTime.of(10, 0)

    fun dueMoment(deadline: Deadline): LocalDateTime =
        deadline.date.atTime(deadline.time ?: END_OF_DAY)

    fun fireTimes(deadline: Deadline, setAt: LocalDateTime): List<LocalDateTime> {
        val due = dueMoment(deadline)
        val hourBefore = deadline.time?.let { due.minusHours(1) }
        val onTheDay = deadline.date.atTime(MORNING).takeIf { it.isBefore(due) } ?: hourBefore
        val standard = listOfNotNull(deadline.date.minusDays(1).atTime(MORNING), onTheDay)
            .filter { it.isAfter(setAt) }

        // Captured too close to the deadline for either slot: still say something once.
        return standard.ifEmpty { listOfNotNull(hourBefore, due).filter { it.isAfter(setAt) }.take(1) }
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

    /** With the phone off at check-in time, it happens once the phone is back on. */
    fun checkInDue(lastCheckIn: LocalDate?, now: LocalDateTime): Boolean =
        !now.toLocalTime().isBefore(DAILY_CHECK_IN) && (lastCheckIn == null || lastCheckIn.isBefore(now.toLocalDate()))

    fun nextCheckIn(now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atTime(DAILY_CHECK_IN)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }
}
