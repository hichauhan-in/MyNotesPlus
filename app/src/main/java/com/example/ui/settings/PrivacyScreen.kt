package com.example.ui.settings

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val html by produceState<String?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("index.html").bufferedReader().use { it.readText() } }
                .getOrDefault("<html><body><h1>Privacy policy unavailable</h1><p>Please contact the developer at https://www.hichauhan.in/.</p></body></html>")
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Privacy policy", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close privacy policy") }
            }
            val policy = html
            if (policy == null) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { viewContext -> WebView(viewContext).apply {
                    settings.javaScriptEnabled = false
                    settings.domStorageEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.blockNetworkLoads = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val uri = request.url
                            if (uri.host == "mynotes.invalid") return false
                            if (uri.scheme == "https" || uri.scheme == "mailto") {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                            }
                            return true
                        }
                    }
                    loadDataWithBaseURL("https://mynotes.invalid/", policy, "text/html", "UTF-8", null)
                } },
                onRelease = { it.destroy() },
            )
        }
    }
}

@Composable
internal fun DataControlsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    fun open(url: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Data controls") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Local data", style = MaterialTheme.typography.titleSmall)
                Text("Delete notes, books and templates through Trash, and reminders from Reminders. Android's Clear storage removes all local app data. Export anything you need first.")
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                }) { Text("Device storage") }
                Text("Google Drive", style = MaterialTheme.typography.titleSmall)
                Text("Disconnect Drive on every device before removing cloud data. Delete the MyNotes and MyNotes Shared folders in Drive, then empty Drive Trash. In Drive Settings > Manage apps, delete MyNotes hidden app data to remove recovery keys, templates and reminders.")
                TextButton(onClick = { open("https://drive.google.com/drive/my-drive") }) { Text("Manage Drive files") }
                Text("Disconnecting does not delete existing copies. MyNotes does not create or delete a separate app account or your Google account.")
                TextButton(onClick = { open("https://myaccount.google.com/connections") }) { Text("Google account permissions") }
                Text("Exports in Downloads and copies received by other people must be removed separately.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}