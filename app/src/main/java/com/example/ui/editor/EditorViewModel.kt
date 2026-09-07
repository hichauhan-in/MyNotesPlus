package com.example.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.di.AppContainer
import com.example.domain.model.AttachmentMarkup
import com.example.domain.model.CustomTemplate
import com.example.domain.model.Note
import com.example.domain.model.NoteType
import com.example.domain.model.EditHistory
import com.example.data.local.NoteVersionEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

enum class SaveStatus { Idle, Editing, Saving, Saved, Error }

data class EditorUiState(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val content: String = "",
    val type: NoteType = NoteType.TEXT,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val colorArgb: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val attachments: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val folderId: String? = null,
    val saveStatus: SaveStatus = SaveStatus.Idle,
    /** Template editor: when true this screen edits a reusable template, not a note. */
    val templateMode: Boolean = false,
    /** The template being edited ("new" when creating one), used to add vs update on save. */
    val editingTemplateId: String? = null,
    /** The icon key chosen for the template. */
    val iconKey: String = "note",
    /** For existing notes: whether to open straight into edit mode (user preference). */
    val startInEditMode: Boolean = false,
    val bodyGeneration: Long = 0,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
) {
    val wordCount: Int
        get() = AttachmentMarkup.stripTokens(content).trim()
            .let { if (it.isEmpty()) 0 else it.split(Regex("\\s+")).size }

    val charCount: Int get() = content.length

    /** Reading time in minutes (~200 wpm), at least 1 when there is content. */
    val readingMinutes: Int
        get() = if (wordCount == 0) 0 else max(1, (wordCount / 200.0).roundToInt())

    /** Speaking time in minutes (~130 wpm). */
    val speakingMinutes: Int
        get() = if (wordCount == 0) 0 else max(1, (wordCount / 130.0).roundToInt())

    val hasContent: Boolean get() = title.isNotBlank() || content.isNotBlank() ||
        attachments.isNotEmpty() || tags.isNotEmpty()
}

class EditorViewModel : ViewModel() {
    private val repository = AppContainer.noteRepository!!
    private val settings = AppContainer.settingsRepository!!
    private val appScope = AppContainer.applicationScope

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()
    private val history = EditHistory<EditorUiState>()
    private val _versions = MutableStateFlow<List<NoteVersionEntity>>(emptyList())
    val versions = _versions.asStateFlow()

    private fun rememberEdit(group: String? = null) {
        if (_state.value.type != NoteType.EXPENSE) history.record(_state.value, group)
        _state.value = _state.value.copy(canUndo = history.canUndo, canRedo = history.canRedo)
    }

    fun undo() { history.undo(_state.value)?.let(::applyHistory) }
    fun redo() { history.redo(_state.value)?.let(::applyHistory) }

    private fun applyHistory(snapshot: EditorUiState) {
        dirty = true
        _state.value = snapshot.copy(bodyGeneration = _state.value.bodyGeneration + 1,
            saveStatus = SaveStatus.Editing, canUndo = history.canUndo, canRedo = history.canRedo)
        flush()
    }

    fun loadVersions() {
        flush()
        viewModelScope.launch { saveMutex.withLock { _versions.value = repository.versions(_state.value.id).first() } }
    }

    suspend fun versionPreview(id: String): Pair<String, String>? = repository.versionContent(_state.value.id, id)?.let { it.first to it.second }

    fun recoverVersion(id: String, onResult: (String) -> Unit) {
        appScope.launch(Dispatchers.Main.immediate) {
            try {
                val current = requireNotNull(repository.getNoteById(_state.value.id))
                val version = requireNotNull(repository.versionContent(current.id, id)) { "This version cannot be decrypted" }
                repository.duplicate(current.copy(title = version.first, content = version.second, attachments = version.third),
                    "${version.first.ifBlank { "Untitled" }} (Recovered)")
                onResult("Recovered copy saved to Home")
            } catch (failure: Exception) { onResult(failure.message ?: "Could not recover this version") }
        }
    }

