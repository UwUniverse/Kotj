@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lopleec.kotj.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NoteAlt
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lopleec.kotj.R
import com.lopleec.kotj.model.Category
import com.lopleec.kotj.model.NoteSummary
import com.lopleec.kotj.data.NoteSort
import kotlinx.coroutines.launch
import org.uwuaosp.compose.settingslib.SettingsToolbarActionButton
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.text.Collator

@Composable
fun KotjApp(
    viewModel: NotesViewModel = viewModel(),
    onSystemUnlock: (String) -> Unit = {},
    onSystemEncrypt: (String) -> Unit = {},
    onSystemDeleteEditor: (String) -> Unit = {},
    onSystemMoveToTrash: (String) -> Unit = {},
    onSystemDeleteForever: (String) -> Unit = {},
) {
    val state = viewModel.state
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    state.message?.let { message ->
        LaunchedEffect(message) {
            snackbar.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    BackHandler(enabled = state.importPreview != null || state.editor != null || settingsVisible) {
        when {
            state.importPreview != null -> viewModel.dismissImportPreview()
            state.editor != null -> viewModel.closeEditor()
            else -> settingsVisible = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (state.importPreview != null) {
            ImportPreviewScreen(
                preview = state.importPreview,
                onBack = viewModel::dismissImportPreview,
                onSave = viewModel::saveImportPreview,
            )
        } else if (settingsVisible) {
            SettingsScreen(
                settings = state.settings,
                onUpdate = viewModel::updateSettings,
                onBack = { settingsVisible = false },
            )
        } else if (state.editor == null) {
            LibraryScreen(
                state = state,
                viewModel = viewModel,
                snackbar = snackbar,
                onOpenSettings = { settingsVisible = true },
                onSystemMoveToTrash = onSystemMoveToTrash,
                onSystemDeleteForever = onSystemDeleteForever,
            )
        } else {
            NoteEditorScreen(
                session = state.editor,
                categories = state.categories,
                onBack = viewModel::closeEditor,
                onUpdate = viewModel::updateDocument,
                onCategoryChange = viewModel::setEditorCategory,
                onEncrypt = viewModel::encryptEditor,
                useSystemUnlock = state.settings.useSystemUnlock,
                hasSystemUnlock = viewModel.shouldUseSystemUnlock(state.editor.noteId),
                onSystemEncrypt = { onSystemEncrypt(state.editor.noteId) },
                onSystemDelete = { onSystemDeleteEditor(state.editor.noteId) },
                onRemoveEncryption = viewModel::removeEncryption,
                onAddImage = viewModel::addImage,
                onReadAttachment = viewModel::readAttachment,
                onDelete = viewModel::moveEditorToTrash,
                confirmBeforeDelete = state.settings.confirmBeforeDelete,
                canUndo = state.canUndo,
                canRedo = state.canRedo,
                onUndo = viewModel::undoEditor,
                onRedo = viewModel::redoEditor,
                onTogglePinned = viewModel::toggleEditorPinned,
                snackbar = snackbar,
            )
        }
    }

    val unlockNoteId = state.unlockNoteId
    if (unlockNoteId != null && state.unlockWithSystem) {
        LaunchedEffect(unlockNoteId) {
            onSystemUnlock(unlockNoteId)
        }
    } else if (unlockNoteId != null) {
        PasswordDialog(
            title = stringResource(R.string.library_unlock_note),
            body = if (state.settings.useSystemUnlock) {
                stringResource(R.string.library_legacy_password_message)
            } else {
                stringResource(R.string.library_unlock_note_message)
            },
            confirmLabel = stringResource(R.string.library_unlock),
            onDismiss = viewModel::dismissUnlock,
            onConfirm = viewModel::unlock,
        )
    }
}

@Composable
private fun LibraryScreen(
    state: NotesUiState,
    viewModel: NotesViewModel,
    snackbar: SnackbarHostState,
    onOpenSettings: () -> Unit,
    onSystemMoveToTrash: (String) -> Unit,
    onSystemDeleteForever: (String) -> Unit,
) {
    val systemLocale = LocalConfiguration.current.locales[0]
    val locale = if (systemLocale.language == "zh" && systemLocale.country == "CN") {
        Locale.SIMPLIFIED_CHINESE
    } else {
        Locale.ENGLISH
    }
    val drawerState = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var searchVisible by rememberSaveable { mutableStateOf(state.query.isNotBlank()) }
    var categoryDialog by remember { mutableStateOf<Category?>(null) }
    var addingCategory by remember { mutableStateOf(false) }
    var deleteForever by remember { mutableStateOf<NoteSummary?>(null) }
    var moveToTrash by remember { mutableStateOf<NoteSummary?>(null) }
    var noteActions by remember { mutableStateOf<NoteSummary?>(null) }
    var renameNote by remember { mutableStateOf<NoteSummary?>(null) }
    var moveNote by remember { mutableStateOf<NoteSummary?>(null) }
    var emptyTrashConfirm by remember { mutableStateOf(false) }
    var protectedDelete by remember { mutableStateOf<ProtectedDeleteRequest?>(null) }

    val visibleNotes = remember(
        state.notes,
        state.query,
        state.selectedCategoryId,
        state.showingTrash,
        state.settings.noteSort,
        state.settings.groupNotesByDate,
        locale,
    ) {
        val query = state.query.trim().lowercase(Locale.ROOT)
        val filtered = state.notes.filter { note ->
            when {
                query.isNotEmpty() -> note.searchText.contains(query) || note.title.lowercase(Locale.ROOT).contains(query)
                state.showingTrash -> true
                state.selectedCategoryId != null -> note.categoryId == state.selectedCategoryId
                else -> true
            }
        }
        val titleCollator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        filtered.sortedWith(
            compareBy<NoteSummary> { !it.pinned }
                .thenBy {
                    if (state.settings.groupNotesByDate && !it.pinned) dateSectionRank(it.deletedAt ?: it.updatedAt)
                    else 0
                }
                .thenComparator { left, right ->
                    when (state.settings.noteSort) {
                        NoteSort.UPDATED -> (right.deletedAt ?: right.updatedAt).compareTo(left.deletedAt ?: left.updatedAt)
                        NoteSort.TITLE -> titleCollator.compare(left.title, right.title)
                    }
                },
        )
    }
    val selectedName = when {
        state.showingTrash -> stringResource(R.string.library_recently_deleted)
        state.selectedCategoryId == null -> stringResource(R.string.library_all_notes)
        else -> state.categories.firstOrNull { it.id == state.selectedCategoryId }?.localizedName()
            ?: stringResource(R.string.library_notes)
    }
    val groupedNotes = linkedMapOf<String, MutableList<NoteSummary>>()
    if (state.settings.groupNotesByDate && !state.showingTrash) {
        for (note in visibleNotes) {
            val label = if (note.pinned) {
                stringResource(R.string.library_pinned)
            } else {
                dateSectionLabel(note.updatedAt, locale)
            }
            groupedNotes.getOrPut(label) { mutableListOf() }.add(note)
        }
    } else {
        groupedNotes[""] = visibleNotes.toMutableList()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.NoteAlt, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                }
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.library_all_notes)) },
                    selected = !state.showingTrash && state.selectedCategoryId == null,
                    onClick = {
                        viewModel.selectAll()
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Outlined.SelectAll, null) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Text(
                    stringResource(R.string.library_groups),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 28.dp, top = 22.dp, bottom = 8.dp),
                )
                state.categories.forEach { category ->
                    var menuExpanded by remember { mutableStateOf(false) }
                    NavigationDrawerItem(
                        label = { Text(category.localizedName()) },
                        selected = !state.showingTrash && state.selectedCategoryId == category.id,
                        onClick = {
                            viewModel.selectCategory(category.id)
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Outlined.Folder, null) },
                        badge = {
                            Box {
                                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Outlined.MoreVert, stringResource(R.string.library_group_menu), modifier = Modifier.size(20.dp))
                                }
                                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.library_rename)) },
                                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                        onClick = {
                                            menuExpanded = false
                                            categoryDialog = category
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.library_delete_group)) },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.deleteCategory(category.id)
                                        },
                                    )
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.library_new_group)) },
                    selected = false,
                    onClick = { addingCategory = true },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.library_recently_deleted)) },
                    selected = state.showingTrash,
                    onClick = {
                        viewModel.showTrash()
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Outlined.Delete, null) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Text(selectedName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Outlined.Menu, stringResource(R.string.library_open_groups))
                            }
                        },
                        actions = {
                            if (state.showingTrash && state.notes.isNotEmpty()) {
                                TextButton(onClick = { emptyTrashConfirm = true }) { Text(stringResource(R.string.library_empty)) }
                            } else {
                                IconButton(onClick = {
                                    searchVisible = !searchVisible
                                    if (!searchVisible) viewModel.setQuery("")
                                }) { Icon(if (searchVisible) Icons.AutoMirrored.Outlined.ArrowBack else Icons.Outlined.Search, stringResource(R.string.library_global_search)) }
                            }
                            SettingsToolbarActionButton(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.library_settings),
                                onClick = onOpenSettings,
                                buttonSize = 64.dp,
                                containerSize = 48.dp,
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                    if (searchVisible && !state.showingTrash) {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = viewModel::setQuery,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            placeholder = { Text(stringResource(R.string.library_search_hint)) },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            singleLine = true,
                        )
                    }
                }
            },
            floatingActionButton = {
                if (!state.showingTrash) {
                    LargeFloatingActionButton(
                        onClick = viewModel::createNote,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.library_new_note),
                            modifier = Modifier.size(FloatingActionButtonDefaults.LargeIconSize),
                        )
                    }
                }
            },
        ) { padding ->
            when {
                state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                visibleNotes.isEmpty() -> EmptyLibrary(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    trash = state.showingTrash,
                    searching = state.query.isNotBlank(),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 112.dp),
                ) {
                    groupedNotes.forEach { (group, notes) ->
                        if (group.isNotEmpty()) {
                            item(key = "section-$group") {
                                Text(
                                    group,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 24.dp, top = 18.dp, end = 24.dp, bottom = 6.dp),
                                )
                            }
                        }
                        items(notes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                category = state.categories.firstOrNull { it.id == note.categoryId },
                                trash = state.showingTrash,
                                query = state.query,
                                onOpen = { viewModel.requestOpen(note) },
                                onMore = { noteActions = note },
                                onRestore = { viewModel.restore(note.id) },
                                onDeleteForever = {
                                    if (note.encrypted) {
                                        if (viewModel.shouldUseSystemUnlock(note.id)) {
                                            onSystemDeleteForever(note.id)
                                        } else {
                                            protectedDelete = ProtectedDeleteRequest(note, ProtectedDeleteAction.FOREVER)
                                        }
                                    } else {
                                        deleteForever = note
                                    }
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (addingCategory) {
        CategoryDialog(
            title = stringResource(R.string.library_new_group),
            initialValue = "",
            onDismiss = { addingCategory = false },
            onConfirm = {
                viewModel.addCategory(it)
                addingCategory = false
            },
        )
    }
    noteActions?.let { note ->
        ModalBottomSheet(
            onDismissRequest = { noteActions = null },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                note.title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            ListItem(
                headlineContent = { Text(if (note.pinned) stringResource(R.string.library_unpin) else stringResource(R.string.library_pin)) },
                leadingContent = { Icon(Icons.Outlined.PushPin, null) },
                modifier = Modifier.clickable {
                    viewModel.setPinned(note.id, !note.pinned)
                    noteActions = null
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.library_open)) },
                leadingContent = { Icon(Icons.Outlined.Edit, null) },
                modifier = Modifier.clickable {
                    noteActions = null
                    viewModel.requestOpen(note)
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.library_rename)) },
                supportingContent = if (note.encrypted) ({ Text(stringResource(R.string.library_open_and_unlock_first)) }) else null,
                leadingContent = { Icon(Icons.Outlined.Edit, null) },
                modifier = Modifier.clickable(enabled = !note.encrypted) {
                    noteActions = null
                    renameNote = note
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.library_move_to_group)) },
                leadingContent = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null) },
                modifier = Modifier.clickable {
                    noteActions = null
                    moveNote = note
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.library_move_to_recently_deleted), color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable {
                    noteActions = null
                    when {
                        note.encrypted && viewModel.shouldUseSystemUnlock(note.id) -> onSystemMoveToTrash(note.id)
                        note.encrypted -> protectedDelete = ProtectedDeleteRequest(note, ProtectedDeleteAction.TRASH)
                        state.settings.confirmBeforeDelete -> moveToTrash = note
                        else -> viewModel.moveNoteToTrash(note.id)
                    }
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
    renameNote?.let { note ->
        TextValueDialog(
            title = stringResource(R.string.library_rename_note),
            label = stringResource(R.string.library_title),
            initialValue = if (note.title == "无标题" || note.title == "Untitled") "" else note.title,
            onDismiss = { renameNote = null },
            onConfirm = {
                viewModel.renameNote(note.id, it)
                renameNote = null
            },
        )
    }
    moveNote?.let { note ->
        ModalBottomSheet(
            onDismissRequest = { moveNote = null },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                stringResource(R.string.library_move_to_group),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.library_no_group)) },
                leadingContent = { RadioButton(selected = note.categoryId == null, onClick = null) },
                modifier = Modifier.clickable {
                    viewModel.moveNote(note.id, null)
                    moveNote = null
                },
            )
            state.categories.forEach { category ->
                ListItem(
                    headlineContent = { Text(category.localizedName()) },
                    leadingContent = { RadioButton(selected = note.categoryId == category.id, onClick = null) },
                    modifier = Modifier.clickable {
                        viewModel.moveNote(note.id, category.id)
                        moveNote = null
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    categoryDialog?.let { category ->
        CategoryDialog(
            title = stringResource(R.string.library_rename_group),
            initialValue = category.name,
            onDismiss = { categoryDialog = null },
            onConfirm = {
                viewModel.renameCategory(category.id, it)
                categoryDialog = null
            },
        )
    }
    deleteForever?.let { note ->
        AlertDialog(
            onDismissRequest = { deleteForever = null },
            icon = { Icon(Icons.Outlined.DeleteForever, null) },
            title = { Text(stringResource(R.string.library_delete_forever_question)) },
            text = { Text(stringResource(R.string.library_cannot_be_recovered, note.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteForever(note.id, onSuccess = { deleteForever = null })
                }) { Text(stringResource(R.string.library_delete_forever)) }
            },
            dismissButton = { TextButton(onClick = { deleteForever = null }) { Text(stringResource(R.string.library_cancel)) } },
        )
    }
    moveToTrash?.let { note ->
        AlertDialog(
            onDismissRequest = { moveToTrash = null },
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text(stringResource(R.string.library_move_to_recently_deleted_question)) },
            text = { Text(stringResource(R.string.library_restore_during_retention)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.moveNoteToTrash(note.id, onSuccess = { moveToTrash = null })
                }) { Text(stringResource(R.string.library_delete)) }
            },
            dismissButton = { TextButton(onClick = { moveToTrash = null }) { Text(stringResource(R.string.library_cancel)) } },
        )
    }
    if (emptyTrashConfirm) {
        AlertDialog(
            onDismissRequest = { emptyTrashConfirm = false },
            title = { Text(stringResource(R.string.library_empty_recently_deleted_question)) },
            text = { Text(stringResource(R.string.library_empty_recently_deleted_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.emptyTrash()
                    emptyTrashConfirm = false
                }) { Text(stringResource(R.string.library_delete_all)) }
            },
            dismissButton = { TextButton(onClick = { emptyTrashConfirm = false }) { Text(stringResource(R.string.library_cancel)) } },
        )
    }
    protectedDelete?.let { request ->
        val permanent = request.action == ProtectedDeleteAction.FOREVER
        PasswordDialog(
            title = if (permanent) {
                stringResource(R.string.library_delete_encrypted_note_forever)
            } else {
                stringResource(R.string.library_delete_encrypted_note)
            },
            body = if (permanent) {
                stringResource(R.string.library_delete_encrypted_forever_message)
            } else {
                stringResource(R.string.library_delete_encrypted_message)
            },
            confirmLabel = if (permanent) stringResource(R.string.library_delete_forever) else stringResource(R.string.library_delete),
            onDismiss = { protectedDelete = null },
            onConfirm = { password ->
                if (permanent) {
                    viewModel.deleteForever(request.note.id, password) { protectedDelete = null }
                } else {
                    viewModel.moveNoteToTrash(request.note.id, password) { protectedDelete = null }
                }
            },
        )
    }
}

