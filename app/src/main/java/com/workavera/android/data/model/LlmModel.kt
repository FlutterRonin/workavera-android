package com.workavera.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** LLM provider protocol offered when configuring a model. */
enum class LlmProtocol(val raw: String, val label: String, val details: String, val defaultBaseUrl: String) {
    OPENAI("openai", "OpenAI", "Chat Completions API", "https://api.openai.com/v1"),
    OPENAI_COMPATIBLE("openai-compatible", "OpenAI compatible", "Chat Completions API", "https://api.openai.com/v1"),
    ANTHROPIC("anthropic", "Anthropic", "Messages API", "https://api.anthropic.com"),
    GOOGLE("google", "Google", "Gemini generateContent API", "https://generativelanguage.googleapis.com/");

    companion object {
        fun fromRaw(raw: String?): LlmProtocol =
            entries.firstOrNull { it.raw == raw } ?: OPENAI

        val defaultBaseUrls: Set<String> get() = entries.map { it.defaultBaseUrl }.toSet()
    }
}

/**
 * An LLM model configuration returned by the custom /api/llm/models endpoint
 * (camelCase JSON, with the secret key replaced by a [hasApiKey] flag).
 */
@Serializable
data class LlmModel(
    val id: String,
    val name: String = "",
    @SerialName("modelId") val modelId: String = "",
    @SerialName("baseUrl") val baseUrl: String = "",
    @SerialName("protocol") val protocolRaw: String = "openai",
    val maxOutputTokens: Int = 0,
    val maxContextTokens: Int = 256_000,
    val isDefault: Boolean = false,
    @SerialName("sharedFrom") val sharedFrom: String = "",
    val sharedFromName: String = "",
    @SerialName("hasApiKey") val hasApiKey: Boolean = false,
    val created: String = "",
    val updated: String = "",
) {
    val protocol: LlmProtocol get() = LlmProtocol.fromRaw(protocolRaw)
    val isSharedCopy: Boolean get() = sharedFrom.isNotEmpty()
    val effectiveOutputTokens: Int get() = if (maxOutputTokens > 0) maxOutputTokens else 16_384
    val effectiveContextTokens: Int get() = if (maxContextTokens > 0) maxContextTokens else 256_000
}

/** Create/update payload. A null [apiKey] keeps the saved key; "" clears it. */
@Serializable
data class LlmModelInput(
    val name: String,
    @SerialName("modelId") val modelId: String,
    @SerialName("baseUrl") val baseUrl: String,
    @SerialName("protocol") val protocol: String,
    val maxOutputTokens: Int,
    val maxContextTokens: Int,
    val apiKey: String? = null,
)

/** A user a model configuration can be shared with. */
@Serializable
data class LlmShareTarget(
    val id: String,
    val name: String = "",
)

/** Formats a token count as e.g. 16k / 256k / 1M. */
object LlmTokenFormat {
    fun display(value: Int): String = when {
        value >= 1_000_000 -> compact(value / 1_000_000.0) + "M"
        value >= 1_000 -> compact(value / 1_000.0) + "k"
        else -> value.toString()
    }

    private fun compact(value: Double): String {
        val rounded = (value * 10).let { Math.round(it) } / 10.0
        return if (rounded == Math.floor(rounded)) rounded.toInt().toString()
        else String.format("%.1f", rounded)
    }

    /** Parses "16384", "256k", or "1m" into a positive Int, or null if invalid. */
    fun parse(raw: String): Int? {
        val normalized = raw.trim().lowercase().replace(",", "")
        if (normalized.isEmpty()) return null
        val (numberPart, multiplier) = when {
            normalized.endsWith("k") -> normalized.dropLast(1) to 1_000.0
            normalized.endsWith("m") -> normalized.dropLast(1) to 1_000_000.0
            else -> normalized to 1.0
        }
        val parsed = numberPart.toDoubleOrNull() ?: return null
        val result = parsed * multiplier
        if (result <= 0 || result > Int.MAX_VALUE || result != Math.floor(result)) return null
        return result.toInt()
    }
}
