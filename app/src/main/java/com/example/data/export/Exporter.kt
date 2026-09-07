package com.example.data.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Base64
import com.example.data.attachments.AttachmentStore
import com.example.domain.model.Checklist
import com.example.domain.model.Folder
import com.example.domain.model.Note
import com.example.domain.model.NoteType
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A file format a note or book can be exported to. */
enum class ExportFormat(val label: String, val ext: String, val mime: String) {
    TXT("Plain text", "txt", "text/plain"),
    MD("Markdown", "md", "text/markdown"),
    HTML("Web page", "html", "text/html"),
    PDF("PDF document", "pdf", "application/pdf"),
}

/**
 * Converts decrypted [Note]s into shareable files. Notes only ever reach this object already
 * decrypted (as [Note] objects), so nothing here touches ciphertext or keys.
 */
object Exporter {

    private val IMG = Regex("""^!\[([^\]]*)]\(attachment://([^)\s?]+)(?:\?w=(\d+))?\)$""")
    private val TABLE = Regex("""^\[\[table:([A-Za-z0-9+/=]+)]]$""")
    private val CALLOUT = Regex("""^\[\[callout:([A-Za-z0-9+/=]+)]]$""")
    private val SCRIBBLE = Regex("""^\[\[scribble:([A-Za-z0-9+/=]+)]]$""")
    private val INK = Regex("""^\[\[ink:([A-Za-z0-9+/=]+)]]$""")

    // ---- Public API -------------------------------------------------------------

    /** The bytes of [note] rendered in [format]. */
    fun noteBytes(note: Note, format: ExportFormat): ByteArray = when (format) {
        ExportFormat.TXT -> plainDoc(note).toByteArray(Charsets.UTF_8)
        ExportFormat.MD -> markdownDoc(note).toByteArray(Charsets.UTF_8)
        ExportFormat.HTML -> htmlDoc(note).toByteArray(Charsets.UTF_8)
        ExportFormat.PDF -> pdfBytes(note)
    }

    /** A filesystem-safe base file name (no extension) for [note]. */
    fun noteFileBase(note: Note): String = safe(note.title.ifBlank { "Untitled" })

    /** A filesystem-safe fragment for a file/folder name. */
    fun safe(name: String): String =
        name.trim()
            .replace(Regex("""[\\/:*?"<>|\r\n\t]+"""), "_")
            .trim('_', '.', ' ')
            .ifBlank { "Untitled" }
            .take(80)

