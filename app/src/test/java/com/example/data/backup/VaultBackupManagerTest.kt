package com.example.data.backup

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.attachments.AttachmentStore
import com.example.data.security.TestKeyStoreProvider
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.repository.NoteRepository
import com.example.data.repository.FolderRepository
import com.example.data.repository.ReminderRepository
import com.example.data.settings.SettingsRepository
import com.example.domain.model.AttachmentMarkup
import com.example.domain.model.CustomTemplate
import com.example.domain.model.Note
import com.example.domain.model.Reminder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VaultBackupManagerTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var notes: NoteRepository
    private lateinit var folders: FolderRepository
    private lateinit var reminders: ReminderRepository
    private lateinit var settings: SettingsRepository

    @Before fun setup() {
        TestKeyStoreProvider.install()
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        notes = NoteRepository(database.noteDao(), context, database)
        folders = FolderRepository(database.folderDao(), database.noteDao(), context, database)
        reminders = ReminderRepository(database.reminderDao(), database)
        settings = SettingsRepository(context)
        val provider = BackupDocumentProvider()
        provider.attachInfo(context, android.content.pm.ProviderInfo().apply { authority = "mynotes.test.backup" })
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("mynotes.test.backup", provider)
    }

    @After fun closeDatabase() { database.close() }

    private fun manager() = VaultBackupManager(context, database, settings, reminders)

    private fun target(): android.net.Uri {
        return android.net.Uri.parse("content://mynotes.test.backup/${UUID.randomUUID()}.mynotesbackup")
    }

    private fun password() = "a private backup passphrase".toCharArray()

    @Test fun roundTripKeepsMediaBooksTemplatesRemindersAndRecentVersions(): Unit = runBlocking {
        val book = folders.createFolder("Money", null)
        val media = AttachmentStore.newImageFile(context).name
        val bytes = byteArrayOf(12, 34, 56, 78)
        assertTrue(AttachmentStore.writeEncrypted(context, media, bytes))
        val note = Note(UUID.randomUUID().toString(), "Private receipt", "Original\n${AttachmentMarkup.imageToken(media)}", 100, 100, folderId = book, attachments = listOf(media))
        notes.saveNote(note)
        notes.saveNote(note.copy(content = "Updated\n${AttachmentMarkup.imageToken(media)}"))
        settings.addTemplate(CustomTemplate(name = "Private template", iconKey = "note", content = "Template text"))
        val reminder = Reminder(title = "Review receipt", noteId = note.id, triggerAt = System.currentTimeMillis() + 86_400_000)
        reminders.save(reminder)
        val backup = manager()
        val uri = target()
        val summary = backup.export(uri, password())
        assertEquals(1, summary.notes)
        assertEquals(1, summary.versions)
        val prepared = backup.prepare(uri, password())
        assertEquals(1, notes.allNotes.first().size)
        backup.restore(prepared)
        val restored = notes.allNotes.first().single { it.id != note.id }
        assertNotEquals(note.folderId, restored.folderId)
        assertNotEquals(media, restored.attachments.single())
        assertArrayEquals(bytes, AttachmentStore.readDecrypted(context, restored.attachments.single()))
        assertNotNull(notes.getNoteById(note.id))
        val version = notes.versions(restored.id).first().single()
        assertTrue(requireNotNull(notes.versionContent(restored.id, version.id)).second.startsWith("Original"))
        assertEquals(2, settings.customTemplates.first().size)
        assertEquals(restored.id, reminders.allForSync().single { it.id != reminder.id }.noteId)
        assertFalse(AttachmentStore.fileFor(context, restored.attachments.single()).readBytes().contentEquals(bytes))
        backup.discard(prepared)
        assertTrue(AttachmentStore.fileFor(context, restored.attachments.single()).exists())
        assertThrows(IllegalStateException::class.java) { runBlocking { backup.restore(prepared) } }
    }

    @Test fun cancellingPreparedRestoreLeavesOriginalDataAndRemovesOnlyStagedFiles() = runBlocking {
        val media = AttachmentStore.newImageFile(context).name
        assertTrue(AttachmentStore.writeEncrypted(context, media, byteArrayOf(1, 2, 3)))
        val note = Note(UUID.randomUUID().toString(), "Original", AttachmentMarkup.imageToken(media), 1, 1, attachments = listOf(media))
        notes.saveNote(note)
        val backup = manager()
        val uri = target()
        backup.export(uri, password())
        val staged = backup.prepare(uri, password())
        assertTrue(staged.files.all { AttachmentStore.fileFor(context, it).exists() })
        backup.discard(staged)
        assertTrue(staged.files.none { AttachmentStore.fileFor(context, it).exists() })
        assertTrue(AttachmentStore.fileFor(context, media).exists())
        assertNotNull(notes.getNoteById(note.id))
    }
}