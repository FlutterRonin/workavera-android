package com.workavera.android.data

import com.workavera.android.data.model.AppAppearance
import com.workavera.android.data.network.ApiException
import com.workavera.android.data.network.PocketBaseApi
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Chat-memory flags plus the preferences record id, loaded from the server. */
data class MemoryPreferences(
    val id: String? = null,
    val memoryEnabled: Boolean = false,
    val memoryAutoCapture: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

/**
 * Bridges the server user_preferences record with the local appearance cache.
 * Appearance is written to [SettingsRepository] first (so the theme changes
 * instantly and survives offline, like the iOS fallbackAppearance) and then
 * synced to the server; the memory flags live only on the server.
 */
class PreferencesRepository(
    private val api: PocketBaseApi,
    private val authRepository: AuthRepository,
    private val settings: SettingsRepository,
) {

    private val _memory = MutableStateFlow(MemoryPreferences())
    val memory: StateFlow<MemoryPreferences> = _memory.asStateFlow()

    private fun session(): AuthState.LoggedIn? =
        authRepository.state.value as? AuthState.LoggedIn

    /** Fetches the server preferences, updating the local appearance and memory flags. */
    suspend fun load() {
        val session = session() ?: return
        try {
            val prefs = api.getPreferences(session.server, session.token, session.user.id)
            settings.setAppearance(prefs.appearance)
            _memory.value = MemoryPreferences(
                id = prefs.id,
                memoryEnabled = prefs.memoryEnabled,
                memoryAutoCapture = prefs.memoryAutoCapture,
                loaded = true,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _memory.value = _memory.value.copy(error = errorMessage(e))
        }
    }

    /** Applies the appearance locally at once, then syncs it to the server (throws on failure). */
    suspend fun updateAppearance(appearance: AppAppearance) {
        settings.setAppearance(appearance)
        val session = session() ?: return
        val id = ensurePreferencesId(session) ?: return
        api.updatePreferences(session.server, session.token, id, theme = appearance)
    }

    /** Optimistically flips the switch, then syncs the server; reverts and rethrows on failure. */
    suspend fun updateMemoryEnabled(enabled: Boolean) {
        val session = session() ?: return
        val id = ensurePreferencesId(session) ?: return
        val previous = _memory.value
        _memory.value = previous.copy(memoryEnabled = enabled)
        try {
            val prefs = api.updatePreferences(session.server, session.token, id, memoryEnabled = enabled)
            applyServerPreferences(id, prefs.memoryEnabled, prefs.memoryAutoCapture)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _memory.value = previous
            throw e
        }
    }

    /** Optimistically flips the switch, then syncs the server; reverts and rethrows on failure. */
    suspend fun updateMemoryAutoCapture(enabled: Boolean) {
        val session = session() ?: return
        val id = ensurePreferencesId(session) ?: return
        val previous = _memory.value
        _memory.value = previous.copy(memoryAutoCapture = enabled)
        try {
            val prefs = api.updatePreferences(session.server, session.token, id, memoryAutoCapture = enabled)
            applyServerPreferences(id, prefs.memoryEnabled, prefs.memoryAutoCapture)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _memory.value = previous
            throw e
        }
    }

    /** Returns the cached preferences id, loading it once if not yet known. */
    private suspend fun ensurePreferencesId(session: AuthState.LoggedIn): String? {
        _memory.value.id?.let { return it }
        val prefs = api.getPreferences(session.server, session.token, session.user.id)
        settings.setAppearance(prefs.appearance)
        applyServerPreferences(prefs.id, prefs.memoryEnabled, prefs.memoryAutoCapture)
        return prefs.id
    }

    private fun applyServerPreferences(id: String, memoryEnabled: Boolean, memoryAutoCapture: Boolean) {
        _memory.value = MemoryPreferences(
            id = id,
            memoryEnabled = memoryEnabled,
            memoryAutoCapture = memoryAutoCapture,
            loaded = true,
        )
    }

    private fun errorMessage(e: Exception): String =
        (e as? ApiException)?.userMessage ?: e.message ?: "Could not load preferences."
}
