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

class NoteParserTest {
    // Wednesday 4 Nov 2026, 10:00.
    private val now = LocalDateTime.of(2026, 11, 4, 10, 0)
    private val thursday = LocalDate.of(2026, 11, 5)
    private val friday = LocalDate.of(2026, 11, 6)
    private val parser = NoteParser()

    private fun parse(text: String) = parser.parse(text, now)

    private fun titles(text: String) = parse(text).map { it.title }

    private fun kinds(text: String) = parse(text).map { it.kind }

    @Test fun `single task with deadline`() {
        val item = parse("I need to call the bank tomorrow.").single()
        assertEquals(TASK, item.kind)
        assertEquals("Call the bank", item.title)
        assertEquals(Deadline(thursday), item.deadline)
    }

    @Test fun `task without a deadline stays a task`() {
        val item = parse("Remember to renew the car insurance.").single()
        assertEquals(TASK, item.kind)
        assertEquals("Renew the car insurance", item.title)
        assertNull(item.deadline)
    }

    @Test fun `task with time keeps the clock time`() {
        val item = parse("Call Ravi about the contract on Friday at 5 p.m.").single()
        assertEquals("Call Ravi about the contract", item.title)
        assertEquals(Deadline(friday, LocalTime.of(17, 0)), item.deadline)
    }

    @Test fun `preamble is stripped from titles`() {
        assertEquals(listOf("Send the invoice"), titles("Okay so I need to send the invoice."))
        assertEquals(listOf("Book the tickets"), titles("Don't forget to book the tickets."))
        assertEquals(listOf("Pay the electricity bill"), titles("Remind me to pay the electricity bill."))
        assertEquals(listOf("Email Priya"), titles("I'll email Priya."))
        assertEquals(listOf("Check the logs"), titles("Let me check the logs."))
        assertEquals(listOf("Water the plants"), titles("We have to water the plants."))
    }

    @Test fun `several sentences become several tasks`() {
        val items = parse("Call the bank tomorrow. Send the invoice to Vivek by Friday. Book a cab.")
        assertEquals(listOf("Call the bank", "Send the invoice to Vivek", "Book a cab"), items.map { it.title })
        assertEquals(listOf(Deadline(thursday), Deadline(friday), null), items.map { it.deadline })
        assertTrue(items.all { it.kind == TASK })
    }

    @Test fun `and also separates tasks in one sentence`() {
        val items = parse("I need to call the bank tomorrow and also email Ravi.")
        assertEquals(listOf("Call the bank", "Email Ravi"), items.map { it.title })
        assertEquals(listOf(Deadline(thursday), null), items.map { it.deadline })
    }

    @Test fun `and followed by a verb separates tasks`() {
        assertEquals(listOf("Buy milk", "Pay the rent"), titles("Buy milk and pay the rent."))
        assertEquals(listOf("Call Ravi", "Book the flight"), titles("Call Ravi, book the flight."))
    }

    @Test fun `and followed by a noun stays one task`() {
        assertEquals(listOf("Buy milk and eggs"), titles("Buy milk and eggs."))
        assertEquals(listOf("Buy milk, eggs, bread"), titles("Buy milk, eggs, bread."))
    }

    @Test fun `verb acting on the same person stays one task`() {
        assertEquals(listOf("Call Ravi and ask him about the bill"), titles("Call Ravi and ask him about the bill."))
    }

    @Test fun `then separates tasks only before a new action`() {
        assertEquals(listOf("Call the bank", "Email Ravi"), titles("Call the bank, then email Ravi."))
        assertEquals(listOf("Call the bank", "Email Ravi"), titles("Call the bank and then email Ravi."))
    }

    @Test fun `trailing deadline covers tasks joined by and`() {
        val items = parse("Call the bank and send the invoice by Friday.")
        assertEquals(listOf("Call the bank", "Send the invoice"), items.map { it.title })
        assertEquals(listOf(Deadline(friday), Deadline(friday)), items.map { it.deadline })
    }

