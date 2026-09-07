package com.example.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.backup.VaultArchive
import com.example.data.backup.VaultBackupManager
import com.example.di.AppContainer
import com.example.ui.share.ImportPassphraseDialog
import com.example.ui.share.SharePassphraseDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.crypto.AEADBadTagException

internal enum class BackupPrompt { EXPORT, IMPORT }

internal data class BackupUiState(
    val busy: Boolean = false,
    val prompt: BackupPrompt? = null,
    val preview: VaultBackupManager.Summary? = null,
    val message: String? = null,
    val error: String? = null,
    val wrongPassphrase: Boolean = false,
)

internal class BackupViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(BackupUiState())
    val state = mutableState.asStateFlow()
    private var manager: VaultBackupManager? = null
    private var uri: Uri? = null
    private var prepared: VaultBackupManager.Prepared? = null
    @Volatile private var disposed = false

    fun choose(context: Context, target: Uri, prompt: BackupPrompt) {
        if (mutableState.value.busy) return
        manager = manager ?: VaultBackupManager(context)
        uri = target
        mutableState.value = BackupUiState(prompt = prompt)
    }

    fun submit(passphrase: CharArray) {
        val worker = manager
        val target = uri
        val prompt = mutableState.value.prompt
        if (worker == null || target == null || prompt == null || mutableState.value.busy) {
            passphrase.fill('\u0000')
            return
        }
        mutableState.value = BackupUiState(busy = true, prompt = prompt.takeIf { it == BackupPrompt.IMPORT })
        AppContainer.applicationScope.launch(Dispatchers.IO) {
            try {
                if (prompt == BackupPrompt.EXPORT) {
                    val summary = worker.export(target, passphrase)
                    mutableState.value = BackupUiState(message = "Encrypted backup saved. ${summary.description}")
                } else {
                    val result = worker.prepare(target, passphrase)
                    if (disposed) worker.discard(result)
                    else {
                        prepared = result
                        mutableState.value = BackupUiState(preview = result.summary)
                    }
                }
            } catch (failure: Exception) {
                val wrong = failure is AEADBadTagException
                mutableState.value = BackupUiState(
                    prompt = BackupPrompt.IMPORT.takeIf { wrong && prompt == BackupPrompt.IMPORT },
                    wrongPassphrase = wrong,
                    error = if (wrong) "Incorrect passphrase or damaged backup" else failure.message ?: "Backup operation failed",
                )
            } finally { passphrase.fill('\u0000') }
        }
    }

    fun restore() {
        val worker = manager ?: return
        val pending = prepared ?: return
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true, error = null)
        AppContainer.applicationScope.launch(Dispatchers.IO) {
            try {
                val summary = worker.restore(pending)
                prepared = null
                mutableState.value = BackupUiState(message = "Restored separate copies. ${summary.description}")
            } catch (failure: Exception) {
                worker.discard(pending)
                prepared = null
                mutableState.value = BackupUiState(error = failure.message ?: "Restore failed; existing notes were not replaced")
            }
        }
    }

    fun cancel() {
        if (mutableState.value.busy) return
        val pending = prepared
        val worker = manager
        prepared = null
        uri = null
        mutableState.value = BackupUiState()
        if (pending != null && worker != null) AppContainer.applicationScope.launch { worker.discard(pending) }
    }

    override fun onCleared() {
        disposed = true
        if (!mutableState.value.busy) cancel()
        super.onCleared()
    }
}

@Composable
internal fun BackupControls() {
    val model: BackupViewModel = viewModel()
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { model.choose(context, it, BackupPrompt.EXPORT) }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { model.choose(context, it, BackupPrompt.IMPORT) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { save.launch("MyNotes-${System.currentTimeMillis()}.${VaultArchive.EXTENSION}") },
            enabled = !state.busy, modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.Download, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Create encrypted backup")
        }
        OutlinedButton(onClick = { open.launch(arrayOf("*/*")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Restore, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Restore backup")
        }
        if (state.busy) Row {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Processing encrypted backup...", style = MaterialTheme.typography.bodySmall)
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
    when (state.prompt) {
        BackupPrompt.EXPORT -> SharePassphraseDialog(
            onConfirm = model::submit, onDismiss = model::cancel, title = "Protect your backup",
            description = "This passphrase is required to restore your backup. It cannot be recovered if forgotten.", confirmLabel = "Create backup",
        )
        BackupPrompt.IMPORT -> ImportPassphraseDialog(
            wrong = state.wrongPassphrase, busy = state.busy, onConfirm = model::submit, onDismiss = model::cancel,
            title = "Unlock backup", description = "Enter the passphrase used to protect this backup.",
        )
        null -> Unit
    }
    state.preview?.let { preview ->
        AlertDialog(
            onDismissRequest = model::cancel, title = { Text("Restore backup?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(preview.description)
                Text("Adds separate copies. Existing notes, security settings and Drive credentials will not be replaced.")
            } },
            confirmButton = { TextButton(onClick = model::restore, enabled = !state.busy) { Text(if (state.busy) "Restoring..." else "Restore") } },
            dismissButton = { TextButton(onClick = model::cancel, enabled = !state.busy) { Text("Cancel") } },
        )
    }
}