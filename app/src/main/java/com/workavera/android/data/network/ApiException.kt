package com.workavera.android.data.network

/**
 * Unified network-layer exception. statusCode 0 means no HTTP response was
 * received at all (bad address / no network / certificate issues, etc.).
 */
class ApiException(
    val statusCode: Int,
    override val message: String,
    val fieldMessages: Map<String, String> = emptyMap(),
    cause: Throwable? = null,
) : Exception(message, cause) {

    val isAuthFailure: Boolean get() = statusCode == 401 || statusCode == 403

    /** Readable message including the first field-level error, matching the iOS APIError presentation. */
    val userMessage: String
        get() {
            val first = fieldMessages.entries.sortedBy { it.key }.firstOrNull()
                ?: return message
            return "$message ${first.key}: ${first.value}"
        }
}
