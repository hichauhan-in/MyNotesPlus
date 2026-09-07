package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [NoteEntity::class, FolderEntity::class, ReminderEntity::class],
    version = 8,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        /** Adds the note `type` column (TEXT / CHECKLIST) without wiping existing notes. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN type TEXT NOT NULL DEFAULT 'TEXT'")
            }
        }

        /** Adds the note `attachments` column (comma-separated image file names). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN attachments TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Adds the folder `parentId` column so books can be nested inside other books. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN parentId TEXT")
            }
        }

        /** Adds folder trash columns so deleting a book keeps it (and its contents) in Trash. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN isTrashed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE folders ADD COLUMN trashedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Creates the reminders table (title/body stored encrypted, like notes). */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS reminders (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "encryptedTitle TEXT NOT NULL, " +
                        "encryptedBody TEXT NOT NULL, " +
                        "noteId TEXT, " +
                        "triggerAt INTEGER NOT NULL, " +
                        "repeat TEXT NOT NULL, " +
                        "enabled INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL)",
                )
            }
        }

        /** Adds the reminder `updatedAt` column so reminders can sync (last-write-wins). */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE reminders SET updatedAt = createdAt")
            }
        }
    }
}
