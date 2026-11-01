package com.sameer.yaad.parse

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

internal data class Extraction(val deadline: Deadline?, val remainder: String)

/**
 * Finds the first spoken date and time in a clause and cuts them out of the text.
 *
 * Ambiguous phrases resolve to the earlier date, because a reminder that comes early
 * costs a glance while one that comes late costs the deadline.
 */
internal class DeadlineExtractor(private val now: LocalDateTime) {
    private val today: LocalDate = now.toLocalDate()

    fun extract(text: String): Extraction {
        if (RECURRING.containsMatchIn(text)) return Extraction(null, text)

        val dated = findDate(text)
        val afterDate = dated?.remainder ?: text
        val timed = findTime(afterDate)
        val remainder = tidy(timed?.remainder ?: afterDate)

        val time = dated?.time ?: timed?.time
        val date = dated?.date ?: time?.let(::nextOccurrenceOf)
        return Extraction(date?.let { Deadline(it, time) }, remainder)
    }

    private data class DateHit(val date: LocalDate, val time: LocalTime?, val remainder: String)
    private data class TimeHit(val time: LocalTime, val remainder: String)

    private class Rule(pattern: String, val resolve: DeadlineExtractor.(MatchResult) -> Pair<LocalDate, LocalTime?>?) {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
    }

    private fun findDate(text: String): DateHit? {
        for (rule in DATE_RULES) {
            for (match in rule.regex.findAll(text)) {
                val (date, time) = rule.resolve(this, match) ?: continue
                return DateHit(date, time, text.removeRange(match.range))
            }
        }
        return null
    }

    private fun findTime(text: String): TimeHit? {
        for ((regex, resolve) in TIME_RULES) {
            val match = regex.find(text) ?: continue
            val time = resolve(match) ?: continue
            return TimeHit(time, text.removeRange(match.range))
        }
        return null
    }

    private fun nextOccurrenceOf(time: LocalTime): LocalDate =
        if (time.isAfter(now.toLocalTime())) today else today.plusDays(1)

    private fun comingWeekday(day: DayOfWeek): LocalDate = today.with(TemporalAdjusters.next(day))

    private fun thisWeekday(day: DayOfWeek): LocalDate = today.with(TemporalAdjusters.nextOrSame(day))

    /** "Next Friday" skips a Friday in the current Monday-to-Sunday week but not one in the next. */
    private fun nextWeekday(day: DayOfWeek): LocalDate {
        val coming = comingWeekday(day)
        val sundayThisWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return if (coming.isAfter(sundayThisWeek)) coming else coming.plusWeeks(1)
    }

    private fun endOfThisWeek(): LocalDate {
        val friday = thisWeekday(DayOfWeek.FRIDAY)
        val sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return if (friday.isAfter(sunday)) sunday else friday
    }

