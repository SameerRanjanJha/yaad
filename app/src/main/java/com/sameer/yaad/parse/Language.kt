package com.sameer.yaad.parse

/**
 * The English phrasing rules: what reads as a task, what reads as an idea, and what is only
 * preamble. Everything the speaker mentions is theirs to do, so wording addressed to the app
 * ("set a reminder to...") is removed, leaving just the thing they have to do.
 */
internal object Language {
    private val IC = RegexOption.IGNORE_CASE
    private val WHITESPACE = Regex("""\s+""")

    /** Concrete errands: saying one is already a to-do. */
    private val ERRAND_VERBS = wordSet(
        """
        apply arrange ask attend back block book bring buy call cancel charge check clean clear close collect
        complete confirm contact deliver delete deploy discuss do download draft drop email fetch file fill find
        finish fix follow forward get give go inform install invite join look mail meet message move open order
        organize pack pay phone pick ping post prepare print publish purchase push put read recharge register
        remove renew repair reply reschedule reserve respond return review ring run scan schedule see send share
        sign sort speak submit take talk tell test text transfer update upload verify visit wash watch whatsapp
        withdraw
        """,
    )

    /** Verbs for making something: "build an app" is a possibility, "write the report" is a job. */
    private val PROJECT_VERBS = wordSet(
        """
        add build change come create design develop explore improve launch learn make plan redesign research
        rewrite set start stop study try write
        """,
    )
    private val DEFINITE_WORDS = setOf("the", "my", "our", "your", "this", "that", "these", "those", "his", "her", "their")
    private val VERB_PARTICLES = setOf("up", "out", "off", "on", "in")
    private val OBJECT_PRONOUNS = setOf("him", "her", "them", "it", "me", "us")

    private const val EVENT_NOUNS = "meeting|call|appointment|interview|exam|flight|train|presentation|demo|session|class"

    private val INTENT_START = Regex(
        """^(?:i|we)\s+(?:really\s+|also\s+|still\s+|just\s+)*(?:need|have|must|should|want|will|shall|ought|got|think)\b|""" +
            """^(?:i'll|we'll|i'm going to|i am going to|i'm gonna|we're going to|let's)\b|""" +
            """^(?:need|have|got)\s+to\b|^(?:remember|don't forget|do not forget|remind me|make sure|let me)\b""",
        IC,
    )

    private val STRONG_TASK = Regex(
        """\b(?:need|needs|have|has|got)\s+to\b|\bmust\b|\bremember to\b|\bdon't forget\b|\bdo not forget\b|\bremind me\b|\bmake sure\b|""" +
            """\b(?:set|add|create|make|put)\s+(?:(?:a|an|the|me a|my)\s+)?(?:new\s+)?(?:reminder|task|to-?do)\b|""" +
            """\bto (?:my|the) (?:\w+\s+)?(?:list|tasks?|to-?dos?)\b|\bmy (?:task|job|to-?do) (?:for \w+ )?is\b|\bone task is\b|^(?:task|to ?do)\b""",
        IC,
    )
    private val COMMITMENT = Regex(
        """\bshould\b|\bi'll\b|\bwe'll\b|\bi will\b|\bwe will\b|\bgoing to\b|\bgonna\b|\bgotta\b|\blet me\b|\bpending\b""",
        IC,
    )
    private val EVENT_START = Regex("""^(?:$EVENT_NOUNS)\b""", IC)

    private val EXPLICIT_IDEA = Regex(
        """^(?:(?:so|okay|ok|also|and|but|oh|another|one more|new|quick)[,]?\s+)*(?:an?\s+|one\s+|my\s+)?(?:new\s+|quick\s+|good\s+|great\s+)?idea\b""",
        IC,
    )
    private val IDEA_SIGNAL = Regex(
        """\bidea\b|\bwhat if\b|\bhow about\b|\bmaybe\b(?!\s+(?:today|tomorrow|next|this|on|by|at)\b)|\b(?:we|i|you) (?:could|might|can)\b|\bwhy don't (?:we|i)\b|\blet's\b|""" +
            """\bwould be (?:\w+ )?(?:cool|nice|great|good|interesting|fun|awesome|useful|amazing|helpful|better)\b|""" +
            """\bwouldn't it be\b|\bi was (?:just )?thinking\b|\bi'?m thinking\b|\bthinking (?:about|of)\b|\bi wonder\b|\bi think\b|\bi feel\b|\bi believe\b|""" +
            """\bi wish\b|\bi'd love\b|\bwould love to\b|\bmight be (?:\w+ )?(?:nice|worth|good|interesting)\b|\bconcept\b|\bbrainstorm\b|\bsome ?day\b""",
        IC,
    )

