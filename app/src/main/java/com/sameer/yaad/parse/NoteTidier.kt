package com.sameer.yaad.parse

/**
 * Leaves one item per thing to do in a single note. People repeat themselves and explain as they
 * talk, and both the model and the rules tend to make an item of every sentence.
 *
 * Words said in one breath are far more likely to be about the same thing than words said days
 * apart, so this goes further than the check across notes. Nothing is thrown away: whatever a
 * merged item added moves to the detail of the one that stays, and the full note stays in the log.
 */
internal object NoteTidier {
    /** An item left after tidying, and the positions in the input of every item folded into it, its own first. */
    class Group(val item: ParsedItem, val sources: List<Int>)

    /** [background] is what the speaker said that isn't an item, kept as detail on the closest item. */
    fun tidy(items: List<ParsedItem>, background: List<ParsedItem> = emptyList()): List<ParsedItem> =
        group(items, background).map { it.item }

    fun group(items: List<ParsedItem>, background: List<ParsedItem> = emptyList()): List<Group> {
        val tidied = attachStatements(mergeRepeats(items.mapIndexed { index, item -> Group(item, listOf(index)) })).toMutableList()
        for (sentence in background) {
            val target = closestItem(sentence, tidied.map { it.item }) ?: tidied.lastIndex.takeIf { it >= 0 } ?: continue
            tidied[target] = Group(absorb(tidied[target].item, sentence), tidied[target].sources)
        }
        return tidied
    }

    private fun mergeRepeats(groups: List<Group>): List<Group> {
        val kept = mutableListOf<Group>()
        for (group in groups) {
            val match = kept.indexOfFirst { isRepeat(it.item, group.item) }
            if (match < 0) kept += group else kept[match] = combine(kept[match], group, merge(kept[match].item, group.item))
        }
        return kept
    }

    private fun combine(kept: Group, other: Group, item: ParsedItem) = Group(item, kept.sources + other.sources)

    private fun isRepeat(older: ParsedItem, newer: ParsedItem): Boolean {
        val olderWords = TitleWords.of(older.title)
        val newerWords = TitleWords.of(newer.title)
        if (olderWords.isEmpty() || newerWords.isEmpty()) return false
        if (olderWords == newerWords || refersBackTo(older, newer)) return true
        return older.kind == newer.kind && isShorterForm(olderWords, newerWords)
    }

    /** "Pay the bill" and "pay the bill for the flat". "Pay the electricity bill" and "pay the water bill" are two jobs. */
    private fun isShorterForm(first: Set<String>, second: Set<String>): Boolean {
        val (shorter, longer) = if (first.size < second.size) first to second else second to first
        return shorter.size >= 2 && longer.containsAll(shorter) && !TitleWords.changeTheMeaning(longer - shorter)
    }

    /** "Pay the bill. Pay it by Friday." A new name means a new job: "Send it to Ankit". */
    private fun refersBackTo(older: ParsedItem, newer: ParsedItem): Boolean {
        val pronoun = SAME_VERB_PRONOUN.find(newer.title) ?: return false
        val sameVerb = older.title.substringBefore(' ').equals(pronoun.groupValues[1], ignoreCase = true)
        return sameVerb && names(newer.title).all { it in names(older.title) }
    }

    private fun merge(older: ParsedItem, newer: ParsedItem): ParsedItem {
        val olderWords = TitleWords.of(older.title)
        val newerWords = TitleWords.of(newer.title)
        val newerIsFuller = newerWords.size > olderWords.size && newerWords.containsAll(olderWords) && wordCount(newer.title) <= MAX_TITLE_WORDS
        val (title, otherTitle) = if (newerIsFuller) newer.title to older.title else older.title to newer.title
        val adds = (TitleWords.of(otherTitle) - TitleWords.of(title) - REFERRING_WORDS).isNotEmpty()
        return absorb(older.copy(title = title), newer, said = otherTitle.takeIf { adds })
    }

    /** Takes in what [other] adds: what was said as detail, and its deadline if this item has none. */
    private fun absorb(item: ParsedItem, other: ParsedItem, said: String? = other.title): ParsedItem {
        val deadline = when {
            item.deadline == null -> other.deadline
            item.deadline.date == other.deadline?.date -> item.deadline.copy(time = item.deadline.time ?: other.deadline.time)
            else -> item.deadline
        }
        val detail = listOfNotNull(item.detail, said, other.detail).distinct().joinToString(". ").ifEmpty { null }
        val isTask = item.kind == ItemKind.TASK || other.kind == ItemKind.TASK || deadline != null
        return item.copy(kind = if (isTask) ItemKind.TASK else ItemKind.IDEA, detail = detail, deadline = deadline)
    }

