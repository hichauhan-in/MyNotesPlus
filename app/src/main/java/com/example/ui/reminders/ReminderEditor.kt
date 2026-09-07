package com.example.ui.reminders

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.Reminder
import com.example.domain.model.ReminderRepeat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * A reusable reminder editor (shared by the Reminders screen and the in-note "Remind me" action).
 * Pass [initial] to edit an existing reminder, or leave it null and provide [prefillTitle] /
 * [prefillBody] / [prefillNoteId] to create one (e.g. attached to the note being edited).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderEditorSheet(
    initial: Reminder?,
    onSave: (Reminder) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    prefillTitle: String = "",
    prefillBody: String = "",
    prefillNoteId: String? = null,
    prefillTriggerAt: Long? = null,
    checklistOptions: List<String> = emptyList(),
    busy: Boolean = false,
    error: String? = null,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { SimpleDateFormat("d MMM yyyy", locale) }
    val timeFormat = remember(locale) { SimpleDateFormat("h:mm a", locale) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val defaultTime = remember {
        Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis
    }
    var title by remember { mutableStateOf(initial?.title ?: prefillTitle) }
    var body by remember { mutableStateOf(initial?.body ?: prefillBody) }
    var triggerAt by remember { mutableStateOf(initial?.triggerAt ?: prefillTriggerAt ?: defaultTime) }
    var repeat by remember { mutableStateOf(initial?.repeat ?: ReminderRepeat.NONE) }
    var checklistText by remember { mutableStateOf(initial?.checklistText) }
    var showChecklistPicker by remember { mutableStateOf(false) }
    val noteId = initial?.noteId ?: prefillNoteId

    fun pickDate() {
        val cal = Calendar.getInstance().apply { timeInMillis = triggerAt }
        DatePickerDialog(
            context,
            { _, y, m, d ->
                triggerAt = Calendar.getInstance().apply {
                    timeInMillis = triggerAt
                    set(Calendar.YEAR, y); set(Calendar.MONTH, m); set(Calendar.DAY_OF_MONTH, d)
                }.timeInMillis
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    fun pickTime() {
        val cal = Calendar.getInstance().apply { timeInMillis = triggerAt }
        TimePickerDialog(
            context,
            { _, h, min ->
                triggerAt = Calendar.getInstance().apply {
                    timeInMillis = triggerAt
                    set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min); set(Calendar.SECOND, 0)
                }.timeInMillis
            },
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(context),
        ).show()
    }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = if (initial == null) "New reminder" else "Edit reminder",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("What to remember (optional)") },
                keyboardOptions = KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            if (checklistOptions.isNotEmpty()) {
                OutlinedButton(onClick = { showChecklistPicker = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(checklistText ?: "Whole checklist", maxLines = 2)
                }
                DropdownMenu(expanded = showChecklistPicker, onDismissRequest = { showChecklistPicker = false }) {
                    DropdownMenuItem(text = { Text("Whole checklist") }, onClick = { checklistText = null; showChecklistPicker = false })
                    checklistOptions.distinct().forEach { option ->
                        DropdownMenuItem(text = { Text(option, maxLines = 2) }, onClick = { checklistText = option; showChecklistPicker = false })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReminderPickerButton(
                    icon = Icons.Rounded.CalendarMonth,
                    text = dateFormat.format(triggerAt),
                    modifier = Modifier.weight(1f),
                    onClick = { pickDate() },
                )
                ReminderPickerButton(
                    icon = Icons.Rounded.Schedule,
                    text = timeFormat.format(triggerAt),
                    modifier = Modifier.weight(1f),
                    onClick = { pickTime() },
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Repeat",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReminderRepeat.entries.forEach { option ->
                    ReminderRepeatChip(label = option.label, selected = option == repeat) { repeat = option }
                }
            }
            Spacer(Modifier.height(22.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(4.dp))
                ReminderSaveButton(
                    enabled = title.isNotBlank() && !busy && triggerAt > System.currentTimeMillis(),
                    onClick = {
                        onSave(
                            (initial ?: Reminder(title = "", triggerAt = triggerAt, noteId = noteId)).copy(
                                title = title.trim(),
                                body = body.trim(),
                                triggerAt = triggerAt,
                                repeat = repeat,
                                enabled = true,
                                completedAt = null,
                                lastNotifiedAt = null,
                                snoozedUntil = null,
                                repeatAnchorAt = if (initial != null && initial.triggerAt == triggerAt && initial.repeat == repeat) initial.repeatAnchorAt else triggerAt,
                                checklistText = checklistText,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ReminderPickerButton(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
private fun ReminderRepeatChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ReminderSaveButton(enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = "Save",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    )
}