    private val BARE_IDEA_MARKER = Regex(
        """^(?:(?:so|okay|ok|and|also|oh|but)[,]?\s+)*""" +
            """(?:i was (?:just )?thinking|""" +
            """(?:i\s+(?:have|had|got)\s+|i've got\s+|here's\s+|here is\s+|there's\s+|there is\s+)?""" +
            """(?:another\s+|one more\s+|one\s+|an?\s+)?(?:new\s+|quick\s+|small\s+|good\s+|great\s+)?idea(?:\s+is)?)[.:,]?$""",
        IC,
    )
    private val NEW_IDEA = Regex("""\b(?:another|one more|second|new|different)\s+(?:\w+\s+)?idea\b""", IC)

    /** "There are two things": it introduces the note without saying anything to keep. */
    private val ANNOUNCEMENT = Regex(
        """^(?:(?:okay|ok|so|right|alright|well|hey)[,]?\s+)*(?:there are|there're|i have|i've got|i got|got|here are)\s+""" +
            """(?:a few|a couple of|a couple|two|three|four|five|six|some|few|many|several|\d+)\s+(?:\w+\s+)?(?:things|items|tasks|points|ideas)\b.*$""",
        IC,
    )

    private val CONTINUATION = Regex(
        """^(?:it|it's|this|that|that's|these|those|because|since|so that|which|they|he|she|the reason|as)\b""",
        IC,
    )
    private val DUE_WORDING_ONLY = Regex(
        """^(?:(?:it|this|that|these|those)(?:'s|\s+is|\s+are)?)?\s*(?:all\s+)?(?:due|needed|needs to be done)?$""",
        IC,
    )

    private val QUESTION_TO_APP = Regex("""^(?:can|could|would|will) you(?:\s+please)?\s+""", IC)
    private val ADD_TO_LIST = Regex("""^add\s+(.+?)\s+to\s+(?:my|the)\s+(?:\w+\s+)?(?:list|tasks?|to-?dos?)$""", IC)

    private val PREAMBLE = listOf(
        Regex("""^(?:okay|ok|so|right|alright|all right|well|hey|hi|hello|and|but|then|also|please|yeah|yes|now|oh|actually|basically)[,:]?\s+""", IC),
        Regex("""^(?:hey\s+)?yaad[,:]\s+""", IC),
        Regex("""^(?:first|firstly|second|secondly|third|thirdly|next|finally|lastly)[,:]\s*""", IC),
        Regex("""^(?:firstly|secondly|thirdly|finally|lastly)\s+""", IC),
        Regex("""^(?:first|second|third|next)\s+(?=(?:i|we)\b)""", IC),
        Regex("""^(?:one more thing|another thing)[,:]?\s+""", IC),

        // Wording addressed to the app.
        QUESTION_TO_APP,
        Regex("""^(?:set|add|create|make|put|save)\s+(?:(?:a|an|the|me a|my)\s+)?(?:new\s+)?(?:reminder|task|to-?do|note|item|entry)(?:\s+for me)?(?:\s+(?:to|for|that|about|saying)\b|[:,])\s*""", IC),
        Regex("""^(?:note|write|jot|put|take)\s+(?:this\s+|it\s+)?down(?:\s+that)?[:,]?\s+""", IC),
        Regex("""^(?:note|remember|record)\s+that\s+""", IC),
        Regex("""^you\s+(?:(?:need|have|got)\s+to|should|must)\s+(?=remind\b)""", IC),
        Regex("""^remind me\s+(?:to|about|that)\s+""", IC),

        // Wording that says it's the speaker's job.
        Regex("""^(?:i|we)\s+(?:really\s+|also\s+|still\s+|just\s+)*(?:need|have|got|ought|want)\s+to\s+""", IC),
        Regex("""^(?:need|have|got)\s+to\s+""", IC),
        Regex("""^(?:i|we)\s+(?:really\s+|also\s+)?(?:must|should|gotta|will|shall)\s+""", IC),
        Regex("""^(?:i'll|we'll|i'm going to|i am going to|we're going to|i'm gonna|gonna)\s+""", IC),
        Regex("""^let me\s+""", IC),
        Regex("""^let's\s+""", IC),
        Regex("""^(?:please\s+)?(?:remember|don't forget|do not forget)\s+to\s+""", IC),
        Regex("""^make sure\s+(?:to\s+|that\s+i\s+|i\s+)""", IC),
        Regex("""^(?:task|to ?do)[:,]\s*""", IC),
        Regex("""^my\s+(?:task|job|to-?do)\s+(?:for\s+\w+\s+)?is\s+(?:to\s+)?""", IC),
        Regex("""^(?:one|another|the|a)\s+task\s+is\s+(?:to\s+)?""", IC),
        Regex("""^(?:i have|i've got|i got|there's|there is)\s+an?\s+(?=(?:$EVENT_NOUNS)\b)""", IC),

        // Wording that announces an idea.
        Regex("""^(?:i\s+(?:have|had|got)\s+|i've got\s+|here's\s+|here is\s+|there's\s+|there is\s+|another\s+|one more\s+)?(?:an?\s+)?(?:new\s+|quick\s+|small\s+|good\s+|great\s+)?idea(?:\s+is(?:\s+to)?|\s+that)?[:,]\s*""", IC),
        Regex("""^(?:i\s+(?:have|had|got)\s+|i've got\s+|here's\s+|here is\s+|there's\s+|there is\s+)(?:an?\s+)?(?:new\s+|quick\s+|small\s+|good\s+|great\s+)?(?=idea\s+(?:for|about|on)\b)""", IC),
        Regex("""^(?:an?\s+|one\s+|my\s+|another\s+|the\s+)?(?:new\s+|quick\s+|small\s+|good\s+|great\s+)?idea\s+(?:is|would be|could be|might be)(?:\s+to|\s+that)?\s+""", IC),
        Regex("""^(?:i was (?:just )?thinking|i'm thinking|i am thinking|thinking)(?:\s+(?:that|of|about))?\s+""", IC),
        Regex("""^(?:i think|i feel|i believe)(?:\s+that)?\s+""", IC),
    )
    private val TRAILING_PUNCTUATION = Regex("""[\s,.;:!]+$""")
    private val DANGLING_JOINER = Regex("""[\s,]+(?:and|then|also|maybe)$""", IC)

