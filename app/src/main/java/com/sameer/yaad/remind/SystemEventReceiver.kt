package com.sameer.yaad.remind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sameer.yaad.yaad
import kotlinx.coroutines.launch

/** A reboot, an update, a clock change or a swiped-away mic all recover the same way. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LockScreenMic.sync(context)
        val app = context.yaad
        val pending = goAsync()
        app.scope.launch {
            try {
                app.reminders.sync()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REPOST_MIC = "com.sameer.yaad.REPOST_MIC"
    }
}