    /**
     * Streams a single note into [out] as a ZIP: the note file in [format] plus every image /
     * voice attachment in an `attachments/` folder, so a note with media stays self-contained.
     */
    fun writeNoteZip(context: Context, note: Note, format: ExportFormat, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            val base = noteFileBase(note)
            zip.putNextEntry(ZipEntry("$base.${format.ext}"))
            zip.write(noteBytes(note, format))
            zip.closeEntry()
            note.attachments.forEach { att ->
                runCatching {
                    val bytes = AttachmentStore.readDecrypted(context, att)
                    if (bytes != null) {
                        zip.putNextEntry(ZipEntry("attachments/${AttachmentStore.fileFor(context, att).name}"))
                        zip.write(bytes)
                        zip.closeEntry()
                    }
                }
            }
        }
    }

    /**
     * Streams a book (the folder [rootId] and its whole subtree) into [out] as a ZIP: real
     * subfolders mirror the book hierarchy, each note is a file in [format], and every image /
     * voice attachment is copied into an `attachments/` folder beside its note.
     */
    fun writeBookZip(
        context: Context,
        rootId: String,
        folders: List<Folder>,
        notes: List<Note>,
        format: ExportFormat,
        out: OutputStream,
    ) {
        val active = folders.filter { !it.isTrashed }
        val childrenOf = active.groupBy { it.parentId }
        val notesByFolder = notes.filter { !it.isTrashed && !it.isArchived }.groupBy { it.folderId }
        val root = folders.firstOrNull { it.id == rootId } ?: return
        ZipOutputStream(out).use { zip ->
            fun put(path: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry()
            }
            fun walk(folderId: String, folderName: String, prefix: String) {
                val dirPath = prefix + safe(folderName) + "/"
                zip.putNextEntry(ZipEntry(dirPath)); zip.closeEntry()
                val used = HashSet<String>()
                notesByFolder[folderId].orEmpty().forEach { note ->
                    val base = noteFileBase(note)
                    var name = base
                    var i = 2
                    while (!used.add(name.lowercase())) { name = "$base ($i)"; i++ }
                    put("$dirPath$name.${format.ext}", noteBytes(note, format))
                    note.attachments.forEach { att ->
                        runCatching {
                            val bytes = AttachmentStore.readDecrypted(context, att)
                            if (bytes != null) put("${dirPath}attachments/${AttachmentStore.fileFor(context, att).name}", bytes)
                        }
                    }
                }
                childrenOf[folderId].orEmpty().sortedBy { it.name.lowercase() }.forEach { child ->
                    walk(child.id, child.name, dirPath)
                }
            }
            walk(root.id, root.name, "")
        }
    }

    // ---- Whole-document assembly ------------------------------------------------

    private fun plainDoc(note: Note): String = buildString {
        val t = note.title.ifBlank { "Untitled" }
        appendLine(t)
        appendLine("=".repeat(t.length.coerceIn(3, 50)))
        if (note.tags.isNotEmpty()) appendLine(note.tags.joinToString(" ") { "#$it" })
        appendLine()
        append(bodyText(note))
    }

    private fun markdownDoc(note: Note): String = buildString {
        appendLine("# " + note.title.ifBlank { "Untitled" })
        appendLine()
        if (note.tags.isNotEmpty()) {
            appendLine(note.tags.joinToString(" ") { "#$it" })
            appendLine()
        }
        append(bodyMd(note))
    }

    private fun htmlDoc(note: Note): String = buildString {
        val t = esc(note.title.ifBlank { "Untitled" })
        append("<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        append("<title>").append(t).append("</title><style>")
        append("body{font-family:system-ui,-apple-system,'Segoe UI',Roboto,sans-serif;max-width:720px;margin:32px auto;padding:0 20px;line-height:1.6;color:#1b1b1f}")
        append("h1{margin:0 0 4px}.tags{color:#6b6b70;margin:0 0 20px}")
        append("img{max-width:100%;height:auto;border-radius:10px;margin:8px 0}")
        append("table{border-collapse:collapse;margin:10px 0}td,th{border:1px solid #d0d0d5;padding:6px 10px;text-align:left}")
        append("blockquote{border-left:3px solid #b0b0b8;margin:10px 0;padding:6px 14px;background:#f5f5f8;border-radius:0 8px 8px 0}")
        append("hr{border:none;border-top:1px solid #ddd;margin:18px 0}code{background:#f0f0f3;padding:1px 5px;border-radius:5px}")
        append("ul.tasks{list-style:none;padding-left:0}</style></head><body>")
        append("<h1>").append(t).append("</h1>")
        if (note.tags.isNotEmpty()) {
            append("<div class=\"tags\">").append(note.tags.joinToString(" ") { "#" + esc(it) }).append("</div>")
        }
        append(bodyHtml(note))
        append("</body></html>")
    }

    // ---- Body by note type ------------------------------------------------------

    private fun bodyText(note: Note): String = when (note.type) {
        NoteType.CHECKLIST -> Checklist.parse(note.content)
            .joinToString("\n") { (if (it.checked) "[x] " else "[ ] ") + it.text }
        NoteType.EXPENSE -> expenseLines(note.content).joinToString("\n")
        NoteType.SCRIBBLE -> scribbleToText(note.content)
        else -> textNoteToPlain(note.content)
    }

    private fun bodyMd(note: Note): String = when (note.type) {
        NoteType.CHECKLIST -> Checklist.parse(note.content)
            .joinToString("\n") { "- [${if (it.checked) "x" else " "}] " + it.text }
        NoteType.EXPENSE -> expenseMd(note.content)
        NoteType.SCRIBBLE -> whiteboardMd(note.content)
        else -> textNoteToMd(note.content)
    }

    private fun bodyHtml(note: Note): String = when (note.type) {
        NoteType.CHECKLIST -> checklistHtml(note.content)
        NoteType.EXPENSE -> expenseHtml(note.content)
        NoteType.SCRIBBLE -> whiteboardHtml(note.content)
        else -> textNoteToHtml(note.content)
    }

    // ---- Text notes -------------------------------------------------------------

    private fun textNoteToPlain(content: String): String = buildString {
        content.split("\n").forEach { raw ->
            val l = raw.trim()
            val img = IMG.matchEntire(l)
            val tbl = TABLE.matchEntire(l)
            val cal = CALLOUT.matchEntire(l)
            when {
                img != null -> appendLine(
                    if (img.groupValues[1] == "audio") "[Voice note: ${img.groupValues[2]}]"
                    else "[Image: ${img.groupValues[2]}]",
                )
                tbl != null -> appendLine(tableToText(decodeCells(tbl.groupValues[1])))
                cal != null -> { val (e, t) = decodeCallout(cal.groupValues[1]); appendLine("$e $t".trim()) }
                SCRIBBLE.matches(l) -> appendLine("[Sketch]")
                INK.matches(l) -> appendLine("[Handwriting]")
                else -> appendLine(raw)
            }
        }
    }.trimEnd()

    private fun textNoteToMd(content: String): String = buildString {
        content.split("\n").forEach { raw ->
            val l = raw.trim()
            val img = IMG.matchEntire(l)
            val tbl = TABLE.matchEntire(l)
            val cal = CALLOUT.matchEntire(l)
            val scr = SCRIBBLE.matchEntire(l)
            val ink = INK.matchEntire(l)
            when {
                img != null -> {
                    val f = img.groupValues[2]
                    appendLine(
                        if (img.groupValues[1] == "audio") "[🔊 Voice note](attachments/$f)"
                        else "![image](attachments/$f)",
                    )
                }
                tbl != null -> { appendLine(tableToMd(decodeCells(tbl.groupValues[1]))) }
                cal != null -> { val (e, t) = decodeCallout(cal.groupValues[1]); appendLine("> $e $t".trim()) }
                scr != null -> appendLine(scribbleBlockPng(scr.groupValues[1])?.let { "![sketch](${dataUri(it)})" } ?: "_[Sketch]_")
                ink != null -> appendLine(inkPng(ink.groupValues[1])?.let { "![handwriting](${dataUri(it)})" } ?: "_[Handwriting]_")
                else -> appendLine(raw)
            }
        }
    }.trimEnd()

    private fun textNoteToHtml(content: String): String {
        val sb = StringBuilder()
        val lines = content.split("\n")
        val para = StringBuilder()
        fun flush() {
            if (para.isNotBlank()) {
                val html = para.toString().trim().split("\n").joinToString("<br>") { inlineMd(it) }
                sb.append("<p>").append(html).append("</p>")
            }
            para.setLength(0)
        }
        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val l = raw.trim()
            val img = IMG.matchEntire(l)
            val tbl = TABLE.matchEntire(l)
            val cal = CALLOUT.matchEntire(l)
            val scr = SCRIBBLE.matchEntire(l)
            val ink = INK.matchEntire(l)
            when {
                l.isEmpty() -> flush()
                img != null -> {
                    flush()
                    val f = esc(img.groupValues[2])
                    if (img.groupValues[1] == "audio") {
                        sb.append("<p>🔊 <a href=\"attachments/$f\">Voice note</a></p>")
                    } else {
                        sb.append("<img src=\"attachments/$f\" alt=\"image\">")
                    }
                }
                tbl != null -> { flush(); sb.append(tableToHtml(decodeCells(tbl.groupValues[1]))) }
                cal != null -> {
                    flush()
                    val (e, t) = decodeCallout(cal.groupValues[1])
                    sb.append("<blockquote>").append(esc(e)).append(" ").append(inlineMd(t)).append("</blockquote>")
                }
                scr != null -> {
                    flush()
                    val png = scribbleBlockPng(scr.groupValues[1])
                    if (png != null) sb.append("<img src=\"").append(dataUri(png)).append("\" alt=\"sketch\">")
                    else sb.append("<p><em>[Sketch]</em></p>")
                }
                ink != null -> {
                    flush()
                    val png = inkPng(ink.groupValues[1])
                    if (png != null) sb.append("<img src=\"").append(dataUri(png)).append("\" alt=\"handwriting\">")
                    else sb.append("<p><em>[Handwriting]</em></p>")
                }
                l.startsWith("### ") -> { flush(); sb.append("<h3>").append(inlineMd(l.removePrefix("### "))).append("</h3>") }
                l.startsWith("## ") -> { flush(); sb.append("<h2>").append(inlineMd(l.removePrefix("## "))).append("</h2>") }
                l.startsWith("# ") -> { flush(); sb.append("<h2>").append(inlineMd(l.removePrefix("# "))).append("</h2>") }
                l.startsWith("> ") -> { flush(); sb.append("<blockquote>").append(inlineMd(l.removePrefix("> "))).append("</blockquote>") }
                Regex("^-{3,}$").matches(l) -> { flush(); sb.append("<hr>") }
                l.startsWith("- ") || l.startsWith("* ") -> {
                    flush()
                    sb.append("<ul>")
                    var j = i
                    while (j < lines.size) {
                        val lj = lines[j].trim()
                        if (lj.startsWith("- ") || lj.startsWith("* ")) {
                            sb.append("<li>").append(inlineMd(lj.substring(2))).append("</li>"); j++
                        } else break
                    }
                    sb.append("</ul>"); i = j - 1
                }
                Regex("""^\d+\. """).containsMatchIn(l) -> {
                    flush()
                    sb.append("<ol>")
                    var j = i
                    while (j < lines.size) {
                        val m = Regex("""^\d+\. (.*)$""").matchEntire(lines[j].trim())
                        if (m != null) { sb.append("<li>").append(inlineMd(m.groupValues[1])).append("</li>"); j++ } else break
                    }
                    sb.append("</ol>"); i = j - 1
                }
                else -> para.append(raw).append("\n")
            }
            i++
        }
        flush()
        return sb.toString()
    }

    private fun inlineMd(s: String): String {
        var r = esc(s)
        r = Regex("""\*\*(.+?)\*\*""").replace(r) { "<strong>${it.groupValues[1]}</strong>" }
        r = Regex("""(?<!\*)\*(?!\*)(.+?)\*""").replace(r) { "<em>${it.groupValues[1]}</em>" }
        r = Regex("""~~(.+?)~~""").replace(r) { "<del>${it.groupValues[1]}</del>" }
        r = Regex("""==(.+?)==""").replace(r) { "<mark>${it.groupValues[1]}</mark>" }
        r = Regex("""`([^`]+?)`""").replace(r) { "<code>${it.groupValues[1]}</code>" }
        return r
    }

    // ---- Checklist / expense / scribble -----------------------------------------

    private fun checklistHtml(content: String): String {
        val items = Checklist.parse(content)
        if (items.isEmpty()) return ""
        return "<ul class=\"tasks\">" + items.joinToString("") {
            "<li>" + (if (it.checked) "☑ " else "☐ ") + esc(it.text) + "</li>"
        } + "</ul>"
    }

    private fun decodeCells(b64: String): List<List<String>> {
        val c = decodeJson(b64)?.optJSONArray("c") ?: return emptyList()
        return (0 until c.length()).map { r ->
            val row = c.getJSONArray(r)
            (0 until row.length()).map { row.optString(it) }
        }
    }

    private fun tableToText(cells: List<List<String>>): String =
        cells.joinToString("\n") { row -> row.joinToString(" | ") }.ifBlank { "" }

    private fun tableToMd(cells: List<List<String>>): String {
        if (cells.isEmpty()) return ""
        val cols = cells.maxOf { it.size }.coerceAtLeast(1)
        fun row(r: List<String>) = (0 until cols).joinToString(" | ") { (r.getOrNull(it) ?: "").replace("|", "\\|").ifBlank { " " } }
        return buildString {
            appendLine("| ${row(cells.first())} |")
            appendLine("| ${(0 until cols).joinToString(" | ") { "---" }} |")
            cells.drop(1).forEach { appendLine("| ${row(it)} |") }
        }.trimEnd()
    }

    private fun tableToHtml(cells: List<List<String>>): String {
        if (cells.isEmpty()) return ""
        return buildString {
            append("<table>")
            cells.forEachIndexed { i, row ->
                append("<tr>")
                row.forEach { cell ->
                    val tag = if (i == 0) "th" else "td"
                    append("<$tag>").append(esc(cell)).append("</$tag>")
                }
                append("</tr>")
            }
            append("</table>")
        }
    }

    private fun expenseLines(content: String): List<String> {
        val o = decodeJsonRaw(content) ?: return listOf("(empty budget)")
        if (o.optInt("version", 1) >= 2 && o.has("accounts")) return expenseV2Lines(o)
        val out = mutableListOf<String>()
        out.add("Income: ₹${money(o.optDouble("income", 0.0))}")
        val secs = o.optJSONArray("sections") ?: JSONArray()
        for (i in 0 until secs.length()) {
            val s = secs.getJSONObject(i)
            val items = s.optJSONArray("items") ?: JSONArray()
            var total = 0.0
            val itemLines = (0 until items.length()).map { j ->
                val it = items.getJSONObject(j)
                total += it.optDouble("amount", 0.0)
                "  - ${it.optString("name").ifBlank { "(unnamed)" }}: ₹${money(it.optDouble("amount", 0.0))}"
            }
            out.add("")
            out.add("${s.optString("name").ifBlank { "Section" }} — ₹${money(total)}")
            out.addAll(itemLines)
        }
        return out
    }

    private fun expenseMd(content: String): String {
        val o = decodeJsonRaw(content) ?: return "_(empty budget)_"
        if (o.optInt("version", 1) >= 2 && o.has("accounts")) return expenseV2Md(o)
        return buildString {
            appendLine("**Income:** ₹${money(o.optDouble("income", 0.0))}")
            val secs = o.optJSONArray("sections") ?: JSONArray()
            for (i in 0 until secs.length()) {
                val s = secs.getJSONObject(i)
                val items = s.optJSONArray("items") ?: JSONArray()
                var total = 0.0
                val lines = (0 until items.length()).map { j ->
                    val it = items.getJSONObject(j)
                    total += it.optDouble("amount", 0.0)
                    "- ${it.optString("name").ifBlank { "(unnamed)" }}: ₹${money(it.optDouble("amount", 0.0))}"
                }
                appendLine()
                appendLine("### ${s.optString("name").ifBlank { "Section" }} (₹${money(total)})")
                lines.forEach { appendLine(it) }
            }
        }.trimEnd()
    }

    private fun expenseHtml(content: String): String {
        val o = decodeJsonRaw(content) ?: return "<p><em>(empty budget)</em></p>"
        if (o.optInt("version", 1) >= 2 && o.has("accounts")) return expenseV2Html(o)
        return buildString {
            append("<p><strong>Income:</strong> ₹${money(o.optDouble("income", 0.0))}</p>")
            val secs = o.optJSONArray("sections") ?: JSONArray()
            for (i in 0 until secs.length()) {
                val s = secs.getJSONObject(i)
                val items = s.optJSONArray("items") ?: JSONArray()
                var total = 0.0
                val lis = (0 until items.length()).map { j ->
                    val it = items.getJSONObject(j)
                    total += it.optDouble("amount", 0.0)
                    "<li>${esc(it.optString("name").ifBlank { "(unnamed)" })}: ₹${money(it.optDouble("amount", 0.0))}</li>"
                }
                append("<h3>${esc(s.optString("name").ifBlank { "Section" })} (₹${money(total)})</h3>")
                if (lis.isNotEmpty()) append("<ul>").also { lis.forEach { append(it) } }.also { append("</ul>") }
            }
        }
    }

    // ---- Multi-account expense format (version 2) ----

    private fun expenseV2Lines(o: JSONObject): List<String> {
        val out = mutableListOf<String>()
        val accs = o.optJSONArray("accounts") ?: JSONArray()
        fun accName(id: String): String {
            for (i in 0 until accs.length()) {
                val a = accs.getJSONObject(i)
                if (a.optString("id") == id) return a.optString("name").ifBlank { "Account" }
            }
            return "?"
        }
        if (accs.length() == 0) return listOf("(no accounts)")
        for (i in 0 until accs.length()) {
            val a = accs.getJSONObject(i)
            if (i > 0) out.add("")
            out.add("${a.optString("name").ifBlank { "Account" }} — Total ₹${money(a.optDouble("balance", 0.0) + a.optDouble("credit", 0.0))}")
            val tags = a.optJSONArray("tags")
            if (tags != null && tags.length() > 0) {
                out.add("  Tags: " + (0 until tags.length()).joinToString(", ") { tags.optString(it) })
            }
            val secs = a.optJSONArray("sections") ?: JSONArray()
            for (j in 0 until secs.length()) {
                val s = secs.getJSONObject(j)
                val items = s.optJSONArray("items") ?: JSONArray()
                var total = 0.0
                val itemLines = (0 until items.length()).map { k ->
                    val it = items.getJSONObject(k)
                    total += it.optDouble("amount", 0.0)
                    "    - ${it.optString("name").ifBlank { "(unnamed)" }}: ₹${money(it.optDouble("amount", 0.0))}"
                }
                val tag = if (s.optBoolean("deduct", true)) " (from balance)" else " (tracked)"
                out.add("  ${s.optString("name").ifBlank { "Section" }} — ₹${money(total)}$tag")
                out.addAll(itemLines)
            }
        }
        val transfers = o.optJSONArray("transfers")
        if (transfers != null && transfers.length() > 0) {
            out.add("")
            out.add("Bank transfers")
            for (i in 0 until transfers.length()) {
                val t = transfers.getJSONObject(i)
                out.add("  - ${t.optString("name").ifBlank { "Transfer" }}: ${accName(t.optString("from"))} -> ${accName(t.optString("to"))} = ₹${money(t.optDouble("amount", 0.0))}")
            }
        }
        return out
    }

    private fun expenseV2Md(o: JSONObject): String = buildString {
        val accs = o.optJSONArray("accounts") ?: JSONArray()
        fun accName(id: String): String {
            for (i in 0 until accs.length()) {
                val a = accs.getJSONObject(i)
                if (a.optString("id") == id) return a.optString("name").ifBlank { "Account" }
            }
            return "?"
        }
        if (accs.length() == 0) { append("_(no accounts)_"); return@buildString }
        for (i in 0 until accs.length()) {
            val a = accs.getJSONObject(i)
            if (i > 0) appendLine()
            appendLine("## ${a.optString("name").ifBlank { "Account" }} — ₹${money(a.optDouble("balance", 0.0) + a.optDouble("credit", 0.0))}")
            val tags = a.optJSONArray("tags")
            if (tags != null && tags.length() > 0) {
                appendLine("_" + (0 until tags.length()).joinToString(", ") { tags.optString(it) } + "_")
            }
            val secs = a.optJSONArray("sections") ?: JSONArray()
            for (j in 0 until secs.length()) {
                val s = secs.getJSONObject(j)
                val items = s.optJSONArray("items") ?: JSONArray()
                var total = 0.0
                val lines = (0 until items.length()).map { k ->
                    val it = items.getJSONObject(k)
                    total += it.optDouble("amount", 0.0)
                    "- ${it.optString("name").ifBlank { "(unnamed)" }}: ₹${money(it.optDouble("amount", 0.0))}"
                }
                appendLine()
                appendLine("### ${s.optString("name").ifBlank { "Section" }} (₹${money(total)})")
                lines.forEach { appendLine(it) }
            }
        }
        val transfers = o.optJSONArray("transfers")
        if (transfers != null && transfers.length() > 0) {
            appendLine()
            appendLine("## Bank transfers")
            for (i in 0 until transfers.length()) {
                val t = transfers.getJSONObject(i)
                appendLine("- ${t.optString("name").ifBlank { "Transfer" }}: ${accName(t.optString("from"))} → ${accName(t.optString("to"))} (₹${money(t.optDouble("amount", 0.0))})")
            }
        }
    }.trimEnd()

    private fun expenseV2Html(o: JSONObject): String = buildString {
        val accs = o.optJSONArray("accounts") ?: JSONArray()
        fun accName(id: String): String {
            for (i in 0 until accs.length()) {
                val a = accs.getJSONObject(i)
                if (a.optString("id") == id) return a.optString("name").ifBlank { "Account" }
            }
            return "?"
        }
        if (accs.length() == 0) { append("<p><em>(no accounts)</em></p>"); return@buildString }
        for (i in 0 until accs.length()) {
            val a = accs.getJSONObject(i)
            append("<h2>${esc(a.optString("name").ifBlank { "Account" })} — ₹${money(a.optDouble("balance", 0.0) + a.optDouble("credit", 0.0))}</h2>")
            val tags = a.optJSONArray("tags")
            if (tags != null && tags.length() > 0) {
                append("<p><em>" + (0 until tags.length()).joinToString(", ") { esc(tags.optString(it)) } + "</em></p>")
            }
            val secs = a.optJSONArray("sections") ?: JSONArray()
            for (j in 0 until secs.length()) {
                val s = secs.getJSONObject(j)
                val items = s.optJSONArray("items") ?: JSONArray()
                var total = 0.0
                val lis = (0 until items.length()).map { k ->
                    val it = items.getJSONObject(k)
                    total += it.optDouble("amount", 0.0)
                    "<li>${esc(it.optString("name").ifBlank { "(unnamed)" })}: ₹${money(it.optDouble("amount", 0.0))}</li>"
                }
                append("<h3>${esc(s.optString("name").ifBlank { "Section" })} (₹${money(total)})</h3>")
                if (lis.isNotEmpty()) {
                    append("<ul>")
                    lis.forEach { append(it) }
                    append("</ul>")
                }
            }
        }
        val transfers = o.optJSONArray("transfers")
        if (transfers != null && transfers.length() > 0) {
            append("<h2>Bank transfers</h2><ul>")
            for (i in 0 until transfers.length()) {
                val t = transfers.getJSONObject(i)
                append("<li>${esc(t.optString("name").ifBlank { "Transfer" })}: ${esc(accName(t.optString("from")))} → ${esc(accName(t.optString("to")))} (₹${money(t.optDouble("amount", 0.0))})</li>")
            }
            append("</ul>")
        }
    }

    private fun scribbleToText(content: String): String {
        val o = decodeJsonRaw(content) ?: return "[Board]"
        val texts = o.optJSONArray("t") ?: JSONArray()
        val notes = (0 until texts.length()).map { texts.getJSONObject(it).optString("t") }.filter { it.isNotBlank() }
        return buildString {
            append("[Board drawing]")
            if (notes.isNotEmpty()) {
                append("\n\nNotes on the board:\n")
                append(notes.joinToString("\n") { "- $it" })
            }
        }
    }

    // ---- Scribble / whiteboard rendering ----------------------------------------
    // A drawing is stored only as vector points, so on export we rasterise it to a PNG. That PNG is
    // then embedded inline (HTML/Markdown data URI) or drawn onto the page (PDF), so the actual
    // sketch shows up in the file - never just a "[Sketch]" placeholder.

    private const val SCRIBBLE_PAD = 28f
    private const val SCRIBBLE_MAX_DIM = 2200
    private const val SCRIBBLE_INK = 0xFF1B1B1F.toInt()

    /** A stroke as points ([[x,y],...]) plus an ARGB colour and pixel width. */
    private class RenderStroke(val points: List<FloatArray>, val color: Int, val width: Float)

    private fun JSONArray.toPoints(): List<FloatArray> =
        (0 until length()).mapNotNull { k ->
            optJSONArray(k)?.let { floatArrayOf(it.optDouble(0).toFloat(), it.optDouble(1).toFloat()) }
        }

    /** Renders an inline scribble block token (its base64 payload) to a PNG, or null if empty. */
    private fun scribbleBlockPng(b64: String): ByteArray? {
        val o = decodeJson(b64) ?: return null
        val s = o.optJSONArray("s") ?: return null
        val strokes = (0 until s.length()).mapNotNull { i ->
            s.optJSONArray(i)?.toPoints()?.takeIf { it.isNotEmpty() }?.let { RenderStroke(it, SCRIBBLE_INK, 5f) }
        }
        return drawScribblePng(strokes, emptyList())
    }

    /** Renders a whole whiteboard note (its raw JSON) to a PNG, or null if it has no drawing. */
    private fun whiteboardPng(content: String): ByteArray? {
        val o = decodeJsonRaw(content) ?: return null
        val s = o.optJSONArray("s") ?: JSONArray()
        val strokes = (0 until s.length()).mapNotNull { i ->
            when (val el = s.opt(i)) {
                is JSONObject -> el.optJSONArray("p")?.toPoints()?.takeIf { it.isNotEmpty() }?.let {
                    val color = el.optInt("c", 0).let { c -> if (c == 0) SCRIBBLE_INK else c }
                    RenderStroke(it, color, el.optDouble("w", 3.0).toFloat().coerceAtLeast(2f))
                }
                is JSONArray -> el.toPoints().takeIf { it.isNotEmpty() }?.let { RenderStroke(it, SCRIBBLE_INK, 4f) }
                else -> null
            }
        }
        val t = o.optJSONArray("t") ?: JSONArray()
        val texts = (0 until t.length()).mapNotNull {
            val to = t.getJSONObject(it)
            to.optString("t").takeIf { s2 -> s2.isNotBlank() }?.let { txt ->
                Triple(to.optDouble("x").toFloat(), to.optDouble("y").toFloat(), txt)
            }
        }
        return drawScribblePng(strokes, texts)
    }

    private fun drawScribblePng(strokes: List<RenderStroke>, texts: List<Triple<Float, Float, String>>): ByteArray? {
        if (strokes.isEmpty() && texts.isEmpty()) return null
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        strokes.forEach { st ->
            st.points.forEach { p ->
                minX = minOf(minX, p[0] - st.width); minY = minOf(minY, p[1] - st.width)
                maxX = maxOf(maxX, p[0] + st.width); maxY = maxOf(maxY, p[1] + st.width)
            }
        }
        texts.forEach { (x, y, _) ->
            minX = minOf(minX, x); minY = minOf(minY, y - 20f)
            maxX = maxOf(maxX, x + 220f); maxY = maxOf(maxY, y + 24f)
        }
        if (minX > maxX || minY > maxY) return null
        val rawW = (maxX - minX) + SCRIBBLE_PAD * 2
        val rawH = (maxY - minY) + SCRIBBLE_PAD * 2
        val scale = minOf(1f, SCRIBBLE_MAX_DIM / maxOf(rawW, rawH, 1f))
        val w = (rawW * scale).toInt().coerceIn(1, SCRIBBLE_MAX_DIM)
        val h = (rawH * scale).toInt().coerceIn(1, SCRIBBLE_MAX_DIM)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(bmp)
            canvas.drawColor(0xFFFFFFFF.toInt())
            canvas.translate(SCRIBBLE_PAD * scale, SCRIBBLE_PAD * scale)
            canvas.scale(scale, scale)
            canvas.translate(-minX, -minY)
            val paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            strokes.forEach { st ->
                paint.color = st.color
                paint.strokeWidth = st.width
                if (st.points.size == 1) {
                    val dot = Paint(paint).apply { style = Paint.Style.FILL }
                    canvas.drawCircle(st.points[0][0], st.points[0][1], st.width / 2f, dot)
                } else {
                    val path = Path().apply {
                        moveTo(st.points[0][0], st.points[0][1])
                        for (i in 1 until st.points.size) lineTo(st.points[i][0], st.points[i][1])
                    }
                    canvas.drawPath(path, paint)
                }
            }
            if (texts.isNotEmpty()) {
                val tp = Paint().apply { isAntiAlias = true; color = SCRIBBLE_INK; textSize = 18f }
                texts.forEach { (x, y, text) -> canvas.drawText(text, x, y + 16f, tp) }
            }
            ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        } catch (e: Exception) {
            null
        } finally {
            bmp.recycle()
        }
    }

    private fun dataUri(png: ByteArray): String =
        "data:image/png;base64," + Base64.encodeToString(png, Base64.NO_WRAP)

    private fun boardTextNotes(content: String): List<String> {
        val o = decodeJsonRaw(content) ?: return emptyList()
        val t = o.optJSONArray("t") ?: JSONArray()
        return (0 until t.length()).map { t.getJSONObject(it).optString("t") }.filter { it.isNotBlank() }
    }

    /** Every scribble in a note (a whole whiteboard, or each inline block), rasterised to PNG. */
    private fun noteScribblePngs(note: Note): List<ByteArray> {
        if (note.type == NoteType.SCRIBBLE) return listOfNotNull(whiteboardPng(note.content))
        return note.content.split("\n").mapNotNull { line ->
            val l = line.trim()
            SCRIBBLE.matchEntire(l)?.let { return@mapNotNull scribbleBlockPng(it.groupValues[1]) }
            INK.matchEntire(l)?.let { return@mapNotNull inkPng(it.groupValues[1]) }
            null
        }
    }

    /** Rasterises a page-ink token ({s:[{c,w,p}]}) to a PNG, or null if it has no strokes. */
    private fun inkPng(b64: String): ByteArray? {
        val o = decodeJson(b64) ?: return null
        val s = o.optJSONArray("s") ?: return null
        val strokes = (0 until s.length()).mapNotNull { i ->
            val el = s.optJSONObject(i) ?: return@mapNotNull null
            el.optJSONArray("p")?.toPoints()?.takeIf { it.isNotEmpty() }?.let {
                val color = el.optInt("c", 0).let { c -> if (c == 0) SCRIBBLE_INK else c }
                RenderStroke(it, color, el.optDouble("w", 3.0).toFloat().coerceAtLeast(2f))
            }
        }
        return drawScribblePng(strokes, emptyList())
    }

    private fun whiteboardHtml(content: String): String = buildString {
        whiteboardPng(content)?.let { append("<img src=\"").append(dataUri(it)).append("\" alt=\"whiteboard\">") }
            ?: append("<p><em>[Board]</em></p>")
        val notes = boardTextNotes(content)
        if (notes.isNotEmpty()) {
            append("<p>Notes on the board:<br>")
            append(notes.joinToString("<br>") { "- " + esc(it) })
            append("</p>")
        }
    }

    private fun whiteboardMd(content: String): String = buildString {
        whiteboardPng(content)?.let { append("![whiteboard](").append(dataUri(it)).append(")\n") }
            ?: append("_[Board]_\n")
        val notes = boardTextNotes(content)
        if (notes.isNotEmpty()) {
            append("\nNotes on the board:\n")
            append(notes.joinToString("\n") { "- $it" })
        }
    }.trimEnd()

    // ---- PDF --------------------------------------------------------------------

    private fun pdfBytes(note: Note): ByteArray {
        val text = plainDoc(note)
        val pageW = 595
        val pageH = 842
        val margin = 42
        val lineH = 18f
        val maxW = (pageW - 2 * margin).toFloat()
        val paint = Paint().apply { textSize = 12f; color = 0xFF1B1B1F.toInt() }

        val wrapped = mutableListOf<String>()
        text.split("\n").forEach { p ->
            if (p.isEmpty()) wrapped.add("") else wrapLine(p, paint, maxW, wrapped)
        }

        val doc = PdfDocument()
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
        var canvas = page.canvas
        var y = margin.toFloat()
        wrapped.forEach { line ->
            if (y + lineH > pageH - margin) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
                canvas = page.canvas
                y = margin.toFloat()
            }
            if (line.isNotEmpty()) canvas.drawText(line, margin.toFloat(), y + lineH, paint)
            y += lineH
        }
        doc.finishPage(page)
        // Draw any scribbles/whiteboards as real images, each centered on its own page.
        noteScribblePngs(note).forEach { png ->
            val bmp = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return@forEach
            val sp = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, ++pageNum).create())
            val availW = (pageW - 2 * margin).toFloat()
            val availH = (pageH - 2 * margin).toFloat()
            val fit = minOf(availW / bmp.width, availH / bmp.height, 1f)
            val dw = bmp.width * fit
            val dh = bmp.height * fit
            val left = margin + (availW - dw) / 2f
            val top = margin + (availH - dh) / 2f
            sp.canvas.drawBitmap(bmp, null, RectF(left, top, left + dw, top + dh), null)
            doc.finishPage(sp)
            bmp.recycle()
        }
        val bos = ByteArrayOutputStream()
        doc.writeTo(bos)
        doc.close()
        return bos.toByteArray()
    }

    private fun wrapLine(text: String, paint: Paint, maxW: Float, out: MutableList<String>) {
        var line = ""
        text.split(" ").forEach { w ->
            val trial = if (line.isEmpty()) w else "$line $w"
            if (line.isEmpty() || paint.measureText(trial) <= maxW) {
                line = trial
            } else {
                out.add(line); line = w
            }
        }
        out.add(line)
    }

    // ---- Small helpers ----------------------------------------------------------

    private fun decodeJson(b64: String): JSONObject? = runCatching {
        JSONObject(String(Base64.decode(b64, Base64.NO_WRAP), Charsets.UTF_8))
    }.getOrNull()

    private fun decodeJsonRaw(content: String): JSONObject? = runCatching { JSONObject(content) }.getOrNull()

    private fun decodeCallout(b64: String): Pair<String, String> {
        val o = decodeJson(b64) ?: return "💡" to ""
        return o.optString("e", "💡").ifBlank { "💡" } to o.optString("t")
    }

    private fun money(v: Double): String =
        if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.2f", v)

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")
}

