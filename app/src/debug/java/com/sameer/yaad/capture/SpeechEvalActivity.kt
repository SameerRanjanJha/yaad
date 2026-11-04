package com.sameer.yaad.capture

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import com.sameer.yaad.SpeechMode
import java.io.File
import kotlin.concurrent.thread
import org.json.JSONObject

/**
 * Debug only: transcribes every recording under `speech/<condition>/` with a Whisper model file,
 * through the app's real speech code, and writes the text to `speech-eval-<run>.jsonl` for scoring on the PC.
 *
 * adb shell am start -n com.sameer.yaad/.capture.SpeechEvalActivity --es model ggml-small-q5_1.bin --es run small --ez detector true --ez rumble true
 *
 * `--es only <condition>` limits it to one folder.
 */
class SpeechEvalActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val status = TextView(this).apply { text = "Starting" }
        setContentView(status)

        val folder = checkNotNull(getExternalFilesDir(null))
        val model = File(folder, checkNotNull(intent.getStringExtra("model")))
        val run = checkNotNull(intent.getStringExtra("run"))
        val detector = WhisperEngine.speechDetectorFile(assets, noBackupFilesDir).takeIf { intent.getBooleanExtra("detector", true) }
        val rumble = intent.getBooleanExtra("rumble", true)
        val only = intent.getStringExtra("only")
        val output = File(folder, "speech-eval-$run.jsonl").apply { delete() }

        thread {
            val recordings = File(folder, "speech").walk()
                .filter { it.extension == "wav" && (only == null || it.parentFile?.name == only) }
                .sorted()
                .toList()
            val handle = WhisperNative.loadFile(model.path)
            WhisperEngine(handle, detector, rumble).use { engine ->
                recordings.forEachIndexed { index, recording ->
                    val samples = WavFile.readSamples(recording)
                    val start = System.currentTimeMillis()
                    val text = engine.transcribe(samples, SpeechMode.ENGLISH)
                    val row = JSONObject()
                        .put("file", recording.relativeTo(File(folder, "speech")).invariantSeparatorsPath)
                        .put("ms", System.currentTimeMillis() - start)
                        .put("text", text)
                    output.appendText(row.toString() + "\n")
                    runOnUiThread { status.text = "${index + 1} of ${recordings.size}" }
                }
            }
            output.appendText("""{"done":true}""" + "\n")
            runOnUiThread { status.text = "Done" }
        }
    }
}
