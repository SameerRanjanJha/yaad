package com.sameer.yaad.capture

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.Closeable

interface PcmSource : Closeable {
    /** Fills the buffer and returns how many samples were read, or a negative number on failure. */
    fun read(buffer: ShortArray): Int
}

class MicPcmSource : PcmSource {
    private val recorder = openRecorder().apply { startRecording() }

    override fun read(buffer: ShortArray): Int = recorder.read(buffer, 0, buffer.size)

    override fun close() {
        runCatching { recorder.stop() }
        recorder.release()
    }

    // The caller checks RECORD_AUDIO before starting the service.
    @SuppressLint("MissingPermission")
    private fun openRecorder(): AudioRecord {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferBytes = maxOf(minBuffer, SAMPLE_RATE * 2)
        for (source in listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)) {
            val recorder = AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes)
            if (recorder.state == AudioRecord.STATE_INITIALIZED) return recorder
            recorder.release()
        }
        error("Could not open the microphone")
    }
}
