package com.workavera.android.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewKanban
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * All app modules, grouped and ordered like the Workavera Web / iOS apps:
 * Overview (Dashboard) / Workflow (Reading…Calendar) / System (Settings).
 * Notifications lives behind the top-bar bell; Profile is reached via the
 * sidebar user card.
 */
enum class AppSection(
    val route: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
) {
    DASHBOARD(
        route = "dashboard",
        title = "Dashboard",
        description = "Your personal overview at a glance.",
        icon = Icons.Outlined.Home,
    ),
    READING(
        route = "reading",
        title = "Reading",
        description = "Bookmarks and reading list with tags, read status, and AI summaries.",
        icon = Icons.AutoMirrored.Outlined.MenuBook,
    ),
    CONTACTS(
        route = "contacts",
        title = "Contacts",
        description = "Manage clients, partners, and collaborators.",
        icon = Icons.Outlined.Contacts,
    ),
    CHAT(
        route = "chat",
        title = "Chat",
        description = "Conversations with your Workavera assistant, with streaming output and tool calls.",
        icon = Icons.AutoMirrored.Outlined.Chat,
    ),
    BOARD(
        route = "board",
        title = "Board",
        description = "Manage projects and tasks in a Kanban board with workflows, labels, and due dates.",
        icon = Icons.Outlined.ViewKanban,
    ),
    DOCS(
        route = "docs",
        title = "Docs",
        description = "Create notes, team documents, and public content with versioning and AI editing.",
        icon = Icons.Outlined.Description,
    ),
    CALENDAR(
        route = "calendar",
        title = "Calendar",
        description = "Schedule and events, merged with visible board deadlines and reminders.",
        icon = Icons.Outlined.CalendarMonth,
    ),
    NOTIFICATIONS(
        route = "notifications",
        title = "Notifications",
        description = "Task due alerts, calendar reminders, and model share requests.",
        icon = Icons.Outlined.Notifications,
    ),
    SETTINGS(
        route = "settings",
        title = "Settings",
        description = "Manage your preferences and API keys.",
        icon = Icons.Outlined.Settings,
    ),
    PROFILE(
        route = "profile",
        title = "Profile",
        description = "Your profile, avatar, and status.",
        icon = Icons.Outlined.AccountCircle,
    );

    companion object {
        val overview = listOf(DASHBOARD)
        val workflow = listOf(READING, CONTACTS, CHAT, BOARD, DOCS, CALENDAR)
        val system = listOf(SETTINGS)

        /** Dashboard quick links, items and order matching iOS DashboardView.quickLinks (no Contacts). */
        val dashboardQuickLinks = listOf(CHAT, BOARD, DOCS, CALENDAR, READING)

        fun fromRoute(route: String?): AppSection? =
            entries.firstOrNull { it.route == route }
    }
}
