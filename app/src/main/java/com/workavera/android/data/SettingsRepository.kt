package com.workavera.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.workavera.android.data.model.AppAppearance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "workavera_settings")

/**
 * Local settings: server address, last sign-in email, session token.
 * The token records which server it belongs to, so switching servers never
 * reuses a stale token (mirrors the iOS practice of naming Keychain accounts
 * per server).
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val SERVER_ADDRESS = stringPreferencesKey("server_address")
        val LAST_LOGIN_EMAIL = stringPreferencesKey("last_login_email")
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val AUTH_TOKEN_SERVER = stringPreferencesKey("auth_token_server")
        val APPEARANCE = stringPreferencesKey("appearance")
    }

    val serverAddress: Flow<String> =
        context.dataStore.data.map { it[Keys.SERVER_ADDRESS] ?: "" }

    /**
     * Persisted appearance, so the theme applies instantly at launch (before the
     * server preferences load) and offline — mirroring the iOS fallbackAppearance.
     */
    val appearance: Flow<AppAppearance> =
        context.dataStore.data.map { AppAppearance.fromRaw(it[Keys.APPEARANCE]) }

    val lastLoginEmail: Flow<String> =
        context.dataStore.data.map { it[Keys.LAST_LOGIN_EMAIL] ?: "" }

    val authToken: Flow<String> =
        context.dataStore.data.map { it[Keys.AUTH_TOKEN] ?: "" }

    val authTokenServer: Flow<String> =
        context.dataStore.data.map { it[Keys.AUTH_TOKEN_SERVER] ?: "" }

    suspend fun setServerAddress(address: String) {
        context.dataStore.edit { it[Keys.SERVER_ADDRESS] = address.trim() }
    }

    suspend fun setLastLoginEmail(email: String) {
        context.dataStore.edit { it[Keys.LAST_LOGIN_EMAIL] = email.trim() }
    }

    suspend fun setAppearance(appearance: AppAppearance) {
        context.dataStore.edit { it[Keys.APPEARANCE] = appearance.raw }
    }

    suspend fun setAuthToken(token: String, server: String) {
        context.dataStore.edit {
            it[Keys.AUTH_TOKEN] = token
            it[Keys.AUTH_TOKEN_SERVER] = server
        }
    }

    suspend fun clearAuthToken() {
        context.dataStore.edit {
            it.remove(Keys.AUTH_TOKEN)
            it.remove(Keys.AUTH_TOKEN_SERVER)
        }
    }
}
