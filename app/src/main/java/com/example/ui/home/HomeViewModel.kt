package com.example.ui.home

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.di.AppContainer
import com.example.domain.model.CustomTemplate
import com.example.domain.model.Folder
import com.example.domain.model.Note
import com.example.data.sync.SyncCoordinator
import com.example.data.sync.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NoteFilter(val label: String, val icon: ImageVector) {
    ALL("All", Icons.Rounded.GridView),
    RECENT("Recent", Icons.Rounded.Schedule),
    FAVORITES("Favorites", Icons.Rounded.Favorite),
    PINNED("Pinned", Icons.Rounded.PushPin),
    ARCHIVED("Archived", Icons.Rounded.Archive),
    TRASH("Trash", Icons.Rounded.Delete),
}

/** A book (folder) plus how much it contains, for display on the home screen. */
@Immutable
data class BookItem(
    val folder: Folder,
    val noteCount: Int,
    val subBookCount: Int,
)

/** Totals shown in the "delete this book and everything in it?" confirmation. */
data class BookDeletionSummary(
    val subBooks: Int,
    val notes: Int,
) {
    val hasContent: Boolean get() = subBooks > 0 || notes > 0
}

data class HomeUiState(
    val filter: NoteFilter = NoteFilter.ALL,
    val query: String = "",
    val pinned: List<Note> = emptyList(),
    val notes: List<Note> = emptyList(),
    val books: List<BookItem> = emptyList(),
    val currentFolderId: String? = null,
    /** Path of books from the top level down to the current one (empty at the root). */
    val breadcrumb: List<Folder> = emptyList(),
    val totalNotes: Int = 0,
    val loading: Boolean = true,
) {
    val currentBook: Folder? get() = breadcrumb.lastOrNull()
    val isEmpty: Boolean get() = pinned.isEmpty() && notes.isEmpty() && books.isEmpty()
    val sectionTitle: String
        get() = when (filter) {
            NoteFilter.ALL -> if (currentBook != null) "Notes" else "All notes"
            NoteFilter.RECENT -> "Recently edited"
            NoteFilter.FAVORITES -> "Favorites"
            NoteFilter.PINNED -> "Pinned"
            NoteFilter.ARCHIVED -> "Archived"
            NoteFilter.TRASH -> "Deleted notes"
        }
}

class HomeViewModel : ViewModel() {
    private val repository = AppContainer.noteRepository!!
    private val folders = AppContainer.folderRepository!!
    private val settings = AppContainer.settingsRepository!!
    private val syncManager = AppContainer.cloudSyncManager!!

