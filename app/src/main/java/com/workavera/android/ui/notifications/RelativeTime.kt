package com.workavera.android.ui.notifications

import com.workavera.android.data.model.parseServerDate
import java.time.Duration
import java.time.Instant

/**
 * Converts a PocketBase date string to a relative time ("3 hours ago" style).
 * Handles both ISO-8601 (with T) and PocketBase's "yyyy-MM-dd HH:mm:ss.SSSZ".
 */
object RelativeTime {

    fun format(raw: String, now: Instant = Instant.now()): String {
        val instant = parseServerDate(raw) ?: return raw.take(10)
        val duration = Duration.between(instant, now)
        val minutes = duration.toMinutes()
        val hours = duration.toHours()
        val days = duration.toDays()
        return when {
            duration.isNegative || minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            hours < 24 -> if (hours == 1L) "1 hour ago" else "$hours hours ago"
            days < 7 -> if (days == 1L) "1 day ago" else "$days days ago"
            else -> raw.take(10)
        }
    }
}
