package com.example.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.auth.api.identity.Identity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.sync.DriveAuth
import com.example.data.sync.DriveRecoveryMethod
import com.example.data.settings.PageInkTextMode
import com.example.ui.components.NeuIconButton
import com.example.ui.components.NeuSurface
import com.example.ui.components.mynotesSwitchColors
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.brandGradientHorizontal
import com.example.ui.util.responsiveHorizontalPadding

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val automaticSyncError by com.example.data.sync.SyncStatus.error.collectAsStateWithLifecycle()
    val insets = WindowInsets.systemBars.asPaddingValues()
    val contentSidePadding = responsiveHorizontalPadding(compact = 20.dp)
    var showAppInfo by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showDataControls by remember { mutableStateOf(false) }

    // Google Drive connect flow: the Authorization API returns an access token directly, or a
    // consent screen to launch first (for a first-time grant) - handled by this launcher.
    val driveContext = LocalContext.current
    val driveConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val data = result.data
        if (data == null) {
            viewModel.onDriveAuthFailed("Consent closed without a result (code ${result.resultCode}).")
            return@rememberLauncherForActivityResult
        }
        val outcome = runCatching {
            Identity.getAuthorizationClient(driveContext).getAuthorizationResultFromIntent(data)
        }
        val token = outcome.getOrNull()?.accessToken
        when {
            token != null -> viewModel.onDriveAuthorized(token)
            outcome.isFailure -> viewModel.onDriveAuthFailed(outcome.exceptionOrNull()?.message ?: "Authorization failed.")
            else -> viewModel.onDriveAuthFailed("No access token returned (code ${result.resultCode}).")
        }
    }
    fun connectDrive() {
        viewModel.onDriveConnecting()
        Identity.getAuthorizationClient(driveContext)
            .authorize(DriveAuth.request())
            .addOnSuccessListener { authResult ->
                val pending = authResult.pendingIntent
                if (authResult.hasResolution() && pending != null) {
                    runCatching {
                        driveConsentLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    }.onFailure { viewModel.onDriveAuthFailed(it.message) }
                } else {
                    val token = authResult.accessToken
                    if (token != null) viewModel.onDriveAuthorized(token)
                    else viewModel.onDriveAuthFailed("No access token was returned.")
                }
            }
            .addOnFailureListener { viewModel.onDriveAuthFailed(it.message) }
    }
    // "Sync now": silently re-authorize (no UI when already granted) to get a fresh token, then sync.
    fun syncNow() {
        viewModel.onSyncStarting()
        Identity.getAuthorizationClient(driveContext)
            .authorize(DriveAuth.request())
            .addOnSuccessListener { authResult ->
                val token = authResult.accessToken
                if (!authResult.hasResolution() && token != null) {
                    viewModel.syncNow(token)
                } else {
                    viewModel.onSyncFailed("Please reconnect Google Drive to sync.")
                }
            }
            .addOnFailureListener { viewModel.onSyncFailed(it.message) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = insets.calculateTopPadding() + 8.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeuIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                size = 44.dp,
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = contentSidePadding)
                .padding(bottom = insets.calculateBottomPadding() + 32.dp),
        ) {
            // ---- Appearance ----
            SettingsSection(title = "Appearance", icon = Icons.Rounded.ColorLens) {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                ThemeModeSelector(
                    selected = settings.themeMode,
                    onSelect = viewModel::setThemeMode,
                )
                Spacer(Modifier.height(8.dp))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingsDivider()
                    ToggleRow(
                        icon = Icons.Rounded.Brightness6,
                        title = "Dynamic colour",
                        subtitle = "Match your wallpaper (Material You)",
                        checked = settings.dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ---- Security ----
            SettingsSection(title = "Security & Privacy", icon = Icons.Rounded.Shield) {
                InfoBanner(
                    icon = Icons.Rounded.Lock,
                    text = "Note content is encrypted with AES-256-GCM using Android Keystore keys. Exports and public shares are readable copies.",
                )
                Spacer(Modifier.height(14.dp))
                ToggleRow(
                    icon = Icons.Rounded.Fingerprint,
                    title = "App lock",
                    subtitle = "Require fingerprint or screen lock to open",
                    checked = settings.appLockEnabled,
                    onCheckedChange = { viewModel.setAppLockEnabled(it, driveContext) },
                )
                SettingsDivider()
                ToggleRow(
                    icon = Icons.Rounded.VisibilityOff,
                    title = "Hide in recent apps",
                    subtitle = "Block screenshots and app-switcher previews",
                    checked = settings.hideFromRecents,
                    onCheckedChange = viewModel::setHideFromRecents,
                )
                SettingsDivider()
                SettingsLinkRow(
                    icon = Icons.Rounded.Shield,
                    title = "Privacy policy",
                    subtitle = "Storage, Google services and sharing",
                    trailingIcon = Icons.Rounded.ChevronRight,
                    onClick = { showPrivacyPolicy = true },
                )
                SettingsDivider()
                SettingsLinkRow(
                    icon = Icons.Rounded.Tune,
                    title = "Data controls",
                    subtitle = "Local data, Drive copies and access",
                    trailingIcon = Icons.Rounded.ChevronRight,
                    onClick = { showDataControls = true },
                )
            }

            Spacer(Modifier.height(20.dp))

            // ---- Cloud ----
            SettingsSection(title = "Backup & Sync", icon = Icons.Rounded.CloudUpload) {
                BackupControls()
                SettingsDivider()
                when {
                    syncState.connecting || syncState.busy -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = if (syncState.busy) "Setting up encryption…" else "Connecting to Google Drive…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    settings.driveAccountEmail == null -> {
                        SettingsLinkRow(
                            icon = Icons.Rounded.Link,
                            title = "Connect Google Drive",
                            subtitle = "Back up and sync your notes - end-to-end encrypted",
                            trailingIcon = Icons.Rounded.ChevronRight,
                            onClick = { connectDrive() },
                        )
                    }
                    else -> {
                        SettingsLinkRow(
                            icon = Icons.Rounded.CheckCircle,
                            title = "Connected",
                            subtitle = settings.driveAccountEmail ?: "",
                            onClick = {},
                        )
                        if (!settings.recoveryConfigured || settings.driveRecoveryIssue != null || syncState.folderMissing) {
                            SettingsDivider()
                            SettingsLinkRow(
                                icon = Icons.Rounded.Lock,
                                title = if (settings.driveRecoveryIssue != null || syncState.folderMissing) "Repair Drive sync" else "Finish encryption setup",
                                subtitle = when (settings.driveRecoveryIssue) {
                                    "FOLDER_MISSING" -> "Check the missing folder or start over"
                                    "KEY_CHANGED" -> "Unlock with the current recovery passphrase or PIN"
                                    "KEY_MISSING" -> "Review the missing cloud recovery key"
                                    else -> "Choose a recovery passphrase or PIN"
                                },
                                trailingIcon = Icons.Rounded.ChevronRight,
                                onClick = { connectDrive() },
                            )
                        } else {
                            SettingsDivider()
                            SettingsLinkRow(
                                icon = Icons.Rounded.Sync,
                                title = if (syncState.syncing) "Syncing…" else "Sync now",
                                subtitle = if (syncState.syncing) "Syncing your notes with Drive"
                                    else lastSyncedLabel(settings.lastSyncedAt),
                                trailingIcon = if (syncState.syncing) null else Icons.Rounded.ChevronRight,
                                onClick = { if (!syncState.syncing) syncNow() },
                            )
                        }
                        SettingsDivider()
                        SettingsLinkRow(
                            icon = Icons.Rounded.LinkOff,
                            title = "Disconnect this device",
                            subtitle = "Stops syncing here; your Drive copy stays",
                            onClick = { viewModel.disconnectDrive() },
                        )
                    }
                }
                (syncState.error ?: automaticSyncError.takeIf { settings.driveAccountEmail != null })?.let { error ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(12.dp))
                InfoBanner(
                    icon = Icons.Rounded.Shield,
                    text = "Drive sync is encrypted and covers note records, templates and reminders. Images, recordings and book structure are included in local encrypted backups, not Drive sync. Keep your recovery passphrase or PIN safe.",
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }

            Spacer(Modifier.height(20.dp))

            // ---- Notifications ----
            SettingsSection(title = "Notifications", icon = Icons.Rounded.NotificationsActive) {
                val notifContext = LocalContext.current
                SettingsLinkRow(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "Notification settings",
                    subtitle = "Manage how reminders alert you",
                    trailingIcon = Icons.Rounded.OpenInNew,
                    onClick = { com.example.data.reminders.NotificationHelper.openSettings(notifContext) },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingsDivider()
                    val canExact = com.example.data.reminders.ReminderScheduler.canScheduleExact(notifContext)
                    SettingsLinkRow(
                        icon = Icons.Rounded.Schedule,
                        title = "Precise reminders",
                        subtitle = if (canExact) "On - reminders fire at the exact time you set"
                        else "Off - reminders may arrive a little late. Tap to allow.",
                        trailingIcon = if (canExact) null else Icons.Rounded.OpenInNew,
                        onClick = {
                            runCatching {
                                notifContext.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                            }
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Reminders you set on a note or from the + menu appear here as notifications. " +
                        "They are stored encrypted on your device and never leave it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

            // ---- Additional configurations ----
            SettingsSection(title = "Additional configurations", icon = Icons.Rounded.Tune) {
                ToggleRow(
                    icon = Icons.Rounded.Edit,
                    title = "Open notes in edit mode",
                    subtitle = if (settings.openNotesInEditMode)
                        "Notes open ready to edit"
                    else
                        "Notes open read-only - tap the pencil to edit",
                    checked = settings.openNotesInEditMode,
                    onCheckedChange = { viewModel.setOpenNotesInEditMode(it) },
                )
                SettingsDivider()
                ToggleRow(
                    icon = Icons.Rounded.Swipe,
                    title = "Swipe between tabs",
                    subtitle = if (settings.swipeNavigationEnabled)
                        "Swipe left or right on the home screen to switch tabs"
                    else
                        "Use the filter chips only",
                    checked = settings.swipeNavigationEnabled,
                    onCheckedChange = { viewModel.setSwipeNavigationEnabled(it) },
                )
                SettingsDivider()
                // Where typed text goes relative to a full-page pen drawing.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Draw,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Drawing & text",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (settings.pageInkTextMode == PageInkTextMode.BELOW)
                                "Draw over existing text; continue typing below ink"
                            else
                                "Text and drawings can overlap freely",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                PageInkModeSelector(
                    selected = settings.pageInkTextMode,
                    onSelect = { viewModel.setPageInkTextMode(it) },
                )
                SettingsDivider()
                ToggleRow(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "Smart suggestions",
                    subtitle = if (settings.smartSuggestionsEnabled)
                        "Spot dates, links, phone numbers & addresses in a note - on‑device"
                    else
                        "Off - no on‑device text analysis",
                    checked = settings.smartSuggestionsEnabled,
                    onCheckedChange = { viewModel.setSmartSuggestionsEnabled(it) },
                )
            }

            Spacer(Modifier.height(20.dp))

            // ---- Export ----
            SettingsSection(title = "Export", icon = Icons.Rounded.FolderOpen) {
                val exportContext = LocalContext.current
                val exportFolder = settings.defaultExportFolder
                val supportsDownloads = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
                val exportPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocumentTree(),
                ) { uri ->
                    if (uri != null) {
                        runCatching {
                            exportContext.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                            )
                        }
                        viewModel.setDefaultExportFolder(uri.toString())
                    }
                }
                SettingsLinkRow(
                    icon = Icons.Rounded.FolderOpen,
                    title = "Default export folder",
                    subtitle = exportFolder?.let { exportFolderLabel(it) }
                        ?: if (supportsDownloads) "Downloads/MyNotes (default)" else "Ask each time when exporting",
                    trailingIcon = Icons.Rounded.Edit,
                    onClick = { runCatching { exportPicker.launch(null) } },
                )
                if (exportFolder != null) {
                    SettingsDivider()
                    SettingsLinkRow(
                        icon = Icons.Rounded.Close,
                        title = "Reset to default",
                        subtitle = if (supportsDownloads) "Go back to saving in Downloads/MyNotes" else "Go back to choosing a location each time",
                        onClick = { viewModel.setDefaultExportFolder(null) },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Notes export as text, Markdown, HTML or PDF; a book exports as a ZIP that " +
                        "keeps its folder structure and attachments. By default files are saved to your " +
                        "Downloads/MyNotes folder - pick a different folder above to change that.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

            // ---- About ----
            SettingsSection(title = "About", icon = Icons.Rounded.Info) {
                val context = LocalContext.current
                SettingsLinkRow(
                    icon = Icons.Rounded.Person,
                    title = "About the developer",
                    subtitle = "https://www.hichauhan.in/",
                    trailingIcon = Icons.Rounded.OpenInNew,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.hichauhan.in/")),
                            )
                        }
                    },
                )
                SettingsDivider()
                SettingsLinkRow(
                    icon = Icons.Rounded.Info,
                    title = "Application Information",
                    subtitle = "Understanding MyNotes+",
                    trailingIcon = Icons.Rounded.ChevronRight,
                    onClick = { showAppInfo = true },
                )
            }
        }
    }

    if (showAppInfo) {
        AppInfoDialog(onDismiss = { showAppInfo = false })
    }
    if (showPrivacyPolicy) PrivacyPolicyDialog(onDismiss = { showPrivacyPolicy = false })
    if (showDataControls) DataControlsDialog(onDismiss = { showDataControls = false })

    if (syncState.confirmRestart) DriveRestartDialog(
        onConfirm = viewModel::confirmDriveRestart,
        onCheckAgain = { viewModel.dismissDriveRestart(); connectDrive() },
        onDismiss = viewModel::dismissDriveRestart,
    )

    when (syncState.passphrasePrompt) {
        PassphraseMode.CREATE -> CreatePassphraseDialog(
            busy = syncState.busy,
            error = syncState.passphraseError,
            onConfirm = { secret, method -> viewModel.submitCreatePassphrase(secret, method) },
            onDismiss = { viewModel.dismissPassphrase() },
            restarting = syncState.restartConfirmed,
        )
        PassphraseMode.ENTER -> EnterPassphraseDialog(
            busy = syncState.busy,
            error = syncState.passphraseError,
            onConfirm = { viewModel.submitEnterPassphrase(it) },
            onDismiss = { viewModel.dismissPassphrase() },
            method = syncState.recoveryMethod,
        )
        null -> Unit
    }
}

private fun exportFolderLabel(uriStr: String): String = runCatching {
    val id = DocumentsContract.getTreeDocumentId(Uri.parse(uriStr))
    id.substringAfter(':').ifBlank { id }
}.getOrDefault("Selected folder")

private fun lastSyncedLabel(millis: Long): String =
    if (millis <= 0L) {
        "Not synced yet"
    } else {
        "Last synced " + DateUtils.getRelativeTimeSpanString(
            millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
        )
    }

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    NeuSurface(
        cornerRadius = 24.dp,
        contentPadding = PaddingValues(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}

@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val options = listOf(
        Triple(ThemeMode.SYSTEM, "System", Icons.Rounded.SettingsBrightness),
        Triple(ThemeMode.LIGHT, "Light", Icons.Rounded.LightMode),
        Triple(ThemeMode.DARK, "Dark", Icons.Rounded.DarkMode),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (mode, label, icon) ->
            val isSelected = mode == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .then(
                        if (isSelected) Modifier.background(brandGradientHorizontal())
                        else Modifier
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(mode) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = mynotesSwitchColors(),
        )
    }
}

@Composable
private fun PageInkModeSelector(
    selected: PageInkTextMode,
    onSelect: (PageInkTextMode) -> Unit,
) {
    val options = listOf(
        Triple(PageInkTextMode.BELOW, "Protect ink", Icons.Rounded.VerticalAlignBottom),
        Triple(PageInkTextMode.FREE, "Free overlay", Icons.Rounded.Layers),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (mode, label, icon) ->
            val isSelected = mode == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .then(
                        if (isSelected) Modifier.background(brandGradientHorizontal())
                        else Modifier
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(mode) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoBanner(
    icon: ImageVector,
    text: String,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.10f))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SettingsLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailingIcon: ImageVector? = null,
    trailingLabel: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            trailingLabel != null -> Text(
                text = trailingLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )

            trailingIcon != null -> Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    )
}

/**
 * A full-screen "About the app" popup with a little breathing room around the edges. It explains
 * what MyNotes+ is for, why it's useful, what's inside, and how it keeps notes private.
 */
@Composable
private fun AppInfoDialog(onDismiss: () -> Unit) {
    SettingsInfoDialog("MyNotes+", "Private notes, truly yours", Icons.Rounded.AutoAwesome, onDismiss) {
        AppInfoSection(icon = Icons.Rounded.AutoAwesome, title = "Our mission") {
            AppInfoParagraph(
                "MyNotes+ is built on one idea: your notes belong to you and no one else. " +
                    "Write, plan and remember without a required sign-in or advertising. " +
                    "Optional Google services have their own data practices, explained in the privacy policy.",
            )
        }
        AppInfoSection(icon = Icons.Rounded.Bolt, title = "Why you'll love it") {
            AppInfoBullet("Note content is encrypted on your device.")
            AppInfoBullet("Create and read notes offline; optional models may need a download.")
            AppInfoBullet("No MyNotes account required and no advertising.")
            AppInfoBullet("Organise your way with books, tags, pins and colours.")
        }
        AppInfoSection(icon = Icons.Rounded.Widgets, title = "What's inside") {
            AppInfoBullet("Notes, checklists, tables and callouts.")
            AppInfoBullet("Boards and expense trackers.")
            AppInfoBullet("Photos, voice notes and reusable templates.")
            AppInfoBullet("Recoverable Trash with adjustable retention.")
        }
        AppInfoSection(icon = Icons.Rounded.Shield, title = "Privacy & safety") {
            AppInfoParagraph(
                "Note content uses AES-256-GCM and Android Keystore keys. Metadata and temporary " +
                    "capture/export files are described in the privacy policy. Drive sync encrypts content; " +
                    "public links are readable copies. AI processing is local, but Google SDKs can send diagnostics.",
            )
        }
        AppInfoSection(icon = Icons.Rounded.Lock, title = "In your control") {
            AppInfoParagraph(
                "Add an optional fingerprint or screen-lock to open the app, protect previews in the " +
                    "recent-apps switcher, and delete anything whenever you like. It's your space, on your terms.",
            )
        }
    }
}

@Composable
internal fun SettingsInfoDialog(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onDismiss: () -> Unit,
    closeDescription: String = "Close",
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val insets = WindowInsets.systemBars.asPaddingValues()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = insets.calculateTopPadding() + 20.dp,
                    bottom = insets.calculateBottomPadding() + 20.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                // Branded header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(brandGradientHorizontal())
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = closeDescription,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Scrollable body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 22.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
internal fun AppInfoSection(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Spacer(Modifier.height(10.dp))
    Column(Modifier.fillMaxWidth()) { content() }
    Spacer(Modifier.height(22.dp))
}

@Composable
internal fun AppInfoParagraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AppInfoBullet(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Shared surface for the recovery-passphrase dialogs. */
@Composable
private fun PassphraseScaffold(
    title: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    confirmButton: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), content = content) },
        confirmButton = confirmButton,
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
        properties = DialogProperties(securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn),
    )
}

