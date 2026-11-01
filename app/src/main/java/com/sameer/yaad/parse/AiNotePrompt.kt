package com.sameer.yaad.parse

/** The prompt and answer format for the language model. */
internal object AiNotePrompt {
    /** Forces the model to answer in this exact JSON shape, so its reply can always be read. */
    const val GRAMMAR = """root ::= "{\"items\":[" item ("," item)* "]}"
item ::= "{\"type\":" type ",\"title\":" string ",\"when\":" string "}"
type ::= "\"task\"" | "\"idea\""
string ::= "\"" char* "\""
char ::= [^"\\\x00-\x1F]
"""

    private const val SYSTEM = """You turn a person's spoken note into their personal to-do list. The person is talking to themselves. Every task is something THE PERSON must do. You never do anything yourself.

Each item has:
- type: "task" if it is something the person has to do, such as an errand, a call, a payment or an appointment, or anything with a date. "idea" if it is a thought, a suggestion, a possibility, or something they might make or try one day. Words like could, maybe, what if and I was wondering mean it is an idea.
- title: short, in the person's own words, without the date. A task starts with a verb. Leave out wording like "remind me", "set a reminder", "add a task", "I need to", "can you".
- when: the exact date or time words from the note, or "" if there are none.

Put each separate thing in its own item. Keep everything the person said. Invent nothing."""

    private val EXAMPLES = listOf(
        "Set a reminder to call mom tomorrow at 5 pm." to
            """{"items":[{"type":"task","title":"Call mom","when":"tomorrow at 5 pm"}]}""",
        "Pay the rent by Friday, and I had an idea, we could start a newsletter for founders." to
            """{"items":[{"type":"task","title":"Pay the rent","when":"by Friday"},{"type":"idea","title":"Start a newsletter for founders","when":""}]}""",
        "So I was thinking the onboarding is too long. Also I have to send the report to Vivek and book a cab for Monday morning." to
            """{"items":[{"type":"idea","title":"The onboarding is too long","when":""},{"type":"task","title":"Send the report to Vivek","when":""},{"type":"task","title":"Book a cab","when":"Monday morning"}]}""",
        "Tomorrow I have to submit the form. The shop could sell gift cards, maybe online too. Build a small app for tracking habits." to
            """{"items":[{"type":"task","title":"Submit the form","when":"Tomorrow"},{"type":"idea","title":"The shop could sell gift cards, maybe online too","when":""},{"type":"idea","title":"Build a small app for tracking habits","when":""}]}""",
    )

    /** Subjects only the examples talk about. Seeing one in a reply means the model copied it from them. */
    val EXAMPLE_TOPICS = setOf("mom", "rent", "newsletter", "founders", "onboarding", "vivek", "cab", "form", "shop", "gift", "cards", "habits", "tracking")

    /** Builds ChatML, the format this model expects. `prefill` starts the model's reply. */
    fun build(note: String, prefill: String): String = buildString {
        turn("system", SYSTEM)
        for ((said, answer) in EXAMPLES) {
            turn("user", said)
            turn("assistant", prefill + answer)
        }
        turn("user", note)
        append("<|im_start|>assistant\n").append(prefill)
    }

    private fun StringBuilder.turn(role: String, text: String) {
        append("<|im_start|>").append(role).append('\n').append(text).append("<|im_end|>\n")
    }
}
