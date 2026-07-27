package com.workavera.android.ui.chat

import com.workavera.android.data.model.ChatMessagePart
import com.workavera.android.data.model.DocumentKind
import com.workavera.android.data.model.EventColor
import com.workavera.android.data.model.EventRecurrenceFrequency
import com.workavera.android.data.model.MemoryCategory
import com.workavera.android.data.model.MemoryOrigin
import com.workavera.android.data.model.ReadingStatus
import com.workavera.android.data.model.TaskPriority
import com.workavera.android.data.model.UserStatus
import com.workavera.android.data.model.decodingEmbeddedJson
import com.workavera.android.data.model.parseServerDate
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull

// Parsed views of the specialized assistant tool calls, mirroring the iOS
// *ToolPayload.swift models. Output shapes are heterogeneous (arrays, bare
// documents, batch envelopes), so the per-tool envelopes are navigated
// imperatively and only the regular leaf shapes are @Serializable; elements
// are decoded one by one so a malformed entry is dropped, like the iOS
// compactMap parsing.

private val payloadJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

private inline fun <reified T> JsonElement?.decode(): T? =
    this?.let { runCatching { payloadJson.decodeFromJsonElement<T>(it) }.getOrNull() }

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

// ---- Memory (iOS MemoryToolPayload in ChatMemory.swift) ----

enum class MemoryToolAction(val raw: String) {
    CREATED("created"),
    UPDATED("updated"),
    UNCHANGED("unchanged"),
    FORGOTTEN("forgotten"),
    UNDONE("undone");

    companion object {
        fun fromRaw(raw: String?): MemoryToolAction? = entries.firstOrNull { it.raw == raw }
    }
}

@Serializable
private data class MemoryFields(
    val category: String? = null,
    val content: String? = null,
    val origin: String? = null,
)

@Serializable
private data class MemoryOutput(
    val action: String,
    val memory: MemoryFields? = null,
)

/** Parsed view of a `system_memory_upsert` / `system_memory_forget` tool call. */
internal data class MemoryToolPayload(
    val action: MemoryToolAction? = null,
    val category: MemoryCategory? = null,
    val content: String? = null,
    val origin: MemoryOrigin? = null,
) {
    companion object {
        /** While the call runs, the card previews what is being saved from the input. */
        fun fromInput(input: JsonElement?): MemoryToolPayload? {
            val fields: MemoryFields = input?.decodingEmbeddedJson().decode() ?: return null
            return MemoryToolPayload(
                category = MemoryCategory.entries.firstOrNull { it.raw == fields.category },
                content = fields.content,
                origin = MemoryOrigin.entries.firstOrNull { it.raw == fields.origin },
            )
        }

        /** Settled result: `{action, memory}`; the action is required. */
        fun fromOutput(output: JsonElement?): MemoryToolPayload? {
            val decoded: MemoryOutput = output?.decodingEmbeddedJson().decode() ?: return null
            return MemoryToolPayload(
                action = MemoryToolAction.fromRaw(decoded.action) ?: return null,
                category = MemoryCategory.entries.firstOrNull { it.raw == decoded.memory?.category },
                content = decoded.memory?.content,
                origin = MemoryOrigin.entries.firstOrNull { it.raw == decoded.memory?.origin },
            )
        }
    }
}

/** Prefer the settled result; fall back to the input preview (iOS MemoryToolCard.payload). */
internal fun ChatMessagePart.memoryToolPayload(): MemoryToolPayload? =
    if (state == "output-available") {
        MemoryToolPayload.fromOutput(output) ?: MemoryToolPayload.fromInput(input)
    } else {
        MemoryToolPayload.fromInput(input)
    }

// ---- Docs (iOS DocsToolPayload.swift) ----

@Serializable
internal data class DocsToolDocument(
    val id: String,
    val title: String = "Untitled",
    val kind: String? = null,
    @SerialName("content") val excerpt: String? = null,
    val projectName: String? = null,
    val folderName: String? = null,
    val revision: Int? = null,
    val updated: String? = null,
) {
    val location: String?
        get() = listOf(projectName, folderName).firstOrNull { !it.isNullOrEmpty() }
    val isHtml: Boolean get() = kind == DocumentKind.HTML.raw
}

@Serializable
internal data class DocsToolFolder(
    val id: String,
    val name: String = "Untitled",
)

@Serializable
private data class DocsMoveDocument(
    val title: String? = null,
    val folderName: String? = null,
    val projectName: String? = null,
)