    private fun attachStatements(groups: List<Group>): List<Group> {
        val result = groups.toMutableList()
        val actions = groups.indices.filterNot { isStatement(groups[it].item) }
        val attached = mutableSetOf<Int>()
        for (index in groups.indices - actions.toSet()) {
            val target = targetFor(index, result.map { it.item }, actions) ?: continue
            result[target] = combine(result[target], groups[index], absorb(result[target].item, groups[index].item))
            attached += index
        }
        return result.filterIndexed { index, _ -> index !in attached }
    }

    private fun targetFor(index: Int, items: List<ParsedItem>, actions: List<Int>): Int? {
        val statement = items[index]
        val closest = closestItem(statement, actions.map { items[it] })?.let { actions[it] }
        if (closest != null) return closest.takeIf { canTakeDeadline(items[it], statement) }
        val previous = actions.lastOrNull { it < index } ?: return null
        // "The launch is" and "is the last day" are what's left of a sentence once its date is out, so that date is all it adds.
        if (ONLY_A_DATE_LEFT.containsMatchIn(statement.title)) return previous.takeIf { items[it].deadline?.date in listOf(null, statement.deadline?.date) }
        // "His number is in my phone" is about whatever was said just before it.
        return previous.takeIf { statement.deadline == null && POINTS_BACK.containsMatchIn(statement.title) }
    }

    /**
     * A sentence with its own date only joins an item if it's plainly about the same thing. If not,
     * the date lands on the wrong task and the thing said disappears: "the meeting with Ravi is on
     * Friday" and "call Ravi about the contract".
     */
    private fun canTakeDeadline(item: ParsedItem, statement: ParsedItem): Boolean {
        val date = statement.deadline?.date ?: return true
        return when (item.deadline?.date) {
            date -> true
            null -> TitleWords.of(item.title).containsAll(subjectWords(statement.title))
            else -> false
        }
    }

    private fun closestItem(sentence: ParsedItem, items: List<ParsedItem>): Int? {
        val words = subjectWords(sentence.title)
        val overlaps = items.map { (TitleWords.of(it.title) intersect words).size }
        val best = overlaps.maxOrNull() ?: return null
        return if (best > 0) overlaps.indexOf(best) else null
    }

    private fun subjectWords(title: String): Set<String> = TitleWords.of(title) - SAYS_NOTHING

    /** Says something about a thing rather than what to do, like "the bill is due on the 10th". */
    private fun isStatement(item: ParsedItem): Boolean {
        if (POSSIBILITY.containsMatchIn(item.title) || OWN_JOB.containsMatchIn(item.title)) return false
        val words = item.title.lowercase().split(' ').map { it.trim(',', '.') }
        return words.first() in STATEMENT_START || words.getOrNull(1) in STATEMENT_SECOND_WORD
    }

    private fun names(title: String): Set<String> =
        title.split(' ').drop(1).filter { it.firstOrNull()?.isUpperCase() == true }.map { it.trim(',', '.') }.toSet()

    private fun wordCount(title: String) = title.trim().split(' ').size

    private const val MAX_TITLE_WORDS = 8

    private val IC = RegexOption.IGNORE_CASE
    private val SAME_VERB_PRONOUN = Regex("""^([\p{L}']+)\s+(?:(?:to|with|for|on)\s+)?(?:it|him|her|them|whichever)\b""", IC)
    private val POINTS_BACK = Regex("""^(?:it|it's|its|he|she|they|his|her|their|that|that's|those|these|this)\b|\b(?:it|him|them)\b""", IC)
    private val ONLY_A_DATE_LEFT = Regex("""\b(?:is|are|was|were|be|due)$|^(?:is|are|was)\b""", IC)
    private val POSSIBILITY = Regex("""\b(?:could|might|maybe|what if|how about|would be|idea)\b""", IC)

    // Leading wording like this is already gone from a title, so here it starts a later clause: "so I should ask him".
    private val OWN_JOB = Regex("""\b(?:i|we)\s+(?:\w+\s+)?(?:have|need|must|should|will)\b|\b(?:i'll|we'll)\b""", IC)

    private val STATEMENT_START = setOf(
        "the", "a", "an", "this", "that", "that's", "these", "those", "it", "it's", "its", "he", "she", "they",
        "his", "her", "their", "we", "i", "there", "there's", "last", "ensure", "is", "are", "was",
    )
    private val STATEMENT_SECOND_WORD = setOf(
        "is", "are", "was", "were", "will", "has", "have", "had", "wants", "said", "says", "can", "needs", "closes", "opens",
    )
    private val REFERRING_WORDS = TitleWords.of("it him them whichever")
    private val SAYS_NOTHING = TitleWords.of(
        "is are was were will has have had wants needs can should would must may be so we he she they it it's " +
            "there there's thing last time due not ensure ready",
    )
}
