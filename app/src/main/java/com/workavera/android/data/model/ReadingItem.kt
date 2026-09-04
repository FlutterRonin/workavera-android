package com.workavera.android.data.model

import java.net.URI
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Reading item lifecycle state, matching the iOS ReadingStatus. */
enum class ReadingStatus(val raw: String, val label: String) {
    UNREAD("unread", "Unread"),
    READ("read", "Read"),
    ARCHIVED("archived", "Archived");

    companion object {
        fun fromRaw(raw: String?): ReadingStatus = entries.firstOrNull { it.raw == raw } ?: UNREAD
    }
}

/** Which slice of the reading list is shown, matching the iOS ReadingListFilter. */
enum class ReadingListFilter(val raw: String, val label: String) {
    ACTIVE("active", "All"),
    UNREAD("unread", "Unread"),
    READ("read", "Read"),
    PINNED("pinned", "Pinned"),
    ARCHIVED("archived", "Archived"),
}

/**
 * A record of the PocketBase reading_items collection, matching the iOS
 * ReadingItem. The list endpoint deliberately omits content_text; it only
 * arrives via status updates or the summarize endpoint.
 */
@Serializable
data class ReadingItem(
    val id: String,
    val owner: String = "",
    val project: String = "",
    val url: String = "",
    val title: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val status: String = "unread",
    val pinned: Boolean = false,
    @SerialName("content_text") val contentText: String = "",
    val summary: String = "",
    @SerialName("key_points") val keyPoints: List<String> = emptyList(),
    @SerialName("summary_language") val summaryLanguage: String? = null,
    val created: String = "",
    val updated: String = "",
) {
    val readingStatus: ReadingStatus get() = ReadingStatus.fromRaw(status)
    val projectId: String? get() = project.ifEmpty { null }

    /** URL host without any "www."; falls back to the raw URL when it does not parse. */
    val host: String
        get() {
            val parsed = try {
                URI(url).host
            } catch (_: Exception) {
                null
            }
            return if (parsed.isNullOrEmpty()) url else parsed.replace("www.", "")
        }

    /** Card preview text: summary first, then description, then the URL itself. */
    val preview: String
        get() = when {
            summary.isNotEmpty() -> summary
            description.isNotEmpty() -> description
            else -> url
        }

    val updatedAt: Instant? get() = parseServerDate(updated)
}

/** One page of the reading list plus the global unread count, matching the iOS ReadingPage. */
@Serializable
data class ReadingPage(
    val items: List<ReadingItem> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val totalItems: Int = 0,
    val unreadCount: Int = 0,
)

/** Fields the add sheet collects, matching the iOS ReadingItemInput. */
data class ReadingItemInput(
    val url: String,
    val title: String,
    val description: String,
    val tags: List<String>,
    val status: ReadingStatus = ReadingStatus.UNREAD,
    /** Pre-fetched summary a kept Discover candidate carries along (iOS ReadingItemInput). */
    val summary: String = "",
    val keyPoints: List<String> = emptyList(),
    val summaryLanguage: String = "",
)

/** Answer of POST /api/reading/items/{id}/summarize, matching the iOS ReadingSummaryResponse. */
@Serializable
data class ReadingSummaryResponse(
    @SerialName("content_text") val contentText: String = "",
    val summary: String = "",
    @SerialName("key_points") val keyPoints: List<String> = emptyList(),
)

// ---- Subscriptions (discover) ----

/** Subscription kind, matching the iOS ReadingSourceKind. */
enum class ReadingSourceKind(val raw: String, val label: String) {
    RSS("rss", "RSS / Atom feed"),
    GITHUB_TRENDING("github_trending", "GitHub Trending");

    companion object {
        fun fromRaw(raw: String?): ReadingSourceKind =
            entries.firstOrNull { it.raw == raw } ?: RSS
    }
}

/** GitHub Trending period, matching the iOS TrendingSince. */
enum class TrendingSince(val raw: String, val label: String) {
    DAILY("daily", "today"),
    WEEKLY("weekly", "this week"),
    MONTHLY("monthly", "this month");

    companion object {
        fun fromRaw(raw: String?): TrendingSince =
            entries.firstOrNull { it.raw == raw } ?: WEEKLY
    }
}

