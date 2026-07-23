package com.workavera.android.data.model

import kotlinx.serialization.Serializable

/**
 * Dashboard stats, matching the iOS DashboardSummary. Not returned by a single
 * endpoint — aggregated client-side from 5 list count queries:
 * nextSevenDays = calendar events in the next 7 days + open tasks due within
 * the next 7 days.
 */
@Serializable
data class DashboardSummary(
    val activeProjects: Int,
    val openTasks: Int,
    val nextSevenDays: Int,
    val unreadReading: Int,
)
