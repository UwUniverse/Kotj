package com.lopleec.kotj.ui

import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lopleec.kotj.R
import com.lopleec.kotj.data.NotesRepository
import com.lopleec.kotj.data.AttachmentContent
import com.lopleec.kotj.data.AppSettings
import com.lopleec.kotj.data.SettingsRepository
import com.lopleec.kotj.model.Category
import com.lopleec.kotj.model.EditorSession
import com.lopleec.kotj.model.NoteDocument
import com.lopleec.kotj.model.NoteBlock
import com.lopleec.kotj.model.NoteSummary
import com.lopleec.kotj.model.ImportPreview
import com.lopleec.kotj.importer.NoteImporter
import com.lopleec.kotj.security.SystemUnlockStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NotesUiState(
    val categories: List<Category> = emptyList(),
    val notes: List<NoteSummary> = emptyList(),
    val selectedCategoryId: String? = null,
    val showingTrash: Boolean = false,
    val query: String = "",
    val editor: EditorSession? = null,
    val unlockNoteId: String? = null,
    val unlockWithSystem: Boolean = false,
    val pendingOpenQuery: String = "",
    val loading: Boolean = true,
    val message: String? = null,
    val settings: AppSettings = AppSettings(),
    val importPreview: ImportPreview? = null,
    val securityOperationInProgress: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = NotesRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val systemUnlockStore = SystemUnlockStore(application)
    private var saveJob: Job? = null
    private var autoLockJob: Job? = null
    private var createJob: Job? = null
    private var openJob: Job? = null
    private var importJob: Job? = null
    private var saveImportJob: Job? = null
    private var refreshGeneration = 0L
    private val undoHistory = ArrayDeque<NoteDocument>()
    private val redoHistory = ArrayDeque<NoteDocument>()

    var state by mutableStateOf(NotesUiState(settings = settingsRepository.load()))
        private set

    init {
        viewModelScope.launch {
            val hardeningError = withContext(Dispatchers.IO) {
                runCatching {
                    repository.hardenStorage()
                    repository.purgeExpiredTrash(state.settings.trashRetentionDays)
                        .forEach(systemUnlockStore::remove)
                    systemUnlockStore.cleanupOrphans(repository.allNoteIds())
                }.exceptionOrNull()
            }
            refresh()
            if (hardeningError != null) {
                state = state.copy(
                    message = string(R.string.secure_storage_cleanup_failed),
                )
            }
        }
    }

    fun refresh() {
        val generation = ++refreshGeneration
        viewModelScope.launch {
            val showingTrash = state.showingTrash
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.categories() to repository.listNotes(showingTrash)
                }
            }.onSuccess { result ->
                if (generation == refreshGeneration && showingTrash == state.showingTrash) {
                    state = state.copy(categories = result.first, notes = result.second, loading = false)
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (generation == refreshGeneration) {
                    state = state.copy(
                        loading = false,
                        message = string(R.string.notes_load_failed),
                    )
                }
            }
        }
    }

    fun selectAll() {
        state = state.copy(selectedCategoryId = null, showingTrash = false, query = "", loading = true)
        refresh()
    }

    fun selectCategory(id: String) {
        state = state.copy(selectedCategoryId = id, showingTrash = false, query = "", loading = true)
        refresh()
    }

    fun showTrash() {
        state = state.copy(selectedCategoryId = null, showingTrash = true, query = "", loading = true)
        refresh()
    }

    fun setQuery(query: String) {
        state = state.copy(query = query)
    }

    fun openExternalIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW || uri.scheme != ContentResolver.SCHEME_CONTENT) return
        if (importJob?.isActive == true) return
        val application = getApplication<Application>()
        val editorSnapshot = state.editor
        val previousSave = saveJob
        previousSave?.cancel()
        saveJob = null
        state = state.copy(securityOperationInProgress = true)
        importJob = viewModelScope.launch {
            previousSave?.join()
            val result = runCatching {
                val preview = withContext(Dispatchers.IO) {
                    val imported = NoteImporter.read(
                        resolver = application.contentResolver,
                        uri = uri,
                        mimeType = intent.type ?: application.contentResolver.getType(uri),
                    )
                    if (editorSnapshot != null) {
                        if (editorSnapshot.document.isMeaningfullyEmpty()) {
                            repository.deleteAny(editorSnapshot.noteId)
                        } else {
                            persistSnapshot(editorSnapshot, cleanupAttachments = true)
                        }
                    }
                    imported
                }
                preview
            }
            result.onSuccess { preview ->
                if (editorSnapshot?.document?.isMeaningfullyEmpty() == true) {
                    systemUnlockStore.remove(editorSnapshot.noteId)
                }
                resetHistory()
                state = state.copy(
                    editor = null,
                    importPreview = preview,
                    message = null,
                    securityOperationInProgress = false,
                    canUndo = false,
                    canRedo = false,
                )
            }.onFailure { error ->
                if (error is CancellationException) return@launch
                state = state.copy(
                    message = string(R.string.file_open_failed),
                    securityOperationInProgress = false,
                )
            }
        }
    }

    fun dismissImportPreview() {
        if (state.securityOperationInProgress) return
        state = state.copy(importPreview = null)
    }

    fun saveImportPreview() {
        if (saveImportJob?.isActive == true || state.securityOperationInProgress) return
        val preview = state.importPreview ?: return
        val categoryId = state.selectedCategoryId
        state = state.copy(securityOperationInProgress = true)
        saveImportJob = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val stored = repository.createBlank(categoryId)
                    runCatching {
                        repository.saveDocument(
                            noteId = stored.id,
                            categoryId = categoryId,
                            document = preview.document,
                            encrypted = false,
                            password = null,
                        )
                    }.onFailure { repository.deleteAny(stored.id) }.getOrThrow()
                }
            }.onSuccess {
                state = state.copy(
                    importPreview = null,
                    message = string(R.string.import_saved_as_note),
                    securityOperationInProgress = false,
                )
                refresh()
            }.onFailure { error ->
                if (error is CancellationException) throw error
                state = state.copy(
                    message = string(R.string.file_save_failed),
                    securityOperationInProgress = false,
                )
            }
        }
    }

    fun createNote() {
        if (createJob?.isActive == true || state.editor != null || state.securityOperationInProgress) return
        val categoryId = state.selectedCategoryId
        createJob = viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.createBlank(categoryId) } }
                .onSuccess { note ->
                    state = state.copy(
                        editor = EditorSession(
                            noteId = note.id,
                            categoryId = note.categoryId,
                            document = NoteDocument(blocks = listOf(NoteBlock())),
                            encrypted = false,
                            autoFocus = true,
                        ),
                        canUndo = false,
                        canRedo = false,
                    )
                    resetHistory()
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    state = state.copy(message = string(R.string.note_create_failed))
                }
        }
    }

    fun requestOpen(note: NoteSummary) {
        if (state.securityOperationInProgress) return
        if (note.encrypted) {
            state = state.copy(
                unlockNoteId = note.id,
                unlockWithSystem = shouldUseSystemUnlock(note.id),
                pendingOpenQuery = state.query.trim(),
            )
        } else {
            openNote(note.id, null, state.query.trim())
        }
    }

    fun unlock(password: String) {
        val id = state.unlockNoteId ?: return
        openNote(id, password, state.pendingOpenQuery)
    }

    fun dismissUnlock() {
        openJob?.cancel()
        state = state.copy(unlockNoteId = null, unlockWithSystem = false, pendingOpenQuery = "")
    }

    fun shouldUseSystemUnlock(noteId: String): Boolean =
        systemUnlockStore.hasPassword(noteId) &&
            (state.settings.useSystemUnlock || systemUnlockStore.isSystemOnly(noteId))

    private fun openNote(id: String, password: String?, initialSearchQuery: String = "") {
        openJob?.cancel()
        openJob = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val stored = requireNotNull(repository.readNote(id)) { string(R.string.note_not_found) }
                    val decoded = repository.decode(stored, password)
                    val document = if (
                        stored.encrypted && repository.hasUnprotectedImages(decoded)
                    ) {
                        repository.changeEncryption(
                            noteId = id,
                            categoryId = stored.categoryId,
                            document = decoded,
                            targetEncrypted = true,
                            password = password,
                        )
                    } else {
                        decoded
                    }
                    repository.cleanupAttachments(id, document)
                    EditorSession(
                        noteId = id,
                        categoryId = stored.categoryId,
                        document = normalizeEditorFlow(document),
                        encrypted = stored.encrypted,
                        password = password,
                        pinned = stored.pinned,
                        autoFocus = false,
                        initialSearchQuery = initialSearchQuery,
                    )
                }
            }.onSuccess { editor ->
                state = state.copy(
                    editor = editor,
                    unlockNoteId = null,
                    unlockWithSystem = false,
                    pendingOpenQuery = "",
                    message = null,
                    canUndo = false,
                    canRedo = false,
                )
                resetHistory()
            }.onFailure { error ->
                if (error is CancellationException) return@launch
                state = state.copy(message = string(R.string.note_open_failed))
            }
        }
    }

    fun updateDocument(transform: (NoteDocument) -> NoteDocument) {
        if (state.securityOperationInProgress) return
        val editor = state.editor ?: return
        val changed = transform(editor.document)
        if (changed == editor.document) return
        recordUndo(editor.document)
        state = state.copy(
            editor = editor.copy(document = changed),
            canUndo = undoHistory.isNotEmpty(),
            canRedo = false,
        )
        scheduleSave()
    }

    fun undoEditor() {
        val editor = state.editor ?: return
        val previous = undoHistory.removeLastOrNull() ?: return
        redoHistory.addLast(editor.document)
        state = state.copy(
            editor = editor.copy(document = previous),
            canUndo = undoHistory.isNotEmpty(),
            canRedo = true,
        )
        scheduleSave()
    }

    fun redoEditor() {
        val editor = state.editor ?: return
        val next = redoHistory.removeLastOrNull() ?: return
        undoHistory.addLast(editor.document)
        state = state.copy(
            editor = editor.copy(document = next),
            canUndo = true,
            canRedo = redoHistory.isNotEmpty(),
        )
        scheduleSave()
    }

    fun toggleEditorPinned() {
        val editor = state.editor ?: return
        val pinned = !editor.pinned
        state = state.copy(editor = editor.copy(pinned = pinned))
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.setPinned(editor.noteId, pinned) } }
                .onSuccess { refreshSummariesKeepingEditor() }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    val current = state.editor
                    if (current?.noteId == editor.noteId) {
                        state = state.copy(
                            editor = current.copy(pinned = editor.pinned),
                            message = string(R.string.pin_update_failed),
                        )
                    }
                }
        }
    }

    fun setPinned(id: String, pinned: Boolean) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.setPinned(id, pinned) } }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    state = state.copy(message = string(R.string.pin_update_failed))
                }
            refresh()
        }
    }

    fun setEditorCategory(categoryId: String?) {
        if (state.securityOperationInProgress) return
        val editor = state.editor ?: return
        state = state.copy(editor = editor.copy(categoryId = categoryId))
        scheduleSave()
    }

    fun encryptEditor(password: String) {
        encryptEditor(password) {}
    }

    fun encryptEditor(password: String, onComplete: (Boolean) -> Unit) {
        if (password.length < 4) {
            state = state.copy(message = string(R.string.password_too_short))
            onComplete(false)
            return
        }
        val editor = state.editor ?: run {
            onComplete(false)
            return
        }
        val previousSave = saveJob
        previousSave?.cancel()
        saveJob = null
        state = state.copy(securityOperationInProgress = true)
        viewModelScope.launch {
            previousSave?.join()
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.changeEncryption(
                        noteId = editor.noteId,
                        categoryId = editor.categoryId,
                        document = editor.document,
                        targetEncrypted = true,
                        password = password,
                    )
                }
            }.onSuccess { protectedDocument ->
                val current = state.editor
                resetHistory()
                state = state.copy(
                    editor = current?.takeIf { it.noteId == editor.noteId }?.copy(
                        document = protectedDocument,
                        encrypted = true,
                        password = password,
                    ),
                    message = string(R.string.note_encrypted),
                    securityOperationInProgress = false,
                    canUndo = false,
                    canRedo = false,
                )
                refreshSummariesKeepingEditor()
                onComplete(true)
            }.onFailure { error ->
                state = state.copy(
                    message = string(R.string.note_encrypt_failed),
                    securityOperationInProgress = false,
                )
                onComplete(false)
            }
        }
    }

    fun removeEncryption() {
        val editor = state.editor ?: return
        val previousSave = saveJob
        previousSave?.cancel()
        saveJob = null
        state = state.copy(securityOperationInProgress = true)
        viewModelScope.launch {
            previousSave?.join()
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.changeEncryption(
                        noteId = editor.noteId,
                        categoryId = editor.categoryId,
                        document = editor.document,
                        targetEncrypted = false,
                        password = editor.password,
                    )
                }
            }.onSuccess { plainDocument ->
                systemUnlockStore.remove(editor.noteId)
                val current = state.editor
                resetHistory()
                state = state.copy(
                    editor = current?.takeIf { it.noteId == editor.noteId }?.copy(
                        document = plainDocument,
                        encrypted = false,
                        password = null,
                    ),
                    message = string(R.string.password_removed),
                    securityOperationInProgress = false,
                    canUndo = false,
                    canRedo = false,
                )
                refreshSummariesKeepingEditor()
            }.onFailure { error ->
                state = state.copy(
                    message = string(R.string.password_remove_failed),
                    securityOperationInProgress = false,
                )
            }
        }
    }

    fun addImage(uri: Uri, afterBlockId: String?) {
        if (state.securityOperationInProgress) return
        val editor = state.editor ?: return
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    repository.importImage(editor.noteId, uri, editor.encrypted, editor.password)
                }
            }
            result.onSuccess { block ->
                val current = state.editor
                if (current == null || current.noteId != editor.noteId) {
                    withContext(Dispatchers.IO) { repository.discardAttachment(block) }
                    return@onSuccess
                }
                val insertionIndex = when {
                    afterBlockId == "__note_title__" -> 0
                    else -> current.document.blocks.indexOfFirst { it.id == afterBlockId }
                        .let { if (it < 0) current.document.blocks.size else it + 1 }
                }
                val document = current.document.copy(
                    blocks = current.document.blocks.toMutableList().apply {
                        add(insertionIndex, block)
                        add(insertionIndex + 1, NoteBlock(type = com.lopleec.kotj.model.BlockType.TEXT))
                    },
                )
                recordUndo(current.document)
                state = state.copy(
                    editor = current.copy(document = document),
                    canUndo = true,
                    canRedo = false,
                )
                scheduleSave()
            }.onFailure { error ->
                state = state.copy(message = string(R.string.photo_add_failed))
            }
        }
    }

    fun readAttachment(block: com.lopleec.kotj.model.NoteBlock, password: String?): AttachmentContent =
        repository.readAttachment(block, password)

    fun closeEditor() {
        if (state.securityOperationInProgress) return
        val snapshot = state.editor ?: return
        val previousSave = saveJob
        previousSave?.cancel()
        saveJob = null
        state = state.copy(securityOperationInProgress = true)
        viewModelScope.launch {
            previousSave?.join()
            val empty = snapshot.document.isMeaningfullyEmpty()
            runCatching {
                if (empty) {
                    withContext(Dispatchers.IO) { repository.deleteAny(snapshot.noteId) }
                } else {
                    val cleaned = snapshot.copy(
                        document = snapshot.document.copy(
                            blocks = snapshot.document.blocks.filterNot { block ->
                                block.type == com.lopleec.kotj.model.BlockType.TEXT && block.text.isBlank()
                            },
                        ),
                    )
                    persistSnapshot(cleaned, cleanupAttachments = true)
                }
            }.onSuccess {
                if (empty) systemUnlockStore.remove(snapshot.noteId)
                resetHistory()
                state = state.copy(
                    editor = null,
                    securityOperationInProgress = false,
                    canUndo = false,
                    canRedo = false,
                )
                refresh()
            }.onFailure { error ->
                if (error is CancellationException) throw error
                state = state.copy(
                    securityOperationInProgress = false,
                    message = string(R.string.note_save_failed),
                )
            }
        }
    }

    fun moveEditorToTrash(password: String?) {
        if (state.securityOperationInProgress) return
        val editor = state.editor ?: return
        val previousSave = saveJob
        previousSave?.cancel()
        saveJob = null
        state = state.copy(securityOperationInProgress = true)
        viewModelScope.launch {
            previousSave?.join()
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    if (editor.encrypted) {
                        require(!password.isNullOrEmpty() && password == editor.password) { "密码错误" }
                    }
                    repository.saveDocument(
                        noteId = editor.noteId,
                        categoryId = editor.categoryId,
                        document = editor.document,
                        encrypted = editor.encrypted,
                        password = editor.password,
                    )
                    repository.setDeleted(editor.noteId, true)
                }
            }
            result.onSuccess {
                resetHistory()
                state = state.copy(
                    editor = null,
                    message = string(R.string.moved_to_trash),
                    securityOperationInProgress = false,
                    canUndo = false,
                    canRedo = false,
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                state = state.copy(
                    message = string(R.string.incorrect_password_not_deleted),
                    securityOperationInProgress = false,
                )
            }
            refresh()
        }
    }

    fun restore(id: String) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.setDeleted(id, false) } }
                .onSuccess { state = state.copy(message = string(R.string.note_restored)) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    state = state.copy(message = string(R.string.note_restore_failed))
                }
            refresh()
        }
    }

    fun moveNoteToTrash(id: String, password: String? = null, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.moveToTrashAuthorized(id, password) } }
                .onSuccess {
                    onSuccess()
                    state = state.copy(message = string(R.string.moved_to_trash))
                }
                .onFailure { error ->
                    state = state.copy(message = string(R.string.incorrect_password_not_deleted))
                }
            refresh()
        }
    }

    fun moveNote(id: String, categoryId: String?) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.moveNote(id, categoryId) } }
                .onSuccess { state = state.copy(message = string(R.string.note_moved_to_group)) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    state = state.copy(message = string(R.string.note_move_failed))
                }
            refresh()
        }
    }

    fun renameNote(id: String, title: String) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.renameNote(id, title) } }
                .onSuccess { state = state.copy(message = string(R.string.note_title_updated)) }
                .onFailure { state = state.copy(message = string(R.string.note_rename_failed)) }
            refresh()
        }
    }

    fun updateSettings(settings: AppSettings) {
        settingsRepository.save(settings)
        state = state.copy(settings = settings)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.purgeExpiredTrash(settings.trashRetentionDays)
                        .forEach(systemUnlockStore::remove)
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                state = state.copy(message = string(R.string.trash_cleanup_failed))
            }
            if (state.showingTrash) refresh()
        }
    }

    fun deleteForever(id: String, password: String? = null, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.deleteForeverAuthorized(id, password) } }
                .onSuccess {
                    systemUnlockStore.remove(id)
                    onSuccess()
                    state = state.copy(message = string(R.string.deleted_forever))
                }
                .onFailure { error ->
                    state = state.copy(message = string(R.string.incorrect_password_not_deleted))
                }
            refresh()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.emptyTrashUnencrypted()
                    repository.encryptedTrashCount()
                }
            }.onSuccess { encryptedLeft ->
                state = state.copy(
                    message = if (encryptedLeft > 0) {
                        string(R.string.unencrypted_trash_deleted)
                    } else {
                        string(R.string.trash_emptied)
                    },
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                state = state.copy(message = string(R.string.trash_empty_failed))
            }
            refresh()
        }
    }

    fun addCategory(name: String) = categoryAction { repository.addCategory(name) }
    fun renameCategory(id: String, name: String) = categoryAction { repository.renameCategory(id, name) }
    fun deleteCategory(id: String) = categoryAction {
        repository.deleteCategory(id)
        if (state.selectedCategoryId == id) state = state.copy(selectedCategoryId = null)
    }

    private fun categoryAction(action: () -> Unit) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { action() } }
                .onFailure { state = state.copy(message = string(R.string.group_update_failed)) }
            refresh()
        }
    }

    fun clearMessage() {
        state = state.copy(message = null)
    }

    fun showMessage(@StringRes id: Int, vararg args: Any) {
        state = state.copy(message = string(id, *args))
    }

    fun scheduleEncryptedAutoLock() {
        autoLockJob?.cancel()
        val noteId = state.editor?.takeIf { it.encrypted }?.noteId ?: return
        autoLockJob = viewModelScope.launch {
            delay(AUTO_LOCK_DELAY_MS)
            if (state.editor?.noteId == noteId) lockEncryptedEditor()
        }
    }

    fun cancelEncryptedAutoLock() {
        autoLockJob?.cancel()
        autoLockJob = null
    }

    private fun lockEncryptedEditor() {
        val editor = state.editor?.takeIf { it.encrypted } ?: return
        val previousSave = saveJob
        previousSave?.cancel()
        saveJob = null
        state = state.copy(securityOperationInProgress = true)
        viewModelScope.launch {
            previousSave?.join()
            runCatching {
                persistSnapshot(editor, cleanupAttachments = true)
            }.onSuccess {
                if (state.editor?.noteId == editor.noteId) {
                    resetHistory()
                    state = state.copy(
                        editor = null,
                        securityOperationInProgress = false,
                        canUndo = false,
                        canRedo = false,
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                state = state.copy(
                    securityOperationInProgress = false,
                    message = string(R.string.auto_lock_save_failed),
                )
            }
            refresh()
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(850)
            state.editor?.let { saveSnapshot(it) }
            refreshSummariesKeepingEditor()
        }
    }

    private suspend fun saveSnapshot(editor: EditorSession, cleanupAttachments: Boolean = false) {
        runCatching {
            persistSnapshot(editor, cleanupAttachments)
        }.onFailure { error ->
            if (error is CancellationException) throw error
            state = state.copy(message = string(R.string.note_background_save_failed))
        }
    }

    private suspend fun persistSnapshot(editor: EditorSession, cleanupAttachments: Boolean = false) {
        withContext(Dispatchers.IO) {
            repository.saveDocument(
                noteId = editor.noteId,
                categoryId = editor.categoryId,
                document = editor.document,
                encrypted = editor.encrypted,
                password = editor.password,
                cleanupAttachments = cleanupAttachments,
            )
        }
    }

    private suspend fun refreshSummariesKeepingEditor() {
        val generation = ++refreshGeneration
        val showingTrash = state.showingTrash
        runCatching { withContext(Dispatchers.IO) { repository.listNotes(showingTrash) } }
            .onSuccess { notes ->
                if (generation == refreshGeneration && showingTrash == state.showingTrash) {
                    state = state.copy(notes = notes)
                }
            }
            .onFailure { error ->
                if (error is CancellationException) throw error
                if (generation == refreshGeneration) {
                    state = state.copy(message = string(R.string.notes_refresh_failed))
                }
            }
    }

    private fun recordUndo(document: NoteDocument) {
        if (undoHistory.lastOrNull() != document) {
            undoHistory.addLast(document)
            while (undoHistory.size > MAX_HISTORY) undoHistory.removeFirst()
        }
        redoHistory.clear()
    }

    private fun resetHistory() {
        undoHistory.clear()
        redoHistory.clear()
    }

    private fun string(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    private companion object {
        const val AUTO_LOCK_DELAY_MS = 15_000L
        const val MAX_HISTORY = 100
    }
}