/** Small Storage-Access-Framework helpers for writing exports to a picked location or a saved folder. */
object ExportIO {

    /** Writes [bytes] to the document [uri] (from a create-document picker). Returns success. */
    fun writeBytes(context: Context, uri: Uri, bytes: ByteArray): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
    }.getOrDefault(false)

    /** Streams into the document [uri] via [block]. Returns success. */
    fun writeStream(context: Context, uri: Uri, block: (OutputStream) -> Unit): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { block(it); true } ?: false
    }.getOrDefault(false)

    /**
     * Creates a new document named [displayName] with [mime] inside the persisted tree [treeUriStr]
     * (from ACTION_OPEN_DOCUMENT_TREE). Returns its Uri, or null if the folder is unavailable.
     */
    fun createInTree(context: Context, treeUriStr: String, displayName: String, mime: String): Uri? = runCatching {
        val tree = Uri.parse(treeUriStr)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        DocumentsContract.createDocument(context.contentResolver, parent, mime, displayName)
    }.getOrNull()

    /** The public folder new exports land in by default (visible in the Files/Downloads app). */
    const val DOWNLOADS_SUBFOLDER = "MyNotes"

    /** True when we can silently write to the public Downloads folder without any permission. */
    val supportsDownloadsExport: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /**
     * Writes [bytes] into Downloads/[DOWNLOADS_SUBFOLDER] via MediaStore. Needs no permission on
     * Android 10+ (scoped storage); duplicate names are auto-numbered by the system. Returns the
     * new item's Uri, or null on older versions / failure so the caller can fall back to a picker.
     */
    fun writeToDownloads(context: Context, displayName: String, mime: String, bytes: ByteArray): Uri? =
        writeToDownloads(context, displayName, mime) { it.write(bytes) }

    /** Streaming variant of [writeToDownloads]. */
    fun writeToDownloads(
        context: Context,
        displayName: String,
        mime: String,
        block: (OutputStream) -> Unit,
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
        val relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + DOWNLOADS_SUBFOLDER
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null
        val ok = runCatching {
            resolver.openOutputStream(uri)?.use { block(it) } ?: return@runCatching false
            true
        }.getOrDefault(false)
        if (!ok) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        runCatching {
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }
}
