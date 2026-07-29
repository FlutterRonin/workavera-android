package com.workavera.android.ui.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatTopBarStateTest {
    @Test
    fun createRequestIsConsumedOnlyOnce() {
        val state = ChatTopBarState()

        assertFalse(state.consumeCreateRequest())

        state.requestCreate()
        assertTrue(state.consumeCreateRequest())
        assertFalse(state.consumeCreateRequest())

        state.requestCreate()
        assertTrue(state.consumeCreateRequest())
        assertFalse(state.consumeCreateRequest())
    }
}
