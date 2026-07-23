package com.workavera.android.data

import android.content.Context
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Disk cache for API responses (same idea as the iOS APIResponseCache):
 * keys are namespaced as "module|server|userId[|page]" to isolate accounts,
 * SHA-256 of the key is the file name, and all read/write failures are
 * silent — the cache is only an accelerator and must never affect the main flow.
 */
class ResponseCache(
    context: Context,
    private val json: Json,
) {

    private val dir = File(context.cacheDir, "api_responses")

    fun <T> read(key: String, serializer: KSerializer<T>): T? {
        val file = file(key)
        if (!file.exists()) return null
        return try {
            json.decodeFromString(serializer, file.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun <T> write(key: String, serializer: KSerializer<T>, value: T) {
        try {
            dir.mkdirs()
            file(key).writeText(json.encodeToString(serializer, value))
        } catch (_: Exception) {
            // Ignore disk-full / IO errors; the next network refresh writes again.
        }
    }

    private fun file(key: String): File = File(dir, sha256(key) + ".json")

    private fun sha256(key: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
