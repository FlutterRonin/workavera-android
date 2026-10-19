package com.workavera.android.data

import com.workavera.android.data.model.AvatarUpload
import com.workavera.android.data.model.ProfileUpdate
import com.workavera.android.data.model.User
import com.workavera.android.data.model.UserStatus
import com.workavera.android.data.network.ApiException
import com.workavera.android.data.network.PocketBaseApi
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

sealed interface AuthState {
    /** Restoring the session with the locally stored token at launch. */
    data object Restoring : AuthState

    /** Token restore failed (network etc. — credentials may still be valid); retryable. */
    data class RecoveryFailed(val message: String) : AuthState

    data object LoggedOut : AuthState

    data class LoggedIn(
        val user: User,
        val token: String,
        val server: String,
    ) : AuthState
}

/** Single source of truth for the login session; the UI layer only observes [state]. */
class AuthRepository(
    private val api: PocketBaseApi,
    private val settings: SettingsRepository,
) {

    private val _state = MutableStateFlow<AuthState>(AuthState.Restoring)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /**
     * Cache-buster appended to the avatar URL (iOS avatarRevision): the
     * avatar URL is stable across uploads, so it changes after every avatar
     * upload and every session refresh to force a reload.
     */
    private var avatarRevision: String? = null

    /** Called at app launch: exchanges the saved token for a fresh session. */
    suspend fun restoreSession() {
        _state.value = AuthState.Restoring
        val server = settings.serverAddress.first()
        val token = settings.authToken.first()
        val tokenServer = settings.authTokenServer.first()

        if (server.isBlank() || token.isBlank() || tokenServer != server) {
            _state.value = AuthState.LoggedOut
            return
        }

        try {
            val auth = api.authRefresh(server, token)
            settings.setAuthToken(auth.token, server, auth.record.id)
            _state.value = AuthState.LoggedIn(auth.record, auth.token, server)
        } catch (e: ApiException) {
            if (e.isAuthFailure) {
                settings.clearAuthToken()
                _state.value = AuthState.LoggedOut
            } else {
                _state.value = AuthState.RecoveryFailed(e.userMessage)
            }
        }
    }

    /** Throws [ApiException] on failure; the caller presents the error. */
    suspend fun login(email: String, password: String) {
        val server = settings.serverAddress.first()
        if (server.isBlank()) {
            throw ApiException(0, "Set your server address first.")
        }
        val identity = email.trim()
        val auth = api.authWithPassword(server, identity, password)
        settings.setAuthToken(auth.token, server, auth.record.id)
        settings.setLastLoginEmail(identity)
        _state.value = AuthState.LoggedIn(auth.record, auth.token, server)
    }

    suspend fun logout() {
        settings.clearAuthToken()
        _state.value = AuthState.LoggedOut
    }

    /** Gives up on session recovery and returns to sign-in (keeps the server address, clears the stale token). */
    suspend fun abandonRecovery() {
        settings.clearAuthToken()
        _state.value = AuthState.LoggedOut
    }

    suspend fun updateStatus(status: UserStatus) {
        val current = _state.value as? AuthState.LoggedIn ?: return
        val updated = api.updateStatus(current.server, current.token, current.user.id, status)
        applyUpdatedUser(updated)
    }

    /** Updates the profile (optionally with an avatar) and refreshes the session with the returned user. */
    suspend fun updateProfile(profile: ProfileUpdate, avatar: AvatarUpload?) {
        val current = _state.value as? AuthState.LoggedIn ?: return
        val updated = api.updateProfile(current.server, current.token, current.user.id, profile, avatar)
        applyUpdatedUser(updated)
        if (avatar != null) {
            avatarRevision = UUID.randomUUID().toString()
        }
    }

    /**
     * Re-fetches the user record and rotates the session token (iOS
     * refreshSession), e.g. for pull-to-refresh on the profile page.
     */
    suspend fun refreshSession() {
        val current = _state.value as? AuthState.LoggedIn ?: return
        val auth = api.authRefresh(current.server, current.token)
        // Ignore a response belonging to a session that changed in flight.
        val latest = _state.value as? AuthState.LoggedIn
        if (latest == null || latest.user.id != current.user.id || latest.token != current.token) {
            return
        }
        settings.setAuthToken(auth.token, current.server, auth.record.id)
        _state.value = AuthState.LoggedIn(auth.record, auth.token, current.server)
        avatarRevision = UUID.randomUUID().toString()
    }

    /**
     * After changing the password, signs in again with the new password to get
     * a fresh token. If that fails, signs out and asks the user to sign in with
     * the new password, matching the iOS app.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String) {
        val current = _state.value as? AuthState.LoggedIn ?: return
        api.changePassword(current.server, current.token, current.user.id, currentPassword, newPassword)
        try {
            val auth = api.authWithPassword(current.server, current.user.email, newPassword)
            settings.setAuthToken(auth.token, current.server, auth.record.id)
            _state.value = AuthState.LoggedIn(auth.record, auth.token, current.server)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logout()
            throw ApiException(
                statusCode = 401,
                message = "Your password was updated. Sign in again with your new password.",
            )
        }
    }

    private fun applyUpdatedUser(updated: User) {
        _state.update { state ->
            if (state is AuthState.LoggedIn && state.user.id == updated.id) {
                state.copy(user = updated)
            } else {
                state
            }
        }
    }

    /** Avatar URL of the signed-in user; null when no avatar is set. */
    fun avatarUrl(state: AuthState.LoggedIn): String? {
        val url = api.fileUrl(state.server, state.user.collectionId, state.user.id, state.user.avatar)
            ?: return null
        val revision = avatarRevision ?: return url
        return "$url?revision=$revision"
    }
}
