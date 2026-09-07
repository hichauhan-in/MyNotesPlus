package com.example.di

import android.content.Context
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.repository.FolderRepository
import com.example.data.repository.NoteRepository
import com.example.data.repository.ReminderRepository
import com.example.data.settings.SettingsRepository
import com.example.data.sync.CloudSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Minimal manual dependency container. Initialised once from [com.example.VaultNotesApp].
 */
object AppContainer {
    private var database: AppDatabase? = null

    /** Process-lifetime scope for work that must outlive a screen (e.g. final autosave). */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var noteRepository: NoteRepository? = null
        private set

    var folderRepository: FolderRepository? = null
        private set

    var reminderRepository: ReminderRepository? = null
        private set

    var settingsRepository: SettingsRepository? = null
        private set

    var cloudSyncManager: CloudSyncManager? = null
        private set

    fun init(context: Context) {
        if (database == null) {
            database = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "vault_notes_db"
            )
                // Preserve notes across additive schema changes where possible.
                .addMigrations(
                    AppDatabase.MIGRATION_2_3,
                    AppDatabase.MIGRATION_3_4,
                    AppDatabase.MIGRATION_4_5,
                    AppDatabase.MIGRATION_5_6,
                    AppDatabase.MIGRATION_6_7,
                    AppDatabase.MIGRATION_7_8,
                )
                // Any other unknown schema jump: wipe & rebuild rather than crash.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
            noteRepository = NoteRepository(database!!.noteDao(), context.applicationContext)
            folderRepository = FolderRepository(database!!.folderDao(), database!!.noteDao(), context.applicationContext)
            reminderRepository = ReminderRepository(database!!.reminderDao())
        }
        if (settingsRepository == null) {
            settingsRepository = SettingsRepository(context.applicationContext)
        }
        if (cloudSyncManager == null) {
            cloudSyncManager = CloudSyncManager(
                settingsRepository!!,
                noteRepository!!,
                folderRepository!!,
                reminderRepository!!,
                context.applicationContext,
            )
        }
    }
}
