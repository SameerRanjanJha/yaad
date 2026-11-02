package com.sameer.yaad.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon as PlatformIcon
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.sameer.yaad.R
import com.sameer.yaad.SpeechMode
import com.sameer.yaad.capture.CaptureTileService
import com.sameer.yaad.capture.MicWidgetProvider
import com.sameer.yaad.remind.LockScreenMic
import com.sameer.yaad.yaad

@Composable
fun SetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.yaad

    // Permissions change in other screens, so re-read everything each time this one comes back.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        LockScreenMic.sync(context)
        onPauseOrDispose { }
    }

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh++
        LockScreenMic.sync(context)
    }

    var lockScreenMic by remember { mutableStateOf(app.prefs.lockScreenMic) }
    var aiSorting by remember { mutableStateOf(app.prefs.aiSorting) }
    var speechMode by remember { mutableStateOf(app.prefs.speechMode) }
    BackHandler(onBack = onDone)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Set up Yaad", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Everything stays on this phone. Yaad has no internet permission, so it can't send anything anywhere.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val status = remember(refresh) { SetupStatus.of(context) }
            SetupRow(
                title = "Microphone",
                body = "Needed to record your notes.",
                done = status.microphone,
                action = "Allow",
                onAction = { requestPermission.launch(Manifest.permission.RECORD_AUDIO) },
            )
            SetupRow(
                title = "Notifications",
                body = "Needed for deadline reminders, the daily check-in and the lock-screen mic.",
                done = status.notifications,
                action = "Allow",
                onAction = { requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
            SetupRow(
                title = "Run without battery limits",
                body = "Otherwise the phone may delay reminders to save battery.",
                done = status.noBatteryLimits,
                action = "Allow",
                onAction = { context.requestBatteryExemption() },
            )
            SetupRow(
                title = "Phone background settings",
                body = "Some phones, such as OnePlus and Xiaomi, can kill apps anyway. Open App battery usage and allow background activity, and turn on Auto launch if you see it.",
                done = false,
                action = "Open",
                onAction = { context.openAppSettings() },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SetupRow(
                title = "Home-screen mic",
                body = "A one-tap mic widget.",
                done = status.micWidget,
                action = "Add",
                onAction = { context.requestMicWidget() },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Lock-screen mic", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "A permanent notification on your lock screen. Tap it to record without unlocking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = lockScreenMic,
                    onCheckedChange = {
                        lockScreenMic = it
                        app.prefs.lockScreenMic = it
                        LockScreenMic.sync(context)
                    },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SetupRow(
                    title = "Quick Settings mic",
                    body = "A mic tile in the panel you swipe down. It also works on the lock screen.",
                    done = false,
                    action = "Add",
                    onAction = { context.requestQuickSettingsTile() },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sort notes with AI", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Runs on this phone, never online. Turn off for instant but simpler sorting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = aiSorting,
                    onCheckedChange = {
                        aiSorting = it
                        app.prefs.aiSorting = it
                    },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text("You speak", style = MaterialTheme.typography.titleMedium)
            SpeechMode.entries.forEach { mode ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = speechMode == mode,
                            role = Role.RadioButton,
                            onClick = {
                                speechMode = mode
                                app.prefs.speechMode = mode
                            },
                        ),
                ) {
                    RadioButton(selected = speechMode == mode, onClick = null)
                    Text(mode.label, modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp))
                }
            }

            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

private data class SetupStatus(
    val microphone: Boolean,
    val notifications: Boolean,
    val noBatteryLimits: Boolean,
    val micWidget: Boolean,
) {
    companion object {
        fun of(context: Context) = SetupStatus(
            microphone = context.hasPermission(Manifest.permission.RECORD_AUDIO),
            notifications = context.hasPermission(Manifest.permission.POST_NOTIFICATIONS),
            noBatteryLimits = context.ignoresBatteryLimits(),
            micWidget = context.hasMicWidget(),
        )
    }
}

@Composable
private fun SetupRow(title: String, body: String, done: Boolean, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (done) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = "Done",
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}

private fun Context.hasPermission(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun Context.ignoresBatteryLimits(): Boolean =
    getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

// Play Store policy limits this request, but this app is sideloaded.
@SuppressLint("BatteryLife")
private fun Context.requestBatteryExemption() {
    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$packageName".toUri()))
}

private fun Context.openAppSettings() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
}

private fun Context.hasMicWidget(): Boolean =
    AppWidgetManager.getInstance(this).getAppWidgetIds(ComponentName(this, MicWidgetProvider::class.java)).isNotEmpty()

private fun Context.requestMicWidget() {
    AppWidgetManager.getInstance(this).requestPinAppWidget(ComponentName(this, MicWidgetProvider::class.java), null, null)
}

@SuppressLint("NewApi")
private fun Context.requestQuickSettingsTile() {
    getSystemService(StatusBarManager::class.java).requestAddTileService(
        ComponentName(this, CaptureTileService::class.java),
        getString(R.string.tile_label),
        PlatformIcon.createWithResource(this, R.drawable.ic_mic),
        mainExecutor,
    ) { }
}
