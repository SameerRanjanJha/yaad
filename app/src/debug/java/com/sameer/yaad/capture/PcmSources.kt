package com.sameer.yaad.capture

import android.content.Context
import java.io.File

/**
 * Debug builds play `test-audio.wav` from the app's external files folder instead of the mic,
 * so the whole capture flow can be tested on an emulator with known speech.
 */
object PcmSources {
    fun open(context: Context): PcmSource {
        val testAudio = File(context.getExternalFilesDir(null), "test-audio.wav")
        return if (testAudio.exists()) FilePcmSource(testAudio) else MicPcmSource()
    }
}

private class FilePcmSource(file: File) : PcmSource {
    private val samples = WavFile.readSamples(file)
    private var position = 0

    override fun read(buffer: ShortArray): Int {
        // Paces itself like a real microphone. Past the end it reports silence so auto-stop runs.
        Thread.sleep(buffer.size * 1000L / SAMPLE_RATE)
        for (i in buffer.indices) {
            buffer[i] = if (position < samples.size) (samples[position++] * 32767).toInt().toShort() else 0
        }
        return buffer.size
    }

    override fun close() = Unit
}
