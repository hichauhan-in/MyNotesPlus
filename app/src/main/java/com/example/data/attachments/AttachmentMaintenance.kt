package com.example.data.attachments

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.security.EncryptionManager
import com.example.data.settings.SettingsRepository
import com.example.domain.model.AttachmentMarkup
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AttachmentMaintenance(
    private val context: Context,
    private val database: AppDatabase,
    private val settings: SettingsRepository = SettingsRepository(context),
) {
    suspend fun removeUnused(candidates: Collection<String>) = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) return@withContext
        val referenced = database.withTransaction {
            val notes = database.noteDao().getAllNotesOnce()
            val versions = database.noteDao().getAllVersionsOnce()
            val names = linkedSetOf<String>()
            (notes.map { it.encryptedContent to it.attachments } + versions.map { it.encryptedContent to it.attachments }).forEach { (encrypted, attachments) ->
                val content = EncryptionManager.decryptOrNull(encrypted) ?: return@withTransaction null
                names.addAll(attachments.split(",").filter(String::isNotBlank))
                names.addAll(AttachmentMarkup.fileNames(content))
            }
            names
        } ?: return@withContext
        referenced.addAll(settings.templateAttachmentNamesForCleanup() ?: return@withContext)
        candidates.distinct().filterNot { it in referenced }.forEach { AttachmentStore.delete(context, it) }
    }

    suspend fun removeAbandoned(now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val cutoff = now - 48 * 60 * 60_000L
        val files = File(context.filesDir, "attachments").listFiles().orEmpty()
            .filter { it.isFile && it.lastModified() < cutoff && !it.name.startsWith(".") && !it.name.endsWith(".bak") && !it.name.endsWith(".new") }
        removeUnused(files.map { it.name })
    }
}