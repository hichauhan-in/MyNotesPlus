package com.example.data.sync

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.FolderRepository
import com.example.data.repository.NoteRepository
import com.example.data.repository.ReminderRepository
import com.example.data.security.EncryptionManager
import com.example.data.security.TestKeyStoreProvider
import com.example.data.settings.SettingsRepository
import com.example.domain.model.CustomTemplate
import com.example.domain.model.Note
import com.example.domain.model.Reminder
import com.example.ui.settings.PassphraseMode
import com.example.ui.settings.SettingsViewModel
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CloudSyncRecoveryTest {
    private lateinit var database: AppDatabase
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var settings: SettingsRepository
    private lateinit var notes: NoteRepository
    private lateinit var reminders: ReminderRepository
    private lateinit var manager: CloudSyncManager
    private lateinit var drive: RecoveryDrive
    private val token = "test-access"

    @Before fun setup() {
        TestKeyStoreProvider.install()
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settings = SettingsRepository(context, PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            File(context.filesDir, "recovery-${UUID.randomUUID()}.preferences_pb")
        })
        notes = NoteRepository(database.noteDao(), context, database)
        reminders = ReminderRepository(database.reminderDao(), database)
        drive = RecoveryDrive()
        manager = CloudSyncManager(settings, notes, FolderRepository(database.folderDao(), database.noteDao(), context, database), reminders, context, drive)
    }

    @After fun close() { database.close(); dataStoreScope.cancel() }

    private suspend fun existing(present: Boolean, legacy: Boolean = false): Pair<Note, DriveRecoveryEnvelope> {
        val key = SyncCrypto.newDataKey()
        val envelope = if (legacy) {
            val salt = SyncCrypto.newSalt()
            val derived = SyncCrypto.deriveKeyFromPassphrase("old phrase".toCharArray(), salt)
            try {
                DriveRecoveryEnvelope.decode(JSONObject().put("version", 1).put("salt", SyncCrypto.encodeBase64(salt))
                    .put("wrappedDek", SyncCrypto.encodeBase64(SyncCrypto.wrapDataKey(key, derived))).toString())
            } finally { derived.fill(0) }
        } else DriveRecoveryEnvelope.create("old phrase".toCharArray(), DriveRecoveryMethod.PASSPHRASE, key, "old-folder")
        drive.createAppDataFile(token, "mynotes.key.json", envelope.encode())
        drive.folders["old-folder"] = if (present) DriveFolderStatus.PRESENT else DriveFolderStatus.MISSING
        settings.selectDriveAccount(drive.email)
        settings.activateDriveKey(drive.email, "old-folder", SyncCrypto.encodeBase64(EncryptionManager.encryptBytes(key)), envelope.identity)
        key.fill(0)
        val note = Note(UUID.randomUUID().toString(), "Local notebook", "Keep this content", 100, 100)
        notes.saveNote(note)
        settings.setSyncedNoteVersions(notes.idStamps())
        settings.setSyncedReminderIds(setOf("old-reminder"))
        settings.setLastSyncedAt(12345)
        return note to envelope
    }

    @Test fun deletedLegacyFolderNeverTrashesLocalNotesOrRequestsTheOldPhrase() = runBlocking {
        val (note, envelope) = existing(present = false, legacy = true)
        val checkpoints = settings.syncedNoteVersions()
        val localKey = settings.wrappedDataKey()
        assertEquals(CloudSyncManager.SyncOutcome.FolderMissing, manager.syncNow(token))
        assertEquals(CloudSyncManager.RemoteState.FOLDER_MISSING, manager.connect(token).state)
        assertFalse(requireNotNull(notes.getNoteById(note.id)).isTrashed)
        assertEquals(note.content, notes.getNoteById(note.id)?.content)
        assertEquals(checkpoints, settings.syncedNoteVersions())
        assertEquals(localKey, settings.wrappedDataKey())
        assertEquals(envelope.identity, drive.envelope().identity)
        assertEquals(0, drive.noteLists)
        assertEquals(0, drive.deletedFiles)
        assertEquals("FOLDER_MISSING", settings.snapshot().driveRecoveryIssue)
    }

    @Test fun confirmedPinRestartPreservesLocalItemsAndUploadsToANewGeneration() = runBlocking {
        val (note, old) = existing(present = false, legacy = true)
        settings.addTemplate(CustomTemplate(name = "Local template", iconKey = "note", content = "My template"))
        reminders.save(Reminder(id = "local-reminder", title = "My reminder", triggerAt = System.currentTimeMillis() + 100_000))
        drive.createAppDataFile(token, old.templatesName, "old encrypted templates")
        drive.createAppDataFile(token, old.remindersName, "old encrypted reminders")
        val secret = "001234".toCharArray()
        assertEquals(CloudSyncManager.SetupResult.SUCCESS, manager.provision(token, secret, DriveRecoveryMethod.PIN, restartConfirmed = true))
        assertTrue(secret.all { it == '\u0000' })
        val current = drive.envelope()
        val folder = requireNotNull(current.folderId)
        assertNotEquals(old.identity, current.identity)
        assertNotEquals("old-folder", folder)
        assertEquals(DriveRecoveryMethod.PIN, current.method)
        assertTrue(settings.syncedNoteVersions().isEmpty())
        assertTrue(settings.syncedReminderIds().isEmpty())
        assertEquals(0L, settings.snapshot().lastSyncedAt)
        assertTrue(drive.files.values.any { it.name.startsWith("mynotes.key.retired.") })
        assertEquals("old encrypted templates", drive.named(old.templatesName)?.content)
        assertEquals("old encrypted reminders", drive.named(old.remindersName)?.content)
        assertEquals(note.content, notes.getNoteById(note.id)?.content)
        assertTrue(manager.syncNow(token) is CloudSyncManager.SyncOutcome.Success)
        assertTrue(drive.noteRecords[folder].orEmpty().containsKey(note.id))
        assertNotNull(drive.named(current.templatesName))
        assertNotNull(drive.named(current.remindersName))
        assertEquals(1, settings.customTemplates.first().size)
        assertEquals(1, reminders.allForSync().size)
        assertNull(settings.snapshot().driveRecoveryIssue)
        assertEquals(0, drive.deletedFiles)
    }

    @Test fun resetNeverOverwritesAnActiveFolderOrTreatsOfflineAsMissing() = runBlocking {
        val (_, old) = existing(present = true)
        assertEquals(CloudSyncManager.SetupResult.STATE_CHANGED, manager.provision(token, "1234".toCharArray(), DriveRecoveryMethod.PIN, true))
        drive.offline = true
        assertEquals(CloudSyncManager.RemoteState.ERROR, manager.connect(token).state)
        assertEquals(CloudSyncManager.SetupResult.ERROR, manager.provision(token, "1234".toCharArray(), DriveRecoveryMethod.PIN, true))
        drive.offline = false
        assertEquals(old.identity, drive.envelope().identity)
        assertEquals(0, drive.generatedFolders)
    }

    @Test fun folderDeletedDuringListingCannotBecomeIndividualNoteDeletions() = runBlocking {
        val (note, _) = existing(present = true)
        drive.deleteFolderOnList = true
        assertEquals(CloudSyncManager.SyncOutcome.FolderMissing, manager.syncNow(token))
        assertFalse(requireNotNull(notes.getNoteById(note.id)).isTrashed)
        assertTrue(settings.syncedNoteVersions().containsKey(note.id))
        assertEquals(0, drive.deletedFiles)
    }

    @Test fun restoringTheFolderFromTrashResumesTheExistingRecoverySetup() = runBlocking {
        val (note, old) = existing(present = false)
        assertEquals(CloudSyncManager.RemoteState.FOLDER_MISSING, manager.connect(token).state)
        drive.folders["old-folder"] = DriveFolderStatus.PRESENT
        val connected = manager.connect(token)
        assertEquals(CloudSyncManager.RemoteState.HAS_ENVELOPE, connected.state)
        assertTrue(connected.canUseLocalKey)
        assertNull(settings.snapshot().driveRecoveryIssue)
        assertEquals(CloudSyncManager.SetupResult.STATE_CHANGED, manager.provision(token, "1234".toCharArray(), DriveRecoveryMethod.PIN, true))
        assertEquals(old.identity, drive.envelope().identity)
        assertEquals(note.content, notes.getNoteById(note.id)?.content)
        assertEquals(0, drive.generatedFolders)
    }

    @Test fun folderRestoredWhileResetIsPreparedKeepsItsOriginalRecoveryKey() = runBlocking {
        val (note, old) = existing(present = false)
        val localKey = settings.wrappedDataKey()
        val checkpoints = settings.syncedNoteVersions()
        drive.onRecoveryArchived = { drive.folders["old-folder"] = DriveFolderStatus.PRESENT }
        assertEquals(CloudSyncManager.SetupResult.STATE_CHANGED, manager.provision(token, "1234".toCharArray(), DriveRecoveryMethod.PIN, true))
        assertEquals(old.identity, drive.envelope().identity)
        assertEquals(localKey, settings.wrappedDataKey())
        assertEquals(checkpoints, settings.syncedNoteVersions())
        assertEquals(note.content, notes.getNoteById(note.id)?.content)
        assertEquals(setOf("old-folder"), drive.folders.filterValues { it == DriveFolderStatus.PRESENT }.keys)
        assertTrue(manager.connect(token).canUseLocalKey)
    }

    @Test fun keyReplacedDuringListingStopsBeforeMergingStaleDeletions() = runBlocking {
        val (note, _) = existing(present = true)
        val key = SyncCrypto.newDataKey()
        val replacement = try {
            DriveRecoveryEnvelope.create("098765".toCharArray(), DriveRecoveryMethod.PIN, key, "new-folder")
        } finally { key.fill(0) }
        drive.onNotesListed = {
            drive.replaceEnvelope(replacement)
            drive.folders["new-folder"] = DriveFolderStatus.PRESENT
        }
        assertEquals(CloudSyncManager.SyncOutcome.KeyChanged, manager.syncNow(token))
        assertFalse(requireNotNull(notes.getNoteById(note.id)).isTrashed)
        assertTrue(settings.syncedNoteVersions().containsKey(note.id))
        assertEquals(0, drive.deletedFiles)
    }

    @Test fun interruptedFolderCreationResumesWithoutAnotherKeyOrPassword() = runBlocking {
        val (_, old) = existing(present = false)
        drive.failFolderCreation = true
        assertEquals(CloudSyncManager.SetupResult.ERROR, manager.provision(token, "1234".toCharArray(), DriveRecoveryMethod.PIN, true))
        val staged = drive.envelope()
        assertNotEquals(old.identity, staged.identity)
        assertEquals(old.identity, settings.snapshot().driveKeyIdentity)
        assertNotNull(settings.pendingDriveSetup())
        drive.failFolderCreation = false
        val connected = manager.connect(token)
        assertEquals(CloudSyncManager.RemoteState.HAS_ENVELOPE, connected.state)
        assertTrue(connected.canUseLocalKey)
        assertEquals(staged.identity, settings.snapshot().driveKeyIdentity)
        assertEquals(1, drive.generatedFolders)
        assertNull(settings.pendingDriveSetup())
    }

    @Test fun rejectedKeyUpdateKeepsTheExistingLocalKeyAndCheckpoints() = runBlocking {
        val (_, old) = existing(present = false)
        val checkpoints = settings.syncedNoteVersions()
        val wrappedKey = settings.wrappedDataKey()
        drive.rejectKeyUpdate = true
        assertEquals(CloudSyncManager.SetupResult.STATE_CHANGED, manager.provision(token, "1234".toCharArray(), DriveRecoveryMethod.PIN, true))
        assertEquals(old.identity, drive.envelope().identity)
        assertEquals(wrappedKey, settings.wrappedDataKey())
        assertEquals(checkpoints, settings.syncedNoteVersions())
        assertFalse(drive.folders.values.any { it == DriveFolderStatus.PRESENT })
    }

    @Test fun replacedCloudKeyStopsAnOlderDeviceBeforeUpload() = runBlocking {
        val (note, _) = existing(present = true)
        val key = SyncCrypto.newDataKey()
        val replacement = DriveRecoveryEnvelope.create("098765".toCharArray(), DriveRecoveryMethod.PIN, key, "new-folder")
        key.fill(0)
        drive.replaceEnvelope(replacement)
        drive.folders["new-folder"] = DriveFolderStatus.PRESENT
        assertEquals(CloudSyncManager.SyncOutcome.KeyChanged, manager.syncNow(token))
        assertEquals(0, drive.noteLists)
        val status = manager.connect(token)
        assertFalse(status.canUseLocalKey)
        assertEquals(DriveRecoveryMethod.PIN, status.method)
        assertEquals(CloudSyncManager.RestoreResult.SUCCESS, manager.restore(token, "098765".toCharArray()))
        assertEquals(replacement.identity, settings.snapshot().driveKeyIdentity)
        assertEquals(note.content, notes.getNoteById(note.id)?.content)
        assertTrue(settings.syncedNoteVersions().isEmpty())
    }

    @Test fun existingFolderWithoutRecoveryMetadataIsNeverSilentlyReplaced() = runBlocking {
        existing(present = true)
        drive.files.entries.removeAll { it.value.name == "mynotes.key.json" }
        assertEquals(CloudSyncManager.RemoteState.KEY_MISSING, manager.connect(token).state)
        assertEquals(CloudSyncManager.SetupResult.STATE_CHANGED, manager.provision(token, "1234".toCharArray(), DriveRecoveryMethod.PIN, true))
        assertEquals(0, drive.generatedFolders)
    }

    @Test fun newAccountCanChooseAPassphraseWithoutRestartingAnything() = runBlocking {
        assertEquals(CloudSyncManager.RemoteState.NO_ENVELOPE, manager.connect(token).state)
        val secret = "a new recovery phrase".toCharArray()
        assertEquals(CloudSyncManager.SetupResult.SUCCESS, manager.provision(token, secret))
        assertTrue(secret.all { it == '\u0000' })
        assertEquals(DriveRecoveryMethod.PASSPHRASE, drive.envelope().method)
        assertTrue(manager.connect(token).canUseLocalKey)
        assertEquals(1, drive.generatedFolders)
        assertEquals(1, drive.files.size)
        assertEquals(0, drive.deletedFiles)
    }

    @Test fun switchingGoogleAccountsClearsOnlyThePreviousCloudState() = runBlocking {
        val (note, _) = existing(present = true)
        drive.email = "another@example.test"
        drive.files.clear()
        drive.folders.clear()
        assertEquals(CloudSyncManager.RemoteState.NO_ENVELOPE, manager.connect(token).state)
        assertEquals(drive.email, settings.snapshot().driveAccountEmail)
        assertNull(settings.wrappedDataKey())
        assertNull(settings.snapshot().driveFolderId)
        assertFalse(settings.snapshot().recoveryConfigured)
        assertEquals(0L, settings.snapshot().lastSyncedAt)
        assertTrue(settings.syncedNoteVersions().isEmpty())
        assertEquals(note.content, notes.getNoteById(note.id)?.content)
        assertFalse(requireNotNull(notes.getNoteById(note.id)).isTrashed)
        assertEquals(0, drive.deletedFiles)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun settingsRequiresResetApprovalAndUsesThePinForLaterUnlocks() = runBlocking {
        val (note, old) = existing(present = false)
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val applicationScope = CoroutineScope(SupervisorJob() + dispatcher)
        val store = ViewModelStore()
        try {
            val viewModel = SettingsViewModel(settings, manager, applicationScope)
            store.put("settings", viewModel)
            viewModel.onDriveAuthorized(token)
            var state = withTimeout(10_000) { viewModel.syncState.first { !it.connecting } }
            assertTrue(state.confirmRestart)
            assertNull(state.passphrasePrompt)
            viewModel.dismissDriveRestart()
            assertFalse(viewModel.syncState.value.confirmRestart)
            assertEquals(old.identity, drive.envelope().identity)
            assertEquals(0, drive.generatedFolders)

            viewModel.onDriveAuthorized(token)
            withTimeout(10_000) { viewModel.syncState.first { !it.connecting } }
            viewModel.confirmDriveRestart()
            assertTrue(viewModel.syncState.value.restartConfirmed)
            assertEquals(PassphraseMode.CREATE, viewModel.syncState.value.passphrasePrompt)
            viewModel.submitCreatePassphrase("0012".toCharArray(), DriveRecoveryMethod.PIN)
            withTimeout(10_000) { settings.settings.first { it.lastSyncedAt > 0 && it.driveKeyIdentity != old.identity } }
            withTimeout(10_000) { viewModel.syncState.first { !it.busy && !it.syncing } }
            assertEquals(DriveRecoveryMethod.PIN, drive.envelope().method)
            assertEquals(note.content, notes.getNoteById(note.id)?.content)
            assertTrue(drive.noteRecords[drive.envelope().folderId].orEmpty().containsKey(note.id))

            settings.setWrappedDataKey(null)
            viewModel.syncNow(token)
            state = withTimeout(10_000) { viewModel.syncState.first { it.passphrasePrompt == PassphraseMode.ENTER } }
            assertEquals(DriveRecoveryMethod.PIN, state.recoveryMethod)
            val wrongPin = "9999".toCharArray()
            viewModel.submitEnterPassphrase(wrongPin)
            state = withTimeout(10_000) { viewModel.syncState.first { it.passphraseError != null && !it.busy } }
            assertTrue(requireNotNull(state.passphraseError).contains("PIN", ignoreCase = true))
            assertTrue(wrongPin.all { it == '\u0000' })
            assertNull(settings.wrappedDataKey())

            settings.setLastSyncedAt(0)
            viewModel.submitEnterPassphrase("0012".toCharArray())
            withTimeout(10_000) { settings.settings.first { it.lastSyncedAt > 0 } }
            withTimeout(10_000) { viewModel.syncState.first { !it.busy && !it.syncing } }
            assertNull(viewModel.syncState.value.passphrasePrompt)
            assertTrue(manager.hasLocalKey())
            assertNull(settings.snapshot().driveRecoveryIssue)
        } finally {
            store.clear()
            applicationScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test fun freshPinSetupAndReconnectWorkThroughTheRealRestClient() = runBlocking {
        verifyRestSetup(DriveRecoveryMethod.PIN, "001234")
    }

    @Test fun freshPassphraseSetupAndReconnectWorkThroughTheRealRestClient() = runBlocking {
        verifyRestSetup(DriveRecoveryMethod.PASSPHRASE, "my new recovery phrase")
    }

    private fun managerUsingRest(): CloudSyncManager {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return CloudSyncManager(settings, notes, FolderRepository(database.folderDao(), database.noteDao(), context, database), reminders, context, drive.restClient())
    }

    private suspend fun verifyRestSetup(method: DriveRecoveryMethod, credential: String) {
        val restManager = managerUsingRest()
        val note = Note(UUID.randomUUID().toString(), "Retained local note", "My saved content", 100, 100)
        val reminder = Reminder(id = "rest-reminder", title = "First reminder", triggerAt = System.currentTimeMillis() + 100_000)
        notes.saveNote(note)
        settings.addTemplate(CustomTemplate(name = "First template", iconKey = "note", content = "Before sync"))
        reminders.save(reminder)
        assertEquals(CloudSyncManager.RemoteState.NO_ENVELOPE, restManager.connect(token).state)
        val secret = credential.toCharArray()
        assertEquals(CloudSyncManager.SetupResult.SUCCESS, restManager.provision(token, secret, method))
        assertTrue(secret.all { it == '\u0000' })
        assertEquals(method, drive.envelope().method)
        assertTrue(restManager.syncNow(token) is CloudSyncManager.SyncOutcome.Success)
        assertTrue(drive.noteRecords[drive.envelope().folderId].orEmpty().containsKey(note.id))
        assertEquals(note.content, notes.getNoteById(note.id)?.content)

        val edited = requireNotNull(notes.getNoteById(note.id)).copy(content = "Edited after first sync")
        notes.saveNote(edited)
        val savedStamp = requireNotNull(notes.getNoteById(note.id)).updatedAt
        settings.addTemplate(CustomTemplate(name = "Second template", iconKey = "note", content = "After sync"))
        reminders.save(reminder.copy(title = "Edited reminder", updatedAt = reminder.updatedAt + 1_000))
        assertTrue(restManager.syncNow(token) is CloudSyncManager.SyncOutcome.Success)
        assertEquals(3, drive.conditionalWrites)
        assertEquals(2, settings.allTemplatesForSync().size)
        val remoteNote = requireNotNull(drive.noteRecords[drive.envelope().folderId]?.get(note.id))
        assertEquals(savedStamp, remoteNote.updatedAt)
        val key = requireNotNull(drive.envelope().unlock(credential.toCharArray()))
        try {
            val content = requireNotNull(SyncCrypto.decrypt(SyncCrypto.decodeBase64(requireNotNull(drive.files[remoteNote.fileId]).content), key))
            assertEquals(edited.content, JSONObject(String(content, Charsets.UTF_8)).getString("content"))
        } finally { key.fill(0) }

        restManager.forgetLocalKeys()
        val reconnect = restManager.connect(token)
        assertEquals(CloudSyncManager.RemoteState.HAS_ENVELOPE, reconnect.state)
        assertEquals(method, reconnect.method)
        assertFalse(reconnect.canUseLocalKey)
        assertEquals(CloudSyncManager.RestoreResult.SUCCESS, restManager.restore(token, credential.toCharArray()))
        assertTrue(restManager.syncNow(token) is CloudSyncManager.SyncOutcome.Success)
        assertFalse(requireNotNull(notes.getNoteById(note.id)).isTrashed)
        assertEquals(edited.content, notes.getNoteById(note.id)?.content)
        assertEquals(0, drive.deletedFiles)
    }

    @Test fun setupPublishedBeforeMissingRevisionCanResumeWithoutANewCredential() = runBlocking {
        val restManager = managerUsingRest()
        val note = Note(UUID.randomUUID().toString(), "Saved before setup", "Do not lose this", 100, 100)
        notes.saveNote(note)
        assertEquals(CloudSyncManager.RemoteState.NO_ENVELOPE, restManager.connect(token).state)
        drive.omitFileEtag = true
        val failed = restManager.provision(token, "0012".toCharArray(), DriveRecoveryMethod.PIN)
        assertTrue(failed is CloudSyncManager.SetupResult.Failure && failed.message.contains("MISSING_REVISION"))
        val published = drive.envelope()
        assertNotNull(settings.pendingDriveSetup())
        assertNull(settings.wrappedDataKey())
        assertFalse(drive.folders.values.any { it == DriveFolderStatus.PRESENT })

        drive.omitFileEtag = false
        val resumed = restManager.connect(token)
        assertEquals(CloudSyncManager.RemoteState.HAS_ENVELOPE, resumed.state)
        assertTrue(resumed.canUseLocalKey)
        assertEquals(DriveRecoveryMethod.PIN, resumed.method)
        assertEquals(published.identity, drive.envelope().identity)
        assertEquals(published.identity, settings.snapshot().driveKeyIdentity)
        assertEquals(1, drive.generatedFolders)
        assertEquals(1, drive.files.values.count { it.name == "mynotes.key.json" })
        assertNull(settings.pendingDriveSetup())
        assertTrue(restManager.syncNow(token) is CloudSyncManager.SyncOutcome.Success)
        assertEquals(note.content, notes.getNoteById(note.id)?.content)
        assertFalse(requireNotNull(notes.getNoteById(note.id)).isTrashed)
    }

    @Test fun aLegacyPassphraseAccountReconnectsWithoutEtagHeaders() = runBlocking {
        val (note, original) = existing(present = true, legacy = true)
        val restManager = managerUsingRest()
        restManager.forgetLocalKeys()
        val state = restManager.connect(token)
        assertEquals(CloudSyncManager.RemoteState.HAS_ENVELOPE, state.state)
        assertEquals(DriveRecoveryMethod.PASSPHRASE, state.method)
        assertFalse(state.canUseLocalKey)
        assertEquals(CloudSyncManager.RestoreResult.SUCCESS, restManager.restore(token, "old phrase".toCharArray()))
        assertTrue(restManager.syncNow(token) is CloudSyncManager.SyncOutcome.Success)
        assertEquals(original.identity, drive.envelope().identity)
        assertEquals(note.content, notes.getNoteById(note.id)?.content)
        assertEquals(0, drive.generatedFolders)
    }

    @Test fun confirmedRestartUsesARealConditionalKeyUpdateWithoutHeaders() = runBlocking {
        val (note, original) = existing(present = false, legacy = true)
        val restManager = managerUsingRest()
        assertEquals(CloudSyncManager.SetupResult.SUCCESS, restManager.provision(token, "0099".toCharArray(), DriveRecoveryMethod.PIN, true))
        assertNotEquals(original.identity, drive.envelope().identity)
        assertEquals(1, drive.conditionalWrites)
        assertEquals(drive.envelope().identity, settings.snapshot().driveKeyIdentity)
        assertTrue(restManager.syncNow(token) is CloudSyncManager.SyncOutcome.Success)
        assertEquals(note.content, notes.getNoteById(note.id)?.content)
        assertEquals(0, drive.deletedFiles)
    }

    @Test fun rejectedConditionalRestartKeepsTheOriginalKeyAndLocalCheckpoint() = runBlocking {
        val (_, original) = existing(present = false)
        val localKey = settings.wrappedDataKey()
        val checkpoints = settings.syncedNoteVersions()
        val restManager = managerUsingRest()
        drive.rejectKeyUpdate = true
        val result = restManager.provision(token, "0099".toCharArray(), DriveRecoveryMethod.PIN, true)
        assertTrue(result is CloudSyncManager.SetupResult.Failure && result.message.contains("HTTP 412"))
        assertEquals(1, drive.conditionalWrites)
        assertEquals(original.identity, drive.envelope().identity)
        assertEquals(localKey, settings.wrappedDataKey())
        assertEquals(checkpoints, settings.syncedNoteVersions())
        assertFalse(drive.folders.values.any { it == DriveFolderStatus.PRESENT })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun authorizedAccountWithDeniedKeyStorageShowsTheGoogleFailureInSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var requestsToWrite = 0
        val client = DriveRestClient(OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val allowed = request.url.encodedPath.endsWith("/about")
            if (request.method != "GET") requestsToWrite++
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(if (allowed) 200 else 403).message("Test response")
                .body((if (allowed) """{"user":{"emailAddress":"personal@example.test"}}"""
                    else """{"error":{"message":"private response","errors":[{"reason":"insufficientPermissions"}]}}""").toResponseBody()).build()
        }.build())
        val restManager = CloudSyncManager(settings, notes, FolderRepository(database.folderDao(), database.noteDao(), context, database), reminders, context, client)
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val viewModel = SettingsViewModel(settings, restManager, scope)
            store.put("settings", viewModel)
            viewModel.onDriveAuthorized(token)
            val state = withTimeout(10_000) { viewModel.syncState.first { !it.connecting } }
            assertTrue(requireNotNull(state.error).contains("Looking up recovery data (HTTP 403)"))
            assertFalse(state.error.contains("private response"))
            assertNull(state.passphrasePrompt)
            assertFalse(state.confirmRestart)
            val secret = "0012".toCharArray()
            val result = restManager.provision(token, secret, DriveRecoveryMethod.PIN)
            assertTrue(result is CloudSyncManager.SetupResult.Failure)
            assertTrue(secret.all { it == '\u0000' })
            assertEquals(0, requestsToWrite)
            assertNull(settings.wrappedDataKey())
        } finally {
            store.clear()
            scope.cancel()
            Dispatchers.resetMain()
        }
    }

    private class RecoveryDrive : DriveSyncRemote {
        data class Stored(val id: String, val name: String, var content: String, var revision: Int = 1) {
            val etag: String get() = "\"revision-$revision\""
        }
        var email = "personal@example.test"
        var offline = false
        var failFolderCreation = false
        var rejectKeyUpdate = false
        var deleteFolderOnList = false
        var omitFileEtag = false
        var conditionalWrites = 0
        var onRecoveryArchived: (() -> Unit)? = null
        var onNotesListed: (() -> Unit)? = null
        var generatedFolders = 0
        var deletedFiles = 0
        var noteLists = 0
        val folders = linkedMapOf<String, DriveFolderStatus>()
        val files = linkedMapOf<String, Stored>()
        val noteRecords = linkedMapOf<String, MutableMap<String, RemoteNoteMeta>>()

        fun named(name: String): Stored? = files.values.singleOrNull { it.name == name }
        fun envelope(): DriveRecoveryEnvelope = DriveRecoveryEnvelope.decode(requireNotNull(named("mynotes.key.json")).content)
        fun replaceEnvelope(envelope: DriveRecoveryEnvelope) { requireNotNull(named("mynotes.key.json")).apply { content = envelope.encode(); revision++ } }

        fun restClient() = DriveRestClient(OkHttpClient.Builder().addInterceptor { chain -> httpResponse(chain.request()) }.build())

        private fun httpResponse(request: Request): Response {
            fun response(body: String, code: Int = 200): Response =
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Test response")
                    .body(body.toResponseBody()).build()
            val path = request.url.encodedPath
            if (path.endsWith("/about")) return response(JSONObject().put("user", JSONObject().put("emailAddress", email)).toString())
            if (path.endsWith("/generateIds")) return response(JSONObject().put("ids", JSONArray().put(generateFileId("http"))).toString())
            if (request.method == "GET" && path == "/drive/v3/files") {
                val query = requireNotNull(request.url.queryParameter("q"))
                val folder = folders.keys.singleOrNull { query == "'$it' in parents and trashed = false" }
                if (folder != null) {
                    val items = JSONArray()
                    noteRecords[folder].orEmpty().values.forEach { note ->
                        items.put(JSONObject().put("id", note.fileId).put("name", "${note.noteId}.mnote")
                            .put("appProperties", JSONObject().put("noteId", note.noteId).put("updatedAt", note.updatedAt.toString())))
                    }
                    return response(JSONObject().put("kind", "drive#fileList").put("files", items).toString())
                }
                if (request.url.queryParameter("pageToken") == null) return response("""{"kind":"drive#fileList","nextPageToken":"last-page"}""")
                val ids = if (request.url.queryParameter("spaces") == "appDataFolder") {
                    files.values.filter { query == "name = '${it.name}' and trashed = false" }.map { it.id }
                } else folders.filterValues { it == DriveFolderStatus.PRESENT }.keys.toList()
                val result = JSONObject().put("kind", "drive#fileList").put("incompleteSearch", false)
                if (ids.isNotEmpty()) result.put("files", JSONArray(ids.map { JSONObject().put("id", it) }))
                return response(result.toString())
            }
            val id = request.url.pathSegments.last()
            if (request.method == "GET") {
                if (folders[id] == DriveFolderStatus.PRESENT) return response(JSONObject().put("id", id)
                    .put("mimeType", "application/vnd.google-apps.folder").put("trashed", false).toString())
                val file = files[id] ?: return response("{}", 404)
                if (request.url.queryParameter("alt") == "media") {
                    assertEquals("/drive/v3/files/$id", path)
                    assertNull(request.header("If-Match"))
                    return response(file.content)
                }
                val metadata = JSONObject().put("id", id).put("version", file.revision.toString())
                if (path == "/drive/v2/files/$id") {
                    assertEquals("id,etag,version", request.url.queryParameter("fields"))
                    assertEquals("false", request.url.queryParameter("updateViewedDate"))
                    if (!omitFileEtag) metadata.put("etag", file.etag)
                }
                return response(metadata.toString())
            }
            if (request.method == "POST" && path == "/drive/v3/files") {
                val metadata = JSONObject(Buffer().also { requireNotNull(request.body).writeTo(it) }.readUtf8())
                val folderId = metadata.getString("id")
                check(createFolder("http", metadata.getString("name"), folderId))
                return response(JSONObject().put("id", folderId).toString())
            }
            val multipart = request.body as MultipartBody
            val metadata = JSONObject(Buffer().also { multipart.part(0).body.writeTo(it) }.readUtf8())
            val content = Buffer().also { multipart.part(1).body.writeTo(it) }.readUtf8()
            if (request.method != "POST") {
                assertEquals("PUT", request.method)
                assertEquals("/upload/drive/v2/files/$id", path)
                conditionalWrites++
                if (!updateCollection("http", id, content, requireNotNull(request.header("If-Match")))) return response("{}", 412)
                metadata.optJSONArray("properties")?.let { properties ->
                    assertFalse(metadata.has("appProperties"))
                    val values = (0 until properties.length()).associate { index ->
                        val property = properties.getJSONObject(index)
                        assertEquals("PRIVATE", property.getString("visibility"))
                        property.getString("key") to property.getString("value")
                    }
                    val noteId = requireNotNull(values["noteId"])
                    val records = noteRecords.values.single { it[noteId]?.fileId == id }
                    records[noteId] = RemoteNoteMeta(id, noteId, requireNotNull(values["updatedAt"]).toLong())
                }
                return response(JSONObject().put("id", id).toString())
            }
            val parent = metadata.getJSONArray("parents").getString(0)
            val created = if (parent == "appDataFolder") createAppDataFile("http", metadata.getString("name"), content)
                else {
                    val properties = metadata.getJSONObject("appProperties")
                    putNoteFile("http", parent, properties.getString("noteId"), metadata.getString("name"), content,
                        properties.getString("updatedAt").toLong(), null, null)
                }
            return response(JSONObject().put("id", requireNotNull(created)).toString())
        }

        override fun fetchAccountEmail(accessToken: String): String? = email.takeUnless { offline }
        override fun lookupFolder(accessToken: String, name: String): DriveFileLookup {
            if (offline) return DriveFileLookup.Unknown
            val active = folders.filterValues { it == DriveFolderStatus.PRESENT }.keys
            return when (active.size) { 0 -> DriveFileLookup.Missing; 1 -> DriveFileLookup.Found(active.single()); else -> DriveFileLookup.Unknown }
        }
        override fun lookupAppDataFile(accessToken: String, name: String): DriveFileLookup =
            if (offline) DriveFileLookup.Unknown else named(name)?.let { DriveFileLookup.Found(it.id) } ?: DriveFileLookup.Missing
        override fun folderStatus(accessToken: String, folderId: String): DriveFolderStatus =
            if (offline) DriveFolderStatus.UNKNOWN else folders[folderId] ?: DriveFolderStatus.MISSING
        override fun generateFileId(accessToken: String): String? = if (offline) null else "new-folder-${++generatedFolders}"
        override fun createFolder(accessToken: String, name: String, fileId: String): Boolean {
            if (offline || failFolderCreation) return false
            folders[fileId] = DriveFolderStatus.PRESENT
            return true
        }
        override fun createAppDataFile(accessToken: String, name: String, content: String): String? {
            if (offline) return null
            val id = "file-${UUID.randomUUID()}"
            files[id] = Stored(id, name, content)
            if (name.startsWith("mynotes.key.retired.")) onRecoveryArchived?.invoke()
            return id
        }
        override fun downloadNote(accessToken: String, fileId: String): RemoteNoteDownload? =
            if (offline) null else files[fileId]?.let { RemoteNoteDownload(it.content, it.etag) }
        override fun updateCollection(accessToken: String, fileId: String, content: String, etag: String): Boolean {
            val file = files[fileId] ?: return false
            if (offline || file.etag != etag || (rejectKeyUpdate && file.name == "mynotes.key.json")) return false
            file.content = content
            file.revision++
            return true
        }
        override fun listFolderNotes(accessToken: String, folderId: String): List<RemoteNoteMeta>? {
            noteLists++
            if (offline) return null
            onNotesListed?.invoke()
            if (deleteFolderOnList) { folders[folderId] = DriveFolderStatus.MISSING; return emptyList() }
            return noteRecords[folderId]?.values?.toList().orEmpty()
        }
        override fun putNoteFile(accessToken: String, folderId: String, noteId: String, name: String, content: String, updatedAt: Long, existingId: String?, etag: String?): String? {
            if (folderStatus(accessToken, folderId) != DriveFolderStatus.PRESENT) return null
            val id = existingId ?: "note-$noteId"
            files[id] = Stored(id, name, content)
            noteRecords.getOrPut(folderId) { linkedMapOf() }[noteId] = RemoteNoteMeta(id, noteId, updatedAt)
            return id
        }
        override fun deleteFile(accessToken: String, fileId: String, etag: String?): Boolean {
            if (offline) return false
            deletedFiles++
            return files.remove(fileId) != null
        }
    }
}