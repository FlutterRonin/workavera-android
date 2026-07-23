package com.workavera.android.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workavera.android.WorkaveraApplication
import com.workavera.android.data.AuthRepository
import com.workavera.android.data.MemoryPreferences
import com.workavera.android.data.PreferencesRepository
import com.workavera.android.data.SettingsRepository
import com.workavera.android.data.model.AppAppearance
import com.workavera.android.data.network.ApiException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val error: String? = null,
    val isDeletingAccount: Boolean = false,
)

/** Backs the Settings screen: appearance, chat-memory flags, and account deletion. */
class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val preferences: PreferencesRepository,
    settings: SettingsRepository,
) : ViewModel() {

    val appearance: StateFlow<AppAppearance> =
        settings.appearance.stateIn(viewModelScope, SharingStarted.Eagerly, AppAppearance.SYSTEM)

    val memory: StateFlow<MemoryPreferences> = preferences.memory

    var uiState by mutableStateOf(SettingsUiState())
        private set

    init {
        viewModelScope.launch { preferences.load() }
    }

    fun reloadPreferences() {
        viewModelScope.launch { preferences.load() }
    }

    fun updateAppearance(appearance: AppAppearance) {
        viewModelScope.launch {
            try {
                preferences.updateAppearance(appearance)
                uiState = uiState.copy(error = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(error = message(e, "Could not update the theme."))
            }
        }
    }

    fun setMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferences.updateMemoryEnabled(enabled)
                uiState = uiState.copy(error = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(error = message(e, "Could not update your memory settings."))
            }
        }
    }

    fun setMemoryAutoCapture(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferences.updateMemoryAutoCapture(enabled)
                uiState = uiState.copy(error = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(error = message(e, "Could not update your memory settings."))
            }
        }
    }

    /** Deletes the account; on success the session ends and this screen is torn down. */
    fun deleteAccount() {
        if (uiState.isDeletingAccount) return
        uiState = uiState.copy(isDeletingAccount = true, error = null)
        viewModelScope.launch {
            try {
                authRepository.deleteAccount()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isDeletingAccount = false,
                    error = message(e, "Could not delete your account."),
                )
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
                SettingsViewModel(
                    app.container.authRepository,
                    app.container.preferencesRepository,
                    app.container.settings,
                )
            }
        }
    }
}