    @Test fun `leading deadline covers what follows`() {
        val items = parse("By Friday, call the bank and send the invoice.")
        assertEquals(listOf("Call the bank", "Send the invoice"), items.map { it.title })
        assertEquals(listOf(Deadline(friday), Deadline(friday)), items.map { it.deadline })
    }

    @Test fun `deadline spoken in the next sentence applies to the task`() {
        val item = parse("Send the invoice to Ravi. It's due on Friday.").single()
        assertEquals("Send the invoice to Ravi", item.title)
        assertEquals(Deadline(friday), item.deadline)
    }

    @Test fun `bare date sentence applies to the task before it`() {
        val item = parse("Call the dentist. Tomorrow.").single()
        assertEquals("Call the dentist", item.title)
        assertEquals(Deadline(thursday), item.deadline)
    }

    @Test fun `explanation sentence becomes detail`() {
        val item = parse("Call the bank tomorrow. It's about the home loan.").single()
        assertEquals("Call the bank", item.title)
        assertEquals("It's about the home loan", item.detail)
    }

    @Test fun `numbered lists`() {
        assertEquals(
            listOf("Call the bank", "Send the invoice"),
            titles("One, call the bank. Two, send the invoice."),
        )
        assertEquals(
            listOf("Call the bank", "Send the invoice"),
            titles("First, call the bank. Second, send the invoice."),
        )
    }

    @Test fun `idea phrasing reads as an idea`() {
        assertEquals(listOf(IDEA), kinds("What if we added a Hindi version of the landing page?"))
        assertEquals(listOf(IDEA), kinds("We could add a dark mode."))
        assertEquals(listOf(IDEA), kinds("It would be cool to have a referral program."))
        assertEquals(listOf(IDEA), kinds("I was thinking about a weekly newsletter."))
        assertEquals(listOf(IDEA), kinds("Maybe a podcast about founders."))
    }

    @Test fun `explicit idea label`() {
        val item = parse("Idea: a podcast about founders.").single()
        assertEquals(IDEA, item.kind)
        assertEquals("A podcast about founders", item.title)
    }

    @Test fun `idea announced in its own sentence`() {
        val item = parse("I have an idea. A podcast about founders.").single()
        assertEquals(IDEA, item.kind)
        assertEquals("A podcast about founders", item.title)
    }

    @Test fun `idea with a stated subject keeps it`() {
        val item = parse("Idea for the landing page. We could show live rankings.").single()
        assertEquals(IDEA, item.kind)
        assertEquals("Idea for the landing page", item.title)
        assertEquals("We could show live rankings", item.detail)
    }

    @Test fun `neighbouring ideas club into one`() {
        val item = parse("We could add a dark mode. It would be nice for night use. Maybe a schedule too.").single()
        assertEquals(IDEA, item.kind)
        assertEquals("We could add a dark mode", item.title)
        assertEquals("It would be nice for night use. Maybe a schedule too", item.detail)
    }

    @Test fun `another idea starts a new one`() {
        val items = parse("We could add a dark mode. Another idea: a referral program.")
        assertEquals(listOf(IDEA, IDEA), items.map { it.kind })
        assertEquals(listOf("We could add a dark mode", "A referral program"), items.map { it.title })
    }

    @Test fun `a task between ideas keeps them apart`() {
        val items = parse("We could add a dark mode. Call Ravi tomorrow. What if we had a referral program?")
        assertEquals(listOf(IDEA, TASK, IDEA), items.map { it.kind })
    }

