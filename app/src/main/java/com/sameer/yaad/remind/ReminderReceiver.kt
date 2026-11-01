package com.sameer.yaad.remind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sameer.yaad.data.Bucket
import com.sameer.yaad.yaad
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.yaad
        val pending = goAsync()
        app.scope.launch {
            try {
                when (intent.action) {
                    ACTION_ALARM -> app.reminders.sync()
                    ACTION_MARK_DONE -> {
                        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1)
                        app.repository.moveItem(itemId, Bucket.COMPLETED, System.currentTimeMillis())
                        Notifications.cancelDeadline(context, itemId)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ALARM = "com.sameer.yaad.REMINDER_ALARM"
        const val ACTION_MARK_DONE = "com.sameer.yaad.MARK_DONE"
        const val EXTRA_ITEM_ID = "item_id"
    }
}