@Serializable
private data class DocsMoveElement(
    val index: Int = 0,
    val ok: Boolean = false,
    val result: DocsMoveDocument? = null,
    val error: String? = null,
)

internal data class DocsToolMoveResult(
    val index: Int,
    val ok: Boolean,
    val title: String?,
    val destination: String?,
    val error: String?,
)

internal class DocsToolPayload private constructor(
    val documents: List<DocsToolDocument> = emptyList(),
    val folders: List<DocsToolFolder> = emptyList(),
    /** True when docs_ensure_folder created the folder rather than reusing one. */
    val folderWasCreated: Boolean = false,
    val isCreation: Boolean = false,
    val changed: Boolean? = null,
    val matches: Int? = null,
    val moveTotal: Int = 0,
    val moveSucceeded: Int = 0,
    val moveResults: List<DocsToolMoveResult> = emptyList(),
    val chunkContentLength: Int? = null,
    val chunkRevision: Int? = null,
) {
    companion object {
        fun parse(toolName: String, output: JsonElement?): DocsToolPayload? {
            val value = output?.decodingEmbeddedJson() ?: return null
            return when (toolName) {
                "docs_search" -> {
                    val items = value as? JsonArray ?: return null
                    DocsToolPayload(documents = items.mapNotNull { it.decode<DocsToolDocument>() })
                }
                "docs_get" -> {
                    val doc = value.decode<DocsToolDocument>() ?: return null
                    DocsToolPayload(documents = listOf(doc))
                }
                "docs_upsert" -> {
                    val obj = value as? JsonObject ?: return null
                    val inner = obj["document"].decode<DocsToolDocument>()
                    if (inner != null) {
                        DocsToolPayload(documents = listOf(inner), changed = obj.bool("changed"))
                    } else {
                        val doc = value.decode<DocsToolDocument>() ?: return null
                        DocsToolPayload(documents = listOf(doc), isCreation = true)
                    }
                }
                "docs_replace" -> {
                    val obj = value as? JsonObject ?: return null
                    val doc = obj["document"].decode<DocsToolDocument>() ?: return null
                    DocsToolPayload(
                        documents = listOf(doc),
                        changed = obj.bool("changed"),
                        matches = obj.int("matches"),
                    )
                }
                "docs_move" -> {
                    val obj = value as? JsonObject ?: return null
                    val results = (obj["results"] as? JsonArray).orEmpty().map { element ->
                        val item: DocsMoveElement = element.decode() ?: DocsMoveElement()
                        val document = item.result
                        // The backend only reports where a document ended up; the
                        // source location is not part of the tool data.
                        val destination =
                            listOf(document?.folderName, document?.projectName)
                                .firstOrNull { !it.isNullOrEmpty() }
                                ?: if (item.ok) "My Documents" else null
                        DocsToolMoveResult(
                            index = item.index,
                            ok = item.ok,
                            title = document?.title,
                            destination = destination,
                            error = item.error,
                        )
                    }
                    DocsToolPayload(
                        moveTotal = obj.int("total") ?: 0,
                        moveSucceeded = obj.int("succeeded") ?: 0,
                        moveResults = results,
                    )
                }
                "docs_write_chunk" -> {
                    val obj = value as? JsonObject ?: return null
                    DocsToolPayload(
                        chunkContentLength = obj.int("contentLength"),
                        chunkRevision = obj.int("revision"),
                    )
                }
                // The card is dispatched by the `docs_` prefix, so leaving this
                // unparsed would render an empty card rather than fall through
                // to the generic one.
                "docs_ensure_folder" -> {
                    val obj = value as? JsonObject ?: return null
                    val folder = (obj["folder"] as? JsonObject)?.decode<DocsToolFolder>() ?: return null
                    DocsToolPayload(
                        folders = listOf(folder),
                        folderWasCreated = obj.bool("created") ?: false,
                    )
                }
                "docs_list_folders" -> {
                    val items = value as? JsonArray ?: return null
                    DocsToolPayload(folders = items.mapNotNull { it.decode<DocsToolFolder>() })
                }
                else -> null
            }
        }
    }
}

/** Payload is only available once the call settles (iOS DocsToolCard.payload). */
internal fun ChatMessagePart.docsToolPayload(): DocsToolPayload? =
    if (state == "output-available") DocsToolPayload.parse(toolName ?: "", output) else null

// ---- Contacts (iOS ContactsToolPayload.swift) ----