    @Test fun `an idea and a task in one breath`() {
        val items = parse(
            "I need to call the bank tomorrow and also send the invoice to Vivek by Friday. " +
                "Oh and an idea, we could add a Hindi version of the landing page.",
        )
        assertEquals(listOf(TASK, TASK, IDEA), items.map { it.kind })
        assertEquals("Call the bank", items[0].title)
        assertEquals(Deadline(thursday), items[0].deadline)
        assertEquals("Send the invoice to Vivek", items[1].title)
        assertEquals(Deadline(friday), items[1].deadline)
        assertEquals("We could add a Hindi version of the landing page", items[2].title)
    }

    @Test fun `idea phrasing keeps its date words in the text`() {
        val item = parse("What if we launched on Friday?").single()
        assertEquals(IDEA, item.kind)
        assertNull(item.deadline)
        assertTrue(item.title.contains("Friday"))
    }

    @Test fun `strong task wording beats the word idea`() {
        val item = parse("I need to write up the pricing idea by Friday.").single()
        assertEquals(TASK, item.kind)
        assertEquals(Deadline(friday), item.deadline)
    }

    @Test fun `plain observation falls to ideas`() {
        assertEquals(listOf(IDEA), kinds("The onboarding feels slow on mobile."))
    }

    @Test fun `past remark is not a deadline`() {
        val item = parse("Spoke to Ravi last Friday about the loan.").single()
        assertNull(item.deadline)
    }

    @Test fun `filler words go away`() {
        assertEquals(listOf("Call the bank"), titles("Um, I need to, uh, call the bank."))
    }

    @Test fun `abbreviations don't break sentences`() {
        val items = parse("Call Dr. Mehta tomorrow at 5 p.m. Then book the lab test.")
        assertEquals(listOf("Call Dr Mehta", "Book the lab test"), items.map { it.title })
        assertEquals(Deadline(thursday, LocalTime.of(17, 0)), items[0].deadline)
    }

    @Test fun `wording addressed to the app is removed so the task is yours`() {
        assertEquals(listOf("Call mom"), titles("Set a reminder to call mom."))
        assertEquals(listOf("Pay the rent"), titles("Add a task to pay the rent."))
        assertEquals(listOf("Call Ravi"), titles("Can you remind me to call Ravi?"))
        assertEquals(listOf("Buy milk"), titles("Please add buy milk to my list."))
        assertEquals(listOf("Renew my passport"), titles("Note down that I need to renew my passport."))
        assertEquals(listOf("Book the tickets"), titles("Make a note to book the tickets."))
        assertEquals(listOf("Finish the deck"), titles("My task is to finish the deck."))
        assertEquals(listOf("The insurance renewal"), titles("You need to remind me about the insurance renewal."))
        assertEquals(listOf("Pay the rent"), titles("Remind me to pay the rent."))
    }

    @Test fun `requests to the app stay tasks and keep their deadlines`() {
        val item = parse("Set a reminder to call mom tomorrow.").single()
        assertEquals(TASK, item.kind)
        assertEquals("Call mom", item.title)
        assertEquals(Deadline(thursday), item.deadline)

        val another = parse("Add a task to pay the rent by Friday.").single()
        assertEquals(TASK, another.kind)
        assertEquals(Deadline(friday), another.deadline)
    }

    @Test fun `something new to make is an idea`() {
        assertEquals(listOf(IDEA), kinds("Build a small app for tracking habits."))
        assertEquals(listOf(IDEA), kinds("Start a newsletter for product managers."))
        assertEquals(listOf(IDEA), kinds("Make a video on how SEO works."))
        assertEquals(listOf(IDEA), kinds("I want to start a podcast about founders."))
        assertEquals(listOf(IDEA), kinds("I should write a book."))
        assertEquals(listOf(IDEA), kinds("Try using a different color for the buttons."))
    }

    @Test fun `opinions and suggestions are ideas`() {
        assertEquals(listOf(IDEA), kinds("I think we should launch a Hindi version of the site."))
        assertEquals(listOf(IDEA), kinds("Why don't we add a pricing page?"))
        assertEquals(listOf(IDEA), kinds("Let's build a chatbot for customer support."))
        assertEquals(listOf(IDEA), kinds("A good idea would be to automate the weekly report."))
    }

