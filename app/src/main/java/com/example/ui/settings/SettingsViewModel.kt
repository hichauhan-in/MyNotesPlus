package com.example.ui.settings

import android.app.NotificationManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.settings.AppSettings
import com.example.data.settings.SettingsRepository
import com.example.data.sync.CloudSyncManager
import com.example.data.sync.DriveRecoveryMethod
import com.example.di.AppContainer
import com.example.ui.theme.ThemeMode
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which recovery-passphrase dialog to show, if any. */
enum class PassphraseMode { CREATE, ENTER }

/** Transient UI state for the Google Drive connect flow (not persisted). */
data class SyncScreenState(
    val connecting: Boolean = false,
    /** True while provisioning/restoring the encryption key. */
    val busy: Boolean = false,
    /** True while a two-way note sync is running. */
    val syncing: Boolean = false,
    val error: String? = null,
    /** Non-null when the recovery-passphrase dialog should be shown. */
    val passphrasePrompt: PassphraseMode? = null,
    val passphraseError: String? = null,
    val folderMissing: Boolean = false,
    val confirmRestart: Boolean = false,
    val restartConfirmed: Boolean = false,
    val recoveryMethod: DriveRecoveryMethod = DriveRecoveryMethod.PASSPHRASE,
)

class SettingsViewModel(
    private val repository: SettingsRepository = AppContainer.settingsRepository!!,
    private val syncManager: CloudSyncManager = AppContainer.cloudSyncManager!!,
    private val applicationScope: CoroutineScope = AppContainer.applicationScope,
) : ViewModel() {
    /** The current Drive access token (short-lived, in-memory only), captured on connect. */
    private var pendingToken: String? = null

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    private val _syncState = MutableStateFlow(SyncScreenState())
    val syncState: StateFlow<SyncScreenState> = _syncState.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setDynamicColor(value: Boolean) {
        viewModelScope.launch { repository.setDynamicColor(value) }
    }

    fun setAccentIndex(index: Int) {
        viewModelScope.launch { repository.setAccentIndex(index) }
    }

    fun setCloudSyncEnabled(value: Boolean) {
        viewModelScope.launch { repository.setCloudSyncEnabled(value) }
    }

    fun setAppLockEnabled(value: Boolean, context: Context) {
        viewModelScope.launch {
            repository.setAppLockEnabled(value)
            if (value) context.getSystemService(NotificationManager::class.java)?.cancelAll()
            WidgetUpdater.refreshAll(context.applicationContext)
        }
    }

    fun setHideFromRecents(value: Boolean) {
        viewModelScope.launch { repository.setHideFromRecents(value) }
    }

    fun setDefaultExportFolder(uri: String?) {
        viewModelScope.launch { repository.setDefaultExportFolder(uri) }
    }

    fun setOpenNotesInEditMode(value: Boolean) {
        viewModelScope.launch { repository.setOpenNotesInEditMode(value) }
    }

    fun setSwipeNavigationEnabled(value: Boolean) {
        viewModelScope.launch { repository.setSwipeNavigationEnabled(value) }
    }

    fun setPageInkTextMode(mode: com.example.data.settings.PageInkTextMode) {
        viewModelScope.launch { repository.setPageInkTextMode(mode) }
    }

    fun setSmartSuggestionsEnabled(value: Boolean) {
        viewModelScope.launch { repository.setSmartSuggestionsEnabled(value) }
    }

    // ---- Google Drive sync ----

    /** Called while the account picker / consent screen is showing. */
    fun onDriveConnecting() {
        _syncState.value = SyncScreenState(connecting = true)
    }

    /**
     * Called with a valid Drive access token once authorization succeeds. Confirms the token works,
     * remembers the account, then either sets up the encryption key (first time) or asks for the
     * recovery passphrase to unlock it (new device / after disconnect).
     */
    fun onDriveAuthorized(accessToken: String) {
        if (_syncState.value.busy) return
        _syncState.value = SyncScreenState(connecting = true)
        viewModelScope.launch {
            pendingToken = accessToken
            val status = syncManager.connect(accessToken)
            applyRecoveryStatus(status)
            if (status.canUseLocalKey) runSync(accessToken)
        }
    }

    private fun applyRecoveryStatus(status: CloudSyncManager.RecoveryStatus) {
        _syncState.value = when (status.state) {
            CloudSyncManager.RemoteState.HAS_ENVELOPE -> SyncScreenState(
                passphrasePrompt = if (status.canUseLocalKey) null else PassphraseMode.ENTER,
                recoveryMethod = status.method,
            )
            CloudSyncManager.RemoteState.NO_ENVELOPE -> SyncScreenState(passphrasePrompt = PassphraseMode.CREATE)
            CloudSyncManager.RemoteState.FOLDER_MISSING -> SyncScreenState(folderMissing = true, confirmRestart = true)
            CloudSyncManager.RemoteState.KEY_MISSING -> SyncScreenState(error =
                "The Drive folder exists, but its recovery key is missing. Your local notes are safe. Back up local notes and review the existing Drive data before setting up again.")
            CloudSyncManager.RemoteState.ERROR -> SyncScreenState(error =
                status.error ?: "Couldn't verify the Drive folder and recovery key. Check your connection and Google account, then try again. Nothing was reset.")
        }
    }

    fun confirmDriveRestart() {
        if (_syncState.value.busy || !_syncState.value.folderMissing) return
        if (pendingToken == null) {
            _syncState.value = SyncScreenState(folderMissing = true, error = "Reconnect Google Drive to start over.")
        } else {
            _syncState.value = SyncScreenState(folderMissing = true, restartConfirmed = true, passphrasePrompt = PassphraseMode.CREATE)
        }
    }

    fun dismissDriveRestart() {
        if (!_syncState.value.busy) _syncState.value = _syncState.value.copy(confirmRestart = false)
    }
    fun onDriveAuthFailed(message: String?) {
        _syncState.value = SyncScreenState(error = message ?: "Google sign-in was cancelled.")
    }

    /** Creates a brand-new recovery passphrase + key envelope for this account. */
    fun submitCreatePassphrase(passphrase: CharArray, method: DriveRecoveryMethod = DriveRecoveryMethod.PASSPHRASE) {
        if (_syncState.value.busy) { passphrase.fill('\u0000'); return }
        val token = pendingToken
        if (token == null) {
            passphrase.fill('\u0000')
            _syncState.value = _syncState.value.copy(passphrasePrompt = null, error = "Please reconnect Google Drive.")
            return
        }
        val restart = _syncState.value.restartConfirmed
        _syncState.value = _syncState.value.copy(busy = true, passphraseError = null)
        applicationScope.launch(Dispatchers.Main.immediate) {
            when (val result = syncManager.provision(token, passphrase, method, restart)) {
                CloudSyncManager.SetupResult.SUCCESS -> {
                    _syncState.value = SyncScreenState()
                    runSync(token)
                }
                CloudSyncManager.SetupResult.STATE_CHANGED -> {
                    val status = syncManager.connect(token)
                    applyRecoveryStatus(status)
                    if (status.canUseLocalKey) runSync(token)
                }
                CloudSyncManager.SetupResult.ERROR -> _syncState.value = _syncState.value.copy(
                    busy = false,
                    passphraseError = "Couldn't finish Drive setup. Local notes are unchanged. Retry, or reconnect to resume an interrupted setup.",
                )
                is CloudSyncManager.SetupResult.Failure -> _syncState.value = _syncState.value.copy(
                    busy = false, passphraseError = result.message,
                )
            }
        }
    }

    /** Unlocks the existing key envelope with the entered recovery passphrase. */
    fun submitEnterPassphrase(passphrase: CharArray) {
        if (_syncState.value.busy) { passphrase.fill('\u0000'); return }
        val token = pendingToken
        if (token == null) {
            passphrase.fill('\u0000')
            _syncState.value = _syncState.value.copy(passphrasePrompt = null, error = "Please reconnect Google Drive.")
            return
        }
        _syncState.value = _syncState.value.copy(busy = true, passphraseError = null)
        applicationScope.launch(Dispatchers.Main.immediate) {
            when (val result = syncManager.restore(token, passphrase)) {
                CloudSyncManager.RestoreResult.SUCCESS -> {
                    _syncState.value = SyncScreenState()
                    runSync(token)
                }
                CloudSyncManager.RestoreResult.WRONG_PASSPHRASE ->
                    _syncState.value = _syncState.value.copy(busy = false, passphraseError = "Incorrect recovery ${_syncState.value.recoveryMethod.label.lowercase()}. Please try again.")
                CloudSyncManager.RestoreResult.FOLDER_MISSING ->
                    _syncState.value = SyncScreenState(folderMissing = true, confirmRestart = true)
                CloudSyncManager.RestoreResult.KEY_MISSING ->
                    _syncState.value = SyncScreenState(error = "The Drive recovery key is missing. Your local notes are unchanged.")
                CloudSyncManager.RestoreResult.ERROR ->
                    _syncState.value = _syncState.value.copy(busy = false, passphraseError = "Couldn't restore from Drive. Please try again.")
                is CloudSyncManager.RestoreResult.Failure ->
                    _syncState.value = _syncState.value.copy(busy = false, passphraseError = result.message)
            }
        }
    }

    fun dismissPassphrase() {
        if (_syncState.value.busy) return
        _syncState.value = _syncState.value.copy(passphrasePrompt = null, passphraseError = null, busy = false)
    }

    /** Runs a two-way note sync, updating the spinner and the persisted last-synced time. */
    private fun runSync(accessToken: String) {
        _syncState.value = _syncState.value.copy(syncing = true, error = null)
        viewModelScope.launch {
            _syncState.value = when (syncManager.syncNow(accessToken)) {
                is CloudSyncManager.SyncOutcome.Success -> _syncState.value.copy(syncing = false)
                CloudSyncManager.SyncOutcome.FolderMissing ->
                    SyncScreenState(folderMissing = true, confirmRestart = true)
                CloudSyncManager.SyncOutcome.KeyMissing ->
                    _syncState.value.copy(syncing = false, error = "The Drive recovery key is missing. Reconnect to review recovery.")
                CloudSyncManager.SyncOutcome.NotUnlocked, CloudSyncManager.SyncOutcome.KeyChanged ->
                    syncManager.connect(accessToken).let { status ->
                        applyRecoveryStatus(status)
                        _syncState.value
                    }
                CloudSyncManager.SyncOutcome.Error ->
                    _syncState.value.copy(syncing = false, error = "Sync failed. Please try again.")
            }
        }
    }

    /** Manual "Sync now" - the screen supplies a freshly authorized [accessToken]. */
    fun syncNow(accessToken: String) {
        pendingToken = accessToken
        runSync(accessToken)
    }

    fun onSyncStarting() {
        _syncState.value = _syncState.value.copy(syncing = true, error = null)
    }

    fun onSyncFailed(message: String?) {
        _syncState.value = _syncState.value.copy(syncing = false, error = message ?: "Couldn't reach Google Drive.")
    }

    fun disconnectDrive() {
        if (_syncState.value.busy || _syncState.value.connecting || _syncState.value.syncing) return
        viewModelScope.launch {
            syncManager.forgetLocalKeys()
            repository.setDriveAccountEmail(null)
            repository.setCloudSyncEnabled(false)
            pendingToken = null
            _syncState.value = SyncScreenState()
        }
    }

    fun clearSyncError() {
        _syncState.value = _syncState.value.copy(error = null)
    }
}
