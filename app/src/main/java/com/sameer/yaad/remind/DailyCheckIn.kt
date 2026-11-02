package com.sameer.yaad.remind

import com.sameer.yaad.data.Bucket
import com.sameer.yaad.data.ItemEntity
import com.sameer.yaad.data.deadline
import com.sameer.yaad.ui.sortForBucket
import java.time.LocalDateTime

/** The once-a-day look at every task still open, in the same order as the list. */
internal object DailyCheckIn {
    private const val MAX_LINES = 6

    class Summary(val title: String, val lines: List<String>)

    /** Null when nothing is pending, since there's nothing to check. */
    fun summarize(openTasks: List<ItemEntity>, now: LocalDateTime): Summary? {
        if (openTasks.isEmpty()) return null
        val overdue = openTasks.count { item -> item.deadline?.let { DeadlineFormat.isOverdue(it, now) } == true }
        val title = "${plural(openTasks.size, "task")} pending" + if (overdue > 0) ", $overdue overdue" else ""
        val lines = sortForBucket(openTasks, Bucket.TODO).take(MAX_LINES).map { line(it, now) }
        val more = openTasks.size - lines.size
        return Summary(title, if (more > 0) lines + "and $more more" else lines)
    }

    private fun line(item: ItemEntity, now: LocalDateTime): String {
        val deadline = item.deadline ?: return item.title
        val label = DeadlineFormat.label(deadline, now.toLocalDate())
        return if (DeadlineFormat.isOverdue(deadline, now)) "${item.title} (overdue: $label)" else "${item.title} ($label)"
    }
}

internal fun plural(count: Int, noun: String) = if (count == 1) "1 $noun" else "$count ${noun}s"
