package com.sameer.yaad.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sameer.yaad.capture.AnalysisWorker
import com.sameer.yaad.data.NoteEntity
import com.sameer.yaad.data.NoteState
import com.sameer.yaad.data.toLocalDateTime
import com.sameer.yaad.yaad
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val NOTE_TIME = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH)

@Composable
fun LogScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.yaad
    val notes by remember { app.repository.observeNotes() }.collectAsStateWithLifecycle(emptyList())

    Column(modifier.fillMaxSize()) {
        OutlinedButton(
            onClick = { openLogDocument(context) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) { Text("Open document (Documents/Yaad/voice-log.md)") }

        if (notes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Everything you say is kept here, word for word.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                items(notes, key = { it.id }) { note ->
                    NoteRow(note)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: NoteEntity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val canRetry = note.state == NoteState.FAILED || note.state == NoteState.EMPTY

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            NOTE_TIME.format(note.createdAt.toLocalDateTime()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (note.state) {
            NoteState.DONE -> Text(note.transcript.orEmpty(), style = MaterialTheme.typography.bodyMedium)
            NoteState.RECORDING -> StatusText("Recording…")
            NoteState.PENDING, NoteState.TRANSCRIBING -> if (note.reread) {
                Text(note.transcript.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                StatusText("Reading this again…")
            } else {
                StatusText("Writing this down…")
            }
            NoteState.EMPTY -> StatusText("No speech detected. The audio is kept.")
            NoteState.FAILED -> Text(
                "Couldn't write this down: ${note.error.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            note.audioPath?.let { AudioPlayButton(it) }
            if (canRetry) {
                TextButton(onClick = {
                    scope.launch {
                        context.yaad.repository.retryNote(note.id)
                        AnalysisWorker.schedule(context)
                    }
                }) { Text("Try again") }
            }
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun openLogDocument(context: Context) {
    val uri = context.yaad.voiceLog.documentUri()
    if (uri == null) {
        Toast.makeText(context, "The document starts with your first note", Toast.LENGTH_SHORT).show()
        return
    }
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "text/plain")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(view)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app on this phone can open text files", Toast.LENGTH_SHORT).show()
    }
}
