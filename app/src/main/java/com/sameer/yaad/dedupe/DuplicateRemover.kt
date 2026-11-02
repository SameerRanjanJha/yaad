package com.sameer.yaad.dedupe

import com.sameer.yaad.data.Bucket
import com.sameer.yaad.data.ItemEntity
import com.sameer.yaad.data.deadline
import com.sameer.yaad.data.withDeadline
import com.sameer.yaad.parse.ItemKind
import com.sameer.yaad.parse.TitleWords

/**
 * Finds things said twice on the open list. Losing a real task defeats the point of the app, while
 * a leftover duplicate is only a small annoyance, so anything uncertain stays on the list.
 *
 * The oldest item stays and takes on anything the copy adds, such as a deadline. The copy goes to
 * Discarded. Each item is checked once, so restoring one that was wrongly removed makes it stick.
 */
internal object DuplicateRemover {
    class Pass(val changed: List<ItemEntity>, val removed: List<ItemEntity>)

    fun run(openItems: List<ItemEntity>, now: Long): Pass {
        val kept = mutableListOf<ItemEntity>()
        val changed = linkedMapOf<Long, ItemEntity>()
        val removed = mutableListOf<ItemEntity>()
        for (item in openItems.sortedBy { it.id }) {
            if (item.duplicateCheckedAt != null) {
                kept += item
                continue
            }
            val matchIndex = kept.indexOfFirst { sameThing(it, item) }
            if (matchIndex < 0) {
                val checked = item.copy(duplicateCheckedAt = now)
                kept += checked
                changed[item.id] = checked
            } else {
                val merged = merge(kept[matchIndex], item, now)
                val copy = item.copy(bucket = Bucket.DISCARDED, duplicateOfId = merged.id, duplicateCheckedAt = now, updatedAt = now)
                kept[matchIndex] = merged
                changed[merged.id] = merged
                changed[copy.id] = copy
                removed += copy
            }
        }
        return Pass(changed.values.toList(), removed)
    }

    private fun sameThing(older: ItemEntity, newer: ItemEntity): Boolean =
        deadlinesAgree(older, newer) && titlesMatch(older, newer)

    /** "Pay rent by the 5th" said again for next month is a new task, not a repeat. */
    private fun deadlinesAgree(older: ItemEntity, newer: ItemEntity): Boolean {
        val first = older.deadline ?: return true
        val second = newer.deadline ?: return true
        return first.date == second.date && (first.time == null || second.time == null || first.time == second.time)
    }

    private fun titlesMatch(older: ItemEntity, newer: ItemEntity): Boolean {
        val olderWords = meaningWords(older.title)
        val newerWords = meaningWords(newer.title)
        if (olderWords.isEmpty() || newerWords.isEmpty()) return false
        if (olderWords == newerWords) return true
        // A task and an idea can share a few words by chance, so only identical wording links them.
        if (older.kind != newer.kind) return false
        val (shorter, longer) = if (olderWords.size < newerWords.size) olderWords to newerWords else newerWords to olderWords
        return shorter.size >= 2 &&
            longer.containsAll(shorter) &&
            !TitleWords.changeTheMeaning(longer - shorter) &&
            shorter.size * 3 >= longer.size * 2
    }

    private fun merge(older: ItemEntity, newer: ItemEntity, now: Long): ItemEntity {
        val deadline = mergedDeadline(older, newer)
        val deadlineChanged = older.deadline != deadline
        return older.withDeadline(deadline).copy(
            kind = if (newer.kind == ItemKind.TASK) ItemKind.TASK else older.kind,
            title = if (meaningWords(newer.title).size > meaningWords(older.title).size) newer.title else older.title,
            detail = listOfNotNull(older.detail, newer.detail).distinct().joinToString(" ").ifEmpty { null },
            bucket = if (newer.bucket == Bucket.IN_PROGRESS) Bucket.IN_PROGRESS else older.bucket,
            updatedAt = now,
            deadlineSetAt = if (deadlineChanged) now else older.deadlineSetAt,
            lastNotifiedAt = if (deadlineChanged) null else older.lastNotifiedAt,
        )
    }

    private fun mergedDeadline(older: ItemEntity, newer: ItemEntity) = older.deadline.let { first ->
        val second = newer.deadline
        when {
            first == null -> second
            second == null -> first
            else -> first.copy(time = first.time ?: second.time)
        }
    }

    private fun meaningWords(title: String): Set<String> = TitleWords.of(title)
}
