package com.sameer.yaad.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sameer.yaad.R
import com.sameer.yaad.data.Bucket
import com.sameer.yaad.data.ItemEntity
import com.sameer.yaad.data.deadline
import com.sameer.yaad.parse.ItemKind
import com.sameer.yaad.remind.DeadlineFormat
import com.sameer.yaad.yaad
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.launch

@Composable
fun BoardScreen(kind: ItemKind, snackbar: SnackbarHostState, modifier: Modifier = Modifier) {
    val app = LocalContext.current.yaad
    val scope = rememberCoroutineScope()
    val items by remember(kind) { app.repository.observeItems(kind) }.collectAsStateWithLifecycle(emptyList())
    var bucket by rememberSaveable(kind) { mutableStateOf(Bucket.TODO) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }

    val grouped = remember(items) { items.groupBy { it.bucket } }
    val visible = remember(grouped, bucket) { sortForBucket(grouped[bucket].orEmpty(), bucket) }

    val move: (ItemEntity, Bucket) -> Unit = { item, target ->
        scope.launch {
            app.repository.moveItem(item.id, target, System.currentTimeMillis())
            val result = snackbar.showSnackbar(
                message = "Moved to ${target.label}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                app.repository.moveItem(item.id, item.bucket, System.currentTimeMillis())
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            items(Bucket.entries) { entry ->
                FilterChip(
                    selected = bucket == entry,
                    onClick = { bucket = entry },
                    label = { Text("${entry.label} ${grouped[entry].orEmpty().size}") },
                )
            }
        }
        if (visible.isEmpty()) {
            EmptyBucket(kind, bucket)
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                items(visible, key = { it.id }) { item ->
                    ItemRow(item, onOpen = { editingId = item.id }, onMove = { move(item, it) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    editingId?.let { id ->
        items.firstOrNull { it.id == id }?.let { item ->
            EditItemSheet(item, onDismiss = { editingId = null })
        }
    }
}

/** Open work runs soonest deadline first, with undated items after. Finished work runs newest first. */
internal fun sortForBucket(items: List<ItemEntity>, bucket: Bucket): List<ItemEntity> = when (bucket) {
    Bucket.COMPLETED -> items.sortedByDescending { it.completedAt ?: it.updatedAt }
    Bucket.DISCARDED -> items.sortedByDescending { it.updatedAt }
    else -> items.sortedWith(
        compareBy<ItemEntity> { it.deadlineEpochDay ?: Long.MAX_VALUE }
            .thenBy { it.deadlineMinuteOfDay ?: Int.MAX_VALUE }
            .thenByDescending { it.createdAt },
    )
}

@Composable
private fun EmptyBucket(kind: ItemKind, bucket: Bucket) {
    val noun = if (kind == ItemKind.TASK) "tasks" else "ideas"
    val message = when (bucket) {
        Bucket.TODO -> "No $noun on your list.\nTap the mic and say what's on your mind."
        Bucket.IN_PROGRESS -> "Nothing you're working on right now."
        Bucket.COMPLETED -> "Completed $noun show up here."
        Bucket.DISCARDED -> "Discarded $noun show up here."
    }
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ItemRow(item: ItemEntity, onOpen: () -> Unit, onMove: (Bucket) -> Unit) {
    val finished = item.bucket == Bucket.COMPLETED || item.bucket == Bucket.DISCARDED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.bucket == Bucket.DISCARDED) {
            Spacer(Modifier.size(16.dp))
        } else {
            Checkbox(
                checked = item.bucket == Bucket.COMPLETED,
                onCheckedChange = { checked -> onMove(if (checked) Bucket.COMPLETED else Bucket.TODO) },
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.bucket == Bucket.COMPLETED) TextDecoration.LineThrough else null,
                color = if (finished) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            item.detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DeadlineLabel(item, finished)
        }
        when (item.bucket) {
            Bucket.TODO -> {
                // Words, not a play icon: a play button looks like the app would run the task itself.
                TextButton(onClick = { onMove(Bucket.IN_PROGRESS) }) { Text("I'm on it") }
                RowAction(R.drawable.ic_close, "Discard") { onMove(Bucket.DISCARDED) }
            }
            Bucket.IN_PROGRESS -> RowAction(R.drawable.ic_close, "Discard") { onMove(Bucket.DISCARDED) }
            Bucket.COMPLETED -> Unit
            Bucket.DISCARDED -> TextButton(onClick = { onMove(Bucket.TODO) }) { Text("Restore") }
        }
    }
}

@Composable
private fun DeadlineLabel(item: ItemEntity, finished: Boolean) {
    val deadline = item.deadline ?: return
    val overdue = !finished && DeadlineFormat.isOverdue(deadline, LocalDateTime.now())
    val label = DeadlineFormat.label(deadline, LocalDate.now())
    Spacer(Modifier.height(2.dp))
    Text(
        if (overdue) "Overdue: $label" else label,
        style = MaterialTheme.typography.labelMedium,
        color = when {
            overdue -> MaterialTheme.colorScheme.error
            finished -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.primary
        },
    )
}

@Composable
private fun RowAction(icon: Int, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painterResource(icon),
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
