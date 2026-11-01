package com.sameer.yaad.capture

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

const val SAMPLE_RATE = 16_000
private const val HEADER_BYTES = 44
private const val BYTES_PER_SAMPLE = 2

/** Audio reaches disk as it is spoken, so a crash mid-note loses a moment at most. */
class WavWriter(file: File) : Closeable {
    private val output = RandomAccessFile(file, "rw").apply {
        setLength(0)
        write(ByteArray(HEADER_BYTES))
    }
    private var dataBytes = 0

    fun write(samples: ShortArray, count: Int) {
        val bytes = ByteBuffer.allocate(count * BYTES_PER_SAMPLE).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) bytes.putShort(samples[i])
        output.write(bytes.array())
        dataBytes += bytes.capacity()
    }

    override fun close() {
        output.seek(0)
        output.write(header(dataBytes))
        output.close()
    }
}

object WavFile {
    /** Skips the header without trusting it: a recording cut off by a crash never got its sizes written. */
    fun readSamples(file: File): FloatArray {
        val bytes = file.readBytes()
        val count = ((bytes.size - HEADER_BYTES) / BYTES_PER_SAMPLE).coerceAtLeast(0)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(count) { buffer.getShort(HEADER_BYTES + it * BYTES_PER_SAMPLE) / 32768f }
    }

    fun durationMs(file: File): Long {
        val samples = ((file.length() - HEADER_BYTES) / BYTES_PER_SAMPLE).coerceAtLeast(0)
        return samples * 1000 / SAMPLE_RATE
    }
}

private fun header(dataBytes: Int): ByteArray =
    ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray())
        putInt(36 + dataBytes)
        put("WAVE".toByteArray())
        put("fmt ".toByteArray())
        putInt(16)
        putShort(1)
        putShort(1)
        putInt(SAMPLE_RATE)
        putInt(SAMPLE_RATE * BYTES_PER_SAMPLE)
        putShort(BYTES_PER_SAMPLE.toShort())
        putShort(16)
        put("data".toByteArray())
        putInt(dataBytes)
    }.array()
