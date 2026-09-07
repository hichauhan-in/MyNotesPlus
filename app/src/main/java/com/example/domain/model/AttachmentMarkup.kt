package com.example.domain.model

/** The kind of inline attachment a token refers to. */
enum class AttachmentKind { IMAGE, AUDIO }

/** A parsed inline attachment: its kind, backing file name, and (images) width percent. */
data class AttachmentRef(
    val kind: AttachmentKind,
    val fileName: String,
    val widthPercent: Int? = null,
)

/**
 * Inline attachment markup for note content. Images and voice notes live in the note
 * body as a token on their own line, so text and media can be interleaved in any order:
 *   - image:  ![img](attachment://FILE_NAME)          (optionally ...?w=65 for 65% width)
 *   - audio:  ![audio](attachment://FILE_NAME)
 * Only the file name is stored; the bytes live in the app-private attachments directory.
 */
object AttachmentMarkup {

    private val TOKEN = Regex("""!\[([^\]]*)]\(attachment://([^)\s?]+)(?:\?w=(\d+))?\)""")
    private val TABLE = Regex("""\[\[table:[A-Za-z0-9+/=]+]]""")
    private val CALLOUT = Regex("""\[\[callout:[A-Za-z0-9+/=]+]]""")
    private val SCRIBBLE = Regex("""\[\[scribble:[A-Za-z0-9+/=]+]]""")
    private val INK = Regex("""\[\[ink:[A-Za-z0-9+/=]+]]""")

    /** The inline token for an image, optionally with a display width percent (10-100). */
    fun imageToken(fileName: String, widthPercent: Int? = null): String =
        if (widthPercent != null) "![img](attachment://$fileName?w=$widthPercent)"
        else "![img](attachment://$fileName)"

    /** The inline token for a voice note. */
    fun audioToken(fileName: String): String = "![audio](attachment://$fileName)"

    /** File names of every inline attachment (images + audio), in order. Used for cleanup. */
    fun fileNames(content: String): List<String> =
        TOKEN.findAll(content).map { it.groupValues[2] }.toList()

    fun renameFiles(content: String, replacements: Map<String, String>): String = TOKEN.replace(content) { match ->
        val name = replacements[match.groupValues[2]] ?: return@replace match.value
        val width = match.groupValues[3].takeIf { it.isNotEmpty() }?.let { "?w=$it" }.orEmpty()
        "![${match.groupValues[1]}](attachment://$name$width)"
    }

    /** If [line] is exactly an attachment token, returns its parsed reference; otherwise null. */
    fun parseLine(line: String): AttachmentRef? {
        val match = TOKEN.matchEntire(line.trim()) ?: return null
        val kind = if (match.groupValues[1] == "audio") AttachmentKind.AUDIO else AttachmentKind.IMAGE
        return AttachmentRef(
            kind = kind,
            fileName = match.groupValues[2],
            widthPercent = match.groupValues[3].toIntOrNull(),
        )
    }

    /** Content with attachment tokens removed - used for previews, word counts, etc. */
    fun stripTokens(content: String): String = content
        .replace(TOKEN, " ")
        .replace(TABLE, " ")
        .replace(CALLOUT, " ")
        .replace(SCRIBBLE, " ")
        .replace(INK, " ")
}
