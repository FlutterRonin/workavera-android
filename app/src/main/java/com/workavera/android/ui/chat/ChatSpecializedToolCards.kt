package com.workavera.android.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewColumn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workavera.android.data.model.ChatMessagePart
import com.workavera.android.data.model.EventColor
import com.workavera.android.data.model.EventRecurrenceFrequency
import com.workavera.android.data.model.MemoryOrigin
import com.workavera.android.data.model.ReadingStatus
import com.workavera.android.data.model.TaskPriority
import com.workavera.android.data.model.decodingEmbeddedJson
import com.workavera.android.data.model.parseServerDate
import com.workavera.android.ui.components.AvatarView
import com.workavera.android.ui.notifications.RelativeTime
import com.workavera.android.ui.settings.icon
import com.workavera.android.ui.theme.StatusOnline
import com.workavera.android.ui.theme.color
import com.workavera.android.ui.theme.parseHexColor
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Purpose-built cards for the workspace assistant tools, mirroring the iOS
// MemoryToolCard / DocsToolCard / ContactsToolCard / BoardToolCard /
// CalendarToolCard / ReadingToolCard in ChatView.swift. They share the
// generic ChatToolCard anatomy (12 dp rounded surfaceVariant card, 20 dp
// leading icon, mini progress while running) but render the parsed payload
// instead of raw JSON. Approval-gated tools (board_delete_task,
// calendar_delete_event) stay on the generic card.

/** Resolves a PocketBase file URL for an avatar surfaced by a tool card (iOS auth.fileURL). */
internal typealias ToolAvatarResolver = (collectionId: String?, recordId: String, fileName: String) -> String?

private val ChatMessagePart.isToolWorking: Boolean
    get() = state == "input-streaming" || state == "input-available"

private val ChatMessagePart.isToolError: Boolean get() = state == "output-error"

/** iOS `.tertiary` label. */
private val tertiaryColor: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

/** iOS `.quaternary` label. */
private val quaternaryColor: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

private fun JsonElement?.objectString(key: String): String? =
    (this as? JsonObject)?.get(key)
        ?.let { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }

private fun ChatMessagePart.inputString(key: String): String? = input.objectString(key)

/**
 * Shared card anatomy: header (icon, title, subtitle, mini progress, optional
 * chevron) plus the expanded section. Non-collapsible cards (memory) stay
 * expanded and show a [statusAccessory] instead of the chevron.
 */
@Composable
private fun SpecializedToolCardShell(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    isWorking: Boolean,
    collapsible: Boolean = true,
    statusAccessory: (@Composable () -> Unit)? = null,
    expandedContent: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (collapsible) Modifier.clickable { expanded = !expanded } else Modifier)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = tint,
            )
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isWorking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp,
                )
                Spacer(Modifier.width(6.dp))
            }
            statusAccessory?.invoke()
            if (collapsible) {
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(if (expanded) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!collapsible || expanded) {
            Spacer(Modifier.height(10.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = expandedContent,
            )
        }
    }
}

