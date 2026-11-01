package com.sameer.yaad.capture

import android.app.Activity
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import android.widget.TextView
import com.sameer.yaad.llm.LlamaEngine
import com.sameer.yaad.parse.AiNoteParser
import com.sameer.yaad.parse.AiNotePrompt
import com.sameer.yaad.parse.NoteParser
import java.io.File
import java.time.LocalDateTime
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject

/**
 * Debug only: runs the notes in `ai-eval-notes.txt` through a model file, using the app's real
 * sorting code, and writes what came out to `ai-eval-<model>.jsonl` for scoring on the PC.
 *
 * adb shell am start -n com.sameer.yaad/.capture.AiEvalActivity --es model qwen3.gguf --es prefill think
 */
class AiEvalActivity : Activity() {
    private val fixedNow = LocalDateTime.of(2026, 11, 4, 10, 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val status = TextView(this).apply { text = "Starting" }
        setContentView(status)

        val folder = checkNotNull(getExternalFilesDir(null))
        val modelName = checkNotNull(intent.getStringExtra("model"))
        val prefill = if (intent.getStringExtra("prefill") == "think") "<think>\n\n</think>\n\n" else ""
        val output = File(folder, "ai-eval-$modelName.jsonl").apply { delete() }

        thread {
            val notes = File(folder, "ai-eval-notes.txt").readLines().filter { it.isNotBlank() }
            ParcelFileDescriptor.open(File(folder, modelName), ParcelFileDescriptor.MODE_READ_ONLY).use { file ->
                val loadStart = System.currentTimeMillis()
                LlamaEngine.open(file.fd, 0).use { engine ->
                    output.appendText(JSONObject().put("loadMs", System.currentTimeMillis() - loadStart).toString() + "\n")
                    notes.forEachIndexed { index, note ->
                        var raw: String? = null
                        val start = System.currentTimeMillis()
                        val items = AiNoteParser(
                            generate = { prompt ->
                                engine.generate(prompt, AiNotePrompt.GRAMMAR, 400, 600_000).also { raw = it }
                            },
                            rules = NoteParser(),
                            prefill = prefill,
                        ).parse(note, fixedNow)
                        val row = JSONObject()
                            .put("note", note)
                            .put("ms", System.currentTimeMillis() - start)
                            .put("raw", raw)
                            .put(
                                "items",
                                JSONArray(
                                    items.map {
                                        JSONObject()
                                            .put("type", it.kind.name.lowercase())
                                            .put("title", it.title)
                                            .put("due", it.deadline?.let { d -> "${d.date} ${d.time ?: ""}".trim() } ?: "")
                                    },
                                ),
                            )
                        output.appendText(row.toString() + "\n")
                        runOnUiThread { status.text = "${index + 1} of ${notes.size}" }
                    }
                }
            }
            output.appendText("""{"done":true}""" + "\n")
            runOnUiThread { status.text = "Done" }
        }
    }
}
