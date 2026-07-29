package com.workavera.android.ui.chat

import com.workavera.android.data.model.ChatConversation
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatConversationSortTest {
    private fun conversation(
        id: String,
        pinned: Boolean = false,
        lastMessageAt: String = "",
        updated: String = "2026-07-20 00:00:00.000Z",
    ) = ChatConversation(
        id = id,
        lastMessageAt = lastMessageAt,
        pinned = pinned,
        created = "2026-07-01 00:00:00.000Z",
        updated = updated,
    )

    @Test
    fun activeSortRanksPinnedFirstThenLatestMessage() {
        val conversations = listOf(
            conversation("older-message", lastMessageAt = "2026-07-20 08:00:00.000Z"),
            conversation("pinned-quiet", pinned = true, lastMessageAt = "2026-07-01 08:00:00.000Z"),
            conversation("newest-message", lastMessageAt = "2026-07-27 08:00:00.000Z"),
        )

        assertEquals(
            listOf("pinned-quiet", "newest-message", "older-message"),
            conversations.sortedWith(ChatViewModel.activeSort).map { it.id },
        )
    }

    @Test
    fun activeSortRanksConversationsWithoutALastMessageLast() {
        // Matches the server, where an empty date sorts below every dated one.
        val conversations = listOf(
            conversation("undated", updated = "2026-07-28 00:00:00.000Z"),
            conversation("dated", lastMessageAt = "2026-07-20 08:00:00.000Z"),
        )

        assertEquals(
            listOf("dated", "undated"),
            conversations.sortedWith(ChatViewModel.activeSort).map { it.id },
        )
    }

    @Test
    fun activeSortFallsBackToUpdatedWhenMessagesLandTogether() {
        val conversations = listOf(
            conversation(
                "stale",
                lastMessageAt = "2026-07-20 08:00:00.000Z",
                updated = "2026-07-20 08:00:00.000Z",
            ),
            conversation(
                "renamed",
                lastMessageAt = "2026-07-20 08:00:00.000Z",
                updated = "2026-07-21 09:00:00.000Z",
            ),
        )

        assertEquals(
            listOf("renamed", "stale"),
            conversations.sortedWith(ChatViewModel.activeSort).map { it.id },
        )
    }
}
