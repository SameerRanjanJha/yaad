package com.sameer.yaad.capture

import kotlin.math.sqrt

fun rms(samples: ShortArray, count: Int): Float {
    if (count <= 0) return 0f
    var sum = 0.0
    for (i in 0 until count) {
        val value = samples[i] / 32768.0
        sum += value * value
    }
    return sqrt(sum / count).toFloat()
}

/**
 * Ends a recording on its own once the speaker has clearly finished, or never started.
 * Silence only ever stops the recording. The audio is still kept.
 */
class AutoStop(
    private val silenceAfterSpeechMs: Long = 8_000,
    private val silenceWithoutSpeechMs: Long = 20_000,
    private val maxRecordingMs: Long = 15 * 60_000,
) {
    private var totalMs = 0L
    private var quietMs = 0L
    private var heardSpeech = false

    // Starts low because people often talk from the first instant, and creeps up so that a
    // noisy room stops counting as speech after a few seconds.
    private var noiseFloor = INITIAL_FLOOR

    fun shouldStop(level: Float, chunkMs: Long): Boolean {
        totalMs += chunkMs
        noiseFloor = if (level < noiseFloor) level.coerceAtLeast(MIN_FLOOR) else noiseFloor * (1 + FLOOR_RISE_PER_MS * chunkMs)
        if (level > maxOf(MIN_SPEECH_LEVEL, noiseFloor * FLOOR_MULTIPLE)) {
            heardSpeech = true
            quietMs = 0
        } else {
            quietMs += chunkMs
        }
        val quietLimit = if (heardSpeech) silenceAfterSpeechMs else silenceWithoutSpeechMs
        return quietMs >= quietLimit || totalMs >= maxRecordingMs
    }

    private companion object {
        const val INITIAL_FLOOR = 0.003f
        const val MIN_FLOOR = 0.0005f
        const val FLOOR_RISE_PER_MS = 0.0002f
        const val MIN_SPEECH_LEVEL = 0.012f
        const val FLOOR_MULTIPLE = 2.5f
    }
}

object SpeechGate {
    private const val WINDOW_SAMPLES = SAMPLE_RATE / 10
    private const val SPEECH_LEVEL = 0.008
    private const val MIN_SPEECH_WINDOWS = 3

    /** Whisper invents text from pure silence, so it only runs if someone may have spoken. */
    fun hasSpeech(samples: FloatArray): Boolean {
        var loudWindows = 0
        var start = 0
        while (start < samples.size) {
            val end = minOf(start + WINDOW_SAMPLES, samples.size)
            var sum = 0.0
            for (i in start until end) sum += samples[i] * samples[i].toDouble()
            if (sqrt(sum / (end - start)) > SPEECH_LEVEL && ++loudWindows >= MIN_SPEECH_WINDOWS) return true
            start = end
        }
        return false
    }
}
