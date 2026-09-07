package com.example.domain.model

/** A single row in a checklist note. */
data class ChecklistItem(
    val text: String,
    val checked: Boolean,
)

/**
 * Checklist notes are stored as plain text (so they stay encrypted like everything else)
 * using a simple, human-readable, Markdown-friendly line format:
 *
 * ```
 * [x] Buy milk
 * [ ] Walk the dog
 * ```
 */
object Checklist {
    private val LINE = Regex("^(?:- )?\\[( |x|X)]\\s?(.*)$")

    fun parse(content: String): List<ChecklistItem> =
        content.lines().mapNotNull { raw ->
            val trimmed = raw.trimEnd()
            if (trimmed.isBlank()) return@mapNotNull null
            val match = LINE.find(trimmed.trimStart())
            if (match != null) {
                ChecklistItem(
                    text = match.groupValues[2].trim(),
                    checked = match.groupValues[1].equals("x", ignoreCase = true),
                )
            } else {
                // Tolerate a plain line by treating it as an unchecked item.
                ChecklistItem(text = trimmed.trim(), checked = false)
            }
        }

    fun serialize(items: List<ChecklistItem>): String =
        items.joinToString("\n") { (if (it.checked) "[x] " else "[ ] ") + it.text }
}
