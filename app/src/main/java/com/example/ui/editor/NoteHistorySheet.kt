package com.example.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.AttachmentMarkup
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteHistorySheet(viewModel: EditorViewModel, onDismiss: () -> Unit) {
    val versions by viewModel.versions.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<Pair<String, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val date = remember { DateFormat.getDateTimeInstance() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Recent versions", style = MaterialTheme.typography.titleLarge)
            if (versions.isEmpty()) Text("No previous versions", style = MaterialTheme.typography.bodyMedium)
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                items(versions, key = { it.id }) { version ->
                    Text(date.format(Date(version.updatedAt)), modifier = Modifier.fillMaxWidth().clickable {
                        scope.launch {
                            val content = viewModel.versionPreview(version.id)
                            if (content != null) { selected = version.id; preview = content }
                        }
                    }.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
    preview?.let { content ->
        AlertDialog(onDismissRequest = { if (!busy) preview = null }, title = { Text(content.first.ifBlank { "Untitled" }) },
            text = { Text(AttachmentMarkup.stripTokens(content.second).take(1200).ifBlank { "Media or drawing version" }, maxLines = 12) },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                selected?.let { id ->
                    busy = true
                    viewModel.recoverVersion(id) { message ->
                        busy = false
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                        preview = null
                    }
                }
            }) { Text("Recover copy") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { preview = null }) { Text("Cancel") } },
        )
    }
}