/** Non-sensitive contact projection (email/phone never leave the backend). */
@Serializable
internal data class ContactsToolContact(
    val id: String,
    val name: String = "",
    val title: String? = null,
    val status: String? = null,
    val avatar: String? = null,
    val collectionId: String? = null,
) {
    val displayTitle: String? get() = title?.takeIf { it.isNotEmpty() }
    val avatarFile: String? get() = avatar?.takeIf { it.isNotEmpty() }
    val collectionID: String? get() = collectionId?.takeIf { it.isNotEmpty() }
    val presence: UserStatus?
        get() = status?.let { raw -> UserStatus.entries.firstOrNull { it.raw == raw } }
}

internal class ContactsToolPayload private constructor(
    val contacts: List<ContactsToolContact>,
) {
    companion object {
        fun parse(output: JsonElement?): ContactsToolPayload? {
            val items = output?.decodingEmbeddedJson() as? JsonArray ?: return null
            return ContactsToolPayload(items.mapNotNull { it.decode<ContactsToolContact>() })
        }
    }
}

internal fun ChatMessagePart.contactsToolPayload(): ContactsToolPayload? =
    if (state == "output-available") ContactsToolPayload.parse(output) else null

// ---- Board (iOS BoardToolPayload.swift) ----

@Serializable
internal data class BoardToolParticipant(
    val id: String,
    val name: String = "",
    val avatar: String? = null,
    val collectionId: String? = null,
    val role: String? = null,
) {
    val avatarFile: String? get() = avatar?.takeIf { it.isNotEmpty() }
    val collectionID: String? get() = collectionId?.takeIf { it.isNotEmpty() }
    val displayRole: String? get() = role?.takeIf { it.isNotEmpty() }
}

@Serializable
internal data class BoardToolState(
    val id: String,
    val name: String,
    val color: String? = null,
    val taskCount: Int? = null,
) {
    val displayColor: String? get() = color?.takeIf { it.isNotEmpty() }
}

@Serializable
internal data class BoardToolLabel(
    val id: String,
    val name: String,
    val color: String? = null,
)

@Serializable
internal data class BoardToolProject(
    val id: String,
    val name: String,
    val description: String? = null,
    val archived: Boolean = false,
    val currentActorRole: String? = null,
    val states: List<BoardToolState> = emptyList(),
    val labels: List<BoardToolLabel> = emptyList(),
    val members: List<BoardToolParticipant> = emptyList(),
) {
    val displayDescription: String? get() = description?.takeIf { it.isNotEmpty() }
    val role: String? get() = currentActorRole?.takeIf { it.isNotEmpty() }
    val taskCount: Int get() = states.sumOf { it.taskCount ?: 0 }
}

@Serializable
internal data class BoardNameRef(val name: String? = null)

@Serializable
internal data class BoardStateRef(val name: String? = null, val color: String? = null)

@Serializable
internal data class BoardToolTask(
    val id: String,
    val title: String,
    val description: String? = null,
    val priority: String? = null,
    val dueDate: String? = null,
    val project: BoardNameRef? = null,
    val state: BoardStateRef? = null,
    val labels: List<BoardToolLabel> = emptyList(),
    val assignees: List<BoardToolParticipant> = emptyList(),
) {
    val displayDescription: String? get() = description?.takeIf { it.isNotEmpty() }
    val taskPriority: TaskPriority?
        get() = priority?.let { raw -> TaskPriority.entries.firstOrNull { it.raw == raw } }
    val displayDueDate: String? get() = dueDate?.takeIf { it.isNotEmpty() }
    val projectName: String? get() = project?.name?.takeIf { it.isNotEmpty() }
    val stateName: String? get() = state?.name?.takeIf { it.isNotEmpty() }
    val stateColor: String? get() = state?.color?.takeIf { it.isNotEmpty() }

    /** Due dates arrive as `YYYY-MM-DD`; overdue means before today. */
    val isOverdue: Boolean
        get() = displayDueDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.isBefore(LocalDate.now()) == true
}

@Serializable
internal data class BoardToolTemplate(
    val id: String,
    val name: String,
    val description: String? = null,
    val states: List<JsonElement> = emptyList(),
    val labels: List<JsonElement> = emptyList(),
) {
    val displayDescription: String? get() = description?.takeIf { it.isNotEmpty() }
    val stateCount: Int get() = states.size
    val labelCount: Int get() = labels.size
}

/** Covers both the batch per-item shape and the single MutationResult shape. */
@Serializable
private data class BoardMutationResult(
    val action: String? = null,
    val resourceType: String? = null,
    val name: String? = null,
)

