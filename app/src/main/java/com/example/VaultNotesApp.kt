package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.data.attachments.AttachmentStore
import com.example.data.attachments.EncAttachmentKeyer
import com.example.data.attachments.EncryptedAttachmentFetcher
import com.example.data.reminders.NotificationHelper
import com.example.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class VaultNotesApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        NotificationHelper.ensureChannel(this)
        purgeExpiredTrash()
        encryptExistingAttachments()
    }

    /**
     * The app-wide Coil loader, taught to decrypt [com.example.data.attachments.EncAttachment]
     * models. Every `AsyncImage` that omits an explicit loader uses this one.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(EncryptedAttachmentFetcher.Factory(this@VaultNotesApp))
                add(EncAttachmentKeyer())
            }
            .build()

    /** Permanently removes trashed notes and templates that have outlived the retention window. */
    private fun purgeExpiredTrash() {
        val notes = AppContainer.noteRepository ?: return
        val settings = AppContainer.settingsRepository ?: return
        AppContainer.applicationScope.launch {
            val days = runCatching { settings.settings.first().trashRetentionDays }.getOrDefault(30)
            notes.purgeTrashOlderThan(days)
            if (days > 0) {
                val cutoff = System.currentTimeMillis() - days.toLong() * 24 * 60 * 60 * 1000
                settings.purgeTrashedTemplatesBefore(cutoff)
            }
        }
    }

    /**
     * Encrypts any attachment files that predate at-rest encryption. Runs once in the background
     * (a marker file short-circuits later launches) so it never blocks startup or the UI.
     */
    private fun encryptExistingAttachments() {
        AppContainer.applicationScope.launch(Dispatchers.IO) {
            AttachmentStore.migrateExistingToEncrypted(applicationContext)
        }
    }
}
