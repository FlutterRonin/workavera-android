package com.workavera.android.ui.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workavera.android.data.model.ChatMessage
import com.workavera.android.data.model.ChatMessagePart
import com.workavera.android.data.model.ChatMessageRole
import com.workavera.android.data.model.ChatMessageStatus
import com.workavera.android.ui.components.MarkdownContent
import com.workavera.android.ui.notifications.RelativeTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * One chat message: right-aligned bubble for the user, header + parts for the
 * assistant (iOS ChatMessageView).
 */
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    runActive: Boolean,
    submittingApprovalIds: Set<String>,
    onRespondApproval: (String, Boolean) -> Unit,
    resolveAvatar: ToolAvatarResolver,
) {
    val isUser = message.roleEnum == ChatMessageRole.USER
    val status = message.metadata?.statusEnum

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isUser) "You" else "Assistant",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val modelName = message.metadata?.model?.name
            if (!isUser && !modelName.isNullOrEmpty()) {
                HeaderSeparator()
                Text(
                    modelName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val createdAt = message.metadata?.createdAt
            if (!createdAt.isNullOrEmpty()) {
                HeaderSeparator()
                Text(
                    RelativeTime.format(createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (status == ChatMessageStatus.STREAMING) {
                Spacer(Modifier.width(5.dp))
                StreamingBadge()
            }
        }
        Spacer(Modifier.height(7.dp))

        if (isUser) {
            UserMessageBubble(parts = message.parts)
        } else {
            if (message.showsWaitingIndicator) {
                TypingDots()
                Spacer(Modifier.height(8.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                message.parts.forEach { part ->
                    AssistantPartView(
                        part = part,
                        approvalData = message.approvalData(part),
                        runActive = runActive,
                        messageStatus = status,
                        submittingApprovalIds = submittingApprovalIds,
                        onRespondApproval = onRespondApproval,
                        resolveAvatar = resolveAvatar,
                    )
                }
                message.metadata?.error?.let { error ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            error.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            message.metadata?.usage?.let { usage ->
                if (usage.inputTokens > 0 || usage.outputTokens > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "↑ ${usage.inputTokens} in   ↓ ${usage.outputTokens} out",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderSeparator() {
    Text(
        " · ",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Pulsing capsule shown while the assistant message is streaming. */
@Composable
private fun StreamingBadge() {
    val transition = rememberInfiniteTransition(label = "streamingBadge")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    Text(
        "STREAMING",
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .alpha(alpha)
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                CircleShape,
            )
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

/** True while the run is active but nothing visible has arrived yet. */
private val ChatMessage.showsWaitingIndicator: Boolean
    get() {
        if (roleEnum != ChatMessageRole.ASSISTANT) return false
        val status = metadata?.statusEnum
        if (status != ChatMessageStatus.STREAMING && status != ChatMessageStatus.PENDING) {
            return false
        }
        return parts.none { !it.text.isNullOrEmpty() || it.type == "dynamic-tool" }
    }

/** Approval payload of the sibling data-approval part matching a tool call. */
private fun ChatMessage.approvalData(toolPart: ChatMessagePart): JsonElement? {
    if (toolPart.type != "dynamic-tool") return null
    val toolCallId = toolPart.toolCallID ?: return null
    return parts.firstOrNull { part ->
        if (part.type != "data-approval") return@firstOrNull false
        val obj = part.data as? JsonObject ?: return@firstOrNull false
        obj["toolCallId"]?.jsonPrimitive?.contentOrNull == toolCallId
    }?.data
}

/** Three staggered pulsing dots shown while the first token is on its way. */
@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typingDots")
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(3) { index ->
            val progress by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(550, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 160),
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer {
                        scaleX = 0.55f + 0.45f * progress
                        scaleY = scaleX
                        alpha = progress
                    }
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        CircleShape,
                    ),
            )
        }
    }
}

/** Per-part dispatch for assistant messages (iOS AssistantMessagePartView). */
@Composable
private fun AssistantPartView(
    part: ChatMessagePart,
    approvalData: JsonElement?,
    runActive: Boolean,
    messageStatus: ChatMessageStatus?,
    submittingApprovalIds: Set<String>,
    onRespondApproval: (String, Boolean) -> Unit,
    resolveAvatar: ToolAvatarResolver,
) {
    // While streaming, render plain text to avoid re-parsing the whole
    // Markdown document on every token; swap to Markdown once settled.
    val isStreaming = messageStatus == ChatMessageStatus.STREAMING ||
        messageStatus == ChatMessageStatus.PENDING

    when (part.type) {
        "text" -> {
            val text = part.text
            if (!text.isNullOrEmpty()) {
                if (isStreaming) {
                    Text(text, style = MaterialTheme.typography.bodyLarge)
                } else {
                    MarkdownContent(content = text)
                }
            }
        }
        "reasoning" -> {
            val text = part.text
            if (!text.isNullOrEmpty()) {
                ReasoningDisclosure(text = text, isStreaming = isStreaming)
            }
        }
        // Specialized cards for the workspace tools (iOS toolName dispatch);
        // approval-gated tools keep the generic card with its approval flow.
        "dynamic-tool" -> {
            val toolName = part.toolName ?: ""
            when {
                toolName == "system_memory_upsert" || toolName == "system_memory_forget" ->
                    MemoryToolCard(part)
                toolName.startsWith("docs_") -> DocsToolCard(part)
                toolName == "contacts_search" -> ContactsToolCard(part, resolveAvatar)
                toolName.startsWith("board_") && toolName != "board_delete_task" ->
                    BoardToolCard(part, resolveAvatar)
                toolName.startsWith("calendar_") && toolName != "calendar_delete_event" ->
                    CalendarToolCard(part)
                toolName.startsWith("reading_") -> ReadingToolCard(part)
                else -> ChatToolCard(
                    part = part,
                    approvalData = approvalData,
                    runActive = runActive,
                    messageStatus = messageStatus,
                    submittingApprovalIds = submittingApprovalIds,
                    onRespondApproval = onRespondApproval,
                )
            }
        }
        "source-url" -> {
            val url = part.url
            if (!url.isNullOrEmpty()) {
                val uriHandler = LocalUriHandler.current
                Row(
                    modifier = Modifier.clickable { uriHandler.openUri(url) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Link,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        part.title?.takeIf { it.isNotEmpty() } ?: url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        "source-document" -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Description,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                part.title ?: part.filename ?: "Source document",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        "data-compaction" -> CompactionStatus(data = part.data)
        // step-start and data-approval render nothing (approval data is
        // passed into the tool card instead).
        else -> Unit
    }
}

/**
 * Right-aligned bubble for what the user sent, collapsed if it is long
 * (iOS UserMessageBubble).
 *
 * A pasted log or file is easily thousands of lines, and laying all of them
 * out costs the same whether or not they are on screen. Capping the collapsed
 * height caps that work too, so a long message costs what a short one does
 * until the reader asks for the rest.
 */
@Composable
private fun UserMessageBubble(parts: List<ChatMessagePart>) {
    val texts = remember(parts) {
        parts.mapNotNull { part -> part.text?.takeIf { part.type == "text" } }
    }
    // Deliberately a character/line count rather than a measurement: asking
    // for the real height is the work this exists to avoid.
    val isLong = remember(texts) {
        texts.sumOf { it.length } > LONG_MESSAGE_CHARACTERS ||
            texts.sumOf { text -> 1 + text.count { it == '\n' } } > COLLAPSED_LINE_LIMIT
    }
    var expanded by remember(parts) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        texts.forEach { text ->
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = if (expanded || !isLong) Int.MAX_VALUE else COLLAPSED_LINE_LIMIT,
                overflow = if (expanded || !isLong) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
        }
        if (isLong) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (expanded) "Show Less" else "Show More",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) "Show less" else "Show more",
                    modifier = Modifier
                        .size(14.dp)
                        .rotate(if (expanded) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val COLLAPSED_LINE_LIMIT = 12
private const val LONG_MESSAGE_CHARACTERS = 500

/** Collapsible reasoning ("thinking") section (iOS ReasoningDisclosure). */
@Composable
private fun ReasoningDisclosure(text: String, isStreaming: Boolean) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (isStreaming) "Thinking…" else "Thought",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier
                    .size(14.dp)
                    .rotate(if (expanded) 0f else -90f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                // onSurfaceVariant alone lands too close to the body text the
                // reasoning sits directly above, so the two read as one block
                // and the answer is hard to find.
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Context-compaction status line (iOS ChatCompactionStatus). */
@Composable
private fun CompactionStatus(data: JsonElement?) {
    val state = (data as? JsonObject)?.get("state")?.jsonPrimitive?.contentOrNull
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            "started" -> CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
            )
            "failed" -> Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Icon(
                Icons.Outlined.Compress,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(
            when (state) {
                "started" -> "Compacting context…"
                "failed" -> "Context compaction failed — continued with full history"
                else -> "Older messages were compacted into a summary"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
