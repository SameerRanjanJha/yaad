package com.sameer.yaad.remind

import com.sameer.yaad.parse.Deadline
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object DeadlineFormat {
    private val DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    fun label(deadline: Deadline, today: LocalDate): String {
        val day = when (ChronoUnit.DAYS.between(today, deadline.date)) {
            0L -> "Today"
            1L -> "Tomorrow"
            else -> DATE.format(deadline.date)
        }
        return deadline.time?.let { "$day, ${TIME.format(it)}" } ?: day
    }

    /** The headline of a reminder notification. */
    fun dueIn(deadline: Deadline, today: LocalDate): String {
        val time = deadline.time?.let { ", ${TIME.format(it)}" }.orEmpty()
        return when (val days = ChronoUnit.DAYS.between(today, deadline.date)) {
            0L -> "You have this due today$time"
            1L -> "You have this due tomorrow$time"
            else -> "You have this due in $days days$time"
        }
    }

    fun isOverdue(deadline: Deadline, now: LocalDateTime): Boolean =
        ReminderPolicy.dueMoment(deadline).isBefore(now)
}
