package com.workavera.android.ui.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ToolParameterFormatterTest {
    @Test
    fun summaryDoesNotSerializeLargeString() {
        val value = JsonObject(
            mapOf(
                "content" to JsonPrimitive("<p>large</p>".repeat(1_000)),
                "title" to JsonPrimitive("Plan"),
            ),
        )

        val summary = ToolParameterFormatter.summary(value)

        assertTrue(summary.contains("content: 12000 chars"))
        assertTrue(summary.contains("""title: "Plan""""))
        assertFalse(summary.contains("<p>large</p>"))
    }

    @Test
    fun previewBoundsStringsArraysObjectsAndDepth() {
        val deep = JsonObject(
            mapOf(
                "level1" to JsonObject(
                    mapOf(
                        "level2" to JsonObject(
                            mapOf(
                                "level3" to JsonObject(
                                    mapOf(
                                        "level4" to JsonObject(
                                            mapOf("hidden" to JsonPrimitive("value")),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val value = JsonObject(
            mapOf(
                "html" to JsonPrimitive("x".repeat(3_000)),
                "items" to JsonArray((0..<15).map { JsonPrimitive(it) }),
                "fields" to JsonObject(
                    (0..<40).associate { "key-$it" to JsonPrimitive(true) },
                ),
                "deep" to deep,
            ),
        )

        val preview = ToolParameterFormatter.preview(value)

        assertTrue(preview.truncated)
        assertTrue(
            preview.text.length <=
                ToolParameterFormatter.MAX_PREVIEW_CHARACTERS +
                "\n… preview shortened".length,
        )
        assertTrue(preview.text.contains("1000 more chars"))
        assertTrue(preview.text.contains("5 more items"))
        assertTrue(preview.text.contains("10 more fields"))
        assertTrue(preview.text.contains("hidden at depth limit"))
    }
}
