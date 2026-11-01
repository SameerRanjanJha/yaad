package com.sameer.yaad.parse

import com.sameer.yaad.parse.ItemKind.IDEA
import com.sameer.yaad.parse.ItemKind.TASK
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiNoteParserTest {
    // Wednesday 4 Nov 2026, 10:00.
    private val now = LocalDateTime.of(2026, 11, 4, 10, 0)
    private val thursday = LocalDate.of(2026, 11, 5)
    private val friday = LocalDate.of(2026, 11, 6)

    private fun parser(reply: String?) = AiNoteParser({ reply }, NoteParser(), prefill = "")

    private fun item(type: String, title: String, whenSaid: String = "") =
        """{"type":"$type","title":"$title","when":"$whenSaid"}"""

    private fun reply(vararg items: String) = """{"items":[${items.joinToString(",")}]}"""

    @Test fun `the model decides the items and their kinds`() {
        val items = parser(reply(item("task", "Pick up the car", "tomorrow"), item("idea", "A better headline")))
            .parse("okay so tomorrow I have to pick up the car and the landing page could use a better headline", now)
        assertEquals(listOf(TASK, IDEA), items.map { it.kind })
        assertEquals(listOf("Pick up the car", "A better headline"), items.map { it.title })
        assertEquals(Deadline(thursday), items[0].deadline)
    }

    @Test fun `dates come from the tested extractor, not the model`() {
        val item = parser(reply(item("task", "Call Ravi", "Friday at 5 pm")))
            .parse("Call Ravi on Friday at 5 pm", now).single()
        assertEquals(Deadline(friday, LocalTime.of(17, 0)), item.deadline)
    }

    @Test fun `a bare day of the month is understood`() {
        val item = parser(reply(item("task", "Pay the electricity bill", "5th")))
            .parse("the electricity bill has to be paid by the 5th", now).single()
        assertEquals(LocalDate.of(2026, 11, 5), item.deadline?.date)
    }

    @Test fun `a date the person never said is thrown away`() {
        val item = parser(reply(item("task", "Call the bank", "tomorrow"))).parse("Call the bank", now).single()
        assertNull(item.deadline)
    }

    @Test fun `ideas never get deadlines`() {
        val item = parser(reply(item("idea", "Launch the site", "Friday"))).parse("What if we launch the site on Friday", now).single()
        assertEquals(IDEA, item.kind)
        assertNull(item.deadline)
    }

    @Test fun `a date the model missed is recovered from the rules`() {
        val item = parser(reply(item("task", "Call the bank"))).parse("Call the bank tomorrow", now).single()
        assertEquals(Deadline(thursday), item.deadline)
    }

    @Test fun `app wording the model left in is still removed`() {
        val item = parser(reply(item("task", "Set a reminder to call mom"))).parse("Set a reminder to call mom", now).single()
        assertEquals("Call mom", item.title)
    }

    @Test fun `invented items are dropped`() {
        val items = parser(reply(item("task", "Call the bank"), item("task", "Schedule quarterly dentistry checkups")))
            .parse("Call the bank", now)
        assertEquals(listOf("Call the bank"), items.map { it.title })
    }

    @Test fun `date words the model left in a title are removed`() {
        val item = parser(reply(item("task", "Tomorrow I have to submit the report", "Tomorrow")))
            .parse("Tomorrow I have to submit the report.", now).single()
        assertEquals("Submit the report", item.title)
        assertEquals(Deadline(thursday), item.deadline)
    }

    @Test fun `a task the model dropped is added back from the rules`() {
        val items = parser(reply(item("idea", "Podcast with customers")))
            .parse("I need to buy groceries today. I was wondering if we could do a podcast with customers.", now)
        assertEquals(listOf("Podcast with customers", "Buy groceries"), items.map { it.title })
        assertEquals(listOf(IDEA, TASK), items.map { it.kind })
        assertEquals(Deadline(now.toLocalDate()), items[1].deadline)
    }

    @Test fun `an item covered by the model is not added twice`() {
        val items = parser(reply(item("task", "Pay the electricity bill", "by the 5th")))
            .parse("I have to pay the electricity bill by the 5th.", now)
        assertEquals(listOf("Pay the electricity bill"), items.map { it.title })
    }

    @Test fun `the same thing said at length by the rules is not added as a duplicate`() {
        val items = parser(reply(item("idea", "Write a weekly blog about what we learn")))
            .parse("One thing I want to try is writing a weekly blog about what we learn.", now)
        assertEquals(listOf("Write a weekly blog about what we learn"), items.map { it.title })
    }

    @Test fun `a subject copied from the prompt's examples is rejected`() {
        val items = parser(reply(item("idea", "Daily digest email for tracking habits")))
            .parse("What if the app had a daily digest email?", now)
        assertEquals(listOf("What if the app had a daily digest email?"), items.map { it.title })
    }

    @Test fun `the same subject is fine when the person really said it`() {
        val items = parser(reply(item("idea", "App for tracking habits"))).parse("We could make an app for tracking habits.", now)
        assertEquals(listOf("App for tracking habits"), items.map { it.title })
    }

    @Test fun `no reply falls back to the rules`() {
        val items = parser(null).parse("I need to call the bank tomorrow.", now)
        assertEquals(listOf("Call the bank"), items.map { it.title })
        assertEquals(Deadline(thursday), items.single().deadline)
    }

    @Test fun `a broken reply falls back to the rules`() {
        val items = parser("""{"items":[{"type":"task","ti""").parse("I need to call the bank tomorrow.", now)
        assertEquals(listOf("Call the bank"), items.map { it.title })
    }

    @Test fun `only invented items falls back to the rules`() {
        val items = parser(reply(item("task", "Schedule quarterly dentistry checkups"))).parse("I need to call the bank tomorrow.", now)
        assertEquals(listOf("Call the bank"), items.map { it.title })
    }

    @Test fun `long notes go through in pieces and every piece is kept`() {
        val prompts = mutableListOf<String>()
        val long = (1..40).joinToString(" ") { "Sentence number $it talks about the quarterly budget review." }
        val parser = AiNoteParser({ prompts += it; reply(item("idea", "Quarterly budget review")) }, NoteParser(), prefill = "")
        val items = parser.parse(long, now)
        assertTrue("sent ${prompts.size} pieces", prompts.size > 1)
        assertEquals(prompts.size, items.size)
        assertTrue(prompts.first().contains("Sentence number 1 "))
        assertTrue(prompts.last().contains("Sentence number 40 "))
    }

    @Test fun `the prompt ends with the note and the prefill so the model continues from there`() {
        val prompt = AiNotePrompt.build("Call the bank", prefill = "<think>\n\n</think>\n\n")
        assertTrue(prompt.endsWith("<|im_start|>user\nCall the bank<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n"))
    }
}
