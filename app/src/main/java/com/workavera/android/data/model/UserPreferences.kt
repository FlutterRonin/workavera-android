package com.workavera.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * App-wide appearance preference, matching the web/iOS Settings appearance card.
 * [SYSTEM] follows the device dark-mode setting.
 */
enum class AppAppearance(val raw: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    /** Resolves to an explicit dark flag, falling back to [systemDark] for [SYSTEM]. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromRaw(raw: String?): AppAppearance =
            entries.firstOrNull { it.raw == raw } ?: SYSTEM
    }
}

/**
 * A record of the PocketBase user_preferences collection (one per user).
 * Holds the theme and chat-memory flags.
 */
@Serializable
data class UserPreferences(
    val id: String,
    val owner: String = "",
    val theme: String = "system",
    @SerialName("memory_enabled") val memoryEnabled: Boolean = false,
    @SerialName("memory_auto_capture") val memoryAutoCapture: Boolean = false,
) {
    val appearance: AppAppearance get() = AppAppearance.fromRaw(theme)
}
