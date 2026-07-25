package com.workavera.android.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.workavera.android.data.model.ChatConversation
import com.workavera.android.data.model.ChatMessage
import com.workavera.android.data.model.ChatMessageRole
import com.workavera.android.data.model.ChatTokenUsage
import com.workavera.android.data.model.LlmTokenFormat
import com.workavera.android.ui.components.ErrorBanner
import com.workavera.android.ui.components.LoadingIndicator
import com.workavera.android.ui.theme.PriorityHigh

/**
 * Conversation detail, mirroring the iOS ChatConversationDetailView: message
 * history pinned to the bottom while the user hasn't scrolled up, a composer
 * with model picker / context gauge / send-stop button, and the per-model
 * AI data-sharing consent gate.
 */
@Composable
fun ChatConversationView(
    conversationId: String,
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val ui = viewModel.uiState
    val conversation = ui.conversations.firstOrNull { it.id == conversationId }
    val messages = ui.messages(conversationId)
    val isLoadingMessages = conversationId in ui.loadingMessageConversationIds
    val isGenerating = ui.isGenerating(conversationId)

    var draft by rememberSaveable { mutableStateOf("") }
    var selectedModelId by rememberSaveable { mutableStateOf("") }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var pendingConsent by remember { mutableStateOf<PendingAIConsent?>(null) }

    BackHandler(onBack = onBack)

    LaunchedEffect(conversationId) {
        viewModel.loadModelsIfNeeded()
        viewModel.loadMessages(conversationId)
    }
    // Pick the conversation's model, else the default, else the first (iOS chooseInitialModel).
    LaunchedEffect(ui.models, conversation?.modelConfigID) {
        if (selectedModelId.isEmpty() || ui.models.none { it.id == selectedModelId }) {
            selectedModelId = viewModel.preferredModel(conversation)?.id ?: ""
        }
    }

    if (conversation == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            DetailHeader(title = "Chat", onBack = onBack, actions = {})
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Conversation Unavailable",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "It may have been archived or deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    fun send(content: String, modelId: String) {
        draft = ""
        viewModel.sendMessage(content, conversationId, modelId)
    }

    fun submit() {
        val content = draft.trim()
        val model = ui.models.firstOrNull { it.id == selectedModelId } ?: return
        if (content.isEmpty() || isGenerating) return
        if (!viewModel.isAiConsentAuthorized(model.id)) {
            pendingConsent = PendingAIConsent(
                content = content,
                modelId = model.id,
                modelAddress = model.baseUrl,
            )
            return
        }
        send(content, model.id)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        DetailHeader(
            title = conversation.title,
            onBack = onBack,
            actions = {
                DetailMenu(
                    conversation = conversation,
                    enabled = conversation.id !in ui.mutatingConversationIds,
                    onTogglePin = { viewModel.togglePin(conversation) },
                    onRename = { showRename = true },
                    onArchive = {
                        viewModel.archive(conversation) { ok -> if (ok) onBack() }
                    },
                    onDelete = { showDelete = true },
                )
            },
        )

        Box(modifier = Modifier.weight(1f)) {
            when {
                isLoadingMessages && messages.isEmpty() ->
                    LoadingIndicator("Loading messages…")
                messages.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "No Messages Yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Send a message below to start the conversation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> MessageList(
                    messages = messages,
                    conversationId = conversationId,
                    viewModel = viewModel,
                )
            }
        }

        ChatComposer(
            draft = draft,
            onDraftChange = { draft = it },
            models = ui.models,
            selectedModelId = selectedModelId,
            onSelectModel = { selectedModelId = it },
            contextInfo = ChatContextInfo.derive(
                messages = messages,
                conversation = conversation,
                maxTokens = ui.models.firstOrNull { it.id == selectedModelId }?.maxContextTokens ?: 0,
            ),
            isLoading = isLoadingMessages,
            isGenerating = isGenerating,
            onSend = ::submit,
            onStop = { viewModel.stopRun(conversationId) },
        )
    }

    if (showRename) {
        var renameDraft by remember { mutableStateOf(conversation.title) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename conversation") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = { Text("Title") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.rename(conversation, renameDraft)
                        showRename = false
                    },
                    enabled = renameDraft.trim().isNotEmpty(),
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancel") }
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete conversation?") },
            text = { Text("This permanently deletes the conversation and all of its messages.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(conversation) { ok -> if (ok) onBack() }
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            },
        )
    }

    pendingConsent?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingConsent = null },
            title = { Text("Allow AI Data Sharing?") },
            text = {
                Text(
                    "To generate a response, your message and relevant conversation or " +
                        "workspace content will be sent to this AI model:\n\n${pending.modelAddress}",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.authorizeAiConsent(pending.modelId)
                        pendingConsent = null
                        send(pending.content, pending.modelId)
                    },
                ) {
                    Text("Allow and Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConsent = null }) { Text("Not Now") }
            },
        )
    }
}

