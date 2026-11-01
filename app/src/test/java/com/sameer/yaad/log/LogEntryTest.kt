package com.sameer.yaad.log

import com.sameer.yaad.parse.Deadline
import com.sameer.yaad.parse.ItemKind
import com.sameer.yaad.parse.ParsedItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class LogEntryTest {
    @Test fun `entry keeps the words and lists what came of them`() {
        val entry = LogEntry.format(
            LocalDateTime.of(2026, 11, 4, 14, 32),
            "Call Ravi on Friday at 5 pm. We could add a dark mode.",
            listOf(
                ParsedItem(ItemKind.TASK, "Call Ravi", deadline = Deadline(LocalDate.of(2026, 11, 6), LocalTime.of(17, 0))),
                ParsedItem(ItemKind.IDEA, "We could add a dark mode", detail = "Good for night use"),
                ParsedItem(ItemKind.TASK, "Book a cab"),
            ),
        )
        assertEquals(
            """
            ## Wed, 4 Nov 2026, 2:32 PM

            > Call Ravi on Friday at 5 pm. We could add a dark mode.

            - [ ] Task: Call Ravi - due Fri 6 Nov 2026, 5:00 PM
            - Idea: We could add a dark mode (Good for night use)
            - [ ] Task: Book a cab


            """.trimIndent(),
            entry,
        )
    }
}
