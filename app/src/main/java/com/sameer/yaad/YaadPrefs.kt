package com.sameer.yaad

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate

enum class SpeechMode(val label: String, val language: String, val translate: Boolean) {
    ENGLISH("English", "en", false),
    HINDI_ENGLISH("Hindi + English (written in English)", "auto", true),
}

class YaadPrefs(private val context: Context) {
    // The app and the analysis process both use these. Each keeps a private copy unless told to re-read the file.
    @Suppress("DEPRECATION")
    private val prefs get() = context.getSharedPreferences("yaad", Context.MODE_MULTI_PROCESS)

    var setupSeen: Boolean
        get() = prefs.getBoolean("setup_seen", false)
        set(value) = prefs.edit { putBoolean("setup_seen", value) }

    var lockScreenMic: Boolean
        get() = prefs.getBoolean("lock_screen_mic", true)
        set(value) = prefs.edit { putBoolean("lock_screen_mic", value) }

    var aiSorting: Boolean
        get() = prefs.getBoolean("ai_sorting", true)
        set(value) = prefs.edit { putBoolean("ai_sorting", value) }

    var speechMode: SpeechMode
        get() = SpeechMode.entries.firstOrNull { it.name == prefs.getString("speech_mode", null) } ?: SpeechMode.ENGLISH
        set(value) = prefs.edit { putString("speech_mode", value.name) }

    var logDocumentUri: String?
        get() = prefs.getString("log_document_uri", null)
        set(value) = prefs.edit { putString("log_document_uri", value) }

    var lastCheckIn: LocalDate?
        get() = prefs.getString("last_check_in", null)?.let(LocalDate::parse)
        set(value) = prefs.edit { putString("last_check_in", value?.toString()) }
}
