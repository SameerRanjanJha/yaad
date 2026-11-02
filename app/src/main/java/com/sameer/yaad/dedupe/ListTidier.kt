package com.sameer.yaad.dedupe

import com.sameer.yaad.data.Bucket
import com.sameer.yaad.data.ItemEntity
import com.sameer.yaad.data.deadline
import com.sameer.yaad.data.withDeadline
import com.sameer.yaad.parse.NoteTidier
import com.sameer.yaad.parse.ParsedItem

/**
 * Tidies the items already on the list, note by note, the way a new note is tidied as it's sorted.
 * It runs once, for lists made before notes were tidied.
 *
 * Only items still as they were sorted are touched, so anything edited, started or finished stays
 * as it is. An item folded into another goes to Discarded and points at it, so it can be restored.
 */
internal object ListTidier {
    class Pass(val changed: List<ItemEntity>, val removed: List<ItemEntity>)

    fun run(items: List<ItemEntity>, now: Long): Pass {
        val changed = mutableListOf<ItemEntity>()
        val removed = mutableListOf<ItemEntity>()
        val asSorted = items.filter { isAsSorted(it, items) }
        for (noteItems in asSorted.groupBy { it.noteId }.values) {
            val inOrder = noteItems.sortedBy { it.id }
            for (group in NoteTidier.group(inOrder.map { it.toParsedItem() })) {
                if (group.sources.size < 2) continue
                val kept = tidy(inOrder[group.sources.first()], group.item, now)
                val folded = group.sources.drop(1).map {
                    inOrder[it].copy(bucket = Bucket.DISCARDED, duplicateOfId = kept.id, duplicateCheckedAt = now, updatedAt = now)
                }
                changed += kept
                changed += folded
                removed += folded
            }
        }
        return Pass(changed, removed)
    }

    /**
     * Untouched since it was sorted, apart from duplicate removal merging a later copy into it,
     * which leaves the copy with the same update time.
     */
    private fun isAsSorted(item: ItemEntity, items: List<ItemEntity>): Boolean =
        item.bucket == Bucket.TODO &&
            (item.updatedAt == item.createdAt || items.any { it.duplicateOfId == item.id && it.updatedAt == item.updatedAt })

    private fun tidy(item: ItemEntity, tidied: ParsedItem, now: Long): ItemEntity {
        val deadlineChanged = item.deadline != tidied.deadline
        return item.withDeadline(tidied.deadline).copy(
            kind = tidied.kind,
            title = tidied.title,
            detail = tidied.detail,
            updatedAt = now,
            deadlineSetAt = if (deadlineChanged) now else item.deadlineSetAt,
            lastNotifiedAt = if (deadlineChanged) null else item.lastNotifiedAt,
            // A new title or date can make it a repeat of something from another note.
            duplicateCheckedAt = null,
        )
    }

    private fun ItemEntity.toParsedItem() = ParsedItem(kind, title, detail, deadline)
}
