package com.sameer.yaad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sameer.yaad.data.Bucket
import com.sameer.yaad.data.ItemEntity
import com.sameer.yaad.data.NoteEntity
import com.sameer.yaad.data.deadline
import com.sameer.yaad.parse.Deadline
import com.sameer.yaad.parse.ItemKind
import com.sameer.yaad.remind.DeadlineFormat
import com.sameer.yaad.yaad
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.launch

private const val MILLIS_PER_DAY = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditItemSheet(item: ItemEntity, onDismiss: () -> Unit) {
    val app = LocalContext.current.yaad
    val scope = rememberCoroutineScope()
    var title by remember(item.id) { mutableStateOf(item.title) }
    var detail by remember(item.id) { mutableStateOf(item.detail.orEmpty()) }
    var kind by remember(item.id) { mutableStateOf(item.kind) }
    var bucket by remember(item.id) { mutableStateOf(item.bucket) }
    var deadline by remember(item.id) { mutableStateOf(item.deadline) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var sourceNote by remember { mutableStateOf<NoteEntity?>(null) }

    LaunchedEffect(item.noteId) { sourceNote = app.repository.note(item.noteId) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(detail, { detail = it }, label = { Text("Details") }, modifier = Modifier.fillMaxWidth())

            SectionLabel("Type")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(kind == ItemKind.TASK, { kind = ItemKind.TASK }, { Text("Task") })
                FilterChip(kind == ItemKind.IDEA, { kind = ItemKind.IDEA }, { Text("Idea") })
            }

            SectionLabel("Bucket")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Bucket.entries.forEach { entry ->
                    FilterChip(bucket == entry, { bucket = entry }, { Text(entry.label) })
                }
            }

            SectionLabel("Deadline")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickingDate = true }) {
                    Text(deadline?.let { DeadlineFormat.label(it, LocalDate.now()) } ?: "Set date")
                }
                if (deadline != null) {
                    OutlinedButton(onClick = { pickingTime = true }) {
                        Text(if (deadline?.time == null) "Add time" else "Change time")
                    }
                    TextButton(onClick = { deadline = null }) { Text("Remove") }
                }
            }

            sourceNote?.let { note ->
                note.transcript?.let { transcript ->
                    SectionLabel("What you said")
                    Text(
                        transcript,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                note.audioPath?.let { AudioPlayButton(it) }
            }

            Button(
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                onClick = {
                    scope.launch {
                        app.repository.saveEdits(
                            id = item.id,
                            title = title.trim(),
                            detail = detail.trim().ifEmpty { null },
                            kind = kind,
                            bucket = bucket,
                            deadline = deadline,
                            now = System.currentTimeMillis(),
                        )
                        onDismiss()
                    }
                },
            ) { Text("Save") }
        }
    }

    if (pickingDate) {
        DeadlineDatePicker(
            initial = deadline?.date ?: LocalDate.now(),
            onPicked = { date -> deadline = Deadline(date, deadline?.time) },
            onDismiss = { pickingDate = false },
        )
    }
    if (pickingTime) {
        DeadlineTimePicker(
            initial = deadline?.time ?: LocalTime.of(9, 0),
            onPicked = { time -> deadline = deadline?.copy(time = time) },
            onDismiss = { pickingTime = false },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadlineDatePicker(initial: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The picker speaks UTC midnights, so converting by whole days keeps the date from shifting.
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.toEpochDay() * MILLIS_PER_DAY)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPicked(LocalDate.ofEpochDay(it / MILLIS_PER_DAY)) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadlineTimePicker(initial: LocalTime, onPicked: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPicked(LocalTime.of(state.hour, state.minute))
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}
