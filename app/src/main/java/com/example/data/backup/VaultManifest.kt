package com.example.data.backup

import com.example.data.share.ShareImportPolicy
import com.example.domain.model.AttachmentMarkup
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal object VaultManifest {
    private val collections = listOf("notes", "books", "templates", "reminders", "versions")

    fun objects(root: JSONObject, key: String): List<JSONObject> = (if (key == "versions") root.optJSONArray(key) ?: JSONArray() else root.getJSONArray(key)).let { array ->
        require(array.length() <= if (key == "versions") 400_000 else 20_000) { "Too many backup items" }
        (0 until array.length()).map(array::getJSONObject)
    }

    fun strings(root: JSONObject, key: String): List<String> = root.optJSONArray(key)?.let { array ->
        require(array.length() <= 20_000) { "Too many backup references" }
        (0 until array.length()).map(array::getString)
    }.orEmpty()

    fun validate(root: JSONObject) {
        require(root.getInt("version") == 1) { "This backup requires a newer app version" }
        collections.forEach { key ->
            val ids = objects(root, key).map { it.getString("id") }
            require(ids.all { it.isNotBlank() && it.length <= 200 } && ids.distinct().size == ids.size) { "Invalid backup identifiers" }
        }
        val media = objects(root, "media")
        val names = media.map { it.getString("name") }
        val entries = media.map { it.getString("entry") }
        require(names.distinct().size == names.size && entries.distinct().size == entries.size) { "Duplicate backup media" }
        require(entries.all { it.matches(Regex("media-[0-9]{1,5}")) }) { "Invalid media record" }
        names.forEach { ShareImportPolicy.freshName(it) }
        val replacements = names.associateWith { it }
        val books = objects(root, "books").associateBy { it.getString("id") }
        books.values.forEach { book ->
            book.getString("name")
            val seen = mutableSetOf<String>()
            var current: String? = book.getString("id")
            while (current != null) {
                require(seen.add(current)) { "A book cannot contain itself" }
                val parent = requireNotNull(books[current]) { "A parent book is missing" }
                current = parent.optString("parentId").takeUnless { parent.isNull("parentId") || it.isBlank() }
            }
        }
        val notes = objects(root, "notes")
        val noteIds = notes.map { it.getString("id") }.toSet()
        val versions = objects(root, "versions")
        require(versions.groupingBy { it.getString("noteId") }.eachCount().values.all { it <= 20 }) { "Too many versions for one note" }
        (notes + versions).forEach { note ->
            val type = note.getString("type")
            require(type in listOf("TEXT", "CHECKLIST", "EXPENSE", "SCRIBBLE")) { "Unsupported note type" }
            note.getString("title")
            note.getLong("updatedAt")
            if (note.has("noteId")) require(note.getString("noteId") in noteIds) { "A version's note is missing" }
            else {
                note.getLong("createdAt")
                require(note.isNull("folderId") || note.getString("folderId") in books) { "A note's book is missing" }
            }
            require(strings(note, "attachments").all { it in replacements }) { "A note attachment is missing" }
            ShareImportPolicy.renameContent(note.getString("content"), type == "SCRIBBLE", replacements)
            if (type == "EXPENSE") com.example.data.local.ExpenseCodec.decode(note.getString("content"))
        }
        objects(root, "templates").forEach { template ->
            template.getString("name")
            ShareImportPolicy.renameContent(template.getString("content"), false, replacements)
        }
        objects(root, "reminders").forEach { reminder ->
            reminder.getString("title")
            reminder.getString("body")
            reminder.getLong("triggerAt")
            reminder.getLong("createdAt")
            reminder.getLong("updatedAt")
            listOf("completedAt", "lastNotifiedAt", "snoozedUntil").forEach { key -> if (!reminder.isNull(key)) reminder.getLong(key) }
            require(reminder.getString("repeat") in listOf("NONE", "DAILY", "WEEKLY", "MONTHLY")) { "Unsupported reminder repeat" }
        }
    }

    fun restoredCopy(root: JSONObject, now: Long = System.currentTimeMillis()): JSONObject {
        validate(root)
        val copy = JSONObject(root.toString())
        val ids = collections.associateWith { key ->
            objects(copy, key).associate { it.getString("id") to UUID.randomUUID().toString() }
        }
        val media = objects(copy, "media")
        val replacements = media.associate { it.getString("name") to ShareImportPolicy.freshName(it.getString("name")) }
        media.forEach { it.put("originalName", it.getString("name")).put("name", replacements.getValue(it.getString("name"))) }
        collections.forEach { key -> objects(copy, key).forEach { it.put("id", ids.getValue(key).getValue(it.getString("id"))) } }
        objects(copy, "books").forEach { book ->
            book.put("parentId", ids.getValue("books")[book.optString("parentId")] ?: JSONObject.NULL)
            if (book.optBoolean("isTrashed")) book.put("trashedAt", now)
        }
        (objects(copy, "notes") + objects(copy, "versions")).forEach { note ->
            note.put("folderId", ids.getValue("books")[note.optString("folderId")] ?: JSONObject.NULL)
            if (note.has("noteId")) note.put("noteId", ids.getValue("notes").getValue(note.getString("noteId")))
            note.put("content", ShareImportPolicy.renameContent(note.getString("content"), note.getString("type") == "SCRIBBLE", replacements))
            note.put("attachments", JSONArray((strings(note, "attachments").map(replacements::getValue) + AttachmentMarkup.fileNames(note.getString("content"))).distinct()))
            if (note.optBoolean("isTrashed")) note.put("updatedAt", now)
        }
        objects(copy, "templates").forEach { template ->
            template.put("content", ShareImportPolicy.renameContent(template.getString("content"), false, replacements))
            if (!template.isNull("trashedAt")) template.put("trashedAt", now)
        }
        objects(copy, "reminders").forEach { reminder ->
            reminder.put("noteId", ids.getValue("notes")[reminder.optString("noteId")] ?: JSONObject.NULL)
        }
        return copy
    }
}