private data class PendingAIConsent(
    val content: String,
    val modelId: String,
    val modelAddress: String,
)

@Composable
private fun DetailHeader(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
private fun DetailMenu(
    conversation: ChatConversation,
    enabled: Boolean,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuExpanded = true }, enabled = enabled) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "Conversation actions")
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(if (conversation.pinned) "Unpin" else "Pin") },
                leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onTogglePin()
                },
            )
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text("Archive") },
                leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onArchive()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    menuExpanded = false
                    onDelete()
                },
            )
        }
    }
}

/**
 * Message history with bottom pinning: while the user is at (or near) the
 * end, new tokens keep the list scrolled down; scrolling up releases it
 * (iOS bottom-sentinel behavior).
 */
@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    conversationId: String,
    viewModel: ChatViewModel,
) {
    val ui = viewModel.uiState
    val listState = rememberLazyListState()

    val isPinnedToBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 2
        }
    }

    // Jump to the end once the history first appears.
    var didInitialScroll by remember { mutableStateOf(false) }
    LaunchedEffect(messages.isNotEmpty()) {
        if (!didInitialScroll && messages.isNotEmpty()) {
            didInitialScroll = true
            listState.scrollToItem(messages.size - 1)
        }
    }

    val last = messages.lastOrNull()
    val scrollSignature = "${messages.size}:${last?.id}:${last?.parts?.size}:" +
        (last?.parts?.sumOf { it.text?.length ?: 0 } ?: 0)
    LaunchedEffect(scrollSignature) {
        if (didInitialScroll && isPinnedToBottom && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp,
            vertical = 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            Box(modifier = Modifier.widthIn(max = 760.dp)) {
                ChatMessageItem(
                    message = message,
                    runActive = message.metadata?.runID != null &&
                        message.metadata?.runID == ui.activeRunIds[conversationId],
                    submittingApprovalIds = ui.submittingApprovalIds,
                    onRespondApproval = { approvalId, approved ->
                        viewModel.respondToApproval(conversationId, approvalId, approved)
                    },
                    resolveAvatar = viewModel::toolFileUrl,
                )
            }
        }
        ui.messageErrors[conversationId]?.let { error ->
            item {
                ErrorBanner(
                    message = error,
                    onRetry = { viewModel.loadMessages(conversationId, force = true) },
                )
            }
        }
    }
}

/** Composer with model picker, context gauge and send/stop button (iOS ChatComposer). */
@Composable
private fun ChatComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    models: List<com.workavera.android.data.model.LlmModel>,
    selectedModelId: String,
    onSelectModel: (String) -> Unit,
    contextInfo: ChatContextInfo?,
    isLoading: Boolean,
    isGenerating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val canSend = !isLoading &&
        !isGenerating &&
        draft.trim().isNotEmpty() &&
        selectedModelId.isNotEmpty()

    Column(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        TextField(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = {
                Text(
                    if (models.isEmpty()) {
                        "Configure a model to start chatting"
                    } else {
                        "Send a message…"
                    },
                )
            },
            enabled = !isLoading && !isGenerating && models.isNotEmpty(),
            maxLines = 6,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            var modelMenuExpanded by remember { mutableStateOf(false) }
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            enabled = models.isNotEmpty() && !isGenerating,
                            onClick = { modelMenuExpanded = true },
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Memory,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = models.firstOrNull { it.id == selectedModelId }?.name
                            ?: if (models.isEmpty()) "No model" else "Select model",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        Icons.Outlined.UnfoldMore,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = modelMenuExpanded,
                    onDismissRequest = { modelMenuExpanded = false },
                ) {
                    models.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.name) },
                            onClick = {
                                modelMenuExpanded = false
                                onSelectModel(model.id)
                            },
                        )
                    }
                }
            }

            if (contextInfo != null && contextInfo.maxTokens > 0 && contextInfo.usedTokens > 0) {
                Spacer(Modifier.width(10.dp))
                ContextGauge(info = contextInfo)
            }

            Spacer(Modifier.weight(1f))

            if (isGenerating) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .clickable(onClick = onStop),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Stop,
                        contentDescription = "Stop response",
                        modifier = Modifier.size(16.dp),
                        tint = Color.White,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        )
                        .clickable(enabled = canSend, onClick = onSend),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.ArrowUpward,
                        contentDescription = "Send message",
                        modifier = Modifier.size(16.dp),
                        tint = if (canSend) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/**
 * Snapshot of how much of the model's context window the conversation
 * currently occupies, plus accumulated usage (iOS ChatContextInfo).
 */
