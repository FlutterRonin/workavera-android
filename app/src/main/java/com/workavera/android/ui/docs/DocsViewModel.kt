package com.workavera.android.ui.docs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workavera.android.WorkaveraApplication
import com.workavera.android.data.DocsRepository
import com.workavera.android.data.model.DocsViewFilter
import com.workavera.android.data.model.DocumentFolder
import com.workavera.android.data.model.DocumentProject
import com.workavera.android.data.model.DocumentShare
import com.workavera.android.data.model.DocumentSummary
import com.workavera.android.data.model.DocumentVersion
import com.workavera.android.data.model.WorkspaceDocument
import com.workavera.android.data.network.ApiException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

data class DocsUiState(
    val query: String = "",
    val view: DocsViewFilter = DocsViewFilter.MyDocuments,
    val documents: List<DocumentSummary> = emptyList(),
    val folders: List<DocumentFolder> = emptyList(),
    val projects: List<DocumentProject> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val totalItems: Int = 0,
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
    val pinningIds: Set<String> = emptySet(),
    val mutatingIds: Set<String> = emptySet(),
)

/**
 * Docs state and actions, mirroring the iOS DocsStore: cache-first page loads,
 * view switching with rollback on failure, optimistic pin toggling, and a
 * per-document cache for the detail screen.
 */
