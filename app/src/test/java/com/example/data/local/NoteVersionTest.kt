package com.example.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NoteVersionTest {
    @Test fun versionEightUpgradePreservesNotesBooksAndReminderSchedule() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-${java.util.UUID.randomUUID()}.db"
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(8) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE notes (id TEXT NOT NULL PRIMARY KEY, encryptedTitle TEXT NOT NULL, encryptedContent TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, isPinned INTEGER NOT NULL, isFavorite INTEGER NOT NULL, isArchived INTEGER NOT NULL, isTrashed INTEGER NOT NULL, folderId TEXT, tags TEXT NOT NULL, colorArgb INTEGER NOT NULL, type TEXT NOT NULL, attachments TEXT NOT NULL)")
                        listOf("updatedAt", "isPinned", "isTrashed", "isArchived").forEach { column -> db.execSQL("CREATE INDEX index_notes_$column ON notes($column)") }
                        db.execSQL("CREATE TABLE folders (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, colorArgb INTEGER NOT NULL, parentId TEXT, isTrashed INTEGER NOT NULL, trashedAt INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE reminders (id TEXT NOT NULL PRIMARY KEY, encryptedTitle TEXT NOT NULL, encryptedBody TEXT NOT NULL, noteId TEXT, triggerAt INTEGER NOT NULL, repeat TEXT NOT NULL, enabled INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                        db.execSQL("INSERT INTO folders VALUES ('book', 'Money', 0, NULL, 0, 0)")
                        db.execSQL("INSERT INTO notes VALUES ('note', 'encrypted title', 'encrypted body', 10, 20, 0, 1, 0, 0, 'book', '', 0, 'TEXT', 'receipt.jpg')")
                        db.execSQL("INSERT INTO reminders VALUES ('reminder', 'encrypted reminder', 'encrypted details', 'note', 12345, 'MONTHLY', 1, 10, 20)")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build(),
        )
        helper.writableDatabase
        helper.close()
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10).build()
        try {
            val note = requireNotNull(database.noteDao().getNoteById("note"))
            assertEquals("encrypted body", note.encryptedContent)
            assertEquals("receipt.jpg", note.attachments)
            assertEquals("book", note.folderId)
            assertEquals("Money", database.folderDao().getFolderById("book")?.name)
            val reminder = requireNotNull(database.reminderDao().getById("reminder"))
            assertEquals(12345L, reminder.repeatAnchorAt)
            assertNull(reminder.completedAt)
            assertNull(reminder.snoozedUntil)
            assertEquals(emptyList<NoteVersionEntity>(), database.noteDao().versions("note").first())
        } finally { database.close(); context.deleteDatabase(name) }
    }

    @Test fun updatesKeepVersionsAndDeletionCascades() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            val note = NoteEntity(id = "note", encryptedTitle = "ciphertext", encryptedContent = "original", updatedAt = 100)
            db.noteDao().insertNote(note)
            val version = NoteVersionEntity.from(note)
            db.noteDao().insertVersion(version)
            db.noteDao().insertNote(note.copy(encryptedContent = "updated"))
            assertNotNull(db.noteDao().getVersion(version.id))
            assertEquals("updated", db.noteDao().getNoteById("note")?.encryptedContent)
            db.noteDao().deleteNoteById("note")
            assertNull(db.noteDao().getVersion(version.id))
        } finally { db.close() }
    }

    @Test fun historyRetainsOnlyTheLatestTwentyVersions() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            val note = NoteEntity(id = "note", encryptedTitle = "ciphertext", encryptedContent = "original")
            db.noteDao().insertNote(note)
            repeat(25) { index -> db.noteDao().insertVersion(NoteVersionEntity.from(note.copy(updatedAt = index.toLong()))) }
            db.noteDao().trimVersions("note")
            val versions = db.noteDao().versions("note").first()
            assertEquals(20, versions.size)
            assertEquals(24L, versions.first().updatedAt)
            assertEquals(5L, versions.last().updatedAt)
        } finally { db.close() }
    }
}