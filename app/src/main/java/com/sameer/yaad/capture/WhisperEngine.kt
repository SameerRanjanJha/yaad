package com.sameer.yaad.capture

import android.content.res.AssetManager
import com.sameer.yaad.SpeechMode
import java.io.Closeable
import java.io.File

internal object WhisperNative {
    init {
        System.loadLibrary("yaad_whisper")
    }

    external fun load(assets: AssetManager, assetPath: String): Long
    external fun loadFile(path: String): Long
    external fun free(handle: Long)
    external fun transcribe(
        handle: Long,
        samples: FloatArray,
        language: String,
        translate: Boolean,
        threads: Int,
        prompt: String,
    ): String?

    /** Start and end seconds of each stretch of speech, one after another, or null if the detector didn't load. */
    external fun speechStretches(detectorPath: String, samples: FloatArray, threads: Int): FloatArray?
}

interface Transcriber : Closeable {
    fun transcribe(samples: FloatArray, mode: SpeechMode): String
}

/**
 * Holds the model in memory. Keep it open only while there are notes to process.
 * [speechDetector] is the file of the model that finds where someone is speaking.
 */
class WhisperEngine internal constructor(
    private val handle: Long,
    private val speechDetector: File?,
    private val removeRumble: Boolean = true,
) : Transcriber {
    init {
        check(handle != 0L) { "Could not load the speech model" }
    }

    constructor(assets: AssetManager, speechDetector: File?) : this(WhisperNative.load(assets, MODEL_ASSET), speechDetector)

    override fun transcribe(samples: FloatArray, mode: SpeechMode): String {
        val filtered = if (removeRumble) RumbleFilter.apply(samples) else samples
        val stretches = speechDetector?.let { WhisperNative.speechStretches(it.path, filtered, threadCount()) }
        // With no speech found at all, Whisper still hears everything: losing a note is worse than a stray word.
        val speech = stretches?.let { ForegroundSpeech.keep(filtered, it) } ?: filtered
        return WhisperNative.transcribe(handle, speech, mode.language, mode.translate, threadCount(), STYLE_PROMPT)
            ?: error("The speech model failed to run")
    }

    override fun close() = WhisperNative.free(handle)

    private fun threadCount(): Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)

    internal companion object {
        const val MODEL_ASSET = "models/ggml-small-q5_1.bin"
        const val SPEECH_DETECTOR_ASSET = "models/silero-vad.bin"

        // Whisper copies the style of its prompt, which gets punctuation and dates written the way the parser reads them.
        const val STYLE_PROMPT = "Voice memo with reminders and ideas. Call the bank tomorrow. " +
            "Send the invoice to Ravi by Friday at 5 pm."

        /** Whisper reads its speech detector from a file, so the copy in the APK is written out once. */
        fun speechDetectorFile(assets: AssetManager, folder: File): File {
            val file = File(folder, "silero-vad.bin")
            val size = assets.openFd(SPEECH_DETECTOR_ASSET).use { it.length }
            if (file.length() != size) {
                val partial = File(folder, "silero-vad.bin.partial")
                assets.open(SPEECH_DETECTOR_ASSET).use { input -> partial.outputStream().use { input.copyTo(it) } }
                check(partial.renameTo(file) || (file.delete() && partial.renameTo(file))) { "Could not save the speech detector" }
            }
            return file
        }
    }
}
