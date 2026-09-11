package com.example.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.CustomTemplate
import com.example.data.security.EncryptionManager
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.settingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "mynotes_settings")

/** How typed text and full-page pen drawings share space in a note. */
enum class PageInkTextMode {
    /** Ink can cover earlier text; further typing stays in a separate area below the ink. */
    BELOW,
    /** Draw anywhere over the note; typing stays wherever the cursor is (drawings float on top). */
    FREE,
}

/** Immutable snapshot of every user preference. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val accentIndex: Int = 0,
    val cloudSyncEnabled: Boolean = false,
    /** The connected Google account email, or null when Drive sync isn't set up. */
    val driveAccountEmail: String? = null,
    /** True once a recovery passphrase + key envelope have been set up for this account. */
    val recoveryConfigured: Boolean = false,
    /** Drive file id of the visible "MyNotes" sync folder, or null if not created yet. */
    val driveFolderId: String? = null,
    /** True once the one-time "connect Google Drive?" prompt has been shown after onboarding. */
    val syncPrompted: Boolean = false,
    /** Epoch millis of the last successful sync, or 0 if never. */
    val lastSyncedAt: Long = 0L,
    val appLockEnabled: Boolean = false,
    val hideFromRecents: Boolean = false,
    /** How many days deleted notes stay in Trash before auto-deletion. 0 == keep forever. */
    val trashRetentionDays: Int = 30,
    val onboardingComplete: Boolean = false,
    /** Persisted SAF tree Uri (as string) of the default export folder, or null if unset. */
    val defaultExportFolder: String? = null,
    /** When true, opening an existing note goes straight into edit mode instead of read-only. */
    val openNotesInEditMode: Boolean = false,
    /** When true, a horizontal swipe on the home screen moves between the filter tabs. */
    val swipeNavigationEnabled: Boolean = false,
    /** How typed text shares space with full-page pen drawings in a note. */
    val pageInkTextMode: PageInkTextMode = PageInkTextMode.BELOW,
    /** When true, notes detect dates/links/etc on-device and offer one-tap smart actions. */
    val smartSuggestionsEnabled: Boolean = true,
)

