package com.workavera.android.ui.reading

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workavera.android.WorkaveraApplication
import com.workavera.android.data.AiConsentRepository
import com.workavera.android.data.LlmModelsRepository
import com.workavera.android.data.ReadingRepository
import com.workavera.android.data.model.LlmModel
import com.workavera.android.data.model.ReadingItem
import com.workavera.android.data.model.ReadingItemInput
import com.workavera.android.data.model.ReadingListFilter
import com.workavera.android.data.model.ReadingStatus
import com.workavera.android.data.network.ApiException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

data class ReadingUiState(
    val items: List<ReadingItem> = emptyList(),
    val filter: ReadingListFilter = ReadingListFilter.ACTIVE,
    val query: String = "",
    val page: Int = 0,
    val totalPages: Int = 1,
    val totalItems: Int = 0,
    val unreadCount: Int = 0,
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
    val mutatingItemIds: Set<String> = emptySet(),
    val isMarkingAllRead: Boolean = false,
    // LLM models gating the summarize action (iOS LLMSettingsStore)
    val models: List<LlmModel> = emptyList(),
    val isLoadingModels: Boolean = false,
    val hasLoadedModels: Boolean = false,
) {
    /** Model used for summaries: the configured default, else the first one. */
    val defaultModel: LlmModel? get() = models.firstOrNull { it.isDefault } ?: models.firstOrNull()
}

/**
 * Reading state and actions, mirroring the iOS ReadingStore: cache-first page
 * loads with a sequence race guard, filter/search switching back to page 0,
 * per-item mutation tracking, and a page step-back after deleting the last
 * item of a page.
 */
