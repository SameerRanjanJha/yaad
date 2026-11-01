package com.sameer.yaad.remind

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.sameer.yaad.data.ItemDao
import com.sameer.yaad.data.ItemEntity
import com.sameer.yaad.data.deadline
import com.sameer.yaad.data.toEpochMillis
import com.sameer.yaad.data.toLocalDateTime
import java.time.LocalDateTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps a single alarm set for the next reminder due across all open items. Every run works
 * everything out again from the database, so there are no per-item alarms to go stale when an
 * item is edited, a reboot clears the alarms, or the clock changes.
 */
class ReminderEngine(private val context: Context, private val items: ItemDao) {
    private val mutex = Mutex()

    suspend fun sync(now: LocalDateTime = LocalDateTime.now()) = mutex.withLock {
        val open = items.openWithDeadline()
        for (item in open) notifyIfDue(item, now)
        scheduleAlarm(open.mapNotNull { nextFire(it, now) }.minOrNull())
    }

    private suspend fun notifyIfDue(item: ItemEntity, now: LocalDateTime) {
        val deadline = item.deadline ?: return
        val due = ReminderPolicy.dueToFire(
            deadline,
            setAt = item.deadlineSetAt.toLocalDateTime(),
            lastNotifiedAt = item.lastNotifiedAt?.toLocalDateTime(),
            now = now,
        )
        if (due != null) {
            Notifications.showDeadline(context, item, now.toLocalDate())
            items.markNotified(item.id, now.toEpochMillis())
        }
    }

    private fun nextFire(item: ItemEntity, now: LocalDateTime): LocalDateTime? =
        item.deadline?.let { ReminderPolicy.nextFire(it, item.deadlineSetAt.toLocalDateTime(), now) }

    private fun scheduleAlarm(at: LocalDateTime?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val wakeUp = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_ALARM),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (at == null) {
            alarms.cancel(wakeUp)
            return
        }
        // Exact alarms are auto-granted on Android 13+ through USE_EXACT_ALARM.
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMillis(), wakeUp)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMillis(), wakeUp)
        }
    }
}
