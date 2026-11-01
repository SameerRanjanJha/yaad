package com.sameer.yaad.remind

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.sameer.yaad.yaad

object LockScreenMic {
    /** Shows or hides the mic to match the setting. Safe to call any number of times. */
    fun sync(context: Context) {
        if (context.yaad.prefs.lockScreenMic) {
            // Android 14+ lets people swipe ongoing notifications away, which would lose the mic.
            val repost = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, SystemEventReceiver::class.java).setAction(SystemEventReceiver.ACTION_REPOST_MIC),
                PendingIntent.FLAG_IMMUTABLE,
            )
            Notifications.showLockScreenMic(context, whenDismissed = repost)
        } else {
            Notifications.hideLockScreenMic(context)
        }
    }
}
