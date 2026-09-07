package com.example.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.attachments.AttachmentStore
import com.example.data.attachments.EncAttachment
import com.example.data.ml.TextExtractor
import com.example.domain.model.AttachmentMarkup
import com.example.domain.model.ExpItem
import com.example.domain.model.ExpenseMoney
import com.example.domain.model.ReceiptParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
internal fun ReceiptCapture(initial: ExpItem?, onSave: (ExpItem) -> Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val files = remember { mutableListOf<String>() }
    var savedFile by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var amount by remember { mutableStateOf(initial?.amount?.takeIf { it > 0 }?.let(ExpenseMoney::text).orEmpty()) }
    var receiptDate by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<String?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun recognize(fileName: String) {
        image = fileName
        val draft = withContext(Dispatchers.Default) { ReceiptParser.parse(TextExtractor.fromAttachment(context, fileName)) }
        if (name.isBlank()) name = draft.merchant
        draft.amount?.let { amount = ExpenseMoney.text(it) }
        receiptDate = draft.date.orEmpty()
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            error = null
            scope.launch {
                try {
                    val stored = withContext(Dispatchers.IO) { requireNotNull(AttachmentStore.importFromUri(context, uri)) { "Could not read this image (maximum 32 MB)" } }
                    files.add(stored)
                    recognize(stored)
                } catch (failure: Exception) { error = failure.message ?: "Could not read this receipt" }
                finally { busy = false }
            }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = cameraFile
        cameraFile = null
        if (success && file != null) {
            busy = true
            error = null
            scope.launch {
                try {
                    check(withContext(Dispatchers.IO) { AttachmentStore.encryptFileInPlace(context, file.name) }) { "Could not encrypt this receipt" }
                    files.add(file.name)
                    recognize(file.name)
                } catch (failure: Exception) { file.delete(); error = failure.message ?: "Could not save this receipt" }
                finally { busy = false }
            }
        } else file?.delete()
    }
    DisposableEffect(Unit) {
        onDispose {
            files.filter { it != savedFile }.forEach { AttachmentStore.delete(context, it) }
        }
    }
    val parsedAmount = ExpenseMoney.parse(amount)
    val validDate = receiptDate.isBlank() || runCatching {
        val position = ParsePosition(0)
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }.parse(receiptDate, position) != null && position.index == receiptDate.length
    }.getOrDefault(false)
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Receipt") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row {
                TextButton(enabled = !busy, onClick = {
                    val file = AttachmentStore.newImageFile(context)
                    cameraFile = file
                    runCatching { camera.launch(AttachmentStore.uriForFile(context, file)) }.onFailure { file.delete(); cameraFile = null; error = "Camera unavailable" }
                }) { Icon(Icons.Rounded.PhotoCamera, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("Camera") }
                TextButton(enabled = !busy, onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Rounded.AddPhotoAlternate, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("Photos")
                }
            }
            image?.let { AsyncImage(EncAttachment(it), "Receipt preview", modifier = Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Fit) }
            if (busy) CircularProgressIndicator(Modifier.size(24.dp))
            OutlinedTextField(name, { name = it.take(120) }, label = { Text("Merchant / action") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(amount, { if (ExpenseMoney.inputPattern.matches(it)) amount = it }, label = { Text("Amount (INR)") }, enabled = !busy, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(receiptDate, { receiptDate = it.take(10) }, label = { Text("Receipt date (YYYY-MM-DD)") }, enabled = !busy, isError = !validDate, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !busy && image != null && name.isNotBlank() && parsedAmount != null && parsedAmount > 0 && validDate, onClick = {
            val file = requireNotNull(image)
            val item = (initial ?: ExpItem()).copy(name = name.trim(), amount = requireNotNull(parsedAmount), receiptToken = AttachmentMarkup.imageToken(file), receiptDate = receiptDate.ifBlank { null })
            if (onSave(item)) { savedFile = file; onDismiss() } else error = "This action is no longer available"
        }) { Text("Save action") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun ReceiptImage(token: String, onDismiss: () -> Unit) {
    val file = AttachmentMarkup.parseLine(token)?.fileName ?: return
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
                Row {
                    Text("Receipt", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close receipt") }
                }
                AsyncImage(EncAttachment(file), "Receipt", modifier = Modifier.weight(1f).fillMaxWidth(), contentScale = ContentScale.Fit)
            }
        }
    }
}