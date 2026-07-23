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
import com.workavera.android.data.ApiKeysRepository
import com.workavera.android.data.model.ApiKey
import com.workavera.android.data.model.ApiKeyExpiration
import com.workavera.android.data.model.CreatedApiKey
import com.workavera.android.data.network.ApiException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

data class ApiKeysUiState(
    val keys: List<ApiKey> = emptyList(),
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
    /** Ids of keys currently being revoked, for inline loading. */
    val revokingIds: Set<String> = emptySet(),
)

/** Backs the API Keys screen: list, create (one-time secret), and revoke. */
class ApiKeysViewModel(
    private val repository: ApiKeysRepository,
) : ViewModel() {

    var uiState by mutableStateOf(ApiKeysUiState())
        private set

    val serverAddress: String get() = repository.serverAddress

    /** Cache-first: render the disk snapshot, then refresh over the network. */
    fun loadIfNeeded() {
        if (uiState.hasLoaded || uiState.isLoading) return
        repository.cachedKeys()?.let { cached ->
            uiState = uiState.copy(keys = cached, hasLoaded = true)
        }
        refresh()
    }

    fun refresh() {
        if (uiState.isLoading) return
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            uiState = try {
                val keys = repository.load()
                uiState.copy(keys = keys, isLoading = false, hasLoaded = true, error = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState.copy(isLoading = false, hasLoaded = true, error = message(e, "Could not load API keys."))
            }
        }
    }

    /** Creates a key; onComplete receives the one-time secret on success or an error message. */
    fun create(
        name: String,
        expiration: ApiKeyExpiration,
        allowDestructive: Boolean,
        onComplete: (CreatedApiKey?, String?) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val created = repository.create(name.trim(), expiration, allowDestructive)
                refresh()
                onComplete(created, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onComplete(null, message(e, "Could not create the API key."))
            }
        }
    }

    fun revoke(id: String) {
        if (id in uiState.revokingIds) return
        uiState = uiState.copy(revokingIds = uiState.revokingIds + id)
        viewModelScope.launch {
            try {
                repository.revoke(id)
                uiState = uiState.copy(keys = uiState.keys.filterNot { it.id == id })
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(error = message(e, "Could not revoke the API key."))
            } finally {
                uiState = uiState.copy(revokingIds = uiState.revokingIds - id)
            }
        }
    }

    private fun message(e: Exception, fallback: String): String =
        (e as? ApiException)?.userMessage ?: e.message ?: fallback

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as WorkaveraApplication
                ApiKeysViewModel(app.container.apiKeysRepository)
            }
        }
    }
}
