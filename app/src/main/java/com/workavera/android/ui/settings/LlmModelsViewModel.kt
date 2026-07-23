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
import com.workavera.android.data.LlmModelsRepository
import com.workavera.android.data.model.LlmModel
import com.workavera.android.data.model.LlmModelInput
import com.workavera.android.data.model.LlmShareTarget
import com.workavera.android.data.network.ApiException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

data class LlmModelsUiState(
    val models: List<LlmModel> = emptyList(),
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
    val mutatingIds: Set<String> = emptySet(),
    // Sharing
    val shareTargets: List<LlmShareTarget> = emptyList(),
    val isLoadingTargets: Boolean = false,
    val isSharing: Boolean = false,
) {
    /** Only owned (non-shared-copy) models can be shared onward. */
    val shareableModels: List<LlmModel> get() = models.filterNot { it.isSharedCopy }
}

/** Backs Model Configurations and Model Sharing, matching the iOS LLMSettingsStore. */
class LlmModelsViewModel(
    private val repository: LlmModelsRepository,
) : ViewModel() {

    var uiState by mutableStateOf(LlmModelsUiState())
        private set

    /** Cache-first: render the disk snapshot, then refresh over the network. */
    fun loadIfNeeded() {
        if (uiState.hasLoaded || uiState.isLoading) return
        repository.cachedModels()?.let { cached ->
            uiState = uiState.copy(models = cached, hasLoaded = true)
        }
        refresh()
    }

    fun refresh() {
        if (uiState.isLoading) return
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            uiState = try {
                val models = repository.loadModels()
                uiState.copy(models = models, isLoading = false, hasLoaded = true, error = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState.copy(isLoading = false, hasLoaded = true, error = message(e, "Could not load model configurations."))
            }
        }
    }

    /** Creates or updates a model; onComplete gets an error message or null on success. */
    fun save(existingId: String?, input: LlmModelInput, onComplete: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                if (existingId != null) repository.update(existingId, input) else repository.create(input)
                refresh()
                onComplete(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onComplete(message(e, "Could not save the model."))
            }
        }
    }

    fun setDefault(id: String) {
        if (id in uiState.mutatingIds) return
        uiState = uiState.copy(mutatingIds = uiState.mutatingIds + id)
        viewModelScope.launch {
            try {
                repository.setDefault(id)
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(error = message(e, "Could not set the default model."))
            } finally {
                uiState = uiState.copy(mutatingIds = uiState.mutatingIds - id)
            }
        }
    }

    fun delete(id: String) {
        if (id in uiState.mutatingIds) return
        uiState = uiState.copy(mutatingIds = uiState.mutatingIds + id)
        viewModelScope.launch {
            try {
                repository.delete(id)
                uiState = uiState.copy(models = uiState.models.filterNot { it.id == id })
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(error = message(e, "Could not delete the model."))
            } finally {
                uiState = uiState.copy(mutatingIds = uiState.mutatingIds - id)
            }
        }
    }

    fun loadShareTargets() {
        if (uiState.isLoadingTargets) return
        uiState = uiState.copy(isLoadingTargets = true)
        viewModelScope.launch {
            uiState = try {
                val targets = repository.loadShareTargets()
                uiState.copy(shareTargets = targets, isLoadingTargets = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState.copy(isLoadingTargets = false, error = message(e, "Could not load users."))
            }
        }
    }

    /** Shares a model; onComplete gets the invited-user count on success or an error message. */
    fun share(modelId: String, userIds: List<String>, onComplete: (Int?, String?) -> Unit) {
        if (uiState.isSharing) return
        uiState = uiState.copy(isSharing = true)
        viewModelScope.launch {
            try {
                val count = repository.share(modelId, userIds)
                onComplete(count, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onComplete(null, message(e, "Could not share the model."))
            } finally {
                uiState = uiState.copy(isSharing = false)
            }
        }
    }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }

    private fun message(e: Exception, fallback: String): String =
        (e as? ApiException)?.userMessage ?: e.message ?: fallback

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as WorkaveraApplication
                LlmModelsViewModel(app.container.llmModelsRepository)
            }
        }
    }
}