class DocsViewModel(
    private val repository: DocsRepository,
) : ViewModel() {

    var uiState by mutableStateOf(DocsUiState())
        private set

    /** Full documents loaded for the detail screen, keyed by id. */
    val documentCache = mutableStateMapOf<String, WorkspaceDocument>()

    /**
     * Public link of each document that has one. A document listed in
     * [resolvedShareIds] but absent here is known not to be shared, which is
     * what lets the top bar tell "no link" apart from "not checked yet".
     * Never cached to disk: a stale link would be shown as live long after it
     * was revoked or moved to another revision.
     */
    private val shares = mutableStateMapOf<String, DocumentShare>()

    var resolvedShareIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var loadingDocumentIds by mutableStateOf<Set<String>>(emptySet())
        private set

    private var loadSequence = 0

    fun loadIfNeeded() {
        if (uiState.hasLoaded || uiState.isLoading) return
        repository.cachedPage(uiState.view, uiState.query, uiState.page + 1)?.let { cached ->
            uiState = uiState.copy(
                documents = cached.documents,
                folders = cached.folders,
                projects = cached.projects,
                page = maxOf(0, cached.page - 1),
                totalPages = cached.totalPages,
                totalItems = cached.totalItems,
                hasLoaded = true,
            )
        }
        refresh()
    }

    fun refresh(requestedPage: Int? = null, reconcile: Boolean = true) {
        if (uiState.isLoading) return
        viewModelScope.launch { doRefresh(requestedPage, reconcile) }
    }

    private suspend fun doRefresh(requestedPage: Int? = null, reconcile: Boolean = true) {
        val view = uiState.view
        val targetPage = if (view.usesPagination) maxOf(0, requestedPage ?: uiState.page) else 0
        loadSequence += 1
        val sequence = loadSequence
        uiState = uiState.copy(isLoading = true)
        try {
            val snapshot = repository.loadPage(view, targetPage + 1, PAGE_SIZE, uiState.query)
            if (sequence != loadSequence) return
            uiState = uiState.copy(
                documents = snapshot.documents,
                folders = snapshot.folders,
                projects = snapshot.projects,
                page = maxOf(0, snapshot.page - 1),
                totalPages = snapshot.totalPages,
                totalItems = snapshot.totalItems,
                isLoading = false,
            )

            // A folder/project selection may point at something that was
            // deleted; fall back to My Documents and reload once.
            if (reconcile && !isCurrentViewAvailable) {
                uiState = uiState.copy(view = DocsViewFilter.MyDocuments, page = 0)
                doRefresh(requestedPage = 0, reconcile = false)
                return
            }

            uiState = uiState.copy(hasLoaded = true, error = null)
            for (summary in uiState.documents) {
                documentCache[summary.id]?.let { documentCache[summary.id] = it.copy(pinned = summary.pinned) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (sequence != loadSequence) return
            uiState = uiState.copy(
                isLoading = false,
                hasLoaded = true,
                error = message(e, "Could not load the documents."),
            )
        }
    }

    /** Switches the browse location; reverts to the previous view on failure. */
    fun selectView(newView: DocsViewFilter) {
        if (uiState.view == newView || uiState.isLoading) return
        val previousView = uiState.view
        val previousPage = uiState.page
        uiState = uiState.copy(view = newView, page = 0)
        viewModelScope.launch {
            doRefresh(requestedPage = 0)
            if (uiState.error != null) {
                uiState = uiState.copy(view = previousView, page = previousPage)
            }
        }
    }

    fun search(newQuery: String) {
        val normalized = newQuery.trim()
        if (uiState.query == normalized) return
        uiState = uiState.copy(query = normalized, page = 0)
        refresh(requestedPage = 0)
    }

    fun loadPage(newPage: Int) {
        if (!uiState.view.usesPagination) return
        refresh(requestedPage = newPage.coerceIn(0, uiState.totalPages - 1))
    }

    /** Cache-first load of one full document for the detail screen. */
    fun loadDocument(id: String, force: Boolean = false) {
        if (id in loadingDocumentIds) return
        if (!force && !documentCache.containsKey(id)) {
            repository.cachedDocument(id)?.let { documentCache[id] = it }
        }
        loadingDocumentIds = loadingDocumentIds + id
        viewModelScope.launch {
            try {
                val pinned = uiState.documents.firstOrNull { it.id == id }?.pinned
                    ?: documentCache[id]?.pinned
                    ?: false
                documentCache[id] = repository.loadDocument(id, pinned)
                uiState = uiState.copy(error = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(error = message(e, "Could not load the document."))
            } finally {
                loadingDocumentIds = loadingDocumentIds - id
            }
        }
    }

    /** Optimistic pin toggle, rolled back on failure (same flow as the iOS togglePin). */
    fun togglePin(id: String) {
        if (id in uiState.pinningIds) return
        val currentPinned = uiState.documents.firstOrNull { it.id == id }?.pinned
            ?: documentCache[id]?.pinned
            ?: false
        val previousDocuments = uiState.documents
        val previousTotal = uiState.totalItems
        val previousCached = documentCache[id]

        setPinned(id, !currentPinned)
        uiState = uiState.copy(pinningIds = uiState.pinningIds + id)
        viewModelScope.launch {
            try {
                repository.setPinned(id, !currentPinned)
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(
                    documents = previousDocuments,
                    totalItems = previousTotal,
                    error = message(e, "Could not update the pin."),
                )
                if (previousCached != null) documentCache[id] = previousCached
            } finally {
                uiState = uiState.copy(pinningIds = uiState.pinningIds - id)
            }
        }
    }

    fun setArchived(id: String, archived: Boolean) {
        if (id in uiState.mutatingIds) return
        uiState = uiState.copy(mutatingIds = uiState.mutatingIds + id)
        viewModelScope.launch {
            try {
                repository.setArchived(id, archived)
                documentCache.remove(id)
                forgetShare(id)
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(error = message(e, "Could not update the document."))
            } finally {
                uiState = uiState.copy(mutatingIds = uiState.mutatingIds - id)
            }
        }
    }

    fun delete(id: String, onComplete: (Boolean) -> Unit) {
        if (id in uiState.mutatingIds) {
            onComplete(false)
            return
        }
        uiState = uiState.copy(mutatingIds = uiState.mutatingIds + id)
        viewModelScope.launch {
            try {
                repository.delete(id)
                documentCache.remove(id)
                forgetShare(id)
                refresh()
                onComplete(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(error = message(e, "Could not delete the document."))
                onComplete(false)
            } finally {
                uiState = uiState.copy(mutatingIds = uiState.mutatingIds - id)
            }
        }
    }

    suspend fun loadVersionsFromCache(id: String): List<DocumentVersion> =
        repository.cachedVersions(id) ?: emptyList()

    suspend fun loadVersions(id: String): List<DocumentVersion> = repository.loadVersions(id)

    suspend fun loadVersionFromCache(id: String, revision: Int): DocumentVersion? =
        repository.cachedVersion(id, revision)

    suspend fun loadVersion(id: String, revision: Int): DocumentVersion =
        repository.loadVersion(id, revision)

    /** Only the owner of a document may publish it, matching the web app. */
    fun isOwnedBySignedInUser(document: WorkspaceDocument): Boolean =
        document.ownerId.isNotEmpty() && document.ownerId == repository.currentUserId

    fun shareOf(id: String): DocumentShare? = shares[id]

    /**
     * Resolves the link behind the top bar's share button. Only decides how
     * that button reads, so a failure stays quiet; opening the sheet retries
     * and reports it properly.
     */
    fun loadShareIfNeeded(id: String) {
        if (id in resolvedShareIds) return
        viewModelScope.launch {
            try {
                applyShare(id, repository.loadShare(id))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Left unresolved so the sheet can try again.
            }
        }
    }

    suspend fun refreshShare(id: String) = applyShare(id, repository.loadShare(id))

    suspend fun publishShare(id: String, expires: String, republish: Boolean) =
        applyShare(id, repository.publish(id, expires, republish))

    suspend fun revokeShare(id: String) {
        repository.unpublish(id)
        applyShare(id, null)
    }

    private fun applyShare(id: String, share: DocumentShare?) {
        if (share == null) shares.remove(id) else shares[id] = share
        resolvedShareIds = resolvedShareIds + id
    }

    /**
     * Drops what is known about a link rather than declaring it gone: after
     * archiving or deleting, the next document that needs it asks the server.
     */
    private fun forgetShare(id: String) {
        shares.remove(id)
        resolvedShareIds = resolvedShareIds - id
    }

    /** Public address of a link, as a reader would open it in a browser. */
    fun shareUrl(share: DocumentShare): String =
        "${repository.shareBaseUrl.orEmpty()}/s/${share.slug}"

    fun locationName(projectId: String?, folderId: String?): String = when {
        projectId != null -> uiState.projects.firstOrNull { it.id == projectId }?.name ?: "Project"
        folderId != null -> uiState.folders.firstOrNull { it.id == folderId }?.name ?: "Folder"
        else -> "My documents"
    }

    fun label(view: DocsViewFilter = uiState.view): String = when (view) {
        DocsViewFilter.Recent -> "Recent"
        DocsViewFilter.Pinned -> "Pinned"
        DocsViewFilter.MyDocuments -> "My documents"
        is DocsViewFilter.Folder -> uiState.folders.firstOrNull { it.id == view.id }?.name ?: "Folder"
        is DocsViewFilter.Project -> uiState.projects.firstOrNull { it.id == view.id }?.name ?: "Project"
        DocsViewFilter.Archived -> "Archived"
    }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }

    private fun setPinned(id: String, pinned: Boolean) {
        var documents = uiState.documents
        var totalItems = uiState.totalItems
        val index = documents.indexOfFirst { it.id == id }
        if (index >= 0) {
            documents = documents.toMutableList().apply { set(index, get(index).copy(pinned = pinned)) }
            if (uiState.view is DocsViewFilter.Pinned && !pinned) {
                documents = documents.toMutableList().apply { removeAt(index) }
                totalItems = maxOf(0, totalItems - 1)
            }
        }
        uiState = uiState.copy(documents = documents, totalItems = totalItems)
        documentCache[id]?.let { documentCache[id] = it.copy(pinned = pinned) }
    }

    /** A folder/project selection is only valid while the target still exists. */
    private val isCurrentViewAvailable: Boolean
        get() = when (val view = uiState.view) {
            is DocsViewFilter.Folder -> uiState.folders.any { it.id == view.id }
            is DocsViewFilter.Project -> uiState.projects.any { it.id == view.id }
            else -> true
        }

    private fun message(e: Exception, fallback: String): String =
        (e as? ApiException)?.userMessage ?: e.message ?: fallback

    companion object {
        const val PAGE_SIZE = 15

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as WorkaveraApplication
                DocsViewModel(app.container.docsRepository)
            }
        }
    }
}