private enum class ProtectedDeleteAction { TRASH, FOREVER }

private data class ProtectedDeleteRequest(
    val note: NoteSummary,
    val action: ProtectedDeleteAction,
)

@Composable
private fun NoteCard(
    note: NoteSummary,
    category: Category?,
    trash: Boolean,
    onOpen: () -> Unit,
    query: String,
    onMore: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val displayTitle = when (note.title) {
        "无标题" -> stringResource(R.string.library_untitled)
        "加密备忘录" -> stringResource(R.string.library_encrypted_note)
        else -> note.title
    }
    val categoryName = category?.localizedName()
    val formattedTime = formatTime(note.deletedAt ?: note.updatedAt, locale)
    val supportingText = if (categoryName == null) {
        formattedTime
    } else {
        stringResource(R.string.library_note_metadata, formattedTime, categoryName)
    }
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !trash, onClick = onOpen),
        headlineContent = {
            Text(
                highlightedText(displayTitle, query, MaterialTheme.colorScheme.tertiaryContainer),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
        },
        supportingContent = {
            Text(
                supportingText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            if (trash) {
                Row {
                    IconButton(onClick = onRestore) {
                        Icon(Icons.Outlined.RestoreFromTrash, stringResource(R.string.library_restore))
                    }
                    IconButton(onClick = onDeleteForever) {
                        Icon(Icons.Outlined.DeleteForever, stringResource(R.string.library_delete_forever))
                    }
                }
            } else {
                IconButton(onClick = onMore) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.library_more)) }
            }
        },
    )
}