@Serializable
private data class BoardMutationElement(
    val index: Int = 0,
    val ok: Boolean? = null,
    val result: BoardMutationResult? = null,
    val error: String? = null,
    val action: String? = null,
    val resourceType: String? = null,
    val name: String? = null,
)

internal data class BoardToolMutationItem(
    val index: Int,
    val ok: Boolean,
    val name: String?,
    val detail: String?,
    val error: String?,
)

internal class BoardToolPayload private constructor(
    val projects: List<BoardToolProject> = emptyList(),
    val tasks: List<BoardToolTask> = emptyList(),
    val templates: List<BoardToolTemplate> = emptyList(),
    val mutationTotal: Int = 0,
    val mutationSucceeded: Int = 0,
    val mutationItems: List<BoardToolMutationItem> = emptyList(),
    val isBatchMutation: Boolean = false,
) {
    companion object {
        fun parse(toolName: String, output: JsonElement?): BoardToolPayload? {
            val value = output?.decodingEmbeddedJson() ?: return null
            return when (toolName) {
                "board_search_projects" -> {
                    val items = value as? JsonArray ?: return null
                    BoardToolPayload(projects = items.mapNotNull { it.decode<BoardToolProject>() })
                }
                "board_get_project" -> {
                    val project = value.decode<BoardToolProject>() ?: return null
                    BoardToolPayload(projects = listOf(project))
                }
                "board_search_tasks" -> {
                    val items = (value as? JsonObject)?.get("tasks") as? JsonArray ?: return null
                    BoardToolPayload(tasks = items.mapNotNull { it.decode<BoardToolTask>() })
                }
                "board_list_templates" -> {
                    val items = value as? JsonArray ?: return null
                    BoardToolPayload(templates = items.mapNotNull { it.decode<BoardToolTemplate>() })
                }
                "board_create_project", "board_update_project",
                "board_upsert_state", "board_upsert_label", "board_upsert_member",
                "board_create_task", "board_update_task" -> {
                    val obj = value as? JsonObject ?: return null
                    val results = obj["results"] as? JsonArray
                    if (results != null) {
                        BoardToolPayload(
                            isBatchMutation = true,
                            mutationTotal = obj.int("total") ?: results.size,
                            mutationSucceeded = obj.int("succeeded") ?: 0,
                            mutationItems = results.map { mutationItem(it.decode()) },
                        )
                    } else {
                        val item = mutationItem(value.decode())
                        BoardToolPayload(
                            mutationItems = listOf(item),
                            mutationTotal = 1,
                            mutationSucceeded = if (item.ok) 1 else 0,
                        )
                    }
                }
                else -> null
            }
        }

        private fun mutationItem(element: BoardMutationElement?): BoardToolMutationItem {
            val item = element ?: BoardMutationElement()
            val result = item.result
            return if (result != null) {
                // Batch per-item shape: {index, ok, result: MutationResult, error}
                BoardToolMutationItem(
                    index = item.index,
                    ok = item.ok ?: false,
                    name = result.name,
                    detail = listOfNotNull(result.action, result.resourceType).joinToString(" "),
                    error = item.error?.takeIf { it.isNotEmpty() },
                )
            } else {
                // Single MutationResult shape; failed batch items land here too.
                BoardToolMutationItem(
                    index = item.index,
                    ok = item.ok ?: true,
                    name = item.name,
                    detail = listOfNotNull(item.action, item.resourceType).joinToString(" "),
                    error = item.error?.takeIf { it.isNotEmpty() },
                )
            }
        }
    }
}

internal fun ChatMessagePart.boardToolPayload(): BoardToolPayload? =
    if (state == "output-available") BoardToolPayload.parse(toolName ?: "", output) else null

// ---- Calendar (iOS CalendarToolPayload.swift) ----

@Serializable
internal data class CalendarToolEvent(
    val id: String,
    val title: String,
    val allDay: Boolean = false,
    val instanceStart: String? = null,
    val instanceEnd: String? = null,
    val startAt: String? = null,
    val endAt: String? = null,
    val location: String? = null,
    val color: String? = null,
    val recurrenceFrequency: String? = null,
) {
    // Occurrences carry RFC3339 instance times; fall back to the event's own
    // PocketBase datetimes.
    val start: Instant?
        get() = instanceStart?.takeIf { it.isNotEmpty() }?.let(::parseServerDate)
            ?: startAt?.takeIf { it.isNotEmpty() }?.let(::parseServerDate)
    val end: Instant?
        get() = instanceEnd?.takeIf { it.isNotEmpty() }?.let(::parseServerDate)
            ?: endAt?.takeIf { it.isNotEmpty() }?.let(::parseServerDate)
    val displayLocation: String? get() = location?.takeIf { it.isNotEmpty() }
    val eventColor: EventColor?
        get() = color?.let { raw -> EventColor.entries.firstOrNull { it.raw == raw } }
    val recurrence: EventRecurrenceFrequency?
        get() = recurrenceFrequency?.let { raw ->
            EventRecurrenceFrequency.entries.firstOrNull { it.raw == raw }
        }
}

