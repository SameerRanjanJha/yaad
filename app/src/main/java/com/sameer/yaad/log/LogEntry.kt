package com.sameer.yaad.log

import com.sameer.yaad.parse.Deadline
import com.sameer.yaad.parse.ItemKind
import com.sameer.yaad.parse.ParsedItem
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object LogEntry {
    private val HEADING = DateTimeFormatter.ofPattern("EEE, d MMM yyyy, h:mm a", Locale.ENGLISH)
    private val DUE_DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)
    private val DUE_TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    fun format(capturedAt: LocalDateTime, transcript: String, items: List<ParsedItem>): String = buildString {
        append("## ").append(HEADING.format(capturedAt)).append("\n\n")
        append("> ").append(transcript).append("\n\n")
        for (item in items) append(line(item)).append('\n')
        append('\n')
    }

    private fun line(item: ParsedItem): String {
        val label = if (item.kind == ItemKind.TASK) "- [ ] Task: " else "- Idea: "
        val detail = item.detail?.let { " ($it)" }.orEmpty()
        val due = item.deadline?.let { " - due ${formatDue(it)}" }.orEmpty()
        return label + item.title + detail + due
    }

    private fun formatDue(deadline: Deadline): String {
        val date = DUE_DATE.format(deadline.date)
        return deadline.time?.let { "$date, ${DUE_TIME.format(it)}" } ?: date
    }
}
