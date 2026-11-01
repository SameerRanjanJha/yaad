package com.sameer.yaad.parse

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeadlineExtractorTest {
    // Wednesday 4 Nov 2026, 10:00. The week runs Mon 2 Nov to Sun 8 Nov.
    private val now = LocalDateTime.of(2026, 11, 4, 10, 0)
    private val extractor = DeadlineExtractor(now)

    private fun date(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)

    private fun assertDate(expected: LocalDate, text: String) {
        assertEquals(text, expected, extractor.extract(text).deadline?.date)
    }

    private fun assertDeadline(expected: Deadline, text: String) {
        assertEquals(text, expected, extractor.extract(text).deadline)
    }

    private fun assertNoDeadline(text: String) {
        assertNull(text, extractor.extract(text).deadline)
    }

    @Test fun `relative days`() {
        assertDate(date(11, 4), "call Ravi today")
        assertDate(date(11, 5), "call Ravi tomorrow")
        assertDate(date(11, 6), "call Ravi day after tomorrow")
        assertDate(date(11, 6), "call Ravi the day after tomorrow")
        assertDate(date(11, 7), "call Ravi in 3 days")
        assertDate(date(11, 7), "call Ravi in three days")
        assertDate(date(11, 6), "call Ravi in a couple of days")
        assertDate(date(11, 7), "call Ravi in a few days")
        assertDate(date(11, 11), "call Ravi in a week")
        assertDate(date(11, 18), "call Ravi in two weeks")
        assertDate(date(12, 4), "call Ravi in a month")
        assertDate(date(11, 9), "call Ravi 5 days from now")
    }

    @Test fun `hindi words whisper may keep`() {
        assertDate(date(11, 4), "call Ravi aaj")
        assertDate(date(11, 5), "call Ravi kal")
        assertDate(date(11, 6), "call Ravi parso")
    }

    @Test fun `bare weekday is the coming one, never today`() {
        assertDate(date(11, 6), "call Ravi on Friday")
        assertDate(date(11, 6), "call Ravi by Friday")
        assertDate(date(11, 9), "call Ravi Monday")
        assertDate(date(11, 11), "call Ravi on Wednesday")
    }

    @Test fun `this and next weekday`() {
        assertDate(date(11, 6), "call Ravi this Friday")
        assertDate(date(11, 4), "call Ravi this Wednesday")
        assertDate(date(11, 13), "call Ravi next Friday")
        assertDate(date(11, 9), "call Ravi next Monday")
        assertDate(date(11, 6), "call Ravi this coming Friday")
    }

    @Test fun `next weekday near the end of the week takes the earlier date`() {
        val sunday = DeadlineExtractor(LocalDateTime.of(2026, 11, 1, 10, 0))
        assertEquals(date(11, 6), sunday.extract("call Ravi next Friday").deadline?.date)
    }

    @Test fun `weeks and months`() {
        assertDate(date(11, 6), "finish the deck by end of the week")
        assertDate(date(11, 6), "finish the deck by end of this week")
        assertDate(date(11, 6), "finish the deck this week")
        assertDate(date(11, 13), "finish the deck by end of next week")
        assertDate(date(11, 9), "finish the deck next week")
        assertDate(date(11, 7), "finish the deck this weekend")
        assertDate(date(11, 14), "finish the deck next weekend")
        assertDate(date(11, 30), "finish the deck by end of the month")
        assertDate(date(12, 31), "finish the deck by end of next month")
        assertDate(date(12, 1), "finish the deck next month")
        assertDate(date(12, 31), "finish the deck by the end of the year")
    }

    @Test fun `calendar dates`() {
        assertDate(date(11, 15), "pay rent on 15th November")
        assertDate(date(11, 15), "pay rent on the 15th of November")
        assertDate(date(11, 15), "pay rent by November 15")
        assertDate(date(11, 15), "pay rent by November 15th")
        assertDate(date(11, 15), "pay rent by the fifteenth of November")
        assertDate(date(11, 21), "pay rent on November twenty first")
        assertDate(date(12, 5), "pay rent on 5 Dec")
        assertDate(date(5, 3, 2027), "pay rent on May 3")
        assertDate(date(1, 10, 2028), "pay rent on 10 January 2028")
    }

    @Test fun `dates already gone this year roll forward`() {
        assertDate(date(3, 2, 2027), "pay rent on March 2")
    }

    @Test fun `day of month alone`() {
        assertDate(date(11, 15), "pay rent by the 15th")
        assertDate(date(12, 3), "pay rent on the 3rd")
        assertDate(date(11, 5), "pay rent before the fifth")
    }

    @Test fun `numeric dates read day first`() {
        assertDate(date(12, 5), "pay rent by 5/12")
        assertDate(date(12, 5), "pay rent on 5/12/2026")
        assertDate(date(12, 5), "pay rent on 5-12-26")
    }

    @Test fun `bare months`() {
        assertDate(date(12, 1), "renew passport by December")
        assertDate(date(3, 1, 2027), "renew passport in March")
    }

    @Test fun `clock times`() {
        assertDeadline(Deadline(date(11, 5), LocalTime.of(17, 0)), "call Ravi tomorrow at 5 pm")
        assertDeadline(Deadline(date(11, 5), LocalTime.of(17, 30)), "call Ravi at 5:30 pm tomorrow")
        assertDeadline(Deadline(date(11, 5), LocalTime.of(17, 30)), "call Ravi tomorrow at 5.30 pm")
        assertDeadline(Deadline(date(11, 5), LocalTime.of(9, 0)), "call Ravi tomorrow at 9 am")
        assertDeadline(Deadline(date(11, 6), LocalTime.of(15, 0)), "call Ravi on Friday at three pm")
        assertDeadline(Deadline(date(11, 5), LocalTime.of(12, 0)), "call Ravi tomorrow at noon")
        assertDeadline(Deadline(date(11, 5), LocalTime.of(0, 30)), "call Ravi tomorrow at 12:30 am")
    }

    @Test fun `hour without am or pm is guessed the way people mean it`() {
        assertDeadline(Deadline(date(11, 5), LocalTime.of(17, 0)), "call Ravi tomorrow at 5")
        assertDeadline(Deadline(date(11, 5), LocalTime.of(10, 0)), "call Ravi tomorrow at 10")
        assertDeadline(Deadline(date(11, 5), LocalTime.of(16, 0)), "call Ravi tomorrow at 4 o'clock")
    }

    @Test fun `a time alone means its next occurrence`() {
        assertDeadline(Deadline(date(11, 4), LocalTime.of(17, 0)), "call Ravi at 5 pm")
        assertDeadline(Deadline(date(11, 5), LocalTime.of(9, 0)), "call Ravi at 9 am")
        assertDeadline(Deadline(date(11, 5), LocalTime.of(9, 0)), "call Ravi in the morning")
        assertDeadline(Deadline(date(11, 4), LocalTime.of(18, 0)), "call Ravi in the evening")
    }

    @Test fun `parts of the day`() {
        assertDeadline(Deadline(date(11, 4), LocalTime.of(20, 0)), "call Ravi tonight")
        assertDeadline(Deadline(date(11, 4), LocalTime.of(18, 0)), "call Ravi this evening")
        assertDeadline(Deadline(date(11, 5), LocalTime.of(9, 0)), "call Ravi tomorrow morning")
        assertDeadline(Deadline(date(11, 6), LocalTime.of(14, 0)), "call Ravi on Friday afternoon")
        assertDeadline(Deadline(date(11, 4), LocalTime.of(18, 0)), "send it by end of day")
        assertDeadline(Deadline(date(11, 4), LocalTime.of(18, 0)), "send it by EOD")
    }

    @Test fun `hours and minutes from now`() {
        assertDeadline(Deadline(date(11, 4), LocalTime.of(12, 0)), "call Ravi in 2 hours")
        assertDeadline(Deadline(date(11, 4), LocalTime.of(10, 30)), "call Ravi in half an hour")
        assertDeadline(Deadline(date(11, 4), LocalTime.of(10, 20)), "call Ravi in 20 minutes")
    }

    @Test fun `past and recurring phrases are not deadlines`() {
        assertNoDeadline("I spoke to Ravi last Friday")
        assertNoDeadline("gym every Monday")
        assertNoDeadline("take vitamins every day")
        assertNoDeadline("send the weekly report")
    }

    @Test fun `words that only look like dates`() {
        assertNoDeadline("I may need a bigger desk")
        assertNoDeadline("the march was long")
        assertNoDeadline("buy 5 apples")
        assertNoDeadline("on second thought skip it")
        assertNoDeadline("meet on the first floor")
    }

    @Test fun `the phrase is cut from the remaining text`() {
        assertEquals("call Ravi", extractor.extract("call Ravi by Friday").remainder)
        assertEquals("call Ravi", extractor.extract("call Ravi tomorrow at 5 pm").remainder)
        assertEquals("call Ravi about the loan", extractor.extract("call Ravi tomorrow about the loan").remainder)
        assertEquals("call Ravi", extractor.extract("By Friday, call Ravi").remainder)
    }
}