/**
 * Lightweight, non-sensitive preference store backed by Jetpack DataStore.
 * (No note content or keys are ever stored here.)
 */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val ACCENT = intPreferencesKey("accent_index")
        val CLOUD = booleanPreferencesKey("cloud_sync_enabled")
        val DRIVE_EMAIL = stringPreferencesKey("drive_account_email")
        val RECOVERY_CONFIGURED = booleanPreferencesKey("recovery_configured")
        val DRIVE_FOLDER_ID = stringPreferencesKey("drive_folder_id")
        // The Data Encryption Key, wrapped by the device Keystore (never the plaintext DEK).
        val WRAPPED_DEK = stringPreferencesKey("wrapped_dek")
        // Comma-joined note ids that were present at the last successful sync (the merge base).
        val SYNCED_IDS = stringPreferencesKey("synced_note_ids")
        val SYNCED_VERSIONS = stringPreferencesKey("synced_note_versions")
        // Comma-joined reminder ids present at the last successful sync (their merge base).
        val SYNCED_REMINDER_IDS = stringPreferencesKey("synced_reminder_ids")
        val SYNC_PROMPTED = booleanPreferencesKey("sync_prompted")
        val LAST_SYNCED = androidx.datastore.preferences.core.longPreferencesKey("last_synced_at")
        val LOCK = booleanPreferencesKey("app_lock_enabled")
        val HIDE_RECENTS = booleanPreferencesKey("hide_from_recents")
        val TRASH_RETENTION = intPreferencesKey("trash_retention_days")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_complete")
        val TEMPLATES = stringPreferencesKey("custom_templates")
        val EXPORT_FOLDER = stringPreferencesKey("default_export_folder")
        val OPEN_IN_EDIT = booleanPreferencesKey("open_notes_in_edit_mode")
        val SWIPE_NAV = booleanPreferencesKey("swipe_navigation_enabled")
        val PAGE_INK_MODE = stringPreferencesKey("page_ink_text_mode")
        val SMART_SUGGESTIONS = booleanPreferencesKey("smart_suggestions_enabled")
    }

    val customTemplates: Flow<List<CustomTemplate>> = dataStore.data.map { prefs ->
        parseTemplates(prefs[Keys.TEMPLATES] ?: "[]").filter { it.trashedAt == null }
    }

    /** Templates that have been deleted and are recoverable from Trash, newest first. */
    val trashedTemplates: Flow<List<CustomTemplate>> = dataStore.data.map { prefs ->
        parseTemplates(prefs[Keys.TEMPLATES] ?: "[]")
            .filter { it.trashedAt != null }
            .sortedByDescending { it.trashedAt }
    }

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            themeMode = runCatching {
                ThemeMode.valueOf(prefs[Keys.THEME] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = prefs[Keys.DYNAMIC] ?: false,
            accentIndex = prefs[Keys.ACCENT] ?: 0,
            cloudSyncEnabled = prefs[Keys.CLOUD] ?: false,
            driveAccountEmail = prefs[Keys.DRIVE_EMAIL],
            recoveryConfigured = prefs[Keys.RECOVERY_CONFIGURED] ?: false,
            driveFolderId = prefs[Keys.DRIVE_FOLDER_ID],
            syncPrompted = prefs[Keys.SYNC_PROMPTED] ?: false,
            lastSyncedAt = prefs[Keys.LAST_SYNCED] ?: 0L,
            appLockEnabled = prefs[Keys.LOCK] ?: false,
            hideFromRecents = prefs[Keys.HIDE_RECENTS] ?: false,
            trashRetentionDays = prefs[Keys.TRASH_RETENTION] ?: 30,
            onboardingComplete = prefs[Keys.ONBOARDING_DONE] ?: false,
            defaultExportFolder = prefs[Keys.EXPORT_FOLDER],
            openNotesInEditMode = prefs[Keys.OPEN_IN_EDIT] ?: false,
            swipeNavigationEnabled = prefs[Keys.SWIPE_NAV] ?: false,
            pageInkTextMode = runCatching {
                PageInkTextMode.valueOf(prefs[Keys.PAGE_INK_MODE] ?: PageInkTextMode.BELOW.name)
            }.getOrDefault(PageInkTextMode.BELOW),
            smartSuggestionsEnabled = prefs[Keys.SMART_SUGGESTIONS] ?: true,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) =
        edit { it[Keys.THEME] = mode.name }

    suspend fun setDynamicColor(value: Boolean) =
        edit { it[Keys.DYNAMIC] = value }

    suspend fun setAccentIndex(index: Int) =
        edit { it[Keys.ACCENT] = index }

    suspend fun setCloudSyncEnabled(value: Boolean) =
        edit { it[Keys.CLOUD] = value }

    suspend fun setDriveAccountEmail(email: String?) = edit {
        if (email == null) it.remove(Keys.DRIVE_EMAIL) else it[Keys.DRIVE_EMAIL] = email
    }

    suspend fun setRecoveryConfigured(value: Boolean) =
        edit { it[Keys.RECOVERY_CONFIGURED] = value }

    suspend fun setDriveFolderId(id: String?) = edit {
        if (id == null) it.remove(Keys.DRIVE_FOLDER_ID) else it[Keys.DRIVE_FOLDER_ID] = id
    }

    /** One-shot read of the Keystore-wrapped DEK cached on this device (null if not set up here). */
    suspend fun wrappedDataKey(): String? = dataStore.data.first()[Keys.WRAPPED_DEK]

    suspend fun setWrappedDataKey(value: String?) = edit {
        if (value == null) it.remove(Keys.WRAPPED_DEK) else it[Keys.WRAPPED_DEK] = value
    }

    /** The merge-base set of note ids from the last successful sync. */
    suspend fun syncedNoteIds(): Set<String> =
        dataStore.data.first()[Keys.SYNCED_IDS]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    suspend fun setSyncedNoteIds(ids: Set<String>) = edit { it[Keys.SYNCED_IDS] = ids.joinToString(",") }

    suspend fun syncedNoteVersions(): Map<String, Long> {
        val objectValue = JSONObject(dataStore.data.first()[Keys.SYNCED_VERSIONS] ?: "{}")
        return objectValue.keys().asSequence().associateWith { objectValue.getLong(it) }
    }

    suspend fun setSyncedNoteVersions(versions: Map<String, Long>) = edit {
        it[Keys.SYNCED_VERSIONS] = JSONObject(versions).toString()
        it[Keys.SYNCED_IDS] = versions.keys.joinToString(",")
    }

    /** The merge-base set of reminder ids from the last successful sync. */
    suspend fun syncedReminderIds(): Set<String> =
        dataStore.data.first()[Keys.SYNCED_REMINDER_IDS]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    suspend fun setSyncedReminderIds(ids: Set<String>) =
        edit { it[Keys.SYNCED_REMINDER_IDS] = ids.joinToString(",") }

    /** One-shot snapshot of all settings (used by cloud sync for folder id / flags). */
    suspend fun snapshot(): AppSettings = settings.first()

    suspend fun setSyncPrompted(value: Boolean) =
        edit { it[Keys.SYNC_PROMPTED] = value }

    suspend fun setLastSyncedAt(millis: Long) =
        edit { it[Keys.LAST_SYNCED] = millis }

    suspend fun setAppLockEnabled(value: Boolean) =
        edit { it[Keys.LOCK] = value }

    suspend fun setHideFromRecents(value: Boolean) =
        edit { it[Keys.HIDE_RECENTS] = value }

    suspend fun setTrashRetentionDays(days: Int) =
        edit { it[Keys.TRASH_RETENTION] = days }

    suspend fun setOnboardingComplete(value: Boolean) =
        edit { it[Keys.ONBOARDING_DONE] = value }

    suspend fun setDefaultExportFolder(uri: String?) = edit {
        if (uri == null) it.remove(Keys.EXPORT_FOLDER) else it[Keys.EXPORT_FOLDER] = uri
    }

    suspend fun setOpenNotesInEditMode(value: Boolean) =
        edit { it[Keys.OPEN_IN_EDIT] = value }

    suspend fun setSwipeNavigationEnabled(value: Boolean) =
        edit { it[Keys.SWIPE_NAV] = value }

    suspend fun setPageInkTextMode(mode: PageInkTextMode) =
        edit { it[Keys.PAGE_INK_MODE] = mode.name }

    suspend fun setSmartSuggestionsEnabled(value: Boolean) =
        edit { it[Keys.SMART_SUGGESTIONS] = value }

    suspend fun addTemplate(template: CustomTemplate) = dataStore.edit { prefs ->
        val current = parseTemplates(prefs[Keys.TEMPLATES] ?: "[]")
        prefs[Keys.TEMPLATES] = serializeTemplates(current + template.copy(updatedAt = System.currentTimeMillis()))
    }

    internal suspend fun appendImportedTemplates(items: List<CustomTemplate>) = dataStore.edit { prefs ->
        val existing = JSONArray(prefs[Keys.TEMPLATES] ?: "[]")
        val ids = (0 until existing.length()).map { existing.getJSONObject(it).getString("id") }.toSet()
        require(items.none { it.id in ids }) { "Template already exists" }
        val added = JSONArray(serializeTemplates(items))
        for (index in 0 until added.length()) existing.put(added.getJSONObject(index))
        prefs[Keys.TEMPLATES] = existing.toString()
    }

    internal suspend fun removeImportedTemplates(ids: Set<String>) = dataStore.edit { prefs ->
        val existing = JSONArray(prefs[Keys.TEMPLATES] ?: "[]")
        val kept = JSONArray()
        for (index in 0 until existing.length()) {
            val item = existing.getJSONObject(index)
            if (item.getString("id") !in ids) kept.put(item)
        }
        prefs[Keys.TEMPLATES] = kept.toString()
    }

    suspend fun updateTemplate(template: CustomTemplate) = dataStore.edit { prefs ->
        val current = parseTemplates(prefs[Keys.TEMPLATES] ?: "[]")
        val stamped = template.copy(updatedAt = System.currentTimeMillis())
        prefs[Keys.TEMPLATES] = serializeTemplates(
            current.map { if (it.id == template.id) stamped else it },
        )
    }

    suspend fun deleteTemplate(id: String) = dataStore.edit { prefs ->
        // Soft delete: move the template into Trash so it can be recovered for the retention window.
        val current = parseTemplates(prefs[Keys.TEMPLATES] ?: "[]")
        val now = System.currentTimeMillis()
        prefs[Keys.TEMPLATES] = serializeTemplates(
            current.map {
                if (it.id == id && it.trashedAt == null) it.copy(trashedAt = now, updatedAt = now) else it
            },
        )
    }

    suspend fun restoreTemplate(id: String) = dataStore.edit { prefs ->
        val current = parseTemplates(prefs[Keys.TEMPLATES] ?: "[]")
        prefs[Keys.TEMPLATES] = serializeTemplates(
            current.map { if (it.id == id) it.copy(trashedAt = null, updatedAt = System.currentTimeMillis()) else it },
        )
    }

    suspend fun deleteTemplatePermanently(id: String) = dataStore.edit { prefs ->
        val current = parseTemplates(prefs[Keys.TEMPLATES] ?: "[]")
        prefs[Keys.TEMPLATES] = serializeTemplates(current.filterNot { it.id == id })
    }

    /** Permanently removes trashed templates deleted before [cutoff] (epoch millis). */
    suspend fun purgeTrashedTemplatesBefore(cutoff: Long) = dataStore.edit { prefs ->
        val current = parseTemplates(prefs[Keys.TEMPLATES] ?: "[]")
        prefs[Keys.TEMPLATES] = serializeTemplates(
            current.filterNot { it.trashedAt != null && it.trashedAt < cutoff },
        )
    }

    /** Permanently removes every trashed template (used by "Empty Trash"). */
    suspend fun emptyTrashedTemplates() = dataStore.edit { prefs ->
        val current = parseTemplates(prefs[Keys.TEMPLATES] ?: "[]")
        prefs[Keys.TEMPLATES] = serializeTemplates(current.filter { it.trashedAt == null })
    }

    /** One-shot read of every template (including trashed) - used by cloud sync to merge. */
    suspend fun allTemplatesForSync(): List<CustomTemplate> =
        parseTemplates(dataStore.data.first()[Keys.TEMPLATES] ?: "[]")

    internal suspend fun templateAttachmentNamesForCleanup(): Set<String>? {
        val array = JSONArray(dataStore.data.first()[Keys.TEMPLATES] ?: "[]")
        val names = mutableSetOf<String>()
        for (index in 0 until array.length()) {
            val stored = array.getJSONObject(index).optString("content")
            val content = EncryptionManager.decryptOrNull(stored) ?: return null
            names.addAll(com.example.domain.model.AttachmentMarkup.fileNames(content))
        }
        return names
    }

    /** Overwrites the whole template list (used by cloud sync after merging). */
    suspend fun replaceAllTemplates(items: List<CustomTemplate>) = dataStore.edit { prefs ->
        prefs[Keys.TEMPLATES] = serializeTemplates(items)
    }

    internal suspend fun mergeSyncedTemplates(incoming: List<CustomTemplate>) = dataStore.edit { prefs ->
        val current = parseTemplates(prefs[Keys.TEMPLATES] ?: "[]")
        val merged = (incoming + current).groupBy { it.id }.values.map { copies ->
            copies.maxBy { maxOf(it.updatedAt, it.trashedAt ?: 0L) }
        }
        prefs[Keys.TEMPLATES] = serializeTemplates(merged)
    }

    private fun parseTemplates(json: String): List<CustomTemplate> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CustomTemplate(
                id = o.optString("id"),
                name = decryptField(o.optString("name")),
                iconKey = o.optString("icon", "note"),
                content = decryptField(o.optString("content")),
                trashedAt = if (o.has("trashedAt") && !o.isNull("trashedAt")) o.optLong("trashedAt") else null,
                updatedAt = o.optLong("updatedAt", 0L),
            )
        }
    }.getOrDefault(emptyList())

    private fun serializeTemplates(items: List<CustomTemplate>): String {
        val arr = JSONArray()
        items.forEach {
            val o = JSONObject()
                .put("id", it.id)
                // A template's name and body are user content, so they're encrypted at rest just
                // like a note. The icon/id/trashed flag are harmless metadata and stay plaintext.
                .put("name", EncryptionManager.encrypt(it.name))
                .put("icon", it.iconKey)
                .put("content", EncryptionManager.encrypt(it.content))
                .put("updatedAt", it.updatedAt)
            if (it.trashedAt != null) o.put("trashedAt", it.trashedAt)
            arr.put(o)
        }
        return arr.toString()
    }

    /**
     * Decrypts a stored template field, transparently falling back to treating it as legacy
     * plaintext when it isn't encrypted yet - so templates saved before encryption keep working and
     * are re-encrypted the next time any template is written.
     */
    private fun decryptField(stored: String): String =
        if (stored.isEmpty()) "" else EncryptionManager.decryptOrNull(stored) ?: stored

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }
}