internal data class ChatContextInfo(
    val usedTokens: Int,
    val maxTokens: Int,
    val estimated: Boolean,
    val usage: ChatTokenUsage?,
    val totalInputTokens: Int,
    val totalOutputTokens: Int,
) {
    val usedFraction: Float
        get() = if (maxTokens > 0) {
            (usedTokens.toFloat() / maxTokens).coerceAtMost(1f)
        } else {
            0f
        }

    val percentText: String
        get() {
            if (maxTokens <= 0) return "—"
            val percent = usedTokens.toDouble() / maxTokens * 100
            val value = if (percent >= 10) {
                "${percent.toInt()}"
            } else {
                "%.1f".format(percent)
            }
            return "${if (estimated) "~" else ""}$value%"
        }

    companion object {
        /** Occupancy from the newest assistant message that reported usage, else the conversation snapshot. */
        fun derive(
            messages: List<ChatMessage>,
            conversation: ChatConversation?,
            maxTokens: Int,
        ): ChatContextInfo {
            val totalInput = conversation?.inputTokens ?: 0
            val totalOutput = conversation?.outputTokens ?: 0

            for (message in messages.asReversed()) {
                if (message.roleEnum != ChatMessageRole.ASSISTANT) continue
                val metadata = message.metadata ?: continue
                if (metadata.usage == null && metadata.contextTokens == null) continue
                val usage = metadata.contextUsage ?: metadata.usage
                val used = metadata.contextTokens
                    ?: usage?.let { it.inputTokens + it.outputTokens }
                    ?: 0
                return ChatContextInfo(
                    usedTokens = used,
                    maxTokens = maxTokens,
                    estimated = metadata.contextTokensEstimated == true,
                    usage = usage,
                    totalInputTokens = totalInput,
                    totalOutputTokens = totalOutput,
                )
            }

            return ChatContextInfo(
                usedTokens = conversation?.contextTokens ?: 0,
                maxTokens = maxTokens,
                estimated = false,
                usage = null,
                totalInputTokens = totalInput,
                totalOutputTokens = totalOutput,
            )
        }
    }
}

/** Context-window usage ring with a details dialog (iOS ContextGauge + ContextDetails). */
@Composable
private fun ContextGauge(info: ChatContextInfo) {
    var showDetails by remember { mutableStateOf(false) }

    val ringColor = when {
        info.usedFraction < 0.75f -> MaterialTheme.colorScheme.primary
        info.usedFraction < 0.9f -> PriorityHigh
        else -> MaterialTheme.colorScheme.error
    }
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { showDetails = true }
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            info.percentText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(5.dp))
        Canvas(modifier = Modifier.size(15.dp)) {
            drawCircle(color = trackColor, style = Stroke(width = 2.dp.toPx()))
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * info.usedFraction,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) { Text("Done") }
            },
            title = { Text("Context usage") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            info.percentText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${LlmTokenFormat.display(info.usedTokens)} / " +
                                LlmTokenFormat.display(info.maxTokens),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    info.usage?.let { usage ->
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(10.dp))
                        ContextDetailRow("Cache hit", LlmTokenFormat.display(usage.cacheReadTokens))
                        ContextDetailRow(
                            "Cache write",
                            LlmTokenFormat.display(usage.cacheCreationTokens),
                        )
                    }
                    if (info.maxTokens > 0) {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(10.dp))
                        ContextDetailRow(
                            "Compacts at",
                            "${LlmTokenFormat.display((info.maxTokens * 0.75).toInt())} (75%)",
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))
                    ContextDetailRow("Total input", LlmTokenFormat.display(info.totalInputTokens))
                    ContextDetailRow("Total output", LlmTokenFormat.display(info.totalOutputTokens))
                }
            },
        )
    }
}

@Composable
private fun ContextDetailRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