class ReadingViewModel(
    private val repository: ReadingRepository,
    private val llmModelsRepository: LlmModelsRepository,
    private val aiConsent: AiConsentRepository,
) : ViewModel() {

    var uiState by mutableStateOf(ReadingUiState())
        private set

    private var loadSequence = 0

    private val currentUserId: String? get() = repository.currentUserId

    fun loadIfNeeded() {
        if (uiState.hasLoaded || uiState.isLoading) return
        repository.cachedPage(uiState.filter, uiState.query, uiState.page + 1)?.let { cached ->
            uiState = uiState.copy(
                items = cached.items,
                page = maxOf(0, cached.page - 1),
                totalPages = cached.totalPages,
                totalItems = cached.totalItems,
                unreadCount = cached.unreadCount,
                hasLoaded = true,
            )
        }
        refresh()
    }

    fun refresh(requestedPage: Int? = null) {
        if (uiState.isLoading) return
        viewModelScope.launch { doRefresh(requestedPage) }
    }

    private suspend fun doRefresh(requestedPage: Int? = null) {
        val targetPage = maxOf(0, minOf(requestedPage ?: uiState.page, uiState.totalPages - 1))
        loadSequence += 1
        val sequence = loadSequence
        uiState = uiState.copy(isLoading = true)
        try {
            val snapshot = repository.loadPage(uiState.filter, targetPage + 1, PAGE_SIZE, uiState.query)
            if (sequence != loadSequence) return
            uiState = uiState.copy(
                items = snapshot.items,
                page = maxOf(0, snapshot.page - 1),
                totalPages = snapshot.totalPages,
                totalItems = snapshot.totalItems,
                unreadCount = snapshot.unreadCount,
                isLoading = false,
                hasLoaded = true,
                error = null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (sequence != loadSequence) return
            uiState = uiState.copy(
                isLoading = false,
                hasLoaded = true,
                error = message(e, "Could not load the reading list."),
            )
        }
    }

    fun selectFilter(newFilter: ReadingListFilter) {
        if (uiState.filter == newFilter || uiState.isLoading) return
        uiState = uiState.copy(filter = newFilter, page = 0)
        refresh(requestedPage = 0)
    }

    fun search(newQuery: String) {
        val normalized = newQuery.trim()
        if (uiState.query == normalized) return
        uiState = uiState.copy(query = normalized, page = 0)
        refresh(requestedPage = 0)
    }

    fun loadPage(newPage: Int) {
        refresh(requestedPage = newPage.coerceIn(0, uiState.totalPages - 1))
    }

    /** Creates an item, then resets to the unfiltered first page (iOS create). */
    fun create(input: ReadingItemInput, onComplete: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                repository.create(input)
                uiState = uiState.copy(
                    filter = ReadingListFilter.ACTIVE,
                    query = "",
                    page = 0,
                    error = null,
                )
                doRefresh(requestedPage = 0)
                onComplete(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMessage = message(e, "Could not add the item.")
                uiState = uiState.copy(error = errorMessage)
                onComplete(errorMessage)
            }
        }
    }

    /** onUpdated gets the server record so an open detail sheet can refresh its local copy. */
    fun updateStatus(item: ReadingItem, status: ReadingStatus, onUpdated: (ReadingItem) -> Unit = {}) {
        if (item.id in uiState.mutatingItemIds) return
        uiState = uiState.copy(mutatingItemIds = uiState.mutatingItemIds + item.id)
        viewModelScope.launch {
            try {
                val updated = repository.updateStatus(item.id, status)
                doRefresh()
                uiState = uiState.copy(error = null)
                onUpdated(updated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(error = message(e, "Could not update the item."))
            } finally {
                uiState = uiState.copy(mutatingItemIds = uiState.mutatingItemIds - item.id)
            }
        }
    }

    /** Server first, then the local copy is patched and the list refreshed (iOS togglePin). */
    fun togglePin(item: ReadingItem, onUpdated: (ReadingItem) -> Unit = {}) {
        if (item.id in uiState.mutatingItemIds) return
        uiState = uiState.copy(mutatingItemIds = uiState.mutatingItemIds + item.id)
        viewModelScope.launch {
            try {
                repository.setPinned(item.id, !item.pinned)
                val updated = item.copy(pinned = !item.pinned)
                patchItem(updated)
                doRefresh()
                uiState = uiState.copy(error = null)
                onUpdated(updated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(error = message(e, "Could not update the pin."))
            } finally {
                uiState = uiState.copy(mutatingItemIds = uiState.mutatingItemIds - item.id)
            }
        }
    }

    /** After a delete empties a page beyond the first, steps one page back (iOS delete). */
    fun delete(item: ReadingItem, onComplete: (Boolean) -> Unit) {
        if (item.id in uiState.mutatingItemIds) {
            onComplete(false)
            return
        }
        uiState = uiState.copy(mutatingItemIds = uiState.mutatingItemIds + item.id)
        viewModelScope.launch {
            try {
                repository.delete(item.id)
                doRefresh()
                if (uiState.items.isEmpty() && uiState.page > 0 && uiState.totalItems > 0) {
                    doRefresh(requestedPage = uiState.page - 1)
                }
                uiState = uiState.copy(error = null)
                onComplete(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(error = message(e, "Could not delete the item."))
                onComplete(false)
            } finally {
                uiState = uiState.copy(mutatingItemIds = uiState.mutatingItemIds - item.id)
            }
        }
    }

    /** Patches content/summary/key points in place from the summarize endpoint answer. */
    fun summarize(item: ReadingItem, onUpdated: (ReadingItem) -> Unit = {}) {
        if (item.id in uiState.mutatingItemIds) return
        uiState = uiState.copy(mutatingItemIds = uiState.mutatingItemIds + item.id)
        viewModelScope.launch {
            try {
                val response = repository.summarize(item.id)
                val updated = item.copy(
                    contentText = response.contentText,
                    summary = response.summary,
                    keyPoints = response.keyPoints,
                )
                patchItem(updated)
                doRefresh()
                onUpdated(updated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(error = message(e, "Could not summarize the item."))
            } finally {
                uiState = uiState.copy(mutatingItemIds = uiState.mutatingItemIds - item.id)
            }
        }
    }

    fun markAllRead() {
        if (uiState.isMarkingAllRead) return
        uiState = uiState.copy(isMarkingAllRead = true)
        viewModelScope.launch {
            try {
                repository.markAllRead()
                doRefresh()
                uiState = uiState.copy(error = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(error = message(e, "Could not mark all as read."))
            } finally {
                uiState = uiState.copy(isMarkingAllRead = false)
            }
        }
    }

    /** Cache-first model list load for the detail sheet (iOS loadModelsForPresentation). */
    fun loadModelsIfNeeded() {
        if (uiState.hasLoadedModels || uiState.isLoadingModels) return
        llmModelsRepository.cachedModels()?.let { cached ->
            uiState = uiState.copy(models = cached, hasLoadedModels = true)
        }
        uiState = uiState.copy(isLoadingModels = true)
        viewModelScope.launch {
            try {
                val models = llmModelsRepository.loadModels()
                uiState = uiState.copy(models = models, isLoadingModels = false, hasLoadedModels = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The summarize button simply stays disabled without a model list.
                uiState = uiState.copy(isLoadingModels = false, hasLoadedModels = true)
            }
        }
    }

    fun isAiConsentAuthorized(modelId: String): Boolean {
        val userId = currentUserId ?: return false
        return aiConsent.isAuthorized(userId, modelId)
    }

    fun authorizeAiConsent(modelId: String) {
        val userId = currentUserId ?: return
        aiConsent.authorize(userId, modelId)
    }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }

    private fun patchItem(updated: ReadingItem) {
        val index = uiState.items.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            uiState = uiState.copy(
                items = uiState.items.toMutableList().apply { set(index, updated) },
            )
        }
    }

    private fun message(e: Exception, fallback: String): String =
        (e as? ApiException)?.userMessage ?: e.message ?: fallback

    companion object {
        const val PAGE_SIZE = 6

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as WorkaveraApplication
                ReadingViewModel(
                    app.container.readingRepository,
                    app.container.llmModelsRepository,
                    app.container.aiConsentRepository,
                )
            }
        }
    }
}
