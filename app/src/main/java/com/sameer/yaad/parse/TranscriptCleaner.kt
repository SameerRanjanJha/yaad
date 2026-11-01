package com.sameer.yaad.parse

/** Removes what Whisper emits for non-speech, so background noise never becomes a task. */
object TranscriptCleaner {
    private val NON_SPEECH_MARKUP = Regex("""\[[^\]]*]|\([^)]*\)|\*[^*]*\*|♪+""")
    private val WHITESPACE = Regex("""\s+""")

    // Whisper invents these when it is fed near silence.
    private val SILENCE_HALLUCINATIONS = setOf(
        "thank you", "thanks", "thanks for watching", "thank you for watching",
        "bye", "you", "okay", "ok", "so", "the end",
    )

    fun clean(raw: String): String {
        val text = raw.replace(NON_SPEECH_MARKUP, " ").replace(WHITESPACE, " ").trim()
        val bare = text.trim('.', '!', '?', ',', ' ').lowercase()
        return if (bare in SILENCE_HALLUCINATIONS) "" else text
    }
}
