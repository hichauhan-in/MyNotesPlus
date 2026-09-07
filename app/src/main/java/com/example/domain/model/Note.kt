package com.example.domain.model

import androidx.compose.runtime.Immutable

/** The kind of note: free text, an interactive checklist, an expense tracker, or a whiteboard. */
enum class NoteType { TEXT, CHECKLIST, EXPENSE, SCRIBBLE }

/**
 * Decrypted, in-memory representation of a note. Instances of this class only ever
 * exist in RAM - everything written to disk is encrypted (see NoteEntity).
 */
@Immutable
data class Note(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    val folderId: String? = null,
    val tags: List<String> = emptyList(),
    val colorArgb: Int = 0,
    val type: NoteType = NoteType.TEXT,
    /** File names of image attachments stored in the app-private attachments dir. */
    val attachments: List<String> = emptyList(),
) {
    val isChecklist: Boolean get() = type == NoteType.CHECKLIST
    val isExpense: Boolean get() = type == NoteType.EXPENSE
    val isScribble: Boolean get() = type == NoteType.SCRIBBLE

    val preview: String
        get() = when {
            isChecklist -> Checklist.parse(content).joinToString("   ") {
                (if (it.checked) "✓ " else "○ ") + it.text
            }.trim()
            isExpense -> "Expense tracker"
            isScribble -> "Board"
            else -> AttachmentMarkup.stripTokens(content).trim().replace(Regex("\\s+"), " ")
        }

    val wordCount: Int
        get() = AttachmentMarkup.stripTokens(content).trim()
            .let { if (it.isEmpty()) 0 else it.split(Regex("\\s+")).size }

    /** For checklist notes: number of completed items and total items. */
    val checklistDone: Int get() = if (isChecklist) Checklist.parse(content).count { it.checked } else 0
    val checklistTotal: Int get() = if (isChecklist) Checklist.parse(content).count { it.text.isNotBlank() } else 0
}

@Immutable
data class Folder(
    val id: String,
    val name: String,
    val colorArgb: Int = 0,
    val parentId: String? = null,
    val isTrashed: Boolean = false,
)
