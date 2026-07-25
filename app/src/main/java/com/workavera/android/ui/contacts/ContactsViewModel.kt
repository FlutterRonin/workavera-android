package com.workavera.android.ui.contacts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workavera.android.WorkaveraApplication
import com.workavera.android.data.ContactsRepository
import com.workavera.android.data.model.Contact
import com.workavera.android.data.network.ApiException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

data class ContactsUiState(
    val contacts: List<Contact> = emptyList(),
    val favoriteRecordIds: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
    val mutatingContactIds: Set<String> = emptySet(),
)

/**
 * Contacts state and actions, mirroring the iOS ContactsStore: cache-first
 * load with a sequence race guard, and an optimistic favorite toggle that
 * rolls back on failure. The directory is read-only; favorite/unfavorite via
 * the contact_favorites collection is the only mutation.
 */
class ContactsViewModel(
    private val repository: ContactsRepository,
) : ViewModel() {

    var uiState by mutableStateOf(ContactsUiState())
        private set

    private var loadSequence = 0

    private val currentUserId: String? get() = repository.currentUserId

    fun loadIfNeeded() {
        if (uiState.hasLoaded || uiState.isLoading) return
        repository.cachedSnapshot()?.let { cached ->
            uiState = uiState.copy(
                contacts = cached.contacts,
                favoriteRecordIds = cached.favoriteRecordIds,
                hasLoaded = true,
            )
        }
        refresh()
    }

    fun refresh() {
        if (uiState.isLoading) return
        viewModelScope.launch { doRefresh() }
    }

    private suspend fun doRefresh() {
        loadSequence += 1
        val sequence = loadSequence
        uiState = uiState.copy(isLoading = true)
        try {
            val snapshot = repository.loadSnapshot()
            if (sequence != loadSequence) return
            uiState = uiState.copy(
                contacts = snapshot.contacts,
                favoriteRecordIds = snapshot.favoriteRecordIds,
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
                error = message(e, "Could not load contacts."),
            )
        }
    }

    /** Optimistic favorite toggle (iOS toggleFavorite): flip first, roll back on failure. */
    fun toggleFavorite(contactId: String) {
        if (contactId == currentUserId) return
        if (contactId in uiState.mutatingContactIds) return
        val index = uiState.contacts.indexOfFirst { it.id == contactId }
        if (index < 0) return

        val wasFavorite = uiState.contacts[index].isFavorite
        val previousRecordId = uiState.favoriteRecordIds[contactId]
        patchContact(uiState.contacts[index].copy(isFavorite = !wasFavorite))
        uiState = uiState.copy(mutatingContactIds = uiState.mutatingContactIds + contactId)
        viewModelScope.launch {
            try {
                if (previousRecordId != null) {
                    repository.deleteFavorite(previousRecordId)
                    uiState = uiState.copy(favoriteRecordIds = uiState.favoriteRecordIds - contactId)
                } else {
                    val record = repository.createFavorite(contactId)
                    uiState = uiState.copy(
                        favoriteRecordIds = uiState.favoriteRecordIds + (contactId to record.id),
                    )
                }
                doRefresh()
                uiState = uiState.copy(error = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val currentIndex = uiState.contacts.indexOfFirst { it.id == contactId }
                if (currentIndex >= 0) {
                    patchContact(uiState.contacts[currentIndex].copy(isFavorite = wasFavorite))
                }
                uiState = uiState.copy(
                    favoriteRecordIds = if (previousRecordId != null) {
                        uiState.favoriteRecordIds + (contactId to previousRecordId)
                    } else {
                        uiState.favoriteRecordIds - contactId
                    },
                    error = message(e, "Could not update the favorite."),
                )
            } finally {
                uiState = uiState.copy(mutatingContactIds = uiState.mutatingContactIds - contactId)
            }
        }
    }

    fun contact(id: String): Contact? = uiState.contacts.firstOrNull { it.id == id }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }

    private fun patchContact(updated: Contact) {
        val index = uiState.contacts.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            uiState = uiState.copy(
                contacts = uiState.contacts.toMutableList().apply { set(index, updated) },
            )
        }
    }

    private fun message(e: Exception, fallback: String): String =
        (e as? ApiException)?.userMessage ?: e.message ?: fallback

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as WorkaveraApplication
                ContactsViewModel(app.container.contactsRepository)
            }
        }
    }
}