    private fun mondayNextWeek(): LocalDate = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))

    private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    private fun nextDayOfMonth(day: Int): LocalDate? {
        if (day !in 1..31) return null
        val thisMonth = dateOrNull(today.year, today.monthValue, day)
        if (thisMonth != null && !thisMonth.isBefore(today)) return thisMonth
        val next = today.plusMonths(1)
        return dateOrNull(next.year, next.monthValue, day)
    }

    private fun resolveMonthDay(month: Int, day: Int, explicitYear: Int?): LocalDate? {
        val candidate = dateOrNull(explicitYear ?: today.year, month, day) ?: return null
        return if (explicitYear == null && candidate.isBefore(today)) candidate.plusYears(1) else candidate
    }

    private fun resolveBareMonth(month: Int): LocalDate {
        val candidate = LocalDate.of(today.year, month, 1)
        return when {
            month == today.monthValue -> today.with(TemporalAdjusters.lastDayOfMonth())
            candidate.isBefore(today) -> candidate.plusYears(1)
            else -> candidate
        }
    }

    private fun resolveRelative(amount: Long, unit: String): Pair<LocalDate, LocalTime?> {
        val lower = unit.lowercase()
        return when {
            lower.startsWith("minute") -> now.plusMinutes(amount).let { it.toLocalDate() to clockTime(it) }
            lower.startsWith("hour") -> now.plusHours(amount).let { it.toLocalDate() to clockTime(it) }
            lower.startsWith("day") -> today.plusDays(amount) to null
            lower.startsWith("week") -> today.plusWeeks(amount) to null
            else -> today.plusMonths(amount) to null
        }
    }

    private fun clockTime(at: LocalDateTime): LocalTime = at.toLocalTime().truncatedTo(ChronoUnit.MINUTES)

    private companion object {
        const val PART_OF_DAY = "morning|afternoon|evening|night"
        const val WEEKDAY = "monday|tuesday|wednesday|thursday|friday|saturday|sunday"
        const val MONTH_FULL = "january|february|march|april|may|june|july|august|september|october|november|december"
        const val MONTH = "$MONTH_FULL|jan|feb|mar|apr|jun|jul|aug|sept|sep|oct|nov|dec"
        const val BARE_MONTH = "january|february|march|april|june|july|august|september|october|november|december"
        const val ORDINAL_WORD = "thirty[- ]first|thirtieth|twenty[- ]ninth|twenty[- ]eighth|twenty[- ]seventh|" +
            "twenty[- ]sixth|twenty[- ]fifth|twenty[- ]fourth|twenty[- ]third|twenty[- ]second|twenty[- ]first|" +
            "twentieth|nineteenth|eighteenth|seventeenth|sixteenth|fifteenth|fourteenth|thirteenth|twelfth|" +
            "eleventh|tenth|ninth|eighth|seventh|sixth|fifth|fourth|third|second|first"
        const val DAY_NUMBER = """\d{1,2}(?:st|nd|rd|th)?|$ORDINAL_WORD"""
        const val AMOUNT = """\d{1,3}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|a couple of|a couple|a few|an?"""
        const val HOUR_WORD = "one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve"
        const val LEAD = """(?:(?:latest\s+|at the latest\s+)?by|no later than|before|on|due(?:\s+(?:on|by))?|until|till|for)\s+"""
        const val NOT_PAST_OR_RECURRING = """(?<!last\s)(?<!every\s)(?<!each\s)(?<!since\s)(?<!from\s)"""
        const val AFTER_START = """(?<![\w'])"""

        val RECURRING = Regex(
            """\b(?:every|each)\s+(?:other\s+)?(?:day|week|month|year|morning|evening|night|$WEEKDAY)\b|\b(?:daily|weekly|monthly)\b""",
            RegexOption.IGNORE_CASE,
        )

        val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        val ORDINAL_WORDS = listOf(
            "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth",
            "eleventh", "twelfth", "thirteenth", "fourteenth", "fifteenth", "sixteenth", "seventeenth",
            "eighteenth", "nineteenth", "twentieth", "twenty first", "twenty second", "twenty third",
            "twenty fourth", "twenty fifth", "twenty sixth", "twenty seventh", "twenty eighth",
            "twenty ninth", "thirtieth", "thirty first",
        )
        val CARDINALS = listOf(
            "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
        )

        fun monthNumber(name: String): Int = MONTHS.indexOf(name.lowercase().take(3)) + 1

        fun weekday(name: String): DayOfWeek = DayOfWeek.valueOf(name.uppercase())

        fun dayNumber(spoken: String): Int {
            val lower = spoken.lowercase().replace('-', ' ')
            val word = ORDINAL_WORDS.indexOf(lower)
            return if (word >= 0) word + 1 else lower.takeWhile(Char::isDigit).toIntOrNull() ?: 0
        }

        fun amount(spoken: String): Long {
            val lower = spoken.lowercase()
            return when {
                lower == "a" || lower == "an" -> 1
                lower.startsWith("a couple") -> 2
                lower == "a few" -> 3
                else -> lower.toLongOrNull() ?: (CARDINALS.indexOf(lower) + 1L)
            }
        }

        fun hourNumber(spoken: String): Int? {
            val lower = spoken.lowercase()
            return lower.toIntOrNull() ?: CARDINALS.indexOf(lower).takeIf { it >= 0 }?.plus(1)
        }

        fun partOfDayTime(part: String?): LocalTime? = when (part?.lowercase()) {
            "morning" -> LocalTime.of(9, 0)
            "afternoon" -> LocalTime.of(14, 0)
            "evening" -> LocalTime.of(18, 0)
            "night" -> LocalTime.of(21, 0)
            else -> null
        }

        fun fullYear(spoken: String?): Int? {
            val year = spoken?.toIntOrNull() ?: return null
            return if (year < 100) 2000 + year else year
        }

        /** Without am/pm, 1 to 7 is read as afternoon and 8 to 11 as morning, as people usually mean it. */
        fun guessedHour(hour: Int): Int = if (hour in 1..7) hour + 12 else hour

        fun clock(hour: Int, minute: Int, meridiem: String?): LocalTime? {
            val adjusted = when (meridiem?.lowercase()) {
                "am" -> if (hour == 12) 0 else hour
                "pm" -> if (hour == 12) 12 else hour + 12
                else -> guessedHour(hour)
            }
            return if (adjusted in 0..23 && minute in 0..59) LocalTime.of(adjusted, minute) else null
        }

        val DATE_RULES = listOf(
            Rule("""$AFTER_START(?:$LEAD)?(?:the\s+)?day after tomorrow\b|$AFTER_START(?:$LEAD)?parso\b""") { today.plusDays(2) to null },
            Rule("""$AFTER_START(?:$LEAD)?(?:tomorrow|kal)(?:\s+($PART_OF_DAY))?\b""") { m ->
                today.plusDays(1) to partOfDayTime(m.groupValues[1])
            },
            Rule("""$AFTER_START(?:$LEAD)?tonight\b""") { today to LocalTime.of(20, 0) },
            Rule("""$AFTER_START(?:$LEAD)?this\s+(morning|afternoon|evening)\b""") { m -> today to partOfDayTime(m.groupValues[1]) },
            Rule("""$AFTER_START(?:$LEAD)?(?:the\s+)?(?:end of (?:the\s+)?day|eod)\b""") { today to LocalTime.of(18, 0) },
            Rule("""$AFTER_START(?:$LEAD)?(?:today|aaj)\b""") { today to null },

            Rule("""$AFTER_START(?:in|within|after)\s+half an hour\b""") { resolveRelative(30, "minute") },
            Rule("""$AFTER_START(?:in|within|after)\s+($AMOUNT)\s+(minute|hour|day|week|month)s?\b""") { m ->
                resolveRelative(amount(m.groupValues[1]), m.groupValues[2])
            },
            Rule("""$AFTER_START($AMOUNT)\s+(day|week|month)s?\s+from\s+(?:now|today)\b""") { m ->
                resolveRelative(amount(m.groupValues[1]), m.groupValues[2])
            },

            Rule("""$AFTER_START(?:$LEAD)?(?:the\s+)?end of next week\b""") {
                mondayNextWeek().with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)) to null
            },
            Rule("""$AFTER_START(?:$LEAD)?(?:the\s+)?(?:end of (?:the\s+|this\s+)?week|eow)\b""") { endOfThisWeek() to null },
            Rule("""$AFTER_START(?:$LEAD)?this week\b""") { endOfThisWeek() to null },
            Rule("""$AFTER_START(?:$LEAD)?next weekend\b""") { nextWeekday(DayOfWeek.SATURDAY) to null },
            Rule("""$AFTER_START(?:$LEAD)?(?:this|the)\s+weekend\b""") { thisWeekday(DayOfWeek.SATURDAY) to null },
            Rule("""$AFTER_START(?:$LEAD)?next week\b""") { mondayNextWeek() to null },

            Rule("""$AFTER_START(?:$LEAD)?(?:the\s+)?end of next month\b""") {
                today.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth()) to null
            },
            Rule("""$AFTER_START(?:$LEAD)?(?:the\s+)?end of (?:the\s+|this\s+)?month\b""") {
                today.with(TemporalAdjusters.lastDayOfMonth()) to null
            },
            Rule("""$AFTER_START(?:$LEAD)?(?:the\s+)?(?:start of |beginning of )?next month\b""") {
                today.plusMonths(1).withDayOfMonth(1) to null
            },
            Rule("""$AFTER_START(?:$LEAD)?(?:the\s+)?end of (?:the\s+|this\s+)?year\b""") {
                today.with(TemporalAdjusters.lastDayOfYear()) to null
            },

            Rule("""$AFTER_START(?:$LEAD)?(?:the\s+)?($DAY_NUMBER)(?:\s+of)?\s+($MONTH)\b(?:,?\s+(\d{4}))?""") { m ->
                resolveMonthDay(monthNumber(m.groupValues[2]), dayNumber(m.groupValues[1]), fullYear(m.groupValues[3]))?.let { it to null }
            },
            Rule("""$AFTER_START(?:$LEAD)?($MONTH)\s+(?:the\s+)?($DAY_NUMBER)\b(?:,?\s+(\d{4}))?""") { m ->
                resolveMonthDay(monthNumber(m.groupValues[1]), dayNumber(m.groupValues[2]), fullYear(m.groupValues[3]))?.let { it to null }
            },

            Rule("""$AFTER_START(?:$LEAD)?$NOT_PAST_OR_RECURRING(next|this coming|coming|this)\s+($WEEKDAY)\b(?:\s+($PART_OF_DAY))?""") { m ->
                val day = weekday(m.groupValues[2])
                val date = when (m.groupValues[1].lowercase()) {
                    "next" -> nextWeekday(day)
                    "this" -> thisWeekday(day)
                    else -> comingWeekday(day)
                }
                date to partOfDayTime(m.groupValues[3])
            },
            Rule("""$AFTER_START(?:$LEAD)?$NOT_PAST_OR_RECURRING($WEEKDAY)\b(?:\s+($PART_OF_DAY))?""") { m ->
                comingWeekday(weekday(m.groupValues[1])) to partOfDayTime(m.groupValues[2])
            },

            Rule("""$AFTER_START(?:$LEAD)?(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})\b""") { m ->
                dateOrNull(fullYear(m.groupValues[3]) ?: today.year, m.groupValues[2].toInt(), m.groupValues[1].toInt())?.let { it to null }
            },
            Rule("""$AFTER_START$LEAD(\d{1,2})[/-](\d{1,2})\b""") { m ->
                resolveMonthDay(m.groupValues[2].toInt(), m.groupValues[1].toInt(), null)?.let { it to null }
            },

            Rule("""$AFTER_START$LEAD(?:the\s+)?(\d{1,2}(?:st|nd|rd|th)|$ORDINAL_WORD)\b(?!\s+(?:thought|floor|page|time|place|step))""") { m ->
                nextDayOfMonth(dayNumber(m.groupValues[1]))?.let { it to null }
            },
            Rule("""$AFTER_START(?:by|before|until|till|in|for)\s+($BARE_MONTH)\b""") { m ->
                resolveBareMonth(monthNumber(m.groupValues[1])) to null
            },
        )

        const val TIME_LEAD = """(?:(?:at|by|before|around|till|until)\s+)?"""
        const val STRICT_TIME_LEAD = """(?:at|by|before|around|till|until)\s+"""
        const val TIME_END = """(?=\s*(?:$|[,.;!?]|and\b|then\b))"""

        val TIME_RULES: List<Pair<Regex, (MatchResult) -> LocalTime?>> = listOf(
            Regex("""$AFTER_START$TIME_LEAD(\d{1,2})[:.](\d{2})\s*(am|pm)\b""", RegexOption.IGNORE_CASE) to { m ->
                clock(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3])
            },
            Regex("""$AFTER_START$TIME_LEAD(\d{1,2}|$HOUR_WORD)\s*(am|pm)\b""", RegexOption.IGNORE_CASE) to { m ->
                hourNumber(m.groupValues[1])?.let { clock(it, 0, m.groupValues[2]) }
            },
            Regex("""$AFTER_START$TIME_LEAD(\d{1,2}|$HOUR_WORD)\s*o'?\s?clock\b""", RegexOption.IGNORE_CASE) to { m ->
                hourNumber(m.groupValues[1])?.let { clock(it, 0, null) }
            },
            Regex("""$AFTER_START$STRICT_TIME_LEAD(\d{1,2}):(\d{2})\b""", RegexOption.IGNORE_CASE) to { m ->
                clock(m.groupValues[1].toInt(), m.groupValues[2].toInt(), null)
            },
            Regex("""$AFTER_START$TIME_LEAD(noon|midday|midnight)\b""", RegexOption.IGNORE_CASE) to { m ->
                if (m.groupValues[1].equals("midnight", ignoreCase = true)) LocalTime.of(23, 59) else LocalTime.NOON
            },
            Regex("""$AFTER_START$STRICT_TIME_LEAD(\d{1,2}|$HOUR_WORD)$TIME_END""", RegexOption.IGNORE_CASE) to { m ->
                hourNumber(m.groupValues[1])?.let { clock(it, 0, null) }
            },
            Regex("""$AFTER_START(?:in the\s+)(morning|afternoon|evening)\b""", RegexOption.IGNORE_CASE) to { m ->
                partOfDayTime(m.groupValues[1])
            },
            Regex("""$AFTER_START(?:at\s+)(night)\b""", RegexOption.IGNORE_CASE) to { m ->
                partOfDayTime(m.groupValues[1])
            },
        )

        val SPACE_BEFORE_PUNCTUATION = Regex("""\s+([,.;:!?])""")
        val REPEATED_COMMAS = Regex(""",(\s*,)+""")
        val WHITESPACE = Regex("""\s+""")
        val DANGLING_START = Regex("""^[,;:\s]+""")
        val DANGLING_END = Regex(
            """[\s,;:]*(?:\b(?:by|on|at|before|for|due|until|till|latest|and|then)\b[\s,;:]*)*$""",
            RegexOption.IGNORE_CASE,
        )

        fun tidy(text: String): String = text
            .replace(WHITESPACE, " ")
            .replace(SPACE_BEFORE_PUNCTUATION, "$1")
            .replace(REPEATED_COMMAS, ",")
            .replace(DANGLING_START, "")
            .replace(DANGLING_END, "")
            .trim()
    }
}
