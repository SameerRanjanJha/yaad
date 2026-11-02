package com.sameer.yaad.parse

/** What a title says, ignoring small words, word order and everyday ways of saying the same thing. */
internal object TitleWords {
    fun of(title: String): Set<String> =
        WORD.findAll(title.lowercase().replace('’', '\''))
            .map { it.value }
            .filter { it !in SMALL_WORDS && (it.length > 1 || it[0].isDigit()) }
            .mapIndexed { index, word ->
                SAME_PERSON[word] ?: SAME_ACTION[word]?.takeIf { index == 0 } ?: stem(word)
            }
            .toSet()

    /** A longer title with one of these says something the shorter one doesn't: "call Ravi" and "don't call Ravi". */
    fun changeTheMeaning(words: Set<String>): Boolean = words.any { it in CHANGES_THE_MEANING }

    /** Whole words apart from endings, because a shared start could be two people: Priyanka and Priyansh. */
    private fun stem(word: String): String = when {
        '\'' in word || word[0].isDigit() -> word
        word.length > 4 && word.endsWith("ies") -> word.dropLast(3) + "y"
        word.length > 5 && word.endsWith("ing") -> word.dropLast(3)
        word.length > 4 && word.endsWith("ed") -> word.dropLast(2)
        word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
        else -> word
    }.removeSuffix("e")

    private val WORD = Regex("""[\p{L}\p{N}']+""")

    private val SMALL_WORDS = setOf(
        "a", "an", "the", "my", "our", "your", "his", "her", "their", "this", "that", "these", "those",
        "to", "for", "of", "with", "about", "at", "in", "and", "also", "just", "some", "please",
    )

    private val SAME_PERSON = mapOf(
        "mum" to "mom", "mummy" to "mom", "mommy" to "mom", "mother" to "mom", "maa" to "mom",
        "father" to "dad", "papa" to "dad", "daddy" to "dad",
    )

    /** Only for a title's first word, where these are actions: "buy a phone" and "buy a ring" are different. */
    private val SAME_ACTION = mapOf(
        "phone" to "call", "ring" to "call",
        "mail" to "email",
        "purchase" to "buy", "get" to "buy",
        "whatsapp" to "text", "message" to "text", "ping" to "text",
        "repair" to "fix",
        "schedule" to "book",
        "complete" to "finish",
        "speak" to "talk",
    )

    private val CHANGES_THE_MEANING = setOf("not", "don't", "dont", "never", "no", "cancel", "stop", "skip", "avoid", "without", "from", "again")
}
