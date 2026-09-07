package com.example.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.data.attachments.AttachmentStore
import com.example.data.local.FolderEntity
import com.example.data.local.NoteEntity
import com.example.data.local.NoteVersionEntity
import com.example.data.local.ReminderEntity
import com.example.data.reminders.ReminderScheduler
import com.example.data.security.EncryptionManager
import com.example.di.AppContainer
import com.example.domain.model.AttachmentMarkup
import com.example.domain.model.CustomTemplate
import com.example.widget.WidgetUpdater
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class VaultBackupManager(
    context: Context,
    private val database: com.example.data.local.AppDatabase = AppContainer.localDatabase,
    private val settings: com.example.data.settings.SettingsRepository = AppContainer.settingsRepository!!,
    private val reminderRepository: com.example.data.repository.ReminderRepository = AppContainer.reminderRepository!!,
) {
    private val context = context.applicationContext

    data class Summary(val notes: Int, val books: Int, val templates: Int, val reminders: Int, val attachments: Int, val versions: Int) {
        val description: String get() = "$notes notes, $books books, $templates templates, $reminders reminders, $attachments attachments, $versions recent versions"
    }

    class Prepared internal constructor(internal val manifest: JSONObject, internal val files: List<String>, val summary: Summary) {
        internal var committed = false
    }

    suspend fun export(destination: Uri, passphrase: CharArray): Summary = withContext(Dispatchers.IO) {
        operation.withLock {
            var temporary: File? = null
            val verificationPassphrase = passphrase.copyOf()
            try {
                val manifest = snapshot()
                VaultManifest.validate(manifest)
                val entries = listOf(VaultArchive.Entry("manifest") { manifest.toString().toByteArray(Charsets.UTF_8) }) +
                    VaultManifest.objects(manifest, "media").map { media ->
                        VaultArchive.Entry(media.getString("entry")) {
                            val name = media.getString("name")
                            require(AttachmentStore.fileFor(context, name).length() <= VaultArchive.MAX_ENTRY_BYTES + 64L) { "An attachment exceeds the 32 MB backup limit" }
                            requireNotNull(AttachmentStore.readDecrypted(context, name)) { "An attachment is missing or locked; the backup was not completed" }
                        }
                    }
                val file = File.createTempFile("vault-", ".encrypted", context.cacheDir)
                temporary = file
                file.outputStream().use { VaultArchive.write(it, passphrase, entries) }
                file.inputStream().use { VaultArchive.read(it, verificationPassphrase) { _, _ -> } }
                requireNotNull(context.contentResolver.openOutputStream(destination, "wt")) { "Cannot open the backup destination" }.use { output ->
                    file.inputStream().use { it.copyTo(output) }
                }
                summary(manifest)
            } finally {
                passphrase.fill('\u0000')
                verificationPassphrase.fill('\u0000')
                temporary?.delete()
            }
        }
    }

    suspend fun prepare(source: Uri, passphrase: CharArray): Prepared = withContext(NonCancellable + Dispatchers.IO) {
        operation.withLock {
            val written = mutableListOf<String>()
            try {
                var manifest: JSONObject? = null
                var media = emptyMap<String, String>()
                val received = mutableSetOf<String>()
                requireNotNull(context.contentResolver.openInputStream(source)) { "Cannot open the backup" }.use { input ->
                    VaultArchive.read(input, passphrase) { name, bytes ->
                        if (name == "manifest") {
                            manifest = VaultManifest.restoredCopy(JSONObject(String(bytes, Charsets.UTF_8)))
                            media = VaultManifest.objects(requireNotNull(manifest), "media").associate { it.getString("entry") to it.getString("name") }
                        } else {
                            val fileName = requireNotNull(media[name]) { "Unexpected attachment in backup" }
                            written.add(fileName)
                            check(AttachmentStore.writeEncrypted(context, fileName, bytes)) { "Cannot stage an encrypted attachment" }
                            received.add(name)
                        }
                    }
                }
                require(received == media.keys) { "The backup is missing attachments" }
                val complete = requireNotNull(manifest) { "The backup manifest is missing" }
                Prepared(complete, written.toList(), summary(complete))
            } catch (failure: Exception) {
                written.forEach { AttachmentStore.delete(context, it) }
                throw failure
            } finally { passphrase.fill('\u0000') }
        }
    }

    suspend fun restore(prepared: Prepared): Summary = withContext(NonCancellable + Dispatchers.IO) {
        operation.withLock {
            check(!prepared.committed) { "This backup has already been restored" }
            val root = prepared.manifest
            val templates = VaultManifest.objects(root, "templates").map { item ->
                CustomTemplate(item.getString("id"), item.getString("name"), item.optString("icon", "note"), item.getString("content"),
                    trashedAt = item.nullableLong("trashedAt"), updatedAt = item.optLong("updatedAt"))
            }
            val notes = VaultManifest.objects(root, "notes").map { item ->
                NoteEntity(
                    id = item.getString("id"), encryptedTitle = EncryptionManager.encrypt(item.getString("title")),
                    encryptedContent = EncryptionManager.encrypt(item.getString("content")),
                    createdAt = item.getLong("createdAt"), updatedAt = item.getLong("updatedAt"),
                    isPinned = item.optBoolean("isPinned"), isFavorite = item.optBoolean("isFavorite"),
                    isArchived = item.optBoolean("isArchived"), isTrashed = item.optBoolean("isTrashed"),
                    folderId = item.nullableString("folderId"), tags = VaultManifest.strings(item, "tags").joinToString(","),
                    colorArgb = item.optInt("colorArgb"), type = item.getString("type"),
                    attachments = VaultManifest.strings(item, "attachments").joinToString(","),
                )
            }
            val books = VaultManifest.objects(root, "books").map { item ->
                FolderEntity(id = item.getString("id"), name = item.getString("name"), parentId = item.nullableString("parentId"),
                    colorArgb = item.optInt("colorArgb"), isTrashed = item.optBoolean("isTrashed"), trashedAt = item.optLong("trashedAt"))
            }
            val versions = VaultManifest.objects(root, "versions").map { item ->
                NoteVersionEntity(id = item.getString("id"), noteId = item.getString("noteId"),
                    encryptedTitle = EncryptionManager.encrypt(item.getString("title")), encryptedContent = EncryptionManager.encrypt(item.getString("content")),
                    type = item.getString("type"), attachments = VaultManifest.strings(item, "attachments").joinToString(","), updatedAt = item.getLong("updatedAt"))
            }
            val reminders = VaultManifest.objects(root, "reminders").map { item ->
                ReminderEntity(id = item.getString("id"), encryptedTitle = EncryptionManager.encrypt(item.getString("title")),
                    encryptedBody = EncryptionManager.encrypt(item.getString("body")), noteId = item.nullableString("noteId"),
                    triggerAt = item.getLong("triggerAt"), repeat = item.getString("repeat"), enabled = item.optBoolean("enabled", true),
                    createdAt = item.getLong("createdAt"), updatedAt = item.getLong("updatedAt"),
                    completedAt = item.nullableLong("completedAt"), lastNotifiedAt = item.nullableLong("lastNotifiedAt"),
                    snoozedUntil = item.nullableLong("snoozedUntil"), repeatAnchorAt = item.optLong("repeatAnchorAt", item.getLong("triggerAt")),
                    encryptedChecklistText = item.nullableString("checklistText")?.let(EncryptionManager::encrypt))
            }
            var templatesAdded = false
            try {
                database.withTransaction {
                    database.folderDao().insertRestoredFolders(books)
                    database.noteDao().insertRestoredNotes(notes)
                    database.noteDao().insertRestoredVersions(versions)
                    database.reminderDao().insertRestoredReminders(reminders)
                    settings.appendImportedTemplates(templates)
                    templatesAdded = true
                }
                prepared.committed = true
            } catch (failure: Exception) {
                if (templatesAdded) settings.removeImportedTemplates(templates.map { it.id }.toSet())
                throw failure
            }
            reminders.forEach { entity ->
                runCatching {
                    reminderRepository.getById(entity.id)?.let { reminder ->
                        if (reminder.enabled && reminder.effectiveAt > System.currentTimeMillis()) ReminderScheduler.schedule(context, reminder)
                    }
                }
            }
            runCatching { WidgetUpdater.refreshAll(context) }
            prepared.summary
        }
    }

    suspend fun discard(prepared: Prepared) = withContext(NonCancellable + Dispatchers.IO) {
        operation.withLock {
            if (!prepared.committed) prepared.files.forEach { AttachmentStore.delete(context, it) }
        }
    }

    private suspend fun snapshot(): JSONObject {
        val root = database.withTransaction {
            val notes = database.noteDao().getAllNotesOnce()
            val books = database.folderDao().getAllFoldersOnce()
            val bookIds = books.map { it.id }.toSet()
            val reminders = database.reminderDao().getAllOnce()
            val versions = database.noteDao().getAllVersionsOnce()
            JSONObject().put("version", 1).put("createdAt", System.currentTimeMillis())
                .put("versions", JSONArray(versions.map { version ->
                    JSONObject().put("id", version.id).put("noteId", version.noteId).put("title", decrypt(version.encryptedTitle))
                        .put("content", decrypt(version.encryptedContent)).put("type", version.type).put("updatedAt", version.updatedAt)
                        .put("attachments", JSONArray(version.attachments.split(",").filter(String::isNotBlank)))
                }))
                .put("notes", JSONArray(notes.map { note ->
                    JSONObject().put("id", note.id).put("title", decrypt(note.encryptedTitle)).put("content", decrypt(note.encryptedContent))
                        .put("createdAt", note.createdAt).put("updatedAt", note.updatedAt).put("type", note.type)
                        .put("isPinned", note.isPinned).put("isFavorite", note.isFavorite).put("isArchived", note.isArchived).put("isTrashed", note.isTrashed)
                        .put("folderId", note.folderId?.takeIf { it in bookIds } ?: JSONObject.NULL)
                        .put("colorArgb", note.colorArgb).put("tags", JSONArray(note.tags.split(",").filter(String::isNotBlank)))
                        .put("attachments", JSONArray(note.attachments.split(",").filter(String::isNotBlank)))
                }))
                .put("books", JSONArray(books.map { book ->
                    JSONObject().put("id", book.id).put("name", book.name).put("colorArgb", book.colorArgb)
                        .put("parentId", book.parentId?.takeIf { it in bookIds } ?: JSONObject.NULL)
                        .put("isTrashed", book.isTrashed).put("trashedAt", book.trashedAt)
                }))
                .put("reminders", JSONArray(reminders.map { reminder ->
                    JSONObject().put("id", reminder.id).put("title", decrypt(reminder.encryptedTitle)).put("body", decrypt(reminder.encryptedBody))
                        .put("noteId", reminder.noteId ?: JSONObject.NULL).put("triggerAt", reminder.triggerAt).put("repeat", reminder.repeat)
                        .put("enabled", reminder.enabled).put("createdAt", reminder.createdAt).put("updatedAt", reminder.updatedAt)
                        .put("completedAt", reminder.completedAt ?: JSONObject.NULL).put("lastNotifiedAt", reminder.lastNotifiedAt ?: JSONObject.NULL)
                        .put("snoozedUntil", reminder.snoozedUntil ?: JSONObject.NULL).put("repeatAnchorAt", reminder.repeatAnchorAt)
                        .put("checklistText", reminder.encryptedChecklistText?.let(::decrypt) ?: JSONObject.NULL)
                }))
        }
        val templates = settings.allTemplatesForSync()
        root.put("templates", JSONArray(templates.map { template ->
            JSONObject().put("id", template.id).put("name", template.name).put("icon", template.iconKey).put("content", template.content)
                .put("trashedAt", template.trashedAt ?: JSONObject.NULL).put("updatedAt", template.updatedAt)
        }))
        val files = ((VaultManifest.objects(root, "notes") + VaultManifest.objects(root, "versions")).flatMap { note ->
            VaultManifest.strings(note, "attachments") + AttachmentMarkup.fileNames(note.getString("content"))
        } + templates.flatMap { AttachmentMarkup.fileNames(it.content) }).distinct()
        root.put("media", JSONArray(files.mapIndexed { index, name -> JSONObject().put("entry", "media-$index").put("name", name) }))
        return root
    }

    private fun summary(root: JSONObject) = Summary(
        VaultManifest.objects(root, "notes").size, VaultManifest.objects(root, "books").size,
        VaultManifest.objects(root, "templates").size, VaultManifest.objects(root, "reminders").size,
        VaultManifest.objects(root, "media").size,
        VaultManifest.objects(root, "versions").size,
    )

    private fun decrypt(value: String): String = requireNotNull(EncryptionManager.decryptOrNull(value)) { "A saved item is locked; its content has been preserved" }
    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)

    companion object { private val operation = Mutex() }
}