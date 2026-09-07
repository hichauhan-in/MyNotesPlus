package com.example.ui.settings

import android.app.NotificationManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.settings.AppSettings
import com.example.data.sync.CloudSyncManager
import com.example.data.sync.DriveRest
import com.example.di.AppContainer
import com.example.ui.theme.ThemeMode
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
)

class SettingsViewModel : ViewModel() {
    private val repository = AppContainer.settingsRepository!!
    private val syncManager = AppContainer.cloudSyncManager!!

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
        _syncState.value = SyncScreenState(connecting = true)
        viewModelScope.launch {
            val email = withContext(Dispatchers.IO) { DriveRest.fetchAccountEmail(accessToken) }
            if (email == null) {
                _syncState.value = SyncScreenState(error = "Connected, but couldn't reach Google Drive. Please try again.")
                return@launch
            }
            repository.setDriveAccountEmail(email)
            repository.setCloudSyncEnabled(true)
            pendingToken = accessToken
            if (syncManager.hasLocalKey()) {
                // Already set up on this device - just run a sync.
                _syncState.value = SyncScreenState()
                runSync(accessToken)
                return@launch
            }
            when (syncManager.remoteState(accessToken)) {
                CloudSyncManager.RemoteState.HAS_ENVELOPE ->
                    _syncState.value = SyncScreenState(passphrasePrompt = PassphraseMode.ENTER)
                CloudSyncManager.RemoteState.NO_ENVELOPE ->
                    _syncState.value = SyncScreenState(passphrasePrompt = PassphraseMode.CREATE)
                CloudSyncManager.RemoteState.ERROR ->
                    _syncState.value = SyncScreenState(error = "Couldn't reach Google Drive. Please try again.")
            }
        }
    }

    fun onDriveAuthFailed(message: String?) {
        _syncState.value = SyncScreenState(error = message ?: "Google sign-in was cancelled.")
    }

    /** Creates a brand-new recovery passphrase + key envelope for this account. */
    fun submitCreatePassphrase(passphrase: CharArray) {
        val token = pendingToken
        if (token == null) {
            passphrase.fill('\u0000')
            _syncState.value = _syncState.value.copy(passphrasePrompt = null, error = "Please reconnect Google Drive.")
            return
        }
        _syncState.value = _syncState.value.copy(busy = true, passphraseError = null)
        viewModelScope.launch {
            val ok = syncManager.provision(token, passphrase)
            if (ok) {
                _syncState.value = SyncScreenState()
                runSync(token)
            } else {
                _syncState.value = _syncState.value.copy(busy = false, passphraseError = "Couldn't set up sync. Please try again.")
            }
        }
    }

    /** Unlocks the existing key envelope with the entered recovery passphrase. */
    fun submitEnterPassphrase(passphrase: CharArray) {
        val token = pendingToken
        if (token == null) {
            passphrase.fill('\u0000')
            _syncState.value = _syncState.value.copy(passphrasePrompt = null, error = "Please reconnect Google Drive.")
            return
        }
        _syncState.value = _syncState.value.copy(busy = true, passphraseError = null)
        viewModelScope.launch {
            when (syncManager.restore(token, passphrase)) {
                CloudSyncManager.RestoreResult.SUCCESS -> {
                    _syncState.value = SyncScreenState()
                    runSync(token)
                }
                CloudSyncManager.RestoreResult.WRONG_PASSPHRASE ->
                    _syncState.value = _syncState.value.copy(busy = false, passphraseError = "Incorrect passphrase. Please try again.")
                CloudSyncManager.RestoreResult.ERROR ->
                    _syncState.value = _syncState.value.copy(busy = false, passphraseError = "Couldn't restore from Drive. Please try again.")
            }
        }
    }

    fun dismissPassphrase() {
        _syncState.value = _syncState.value.copy(passphrasePrompt = null, passphraseError = null, busy = false)
    }

    /** Runs a two-way note sync, updating the spinner and the persisted last-synced time. */
    private fun runSync(accessToken: String) {
        _syncState.value = _syncState.value.copy(syncing = true, error = null)
        viewModelScope.launch {
            _syncState.value = when (syncManager.syncNow(accessToken)) {
                is CloudSyncManager.SyncOutcome.Success -> _syncState.value.copy(syncing = false)
                CloudSyncManager.SyncOutcome.NotUnlocked ->
                    _syncState.value.copy(syncing = false, error = "Enter your recovery passphrase to sync.")
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
