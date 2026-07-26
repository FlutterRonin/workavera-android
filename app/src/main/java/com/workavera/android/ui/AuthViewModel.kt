package com.workavera.android.ui

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
import com.workavera.android.data.AuthState
import com.workavera.android.data.SettingsRepository
import com.workavera.android.data.model.AvatarUpload
import com.workavera.android.data.model.ProfileUpdate
import com.workavera.android.data.model.UserStatus
import com.workavera.android.data.network.ApiException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class ProfileUiState(
    /** The target status being submitted; null when idle. */
    val updatingStatus: UserStatus? = null,
    val error: String? = null,
)

class AuthViewModel(
    private val repository: AuthRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = repository.state

    val serverAddress: StateFlow<String> =
        settings.serverAddress.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val lastLoginEmail: StateFlow<String> =
        settings.lastLoginEmail.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    var loginUiState by mutableStateOf(LoginUiState())
        private set

    var profileUiState by mutableStateOf(ProfileUiState())
        private set

    /** Pull-to-refresh on the profile page (iOS refreshSession); best-effort. */
    var isRefreshingProfile by mutableStateOf(false)
        private set

    fun refreshProfile() {
        if (isRefreshingProfile) return
        viewModelScope.launch {
            isRefreshingProfile = true
            try {
                repository.refreshSession()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort refresh; the visible session stays as is.
            } finally {
                isRefreshingProfile = false
            }
        }
    }

    init {
        viewModelScope.launch { repository.restoreSession() }
    }

    fun login(email: String, password: String) {
        if (loginUiState.isLoading) return
        viewModelScope.launch {
            loginUiState = LoginUiState(isLoading = true)
            loginUiState = try {
                repository.login(email, password)
                LoginUiState()
            } catch (e: ApiException) {
                LoginUiState(error = e.userMessage)
            } catch (e: Exception) {
                LoginUiState(error = e.message ?: "Sign in failed.")
            }
        }
    }

    fun retryRestore() {
        viewModelScope.launch { repository.restoreSession() }
    }

    fun abandonRecovery() {
        viewModelScope.launch { repository.abandonRecovery() }
    }

    fun logout() {
        viewModelScope.launch { repository.logout() }
    }

    fun saveServerAddress(address: String) {
        viewModelScope.launch { settings.setServerAddress(address) }
    }

    fun updateStatus(status: UserStatus) {
        if (profileUiState.updatingStatus != null) return
        viewModelScope.launch {
            profileUiState = ProfileUiState(updatingStatus = status)
            profileUiState = try {
                repository.updateStatus(status)
                ProfileUiState()
            } catch (e: ApiException) {
                ProfileUiState(error = e.userMessage)
            } catch (e: Exception) {
                ProfileUiState(error = e.message ?: "Could not update your status.")
            }
        }
    }

    /** Saves the profile; onComplete receives null on success, otherwise an error message. */
    fun updateProfile(
        profile: ProfileUpdate,
        avatar: AvatarUpload?,
        onComplete: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                repository.updateProfile(profile, avatar)
                onComplete(null)
            } catch (e: ApiException) {
                onComplete(e.userMessage)
            } catch (e: Exception) {
                onComplete(e.message ?: "Could not save your profile.")
            }
        }
    }

    /** Changes the password; onComplete receives null on success, otherwise an error message. */
    fun changePassword(
        currentPassword: String,
        newPassword: String,
        onComplete: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                repository.changePassword(currentPassword, newPassword)
                onComplete(null)
            } catch (e: ApiException) {
                onComplete(e.userMessage)
            } catch (e: Exception) {
                onComplete(e.message ?: "Could not change your password.")
            }
        }
    }

    fun avatarUrl(state: AuthState.LoggedIn): String? = repository.avatarUrl(state)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as WorkaveraApplication
                AuthViewModel(app.container.authRepository, app.container.settings)
            }
        }
    }
}
