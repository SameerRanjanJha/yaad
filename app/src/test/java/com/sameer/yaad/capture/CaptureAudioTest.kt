package com.sameer.yaad.capture

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CaptureAudioTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `wav round trip keeps samples and writes a valid header`() {
        val file = folder.newFile("note.wav")
        WavWriter(file).use { it.write(shortArrayOf(0, 16384, -16384, 32767), 4) }

        val samples = WavFile.readSamples(file)
        assertArrayEquals(floatArrayOf(0f, 0.5f, -0.5f, 32767 / 32768f), samples, 0.0001f)

        val bytes = file.readBytes()
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals("WAVE", String(bytes, 8, 4))
        assertEquals(44 + 8, bytes.size)
    }

    @Test fun `only the samples passed by count are written`() {
        val file = folder.newFile("note.wav")
        WavWriter(file).use { it.write(shortArrayOf(1, 2, 3, 4), 2) }
        assertEquals(2, WavFile.readSamples(file).size)
    }

    @Test fun `a recording cut off before its header was written is still readable`() {
        val file = folder.newFile("note.wav")
        file.writeBytes(ByteArray(44) + byteArrayOf(0, 64, 0, 64))
        assertEquals(2, WavFile.readSamples(file).size)
    }

    @Test fun `duration comes from the file size`() {
        val file = folder.newFile("note.wav")
        WavWriter(file).use { it.write(ShortArray(SAMPLE_RATE), SAMPLE_RATE) }
        assertEquals(1000, WavFile.durationMs(file))
    }

    @Test fun `silence after speech stops the recording`() {
        val autoStop = AutoStop(silenceAfterSpeechMs = 1000, silenceWithoutSpeechMs = 5000)
        assertFalse(autoStop.shouldStop(0.1f, 100))
        repeat(9) { assertFalse(autoStop.shouldStop(0.001f, 100)) }
        assertTrue(autoStop.shouldStop(0.001f, 100))
    }

    @Test fun `silence with no speech at all waits longer`() {
        val autoStop = AutoStop(silenceAfterSpeechMs = 1000, silenceWithoutSpeechMs = 5000)
        repeat(49) { assertFalse(autoStop.shouldStop(0.001f, 100)) }
        assertTrue(autoStop.shouldStop(0.001f, 100))
    }

    @Test fun `pauses shorter than the limit don't stop the recording`() {
        val autoStop = AutoStop(silenceAfterSpeechMs = 1000, silenceWithoutSpeechMs = 5000)
        repeat(5) {
            assertFalse(autoStop.shouldStop(0.1f, 100))
            repeat(8) { assertFalse(autoStop.shouldStop(0.001f, 100)) }
        }
    }

    @Test fun `steady background noise stops counting as speech`() {
        val autoStop = AutoStop(silenceAfterSpeechMs = 1000, silenceWithoutSpeechMs = 2000)
        val chunksUntilStop = generateSequence(1) { it + 1 }.first { autoStop.shouldStop(0.02f, 100) }
        assertTrue("stopped after $chunksUntilStop chunks", chunksUntilStop in 10..100)
    }

    @Test fun `speech from the very first moment counts`() {
        val autoStop = AutoStop(silenceAfterSpeechMs = 1000, silenceWithoutSpeechMs = 5000)
        assertFalse(autoStop.shouldStop(0.1f, 100))
        repeat(9) { assertFalse(autoStop.shouldStop(0.001f, 100)) }
        assertTrue(autoStop.shouldStop(0.001f, 100))
    }

    @Test fun `recordings have a hard length limit`() {
        val autoStop = AutoStop(maxRecordingMs = 1000)
        repeat(9) { assertFalse(autoStop.shouldStop(0.1f, 100)) }
        assertTrue(autoStop.shouldStop(0.1f, 100))
    }

    @Test fun `speech gate rejects silence and accepts sustained sound`() {
        assertFalse(SpeechGate.hasSpeech(FloatArray(SAMPLE_RATE)))
        assertFalse(SpeechGate.hasSpeech(FloatArray(SAMPLE_RATE) { if (it < 200) 0.5f else 0f }))
        assertTrue(SpeechGate.hasSpeech(FloatArray(SAMPLE_RATE) { 0.05f }))
    }

    @Test fun `the rumble filter takes out hum and keeps the voice range`() {
        fun tone(hz: Double) = FloatArray(SAMPLE_RATE) { kotlin.math.sin(2 * kotlin.math.PI * hz * it / SAMPLE_RATE).toFloat() }
        fun level(samples: FloatArray) = rms(ShortArray(samples.size) { (samples[it] * 32767).toInt().toShort() }.copyOfRange(SAMPLE_RATE / 2, SAMPLE_RATE), SAMPLE_RATE / 2)
        assertTrue(level(RumbleFilter.apply(tone(50.0))) < level(tone(50.0)) * 0.3f)
        assertTrue(level(RumbleFilter.apply(tone(1000.0))) > level(tone(1000.0)) * 0.98f)
    }

    private fun voice(seconds: Double, level: Float) = FloatArray((seconds * SAMPLE_RATE).toInt()) { if (it % 2 == 0) level else -level }

    private fun FloatArray.countAt(level: Float) = count { kotlin.math.abs(it) == level }

    @Test fun `someone talking far quieter than the speaker is dropped`() {
        val audio = voice(2.0, 0.3f) + voice(1.0, 0f) + voice(2.0, 0.02f)
        val kept = ForegroundSpeech.keep(audio, floatArrayOf(0f, 2f, 3f, 5f))!!
        assertEquals(2 * SAMPLE_RATE, kept.countAt(0.3f))
        assertEquals(0, kept.countAt(0.02f))
    }

    @Test fun `background talk straight after the speaker stops is cut off`() {
        val audio = voice(2.0, 0.3f) + voice(3.0, 0.02f)
        val kept = ForegroundSpeech.keep(audio, floatArrayOf(0f, 5f))!!
        assertEquals(2 * SAMPLE_RATE, kept.countAt(0.3f))
        assertTrue(kept.countAt(0.02f) <= SAMPLE_RATE / 2)
    }

    @Test fun `the speaker's own quieter sentences are kept`() {
        val audio = voice(2.0, 0.3f) + voice(2.0, 0.1f)
        val kept = ForegroundSpeech.keep(audio, floatArrayOf(0f, 4f))!!
        assertEquals(2 * SAMPLE_RATE, kept.countAt(0.1f))
    }

    @Test fun `a short loud sound doesn't set the speaker's level`() {
        val audio = voice(0.3, 0.9f) + voice(2.0, 0.1f)
        val kept = ForegroundSpeech.keep(audio, floatArrayOf(0f, 2.3f))!!
        assertEquals(2 * SAMPLE_RATE, kept.countAt(0.1f))
    }

    @Test fun `noise outside the stretches of speech is dropped and the end is padded with silence`() {
        val audio = voice(1.0, 0.05f) + voice(2.0, 0.3f) + voice(1.0, 0.05f)
        val kept = ForegroundSpeech.keep(audio, floatArrayOf(1f, 3f))!!
        assertTrue(kept.countAt(0.05f) <= SAMPLE_RATE)
        assertTrue(kept.takeLast(3 * SAMPLE_RATE).all { it == 0f })
    }

    @Test fun `no speech found gives nothing to keep`() {
        assertEquals(null, ForegroundSpeech.keep(voice(1.0, 0.1f), FloatArray(0)))
    }
}
