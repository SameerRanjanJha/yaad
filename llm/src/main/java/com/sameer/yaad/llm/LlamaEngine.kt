package com.sameer.yaad.llm

import java.io.Closeable

internal object LlamaNative {
    init {
        System.loadLibrary("yaad_llm")
    }

    external fun load(fd: Int, offset: Long, contextSize: Int, threads: Int): Long
    external fun free(handle: Long)
    external fun generate(
        handle: Long,
        prompt: String,
        grammar: String,
        maxTokens: Int,
        timeoutMs: Long,
    ): ByteArray?
}

/** A small language model held in memory. Keep it open only while there are notes to sort. */
class LlamaEngine private constructor(private val handle: Long) : Closeable {

    /** Returns the model's text, which is forced to follow `grammar`, or null if it couldn't run. */
    fun generate(prompt: String, grammar: String, maxTokens: Int, timeoutMs: Long): String? =
        LlamaNative.generate(handle, prompt, grammar, maxTokens, timeoutMs)?.let { String(it) }

    override fun close() = LlamaNative.free(handle)

    companion object {
        private const val CONTEXT_SIZE = 2048

        /** Reads the model from an open file at `offset`, so it can sit inside the APK. The fd can be closed afterwards. */
        fun open(fd: Int, offset: Long): LlamaEngine {
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
            val handle = LlamaNative.load(fd, offset, CONTEXT_SIZE, threads)
            check(handle != 0L) { "Could not load the language model" }
            return LlamaEngine(handle)
        }
    }
}
