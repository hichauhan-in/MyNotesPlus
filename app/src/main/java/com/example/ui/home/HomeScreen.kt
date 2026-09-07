package com.example.ui.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import com.example.data.attachments.EncAttachment
import com.example.data.export.ExportFormat
import com.example.data.export.ExportIO
import com.example.data.export.Exporter
import com.example.data.export.ShareIO
import com.example.data.share.NoteSharing
import com.example.data.sync.DriveAuth
import com.example.data.sync.DriveShare
import com.example.data.sync.SyncStatus
import com.google.android.gms.auth.api.identity.Identity
import com.example.domain.model.CustomTemplate
import com.example.domain.model.Folder
import com.example.domain.model.Note
import com.example.ui.share.ImportPassphraseDialog
import com.example.ui.share.SharePassphraseDialog
import com.example.ui.components.BrandGradientButton
import com.example.ui.components.EmptyState
import com.example.ui.components.NeuCard
import com.example.ui.components.NeuIconButton
import com.example.ui.components.NeuSurface
import com.example.ui.components.PillChip
import com.example.ui.components.SectionHeader
import com.example.ui.components.TagPill
import com.example.ui.theme.LocalNeuColors
import com.example.ui.theme.brandGradientHorizontal
import com.example.ui.theme.neumorphicRaised
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNoteClick: (String) -> Unit,
    onCreateNote: (template: String?, folderId: String?) -> Unit,
    onEditTemplate: (templateId: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenReminders: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.filter.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()

    val selectionMode = selectedIds.isNotEmpty()
    val retentionDays by viewModel.trashRetentionDays.collectAsStateWithLifecycle()
    val customTemplates by viewModel.customTemplates.collectAsStateWithLifecycle()
    val trashedTemplates by viewModel.trashedTemplates.collectAsStateWithLifecycle()
    val selectedBookIds by viewModel.selectedBookIds.collectAsStateWithLifecycle()
    val bookSelectionMode = selectedBookIds.isNotEmpty()
    val hasTrashedTemplates = trashedTemplates.isNotEmpty()
    val defaultExportFolder by viewModel.defaultExportFolder.collectAsStateWithLifecycle()
    val allNotesForExport by viewModel.notesForExport.collectAsStateWithLifecycle()
    val showSyncPrompt by viewModel.showSyncPrompt.collectAsStateWithLifecycle()
    val syncEnabled by viewModel.syncEnabled.collectAsStateWithLifecycle()
    val driveConnected by viewModel.driveConnected.collectAsStateWithLifecycle()
    val swipeNavEnabled by viewModel.swipeNavigationEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fabExpanded by remember { mutableStateOf(false) }
    var templatesExpanded by remember { mutableStateOf(false) }
    var actionNote by remember { mutableStateOf<Note?>(null) }
    var showCoffeeSheet by remember { mutableStateOf(false) }
    var showRetentionSheet by remember { mutableStateOf(false) }
    var showTemplateCreator by remember { mutableStateOf(false) }
    var showBookCreator by remember { mutableStateOf(false) }
    var showMoveSelection by remember { mutableStateOf(false) }
    var moveTargetNote by remember { mutableStateOf<Note?>(null) }
    var bookActionsFor by remember { mutableStateOf<Folder?>(null) }
    var renameBookFor by remember { mutableStateOf<Folder?>(null) }
    var moveBookFor by remember { mutableStateOf<Folder?>(null) }
    var deleteBookFor by remember { mutableStateOf<Folder?>(null) }
    var deleteBookForeverFor by remember { mutableStateOf<Folder?>(null) }
    var showMoveBookSelection by remember { mutableStateOf(false) }
    var showDeleteBooksForeverDialog by remember { mutableStateOf(false) }
    var exportBookFor by remember { mutableStateOf<Folder?>(null) }
    var pendingBookExport by remember { mutableStateOf<Pair<String, ExportFormat>?>(null) }
    var noteShareFor by remember { mutableStateOf<Note?>(null) }
    var noteEncryptFor by remember { mutableStateOf<Note?>(null) }
    var importUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var importWrong by remember { mutableStateOf(false) }
    var importBusy by remember { mutableStateOf(false) }
    var shareBookFor by remember { mutableStateOf<Folder?>(null) }

    val allFolders by viewModel.allFolders.collectAsStateWithLifecycle()
    val foldersById = remember(allFolders) { allFolders.associateBy { it.id } }
    val activeFolders = remember(allFolders) { allFolders.filter { !it.isTrashed } }

    val insets = WindowInsets.systemBars.asPaddingValues()
    val visibleIds = remember(state.pinned, state.notes) {
        state.pinned.map { it.id } + state.notes.map { it.id }
    }

    // Back exits selection, then steps out of a book, before leaving the screen.
    BackHandler(enabled = selectionMode) { viewModel.clearSelection() }
    BackHandler(enabled = bookSelectionMode) { viewModel.clearBookSelection() }
    BackHandler(enabled = !selectionMode && !bookSelectionMode && state.currentBook != null) { viewModel.goUp() }

    val exportBookLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        val pending = pendingBookExport
        pendingBookExport = null
        if (uri != null && pending != null) {
            val (rootId, format) = pending
            val folders = allFolders
            val notes = allNotesForExport
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    ExportIO.writeStream(context, uri) { out ->
                        Exporter.writeBookZip(context, rootId, folders, notes, format, out)
                    }
                }
                android.widget.Toast.makeText(
                    context,
                    if (ok) "Book exported" else "Couldn't export",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun exportBook(book: Folder, format: ExportFormat) {
        val fileName = "${Exporter.safe(book.name)}.zip"
        val folders = allFolders
        val notes = allNotesForExport
        val folder = defaultExportFolder
        when {
            // 1) A folder the user explicitly picked in Settings wins.
            folder != null -> {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        val target = ExportIO.createInTree(context, folder, fileName, "application/zip")
                        target != null && ExportIO.writeStream(context, target) { out ->
                            Exporter.writeBookZip(context, book.id, folders, notes, format, out)
                        }
                    }
                    if (ok) {
                        android.widget.Toast.makeText(context, "Book exported to your folder", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        pendingBookExport = book.id to format
                        runCatching { exportBookLauncher.launch(fileName) }
                    }
                }
            }
            // 2) Default: drop it straight into Downloads/MyNotes (no permission on Android 10+).
            ExportIO.supportsDownloadsExport -> {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        ExportIO.writeToDownloads(context, fileName, "application/zip") { out ->
                            Exporter.writeBookZip(context, book.id, folders, notes, format, out)
                        } != null
                    }
                    if (ok) {
                        android.widget.Toast.makeText(context, "Book saved to Downloads/MyNotes", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        pendingBookExport = book.id to format
                        runCatching { exportBookLauncher.launch(fileName) }
                    }
                }
            }
            // 3) Older devices without scoped storage: ask where to save.
            else -> {
                pendingBookExport = book.id to format
                runCatching { exportBookLauncher.launch(fileName) }
            }
        }
    }

    // Share a note's plain text straight to another app.
    fun shareNoteText(note: Note) {
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                String(Exporter.noteBytes(note, ExportFormat.TXT), Charsets.UTF_8)
            }
            ShareIO.shareText(context, note.title.ifBlank { "Note" }, text)
        }
    }

    // Share a note as a single file (PDF / Markdown) via the system share sheet.
    fun shareNoteFile(note: Note, format: ExportFormat) {
        val fileName = "${Exporter.noteFileBase(note)}.${format.ext}"
        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                ShareIO.writeShareFile(context, fileName, Exporter.noteBytes(note, format))
            }
            if (uri != null) ShareIO.shareFile(context, uri, format.mime, note.title)
            else android.widget.Toast.makeText(context, "Couldn't share", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Share a whole book as a ZIP (folder tree + notes + attachments) via the system share sheet.
    fun shareBook(book: Folder, format: ExportFormat) {
        val fileName = "${Exporter.safe(book.name)}.zip"
        val folders = allFolders
        val notes = allNotesForExport
        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                ShareIO.writeShareStream(context, fileName) { out ->
                    Exporter.writeBookZip(context, book.id, folders, notes, format, out)
                }
            }
            if (uri != null) ShareIO.shareFile(context, uri, "application/zip", book.name)
            else android.widget.Toast.makeText(context, "Couldn't share", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Upload a readable copy of a note to Drive and share an "anyone with the link" URL.
    fun shareNoteDriveLink(note: Note) {
        android.widget.Toast.makeText(context, "Creating share link…", android.widget.Toast.LENGTH_SHORT).show()
        Identity.getAuthorizationClient(context)
            .authorize(DriveAuth.request())
            .addOnSuccessListener { result ->
                val token = result.accessToken
                if (!result.hasResolution() && token != null) {
                    scope.launch {
                        val link = DriveShare.createLink(token, note)
                        if (link != null) ShareIO.shareText(context, note.title.ifBlank { "Note" }, link)
                        else android.widget.Toast.makeText(context, "Couldn't create link", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    android.widget.Toast.makeText(context, "Connect Google Drive in Settings first", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                android.widget.Toast.makeText(context, "Couldn't reach Google Drive", android.widget.Toast.LENGTH_SHORT).show()
            }
    }

    // Pack a note (+ attachments) into an encrypted .mynote file locked by [passphrase] and share it.
    fun shareNoteEncrypted(note: Note, passphrase: CharArray) {
        val fileName = "${Exporter.noteFileBase(note)}.${NoteSharing.FILE_EXTENSION}"
        android.widget.Toast.makeText(context, "Encrypting…", android.widget.Toast.LENGTH_SHORT).show()
        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                val bytes = NoteSharing.exportEncrypted(context, note, passphrase) ?: return@withContext null
                ShareIO.writeShareFile(context, fileName, bytes)
            }
            if (uri != null) ShareIO.shareFile(context, uri, NoteSharing.MIME, note.title.ifBlank { "Note" })
            else android.widget.Toast.makeText(context, "Couldn't create share file", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Import: pick any file, then the passphrase dialog decrypts it into a new note.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importWrong = false
            importUri = uri
        }
    }
    fun startImport() {
        runCatching { importLauncher.launch(arrayOf("*/*")) }
    }
    fun runImport(passphrase: CharArray) {
        val uri = importUri ?: return
        importBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                if (bytes == null) NoteSharing.ImportResult.Invalid
                else NoteSharing.importEncrypted(context, bytes, passphrase)
            }
            importBusy = false
            when (result) {
                is NoteSharing.ImportResult.Success -> {
                    viewModel.saveImportedNote(result.note)
                    importUri = null
                    importWrong = false
                    android.widget.Toast.makeText(context, "Note imported", android.widget.Toast.LENGTH_SHORT).show()
                }
                NoteSharing.ImportResult.WrongPassphrase -> importWrong = true
                NoteSharing.ImportResult.Invalid -> {
                    importUri = null
                    importWrong = false
                    android.widget.Toast.makeText(context, "That file isn't a MyNotes share", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    // Shared across the empty and populated layouts so the filter row keeps its horizontal scroll
    // position when a tab switches between empty and full (e.g. selecting Trash after deleting).
    val filterScrollState = rememberScrollState()

    // Optional (Settings): a horizontal swipe on the notes area moves between filter tabs.
    val swipeModifier = if (swipeNavEnabled && !selectionMode && !bookSelectionMode) {
        Modifier.pointerInput(swipeNavEnabled) {
            var totalDx = 0f
            detectHorizontalDragGestures(
                onDragStart = { totalDx = 0f },
                onHorizontalDrag = { _, dragAmount -> totalDx += dragAmount },
                onDragEnd = {
                    val threshold = 72.dp.toPx()
                    when {
                        totalDx <= -threshold -> viewModel.cycleFilter(forward = true)
                        totalDx >= threshold -> viewModel.cycleFilter(forward = false)
                    }
                },
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (state.isEmpty && !state.loading && !selectionMode && !bookSelectionMode &&
            !(selectedFilter == NoteFilter.TRASH && hasTrashedTemplates)) {
            Box(modifier = Modifier.fillMaxSize().then(swipeModifier)) {
            EmptyHomeContent(
                insets = insets,
                noteCount = state.totalNotes,
                query = query,
                onQueryChange = viewModel::onQueryChanged,
                selectedFilter = selectedFilter,
                onFilterSelect = viewModel::onFilterChanged,
                filterScrollState = filterScrollState,
                currentBook = state.currentBook,
                onExitBook = viewModel::goUp,
                onBookOptions = { state.currentBook?.let { bookActionsFor = it } },
                onCoffee = { showCoffeeSheet = true },
                onOpenSettings = onOpenSettings,
                onSyncClick = if (syncEnabled) ({ viewModel.triggerSync(context) }) else null,
            )
            }
        } else {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(168.dp),
            modifier = Modifier.fillMaxSize().then(swipeModifier),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = insets.calculateTopPadding() + 10.dp,
                bottom = insets.calculateBottomPadding() + 128.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalItemSpacing = 14.dp,
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                val bookIds = state.books.map { it.folder.id }
                if (selectionMode) {
                    SelectionTopBar(
                        count = selectedIds.size,
                        allSelected = visibleIds.isNotEmpty() && selectedIds.size >= visibleIds.size,
                        onClose = viewModel::clearSelection,
                        onToggleSelectAll = {
                            if (selectedIds.size >= visibleIds.size) viewModel.clearSelection()
                            else viewModel.selectAll(visibleIds)
                        },
                    )
                } else if (bookSelectionMode) {
                    SelectionTopBar(
                        count = selectedBookIds.size,
                        allSelected = bookIds.isNotEmpty() && selectedBookIds.size >= bookIds.size,
                        onClose = viewModel::clearBookSelection,
                        onToggleSelectAll = {
                            if (selectedBookIds.size >= bookIds.size) viewModel.clearBookSelection()
                            else viewModel.selectAllBooks(bookIds)
                        },
                    )
                } else {
                    HomeHeader(
                        noteCount = state.totalNotes,
                        onCoffee = { showCoffeeSheet = true },
                        onOpenSettings = onOpenSettings,
                        onSyncClick = if (syncEnabled) ({ viewModel.triggerSync(context) }) else null,
                    )
                }
            }
            item(span = StaggeredGridItemSpan.FullLine) {
                Column {
                    Spacer(Modifier.height(18.dp))
                    SearchField(
                        value = query,
                        onValueChange = viewModel::onQueryChanged,
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
            item(span = StaggeredGridItemSpan.FullLine) {
                Column {
                    FilterChipsRow(
                        selected = selectedFilter,
                        onSelect = viewModel::onFilterChanged,
                        scrollState = filterScrollState,
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }

            val currentBook = state.currentBook

            // In Trash, the retention + empty-trash controls come first (right under the tabs),
            // then the deleted books, then the loose notes.
            if (selectedFilter == NoteFilter.TRASH && currentBook == null && (!state.isEmpty || hasTrashedTemplates)) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Column {
                        TrashBanner(
                            retentionDays = retentionDays,
                            onChangeRetention = { showRetentionSheet = true },
                        )
                        Spacer(Modifier.height(12.dp))
                        EmptyTrashButton(onClick = viewModel::emptyTrash)
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }

            if (currentBook != null) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Column {
                        BookHeader(
                            book = currentBook,
                            onBack = viewModel::goUp,
                            onOptions = { bookActionsFor = currentBook },
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }

            if (state.books.isNotEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Column {
                        SectionHeader(
                            title = if (selectedFilter == NoteFilter.TRASH) "Deleted books" else "Books",
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
                items(state.books, key = { "book_${it.folder.id}" }) { book ->
                    BookCard(
                        book = book,
                        selected = book.folder.id in selectedBookIds,
                        selectionMode = bookSelectionMode,
                        onOpen = { viewModel.openBook(book.folder.id) },
                        onToggleSelect = { viewModel.toggleBookSelection(book.folder.id) },
                        onOptions = { bookActionsFor = book.folder },
                    )
                }
                item(span = StaggeredGridItemSpan.FullLine) {
                    Spacer(Modifier.height(20.dp))
                }
            }

            // Deleted templates get their own Trash section, recoverable within the retention window.
            if (selectedFilter == NoteFilter.TRASH && currentBook == null && hasTrashedTemplates) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Column {
                        SectionHeader(title = "Deleted templates")
                        Spacer(Modifier.height(12.dp))
                    }
                }
                trashedTemplates.forEach { template ->
                    item(span = StaggeredGridItemSpan.FullLine, key = "tpl_${template.id}") {
                        TrashedTemplateRow(
                            template = template,
                            onRestore = { viewModel.restoreTemplate(template.id) },
                            onDeleteForever = { viewModel.deleteTemplateForever(template.id) },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
                item(span = StaggeredGridItemSpan.FullLine) {
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (state.pinned.isNotEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Column {
                        SectionHeader(title = "Pinned")
                        Spacer(Modifier.height(12.dp))
                    }
                }
                items(state.pinned, key = { "pin_${it.id}" }) { note ->
                    NoteCard(
                        note = note,
                        folderName = foldersById[note.folderId]?.name,
                        selected = note.id in selectedIds,
                        selectionMode = selectionMode,
                        cloudConnected = syncEnabled,
                        onOpen = { onNoteClick(note.id) },
                        onToggleSelect = { viewModel.toggleSelection(note.id) },
                        onMore = { actionNote = note },
                    )
                }
                item(span = StaggeredGridItemSpan.FullLine) {
                    Spacer(Modifier.height(20.dp))
                }
            }

            if (state.notes.isNotEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Column {
                        SectionHeader(title = state.sectionTitle)
                        Spacer(Modifier.height(12.dp))
                    }
                }
                items(state.notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        folderName = foldersById[note.folderId]?.name,
                        selected = note.id in selectedIds,
                        selectionMode = selectionMode,
                        cloudConnected = syncEnabled,
                        onOpen = { onNoteClick(note.id) },
                        onToggleSelect = { viewModel.toggleSelection(note.id) },
                        onMore = { actionNote = note },
                    )
                }
            }
        }
        }

        // A gentle loader on first open, while the encrypted library is still being read/decrypted
        // in the background. It disappears the moment the notes are ready, so it never blocks.
        if (state.loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(38.dp),
                )
            }
        }

        // Dim scrim behind the expanded FAB menu. On an empty canvas there are no cards to give the
        // menu items contrast, so darken the backdrop more; with notes on screen the lighter scrim
        // already reads well and a heavier one would needlessly hide them.
        val fabScrimAlpha = if (state.isEmpty) 0.66f else 0.32f
        AnimatedVisibility(
            visible = (fabExpanded || templatesExpanded) && !selectionMode && !bookSelectionMode,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = fabScrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        fabExpanded = false
                        templatesExpanded = false
                    },
            )
        }

        if (!selectionMode && !bookSelectionMode) {
            TemplatesFab(
                expanded = templatesExpanded,
                onToggle = {
                    templatesExpanded = !templatesExpanded
                    fabExpanded = false
                },
                templates = customTemplates,
                onTemplate = { templateId ->
                    templatesExpanded = false
                    onCreateNote("custom:$templateId", viewModel.creationFolderId())
                },
                onManage = {
                    templatesExpanded = false
                    showTemplateCreator = true
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = insets.calculateBottomPadding() + 29.dp),
            )

            ExpandableFab(
                expanded = fabExpanded,
                onToggle = {
                    fabExpanded = !fabExpanded
                    templatesExpanded = false
                },
                onAction = { template ->
                    fabExpanded = false
                    onCreateNote(template, viewModel.creationFolderId())
                },
                onCreateBook = {
                    fabExpanded = false
                    showBookCreator = true
                },
                onImport = {
                    fabExpanded = false
                    startImport()
                },
                onReminder = {
                    fabExpanded = false
                    onOpenReminders()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = insets.calculateBottomPadding() + 24.dp),
            )
        }

        // Contextual bulk-action bar while multiple notes are selected.
        AnimatedVisibility(
            visible = selectionMode,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            SelectionActionBar(
                filter = selectedFilter,
                onPin = viewModel::bulkPin,
                onFavorite = viewModel::bulkFavorite,
                onArchive = viewModel::bulkArchive,
                onTrash = viewModel::bulkTrash,
                onRestore = viewModel::bulkRestore,
                onDeleteForever = viewModel::bulkDeleteForever,
                onMove = { showMoveSelection = true },
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = insets.calculateBottomPadding() + 20.dp),
            )
        }

        // Contextual bulk-action bar while multiple books are selected.
        AnimatedVisibility(
            visible = bookSelectionMode,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BookSelectionActionBar(
                inTrash = selectedFilter == NoteFilter.TRASH,
                onMove = { showMoveBookSelection = true },
                onDelete = { viewModel.bulkTrashBooks() },
                onRestore = { viewModel.bulkRestoreBooks() },
                onDeleteForever = { showDeleteBooksForeverDialog = true },
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = insets.calculateBottomPadding() + 20.dp),
            )
        }
    }

    val current = actionNote
    if (current != null) {
        NoteActionsSheet(
            note = current,
            filter = selectedFilter,
            onMove = {
                actionNote = null
                moveTargetNote = current
            },
            onShare = {
                actionNote = null
                noteShareFor = current
            },
            onDismiss = { actionNote = null },
            viewModel = viewModel,
        )
    }

    if (showCoffeeSheet) {
        BuyCoffeeSheet(onDismiss = { showCoffeeSheet = false })
    }

    if (showSyncPrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSyncPrompt() },
            title = { Text("Back up your notes?") },
            text = {
                Text(
                    "Turn on end-to-end encrypted Google Drive sync to back up your notes and open " +
                        "them on another device. Only you can read them - not even Google. You can " +
                        "always set this up later in Settings.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissSyncPrompt()
                    onOpenSettings()
                }) { Text("Set up") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSyncPrompt() }) { Text("Not now") }
            },
        )
    }

    if (showRetentionSheet) {
        RetentionPickerSheet(
            current = retentionDays,
            onSelect = {
                viewModel.setTrashRetention(it)
                showRetentionSheet = false
            },
            onDismiss = { showRetentionSheet = false },
        )
    }

    if (showTemplateCreator) {
        TemplateManagerSheet(
            templates = customTemplates,
            onNew = { onEditTemplate("new") },
            onEdit = { id -> onEditTemplate(id) },
            onDelete = { viewModel.deleteCustomTemplate(it) },
            onDismiss = { showTemplateCreator = false },
        )
    }

    if (showBookCreator) {
        BookNameDialog(
            title = "New book",
            initial = "",
            confirmLabel = "Create",
            onConfirm = { name, color ->
                viewModel.createBook(name, color)
                showBookCreator = false
            },
            onDismiss = { showBookCreator = false },
        )
    }

    renameBookFor?.let { book ->
        BookNameDialog(
            title = "Edit book",
            initial = book.name,
            initialColor = book.colorArgb,
            confirmLabel = "Save",
            onConfirm = { name, color ->
                viewModel.renameBook(book.id, name)
                viewModel.setBookColor(book.id, color)
                renameBookFor = null
            },
            onDismiss = { renameBookFor = null },
        )
    }

    if (showMoveSelection) {
        FolderPickerSheet(
            title = "Move to book",
            folders = activeFolders,
            onSelect = {
                viewModel.moveSelectedToBook(it)
                showMoveSelection = false
            },
            onDismiss = { showMoveSelection = false },
        )
    }

    moveTargetNote?.let { note ->
        FolderPickerSheet(
            title = "Move to book",
            folders = activeFolders,
            onSelect = {
                viewModel.moveNoteToBook(note.id, it)
                moveTargetNote = null
            },
            onDismiss = { moveTargetNote = null },
        )
    }

    moveBookFor?.let { book ->
        FolderPickerSheet(
            title = "Move book into",
            folders = activeFolders,
            excludeSubtreeOf = book.id,
            onSelect = {
                viewModel.moveBook(book.id, it)
                moveBookFor = null
            },
            onDismiss = { moveBookFor = null },
        )
    }

    if (showMoveBookSelection) {
        FolderPickerSheet(
            title = "Move books into",
            folders = activeFolders,
            excludeSubtrees = selectedBookIds,
            onSelect = {
                viewModel.bulkMoveBooks(it)
                showMoveBookSelection = false
            },
            onDismiss = { showMoveBookSelection = false },
        )
    }

    if (showDeleteBooksForeverDialog) {
        DeleteBooksForeverDialog(
            count = selectedBookIds.size,
            onConfirm = {
                viewModel.bulkDeleteBooksForever()
                showDeleteBooksForeverDialog = false
            },
            onDismiss = { showDeleteBooksForeverDialog = false },
        )
    }

    exportBookFor?.let { book ->
        BookExportSheet(
            bookName = book.name,
            onPick = { format ->
                exportBookFor = null
                exportBook(book, format)
            },
            onDismiss = { exportBookFor = null },
        )
    }

    shareBookFor?.let { book ->
        BookExportSheet(
            bookName = book.name,
            title = "Share \"${book.name}\"",
            subtitle = "Shared as a ZIP that keeps the book's folder structure and attachments. " +
                "Pick the format for the notes inside.",
            onPick = { format ->
                shareBookFor = null
                shareBook(book, format)
            },
            onDismiss = { shareBookFor = null },
        )
    }

    noteShareFor?.let { note ->
        NoteShareSheet(
            onShareText = { noteShareFor = null; shareNoteText(note) },
            onSharePdf = { noteShareFor = null; shareNoteFile(note, ExportFormat.PDF) },
            onShareMarkdown = { noteShareFor = null; shareNoteFile(note, ExportFormat.MD) },
            onShareEncrypted = { noteShareFor = null; noteEncryptFor = note },
            onShareDriveLink = if (driveConnected) ({ noteShareFor = null; shareNoteDriveLink(note) }) else null,
            onDismiss = { noteShareFor = null },
        )
    }

    noteEncryptFor?.let { note ->
        SharePassphraseDialog(
            onConfirm = { passphrase ->
                noteEncryptFor = null
                shareNoteEncrypted(note, passphrase)
            },
            onDismiss = { noteEncryptFor = null },
        )
    }

    if (importUri != null) {
        ImportPassphraseDialog(
            wrong = importWrong,
            busy = importBusy,
            onConfirm = { passphrase -> runImport(passphrase) },
            onDismiss = { if (!importBusy) { importUri = null; importWrong = false } },
        )
    }

    bookActionsFor?.let { book ->
        BookActionsSheet(
            book = book,
            inTrash = selectedFilter == NoteFilter.TRASH,
            onRename = {
                bookActionsFor = null
                renameBookFor = book
            },
            onMove = {
                bookActionsFor = null
                moveBookFor = book
            },
            onDelete = {
                bookActionsFor = null
                deleteBookFor = book
            },
            onRestore = {
                bookActionsFor = null
                viewModel.restoreBook(book.id)
            },
            onDeleteForever = {
                bookActionsFor = null
                deleteBookForeverFor = book
            },
            onExport = {
                bookActionsFor = null
                exportBookFor = book
            },
            onShare = {
                bookActionsFor = null
                shareBookFor = book
            },
            onDismiss = { bookActionsFor = null },
        )
    }

    deleteBookFor?.let { book ->
        DeleteBookDialog(
            book = book,
            summary = viewModel.bookDeletionSummary(book.id),
            onConfirm = {
                viewModel.deleteBook(book.id)
                deleteBookFor = null
            },
            onDismiss = { deleteBookFor = null },
        )
    }

    deleteBookForeverFor?.let { book ->
        DeleteBookForeverDialog(
            book = book,
            onConfirm = {
                viewModel.deleteBookForever(book.id)
                deleteBookForeverFor = null
            },
            onDismiss = { deleteBookForeverFor = null },
        )
    }
}

@Composable
private fun EmptyHomeContent(
    insets: PaddingValues,
    noteCount: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedFilter: NoteFilter,
    onFilterSelect: (NoteFilter) -> Unit,
    filterScrollState: ScrollState,
    currentBook: Folder?,
    onExitBook: () -> Unit,
    onBookOptions: () -> Unit,
    onCoffee: () -> Unit,
    onOpenSettings: () -> Unit,
    onSyncClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = insets.calculateTopPadding() + 10.dp,
                bottom = insets.calculateBottomPadding() + 24.dp,
            ),
    ) {
        HomeHeader(noteCount = noteCount, onCoffee = onCoffee, onOpenSettings = onOpenSettings, onSyncClick = onSyncClick)
        // These gaps intentionally match the populated grid's spacing (its verticalItemSpacing adds
        // ~14dp between the header/search/filter rows) so the top bar looks identical whether the
        // tab is empty or full - never "compact" on an empty tab.
        Spacer(Modifier.height(32.dp))
        SearchField(value = query, onValueChange = onQueryChange)
        Spacer(Modifier.height(30.dp))
        FilterChipsRow(selected = selectedFilter, onSelect = onFilterSelect, scrollState = filterScrollState)
        if (selectedFilter == NoteFilter.ALL && currentBook != null) {
            Spacer(Modifier.height(34.dp))
            BookHeader(book = currentBook, onBack = onExitBook, onOptions = onBookOptions)
        }
        // Centre the empty state in whatever space is left below the search + filters.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                icon = if (currentBook != null) Icons.Rounded.MenuBook else emptyIconFor(selectedFilter),
                title = if (currentBook != null) "This book is empty" else emptyTitleFor(selectedFilter, query),
                subtitle = if (currentBook != null) "Add a note or a sub-book with the + button."
                else emptySubtitleFor(selectedFilter),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HomeHeader(
    noteCount: Int,
    onCoffee: () -> Unit,
    onOpenSettings: () -> Unit,
    onSyncClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "MyNotes",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                SyncPlusBadge(onSyncClick = onSyncClick)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = if (noteCount == 0) "Encrypted & private" else "$noteCount encrypted notes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        NeuIconButton(
            icon = Icons.Rounded.Coffee,
            contentDescription = "Buy me a coffee",
            onClick = onCoffee,
        )
        Spacer(Modifier.width(10.dp))
        NeuIconButton(
            icon = Icons.Rounded.Settings,
            contentDescription = "Settings",
            onClick = onOpenSettings,
        )
    }
}

/**
 * The "+" from the MyNotes+ wordmark, repurposed as a live sync control. It's purple at rest;
 * while a sync runs it spins and gently shifts colour. Tapping it triggers a manual sync (only
 * when [onSyncClick] is provided, i.e. Drive is connected on this device).
 */
@Composable
private fun SyncPlusBadge(onSyncClick: (() -> Unit)?) {
    val syncing by SyncStatus.syncing.collectAsStateWithLifecycle()
    val purple = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.tertiary
    val transition = rememberInfiniteTransition(label = "syncPlus")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "angle",
    )
    val colorT by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "tint",
    )
    val tint = if (syncing) lerp(purple, accent, colorT) else purple
    Box(
        modifier = Modifier
            .padding(start = 2.dp)
            .size(32.dp)
            .clip(CircleShape)
            .then(if (onSyncClick != null) Modifier.clickable(onClick = onSyncClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = if (onSyncClick != null) "Sync now" else null,
            tint = tint,
            modifier = Modifier
                .size(28.dp)
                .then(if (syncing) Modifier.rotate(angle) else Modifier),
        )
    }
}

/**
 * Tiny per-note cloud state: a filled cloud-check (theme accent) when Drive sync is connected on
 * this device, or a muted cloud-off when the note lives only on-device. Deliberately understated.
 */
@Composable
private fun SyncDot(connected: Boolean) {
    Icon(
        imageVector = if (connected) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
        contentDescription = if (connected) "Synced to Drive" else "On this device only",
        tint = if (connected) {
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
        },
        modifier = Modifier.size(14.dp),
    )
    Spacer(Modifier.width(6.dp))
}

@Composable
private fun SelectionTopBar(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeuIconButton(
            icon = Icons.Rounded.Close,
            contentDescription = "Cancel selection",
            onClick = onClose,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = "$count selected",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        NeuIconButton(
            icon = Icons.Rounded.SelectAll,
            contentDescription = if (allSelected) "Deselect all" else "Select all",
            onClick = onToggleSelectAll,
            tint = if (allSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectionActionBar(
    filter: NoteFilter,
    onPin: () -> Unit,
    onFavorite: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    onMove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NeuSurface(
        cornerRadius = 26.dp,
        elevation = 10.dp,
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 6.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (filter == NoteFilter.TRASH) {
                SelectionActionItem(Icons.Rounded.Restore, "Restore", onRestore)
                SelectionActionItem(Icons.Rounded.DeleteForever, "Delete", onDeleteForever, destructive = true)
            } else {
                SelectionActionItem(Icons.Rounded.PushPin, "Pin", onPin)
                SelectionActionItem(Icons.Rounded.FavoriteBorder, "Favorite", onFavorite)
                SelectionActionItem(Icons.Rounded.Folder, "Move", onMove)
                SelectionActionItem(Icons.Rounded.Archive, "Archive", onArchive)
                SelectionActionItem(Icons.Rounded.Delete, "Trash", onTrash, destructive = true)
            }
        }
    }
}

@Composable
private fun SelectionActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
private fun BookSelectionActionBar(
    inTrash: Boolean,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NeuSurface(
        cornerRadius = 26.dp,
        elevation = 10.dp,
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 6.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (inTrash) {
                SelectionActionItem(Icons.Rounded.Restore, "Restore", onRestore)
                SelectionActionItem(Icons.Rounded.DeleteForever, "Delete", onDeleteForever, destructive = true)
            } else {
                SelectionActionItem(Icons.Rounded.Folder, "Move", onMove)
                SelectionActionItem(Icons.Rounded.Delete, "Delete", onDelete, destructive = true)
            }
        }
    }
}

@Composable
private fun TrashedTemplateRow(
    template: CustomTemplate,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    val neu = LocalNeuColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphicRaised(18.dp, neu, elevation = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = templateIcon(template.iconKey),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = template.name.ifBlank { "Untitled template" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            template.trashedAt?.let {
                Text(
                    text = "Deleted ${relativeTime(it)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onRestore),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Restore,
                contentDescription = "Restore template",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onDeleteForever),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.DeleteForever,
                contentDescription = "Delete forever",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun DeleteBooksForeverDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(if (count == 1) "Delete book forever?" else "Delete $count books forever?") },
        text = {
            Text(
                "This permanently removes the selected ${if (count == 1) "book" else "books"} and " +
                    "everything nested inside. This can't be undone.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SelectionBadge(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(10.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            )
            .border(
                width = 1.5.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val neu = LocalNeuColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphicRaised(20.dp, neu, elevation = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = "Search your notes…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AnimatedVisibility(visible = value.isNotEmpty()) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Clear",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable { onValueChange("") },
            )
        }
    }
}

@Composable
private fun FilterChipsRow(
    selected: NoteFilter,
    onSelect: (NoteFilter) -> Unit,
    scrollState: ScrollState = rememberScrollState(),
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NoteFilter.entries.forEach { filter ->
            PillChip(
                label = filter.label,
                selected = filter == selected,
                onClick = { onSelect(filter) },
                leadingIcon = filter.icon,
            )
        }
    }
}

@Composable
private fun TrashBanner(
    retentionDays: Int,
    onChangeRetention: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Auto-delete after",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (retentionDays <= 0) "Never" else "$retentionDays days",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onChangeRetention)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Deleted notes are removed permanently.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/** A deliberately distinct, full-width destructive action, kept apart from the info banner. */
@Composable
private fun EmptyTrashButton(onClick: () -> Unit) {
    val neu = LocalNeuColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphicRaised(16.dp, neu, elevation = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.error)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.DeleteForever,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onError,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Empty Trash now",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onError,
        )
    }
}

@Composable
private fun NoteCard(
    note: Note,
    folderName: String? = null,
    selected: Boolean,
    selectionMode: Boolean,
    cloudConnected: Boolean = false,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onMore: () -> Unit,
) {
    val accent = if (note.colorArgb != 0) Color(note.colorArgb) else null
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxWidth()) {
        NeuCard(
            onClick = { if (selectionMode) onToggleSelect() else onOpen() },
            onLongClick = onToggleSelect,
            cornerRadius = 22.dp,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val thumbnail = note.attachments.firstOrNull {
                it.endsWith(".jpg", ignoreCase = true) ||
                    it.endsWith(".jpeg", ignoreCase = true) ||
                    it.endsWith(".png", ignoreCase = true) ||
                    it.endsWith(".webp", ignoreCase = true)
            }
            if (thumbnail != null) {
                AsyncImage(
                    model = EncAttachment(thumbnail),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                )
            }
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                if (accent != null) {
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .fillMaxHeight()
                            .background(accent),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp),
                ) {
                    if (note.title.isNotBlank()) {
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    if (note.preview.isNotBlank()) {
                        Text(
                            text = note.preview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (note.title.isNotBlank()) 5 else 7,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (note.tags.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            note.tags.take(2).forEach { tag -> TagPill(text = "#$tag") }
                        }
                    }

                    if (note.isChecklist && note.checklistTotal > 0) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Checklist,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${note.checklistDone}/${note.checklistTotal} done",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    if (note.isExpense || note.isScribble) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when {
                                    note.isExpense -> Icons.Rounded.AccountBalanceWallet
                                    else -> Icons.Rounded.Draw
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = when {
                                    note.isExpense -> "Expenses"
                                    else -> "Board"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = relativeTime(note.updatedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            val hint = folderName ?: note.tags.firstOrNull()?.let { "#$it" }
                            if (hint != null) {
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (folderName != null) {
                                        Icon(
                                            imageVector = Icons.Rounded.MenuBook,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(11.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = hint,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        SyncDot(connected = cloudConnected)
                        if (note.isFavorite) {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = "Favorite",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        if (note.isPinned) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = "Pinned",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        if (!selectionMode) {
                            Spacer(Modifier.width(2.dp))
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onMore),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "Note options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(22.dp))
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(22.dp)),
            )
        }
        if (selectionMode) {
            SelectionBadge(selected = selected, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun ExpandableFab(
    expanded: Boolean,
    onToggle: () -> Unit,
    onAction: (template: String?) -> Unit,
    onCreateBook: () -> Unit,
    onImport: () -> Unit,
    onReminder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val neu = LocalNeuColors.current
    val rotation by animateFloatAsState(if (expanded) 45f else 0f, label = "fabRotation")

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(horizontalAlignment = Alignment.End) {
                FabAction("Import note", Icons.Rounded.Download, bordered = true, iconSlotSize = 62.dp) { onImport() }
                Spacer(Modifier.height(3.dp))
                FabAction("New book", Icons.Rounded.CreateNewFolder, bordered = true, iconSlotSize = 62.dp) { onCreateBook() }
                Spacer(Modifier.height(3.dp))
                FabAction("Reminder", Icons.Rounded.NotificationsActive, bordered = true, iconSlotSize = 62.dp) { onReminder() }
                Spacer(Modifier.height(3.dp))
                FabAction("Expenses", Icons.Rounded.AccountBalanceWallet, bordered = true, iconSlotSize = 62.dp) { onAction("expense") }
                Spacer(Modifier.height(3.dp))
                FabAction("Board", Icons.Rounded.Draw, bordered = true, iconSlotSize = 62.dp) { onAction("scribble") }
                Spacer(Modifier.height(3.dp))
                FabAction("Checklist", Icons.Rounded.Checklist, bordered = true, iconSlotSize = 62.dp) { onAction("checklist") }
                Spacer(Modifier.height(3.dp))
                FabAction("New note", Icons.Rounded.EditNote, bordered = true, iconSlotSize = 62.dp) { onAction(null) }
                Spacer(Modifier.height(12.dp))
            }
        }

        Box(
            modifier = Modifier
                .size(62.dp)
                .neumorphicRaised(31.dp, neu, elevation = 10.dp)
                .clip(CircleShape)
                .background(brandGradientHorizontal())
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Create note",
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
                    .rotate(rotation),
            )
        }
    }
}

@Composable
private fun FabAction(
    label: String,
    icon: ImageVector,
    iconStart: Boolean = false,
    bordered: Boolean = false,
    iconSlotSize: Dp = 48.dp,
    onClick: () -> Unit,
) {
    val neu = LocalNeuColors.current
    val chip: @Composable () -> Unit = {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .then(
                    if (bordered) Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                        RoundedCornerShape(10.dp),
                    ) else Modifier,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
    val iconTile: @Composable () -> Unit = {
        // The 48dp tile is centered inside a slot the WIDTH of the parent FAB button (height wraps to
        // the tile) so a spawned action's icon lines up dead-centre under the main button, while the
        // rows stay vertically compact.
        Box(
            modifier = Modifier
                .zIndex(1f)
                .width(iconSlotSize),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .then(
                        if (bordered) {
                            // A crisp brand-gradient ring instead of a soft glow: nothing extends beyond
                            // the circle, so it can never be clipped or muddled by the label chip.
                            Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.5.dp, brandGradientHorizontal(), CircleShape)
                        } else {
                            Modifier
                                .neumorphicRaised(24.dp, neu, elevation = 7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        if (iconStart) {
            iconTile()
            Spacer(Modifier.width(12.dp))
            chip()
        } else {
            chip()
            Spacer(Modifier.width(12.dp))
            iconTile()
        }
    }
}

/**
 * The smaller companion FAB on the bottom-left. It keeps user templates out of the "+" menu:
 * tapping a template opens a new note from it, and "Manage templates" opens the editor sheet.
 */
@Composable
private fun TemplatesFab(
    expanded: Boolean,
    onToggle: () -> Unit,
    templates: List<CustomTemplate>,
    onTemplate: (String) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                templates.forEach { template ->
                    FabAction(
                        template.name,
                        templateIcon(template.iconKey),
                        iconStart = true,
                        bordered = true,
                        iconSlotSize = 52.dp,
                    ) {
                        onTemplate(template.id)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                FabAction(
                    "Manage templates",
                    Icons.Rounded.Tune,
                    iconStart = true,
                    bordered = true,
                    iconSlotSize = 52.dp,
                ) { onManage() }
                Spacer(Modifier.height(16.dp))
            }
        }

        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, brandGradientHorizontal(), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Style,
                contentDescription = "Templates",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteActionsSheet(
    note: Note,
    filter: NoteFilter,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: HomeViewModel,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = note.title.ifBlank { "Untitled note" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))

            if (filter == NoteFilter.TRASH) {
                SheetAction(Icons.Rounded.Restore, "Restore") {
                    viewModel.restoreFromTrash(note); onDismiss()
                }
                SheetAction(Icons.Rounded.DeleteForever, "Delete forever", destructive = true) {
                    viewModel.deleteForever(note); onDismiss()
                }
            } else {
                SheetAction(
                    Icons.Rounded.PushPin,
                    if (note.isPinned) "Unpin" else "Pin to top",
                ) { viewModel.togglePin(note); onDismiss() }
                SheetAction(
                    if (note.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (note.isFavorite) "Remove favorite" else "Add to favorites",
                ) { viewModel.toggleFavorite(note); onDismiss() }
                SheetAction(
                    if (note.isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                    if (note.isArchived) "Unarchive" else "Archive",
                ) { viewModel.toggleArchive(note); onDismiss() }
                SheetAction(Icons.Rounded.Folder, "Move to book") { onMove() }
                SheetAction(Icons.Rounded.Share, "Share") { onShare() }
                SheetAction(Icons.Rounded.Delete, "Move to Trash", destructive = true) {
                    viewModel.moveToTrash(note); onDismiss()
                }
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuyCoffeeSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "Buy me a coffee ☕",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "If MyNotes+ makes your day a little calmer, you can support development of different utilities.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            CoffeeOption(
                icon = Icons.Rounded.CurrencyRupee,
                name = "UPI",
                subtitle = "Contribute via any UPI app",
                comingSoon = false,
                onClick = {
                    val uri = Uri.parse("upi://pay").buildUpon()
                        .appendQueryParameter("pa", "gpay-12199931519@okbizaxis")
                        .appendQueryParameter("pn", "MyNotes+")
                        .appendQueryParameter("cu", "INR")
                        .build()
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), "Support via UPI"),
                        )
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            CoffeeOption(
                icon = Icons.Rounded.LocalCafe,
                name = "Ko-fi",
                subtitle = "Support on ko-fi.com",
                comingSoon = false,
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/hichauhan")),
                        )
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            CoffeeOption(Icons.Rounded.Shop, "Playto", "Support on Playto")

            Spacer(Modifier.height(20.dp))
            Text(
                text = "A voluntary, friendly gesture, nothing more. It does not " +
                    "unlock any features, remove any limits, or change how the app works. " +
                    "MyNotes+ is completely free and always will be, and there's no " +
                    "obligation to contribute.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun CoffeeOption(
    icon: ImageVector,
    name: String,
    subtitle: String,
    comingSoon: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val neu = LocalNeuColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphicRaised(18.dp, neu, elevation = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (!comingSoon && onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (comingSoon) Modifier.alpha(0.45f) else Modifier,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(brandGradientHorizontal()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = if (comingSoon) "Coming soon" else "Open",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (comingSoon) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clip(RoundedCornerShape(50))
                .background(
                    if (comingSoon) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.primary
                )
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RetentionPickerSheet(
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val options = listOf(7, 14, 30, 60, 90, 0)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "Keep deleted notes for",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Notes in Trash are permanently deleted after this period.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            options.forEach { days ->
                val selected = days == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(days) }
                        .padding(vertical = 14.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (days <= 0) "Never (keep forever)" else "$days days",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---- Books (folders) -----------------------------------------------------------

@Composable
private fun BookCard(
    book: BookItem,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onOptions: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        NeuCard(
            onClick = { if (selectionMode) onToggleSelect() else onOpen() },
            onLongClick = onToggleSelect,
            cornerRadius = 22.dp,
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val bookColor = book.folder.colorArgb.takeIf { it != 0 }?.let { Color(it) }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (bookColor != null) Modifier.background(bookColor)
                                else Modifier.background(brandGradientHorizontal()),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MenuBook,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (!selectionMode) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onOptions),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Book options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = book.folder.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = bookSubtitle(book),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(22.dp))
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(22.dp)),
            )
        }
        if (selectionMode) {
            SelectionBadge(selected = selected, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

private fun bookSubtitle(book: BookItem): String {
    val notes = if (book.noteCount == 1) "1 note" else "${book.noteCount} notes"
    if (book.subBookCount == 0) return notes
    val sub = if (book.subBookCount == 1) "1 book" else "${book.subBookCount} books"
    return "$notes · $sub"
}

@Composable
private fun BookHeader(
    book: Folder,
    onBack: () -> Unit,
    onOptions: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Rounded.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = book.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onOptions != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOptions),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Book options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun BookNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String, Int) -> Unit,
    onDismiss: () -> Unit,
    initialColor: Int = 0,
) {
    var name by remember { mutableStateOf(initial) }
    var color by remember { mutableStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.CreateNewFolder,
                contentDescription = null,
                tint = if (color != 0) Color(color) else MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(title) },
        text = {
            Column {
                TemplateTextField(name, { name = it }, "Book name", singleLine = true)
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Colour",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    bookColorChoices.forEach { choice ->
                        BookColorSwatch(
                            colorArgb = choice,
                            selected = choice == color,
                            onClick = { color = choice },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), color) },
                enabled = name.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Colour choices for books; 0 = the default brand-gradient look. */
private val bookColorChoices = listOf(
    0,
    0xFF7E57C2.toInt(),
    0xFF5C6BC0.toInt(),
    0xFF42A5F5.toInt(),
    0xFF26A69A.toInt(),
    0xFF66BB6A.toInt(),
    0xFFFFCA28.toInt(),
    0xFFEF6C00.toInt(),
    0xFFEC407A.toInt(),
    0xFFEF5350.toInt(),
)

@Composable
private fun BookColorSwatch(colorArgb: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .border(
                width = 2.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .padding(4.dp)
            .clip(CircleShape)
            .then(
                if (colorArgb != 0) Modifier.background(Color(colorArgb))
                else Modifier.background(brandGradientHorizontal()),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private data class FolderRow(val folder: Folder, val depth: Int)

private fun flattenFolders(
    all: List<Folder>,
    excludeSubtreeOf: String?,
    excludeSubtrees: Set<String> = emptySet(),
): List<FolderRow> {
    val childrenOf = all.groupBy { it.parentId }
    val excluded = mutableSetOf<String>()
    val stack = ArrayDeque<String>()
    if (excludeSubtreeOf != null) stack.add(excludeSubtreeOf)
    excludeSubtrees.forEach { stack.add(it) }
    while (stack.isNotEmpty()) {
        val id = stack.removeLast()
        if (excluded.add(id)) {
            childrenOf[id].orEmpty().forEach { stack.add(it.id) }
        }
    }
    val result = mutableListOf<FolderRow>()
    fun visit(parentId: String?, depth: Int) {
        childrenOf[parentId].orEmpty().sortedBy { it.name.lowercase() }.forEach { folder ->
            if (folder.id in excluded) return@forEach
            result.add(FolderRow(folder, depth))
            visit(folder.id, depth + 1)
        }
    }
    visit(null, 0)
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderPickerSheet(
    title: String,
    folders: List<Folder>,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    excludeSubtreeOf: String? = null,
    excludeSubtrees: Set<String> = emptySet(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val rows = remember(folders, excludeSubtreeOf, excludeSubtrees) {
        flattenFolders(folders, excludeSubtreeOf, excludeSubtrees)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                FolderPickerRow(Icons.Rounded.Home, "Home (no book)", 0) { onSelect(null) }
                rows.forEach { row ->
                    FolderPickerRow(Icons.Rounded.Folder, row.folder.name, row.depth + 1) {
                        onSelect(row.folder.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderPickerRow(
    icon: ImageVector,
    label: String,
    depth: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width((depth * 18).dp))
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookActionsSheet(
    book: Folder,
    inTrash: Boolean,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = book.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            if (inTrash) {
                SheetAction(Icons.Rounded.Restore, "Restore book") { onRestore() }
                SheetAction(Icons.Rounded.DeleteForever, "Delete forever", destructive = true) { onDeleteForever() }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Restoring brings the book and everything inside it back. Deleting forever can't be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            } else {
                SheetAction(Icons.Rounded.Edit, "Rename") { onRename() }
                SheetAction(Icons.Rounded.Folder, "Move to book") { onMove() }
                SheetAction(Icons.Rounded.FileDownload, "Export book") { onExport() }
                SheetAction(Icons.Rounded.Share, "Share book") { onShare() }
                SheetAction(Icons.Rounded.Delete, "Delete book", destructive = true) { onDelete() }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Deleting a book moves it and everything nested inside it to Trash, where you can restore it or remove it for good.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookExportSheet(
    bookName: String,
    title: String = "Export \"$bookName\"",
    subtitle: String = "Saved as a ZIP that keeps the book's folder structure and attachments. " +
        "Pick the format for the notes inside.",
    onPick: (ExportFormat) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            SheetAction(Icons.Rounded.PictureAsPdf, "Notes as PDF") { onPick(ExportFormat.PDF) }
            SheetAction(Icons.Rounded.Description, "Notes as Markdown") { onPick(ExportFormat.MD) }
            SheetAction(Icons.Rounded.Description, "Notes as plain text") { onPick(ExportFormat.TXT) }
            SheetAction(Icons.Rounded.Code, "Notes as web pages") { onPick(ExportFormat.HTML) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteShareSheet(
    onShareText: () -> Unit,
    onSharePdf: () -> Unit,
    onShareMarkdown: () -> Unit,
    onShareEncrypted: () -> Unit,
    onDismiss: () -> Unit,
    onShareDriveLink: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "Share",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Choose how you'd like to share this note.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            SheetAction(Icons.Rounded.Share, "Share as text") { onShareText() }
            SheetAction(Icons.Rounded.PictureAsPdf, "Share as PDF") { onSharePdf() }
            SheetAction(Icons.Rounded.Description, "Share as Markdown") { onShareMarkdown() }
            SheetAction(Icons.Rounded.Lock, "Share encrypted") { onShareEncrypted() }
            if (onShareDriveLink != null) {
                SheetAction(Icons.Rounded.Link, "Share a link") { onShareDriveLink() }
            }
        }
    }
}

@Composable
private fun DeleteBookDialog(
    book: Folder,
    summary: BookDeletionSummary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val insideText = when {
        summary.subBooks > 0 && summary.notes > 0 ->
            "${plural(summary.subBooks, "sub-book")} and ${plural(summary.notes, "note")} inside it will move too."
        summary.subBooks > 0 -> "${plural(summary.subBooks, "sub-book")} inside it will move too."
        summary.notes > 0 -> "${plural(summary.notes, "note")} inside it will move too."
        else -> "This book is empty."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text("Delete \"${book.name}\"?") },
        text = {
            Text(
                text = if (summary.hasContent) {
                    "$insideText Everything moves to Trash together, keeping its structure, so you can restore or permanently delete it from there."
                } else {
                    "This book will be moved to Trash."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Move to Trash",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun DeleteBookForeverDialog(
    book: Folder,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text("Delete \"${book.name}\" forever?") },
        text = {
            Text("This book, its sub-books and all their notes will be permanently deleted. This can't be undone.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Delete forever",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun plural(count: Int, noun: String): String =
    "$count $noun" + if (count == 1) "" else "s"

private val templateIconOptions: List<Pair<String, ImageVector>> = listOf(
    "note" to Icons.Rounded.EditNote,
    "checklist" to Icons.Rounded.Checklist,
    "meeting" to Icons.Rounded.Groups,
    "work" to Icons.Rounded.Work,
    "idea" to Icons.Rounded.Lightbulb,
    "book" to Icons.Rounded.MenuBook,
    "travel" to Icons.Rounded.Flight,
    "shopping" to Icons.Rounded.ShoppingCart,
    "fitness" to Icons.Rounded.FitnessCenter,
    "finance" to Icons.Rounded.Payments,
    "calendar" to Icons.Rounded.CalendarMonth,
    "reminder" to Icons.Rounded.Alarm,
    "study" to Icons.Rounded.School,
    "code" to Icons.Rounded.Code,
    "music" to Icons.Rounded.MusicNote,
    "food" to Icons.Rounded.Restaurant,
    "favorite" to Icons.Rounded.StarBorder,
)

private fun templateIcon(key: String): ImageVector =
    templateIconOptions.firstOrNull { it.first == key }?.second ?: Icons.Rounded.EditNote

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateManagerSheet(
    templates: List<CustomTemplate>,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Animate the sheet fully down BEFORE running an action, so it glides shut at the same pace it
    // slid in instead of vanishing the instant you tap a template or the New button.
    fun dismissThen(action: () -> Unit = {}) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                onDismiss()
                action()
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Templates",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Reusable drafts. Tap one to edit it, or create a new one below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 420.dp),
            ) {
                if (templates.isEmpty()) {
                    TemplatesEmptyState(modifier = Modifier.fillMaxSize())
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        templates.forEach { template ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { dismissThen { onEdit(template.id) } }
                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = templateIcon(template.iconKey),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = template.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .clickable { onDelete(template.id) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Delete template",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            BrandGradientButton(
                text = "New template",
                icon = Icons.Rounded.Add,
                onClick = { dismissThen(onNew) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TemplatesEmptyState(modifier: Modifier = Modifier) {
    val neu = LocalNeuColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .neumorphicRaised(46.dp, neu, elevation = 10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Style,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "No templates yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Tap “New template” to draft one, then it'll appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TemplateBodyField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val neu = LocalNeuColors.current
    Box(
        modifier = modifier
            .neumorphicRaised(16.dp, neu, elevation = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun TemplateLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun TemplateTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = false,
    minHeight: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val neu = LocalNeuColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphicRaised(16.dp, neu, elevation = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .heightIn(min = minHeight)
            .padding(14.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun IconChoice(icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val neu = LocalNeuColors.current
    Box(
        modifier = Modifier
            .size(48.dp)
            .neumorphicRaised(24.dp, neu, elevation = if (selected) 7.dp else 4.dp)
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(brandGradientHorizontal())
                else Modifier.background(MaterialTheme.colorScheme.surface)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

// ---- helpers -------------------------------------------------------------------

private val monthDayFormat = SimpleDateFormat("MMM d", Locale.getDefault())

private fun relativeTime(millis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - millis
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
        diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
        diff < TimeUnit.DAYS.toMillis(2) -> "Yesterday"
        diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
        else -> monthDayFormat.format(millis)
    }
}

private fun emptyIconFor(filter: NoteFilter): ImageVector = when (filter) {
    NoteFilter.FAVORITES -> Icons.Rounded.FavoriteBorder
    NoteFilter.PINNED -> Icons.Rounded.PushPin
    NoteFilter.ARCHIVED -> Icons.Rounded.Archive
    NoteFilter.TRASH -> Icons.Rounded.Delete
    else -> Icons.Rounded.EditNote
}

private fun emptyTitleFor(filter: NoteFilter, query: String): String = when {
    query.isNotBlank() -> "No matches"
    filter == NoteFilter.FAVORITES -> "No favorites yet"
    filter == NoteFilter.PINNED -> "Nothing pinned"
    filter == NoteFilter.ARCHIVED -> "Archive is empty"
    filter == NoteFilter.TRASH -> "Trash is empty"
    else -> "Your canvas is clear"
}

private fun emptySubtitleFor(filter: NoteFilter): String = when (filter) {
    NoteFilter.FAVORITES -> "Star notes to find them fast."
    NoteFilter.PINNED -> "Pin important notes to keep them on top."
    NoteFilter.ARCHIVED -> "Archived notes are tucked away here."
    NoteFilter.TRASH -> "Deleted notes will appear here."
    else -> "Tap + to write your first encrypted note."
}