/** First-time setup: choose a recovery passphrase (with confirmation). */
@Composable
internal fun CreatePassphraseDialog(
    busy: Boolean,
    error: String?,
    onConfirm: (CharArray, DriveRecoveryMethod) -> Unit,
    onDismiss: () -> Unit,
    restarting: Boolean = false,
) {
    var method by remember { mutableStateOf(DriveRecoveryMethod.PASSPHRASE) }
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var pinRiskAccepted by remember { mutableStateOf(false) }
    val pin = method == DriveRecoveryMethod.PIN
    val secretValid = method.accepts(pass.toCharArray())
    val valid = secretValid && pass == confirm && (!pin || pinRiskAccepted)
    val keyboardType = if (pin) KeyboardType.NumberPassword else KeyboardType.Password
    fun acceptsInput(value: String): Boolean = if (pin) value.length <= 10 && value.all { it in '0'..'9' } else value.length <= 1024
    PassphraseScaffold(
        title = if (restarting) "New Drive recovery credential" else "Protect Drive sync",
        busy = busy,
        onDismiss = onDismiss,
        confirmButton = {
            Button(onClick = { onConfirm(pass.toCharArray(), method) }, enabled = valid && !busy) {
                Text(if (pin) "Set PIN" else "Set passphrase")
            }
        },
    ) {
        Text(
            text = "You'll need this credential to unlock Drive sync on another device. It cannot be recovered if forgotten. Local notes are not deleted or re-encrypted when you set it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DriveRecoveryMethod.entries.forEach { option ->
                androidx.compose.material3.FilterChip(
                    selected = method == option,
                    enabled = !busy,
                    onClick = { method = option; pass = ""; confirm = ""; pinRiskAccepted = false },
                    label = { Text(option.label) },
                )
            }
        }
        if (pin) {
            Text("Use 4 to 10 digits. A short PIN is easier to guess than a long passphrase, especially if someone obtains a copy of the encrypted recovery key.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(checked = pinRiskAccepted, onCheckedChange = { pinRiskAccepted = it }, enabled = !busy)
                Text("I understand a PIN offers weaker protection.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = pass,
            onValueChange = { if (acceptsInput(it)) pass = it },
            label = { Text(if (pin) "Recovery PIN" else "Recovery passphrase") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { if (acceptsInput(it)) confirm = it },
            label = { Text(if (pin) "Confirm PIN" else "Confirm passphrase") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        val warning = when {
            error != null -> error
            pass.isNotEmpty() && !secretValid -> if (pin) "Use 4 to 10 digits." else "Use at least 8 characters. A longer passphrase is recommended."
            confirm.isNotEmpty() && pass != confirm -> if (pin) "PINs don't match." else "Passphrases don't match."
            else -> null
        }
        warning?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** New device / reconnect: enter the existing recovery passphrase to unlock the key. */
@Composable
internal fun EnterPassphraseDialog(
    busy: Boolean,
    error: String?,
    onConfirm: (CharArray) -> Unit,
    onDismiss: () -> Unit,
    method: DriveRecoveryMethod = DriveRecoveryMethod.PASSPHRASE,
) {
    var pass by remember { mutableStateOf("") }
    val pin = method == DriveRecoveryMethod.PIN
    val valid = if (pin) method.accepts(pass.toCharArray()) else pass.isNotEmpty()
    PassphraseScaffold(
        title = if (pin) "Enter recovery PIN" else "Enter recovery passphrase",
        busy = busy,
        onDismiss = onDismiss,
        confirmButton = { Button(onClick = { onConfirm(pass.toCharArray()) }, enabled = valid && !busy) { Text("Unlock") } },
    ) {
        Text(
            text = if (pin) "Enter the recovery PIN you chose for this Drive sync. Leading zeroes are part of the PIN."
                else "Enter your recovery passphrase to unlock your encrypted notes on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = pass,
            onValueChange = { if ((!pin && it.length <= 1024) || (pin && it.length <= 10 && it.all { digit -> digit in '0'..'9' })) pass = it },
            label = { Text(if (pin) "Recovery PIN" else "Passphrase") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = if (pin) KeyboardType.NumberPassword else KeyboardType.Password),
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun DriveRestartDialog(onConfirm: () -> Unit, onCheckAgain: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("MyNotes folder not found") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Deleting the MyNotes folder does not remove its recovery key from Drive's hidden app data.")
                Text("Start over creates a new encrypted sync from the notes, templates and reminders saved on this device. Your local notes stay unchanged. Content that existed only in the deleted folder will not be recovered.")
                Text("Choose a new passphrase or PIN and reconnect other devices afterward. Old cloud recovery data is kept separate.")
                Text("To keep the previous sync instead, restore the MyNotes folder from Drive Trash, then check again.")
                TextButton(onClick = onCheckAgain) { Text("Check again") }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Start over") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}
