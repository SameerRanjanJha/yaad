package com.sameer.yaad.capture

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
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

/**
 * Keeps the speech of the person holding the phone and drops other people talking around them.
 * Whisper writes down every voice it hears, so a TV or a nearby conversation, above all in the
 * quiet that ends every recording, would otherwise turn into tasks. The speaker is much closer to
 * the microphone, so their speech is the loudest, and speech far quieter than that is someone else.
 */
object ForegroundSpeech {
    private const val FRAME = SAMPLE_RATE / 50
    private const val SMOOTH_FRAMES = 5
    private const val QUIETER_THAN_SPEAKER_DB = 12.0

    // The speaker's level is the loudest they hold for this long in all, so a cough can't set it.
    private const val SPEAKER_FRAMES = 50

    // Pauses inside the speaker's own sentences are kept, and a margin around their speech so no word is clipped.
    private const val PAUSE_FRAMES = 30
    private const val MARGIN_FRAMES = 15

    // What's dropped becomes silence, up to this long, since Whisper misses sentences in audio cut too tightly.
    private const val MAX_GAP_SAMPLES = SAMPLE_RATE
    private const val END_SILENCE_SAMPLES = 3 * SAMPLE_RATE

    /** [stretches] holds the start and end seconds of each stretch of speech. Null when there's no speech in them. */
    fun keep(samples: FloatArray, stretches: FloatArray): FloatArray? {
        val frames = samples.size / FRAME
        val inSpeech = BooleanArray(frames)
        for ((start, end) in stretches.toList().chunked(2)) {
            for (frame in frameAt(start, frames) until frameAt(end, frames)) inSpeech[frame] = true
        }
        val speechFrames = (0 until frames).filter { inSpeech[it] }
        if (speechFrames.isEmpty()) return null

        val level = smoothedLevels(samples, frames)
        val loudestFirst = speechFrames.map { level[it] }.sortedDescending()
        val speaker = loudestFirst[minOf(SPEAKER_FRAMES, loudestFirst.lastIndex)]
        val kept = BooleanArray(frames) { inSpeech[it] && level[it] >= speaker - QUIETER_THAN_SPEAKER_DB }
        widen(kept)
        return join(samples, kept)
    }

    private fun frameAt(seconds: Float, frames: Int) = Math.round(seconds * SAMPLE_RATE / FRAME).coerceIn(0, frames)

    private fun smoothedLevels(samples: FloatArray, frames: Int): DoubleArray {
        val power = DoubleArray(frames) { frame ->
            var sum = 0.0
            for (i in frame * FRAME until (frame + 1) * FRAME) sum += samples[i] * samples[i].toDouble()
            sum / FRAME
        }
        return DoubleArray(frames) { frame ->
            val from = maxOf(0, frame - SMOOTH_FRAMES / 2)
            val to = minOf(frames, frame + SMOOTH_FRAMES / 2 + 1)
            10 * log10((from until to).sumOf { power[it] } / (to - from) + 1e-12)
        }
    }

    /** Fills short pauses between kept frames, then adds a margin on either side. */
    private fun widen(kept: BooleanArray) {
        var lastKept = -1
        for (frame in kept.indices) {
            if (!kept[frame]) continue
            if (lastKept >= 0 && frame - lastKept <= PAUSE_FRAMES) kept.fill(true, lastKept, frame)
            lastKept = frame
        }
        val core = kept.copyOf()
        for (frame in core.indices) {
            if (core[frame]) kept.fill(true, maxOf(0, frame - MARGIN_FRAMES), minOf(kept.size, frame + MARGIN_FRAMES + 1))
        }
    }

    private fun join(samples: FloatArray, kept: BooleanArray): FloatArray {
        val runs = mutableListOf<IntRange>()
        var start = -1
        for (frame in 0..kept.size) {
            val on = frame < kept.size && kept[frame]
            if (on && start < 0) start = frame
            if (!on && start >= 0) {
                runs += start * FRAME until frame * FRAME
                start = -1
            }
        }
        val gaps = runs.mapIndexed { index, run -> minOf(run.first - (runs.getOrNull(index - 1)?.last?.plus(1) ?: 0), MAX_GAP_SAMPLES) }
        val joined = FloatArray(runs.sumOf { it.count() } + gaps.sum() + END_SILENCE_SAMPLES)
        var at = 0
        runs.forEachIndexed { index, run ->
            at += gaps[index]
            samples.copyInto(joined, at, run.first, run.last + 1)
            at += run.count()
        }
        return joined
    }
}

/**
 * Takes out sound below the lowest voices: fans, air conditioners, traffic, wind and mains hum.
 * Whisper scales each recording by its loudest sound, so a loud rumble makes speech quieter to it.
 */
object RumbleFilter {
    private const val CUTOFF_HZ = 100.0

    fun apply(samples: FloatArray): FloatArray {
        // A second-order Butterworth high-pass, from the Audio EQ Cookbook.
        val w0 = 2 * PI * CUTOFF_HZ / SAMPLE_RATE
        val alpha = sin(w0) / (2 * sqrt(0.5))
        val a0 = 1 + alpha
        val b0 = (1 + cos(w0)) / 2 / a0
        val b1 = -(1 + cos(w0)) / a0
        val a1 = -2 * cos(w0) / a0
        val a2 = (1 - alpha) / a0
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        return FloatArray(samples.size) { i ->
            val x = samples[i].toDouble()
            val y = b0 * x + b1 * x1 + b0 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            y.toFloat()
        }
    }
}
