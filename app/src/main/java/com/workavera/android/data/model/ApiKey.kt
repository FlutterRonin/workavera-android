package com.workavera.android.data.model

import com.workavera.android.data.network.PocketBaseApi
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A record of the PocketBase api_keys collection (the secret itself is never returned). */
@Serializable
data class ApiKey(
    val id: String,
    val name: String = "",
    val prefix: String = "",
    @SerialName("allow_destructive") val allowDestructive: Boolean = false,
    val expires: String = "",
    @SerialName("last_used") val lastUsed: String = "",
    val created: String = "",
) {
    val expiresAt: Instant? get() = parseServerDate(expires)
    val lastUsedAt: Instant? get() = parseServerDate(lastUsed)
    val isExpired: Boolean get() = expiresAt?.isBefore(Instant.now()) == true
}

/**
 * The one-time response when creating a key: this is the only time the full
 * plaintext [key] is returned; the server stores only its hash.
 */
@Serializable
data class CreatedApiKey(
    val id: String,
    val key: String,
    val prefix: String = "",
    val name: String = "",
    val allowDestructive: Boolean = false,
)

/** Expiration presets offered when creating a key, matching the iOS APIKeyExpiration. */
enum class ApiKeyExpiration(val label: String, val days: Int?) {
    NEVER("Never expires", null),
    SEVEN_DAYS("7 days", 7),
    THIRTY_DAYS("30 days", 30),
    NINETY_DAYS("90 days", 90),
    ONE_YEAR("1 year", 365);

    /** ISO-8601 expiry for the request body; empty string means "never expires". */
    fun requestValue(now: Instant = Instant.now()): String {
        val dayCount = days ?: return ""
        return PocketBaseApi.formatServerInstant(now.plus(dayCount.toLong(), ChronoUnit.DAYS))
    }
}
