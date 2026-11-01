package com.sameer.yaad.parse

import java.time.LocalDateTime
import org.json.JSONException
import org.json.JSONObject

/**
 * Sorts a note using a language model on the phone. The model decides what the items are and
 * what kind they are. It isn't trusted with anything else: dates are worked out by the tested
 * [DeadlineExtractor] from words the model must quote from the note, anything it invents is
 * dropped, and anything it misses that the rules caught is added back. If it fails in any way,
 * the note is sorted by the rules instead, so nothing is lost.
 */
class AiNoteParser(
    private val generate: (prompt: String) -> String?,
    private val rules: NoteParser,
    private val prefill: String,
) {
    fun parse(transcript: String, now: LocalDateTime): List<ParsedItem> {
        val fromRules = rules.parse(transcript, now)
        val fromModel = chunks(transcript).flatMap { chunk ->
            readModelItems(chunk, transcript, now) ?: return fromRules
        }
        if (fromModel.isEmpty()) return fromRules
        return fromModel.map { recoverMissedDeadline(it, fromRules) } + itemsTheModelMissed(fromModel, fromRules)
    }

    private fun readModelItems(chunk: String, transcript: String, now: LocalDateTime): List<ParsedItem>? {
        val reply = generate(AiNotePrompt.build(chunk, prefill)) ?: return null
        val array = try {
            JSONObject(reply).getJSONArray("items")
        } catch (_: JSONException) {
            return null
        }
        return (0 until array.length()).mapNotNull { index ->
            val item = array.getJSONObject(index)
            toParsedItem(item.getString("type"), item.getString("title"), item.getString("when"), transcript, now)
        }
    }

    private fun toParsedItem(type: String, rawTitle: String, rawWhen: String, transcript: String, now: LocalDateTime): ParsedItem? {
        val kind = if (type == "task") ItemKind.TASK else ItemKind.IDEA
        val titleWithoutDate = if (kind == ItemKind.TASK) DeadlineExtractor(now).extract(rawTitle).remainder else rawTitle
        val title = Language.cleanTitle(titleWithoutDate)
        if (title.isBlank() || !comesFromNote(title, transcript)) return null
        val deadline = if (kind == ItemKind.TASK) resolveDeadline(rawWhen, transcript, now) else null
        return ParsedItem(kind, title, deadline = deadline)
    }

    private fun resolveDeadline(spoken: String, transcript: String, now: LocalDateTime): Deadline? {
        val words = spoken.trim()
        if (words.isEmpty() || !transcript.contains(words, ignoreCase = true)) return null
        val extractor = DeadlineExtractor(now)
        // "5th" only reads as a date after a word like "by", which the model may have left out.
        return listOf(words, "by $words", "on $words").firstNotNullOfOrNull { extractor.extract(it).deadline }
    }

    /** A date the rules heard for the same task is kept, since a missed deadline is the costliest mistake. */
    private fun recoverMissedDeadline(item: ParsedItem, fromRules: List<ParsedItem>): ParsedItem {
        if (item.kind != ItemKind.TASK || item.deadline != null) return item
        val itemWords = contentWords(item.title)
        if (itemWords.isEmpty()) return item
        val match = fromRules.firstOrNull { rule ->
            rule.deadline != null && contentWords(rule.title).intersect(itemWords).size * 2 >= itemWords.size
        }
        return match?.let { item.copy(deadline = it.deadline) } ?: item
    }

    /** Items only the rules found. A stray extra item is better than a lost one. */
    private fun itemsTheModelMissed(fromModel: List<ParsedItem>, fromRules: List<ParsedItem>): List<ParsedItem> {
        val covered = fromModel.flatMap { contentWords(it.title) }.toSet()
        return fromRules.filter { rule ->
            val words = contentWords(rule.title)
            // The rules' titles run long, so a partly covered one is usually the same thing said at length.
            words.isNotEmpty() && words.count { it in covered } * 3 < words.size
        }
    }

    private fun comesFromNote(title: String, transcript: String): Boolean {
        val words = contentWords(title)
        if (words.isEmpty()) return true
        val spoken = contentWords(transcript)
        val copiedFromExample = AiNotePrompt.EXAMPLE_TOPICS.any { topic ->
            topic.take(STEM_LENGTH).let { it in words && it !in spoken }
        }
        return !copiedFromExample && words.count { it in spoken } * 2 >= words.size
    }

    /** Word starts rather than whole words, so "automating" matches "automate". */
    private fun contentWords(text: String): Set<String> =
        WORD.findAll(text.lowercase())
            .map { it.value }
            .filter { it.length >= MIN_WORD_LENGTH && it !in STOP_WORDS }
            .map { it.take(STEM_LENGTH) }
            .toSet()

    /** Long notes go through in pieces, since a small model only reads so much at a time. */
    private fun chunks(transcript: String): List<String> {
        if (transcript.length <= MAX_CHUNK_CHARS) return listOf(transcript)
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (word in transcript.split(' ')) {
            current.append(word).append(' ')
            val endsSentence = word.endsWith('.') || word.endsWith('?') || word.endsWith('!')
            if ((endsSentence && current.length >= MIN_CHUNK_CHARS) || current.length >= MAX_CHUNK_CHARS) {
                result += current.toString().trim()
                current.clear()
            }
        }
        if (current.isNotBlank()) result += current.toString().trim()
        return result
    }

    private companion object {
        val WORD = Regex("""[\p{L}\p{N}']+""")
        val STOP_WORDS = setOf(
            "the", "and", "for", "with", "about", "from", "that", "this", "these", "those", "then", "than",
            "you", "your", "our", "his", "her", "they", "them", "can", "could", "should", "would", "will",
            "have", "has", "had", "are", "was", "were", "been", "but", "not", "what", "when", "how", "why",
            "also", "maybe", "some", "there", "here", "into", "out", "use", "using",
            "don't", "want", "it's", "i'm", "i'll", "one", "thing", "try",
        )
        const val STEM_LENGTH = 5
        const val MIN_WORD_LENGTH = 3
        const val MIN_CHUNK_CHARS = 500
        const val MAX_CHUNK_CHARS = 900
    }
}
