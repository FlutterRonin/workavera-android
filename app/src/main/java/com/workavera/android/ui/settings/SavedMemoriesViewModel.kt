package com.workavera.android.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workavera.android.WorkaveraApplication
import com.workavera.android.data.SavedMemoriesRepository
import com.workavera.android.data.model.MemoryCategory
import com.workavera.android.data.model.SavedChatMemory
import com.workavera.android.data.network.ApiException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

data class SavedMemoriesUiState(
    val memories: List<SavedChatMemory> = emptyList(),
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val categoryFilter: MemoryCategory? = null,
    /** Ids being toggled / deleted, for inline loading. */
    val mutatingIds: Set<String> = emptySet(),
) {
    val visible: List<SavedChatMemory>
        get() {
            val q = query.trim().lowercase()
            return memories.filter { memory ->
                (categoryFilter == null || memory.memoryCategory == categoryFilter) &&
                    (q.isEmpty() || memory.content.lowercase().contains(q) || memory.category.contains(q))
            }
        }

    val atLimit: Boolean get() = memories.size >= MEMORY_LIMIT
}

const val MEMORY_LIMIT = 50

/** Backs the Saved Memories screen: list, search/filter, toggle, edit, delete. */
class SavedMemoriesViewModel(
    private val repository: SavedMemoriesRepository,
) : ViewModel() {

    var uiState by mutableStateOf(SavedMemoriesUiState())
        private set

    /** Cache-first: render the disk snapshot, then refresh over the network. */
    fun loadIfNeeded() {
        if (uiState.hasLoaded || uiState.isLoading) return
        repository.cachedMemories()?.let { cached ->
            uiState = uiState.copy(memories = cached, hasLoaded = true)
        }
        refresh()
    }

    fun refresh() {
        if (uiState.isLoading) return
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            uiState = try {
                val memories = repository.load()
                uiState.copy(memories = memories, isLoading = false, hasLoaded = true, error = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState.copy(isLoading = false, hasLoaded = true, error = message(e, "Could not load memories."))
            }
        }
    }

    fun setQuery(query: String) {
        uiState = uiState.copy(query = query)
    }

    fun setCategoryFilter(category: MemoryCategory?) {
        uiState = uiState.copy(categoryFilter = category)
    }

    fun setActive(memory: SavedChatMemory, active: Boolean) {
        if (memory.id in uiState.mutatingIds) return
        uiState = uiState.copy(mutatingIds = uiState.mutatingIds + memory.id)
        viewModelScope.launch {
            try {
                val updated = repository.setActive(memory.id, active)
                replace(updated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(error = message(e, "Could not update the memory."))
            } finally {
                uiState = uiState.copy(mutatingIds = uiState.mutatingIds - memory.id)
            }
        }
    }

    /** Creates or updates a memory; onComplete receives an error message or null on success. */
    fun save(
        existingId: String?,
        category: MemoryCategory,
        content: String,
        onComplete: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val saved = if (existingId != null) {
                    repository.update(existingId, category, content.trim())
                } else {
                    repository.create(category, content.trim())
                }
                replace(saved)
                onComplete(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onComplete(message(e, "Could not save the memory."))
            }
        }
    }

    fun delete(id: String) {
        if (id in uiState.mutatingIds) return
        uiState = uiState.copy(mutatingIds = uiState.mutatingIds + id)
        viewModelScope.launch {
            try {
                repository.delete(id)
                uiState = uiState.copy(memories = uiState.memories.filterNot { it.id == id })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(error = message(e, "Could not delete the memory."))
            } finally {
                uiState = uiState.copy(mutatingIds = uiState.mutatingIds - id)
            }
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            val ids = uiState.memories.map { it.id }
            var failed = 0
            for (id in ids) {
                try {
                    repository.delete(id)
                    uiState = uiState.copy(memories = uiState.memories.filterNot { it.id == id })
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    failed++
                }
            }
            if (failed > 0) {
                uiState = uiState.copy(
                    error = "Some memories could not be removed. Pull to refresh and try again.",
                )
            }
        }
    }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }

    private fun replace(memory: SavedChatMemory) {
        val existing = uiState.memories.any { it.id == memory.id }
        val merged = if (existing) {
            uiState.memories.map { if (it.id == memory.id) memory else it }
        } else {
            uiState.memories + memory
        }.sortedByDescending { it.updated }
        uiState = uiState.copy(memories = merged, error = null)
    }

    private fun message(e: Exception, fallback: String): String =
        (e as? ApiException)?.userMessage ?: e.message ?: fallback

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as WorkaveraApplication
                SavedMemoriesViewModel(app.container.savedMemoriesRepository)
            }
        }
    }
}
