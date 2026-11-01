package com.sameer.yaad.parse

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptCleanerTest {
    @Test fun `non-speech markup is removed`() {
        assertEquals("", TranscriptCleaner.clean("[BLANK_AUDIO]"))
        assertEquals("", TranscriptCleaner.clean(" (wind blowing) "))
        assertEquals("", TranscriptCleaner.clean("♪♪"))
        assertEquals("Call the bank.", TranscriptCleaner.clean("[MUSIC] Call the bank. (coughs)"))
    }

    @Test fun `silence hallucinations are dropped`() {
        assertEquals("", TranscriptCleaner.clean("Thank you."))
        assertEquals("", TranscriptCleaner.clean(" Thanks for watching!"))
    }

    @Test fun `a real sentence containing those words is kept`() {
        assertEquals("Send a thank you note.", TranscriptCleaner.clean("Send a thank you note."))
    }
}