@Serializable
internal data class CalendarToolScheduleTask(
    val id: String,
    val title: String,
    val priority: String? = null,
    val dueDate: String? = null,
    val projectName: String? = null,
    val completed: Boolean = false,
) {
    val taskPriority: TaskPriority?
        get() = priority?.let { raw -> TaskPriority.entries.firstOrNull { it.raw == raw } }
    val displayDueDate: String? get() = dueDate?.takeIf { it.isNotEmpty() }
    val displayProjectName: String? get() = projectName?.takeIf { it.isNotEmpty() }
}

internal data class CalendarToolDay(
    val date: String,
    val events: List<CalendarToolEvent>,
    val tasks: List<CalendarToolScheduleTask>,
)

@Serializable
private data class CalendarTitleRef(val title: String? = null)

@Serializable
private data class CalendarMutationResult(
    val action: String? = null,
    val event: CalendarTitleRef? = null,
)

/** Batch per-item `{index, ok, result, error}` or single EventMutationResult. */
@Serializable
private data class CalendarMutationElement(
    val index: Int = 0,
    val ok: Boolean? = null,
    val result: CalendarMutationResult? = null,
    val error: String? = null,
    val action: String? = null,
    val event: CalendarTitleRef? = null,
)

internal data class CalendarToolMutationItem(
    val index: Int,
    val ok: Boolean,
    val title: String?,
    val action: String?,
    val error: String?,
)

internal class CalendarToolPayload private constructor(
    val days: List<CalendarToolDay> = emptyList(),
    /** Flat matches from calendar_search_events; whole events, not occurrences. */
    val matches: List<CalendarToolEvent> = emptyList(),
    val isSearch: Boolean = false,
    /** False when the backend capped the result, so the card can say so. */
    val searchComplete: Boolean = true,
    val mutationTotal: Int = 0,
    val mutationSucceeded: Int = 0,
    val mutationItems: List<CalendarToolMutationItem> = emptyList(),
    val isBatchMutation: Boolean = false,
) {
    companion object {
        fun parse(toolName: String, output: JsonElement?): CalendarToolPayload? {
            val value = output?.decodingEmbeddedJson() ?: return null
            return when (toolName) {
                "calendar_get_schedule" -> {
                    val dayValues = (value as? JsonObject)?.get("days") as? JsonArray ?: return null
                    CalendarToolPayload(days = dayValues.mapNotNull(::day))
                }
                "calendar_search_events" -> {
                    val obj = value as? JsonObject ?: return null
                    val events = obj["events"] as? JsonArray ?: return null
                    CalendarToolPayload(
                        matches = events.mapNotNull { it.decode<CalendarToolEvent>() },
                        isSearch = true,
                        searchComplete = obj.bool("complete") ?: true,
                    )
                }
                "calendar_create_event", "calendar_update_event" -> {
                    val obj = value as? JsonObject ?: return null
                    val results = obj["results"] as? JsonArray
                    if (results != null) {
                        CalendarToolPayload(
                            isBatchMutation = true,
                            mutationTotal = obj.int("total") ?: results.size,
                            mutationSucceeded = obj.int("succeeded") ?: 0,
                            mutationItems = results.map { mutationItem(it.decode()) },
                        )
                    } else {
                        val item = mutationItem(value.decode())
                        CalendarToolPayload(
                            mutationItems = listOf(item),
                            mutationTotal = 1,
                            mutationSucceeded = if (item.ok) 1 else 0,
                        )
                    }
                }
                else -> null
            }
        }

        private fun day(element: JsonElement): CalendarToolDay? {
            val obj = element as? JsonObject ?: return null
            val date = obj.string("date") ?: return null
            return CalendarToolDay(
                date = date,
                events = (obj["events"] as? JsonArray).orEmpty()
                    .mapNotNull { it.decode<CalendarToolEvent>() },
                tasks = (obj["tasks"] as? JsonArray).orEmpty()
                    .mapNotNull { it.decode<CalendarToolScheduleTask>() },
            )
        }

        private fun mutationItem(element: CalendarMutationElement?): CalendarToolMutationItem {
            val item = element ?: CalendarMutationElement()
            // Batch per-item wraps the EventMutationResult in `result`; a single
            // result carries the fields directly.
            val result = item.result
            return CalendarToolMutationItem(
                index = item.index,
                ok = item.ok ?: true,
                title = (result?.event ?: item.event)?.title,
                action = result?.action ?: item.action,
                error = item.error?.takeIf { it.isNotEmpty() },
            )
        }
    }
}

