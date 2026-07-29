package com.workavera.android.data.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatStreamChunkTest {
    @Test
    fun toolLifecycleBoundariesRenderImmediately() {
        val immediateTypes = listOf(
            "tool-input-start",
            "tool-input-available",
            "tool-input-error",
            "tool-approval-request",
            "tool-approval-response",
            "tool-output-available",
            "tool-output-error",
            "tool-output-denied",
            "data-approval",
        )

        immediateTypes.forEach { type ->
            assertTrue(ChatStreamChunk(type = type).requiresImmediateRender, type)
        }

        listOf("start", "text-delta", "reasoning-delta", "tool-input-delta").forEach { type ->
            assertFalse(ChatStreamChunk(type = type).requiresImmediateRender, type)
        }
    }
}
