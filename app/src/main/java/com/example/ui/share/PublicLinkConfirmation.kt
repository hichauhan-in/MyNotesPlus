package com.example.ui.share

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun PublicLinkConfirmation(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create a public link?") },
        text = { Text(
            "A readable, unencrypted copy will be uploaded to your Google Drive. Anyone with the link can read it. " +
                "Later edits to this note will not update the copy.\n\n" +
                "Only share public content. Use encrypted sharing for private, financial, or identity information. " +
                "To revoke a link, delete its copy from the MyNotes Shared folder in Drive.",
        ) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Create public link") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}