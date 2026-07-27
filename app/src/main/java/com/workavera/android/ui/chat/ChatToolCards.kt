package com.workavera.android.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.workavera.android.data.model.ChatMessagePart
import com.workavera.android.data.model.ChatMessageStatus
import com.workavera.android.data.model.decodingEmbeddedJson
import com.workavera.android.data.model.prettyPrintedJson
import com.workavera.android.ui.theme.StatusOnline
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Generic tool-call card with expandable Input/Output JSON and the approval
 * flow (iOS ChatToolCard). Specialized cards (memory, docs, board, …) are
 * routed before this one in the part dispatch.
 */
@Composable
fun ChatToolCard(
    part: ChatMessagePart,
    approvalData: JsonElement?,
    runActive: Boolean,
    messageStatus: ChatMessageStatus?,
    submittingApprovalIds: Set<String>,
    onRespondApproval: (String, Boolean) -> Unit,
) {
    val approvalObject = approvalData as? JsonObject
    val approvalId = approvalObject?.get("approvalId")?.jsonPrimitive?.contentOrNull
    val awaitingApproval = part.state == "approval-requested" &&
        runActive &&
        messageStatus != ChatMessageStatus.ERROR &&
        messageStatus != ChatMessageStatus.CANCELLED

    // The approve and reject buttons live in the collapsed region, so a card
    // that still needs a decision has to start expanded or the run silently
    // stalls with no visible way to act. Derived instead of initialised once so
    // that a card which only becomes actionable later also expands. An explicit
    // tap always wins.
    var manualExpanded by remember { mutableStateOf<Boolean?>(null) }
    val needsAttention = awaitingApproval || part.state == "approval-responded"
    val expanded = manualExpanded ?: needsAttention
    val confirmationIsDestructive = approvalObject
        ?.get("presentation")?.let { it as? JsonObject }
        ?.get("confirmVariant")?.jsonPrimitive?.contentOrNull == "destructive"
    val confirmationLabel = approvalObject
        ?.get("presentation")?.let { it as? JsonObject }
        ?.get("confirmLabel")?.jsonPrimitive?.contentOrNull ?: "Approve"

    // The tool's own result, when it reports one. Approval-gated tools answer
    // with {ok, action, reason}, which is how a denial and a business failure
    // are told apart from a plain success.
    //
    // Read defensively with `as?` rather than `jsonPrimitive`, which throws on
    // a non-primitive: a tool result can come from a third-party MCP server and
    // is not required to use these keys, let alone these types.
    val resultObject = part.output as? JsonObject
    fun resultField(key: String) = resultObject?.get(key) as? JsonPrimitive
    val isDenied = part.approval?.approved == false ||
        resultField("action")?.contentOrNull == "denied" ||
        resultField("reason")?.contentOrNull == "user_denied"
    val isBusinessFailure = part.state == "output-available" &&
        resultField("ok")?.booleanOrNull == false &&
        !isDenied
    val isFailed = part.state == "output-error" || isBusinessFailure
    val isCompleted = part.state == "output-available" && !isDenied && !isBusinessFailure

    val isWorking = part.state in setOf("input-streaming", "input-available", "approval-responded")
    val isError = part.state == "output-error"
    val tint = when {
        isError -> MaterialTheme.colorScheme.error
        part.state == "output-available" -> StatusOnline
        confirmationIsDestructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { manualExpanded = !expanded }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when {
                    awaitingApproval && confirmationIsDestructive -> Icons.Outlined.Warning
                    awaitingApproval -> Icons.Outlined.VerifiedUser
                    part.state == "output-available" -> Icons.Outlined.CheckCircle
                    isError -> Icons.Outlined.Cancel
                    else -> Icons.Outlined.Build
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = tint,
            )
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    approvalObject?.get("title")?.jsonPrimitive?.contentOrNull
                        ?: displayToolName(part.toolName),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    stateLabel(part, awaitingApproval),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isWorking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp,
                )
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier
                    .size(16.dp)
                    .rotate(if (expanded) 180f else 0f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                approvalObject?.get("summary")?.jsonPrimitive?.contentOrNull?.let { summary ->
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                part.input?.let { input ->
                    JsonSection(title = "Input", value = input.decodingEmbeddedJson())
                }
                part.output?.let { output ->
                    JsonSection(title = "Output", value = output.decodingEmbeddedJson())
                }
                part.errorText?.let { errorText ->
                    Text(
                        errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                // Once a decision is made, explain what happened in the tool's
                // own words rather than leaving the user to read the raw result
                // JSON. Mirrors the web approval card.
                if (approvalObject != null) {
                    val presentation = approvalObject["presentation"] as? JsonObject
                    fun message(key: String, fallback: String) =
                        (presentation?.get(key) as? JsonPrimitive)?.contentOrNull ?: fallback

                    when {
                        part.state == "approval-responded" &&
                            part.approval?.approved == true &&
                            !isCompleted && !isFailed ->
                            OutcomeText(
                                message("pendingMessage", "Applying approved action…"),
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                        isDenied ->
                            OutcomeText(
                                message("deniedMessage", "Action cancelled."),
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                        isCompleted ->
                            OutcomeText(message("successMessage", "Action completed."), StatusOnline)

                        // An explicit errorText is already rendered above; this
                        // covers a tool that failed reporting ok=false without one.
                        isFailed && part.errorText == null ->
                            OutcomeText(
                                message(
                                    "failureMessage",
                                    "The approved action could not be completed.",
                                ),
                                MaterialTheme.colorScheme.error,
                            )
                    }
                }
                if (awaitingApproval && approvalId != null) {
                    val submitting = approvalId in submittingApprovalIds
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        OutlinedButton(
                            onClick = { onRespondApproval(approvalId, false) },
                            enabled = !submitting,
                        ) {
                            Text("Reject")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onRespondApproval(approvalId, true) },
                            enabled = !submitting,
                            colors = if (confirmationIsDestructive) {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                )
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                        ) {
                            Text(confirmationLabel)
                        }
                    }
                }
            }
        }
    }
}

internal fun displayToolName(toolName: String?): String =
    (toolName ?: "Workspace tool")
        .replace("_", " ")
        .split(" ")
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

/** One line describing what happened to an approval-gated action. */
@Composable
private fun OutcomeText(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

private fun stateLabel(part: ChatMessagePart, awaitingApproval: Boolean): String =
    when (part.state) {
        "input-streaming" -> "Preparing…"
        "input-available" -> "Running…"
        "approval-requested" -> if (awaitingApproval) "Awaiting approval" else "Approval expired"
        "approval-responded" ->
            if (part.approval?.approved == false) "Rejected" else "Applying…"
        "output-available" -> "Completed"
        "output-error" -> "Failed"
        else -> part.state ?: "Tool call"
    }

/**
 * Pretty-printed JSON well with a copy button; above [PREVIEW_LIMIT] chars
 * the payload is truncated (the full value stays available via copy).
 */
@Composable
internal fun JsonSection(title: String, value: JsonElement) {
    val clipboard = LocalClipboardManager.current
    val fullText = value.prettyPrintedJson()
    val isTruncated = fullText.length > PREVIEW_LIMIT
    val displayText = if (isTruncated) {
        fullText.take(PREVIEW_LIMIT) + "\n… (${fullText.length - PREVIEW_LIMIT} more characters)"
    } else {
        fullText
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = "Copy $title",
                modifier = Modifier
                    .size(14.dp)
                    .clickable { clipboard.setText(AnnotatedString(fullText)) },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            displayText,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .horizontalScroll(rememberScrollState())
                .padding(8.dp),
        )
    }
}

private const val PREVIEW_LIMIT = 600
