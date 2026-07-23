package com.workavera.android.ui.theme

import androidx.compose.ui.graphics.Color
import com.workavera.android.data.model.TaskPriority
import com.workavera.android.data.model.UserStatus

// Monochrome brand shared with the Web / iOS apps (iOS AccentColor:
// #1A1A1A light / #EBEBEB dark).
val Ink = Color(0xFF1A1A1A)
val Paper = Color(0xFFEBEBEB)

// Presence colors, matching the green/yellow/red/gray of the iOS StatusBadge.
val StatusOnline = Color(0xFF22C55E)
val StatusAway = Color(0xFFEAB308)
val StatusBusy = Color(0xFFEF4444)
val StatusOffline = Color(0xFF9CA3AF)

// Task priority colors, matching iOS TaskPriority.color.
val PriorityNone = Color(0xFF94A3B8)
val PriorityLow = Color(0xFF64748B)
val PriorityMedium = Color(0xFF3B82F6)
val PriorityHigh = Color(0xFFF59E0B)
val PriorityUrgent = Color(0xFFEF4444)

val UserStatus.color: Color
    get() = when (this) {
        UserStatus.ONLINE -> StatusOnline
        UserStatus.AWAY -> StatusAway
        UserStatus.BUSY -> StatusBusy
        UserStatus.OFFLINE -> StatusOffline
    }

val TaskPriority.color: Color
    get() = when (this) {
        TaskPriority.NONE -> PriorityNone
        TaskPriority.LOW -> PriorityLow
        TaskPriority.MEDIUM -> PriorityMedium
        TaskPriority.HIGH -> PriorityHigh
        TaskPriority.URGENT -> PriorityUrgent
    }

/** Parses a server-side hex color (#rrggbb / #aarrggbb), falling back on failure. */
fun parseHexColor(hex: String, fallback: Color): Color =
    try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
