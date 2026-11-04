package com.sameer.yaad.remind

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import com.sameer.yaad.R
import com.sameer.yaad.capture.CaptureActivity
import com.sameer.yaad.capture.CaptureService
import com.sameer.yaad.data.ItemEntity
import com.sameer.yaad.data.deadline
import com.sameer.yaad.parse.ItemKind
import com.sameer.yaad.parse.ParsedItem
import com.sameer.yaad.ui.MainActivity
import java.time.LocalDate

object Notifications {
    const val FOREGROUND_ID = 1
    private const val LOCK_SCREEN_MIC_ID = 2
    private const val DUPLICATES_ID = 3
    private const val DAILY_CHECK_IN_ID = 4
    private const val NOTES_REREAD_ID = 5
    private const val DEADLINE_ID_BASE = 10_000
    private const val NOTE_RESULT_ID_BASE = 1_000_000

    private const val CHANNEL_DEADLINES = "deadlines"
    private const val CHANNEL_CHECK_IN = "daily_check_in"
    private const val CHANNEL_QUICK_CAPTURE = "quick_capture"
    private const val CHANNEL_WORKING = "working"
    private const val CHANNEL_RESULTS = "results"

    private const val RESULT_VISIBLE_MS = 60_000L

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_DEADLINES, "Deadline reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
                NotificationChannel(CHANNEL_CHECK_IN, "Daily check-in", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
                // Default importance but no sound: Android can hide "silent" notifications from
                // the lock screen, and this one exists only to be on the lock screen.
                NotificationChannel(CHANNEL_QUICK_CAPTURE, "Lock-screen mic", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(CHANNEL_WORKING, "Recording", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(CHANNEL_RESULTS, "What was saved", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            ),
        )
    }

    fun recording(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_WORKING)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Recording your note")
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openCapture(context))
            .addAction(
                R.drawable.ic_stop,
                "Stop and save",
                PendingIntent.getService(
                    context,
                    0,
                    CaptureService.intent(context, CaptureService.ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    /**
     * The lock-screen mic. Tapping the notification itself opens the capture screen straight over
     * the lock screen. There's no action button on purpose: Android asks for an unlock first when
     * one of those opens an activity.
     */
    fun showLockScreenMic(context: Context, whenDismissed: PendingIntent) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_QUICK_CAPTURE)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Tap to record a voice note")
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openCapture(context))
            .setDeleteIntent(whenDismissed)
            .build()
        manager(context).notify(LOCK_SCREEN_MIC_ID, notification)
    }

    fun hideLockScreenMic(context: Context) = manager(context).cancel(LOCK_SCREEN_MIC_ID)

    fun showDeadline(context: Context, item: ItemEntity, today: LocalDate) {
        if (!canPost(context)) return
        val deadline = item.deadline ?: return
        val done = PendingIntent.getBroadcast(
            context,
            item.id.toInt(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_MARK_DONE)
                .putExtra(ReminderReceiver.EXTRA_ITEM_ID, item.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_DEADLINES)
            .setSmallIcon(R.drawable.ic_task)
            .setContentTitle(DeadlineFormat.dueIn(deadline, today))
            .setContentText(item.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(item.title, item.detail).joinToString("\n")))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .addAction(R.drawable.ic_check, "Mark done", done)
            .build()
        manager(context).notify(deadlineNotificationId(item.id), notification)
    }

    fun cancelDeadline(context: Context, itemId: Long) = manager(context).cancel(deadlineNotificationId(itemId))

    /** Replaces yesterday's list. With nothing pending, that list just goes away. */
    internal fun showDailyCheckIn(context: Context, summary: DailyCheckIn.Summary?) {
        if (summary == null) {
            manager(context).cancel(DAILY_CHECK_IN_ID)
            return
        }
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_CHECK_IN)
            .setSmallIcon(R.drawable.ic_task)
            .setContentTitle(summary.title)
            .setContentText(summary.lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary.lines.joinToString("\n")))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        manager(context).notify(DAILY_CHECK_IN_ID, notification)
    }

    /** Confirms what was heard, so a misheard note gets noticed right away. */
    fun showCaptureResult(context: Context, noteId: Long, items: List<ParsedItem>) {
        if (!canPost(context)) return
        val tasks = items.count { it.kind == ItemKind.TASK }
        val ideas = items.size - tasks
        val summary = listOfNotNull(
            plural(tasks, "task").takeIf { tasks > 0 },
            plural(ideas, "idea").takeIf { ideas > 0 },
        ).joinToString(" and ")
        val titles = items.joinToString("\n") { it.title }
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle("Added to your list: $summary")
            .setContentText(titles)
            .setStyle(NotificationCompat.BigTextStyle().bigText(titles))
            .setAutoCancel(true)
            .setTimeoutAfter(RESULT_VISIBLE_MS)
            .setContentIntent(openApp(context))
            .build()
        manager(context).notify(noteResultId(noteId), notification)
    }

    fun showDuplicatesRemoved(context: Context, removed: List<ItemEntity>) =
        showMovedToDiscarded(context, "Moved ${plural(removed.size, "duplicate")} to Discarded", removed)

    fun showListTidied(context: Context, removed: List<ItemEntity>) =
        showMovedToDiscarded(context, "Tidied your list: merged ${plural(removed.size, "item")} into others", removed)

    fun showNotesReread(context: Context, notes: Int) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle("Read ${plural(notes, "older note")} again")
            .setContentText("Their items are updated. The old ones are in Discarded.")
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        manager(context).notify(NOTES_REREAD_ID, notification)
    }

    /** Says what went where, so a wrong call gets noticed and can be restored. */
    private fun showMovedToDiscarded(context: Context, title: String, removed: List<ItemEntity>) {
        if (!canPost(context)) return
        val titles = removed.joinToString("\n") { it.title }
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle(title)
            .setContentText(titles)
            .setStyle(NotificationCompat.BigTextStyle().bigText(titles))
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        manager(context).notify(DUPLICATES_ID, notification)
    }

    fun showCaptureFailed(context: Context, noteId: Long) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_close)
            .setContentTitle("Couldn't write down your note")
            .setContentText("The audio is safe. Open Log to retry.")
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        manager(context).notify(noteResultId(noteId), notification)
    }

    /** One per note: analysis can finish several in a row, and each result should be seen. */
    private fun noteResultId(noteId: Long) = NOTE_RESULT_ID_BASE + noteId.toInt()

    private fun deadlineNotificationId(itemId: Long) = DEADLINE_ID_BASE + itemId.toInt()

    private fun openCapture(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, CaptureActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        1,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun canPost(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)
}
