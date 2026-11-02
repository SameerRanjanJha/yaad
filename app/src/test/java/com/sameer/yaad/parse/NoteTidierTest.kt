package com.sameer.yaad.parse

import com.sameer.yaad.parse.ItemKind.IDEA
import com.sameer.yaad.parse.ItemKind.TASK
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteTidierTest {
    private val thursday = Deadline(LocalDate.of(2026, 11, 5))
    private val friday = Deadline(LocalDate.of(2026, 11, 6))

    private fun task(title: String, deadline: Deadline? = null) = ParsedItem(TASK, title, deadline = deadline)
    private fun idea(title: String) = ParsedItem(IDEA, title)

    private fun titles(vararg items: ParsedItem) = NoteTidier.tidy(items.toList()).map { it.title }

    // Repeats

    @Test fun `the same task said three times is one item`() {
        val item = NoteTidier.tidy(listOf(task("Call the bank", thursday), task("Call the bank", thursday), task("Call the bank", friday))).single()
        assertEquals("Call the bank", item.title)
        assertEquals(thursday, item.deadline)
        assertNull(item.detail)
    }

    @Test fun `word order doesn't matter`() {
        assertEquals(listOf("Send the invoice to Priya"), titles(task("Send the invoice to Priya"), task("Send Priya the invoice")))
    }

    @Test fun `the fuller wording wins`() {
        val item = NoteTidier.tidy(listOf(task("Buy milk"), task("Buy milk and eggs"))).single()
        assertEquals("Buy milk and eggs", item.title)
        assertNull(item.detail)
    }

    @Test fun `a shorter form can be much shorter than the long one`() {
        val item = NoteTidier.tidy(listOf(task("Send the invoice to Priya"), task("Send Priya the invoice, and I keep forgetting about it every single week"))).single()
        assertEquals("Send the invoice to Priya", item.title)
    }

    @Test fun `a deadline from a repeat is kept`() {
        val item = NoteTidier.tidy(listOf(task("Talk to Vivek about pricing"), task("Talk to Vivek about pricing", friday))).single()
        assertEquals(friday, item.deadline)
    }

    @Test fun `a time from a repeat on the same day is kept`() {
        val atFive = friday.copy(time = LocalTime.of(17, 0))
        assertEquals(atFive, NoteTidier.tidy(listOf(task("Call Ravi", friday), task("Call Ravi", atFive))).single().deadline)
    }

    @Test fun `the same verb with a pronoun refers back`() {
        val item = NoteTidier.tidy(listOf(task("Pay the electricity bill"), task("Pay it", friday), task("Pay it otherwise they charge a late fee"))).single()
        assertEquals("Pay the electricity bill", item.title)
        assertEquals(friday, item.deadline)
        assertEquals("Pay it otherwise they charge a late fee", item.detail)
    }

    @Test fun `an identical idea and task become one task`() {
        assertEquals(TASK, NoteTidier.tidy(listOf(idea("Check the train times"), task("Check the train times"))).single().kind)
    }

    // Statements

    @Test fun `a fact about a task becomes its detail`() {
        val item = NoteTidier.tidy(listOf(task("Take the car to the service centre"), idea("The car is making a weird noise"))).single()
        assertEquals("Take the car to the service centre", item.title)
        assertEquals("The car is making a weird noise", item.detail)
    }

    @Test fun `a fact can come before its task`() {
        val item = NoteTidier.tidy(listOf(idea("The thing is the car is making a weird noise"), task("Take the car to the service centre"))).single()
        assertEquals("The thing is the car is making a weird noise", item.detail)
    }

    @Test fun `what someone else will do is not a task`() {
        val items = NoteTidier.tidy(listOf(task("Finish the proposal", thursday), task("Send the proposal to Ankit", thursday), task("Ankit will review the proposal", thursday)))
        assertEquals(listOf("Finish the proposal", "Send the proposal to Ankit"), items.map { it.title })
        assertEquals("Ankit will review the proposal", items[1].detail)
    }

    @Test fun `a sentence pointing back joins what came before it`() {
        val items = NoteTidier.tidy(listOf(task("Get the AC serviced"), task("Call the service guy"), idea("His number is in my phone"), task("Tell him to come", friday)))
        assertEquals(listOf("Get the AC serviced", "Call the service guy", "Tell him to come"), items.map { it.title })
        assertEquals("His number is in my phone", items[1].detail)
    }

    @Test fun `a due date said separately moves onto the task`() {
        val item = NoteTidier.tidy(listOf(task("Pay the electricity bill"), task("The electricity bill is", friday))).single()
        assertEquals(friday, item.deadline)
    }

    @Test fun `a date for what a task leads up to moves onto the task`() {
        val item = NoteTidier.tidy(listOf(task("Talk to Vivek before the launch"), task("The launch is", friday))).single()
        assertEquals(friday, item.deadline)
    }

    @Test fun `what's left of a sentence once its date is out joins the item before it`() {
        val item = NoteTidier.tidy(listOf(task("Talk to Vivek about the pricing page", friday), task("The launch is", friday))).single()
        assertEquals("Talk to Vivek about the pricing page", item.title)
        assertEquals(friday, item.deadline)
    }

    @Test fun `what's left of a sentence with another date stays`() {
        assertEquals(2, titles(task("Talk to Vivek about the pricing page", thursday), task("The launch is", friday)).size)
    }

    @Test fun `a pronoun can refer back across kinds`() {
        assertEquals(listOf("Book the flight tickets"), titles(task("Book the flight tickets"), idea("Book whichever is cheaper")))
    }

    @Test fun `background goes to the item it mentions, or else the last one`() {
        val items = NoteTidier.tidy(
            listOf(task("Buy the shoes"), task("Call the plumber")),
            background = listOf(idea("Start from the shoes"), idea("Last time it went badly")),
        )
        assertEquals("Start from the shoes", items[0].detail)
        assertEquals("Last time it went badly", items[1].detail)
    }

    // What has to stay separate

    @Test fun `two different bills stay two tasks`() {
        assertEquals(2, titles(task("Pay the electricity bill"), task("Pay the water bill")).size)
    }

    @Test fun `the same job for different people stays two tasks`() {
        assertEquals(2, titles(task("Email Vivek the report"), task("Email Priya the report")).size)
    }

    @Test fun `saying again means another time`() {
        assertEquals(2, titles(task("Call Ravi", thursday), task("Call Ravi again", friday)).size)
    }

    @Test fun `doing the opposite stays separate`() {
        assertEquals(2, titles(task("Book the cab"), task("Cancel the cab")).size)
        assertEquals(2, titles(task("Renew the gym membership"), task("Don't renew the gym membership")).size)
    }

    @Test fun `the same verb with a new name is a new job`() {
        assertEquals(2, titles(task("Send the report to Vivek"), task("Send it to Ankit")).size)
    }

    @Test fun `a different verb with a pronoun stays`() {
        assertEquals(2, titles(task("Call the service guy"), task("Tell him to come", friday)).size)
    }

    @Test fun `an unrelated thought stays an idea`() {
        assertEquals(3, titles(idea("The onboarding is too long"), task("Send the report to Vivek"), task("Book a cab")).size)
    }

    @Test fun `a suggestion about the same thing stays an idea`() {
        assertEquals(2, titles(task("Update the landing page"), idea("The landing page could use a better headline")).size)
    }

    @Test fun `a dated event stays even if it shares a word with a task`() {
        assertEquals(2, titles(task("Call Ravi about the contract"), task("The meeting with Ravi is", friday)).size)
    }

    @Test fun `a job in a later clause stays a task`() {
        assertEquals(2, titles(task("Prepare the deck"), task("Rahul has those numbers so I should ask him")).size)
    }

    @Test fun `a statement alone stays`() {
        assertEquals(listOf("The car is making a weird noise"), titles(idea("The car is making a weird noise")))
    }

    @Test fun `nothing to tidy changes nothing`() {
        val items = listOf(task("Call the bank", thursday), idea("Add a dark mode"))
        assertEquals(items, NoteTidier.tidy(items))
    }
}