internal fun ChatMessagePart.calendarToolPayload(): CalendarToolPayload? =
    if (state == "output-available") CalendarToolPayload.parse(toolName ?: "", output) else null

// ---- Reading (iOS ReadingToolPayload.swift) ----

@Serializable
internal data class ReadingToolItem(
    val id: String,
    val title: String,
    val url: String? = null,
    val status: String? = null,
    val description: String? = null,
    val summary: String? = null,
    val keyPoints: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
) {
    val displayUrl: String? get() = url?.takeIf { it.isNotEmpty() }
    val displayDescription: String? get() = description?.takeIf { it.isNotEmpty() }
    val displaySummary: String? get() = summary?.takeIf { it.isNotEmpty() }
    val readingStatus: ReadingStatus?
        get() = status?.let { raw -> ReadingStatus.entries.firstOrNull { it.raw == raw } }
    val host: String?
        get() = displayUrl
            ?.let { runCatching { URI(it).host }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?.replace("www.", "")
}

@Serializable
private data class ReadingMutationResult(val title: String? = null)

@Serializable
private data class ReadingMutationElement(
    val index: Int = 0,
    val ok: Boolean = false,
    val result: ReadingMutationResult? = null,
    val error: String? = null,
)

internal data class ReadingToolMutationItem(
    val index: Int,
    val ok: Boolean,
    val title: String?,
    val error: String?,
)

internal class ReadingToolPayload private constructor(
    val items: List<ReadingToolItem> = emptyList(),
    val mutationTotal: Int = 0,
    val mutationSucceeded: Int = 0,
    val mutationItems: List<ReadingToolMutationItem> = emptyList(),
    val isBatchMutation: Boolean = false,
    /** Non-JSON plain-text output (reading_summarize surfaces failures this way). */
    val plainText: String? = null,
) {
    companion object {
        fun parse(toolName: String, output: JsonElement?): ReadingToolPayload? {
            val value = output?.decodingEmbeddedJson()
            // A plain-text body never decodes as JSON; keep it so the card can
            // show it instead of "not found".
            val text = (output as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (text != null && value !is JsonObject && value !is JsonArray) {
                return ReadingToolPayload(plainText = text)
            }
            if (value == null) return null
            return when (toolName) {
                "reading_search" -> {
                    val array = value as? JsonArray ?: return null
                    ReadingToolPayload(items = array.mapNotNull { it.decode<ReadingToolItem>() })
                }
                "reading_get", "reading_summarize" -> {
                    val item = value.decode<ReadingToolItem>() ?: return null
                    ReadingToolPayload(items = listOf(item))
                }
                "reading_upsert" -> {
                    val obj = value as? JsonObject ?: return null
                    val results = obj["results"] as? JsonArray
                    if (results != null) {
                        ReadingToolPayload(
                            isBatchMutation = true,
                            mutationTotal = obj.int("total") ?: results.size,
                            mutationSucceeded = obj.int("succeeded") ?: 0,
                            mutationItems = results.map { element ->
                                val item: ReadingMutationElement =
                                    element.decode() ?: ReadingMutationElement()
                                ReadingToolMutationItem(
                                    index = item.index,
                                    ok = item.ok,
                                    title = item.result?.title,
                                    error = item.error?.takeIf { it.isNotEmpty() },
                                )
                            },
                        )
                    } else {
                        val item = value.decode<ReadingToolItem>() ?: return null
                        ReadingToolPayload(
                            mutationItems = listOf(
                                ReadingToolMutationItem(index = 0, ok = true, title = item.title, error = null),
                            ),
                            mutationTotal = 1,
                            mutationSucceeded = 1,
                        )
                    }
                }
                else -> null
            }
        }
    }
}

internal fun ChatMessagePart.readingToolPayload(): ReadingToolPayload? =
    if (state == "output-available") ReadingToolPayload.parse(toolName ?: "", output) else null
