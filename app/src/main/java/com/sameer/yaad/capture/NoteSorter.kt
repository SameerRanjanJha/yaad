package com.sameer.yaad.capture

import android.content.res.AssetManager
import android.util.Log
import com.sameer.yaad.llm.LlamaEngine
import com.sameer.yaad.parse.AiNoteParser
import com.sameer.yaad.parse.AiNotePrompt
import com.sameer.yaad.parse.NoteParser
import com.sameer.yaad.parse.ParsedItem
import java.io.IOException
import java.time.LocalDateTime

/** Sorts with the model in the APK when allowed, and with the rules whenever that isn't possible. */
class NoteSorter(
    private val assets: AssetManager,
    private val rules: NoteParser,
    private val aiEnabled: () -> Boolean,
    private val disableAi: () -> Unit,
) {
    /** A native crash can't be caught, so it's only seen on the next try. Without this, every note would crash. */
    fun stopUsingAi() {
        Log.w(TAG, "The model crashed while sorting. Using the rules from now on.")
        disableAi()
    }

    fun sort(transcript: String, capturedAt: LocalDateTime, allowAi: Boolean): List<ParsedItem> {
        if (!allowAi || !aiEnabled()) return rules.parse(transcript, capturedAt)
        return sortWithModel(transcript, capturedAt) ?: rules.parse(transcript, capturedAt)
    }

    /**
     * For reading an old note again. Null when the model can't run, so the old reading stays
     * instead of being replaced by a weaker one from the rules.
     */
    fun sortAgain(transcript: String, capturedAt: LocalDateTime, allowAi: Boolean): List<ParsedItem>? = when {
        !aiEnabled() -> rules.parse(transcript, capturedAt)
        allowAi -> sortWithModel(transcript, capturedAt)
        else -> null
    }

    private fun sortWithModel(transcript: String, capturedAt: LocalDateTime): List<ParsedItem>? {
        Log.i(TAG, "Sorting with the model")
        return try {
            openModel().use { engine ->
                AiNoteParser(
                    generate = { prompt -> engine.generate(prompt, AiNotePrompt.GRAMMAR, MAX_TOKENS, TIMEOUT_MS) },
                    rules = rules,
                    prefill = PREFILL,
                ).parse(transcript, capturedAt)
            }
        } catch (error: Exception) {
            Log.w(TAG, "The model failed to sort", error)
            null
        }
    }

    @Throws(IOException::class)
    private fun openModel(): LlamaEngine =
        assets.openFd(MODEL_ASSET).use { LlamaEngine.open(it.parcelFileDescriptor.fd, it.startOffset) }

    private companion object {
        const val TAG = "YaadSorter"
        const val MODEL_ASSET = "models/sorter.gguf"
        const val MAX_TOKENS = 400
        const val TIMEOUT_MS = 180_000L

        // Starts the model's reply past its "thinking" step, which is slow and adds nothing here.
        const val PREFILL = "<think>\n\n</think>\n\n"
    }
}
