package com.sameer.yaad.parse

/** Splits one spoken sentence wherever the speaker moved on to a separate thought. */
internal object ClauseSplitter {
    private val LIST_MARKER = Regex(
        """^(?:number\s+)?(?:one|two|three|four|five|first|second|third|fourth|fifth|\d)[,.:)]\s+""",
        RegexOption.IGNORE_CASE,
    )

    // "also" only separates after a comma or "and": in "I also need to" it belongs to the clause.
    private val HARD_SEPARATOR = Regex(
        """(?:,\s*|\s+and\s+|,\s*and\s+)(?:also|additionally|apart from that|besides that)[,:]?\s+|""" +
            """,?\s+(?:and\s+)?(?:after that|another thing|one more thing|oh and)[,:]?\s+""",
        RegexOption.IGNORE_CASE,
    )
    // An idea spoken in the same breath as something else starts its own clause.
    private val IDEA_BOUNDARY = Regex(
        """(?:,\s*(?:and\s+|but\s+|oh and\s+)?|\s+(?:and|but|oh and)\s+)(?=""" +
            """(?:(?:i\s+(?:have|had|got)|i've got|there's|there is|here's)\s+)?(?:an?\s+|one\s+|another\s+|my\s+)?(?:new\s+|quick\s+|small\s+|good\s+|great\s+)?idea\b|""" +
            """maybe\b(?!\s+(?:today|tomorrow|next|this|on|by|at)\b)|what if\b|how about\b|(?:we|i) could\b|it would be\b)""",
        RegexOption.IGNORE_CASE,
    )
    private val THEN_SEPARATOR = Regex(""",?\s+(?:and\s+)?then\s+""", RegexOption.IGNORE_CASE)
    private val SOFT_SEPARATOR = Regex(""",\s*(?:and\s+)?|\s+and\s+""", RegexOption.IGNORE_CASE)
    private val SEPARATOR_WITH_AND = Regex("""\band\b""", RegexOption.IGNORE_CASE)
    private val COLON_AFTER_LABEL = Regex("""^((?:[\w']+\s+){0,4}(?:idea|task|to ?do)):\s+""", RegexOption.IGNORE_CASE)

    fun split(sentence: String): List<Clause> {
        val body = sentence.trim().replace(LIST_MARKER, "").trimEnd('.', ';', '!')
        val labelled = COLON_AFTER_LABEL.find(body)
        if (labelled != null && Language.isBareIdeaMarker(labelled.groupValues[1])) {
            val rest = body.substring(labelled.range.last + 1)
            return listOf(Clause(labelled.groupValues[1], false)) + splitBody(rest)
        }
        return splitBody(body)
    }

    private fun splitBody(body: String): List<Clause> =
        splitOnHardSeparators(body).flatMap(::splitBeforeIdeas).flatMapIndexed { index, part ->
            splitOnThen(part).flatMap(::splitSoft)
                .mapIndexed { innerIndex, clause ->
                    if (index > 0 && innerIndex == 0) clause.copy(joinedByAnd = false) else clause
                }
        }.filter { it.text.isNotBlank() }

    private fun splitOnHardSeparators(text: String): List<String> = text.split(HARD_SEPARATOR)

    private fun splitBeforeIdeas(text: String): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        for (match in IDEA_BOUNDARY.findAll(text)) {
            val left = text.substring(start, match.range.first).trim()
            if (left.contains(' ')) {
                parts += left
                start = match.range.last + 1
            }
        }
        parts += text.substring(start)
        return parts
    }

    private fun splitOnThen(text: String): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        for (match in THEN_SEPARATOR.findAll(text)) {
            val right = text.substring(match.range.last + 1)
            if (Language.startsNewClause(right)) {
                parts += text.substring(start, match.range.first)
                start = match.range.last + 1
            }
        }
        parts += text.substring(start)
        return parts
    }

    private fun splitSoft(text: String): List<Clause> {
        // Inside an idea, "and" links parts of the same thought rather than separate to-dos.
        if (Language.readsAsIdea(text)) return listOf(Clause(text.trim(), false))

        val clauses = mutableListOf<Clause>()
        var start = 0
        var joinedByAnd = false
        for (match in SOFT_SEPARATOR.findAll(text)) {
            val left = text.substring(start, match.range.first)
            val right = text.substring(match.range.last + 1)
            if (left.trim().contains(' ') && Language.startsNewClause(right)) {
                clauses += Clause(left.trim(), joinedByAnd)
                joinedByAnd = SEPARATOR_WITH_AND.containsMatchIn(match.value)
                start = match.range.last + 1
            }
        }
        clauses += Clause(text.substring(start).trim(), joinedByAnd)
        return clauses
    }
}