    /** True when Drive sync is set up on this device (connected + recovery key unlocked). */
    val syncEnabled: StateFlow<Boolean> = settings.settings
        .map { it.driveAccountEmail != null && it.recoveryConfigured }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** True when a Google Drive account is connected (enables "Share a link", which doesn't need the key). */
    val driveConnected: StateFlow<Boolean> = settings.settings
        .map { it.driveAccountEmail != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** True while a sync is running (drives the animated "+" on Home). */
    val syncing: StateFlow<Boolean> = SyncStatus.syncing

    /** User tapped the "+": run a sync now (no-op unless connected). */
    fun triggerSync(context: Context) {
        SyncCoordinator.manualSync(context.applicationContext, settings, syncManager, AppContainer.applicationScope)
    }

    val trashRetentionDays: StateFlow<Int> = settings.settings
        .map { it.trashRetentionDays }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 30)

    val defaultExportFolder: StateFlow<String?> = settings.settings
        .map { it.defaultExportFolder }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** When true, a horizontal swipe on the home screen switches between filter tabs. */
    val swipeNavigationEnabled: StateFlow<Boolean> = settings.settings
        .map { it.swipeNavigationEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** One-time "connect Google Drive?" prompt: shown after onboarding until acted on / not connected. */
    val showSyncPrompt: StateFlow<Boolean> = settings.settings
        .map { it.onboardingComplete && !it.syncPrompted && it.driveAccountEmail == null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Marks the sync prompt as handled so it never shows again. */
    fun dismissSyncPrompt() = viewModelScope.launch { settings.setSyncPrompted(true) }

    fun setTrashRetention(days: Int) = viewModelScope.launch {
        settings.setTrashRetentionDays(days)
    }

    val customTemplates: StateFlow<List<CustomTemplate>> = settings.customTemplates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Deleted templates recoverable from Trash. */
    val trashedTemplates: StateFlow<List<CustomTemplate>> = settings.trashedTemplates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Every book, flat - used by the "move to book" picker. */
    val allFolders: StateFlow<List<Folder>> = folders.allFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Snapshot of every note, used to summarise what a recursive book delete will remove. */
    private val allNotesSnapshot: StateFlow<List<Note>> = repository.allNotes
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Every note snapshot, exposed for book export traversal. */
    val notesForExport: StateFlow<List<Note>> get() = allNotesSnapshot

    /** Persist a note decrypted from an imported .mynote share file. */
    fun saveImportedNote(note: Note) = viewModelScope.launch {
        repository.saveNote(note)
    }

    fun addCustomTemplate(name: String, iconKey: String, content: String) = viewModelScope.launch {
        settings.addTemplate(CustomTemplate(name = name, iconKey = iconKey, content = content))
    }

    fun updateCustomTemplate(id: String, name: String, iconKey: String, content: String) =
        viewModelScope.launch {
            settings.updateTemplate(
                CustomTemplate(id = id, name = name, iconKey = iconKey, content = content),
            )
        }

    fun deleteCustomTemplate(id: String) = viewModelScope.launch {
        settings.deleteTemplate(id)
    }

    /** Recover a deleted template back into the active templates list. */
    fun restoreTemplate(id: String) = viewModelScope.launch {
        settings.restoreTemplate(id)
    }

    /** Permanently remove a trashed template. */
    fun deleteTemplateForever(id: String) = viewModelScope.launch {
        settings.deleteTemplatePermanently(id)
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(NoteFilter.ALL)
    val filter: StateFlow<NoteFilter> = _filter.asStateFlow()

    private val _currentFolderId = MutableStateFlow<String?>(null)

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _selectedBookIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedBookIds: StateFlow<Set<String>> = _selectedBookIds.asStateFlow()

    val uiState: StateFlow<HomeUiState> =
        combine(
            allNotesSnapshot,
            allFolders,
            _query,
            _filter,
            _currentFolderId,
        ) { notes, folderList, query, filter, currentFolderId ->
            buildState(notes, folderList, query, filter, currentFolderId)
        }.flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = HomeUiState(),
            )

    private fun buildState(
        all: List<Note>,
        folderList: List<Folder>,
        query: String,
        filter: NoteFilter,
        currentFolderId: String?,
    ): HomeUiState {
        val trashedFolderIds = folderList.filter { it.isTrashed }.mapTo(HashSet()) { it.id }
        // A trashed item's "trash parent" is its parent only if that parent is also trashed;
        // otherwise it sits at the root of the trash (e.g. a single note trashed on its own).
        fun trashRootOf(parentId: String?): String? = parentId?.takeIf { it in trashedFolderIds }

        // Only "All" and "Trash" browse the book hierarchy; the rest are global smart-lists.
        // While searching, they also span everything so a tag search finds them all.
        val visible = when (filter) {
            NoteFilter.ALL ->
                if (query.isBlank()) {
                    all.filter { !it.isTrashed && !it.isArchived && it.folderId == currentFolderId }
                } else {
                    all.filter { !it.isTrashed && !it.isArchived }
                }
            NoteFilter.RECENT -> all.filter { !it.isTrashed && !it.isArchived }
                .sortedByDescending { it.updatedAt }
            NoteFilter.FAVORITES -> all.filter { !it.isTrashed && it.isFavorite }
            NoteFilter.PINNED -> all.filter { !it.isTrashed && it.isPinned }
            NoteFilter.ARCHIVED -> all.filter { !it.isTrashed && it.isArchived }
            NoteFilter.TRASH ->
                if (query.isBlank()) all.filter { it.isTrashed && trashRootOf(it.folderId) == currentFolderId }
                else all.filter { it.isTrashed }
        }

        val searched = if (query.isBlank()) visible else visible.filter { note ->
            note.title.contains(query, ignoreCase = true) ||
                note.content.contains(query, ignoreCase = true) ||
                note.tags.any { it.contains(query, ignoreCase = true) }
        }

        val showPinnedSection = filter == NoteFilter.ALL && query.isBlank()
        val pinned = if (showPinnedSection) searched.filter { it.isPinned } else emptyList()
        val rest = if (showPinnedSection) searched.filter { !it.isPinned } else searched

        val browsing = (filter == NoteFilter.ALL || filter == NoteFilter.TRASH) && query.isBlank()
        val books = if (browsing) {
            val children = if (filter == NoteFilter.ALL) {
                folderList.filter { !it.isTrashed && it.parentId == currentFolderId }
            } else {
                folderList.filter { it.isTrashed && trashRootOf(it.parentId) == currentFolderId }
            }
            children.sortedBy { it.name.lowercase() }.map { folder ->
                val inTrash = filter == NoteFilter.TRASH
                BookItem(
                    folder = folder,
                    noteCount = all.count { it.isTrashed == inTrash && it.folderId == folder.id },
                    subBookCount = folderList.count { it.isTrashed == inTrash && it.parentId == folder.id },
                )
            }
        } else {
            emptyList()
        }

        val breadcrumb = when {
            !browsing -> emptyList()
            filter == NoteFilter.TRASH -> buildBreadcrumb(currentFolderId, folderList, trashScoped = true)
            else -> buildBreadcrumb(currentFolderId, folderList, trashScoped = false)
        }

        return HomeUiState(
            filter = filter,
            query = query,
            pinned = pinned,
            notes = rest,
            books = books,
            currentFolderId = if (browsing) currentFolderId else null,
            breadcrumb = breadcrumb,
            totalNotes = all.count { !it.isTrashed && !it.isArchived },
            loading = false,
        )
    }

    private fun buildBreadcrumb(
        currentId: String?,
        folderList: List<Folder>,
        trashScoped: Boolean,
    ): List<Folder> {
        if (currentId == null) return emptyList()
        val byId = folderList.associateBy { it.id }
        val path = ArrayDeque<Folder>()
        var id: String? = currentId
        val seen = mutableSetOf<String>()
        while (id != null && seen.add(id)) {
            val folder = byId[id] ?: break
            if (trashScoped && !folder.isTrashed) break
            path.addFirst(folder)
            id = folder.parentId
        }
        return path.toList()
    }

    fun onQueryChanged(value: String) { _query.value = value }

    fun onFilterChanged(value: NoteFilter) {
        _filter.value = value
        _currentFolderId.value = null
        clearSelection()
        clearBookSelection()
    }

    /** Move to the next/previous filter tab - used by the optional home swipe gesture. */
    fun cycleFilter(forward: Boolean) {
        val values = NoteFilter.entries
        val idx = values.indexOf(_filter.value).coerceAtLeast(0)
        val next = if (forward) (idx + 1) % values.size else (idx - 1 + values.size) % values.size
        onFilterChanged(values[next])
    }

    /** The book new notes should be created in, given the current context. */
    fun creationFolderId(): String? =
        if (_filter.value == NoteFilter.ALL) _currentFolderId.value else null

    // ---- Book navigation ----
    fun openBook(folderId: String) {
        // Stay in whichever list we're browsing (All or Trash); just descend into the book.
        _currentFolderId.value = folderId
        clearSelection()
        clearBookSelection()
    }

    fun exitToRoot() {
        _currentFolderId.value = null
        clearSelection()
        clearBookSelection()
    }

    fun goUp() {
        val crumb = uiState.value.breadcrumb
        _currentFolderId.value = if (crumb.size >= 2) crumb[crumb.size - 2].id else null
        clearSelection()
        clearBookSelection()
    }

    // ---- Book CRUD ----
    fun createBook(name: String, colorArgb: Int = 0) = viewModelScope.launch {
        val parent = if (_filter.value == NoteFilter.ALL) _currentFolderId.value else null
        folders.createFolder(name, parent, colorArgb)
    }

    fun renameBook(id: String, name: String) = viewModelScope.launch {
        folders.renameFolder(id, name)
    }

    /** Update a book's colour label (0 = default gradient). */
    fun setBookColor(id: String, colorArgb: Int) = viewModelScope.launch {
        folders.setFolderColor(id, colorArgb)
    }

    fun deleteBook(id: String) {
        stepOutIfInside(id)
        clearSelection()
        viewModelScope.launch { folders.deleteFolderTree(id) }
    }

    /** Restore a trashed book (and everything nested in it) from Trash. */
    fun restoreBook(id: String) {
        stepOutIfInside(id)
        clearSelection()
        viewModelScope.launch { folders.restoreFolderTree(id) }
    }

    /** Permanently delete a trashed book and everything inside it. */
    fun deleteBookForever(id: String) {
        stepOutIfInside(id)
        clearSelection()
        viewModelScope.launch { folders.deleteFolderTreePermanently(id) }
    }

    /** If we're currently viewing inside [id] (or a descendant), step back out to the root. */
    private fun stepOutIfInside(id: String) {
        val subtree = subtreeIds(id, allFolders.value)
        if (_currentFolderId.value in subtree) _currentFolderId.value = null
    }

    /** How many sub-books and notes a recursive delete of [id] would remove. */
    fun bookDeletionSummary(id: String): BookDeletionSummary {
        val subtree = subtreeIds(id, allFolders.value)
        val notes = allNotesSnapshot.value.count { !it.isTrashed && it.folderId in subtree }
        return BookDeletionSummary(subBooks = subtree.size - 1, notes = notes)
    }

    /** The id of [rootId] plus every book nested inside it, at any depth. */
    private fun subtreeIds(rootId: String, all: List<Folder>): Set<String> {
        val childrenByParent = all.groupBy { it.parentId }
        val result = linkedSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(rootId)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (result.add(current)) {
                childrenByParent[current]?.forEach { queue.add(it.id) }
            }
        }
        return result
    }

    fun moveBook(id: String, newParentId: String?) = viewModelScope.launch {
        folders.moveFolder(id, newParentId)
    }

    // ---- Moving notes into books ----
    fun moveNoteToBook(noteId: String, folderId: String?) = viewModelScope.launch {
        folders.moveNoteToFolder(noteId, folderId)
    }

    fun moveSelectedToBook(folderId: String?) {
        val ids = _selectedIds.value.toList()
        _selectedIds.value = emptySet()
        viewModelScope.launch { folders.moveNotesToFolder(ids, folderId) }
    }

    // ---- Multi-select ----
    fun toggleSelection(id: String) {
        _selectedBookIds.value = emptySet()
        val current = _selectedIds.value.toMutableSet()
        if (!current.add(id)) current.remove(id)
        _selectedIds.value = current
    }

    fun selectAll(ids: List<String>) {
        _selectedBookIds.value = emptySet()
        _selectedIds.value = ids.toSet()
    }

    fun clearSelection() { _selectedIds.value = emptySet() }

    // ---- Book multi-select ----
    fun toggleBookSelection(id: String) {
        _selectedIds.value = emptySet()
        val current = _selectedBookIds.value.toMutableSet()
        if (!current.add(id)) current.remove(id)
        _selectedBookIds.value = current
    }

    fun selectAllBooks(ids: List<String>) {
        _selectedIds.value = emptySet()
        _selectedBookIds.value = ids.toSet()
    }

    fun clearBookSelection() { _selectedBookIds.value = emptySet() }

    private fun runOnBookSelection(action: suspend (String) -> Unit) {
        val ids = _selectedBookIds.value.toList()
        _selectedBookIds.value = emptySet()
        // Step out of the current view if we're inside any of the affected books.
        val subtrees = ids.flatMap { subtreeIds(it, allFolders.value) }.toSet()
        if (_currentFolderId.value in subtrees) _currentFolderId.value = null
        viewModelScope.launch { ids.forEach { action(it) } }
    }

    fun bulkTrashBooks() = runOnBookSelection { folders.deleteFolderTree(it) }
    fun bulkRestoreBooks() = runOnBookSelection { folders.restoreFolderTree(it) }
    fun bulkDeleteBooksForever() = runOnBookSelection { folders.deleteFolderTreePermanently(it) }

    fun bulkMoveBooks(targetId: String?) {
        val ids = _selectedBookIds.value.toList()
        _selectedBookIds.value = emptySet()
        viewModelScope.launch { ids.forEach { folders.moveFolder(it, targetId) } }
    }

    private fun runOnSelection(action: suspend (String) -> Unit) {
        val ids = _selectedIds.value.toList()
        _selectedIds.value = emptySet()
        viewModelScope.launch { ids.forEach { action(it) } }
    }

    fun bulkPin() = runOnSelection { repository.setPinned(it, true) }
    fun bulkFavorite() = runOnSelection { repository.setFavorite(it, true) }
    fun bulkArchive() = runOnSelection { repository.setArchived(it, true) }
    fun bulkTrash() = runOnSelection { repository.setTrashed(it, true) }
    fun bulkRestore() = runOnSelection { repository.setTrashed(it, false) }
    fun bulkDeleteForever() = runOnSelection { repository.deletePermanently(it) }

    fun togglePin(note: Note) {
        viewModelScope.launch { repository.setPinned(note.id, !note.isPinned) }
    }

    fun toggleFavorite(note: Note) {
        viewModelScope.launch { repository.setFavorite(note.id, !note.isFavorite) }
    }

    fun toggleArchive(note: Note) {
        viewModelScope.launch { repository.setArchived(note.id, !note.isArchived) }
    }

    fun moveToTrash(note: Note) {
        viewModelScope.launch { repository.setTrashed(note.id, true) }
    }

    fun restoreFromTrash(note: Note) {
        viewModelScope.launch {
            // If the note's book was trashed too, restoring just the note would orphan it, so
            // send it back to the top level instead.
            if (note.folderId != null && allFolders.value.any { it.id == note.folderId && it.isTrashed }) {
                folders.moveNoteToFolder(note.id, null)
            }
            repository.setTrashed(note.id, false)
        }
    }

    fun deleteForever(note: Note) {
        viewModelScope.launch { repository.deletePermanently(note.id) }
    }

    fun emptyTrash() {
        _currentFolderId.value = null
        viewModelScope.launch {
            repository.emptyTrash()
            folders.emptyTrashedFolders()
            settings.emptyTrashedTemplates()
        }
    }
}