    fun readsAsIdea(text: String): Boolean {
        if (EXPLICIT_IDEA.containsMatchIn(text)) return true
        return !STRONG_TASK.containsMatchIn(text) && IDEA_SIGNAL.containsMatchIn(text)
    }

    fun readsAsTask(text: String): Boolean {
        if (STRONG_TASK.containsMatchIn(text)) return true
        val title = cleanTitle(text)
        val words = title.lowercase().split(WHITESPACE).map { it.trim(',', '.', ':') }
        val verb = words.first()
        return when {
            verb in ERRAND_VERBS -> true
            verb in PROJECT_VERBS -> objectIsDefinite(words)
            EVENT_START.containsMatchIn(title) -> true
            else -> COMMITMENT.containsMatchIn(text)
        }
    }

    fun startsNewClause(text: String): Boolean {
        val trimmed = text.trimStart()
        if (INTENT_START.containsMatchIn(trimmed)) return true
        return startsWithVerb(trimmed) && !objectIsPronoun(trimmed)
    }

    fun isBareIdeaMarker(text: String): Boolean = BARE_IDEA_MARKER.matches(text.trim())

    fun isAnnouncement(text: String): Boolean = ANNOUNCEMENT.matches(text.trim())

    fun namesNewIdea(text: String): Boolean = NEW_IDEA.containsMatchIn(text)

    /** True when this clause only adds to what came before ("It's about the loan"). */
    fun isContinuation(text: String): Boolean =
        CONTINUATION.containsMatchIn(text.trimStart()) && !STRONG_TASK.containsMatchIn(text) && !COMMITMENT.containsMatchIn(text)

    fun isOnlyDueWording(text: String): Boolean = DUE_WORDING_ONLY.matches(text.trim())

    fun cleanTitle(text: String): String {
        var title = text.trim()
        var questionToApp = false
        var previous: String
        do {
            previous = title
            for (pattern in PREAMBLE) {
                if (pattern === QUESTION_TO_APP && pattern.containsMatchIn(title)) questionToApp = true
                title = title.replace(pattern, "")
            }
            title = title.replace(TRAILING_PUNCTUATION, "").replace(DANGLING_JOINER, "").trim()
            title = title.replace(ADD_TO_LIST, "$1")
        } while (title != previous)
        if (questionToApp) title = title.trimEnd('?', ' ')
        return titleCase(title)
    }

    fun titleCase(text: String): String = text.replaceFirstChar { it.uppercase() }

    private fun wordSet(words: String): Set<String> = words.trim().split(WHITESPACE).toSet()

    private fun startsWithVerb(text: String): Boolean {
        val first = text.trim().substringBefore(' ').lowercase().trim(',', '.', ':')
        return first in ERRAND_VERBS || first in PROJECT_VERBS
    }

    private fun objectIsDefinite(words: List<String>): Boolean {
        val afterVerb = words.drop(1).dropWhile { it in VERB_PARTICLES }
        return afterVerb.firstOrNull() in DEFINITE_WORDS
    }

    private fun objectIsPronoun(text: String): Boolean {
        val words = text.trim().split(WHITESPACE)
        return words.size > 1 && words[1].lowercase().trim(',', '.') in OBJECT_PRONOUNS
    }
}
