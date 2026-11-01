package com.sameer.yaad.parse

import java.time.LocalDate
import java.time.LocalTime

enum class ItemKind { TASK, IDEA }

data class Deadline(val date: LocalDate, val time: LocalTime? = null)

data class ParsedItem(
    val kind: ItemKind,
    val title: String,
    val detail: String? = null,
    val deadline: Deadline? = null,
)