@Composable
private fun EmptyLibrary(modifier: Modifier, trash: Boolean, searching: Boolean) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            when {
                trash -> Icons.Outlined.Delete
                searching -> Icons.Outlined.Search
                else -> Icons.Outlined.FolderOpen
            },
            null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            when {
                trash -> stringResource(R.string.library_recently_deleted_empty)
                searching -> stringResource(R.string.library_no_matching_notes)
                else -> stringResource(R.string.library_no_notes_yet)
            },
            style = MaterialTheme.typography.titleLarge,
        )
        if (!trash && !searching) {
            Text(stringResource(R.string.library_start_writing_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CategoryDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Folder, null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.library_group_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank()) onConfirm(value) }, enabled = value.isNotBlank()) { Text(stringResource(R.string.library_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } },
    )
}

@Composable
private fun TextValueDialog(
    title: String,
    label: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text(stringResource(R.string.library_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } },
    )
}

@Composable
fun PasswordDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    requireConfirmation: Boolean = false,
) {
    SecureWindowEffect()
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val valid = password.length >= 4 && (!requireConfirmation || password == confirmation)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Lock, null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(body)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.library_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                if (requireConfirmation) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text(stringResource(R.string.library_confirm_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = confirmation.isNotEmpty() && password != confirmation,
                        singleLine = true,
                    )
                }
                if (password.isNotEmpty() && password.length < 4) {
                    Text(stringResource(R.string.library_password_minimum), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(password) }, enabled = valid) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } },
    )
}