/**
 * One subscription the user owns: an RSS/Atom feed of their own, or GitHub's
 * trending board for one language over a day, week, or month (iOS ReadingSource).
 */
data class ReadingSource(
    val id: String,
    val name: String,
    val kind: ReadingSourceKind,
    val url: String,
    val language: String,
    val since: TrendingSince,
    val enabled: Boolean,
    val lastFetchedAt: Instant?,
    val lastError: String,
) {
    /** The editor's subtitle: the feed address, or the trending board parameters. */
    val subtitle: String
        get() = when (kind) {
            ReadingSourceKind.RSS -> url
            ReadingSourceKind.GITHUB_TRENDING -> "GitHub Trending · $language · ${since.label}"
        }
}

/** Fields the subscription editor collects, matching the iOS ReadingSourceInput. */
data class ReadingSourceInput(
    val name: String = "",
    val kind: ReadingSourceKind = ReadingSourceKind.RSS,
    val url: String = "",
    val language: String = "",
    val since: TrendingSince = TrendingSince.WEEKLY,
    val enabled: Boolean = true,
)

/**
 * One entry a subscription offered. Candidates are never stored: the panel
 * holds them until the user keeps one, which then becomes an ordinary reading
 * item (iOS DiscoverCandidate; the discover endpoint answers in camelCase).
 */
@Serializable
data class DiscoverCandidate(
    val sourceId: String = "",
    val sourceName: String = "",
    val title: String = "",
    val url: String = "",
    val description: String = "",
    val publishedAt: String = "",
    val language: String = "",
    val stars: Int = 0,
    val periodStars: Int = 0,
    val starsPeriod: String = "",
    val alreadySaved: Boolean = false,
) {
    /** Source, star movement and language as one "a · b · c" line. */
    val metaText: String
        get() {
            val parts = mutableListOf(sourceName)
            if (periodStars > 0) {
                val period = TrendingSince.fromRaw(starsPeriod).label
                    .takeIf { TrendingSince.fromRaw(starsPeriod).raw == starsPeriod } ?: ""
                parts.add("★ +$periodStars $period".trim())
            }
            if (stars > 0) parts.add("$stars stars")
            if (language.isNotEmpty()) parts.add(language)
            return parts.filter { it.isNotEmpty() }.joinToString(" · ")
        }
}

/** One subscription that could not be read, matching the iOS DiscoverFailure. */
@Serializable
data class DiscoverFailure(
    val sourceId: String = "",
    val sourceName: String = "",
    val message: String = "",
)

/** Answer of POST /api/reading/discover, matching the iOS discover response. */
@Serializable
data class DiscoverResponse(
    val items: List<DiscoverCandidate> = emptyList(),
    val failures: List<DiscoverFailure> = emptyList(),
)

/** A candidate's summary in the chosen language, matching the iOS CandidateSummary. */
data class CandidateSummary(
    val summary: String,
    val keyPoints: List<String>,
    val language: String,
)

/** Languages a candidate can be summarized into, matching the web app (iOS SummaryLanguage). */
enum class SummaryLanguage(val raw: String) {
    ENGLISH("English"),
    CHINESE("中文"),
    JAPANESE("日本語");

    companion object {
        fun fromRaw(raw: String?): SummaryLanguage =
            entries.firstOrNull { it.raw == raw } ?: ENGLISH
    }
}

/** PocketBase reading_sources record, matching the iOS ReadingSourceRecord. */
@Serializable
data class ReadingSourceRecord(
    val id: String,
    val name: String = "",
    val kind: String = "",
    val url: String = "",
    val params: Params? = null,
    val enabled: Boolean = false,
    @SerialName("last_fetched_at") val lastFetchedAt: String = "",
    @SerialName("last_error") val lastError: String = "",
) {
    @Serializable
    data class Params(
        val language: String = "",
        val since: String = "",
    )

    val source: ReadingSource
        get() = ReadingSource(
            id = id,
            name = name,
            kind = ReadingSourceKind.fromRaw(kind),
            url = url,
            language = params?.language.orEmpty(),
            since = TrendingSince.fromRaw(params?.since),
            enabled = enabled,
            lastFetchedAt = parseServerDate(lastFetchedAt),
            lastError = lastError,
        )
}
