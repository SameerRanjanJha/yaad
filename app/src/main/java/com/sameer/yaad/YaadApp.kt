package com.sameer.yaad

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.sameer.yaad.capture.AnalysisWorker
import com.sameer.yaad.capture.NoteProcessor
import com.sameer.yaad.capture.NoteSorter
import com.sameer.yaad.capture.WavFile
import com.sameer.yaad.capture.WhisperEngine
import com.sameer.yaad.data.YaadDatabase
import com.sameer.yaad.data.YaadRepository
import com.sameer.yaad.log.VoiceLog
import com.sameer.yaad.parse.NoteParser
import com.sameer.yaad.remind.LockScreenMic
import com.sameer.yaad.remind.Notifications
import com.sameer.yaad.remind.ReminderEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Created once in the main process, which records and shows the UI, and once in ":analysis". */
class YaadApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val prefs by lazy { YaadPrefs(this) }
    private val database by lazy {
        Room.databaseBuilder(this, YaadDatabase::class.java, "yaad.db")
            // Lets the screens in one process see what analysis writes from the other.
            .enableMultiInstanceInvalidation()
            .build()
    }
    val reminders by lazy { ReminderEngine(this, database.items()) }
    val repository by lazy { YaadRepository(database) { reminders.sync() } }
    val voiceLog by lazy { VoiceLog(this, prefs) }
    val noteProcessor by lazy {
        NoteProcessor(
            context = this,
            repository = repository,
            voiceLog = voiceLog,
            sorter = NoteSorter(
                assets = assets,
                rules = NoteParser(),
                aiEnabled = { prefs.aiSorting },
                disableAi = { prefs.aiSorting = false },
            ),
            engineFactory = { WhisperEngine(assets) },
            speechMode = { prefs.speechMode },
        )
    }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        if (getProcessName() == packageName) startMainProcess()
    }

    private fun startMainProcess() {
        val processStart = System.currentTimeMillis()
        LockScreenMic.sync(this)
        scope.launch {
            reminders.sync()
            repository.recoverCutOffRecordings(processStart) { WavFile.durationMs(it) }
            if (repository.hasUnfinishedNotes()) AnalysisWorker.schedule(this@YaadApp)
        }
    }
}

val Context.yaad: YaadApp get() = applicationContext as YaadApp
