package com.sameer.yaad.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sameer.yaad.R
import com.sameer.yaad.parse.ItemKind
import com.sameer.yaad.yaad

private enum class Tab(val label: String, @param:DrawableRes val icon: Int) {
    TASKS("My tasks", R.drawable.ic_task),
    IDEAS("My ideas", R.drawable.ic_idea),
    LOG("Log", R.drawable.ic_doc),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YaadRoot(onRecord: () -> Unit) {
    val app = LocalContext.current.yaad
    var tab by rememberSaveable { mutableStateOf(Tab.TASKS) }
    var showSetup by rememberSaveable { mutableStateOf(!app.prefs.setupSeen) }

    if (showSetup) {
        SetupScreen(
            onDone = {
                app.prefs.setupSeen = true
                showSetup = false
            },
        )
        return
    }

    val snackbar = remember { SnackbarHostState() }
    val processing by app.repository.observeProcessingCount().collectAsStateWithLifecycle(0)
    val rereading by app.repository.observeRereadingCount().collectAsStateWithLifecycle(0)

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("Yaad") },
                    actions = {
                        IconButton(onClick = { showSetup = true }) {
                            Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings")
                        }
                    },
                )
                if (processing > 0 || rereading > 0) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        if (processing > 0) "Sorting your note in the background…" else "Reading ${if (rereading == 1) "an older note" else "$rereading older notes"} again…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(painterResource(entry.icon), contentDescription = null) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onRecord) {
                Icon(painterResource(R.drawable.ic_mic), contentDescription = "Record a voice note")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (tab) {
            Tab.TASKS -> BoardScreen(ItemKind.TASK, snackbar, contentModifier)
            Tab.IDEAS -> BoardScreen(ItemKind.IDEA, snackbar, contentModifier)
            Tab.LOG -> LogScreen(contentModifier)
        }
    }
}