    @Test fun `making something specific that already exists is a task`() {
        assertEquals(listOf(TASK), kinds("Write the report."))
        assertEquals(listOf(TASK), kinds("Change my password."))
        assertEquals(listOf(TASK), kinds("Set up the printer."))
    }

    @Test fun `obligation or a date makes even something new a task`() {
        assertEquals(listOf(TASK), kinds("I need to start a podcast."))
        assertEquals(listOf(TASK), kinds("Build a landing page by Friday."))
    }

    @Test fun `plain errands are tasks however they're said`() {
        assertEquals(listOf(TASK), kinds("Pay the electricity bill."))
        assertEquals(listOf(TASK), kinds("I should call mom."))
        assertEquals(listOf(TASK), kinds("I want to call the plumber."))
    }

    @Test fun `appointments are tasks without the leading I have`() {
        val item = parse("I have a meeting with Ravi tomorrow at 3 pm.").single()
        assertEquals(TASK, item.kind)
        assertEquals("Meeting with Ravi", item.title)
        assertEquals(Deadline(thursday, LocalTime.of(15, 0)), item.deadline)
        assertEquals(listOf(TASK), kinds("I have an appointment."))
    }

    @Test fun `an idea in the same sentence as a task is separated`() {
        val items = parse("Call the bank tomorrow, and I have an idea, we could add a dark mode.")
        assertEquals(listOf(TASK, IDEA), items.map { it.kind })
        assertEquals(listOf("Call the bank", "We could add a dark mode"), items.map { it.title })
        assertEquals(Deadline(thursday), items[0].deadline)
    }

    @Test fun `one idea is, inside a task sentence`() {
        val items = parse("I need to call the bank tomorrow and one idea is to move the savings account to another bank.")
        assertEquals(listOf(TASK, IDEA), items.map { it.kind })
        assertEquals(listOf("Call the bank", "Move the savings account to another bank"), items.map { it.title })
        assertEquals(listOf(Deadline(thursday), null), items.map { it.deadline })
    }

    @Test fun `there's an idea, inside a task sentence`() {
        val items = parse("Today I need to send the report and there's an idea about automating it.")
        assertEquals(listOf(TASK, IDEA), items.map { it.kind })
        assertEquals(listOf("Send the report", "Idea about automating it"), items.map { it.title })
    }

    @Test fun `maybe after a task is an idea, but maybe with a date is not`() {
        assertEquals(listOf(TASK, IDEA), kinds("Call the bank, maybe we can move the account."))
        assertEquals(listOf("Call Ravi"), titles("Call Ravi, maybe tomorrow."))
    }

    @Test fun `a thought and a task in one note`() {
        val items = parse(
            "So I was just thinking that the onboarding is too long and maybe we can cut it down to two steps. " +
                "Also I need to pay the electricity bill by the 5th.",
        )
        assertEquals(listOf(IDEA, TASK), items.map { it.kind })
        assertEquals("The onboarding is too long", items[0].title)
        assertEquals("Maybe we can cut it down to two steps", items[0].detail)
        assertEquals("Pay the electricity bill", items[1].title)
    }

    @Test fun `spoken introductions are not items`() {
        val items = parse(
            "Okay so there are two things. First I need to call the CA about the GST filing. " +
                "Second, I think we should launch a Hindi version of the site.",
        )
        assertEquals(listOf(TASK, IDEA), items.map { it.kind })
        assertEquals("Call the CA about the GST filing", items[0].title)
        assertEquals("Launch a Hindi version of the site", items[1].title)
    }

    @Test fun `no capture is ever dropped`() {
        assertEquals(listOf("Hmm"), titles("Hmm."))
        assertEquals(emptyList<ParsedItem>(), parse("   "))
        assertEquals(listOf("Tomorrow."), titles("Tomorrow."))
    }
}
