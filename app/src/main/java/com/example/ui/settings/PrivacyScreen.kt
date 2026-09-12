package com.example.ui.settings

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.BrandGradientButton
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
    var launchError by remember { mutableStateOf<String?>(null) }
    fun open(intent: Intent) {
        launchError = null
        runCatching { context.startActivity(intent) }.onFailure { launchError = "This destination could not be opened. Check that a browser or Settings app is available." }
    }
    SettingsInfoDialog(
        title = "Data controls",
        subtitle = "Storage & permissions",
        icon = Icons.Rounded.Tune,
        onDismiss = onDismiss,
        closeDescription = "Close data controls",
    ) {
        AppInfoSection(icon = Icons.Rounded.Storage, title = "On this device") {
            AppInfoParagraph("Delete notes, books and templates through Trash, and reminders from Reminders. Android's Clear storage removes all local app data. Export anything you need first.")
            DataControlButton("Device storage", Icons.Rounded.Storage) {
                open(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }
        }
        AppInfoSection(icon = Icons.Rounded.FolderOpen, title = "Google Drive") {
            AppInfoParagraph("Disconnect Drive on every device before removing cloud data. Delete the MyNotes and MyNotes Shared folders in Drive, then empty Drive Trash. In Drive Settings > Manage apps, delete MyNotes hidden app data to remove current and older recovery keys, templates and reminders.")
            DataControlButton("Manage Drive files", Icons.Rounded.FolderOpen) {
                open(Intent(Intent.ACTION_VIEW, Uri.parse("https://drive.google.com/drive/my-drive")))
            }
        }
        AppInfoSection(icon = Icons.Rounded.ManageAccounts, title = "Account access") {
            AppInfoParagraph("Disconnecting does not delete existing copies. MyNotes does not create or delete a separate app account or your Google account.")
            DataControlButton("Google account permissions", Icons.Rounded.ManageAccounts) {
                open(Intent(Intent.ACTION_VIEW, Uri.parse("https://myaccount.google.com/connections")))
            }
        }
        AppInfoParagraph("Exports in Downloads and copies received by other people must be removed separately.")
        launchError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
    }
}

@Composable
private fun DataControlButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    BrandGradientButton(
        text = text,
        icon = icon,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { role = Role.Button },
    )
}