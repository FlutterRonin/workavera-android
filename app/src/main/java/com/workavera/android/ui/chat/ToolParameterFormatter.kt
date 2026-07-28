package com.workavera.android.ui.chat

import com.workavera.android.data.model.prettyPrintedJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal data class ToolParameterPreview(
    val text: String,
    val truncated: Boolean,
)

/**
 * Product-wide limits for tool parameter previews. These intentionally match
 * the web and iOS clients.
 */
internal object ToolParameterFormatter {
    const val MAX_PREVIEW_CHARACTERS = 12_000
    const val MAX_STRING_CHARACTERS = 2_000
    const val MAX_ARRAY_ITEMS = 10
    const val MAX_OBJECT_KEYS = 30
    const val MAX_DEPTH = 4
    const val MAX_NODES = 120

    fun summary(value: JsonElement): String = when (value) {
        is JsonObject -> {
            if (value.isEmpty()) {
                ""
            } else {
                val keys = value.keys.sorted()
                val fields = keys.take(4)
                    .map { key -> "$key: ${summarizeValue(value.getValue(key))}" }
                    .toMutableList()
                if (keys.size > fields.size) {
                    fields += "+${keys.size - fields.size} fields"
                }
                fields.joinToString(" · ")
            }
        }
        is JsonArray -> "${value.size} items"
        is JsonPrimitive ->
            if (value.isString) "${value.content.length} chars" else value.toString()
    }

    fun preview(value: JsonElement): ToolParameterPreview {
        val context = PreviewContext()
        val limited = limitedValue(value, depth = 0, context)
        var text = limited.prettyPrintedJson()
        if (text.length > MAX_PREVIEW_CHARACTERS) {
            text = text.take(MAX_PREVIEW_CHARACTERS) + "\n… preview shortened"
            context.truncated = true
        }
        return ToolParameterPreview(text, context.truncated)
    }

    fun fullText(value: JsonElement): String = value.prettyPrintedJson()

    private class PreviewContext {
        var nodes = 0
        var truncated = false
    }

    private fun summarizeValue(value: JsonElement): String = when (value) {
        is JsonObject -> "${value.size} fields"
        is JsonArray -> "${value.size} items"
        is JsonPrimitive -> when {
            !value.isString -> value.toString()
            value.content.length > 48 -> "${value.content.length} chars"
            else -> value.toString()
        }
    }

    private fun limitedValue(
        value: JsonElement,
        depth: Int,
        context: PreviewContext,
    ): JsonElement {
        if (context.nodes >= MAX_NODES) {
            context.truncated = true
            return JsonPrimitive("… preview node limit reached")
        }
        context.nodes++

        return when (value) {
            is JsonPrimitive -> {
                val string = value.takeIf { it.isString }?.content
                if (string != null && string.length > MAX_STRING_CHARACTERS) {
                    context.truncated = true
                    JsonPrimitive(
                        string.take(MAX_STRING_CHARACTERS) +
                            "… [${string.length - MAX_STRING_CHARACTERS} more chars]",
                    )
                } else {
                    value
                }
            }

            is JsonArray -> {
                if (depth >= MAX_DEPTH) {
                    context.truncated = true
                    JsonPrimitive("[Array(${value.size}) hidden at depth limit]")
                } else {
                    val visible = value.take(MAX_ARRAY_ITEMS)
                        .map { limitedValue(it, depth + 1, context) }
                        .toMutableList()
                    if (value.size > visible.size) {
                        context.truncated = true
                        visible += JsonPrimitive("… ${value.size - visible.size} more items")
                    }
                    JsonArray(visible)
                }
            }

            is JsonObject -> {
                if (depth >= MAX_DEPTH) {
                    context.truncated = true
                    JsonPrimitive("[Object with ${value.size} fields hidden at depth limit]")
                } else {
                    val keys = value.keys.sorted()
                    val visibleKeys = keys.take(MAX_OBJECT_KEYS)
                    val visible = visibleKeys.associateWith {
                        limitedValue(value.getValue(it), depth + 1, context)
                    }.toMutableMap()
                    if (keys.size > visibleKeys.size) {
                        context.truncated = true
                        visible["…"] = JsonPrimitive("${keys.size - visibleKeys.size} more fields")
                    }
                    JsonObject(visible)
                }
            }
        }
    }
}