    /** The user's saved default export folder (SAF tree Uri string), or null to ask each time. */
    val defaultExportFolder: StateFlow<String?> =
        settings.settings.map { it.defaultExportFolder }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** True when a Google Drive account is connected (enables "Share a link"). */
    val driveConnected: StateFlow<Boolean> =
        settings.settings.map { it.driveAccountEmail != null }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** How typed text shares space with full-page pen drawings (Settings › Additional configurations). */
    val pageInkTextMode: StateFlow<com.example.data.settings.PageInkTextMode> =
        settings.settings.map { it.pageInkTextMode }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.example.data.settings.PageInkTextMode.BELOW)

    /** Whether on-device smart suggestions (dates → reminders, links, etc.) are enabled. */
    val smartSuggestionsEnabled: StateFlow<Boolean> =
        settings.settings.map { it.smartSuggestionsEnabled }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private var persisted = false
    private var autoSaveJob: Job? = null
    private var loaded = false
    private val saveMutex = Mutex()
    private var saveGeneration = 0L
    private var deleting = false
    private var leaving = false

    /** True only once the user has actually changed something in this session. */
    private var dirty = false

    fun load(id: String?, template: String?, folderId: String? = null, templateId: String? = null) {
        if (loaded) return
        loaded = true
        // Template editor: draft a reusable template instead of a note.
        if (templateId != null) {
            if (templateId == "new") {
                _state.value = EditorUiState(templateMode = true, editingTemplateId = "new")
            } else {
                viewModelScope.launch {
                    val t = settings.customTemplates.first().find { it.id == templateId }
                    _state.value = if (t != null) {
                        EditorUiState(
                            templateMode = true,
                            editingTemplateId = t.id,
                            title = t.name,
                            content = t.content,
                            iconKey = t.iconKey,
                        )
                    } else {
                        EditorUiState(templateMode = true, editingTemplateId = "new")
                    }
                }
            }
            return
        }
        if (id.isNullOrBlank()) {
            if (template != null && template.startsWith("custom:")) {
                val templateId = template.removePrefix("custom:")
                viewModelScope.launch {
                    val t = settings.customTemplates.first().find { it.id == templateId }
                    _state.value = if (t != null) {
                        EditorUiState(title = t.name, content = t.content, folderId = folderId)
                    } else {
                        EditorUiState(folderId = folderId)
                    }
                }
            } else {
                _state.value = seedFor(template).copy(folderId = folderId)
            }
            return
        }
        viewModelScope.launch {
            val note = repository.getNoteById(id)
            if (note != null) {
                persisted = true
                val openEdit = settings.snapshot().openNotesInEditMode
                _state.value = EditorUiState(
                    id = note.id,
                    title = note.title,
                    content = note.content,
                    type = note.type,
                    isPinned = note.isPinned,
                    isFavorite = note.isFavorite,
                    colorArgb = note.colorArgb,
                    createdAt = note.createdAt,
                    updatedAt = note.updatedAt,
                    attachments = note.attachments,
                    tags = note.tags,
                    folderId = note.folderId,
                    saveStatus = SaveStatus.Saved,
                    startInEditMode = openEdit,
                )
            }
        }
    }

    fun onTitleChanged(value: String) {
        if (value == _state.value.title) return
        rememberEdit("title")
        dirty = true
        _state.value = _state.value.copy(title = value, saveStatus = SaveStatus.Editing)
        scheduleAutoSave()
    }

    fun onContentChanged(value: String) {
        if (value == _state.value.content) return
        rememberEdit("content")
        dirty = true
        _state.value = _state.value.copy(
            content = value,
            attachments = AttachmentMarkup.fileNames(value),
            saveStatus = SaveStatus.Editing,
        )
        scheduleAutoSave()
    }

    /** Commit a content change immediately (e.g. after inserting or removing an image). */
    fun commitContentNow(value: String) {
        if (value == _state.value.content) { flush(); return }
        rememberEdit()
        dirty = true
        _state.value = _state.value.copy(
            content = value,
            attachments = AttachmentMarkup.fileNames(value),
            saveStatus = SaveStatus.Editing,
        )
        flush()
    }

    fun togglePin() {
        dirty = true
        _state.value = _state.value.copy(isPinned = !_state.value.isPinned)
        persistNow()
    }

    fun toggleFavorite() {
        dirty = true
        _state.value = _state.value.copy(isFavorite = !_state.value.isFavorite)
        persistNow()
    }

    fun setColor(colorArgb: Int) {
        dirty = true
        _state.value = _state.value.copy(colorArgb = colorArgb)
        persistNow()
    }

    fun addTag(tag: String) {
        val clean = tag.trim().removePrefix("#").trim()
        if (clean.isEmpty()) return
        if (_state.value.tags.any { it.equals(clean, ignoreCase = true) }) return
        dirty = true
        _state.value = _state.value.copy(tags = _state.value.tags + clean)
        persistNow()
    }

    fun removeTag(tag: String) {
        dirty = true
        _state.value = _state.value.copy(tags = _state.value.tags - tag)
        persistNow()
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(700)
            persistNow()
        }
    }

    /** Save immediately (e.g. when leaving the screen). */
    fun flush() {
        autoSaveJob?.cancel()
        persistNow()
    }

    fun saveBeforeLeaving(onDone: () -> Unit) {
        if (leaving || deleting) return
        leaving = true
        flush()
        appScope.launch(Dispatchers.Main.immediate) {
            saveMutex.withLock {
                leaving = false
                if (_state.value.templateMode || !dirty) onDone()
            }
        }
    }

    private fun persistNow() {
        // Template drafts are saved explicitly via saveTemplate(), never auto-saved as notes.
        if (_state.value.templateMode || deleting) return
        // Never save a note the user hasn't actually touched (e.g. opening a template
        // and backing out, or opening an existing note and leaving unchanged).
        if (!dirty) return
        val snapshot = _state.value
        if (!snapshot.hasContent && !persisted && saveGeneration == 0L) {
            dirty = false
            _state.value = _state.value.copy(saveStatus = SaveStatus.Idle)
            return
        }
        dirty = false
        val generation = ++saveGeneration
        appScope.launch(Dispatchers.Main.immediate) {
            saveMutex.withLock {
                try {
                    if (generation == saveGeneration && !dirty) _state.value = _state.value.copy(saveStatus = SaveStatus.Saving)
                    repository.saveNote(
                        Note(
                            id = snapshot.id,
                            title = snapshot.title,
                            content = snapshot.content,
                            type = snapshot.type,
                            createdAt = snapshot.createdAt,
                            updatedAt = System.currentTimeMillis(),
                            isPinned = snapshot.isPinned,
                            isFavorite = snapshot.isFavorite,
                            colorArgb = snapshot.colorArgb,
                            attachments = snapshot.attachments,
                            tags = snapshot.tags,
                            folderId = snapshot.folderId,
                        )
                    )
                    persisted = true
                    if (generation == saveGeneration && !dirty) _state.value = _state.value.copy(saveStatus = SaveStatus.Saved)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (generation == saveGeneration) {
                        dirty = true
                        _state.value = _state.value.copy(saveStatus = SaveStatus.Error)
                    }
                }
            }
        }
    }

    /** Move the note to Trash. Returns via [onDone] once complete. */
    fun deleteToTrash(onDone: () -> Unit) {
        flush()
        deleting = true
        appScope.launch(Dispatchers.Main.immediate) {
            saveMutex.withLock {
                if (dirty) {
                    deleting = false
                    return@withLock
                }
                try {
                    if (persisted) repository.setTrashed(_state.value.id, true)
                    onDone()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    deleting = false
                    _state.value = _state.value.copy(saveStatus = SaveStatus.Error)
                }
            }
        }
    }

    // ---- Template editor ----
    fun setTemplateIcon(key: String) {
        _state.value = _state.value.copy(iconKey = key)
    }

    /** Whether the current template draft has enough to save (a non-blank name). */
    val canSaveTemplate: Boolean get() = _state.value.templateMode && _state.value.title.isNotBlank()

    /** Save the current draft as a template (add a new one, or update the one being edited). */
    fun saveTemplate(onDone: () -> Unit) {
        val s = _state.value
        if (!s.templateMode || s.title.isBlank()) {
            onDone()
            return
        }
        val name = s.title.trim()
        val icon = s.iconKey
        val content = s.content
        val editingId = s.editingTemplateId
        appScope.launch {
            if (editingId == null || editingId == "new") {
                settings.addTemplate(CustomTemplate(name = name, iconKey = icon, content = content))
            } else {
                settings.updateTemplate(CustomTemplate(id = editingId, name = name, iconKey = icon, content = content))
            }
        }
        onDone()
    }

    private fun seedFor(template: String?): EditorUiState = when (template) {
        "checklist" -> EditorUiState(
            title = "Checklist",
            type = NoteType.CHECKLIST,
            content = "[ ] \n[ ] \n[ ] ",
        )

        "expense" -> EditorUiState(
            title = "Expenses",
            type = NoteType.EXPENSE,
            content = "",
        )

        "scribble" -> EditorUiState(
            title = "",
            type = NoteType.SCRIBBLE,
            content = "",
        )

        "meeting" -> EditorUiState(
            title = "Meeting notes",
            content = buildString {
                append("Attendees:\n\n")
                append("Agenda:\n\n")
                append("Discussion:\n\n")
                append("Action items:\n- [ ] \n\n")
                append("Decisions:\n\n")
                append("Next meeting:")
            },
        )

        else -> EditorUiState()
    }
}