@Composable
private fun formatTime(epoch: Long, locale: Locale): String = Instant.ofEpochMilli(epoch)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern(stringResource(R.string.library_note_time_pattern), locale))

private fun highlightedText(value: String, query: String, background: Color) = buildAnnotatedString {
    append(value)
    val needle = query.trim()
    if (needle.isEmpty()) return@buildAnnotatedString
    var from = 0
    while (from < value.length) {
        val start = value.indexOf(needle, from, ignoreCase = true)
        if (start < 0) break
        addStyle(SpanStyle(background = background), start, start + needle.length)
        from = start + needle.length
    }
}

private fun dateSectionRank(epoch: Long, today: LocalDate = LocalDate.now()): Int {
    val date = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate()
    val days = ChronoUnit.DAYS.between(date, today)
    if (days <= 0) return 0
    if (days == 1L) return 1
    if (days <= 7) return 2
    if (days <= 30) return 3
    val months = ChronoUnit.MONTHS.between(YearMonth.from(date), YearMonth.from(today))
    return if (months <= 6) 4 + months.toInt().coerceAtLeast(1) else 20 + (today.year - date.year).coerceAtLeast(0)
}

@Composable
private fun dateSectionLabel(epoch: Long, locale: Locale, today: LocalDate = LocalDate.now()): String {
    val date = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate()
    val days = ChronoUnit.DAYS.between(date, today)
    return when {
        days <= 0 -> stringResource(R.string.library_today)
        days == 1L -> stringResource(R.string.library_yesterday)
        days <= 7 -> stringResource(R.string.library_past_7_days)
        days <= 30 -> stringResource(R.string.library_past_30_days)
        ChronoUnit.MONTHS.between(YearMonth.from(date), YearMonth.from(today)) <= 6 -> {
            date.format(DateTimeFormatter.ofPattern(stringResource(R.string.library_month_year_pattern), locale))
        }
        else -> stringResource(R.string.library_year, date.year)
    }
}

@Composable
private fun Category.localizedName(): String = when {
    id == "personal" && name == "个人" -> stringResource(R.string.library_category_personal)
    id == "work" && name == "工作" -> stringResource(R.string.library_category_work)
    id == "ideas" && name == "灵感" -> stringResource(R.string.library_category_ideas)
    else -> name
}
