package com.sameer.yaad.capture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sameer.yaad.R
import com.sameer.yaad.ui.MainActivity
import com.sameer.yaad.ui.YaadTheme
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * The capture screen every mic button opens. It shows over the lock screen, so it must never
 * display saved notes or tasks: only the recording in progress.
 */
class CaptureActivity : ComponentActivity() {
    private var captureRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Already recording means the user came back to a running capture, so don't start another.
        captureRequested = CaptureSession.isRecording
        if (!captureRequested) CaptureSession.clearResult()

        setContent {
            YaadTheme(darkTheme = true) {
                val state by CaptureSession.state.collectAsStateWithLifecycle()
                BackHandler { CaptureService.stop() }
                if (hasMicPermission()) {
                    CaptureScreen(state, onStop = CaptureService::stop, onCancel = CaptureService::cancel, onFinished = ::finish)
                } else {
                    PermissionNeededScreen(onOpenApp = ::openAppForPermission)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Waits for the window to be visible: Android only lets a visible app start using the mic.
        if (!captureRequested && hasMicPermission()) {
            captureRequested = true
            vibrate(START_BUZZ_MS)
            CaptureService.record(this)
        }
    }

    /** A second tap on any mic button means "stop". */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        CaptureService.stop()
    }

    private fun hasMicPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun openAppForPermission() {
        val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        keyguard.requestDismissKeyguard(this, null)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun vibrate(durationMs: Long) {
        getSystemService(Vibrator::class.java)
            .vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    @Composable
    private fun CaptureScreen(
        state: CaptureState,
        onStop: () -> Unit,
        onCancel: () -> Unit,
        onFinished: () -> Unit,
    ) {
        LaunchedEffect(state) {
            when (state) {
                is CaptureState.Saved -> {
                    vibrate(SAVED_BUZZ_MS)
                    delay(SAVED_CONFIRMATION_MS)
                    onFinished()
                }
                is CaptureState.Cancelled -> onFinished()
                else -> Unit
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (state) {
                is CaptureState.Saved -> ResultMark(R.drawable.ic_check, "Saved")
                is CaptureState.Failed -> ResultMark(R.drawable.ic_close, state.message, onDismiss = onFinished)
                is CaptureState.Recording -> Recording(state, onStop, onCancel)
                else -> Recording(CaptureState.Recording(0, 0f), onStop, onCancel)
            }
        }
    }

    @Composable
    private fun Recording(state: CaptureState.Recording, onStop: () -> Unit, onCancel: () -> Unit) {
        Text(
            text = "Listening",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = formatElapsed(state.elapsedMs),
            color = Color.White,
            fontSize = 44.sp,
            fontWeight = FontWeight.Light,
        )
        Spacer(Modifier.height(56.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
            Box(
                Modifier
                    .size(132.dp)
                    .scale(1f + (state.level * LEVEL_TO_PULSE).coerceAtMost(MAX_PULSE))
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(132.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClickLabel = "Stop and save", role = Role.Button, onClick = onStop),
            ) {
                Icon(
                    painterResource(R.drawable.ic_stop),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp),
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("Tap to stop and save", color = Color.White, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))
        TextButton(onClick = onCancel) { Text("Cancel", color = Color.White.copy(alpha = 0.7f)) }
    }

    @Composable
    private fun ResultMark(icon: Int, message: String, onDismiss: (() -> Unit)? = null) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(16.dp))
        Text(message, color = Color.White, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (onDismiss != null) {
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onDismiss) { Text("Close", color = Color.White) }
        }
    }

    @Composable
    private fun PermissionNeededScreen(onOpenApp: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Yaad needs microphone access before it can record.",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onOpenApp) { Text("Open Yaad", color = Color.White) }
        }
    }

    private fun formatElapsed(elapsedMs: Long): String {
        val seconds = elapsedMs / 1000
        return String.format(Locale.ENGLISH, "%d:%02d", seconds / 60, seconds % 60)
    }

    private companion object {
        const val START_BUZZ_MS = 40L
        const val SAVED_BUZZ_MS = 90L
        const val SAVED_CONFIRMATION_MS = 700L
        const val LEVEL_TO_PULSE = 6f
        const val MAX_PULSE = 0.65f
    }
}
