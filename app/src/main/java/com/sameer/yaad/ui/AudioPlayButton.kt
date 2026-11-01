package com.sameer.yaad.ui

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.sameer.yaad.R
import java.io.File

/** Plays the original recording, for when a word was misheard. Hidden once the audio is gone. */
@Composable
fun AudioPlayButton(path: String) {
    if (!remember(path) { File(path).exists() }) return

    var player by remember(path) { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(path) {
        onDispose { player?.release() }
    }

    val stop = {
        player?.release()
        player = null
    }
    val play = {
        player = MediaPlayer().apply {
            setDataSource(path)
            setOnCompletionListener { stop() }
            prepare()
            start()
        }
    }

    TextButton(onClick = { if (player == null) play() else stop() }) {
        Icon(
            painterResource(if (player == null) R.drawable.ic_play else R.drawable.ic_stop),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(if (player == null) "Play recording" else "Stop")
    }
}
