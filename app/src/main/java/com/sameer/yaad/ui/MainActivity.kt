package com.sameer.yaad.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.sameer.yaad.capture.AnalysisWorker
import com.sameer.yaad.capture.CaptureActivity
import com.sameer.yaad.yaad
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            YaadTheme {
                YaadRoot(onRecord = { startActivity(Intent(this, CaptureActivity::class.java)) })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Analysis looks after itself. This is a nudge in case a battery killer stopped it.
        lifecycleScope.launch {
            val app = yaad
            if (app.repository.hasUnfinishedNotes()) AnalysisWorker.schedule(this@MainActivity)
            app.reminders.sync()
        }
    }
}
