package com.sameer.yaad.parse

import java.time.LocalDateTime

/**
 * Turns a spoken note into tasks and ideas using rules alone, so it runs instantly,
 * offline and predictably. Anything it cannot place still survives as an idea.
 */
class NoteParser {

    fun parse(transcript: String, now: LocalDateTime): List<ParsedItem> {
        val text = normalize(transcript)
        if (text.isBlank()) return emptyList()

        val builder = ItemBuilder()
        val extractor = DeadlineExtractor(now)
        for (sentence in text.split(SENTENCE_BOUNDARY).filter { it.isNotBlank() }) {
            parseSentence(sentence, extractor, builder)
        }
        return NoteTidier.tidy(
            builder.items().ifEmpty { listOf(ParsedItem(ItemKind.IDEA, Language.titleCase(text.trim()))) },
        )
    }

    private fun parseSentence(sentence: String, extractor: DeadlineExtractor, builder: ItemBuilder) {
        val clauses = ClauseSplitter.split(sentence)
        val trailing = clauses.lastOrNull()?.let { deadlineIfTask(it.text, extractor) }
        var carried: Deadline? = null

        clauses.forEachIndexed { index, clause ->
            if (Language.isAnnouncement(clause.text)) return@forEachIndexed

            if (Language.isBareIdeaMarker(clause.text)) {
                builder.expectNewIdea()
                return@forEachIndexed
            }

            val ideaFirst = Language.readsAsIdea(clause.text)
            val extraction = if (ideaFirst) Extraction(null, clause.text) else extractor.extract(clause.text)
            val title = Language.cleanTitle(extraction.remainder)

            if (title.isEmpty()) {
                // A clause that is only a date ("By Friday, call Ravi") governs what follows,
                // or the item before it when nothing follows ("Call Ravi. Tomorrow.").
                if (extraction.deadline != null && index < clauses.lastIndex) {
                    carried = extraction.deadline
                } else {
                    extraction.deadline?.let(builder::applyDeadlineToLast)
                }
                return@forEachIndexed
            }

            if (!ideaFirst && Language.isContinuation(extraction.remainder) && builder.hasItems) {
                extraction.deadline?.let(builder::applyDeadlineToLast)
                if (!Language.isOnlyDueWording(extraction.remainder)) builder.appendToLast(title)
                return@forEachIndexed
            }

            val joinedToLast = index < clauses.lastIndex && clauses[index + 1].joinedByAnd
            val deadline = extraction.deadline
                ?: carried
                ?: trailing.takeIf { joinedToLast && allJoinedAfter(clauses, index) }

            when {
                ideaFirst -> builder.addIdea(title, startsNew = Language.namesNewIdea(clause.text))
                deadline != null || Language.readsAsTask(extraction.remainder) -> builder.addTask(title, deadline)
                else -> builder.addIdea(title, startsNew = false)
            }
        }
    }

    private fun deadlineIfTask(text: String, extractor: DeadlineExtractor): Deadline? =
        if (Language.readsAsIdea(text)) null else extractor.extract(text).deadline

    private fun allJoinedAfter(clauses: List<Clause>, index: Int): Boolean =
        clauses.drop(index + 1).all { it.joinedByAnd }

    private fun normalize(transcript: String): String = transcript
        .replace(MERIDIEM_WITH_DOTS) { "${it.groupValues[1].lowercase()}m" }
        .replace(TITLE_ABBREVIATION) { it.groupValues[1] }
        .replace(FILLER_WORDS, " ")
        .replace(HORIZONTAL_SPACE, " ")
        .trim()

    private companion object {
        val SENTENCE_BOUNDARY = Regex("""(?<=[.!?;])\s+|\n+""")
        val MERIDIEM_WITH_DOTS = Regex("""\b([ap])\.\s?m\b\.?""", RegexOption.IGNORE_CASE)
        val TITLE_ABBREVIATION = Regex("""\b(Dr|Mr|Mrs|Ms|Prof|St|Sr|Jr)\.""")
        val FILLER_WORDS = Regex("""(?:,\s*)?\b(?:um+|uh+|erm+|hmm+|you know),?\s""", RegexOption.IGNORE_CASE)
        val HORIZONTAL_SPACE = Regex("""[ \t]+""")
    }
}

internal data class Clause(val text: String, val joinedByAnd: Boolean)

/** Collects items in spoken order and clubs neighbouring ideas into one. */
private class ItemBuilder {
    private val items = mutableListOf<ParsedItem>()
    private var nextIdeaIsNew = false

    val hasItems: Boolean get() = items.isNotEmpty()

    fun items(): List<ParsedItem> = items.toList()

    fun expectNewIdea() {
        nextIdeaIsNew = true
    }

    fun addTask(title: String, deadline: Deadline?) {
        items += ParsedItem(ItemKind.TASK, title, deadline = deadline)
        nextIdeaIsNew = false
    }

    fun addIdea(title: String, startsNew: Boolean) {
        val previous = items.lastOrNull()
        val clubWithPrevious = previous?.kind == ItemKind.IDEA && !startsNew && !nextIdeaIsNew
        nextIdeaIsNew = false
        if (clubWithPrevious) appendToLast(title) else items += ParsedItem(ItemKind.IDEA, title)
    }

    fun appendToLast(text: String) {
        val last = items.removeAt(items.lastIndex)
        val detail = listOfNotNull(last.detail, text).joinToString(". ")
        items += last.copy(detail = detail)
    }

    /** A deadline spoken after the fact makes an idea a task, since ideas don't have due dates. */
    fun applyDeadlineToLast(deadline: Deadline) {
        val last = items.lastOrNull() ?: return
        if (last.deadline != null) return
        items[items.lastIndex] = last.copy(kind = ItemKind.TASK, deadline = deadline)
    }
}
