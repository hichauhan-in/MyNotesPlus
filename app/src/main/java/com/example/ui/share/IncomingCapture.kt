package com.example.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.example.data.attachments.AttachmentStore
import com.example.data.share.TextImportPolicy
import com.example.di.AppContainer
import com.example.domain.model.AttachmentMarkup
import com.example.domain.model.Note
import com.example.domain.model.NoteType
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CaptureViewModel : ViewModel() {
    var incoming by mutableStateOf<Intent?>(null)
        private set
    var title by mutableStateOf("")
    var text by mutableStateOf("")
        private set
    var destinations by mutableStateOf<List<Note>>(emptyList())
        private set
    var attachmentCount by mutableStateOf(0)
        private set
    var busy by mutableStateOf(false)
        private set
    var ready by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var savedNoteId by mutableStateOf<String?>(null)
        private set
    private var initialHandled = false
    private var preparedIntent: Intent? = null
    private var media = emptyList<Uri>()
    private var audio = false

    fun acceptInitial(intent: Intent?) {
        if (!initialHandled) { initialHandled = true; accept(intent) }
    }

    fun accept(intent: Intent?) {
        if (busy || intent?.action !in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_PROCESS_TEXT)) return
        incoming = intent
        preparedIntent = null
        ready = false
        error = null
    }

    @Suppress("DEPRECATION")
    suspend fun prepare() {
        val request = incoming ?: return
        if (preparedIntent === request) return
        preparedIntent = request
        try {
            val raw = request.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                ?: request.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
            text = TextImportPolicy.sanitize(raw)
            title = request.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty().take(200)
            media = when (request.action) {
                Intent.ACTION_SEND_MULTIPLE -> request.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
                else -> listOfNotNull(request.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            }
            require(media.size <= 10 && media.all { it.scheme == "content" }) { "Share up to 10 images or recordings at once" }
            val mime = request.type.orEmpty()
            require(media.isEmpty() || mime.startsWith("image/") || mime.startsWith("audio/")) { "This attachment type is not supported" }
            audio = mime.startsWith("audio/")
            require(text.isNotBlank() || media.isNotEmpty()) { "There is no content to save" }
            attachmentCount = media.size
            destinations = withContext(Dispatchers.Default) {
                AppContainer.noteRepository!!.allNotes.first().filter { it.type == NoteType.TEXT && !it.isTrashed && !it.isArchived }
            }
            ready = true
        } catch (failure: Exception) { error = failure.message ?: "Could not read shared content" }
    }

    fun save(context: Context, destinationId: String?) {
        if (!ready || busy) return
        busy = true
        error = null
        val app = context.applicationContext
        val capturedTitle = title.take(200)
        val capturedText = text
        AppContainer.applicationScope.launch(Dispatchers.Main.immediate) {
            val written = mutableListOf<String>()
            try {
                savedNoteId = withContext(Dispatchers.IO) {
                    media.forEach { uri ->
                        val ownAuthority = "${app.packageName}.fileprovider"
                        require(uri.authority != ownAuthority) { "Import an exported copy, not a private app file" }
                        val name = if (audio) AttachmentStore.importAudioFromUri(app, uri)
                        else requireNotNull(AttachmentStore.importFromUri(app, uri)) { "Could not import an image (maximum 32 MB)" }
                        written.add(name)
                    }
                    val body = (listOf(capturedText).filter(String::isNotBlank) + written.map {
                        if (audio) AttachmentMarkup.audioToken(it) else AttachmentMarkup.imageToken(it)
                    }).joinToString("\n\n")
                    val repository = AppContainer.noteRepository!!
                    val existing = destinationId?.let { requireNotNull(repository.getNoteById(it)) { "The destination note no longer exists" } }
                    check(existing == null || (!existing.isTrashed && existing.type == NoteType.TEXT)) { "Choose an active text note" }
                    val now = System.currentTimeMillis()
                    val note = existing?.copy(content = listOf(existing.content, body).filter(String::isNotBlank).joinToString("\n\n"),
                        attachments = (existing.attachments + written).distinct())
                        ?: Note(UUID.randomUUID().toString(), capturedTitle.ifBlank { "Captured note" }, body, now, now, attachments = written.toList())
                    repository.saveNote(note, expectedUpdatedAt = existing?.updatedAt)
                    note.id
                }
            } catch (failure: Exception) {
                withContext(Dispatchers.IO) { written.forEach { AttachmentStore.delete(app, it) } }
                error = failure.message ?: "Could not save shared content"
            } finally { busy = false }
        }
    }

    fun dismiss() {
        if (busy) return
        incoming = null
        savedNoteId = null
        media = emptyList()
        text = ""
        title = ""
        destinations = emptyList()
    }
}

@Composable
fun IncomingCapture(model: CaptureViewModel, onSaved: (String) -> Unit) {
    val intent = model.incoming ?: return
    val context = LocalContext.current
    var destination by remember(intent) { mutableStateOf<String?>(null) }
    var chooseDestination by remember { mutableStateOf(false) }
    LaunchedEffect(intent) { model.prepare() }
    LaunchedEffect(model.savedNoteId) {
        model.savedNoteId?.let { id -> model.dismiss(); onSaved(id) }
    }
    AlertDialog(onDismissRequest = model::dismiss, title = { Text("Save to MyNotes+") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (destination == null) OutlinedTextField(model.title, { model.title = it.take(200) }, label = { Text("Title") }, enabled = !model.busy, modifier = Modifier.fillMaxWidth())
                if (model.text.isNotBlank()) Text(model.text.take(600), maxLines = 8)
                if (model.attachmentCount > 0) Text("${model.attachmentCount} attachments")
                OutlinedButton(onClick = { chooseDestination = true }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(model.destinations.firstOrNull { it.id == destination }?.title?.ifBlank { "Untitled" } ?: "New note")
                }
                DropdownMenu(expanded = chooseDestination, onDismissRequest = { chooseDestination = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                    DropdownMenuItem(text = { Text("New note") }, onClick = { destination = null; chooseDestination = false })
                    model.destinations.forEach { note ->
                        DropdownMenuItem(text = { Text(note.title.ifBlank { "Untitled" }, maxLines = 2) }, onClick = { destination = note.id; chooseDestination = false })
                    }
                }
                model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = model.ready && !model.busy, onClick = { model.save(context, destination) }) { Text(if (model.busy) "Saving..." else if (destination == null) "Save" else "Append") } },
        dismissButton = { TextButton(enabled = !model.busy, onClick = model::dismiss) { Text("Cancel") } },
    )
}