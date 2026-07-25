package com.workavera.android.data

import android.content.Context

/**
 * Remembers the per user + model "Allow AI Data Sharing?" consent, matching
 * the iOS AIConsentStore (UserDefaults there, SharedPreferences here).
 */
class AiConsentRepository(context: Context) {

    private val prefs = context.getSharedPreferences("workavera_ai_consent", Context.MODE_PRIVATE)

    fun isAuthorized(userId: String, modelId: String): Boolean =
        prefs.getBoolean(key(userId, modelId), false)

    fun authorize(userId: String, modelId: String) {
        prefs.edit().putBoolean(key(userId, modelId), true).apply()
    }

    private fun key(userId: String, modelId: String) = "$userId.$modelId"
}