/** Red tool-call error line shown at the bottom of the expanded section. */
@Composable
private fun ToolErrorText(part: ChatMessagePart) {
    if (part.isToolError) {
        part.errorText?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Small capsule badge (iOS tertiarySystemFill capsules). */
@Composable
private fun ToolBadge(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    background: Color = MaterialTheme.colorScheme.surface,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** While the call runs, previews what it is operating on from the input. */
@Composable
private fun WorkingInputPreview(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Text with the 3 pt colored quote bar used for excerpts and saved content. */
@Composable
private fun QuotedText(
    text: String,
    barColor: Color,
    style: TextStyle,
    color: Color,
    maxLines: Int = Int.MAX_VALUE,
    textDecoration: TextDecoration? = null,
) {
    Row {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(1.5.dp))
                .background(barColor),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textDecoration = textDecoration,
            modifier = Modifier.weight(1f),
        )
    }
}

/** iOS "+N more" truncation note. */
@Composable
private fun MoreNote(count: Int, suffix: String = "") {
    Text(
        "+$count more$suffix",
        style = MaterialTheme.typography.labelSmall,
        color = tertiaryColor,
    )
}

/** iOS empty-result note (caption, secondary). */
@Composable
private fun EmptyNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Per-item mutation outcome row shared by the board / calendar / reading cards. */
@Composable
private fun MutationResultRow(
    ok: Boolean,
    title: String,
    subtitle: String? = null,
    error: String? = null,
) {
    Row {
        Icon(
            if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
            contentDescription = null,
            modifier = Modifier
                .size(14.dp)
                .padding(top = 1.dp),
            tint = if (ok) StatusOnline else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = tertiaryColor,
                )
            }
            if (!error.isNullOrEmpty()) {
                Text(
                    error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 6 dp colored dot (state / priority), falling back to gray like iOS. */
@Composable
private fun ColoredDot(color: Color, size: androidx.compose.ui.unit.Dp = 6.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

private fun hexColorOrGray(hex: String?): Color =
    hex?.let { parseHexColor(it, Color.Gray) } ?: Color.Gray

// ---- Memory (iOS MemoryToolCard) ----

/**
 * `system_memory_upsert` / `system_memory_forget`. Always expanded because the
 * memory content is the point of the tool call; removals are red and the
 * forgotten content is struck through.
 */
@Composable
internal fun MemoryToolCard(part: ChatMessagePart) {
    val isForget = part.toolName == "system_memory_forget"
    val payload = part.memoryToolPayload()
    val isWorking = part.isToolWorking
    val isError = part.isToolError
    val isRemoval = isForget || payload?.action == MemoryToolAction.FORGOTTEN

    // Monochrome by default; red only for failures and removals.
    val tint = if (isError || isRemoval) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    val title = when {
        isError -> "Memory action failed"
        isWorking -> if (isForget) "Forgetting memory…" else "Saving memory…"
        else -> when (payload?.action) {
            MemoryToolAction.FORGOTTEN -> "Memory forgotten"
            MemoryToolAction.UNDONE -> "Memory change undone"
            MemoryToolAction.UNCHANGED -> "Already remembered"
            MemoryToolAction.UPDATED -> "Memory updated"
            MemoryToolAction.CREATED ->
                if (payload.origin == MemoryOrigin.AUTOMATIC) {
                    "Remembered automatically"
                } else {
                    "Memory saved"
                }
            null -> if (isForget) "Memory forgotten" else "Memory saved"
        }
    }

    val subtitle = run {
        val parts = mutableListOf<String>()
        payload?.category?.let { parts.add(it.label) }
        if (!isRemoval) payload?.origin?.let { parts.add(it.label) }
        if (parts.isEmpty()) "Memory" else parts.joinToString(" · ")
    }

    val categoryIcon = payload?.category?.icon
    val icon = when {
        isError -> Icons.Outlined.Warning
        isRemoval -> Icons.Outlined.Delete
        payload?.action == MemoryToolAction.UNDONE -> Icons.AutoMirrored.Outlined.Undo
        categoryIcon != null -> categoryIcon
        else -> Icons.Outlined.Psychology
    }

    SpecializedToolCardShell(
        icon = icon,
        tint = tint,
        title = title,
        subtitle = subtitle,
        isWorking = isWorking,
        collapsible = false,
        statusAccessory = {
            if (!isWorking) {
                Icon(
                    if (isError) Icons.Outlined.Cancel else Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (isError) MaterialTheme.colorScheme.error else StatusOnline,
                )
            }
        },
    ) {
        val content = payload?.content
        if (!content.isNullOrEmpty()) {
            SelectionContainer {
                QuotedText(
                    text = content,
                    barColor = tint.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isRemoval || payload?.action == MemoryToolAction.UNDONE) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (payload?.action == MemoryToolAction.FORGOTTEN) {
                        TextDecoration.LineThrough
                    } else {
                        null
                    },
                )
            }
        }
        ToolErrorText(part)
    }
}

// ---- Docs (iOS DocsToolCard) ----

private data class DocsToolMeta(val label: String, val icon: ImageVector, val working: String)

private fun docsToolMeta(toolName: String): DocsToolMeta = when (toolName) {
    "docs_search" -> DocsToolMeta("Search Documents", Icons.Outlined.Search, "Searching…")
    "docs_get" -> DocsToolMeta("Read Document", Icons.Outlined.Description, "Loading…")
    "docs_upsert" -> DocsToolMeta("Save Document", Icons.Outlined.Edit, "Saving…")
    "docs_move" -> DocsToolMeta("Move Documents", Icons.Outlined.Folder, "Moving…")
    "docs_replace" -> DocsToolMeta("Replace Text", Icons.Outlined.FindReplace, "Replacing…")
    "docs_write_chunk" -> DocsToolMeta("Write Document", Icons.Outlined.Edit, "Writing…")
    "docs_list_folders" -> DocsToolMeta("Document Folders", Icons.Outlined.Folder, "Loading…")
    else -> DocsToolMeta("Document Tool", Icons.Outlined.InsertDriveFile, "Working…")
}

private const val DOCS_SEARCH_ROW_LIMIT = 4
private const val DOCS_MOVE_ROW_LIMIT = 5

/** Collapsed card for the `docs_*` tools; expanding reveals the affected documents. */
@Composable
internal fun DocsToolCard(part: ChatMessagePart) {
    val toolName = part.toolName ?: ""
    val meta = docsToolMeta(toolName)
    val payload = part.docsToolPayload()
    val isWorking = part.isToolWorking
    val isError = part.isToolError
    val tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    val subtitle = run subtitle@{
        when {
            isWorking -> meta.working
            isError -> "Failed"
            payload == null -> "Completed"
            else -> when (toolName) {
                "docs_search" ->
                    if (payload.documents.size == 1) "1 document" else "${payload.documents.size} documents"
                "docs_get" -> payload.documents.firstOrNull()?.title ?: "Completed"
                "docs_upsert" -> {
                    val doc = payload.documents.firstOrNull() ?: return@subtitle "Completed"
                    val revision = doc.revision?.let { " · R$it" } ?: ""
                    when {
                        payload.isCreation -> "Created$revision"
                        payload.changed == false -> "No changes$revision"
                        else -> "Saved$revision"
                    }
                }
                "docs_replace" -> {
                    val matches = payload.matches?.let { if (it == 1) "1 match" else "$it matches" } ?: "Done"
                    matches + (payload.documents.firstOrNull()?.revision?.let { " · R$it" } ?: "")
                }
                "docs_move" -> "Moved ${payload.moveSucceeded} of ${payload.moveTotal}"
                "docs_write_chunk" -> {
                    val parts = mutableListOf<String>()
                    payload.chunkContentLength?.let { parts.add("Wrote $it characters") }
                    payload.chunkRevision?.let { parts.add("R$it") }
                    if (parts.isEmpty()) "Completed" else parts.joinToString(" · ")
                }
                "docs_list_folders" ->
                    if (payload.folders.size == 1) "1 folder" else "${payload.folders.size} folders"
                else -> "Completed"
            }
        }
    }

    SpecializedToolCardShell(
        icon = meta.icon,
        tint = tint,
        title = meta.label,
        subtitle = subtitle,
        isWorking = isWorking,
    ) {
        if (isWorking) {
            docsInputSummary(part, toolName)?.let { WorkingInputPreview(meta.icon, it) }
        }
        if (payload != null) {
            when (toolName) {
                "docs_search" -> DocsDocumentList(payload.documents)
                "docs_get", "docs_upsert", "docs_replace" ->
                    payload.documents.firstOrNull()?.let { DocsDocumentDetail(it, toolName, payload) }
                "docs_move" -> DocsMoveResultsList(payload.moveResults)
                "docs_write_chunk" -> DocsChunkDetail(part, payload)
                "docs_list_folders" -> DocsFolderList(payload.folders)
            }
        }
        ToolErrorText(part)
    }
}

/** While the call runs, preview what it is operating on from the input. */
private fun docsInputSummary(part: ChatMessagePart, toolName: String): String? = when (toolName) {
    "docs_search" -> part.inputString("query")
    "docs_upsert" -> part.inputString("title")
    "docs_replace" -> part.inputString("find")
    else -> null
}

private fun docsMetaLine(doc: DocsToolDocument): String {
    val parts = mutableListOf<String>()
    doc.location?.let { parts.add(it) }
    doc.revision?.let { parts.add("R$it") }
    doc.updated?.takeIf { it.isNotEmpty() && parseServerDate(it) != null }
        ?.let { parts.add(RelativeTime.format(it)) }
    return parts.joinToString(" · ")
}

@Composable
private fun DocsDocumentList(documents: List<DocsToolDocument>) {
    if (documents.isEmpty()) {
        EmptyNote("No matching documents")
    } else {
        documents.take(DOCS_SEARCH_ROW_LIMIT).forEach { DocsDocumentRow(it) }
        if (documents.size > DOCS_SEARCH_ROW_LIMIT) {
            MoreNote(documents.size - DOCS_SEARCH_ROW_LIMIT)
        }
    }
}

@Composable
private fun DocsDocumentRow(doc: DocsToolDocument) {
    Row {
        Icon(
            if (doc.isHtml) Icons.Outlined.Code else Icons.Outlined.Description,
            contentDescription = null,
            modifier = Modifier
                .size(14.dp)
                .padding(top = 1.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                doc.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            doc.excerpt?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val metaLine = docsMetaLine(doc)
            if (metaLine.isNotEmpty()) {
                Text(
                    metaLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = tertiaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DocsDocumentDetail(doc: DocsToolDocument, toolName: String, payload: DocsToolPayload) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                doc.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            val badge = when (toolName) {
                "docs_upsert" -> when {
                    payload.isCreation -> "Created"
                    payload.changed == false -> "No changes"
                    else -> "Saved"
                }
                "docs_replace" ->
                    payload.matches?.let { if (it == 1) "1 match" else "$it matches" }
                else -> null
            }
            badge?.let {
                Spacer(Modifier.width(8.dp))
                ToolBadge(it)
            }
        }
        val metaLine = docsMetaLine(doc)
        if (metaLine.isNotEmpty()) {
            Text(
                metaLine,
                style = MaterialTheme.typography.labelSmall,
                color = tertiaryColor,
            )
        }
        doc.excerpt?.takeIf { it.isNotEmpty() }?.let {
            QuotedText(
                text = it,
                barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
            )
        }
    }
}

@Composable
private fun DocsChunkDetail(part: ChatMessagePart, payload: DocsToolPayload) {
    // The written text itself comes from the tool input; the output only
    // carries the resulting revision and total content length.
    part.inputString("content")?.takeIf { it.isNotEmpty() }?.let { content ->
        QuotedText(
            text = content,
            barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
        )
    }
    val parts = mutableListOf<String>()
    payload.chunkRevision?.let { parts.add("R$it") }
    payload.chunkContentLength?.let { parts.add("$it characters") }
    if (parts.isNotEmpty()) {
        Text(
            parts.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = tertiaryColor,
        )
    }
}

@Composable
private fun DocsMoveResultsList(results: List<DocsToolMoveResult>) {
    results.take(DOCS_MOVE_ROW_LIMIT).forEach { result ->
        Row {
            Icon(
                if (result.ok) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                contentDescription = null,
                modifier = Modifier
                    .size(14.dp)
                    .padding(top = 1.dp),
                tint = if (result.ok) StatusOnline else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    result.title ?: "Document",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                result.error?.takeIf { it.isNotEmpty() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            result.destination?.let {
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = tertiaryColor,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = tertiaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    if (results.size > DOCS_MOVE_ROW_LIMIT) {
        MoreNote(results.size - DOCS_MOVE_ROW_LIMIT)
    }
}

@Composable
private fun DocsFolderList(folders: List<DocsToolFolder>) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        folders.forEach { folder ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    folder.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---- Contacts (iOS ContactsToolCard) ----

private const val CONTACTS_ROW_LIMIT = 4

/** Collapsed card for the read-only `contacts_search` tool. */
@Composable
internal fun ContactsToolCard(part: ChatMessagePart, resolveAvatar: ToolAvatarResolver) {
    val payload = part.contactsToolPayload()
    val isWorking = part.isToolWorking
    val isError = part.isToolError
    val tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    val subtitle = when {
        isWorking -> "Searching…"
        isError -> "Failed"
        payload == null -> "Completed"
        payload.contacts.isEmpty() -> "No contacts found"
        payload.contacts.size == 1 -> "1 contact"
        else -> "${payload.contacts.size} contacts"
    }

    SpecializedToolCardShell(
        icon = Icons.Outlined.Group,
        tint = tint,
        title = "Search Contacts",
        subtitle = subtitle,
        isWorking = isWorking,
    ) {
        if (isWorking) {
            part.inputString("query")?.takeIf { it.isNotEmpty() }?.let {
                WorkingInputPreview(Icons.Outlined.Search, it)
            }
        }
        if (payload != null) {
            if (payload.contacts.isEmpty() && !isError) {
                EmptyNote("No contacts found")
            }
            payload.contacts.take(CONTACTS_ROW_LIMIT).forEach { contact ->
                ContactsToolRow(contact, resolveAvatar)
            }
            if (payload.contacts.size > CONTACTS_ROW_LIMIT) {
                MoreNote(payload.contacts.size - CONTACTS_ROW_LIMIT)
            }
        }
        ToolErrorText(part)
    }
}

@Composable
private fun ContactsToolRow(contact: ContactsToolContact, resolveAvatar: ToolAvatarResolver) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AvatarView(
            url = contact.avatarFile?.let { resolveAvatar(contact.collectionID, contact.id, it) },
            name = contact.name,
            size = 24.dp,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            contact.name.ifEmpty { "Unknown" },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        contact.displayTitle?.let {
            Spacer(Modifier.width(6.dp))
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = tertiaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Spacer(Modifier.weight(1f))
        contact.presence?.let { status ->
            ColoredDot(status.color, size = 7.dp)
        }
    }
}

// ---- Board (iOS BoardToolCard) ----

private data class BoardToolMeta(val label: String, val icon: ImageVector, val working: String)

private fun boardToolMeta(toolName: String): BoardToolMeta = when (toolName) {
    "board_search_projects" -> BoardToolMeta("Search Projects", Icons.Outlined.Layers, "Searching…")
    "board_get_project" -> BoardToolMeta("Project Details", Icons.Outlined.Layers, "Loading…")
    "board_search_tasks" -> BoardToolMeta("Search Tasks", Icons.Outlined.Checklist, "Searching…")
    "board_list_templates" -> BoardToolMeta("Project Templates", Icons.Outlined.GridView, "Loading…")
    "board_create_project" -> BoardToolMeta("Create Project", Icons.Outlined.Add, "Creating…")
    "board_update_project" -> BoardToolMeta("Update Project", Icons.Outlined.Edit, "Updating…")
    "board_upsert_state" -> BoardToolMeta("Upsert States", Icons.Outlined.ViewColumn, "Saving…")
    "board_upsert_label" -> BoardToolMeta("Upsert Labels", Icons.Outlined.Label, "Saving…")
    "board_upsert_member" -> BoardToolMeta("Upsert Members", Icons.Outlined.Group, "Saving…")
    "board_create_task" -> BoardToolMeta("Create Tasks", Icons.Outlined.Add, "Creating…")
    "board_update_task" -> BoardToolMeta("Update Tasks", Icons.Outlined.Edit, "Updating…")
    else -> BoardToolMeta("Board Tool", Icons.Outlined.Layers, "Working…")
}

private const val BOARD_ROW_LIMIT = 4

/**
 * Collapsed card for the `board_*` tools (projects, tasks, templates and
 * mutations). `board_delete_task` is approval-gated and stays on the generic
 * tool card, which renders the approve/reject flow.
 */
@Composable
internal fun BoardToolCard(part: ChatMessagePart, resolveAvatar: ToolAvatarResolver) {
    val toolName = part.toolName ?: ""
    val meta = boardToolMeta(toolName)
    val payload = part.boardToolPayload()
    val isWorking = part.isToolWorking
    val isError = part.isToolError
    val tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    val subtitle = when {
        isWorking -> meta.working
        isError -> "Failed"
        payload == null -> "Completed"
        else -> when (toolName) {
            "board_search_projects" ->
                if (payload.projects.size == 1) "1 project" else "${payload.projects.size} projects"
            "board_get_project" -> payload.projects.firstOrNull()?.name ?: "Completed"
            "board_search_tasks" ->
                if (payload.tasks.size == 1) "1 task" else "${payload.tasks.size} tasks"
            "board_list_templates" ->
                if (payload.templates.size == 1) "1 template" else "${payload.templates.size} templates"
            else ->
                if (payload.isBatchMutation) {
                    "${payload.mutationSucceeded} of ${payload.mutationTotal} succeeded"
                } else {
                    payload.mutationItems.firstOrNull()?.detail ?: "Completed"
                }
        }
    }

    SpecializedToolCardShell(
        icon = meta.icon,
        tint = tint,
        title = meta.label,
        subtitle = subtitle,
        isWorking = isWorking,
    ) {
        if (isWorking) {
            part.inputString("query")?.takeIf { it.isNotEmpty() }?.let {
                WorkingInputPreview(Icons.Outlined.Search, it)
            }
        }
        if (payload != null) {
            when (toolName) {
                "board_search_projects", "board_get_project" ->
                    BoardProjectList(payload.projects)
                "board_search_tasks" -> BoardTaskList(payload.tasks, resolveAvatar)
                "board_list_templates" -> BoardTemplateList(payload.templates)
                else -> BoardMutationList(payload.mutationItems)
            }
        }
        ToolErrorText(part)
    }
}

@Composable
private fun BoardProjectList(projects: List<BoardToolProject>) {
    if (projects.isEmpty()) {
        EmptyNote("No matching projects")
    }
    projects.take(BOARD_ROW_LIMIT).forEach { project ->
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    project.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (project.archived) {
                    Spacer(Modifier.width(6.dp))
                    ToolBadge("Archived")
                }
                project.role?.let {
                    Spacer(Modifier.width(6.dp))
                    ToolBadge(it.replaceFirstChar(Char::uppercase))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (project.taskCount == 1) "1 task" else "${project.taskCount} tasks",
                    style = MaterialTheme.typography.labelSmall,
                    color = tertiaryColor,
                )
            }
            project.displayDescription?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (project.states.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    project.states.forEach { state ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ColoredDot(hexColorOrGray(state.displayColor))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                state.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            state.taskCount?.let {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "$it",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tertiaryColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (projects.size > BOARD_ROW_LIMIT) {
        MoreNote(projects.size - BOARD_ROW_LIMIT)
    }
}

@Composable
private fun BoardTaskList(tasks: List<BoardToolTask>, resolveAvatar: ToolAvatarResolver) {
    if (tasks.isEmpty()) {
        EmptyNote("No matching tasks")
    }
    tasks.take(BOARD_ROW_LIMIT).forEach { task ->
        Row {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                task.displayDescription?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    task.stateName?.let { name ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ColoredDot(hexColorOrGray(task.stateColor))
                            Spacer(Modifier.width(4.dp))
                            Text(name, style = MaterialTheme.typography.labelSmall, color = tertiaryColor)
                        }
                    }
                    task.taskPriority?.takeIf { it != TaskPriority.NONE }?.let { priority ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ColoredDot(priority.color)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                priority.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = tertiaryColor,
                            )
                        }
                    }
                    task.displayDueDate?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (task.isOverdue) MaterialTheme.colorScheme.error else tertiaryColor,
                        )
                    }
                }
                task.projectName?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = quaternaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (task.assignees.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                    task.assignees.take(3).forEach { assignee ->
                        AvatarView(
                            url = assignee.avatarFile?.let {
                                resolveAvatar(assignee.collectionID, assignee.id, it)
                            },
                            name = assignee.name,
                            size = 20.dp,
                            modifier = Modifier.border(
                                1.5.dp,
                                MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape,
                            ),
                        )
                    }
                }
            }
        }
    }
    if (tasks.size > BOARD_ROW_LIMIT) {
        MoreNote(tasks.size - BOARD_ROW_LIMIT)
    }
}

@Composable
private fun BoardTemplateList(templates: List<BoardToolTemplate>) {
    if (templates.isEmpty()) {
        EmptyNote("No templates available")
    }
    templates.take(BOARD_ROW_LIMIT).forEach { template ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                template.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            template.displayDescription?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${template.stateCount} states · ${template.labelCount} labels",
                style = MaterialTheme.typography.labelSmall,
                color = tertiaryColor,
            )
        }
    }
    if (templates.size > BOARD_ROW_LIMIT) {
        MoreNote(templates.size - BOARD_ROW_LIMIT)
    }
}

@Composable
private fun BoardMutationList(items: List<BoardToolMutationItem>) {
    items.take(BOARD_ROW_LIMIT).forEach { item ->
        MutationResultRow(
            ok = item.ok,
            title = item.name ?: "Record ${item.index + 1}",
            subtitle = item.detail?.takeIf { it.isNotEmpty() }?.replaceFirstChar(Char::uppercase),
            error = item.error,
        )
    }
    if (items.size > BOARD_ROW_LIMIT) {
        MoreNote(items.size - BOARD_ROW_LIMIT)
    }
}

// ---- Calendar (iOS CalendarToolCard) ----

private data class CalendarToolMeta(val label: String, val icon: ImageVector, val working: String)

private fun calendarToolMeta(toolName: String): CalendarToolMeta = when (toolName) {
    "calendar_get_schedule" -> CalendarToolMeta("Schedule", Icons.Outlined.CalendarMonth, "Loading…")
    "calendar_create_event" ->
        CalendarToolMeta("Create Events", Icons.Outlined.EventAvailable, "Creating…")
    "calendar_update_event" -> CalendarToolMeta("Update Events", Icons.Outlined.Edit, "Updating…")
    else -> CalendarToolMeta("Calendar Tool", Icons.Outlined.CalendarMonth, "Working…")
}

private const val CALENDAR_DAY_ROW_LIMIT = 3
private const val CALENDAR_MUTATION_ROW_LIMIT = 4

private val calendarDayParser = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
private val calendarDayHeaderFormat = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
private val calendarTimeFormat = DateTimeFormatter
    .ofLocalizedTime(FormatStyle.SHORT)
    .withLocale(Locale.getDefault())
    .withZone(ZoneId.systemDefault())

/**
 * Collapsed card for `calendar_get_schedule`, `calendar_create_event` and
 * `calendar_update_event`. `calendar_delete_event` is approval-gated and
 * stays on the generic tool card.
 */
@Composable
internal fun CalendarToolCard(part: ChatMessagePart) {
    val toolName = part.toolName ?: ""
    val meta = calendarToolMeta(toolName)
    val payload = part.calendarToolPayload()
    val isWorking = part.isToolWorking
    val isError = part.isToolError
    val tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    val subtitle = when {
        isWorking -> meta.working
        isError -> "Failed"
        payload == null -> "Completed"
        toolName == "calendar_get_schedule" -> {
            val total = payload.days.sumOf { it.events.size + it.tasks.size }
            when (total) {
                0 -> "Nothing scheduled"
                1 -> "1 item"
                else -> "$total items"
            }
        }
        payload.isBatchMutation -> "${payload.mutationSucceeded} of ${payload.mutationTotal} succeeded"
        else -> payload.mutationItems.firstOrNull()?.action?.replaceFirstChar(Char::uppercase)
            ?: "Completed"
    }

    SpecializedToolCardShell(
        icon = meta.icon,
        tint = tint,
        title = meta.label,
        subtitle = subtitle,
        isWorking = isWorking,
    ) {
        if (payload != null) {
            if (toolName == "calendar_get_schedule") {
                CalendarScheduleView(payload)
            } else {
                CalendarMutationList(payload.mutationItems)
            }
        }
        ToolErrorText(part)
    }
}

@Composable
private fun CalendarScheduleView(payload: CalendarToolPayload) {
    if (payload.days.all { it.events.isEmpty() && it.tasks.isEmpty() }) {
        EmptyNote("Nothing scheduled")
    }
    payload.days.forEach { day ->
        if (day.events.isNotEmpty() || day.tasks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CalendarDayHeader(day.date)
                day.events.take(CALENDAR_DAY_ROW_LIMIT).forEach { CalendarEventRow(it) }
                if (day.events.size > CALENDAR_DAY_ROW_LIMIT) {
                    MoreNote(day.events.size - CALENDAR_DAY_ROW_LIMIT, suffix = " events")
                }
                day.tasks.take(CALENDAR_DAY_ROW_LIMIT).forEach { CalendarScheduleTaskRow(it) }
                if (day.tasks.size > CALENDAR_DAY_ROW_LIMIT) {
                    MoreNote(day.tasks.size - CALENDAR_DAY_ROW_LIMIT, suffix = " tasks")
                }
            }
        }
    }
}

@Composable
private fun CalendarDayHeader(date: String) {
    val day = runCatching { LocalDate.parse(date, calendarDayParser) }.getOrNull()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            day?.format(calendarDayHeaderFormat) ?: date,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (day != null && day == LocalDate.now()) {
            ToolBadge(
                "Today",
                color = MaterialTheme.colorScheme.primary,
                background = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            )
        }
    }
}

@Composable
private fun CalendarEventRow(event: CalendarToolEvent) {
    Row {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(1.5.dp))
                .background(
                    event.eventColor?.let { parseHexColor(it.hex, MaterialTheme.colorScheme.primary) }
                        ?: MaterialTheme.colorScheme.primary,
                ),
        )
        Spacer(Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    event.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                event.recurrence?.takeIf { it != EventRecurrenceFrequency.NONE }?.let {
                    Text(
                        it.label,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                if (event.allDay) "All day" else calendarTimeRange(event),
                style = MaterialTheme.typography.labelSmall,
                color = tertiaryColor,
            )
            event.displayLocation?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Place,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = tertiaryColor,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = tertiaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun calendarTimeRange(event: CalendarToolEvent): String {
    val start = event.start?.let(calendarTimeFormat::format) ?: ""
    val end = event.end?.let(calendarTimeFormat::format) ?: ""
    if (start.isEmpty() || end.isEmpty()) return start + end
    return "$start – $end"
}

@Composable
private fun CalendarScheduleTaskRow(task: CalendarToolScheduleTask) {
    Row {
        Icon(
            if (task.completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            modifier = Modifier
                .size(14.dp)
                .padding(top = 1.dp),
            tint = if (task.completed) StatusOnline else tertiaryColor,
        )
        Spacer(Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (task.completed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                task.taskPriority?.takeIf { it != TaskPriority.NONE }?.let { priority ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ColoredDot(priority.color)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            priority.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = tertiaryColor,
                        )
                    }
                }
                task.displayDueDate?.let {
                    Text(
                        "Due $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = tertiaryColor,
                    )
                }
                task.displayProjectName?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = tertiaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarMutationList(items: List<CalendarToolMutationItem>) {
    items.take(CALENDAR_MUTATION_ROW_LIMIT).forEach { item ->
        MutationResultRow(
            ok = item.ok,
            title = item.title ?: "Event ${item.index + 1}",
            subtitle = item.action?.replaceFirstChar(Char::uppercase),
            error = item.error,
        )
    }
    if (items.size > CALENDAR_MUTATION_ROW_LIMIT) {
        MoreNote(items.size - CALENDAR_MUTATION_ROW_LIMIT)
    }
}

// ---- Reading (iOS ReadingToolCard) ----

private data class ReadingToolMeta(val label: String, val icon: ImageVector, val working: String)

private fun readingToolMeta(toolName: String): ReadingToolMeta = when (toolName) {
    "reading_search" ->
        ReadingToolMeta("Reading List", Icons.AutoMirrored.Outlined.MenuBook, "Searching…")
    "reading_upsert" -> ReadingToolMeta("Save Reading Items", Icons.Outlined.Edit, "Saving…")
    "reading_get" -> ReadingToolMeta("Reading Item", Icons.Outlined.Description, "Loading…")
    "reading_summarize" ->
        ReadingToolMeta("Summarize Reading", Icons.Outlined.AutoAwesome, "Summarizing…")
    else -> ReadingToolMeta("Reading Tool", Icons.AutoMirrored.Outlined.MenuBook, "Working…")
}

private const val READING_ROW_LIMIT = 4

/** Status dot colors, matching the iOS ReadingStatus.dotColor extension. */
private val ReadingStatus.dotColor: Color
    get() = when (this) {
        ReadingStatus.UNREAD -> Color(0xFF3B82F6)
        ReadingStatus.READ -> Color(0xFF22C55E)
        ReadingStatus.ARCHIVED -> Color(0xFF64748B)
    }

/** Collapsed card for the `reading_*` tools (search, item detail, summarize, upsert). */
@Composable
internal fun ReadingToolCard(part: ChatMessagePart) {
    val toolName = part.toolName ?: ""
    val meta = readingToolMeta(toolName)
    val payload = part.readingToolPayload()
    val isWorking = part.isToolWorking
    val isError = part.isToolError
    val tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    val subtitle = when {
        isWorking -> meta.working
        isError -> "Failed"
        payload == null -> "Completed"
        payload.plainText != null -> "Failed"
        else -> when (toolName) {
            "reading_search" ->
                when {
                    payload.items.isEmpty() -> "No items found"
                    payload.items.size == 1 -> "1 item"
                    else -> "${payload.items.size} items"
                }
            "reading_get" -> payload.items.firstOrNull()?.title ?: "Completed"
            "reading_summarize" -> if (payload.items.isEmpty()) "Completed" else "Summary ready"
            else -> "${payload.mutationSucceeded} of ${payload.mutationTotal} succeeded"
        }
    }

    SpecializedToolCardShell(
        icon = meta.icon,
        tint = tint,
        title = meta.label,
        subtitle = subtitle,
        isWorking = isWorking,
    ) {
        if (isWorking) {
            part.inputString("query")?.takeIf { it.isNotEmpty() }?.let {
                WorkingInputPreview(Icons.Outlined.Search, it)
            }
        }
        if (payload != null) {
            val plainText = payload.plainText
            if (plainText != null) {
                Text(
                    plainText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                when (toolName) {
                    "reading_search" -> ReadingSearchList(payload.items)
                    "reading_get", "reading_summarize" ->
                        payload.items.firstOrNull()?.let { ReadingItemDetail(it) }
                    else -> ReadingMutationList(payload.mutationItems)
                }
            }
        }
        ToolErrorText(part)
    }
}

@Composable
private fun ReadingSearchList(items: List<ReadingToolItem>) {
    if (items.isEmpty()) {
        EmptyNote("No reading items found")
    }
    items.take(READING_ROW_LIMIT).forEach { ReadingItemRow(it) }
    if (items.size > READING_ROW_LIMIT) {
        MoreNote(items.size - READING_ROW_LIMIT)
    }
}

@Composable
private fun ReadingItemRow(item: ReadingToolItem) {
    Row {
        ColoredDot(item.readingStatus?.dotColor ?: Color.Gray, size = 7.dp)
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            (item.displaySummary ?: item.displayDescription)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ReadingMetaLine(item)
        }
    }
}

@Composable
private fun ReadingItemDetail(item: ReadingToolItem) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColoredDot(item.readingStatus?.dotColor ?: Color.Gray, size = 7.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ReadingMetaLine(item)
        val summary = item.displaySummary
        if (summary == null) {
            item.displayDescription?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Summary",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                item.keyPoints.forEach { point ->
                    Row {
                        Text(
                            "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = tertiaryColor,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            point,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingMetaLine(item: ReadingToolItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item.host?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Link,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = tertiaryColor,
                )
                Spacer(Modifier.width(3.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = tertiaryColor)
            }
        }
        item.readingStatus?.let {
            Text(it.label, style = MaterialTheme.typography.labelSmall, color = tertiaryColor)
        }
        if (item.tags.isNotEmpty()) {
            Text(
                item.tags.joinToString(" ") { "#$it" },
                style = MaterialTheme.typography.labelSmall,
                color = tertiaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReadingMutationList(items: List<ReadingToolMutationItem>) {
    items.take(READING_ROW_LIMIT).forEach { item ->
        MutationResultRow(
            ok = item.ok,
            title = item.title ?: "Item ${item.index + 1}",
            error = item.error,
        )
    }
    if (items.size > READING_ROW_LIMIT) {
        MoreNote(items.size - READING_ROW_LIMIT)
    }
}
