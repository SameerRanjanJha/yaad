package com.sameer.yaad.capture

import android.content.res.AssetManager
import com.sameer.yaad.SpeechMode
import java.io.Closeable

internal object WhisperNative {
    init {
        System.loadLibrary("yaad_whisper")
    }

    external fun load(assets: AssetManager, assetPath: String): Long
    external fun free(handle: Long)
    external fun transcribe(
        handle: Long,
        samples: FloatArray,
        language: String,
        translate: Boolean,
        threads: Int,
        prompt: String,
    ): String?
}

interface Transcriber : Closeable {
    fun transcribe(samples: FloatArray, mode: SpeechMode): String
}

/** Holds the model in memory. Keep it open only while there are notes to process. */
class WhisperEngine(assets: AssetManager) : Transcriber {
    private val handle = WhisperNative.load(assets, MODEL_ASSET).also {
        check(it != 0L) { "Could not load the speech model" }
    }

    override fun transcribe(samples: FloatArray, mode: SpeechMode): String =
        WhisperNative.transcribe(handle, samples, mode.language, mode.translate, threadCount(), STYLE_PROMPT)
            ?: error("The speech model failed to run")

    override fun close() = WhisperNative.free(handle)

    private fun threadCount(): Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)

    private companion object {
        const val MODEL_ASSET = "models/ggml-base-q5_1.bin"

        // Whisper copies the style of its prompt, which gets punctuation and dates written the way the parser reads them.
        const val STYLE_PROMPT = "Voice memo with reminders and ideas. Call the bank tomorrow. " +
            "Send the invoice to Ravi by Friday at 5 pm."
    }
}
