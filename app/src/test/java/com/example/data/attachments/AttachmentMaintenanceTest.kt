package com.example.data.attachments

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.NoteEntity
import com.example.data.local.NoteVersionEntity
import com.example.data.security.EncryptionManager
import com.example.data.security.TestKeyStoreProvider
import com.example.data.settings.SettingsRepository
import com.example.domain.model.AttachmentMarkup
import com.example.domain.model.CustomTemplate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AttachmentMaintenanceTest {
    @Test fun cleanupRetainsTemplateAndVersionMediaUntilTheLastReferenceIsGone() = runBlocking {
        TestKeyStoreProvider.install()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val settings = SettingsRepository(context)
            val maintenance = AttachmentMaintenance(context, database, settings)
            val media = AttachmentStore.newImageFile(context).name
            AttachmentStore.writeEncrypted(context, media, byteArrayOf(1, 2, 3))
            val original = NoteEntity(id = "note", encryptedTitle = EncryptionManager.encrypt("Title"),
                encryptedContent = EncryptionManager.encrypt(AttachmentMarkup.imageToken(media)), attachments = media)
            database.noteDao().insertNote(original)
            val version = NoteVersionEntity.from(original)
            database.noteDao().insertVersion(version)
            database.noteDao().insertNote(original.copy(encryptedContent = EncryptionManager.encrypt("No image"), attachments = ""))
            maintenance.removeUnused(listOf(media))
            assertTrue(AttachmentStore.fileFor(context, media).exists())
            settings.addTemplate(CustomTemplate(id = "template", name = "Template", iconKey = "note", content = AttachmentMarkup.imageToken(media)))
            database.noteDao().deleteNoteById("note")
            maintenance.removeUnused(listOf(media))
            assertTrue(AttachmentStore.fileFor(context, media).exists())
            settings.deleteTemplatePermanently("template")
            maintenance.removeUnused(listOf(media))
            assertFalse(AttachmentStore.fileFor(context, media).exists())
        } finally { database.close() }
    }
}