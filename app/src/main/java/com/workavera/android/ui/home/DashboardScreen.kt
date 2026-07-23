package com.workavera.android.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workavera.android.data.AuthState
import com.workavera.android.data.model.UserStatus
import com.workavera.android.ui.AppSection
import com.workavera.android.ui.AuthViewModel
import com.workavera.android.ui.components.AvatarView
import com.workavera.android.ui.components.ErrorBanner
import com.workavera.android.ui.components.LoadingIndicator
import com.workavera.android.ui.theme.color
import java.time.LocalTime

/**
 * Overview screen, laid out like the iOS DashboardView: greeting card →
 * 2×2 stat cards → Quick Access grouped list, with pull-to-refresh and error
 * retry. Cards reuse the profile screen's white hairline-border style to avoid
 * large gray areas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: AuthState.LoggedIn,
    viewModel: AuthViewModel,
    onOpenSection: (AppSection) -> Unit,
) {
    val dashboardViewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)
    val ui = dashboardViewModel.uiState

    LaunchedEffect(state.user.id) {
        dashboardViewModel.loadIfNeeded()
    }

    PullToRefreshBox(
        isRefreshing = ui.isLoading,
        onRefresh = dashboardViewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!ui.hasLoaded && ui.isLoading) {
            LoadingIndicator("Loading dashboard…")
            return@PullToRefreshBox
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
            ) {
                if (ui.error != null) {
                    ErrorBanner(message = ui.error, onRetry = dashboardViewModel::refresh)
                    Spacer(Modifier.height(16.dp))
                }

                GreetingCard(
                    state = state,
                    avatarUrl = viewModel.avatarUrl(state),
                    onOpenProfile = { onOpenSection(AppSection.PROFILE) },
                )
                Spacer(Modifier.height(24.dp))

                StatsGrid(ui = ui)
                Spacer(Modifier.height(24.dp))

                Text(
                    text = "Quick Access",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                DashboardCard {
                    AppSection.dashboardQuickLinks.forEachIndexed { index, section ->
                        if (index > 0) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(start = 54.dp),
                            )
                        }
                        QuickAccessRow(section = section, onClick = { onOpenSection(section) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GreetingCard(
    state: AuthState.LoggedIn,
    avatarUrl: String?,
    onOpenProfile: () -> Unit,
) {
    val user = state.user
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    DashboardCard(onClick = onOpenProfile) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarView(
                url = avatarUrl,
                name = user.displayName,
                size = 56.dp,
                status = user.userStatus,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                // "Greeting," on the first line, name on the second, matching
                // iOS greetingText; 20sp with a tight line height keeps it compact.
                Text(
                    text = "$greeting,\n${user.displayName}",
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(status = user.userStatus)
                    if (user.title.isNotEmpty()) {
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        Text(
                            text = user.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            // Centered chevron hinting the whole card is tappable (opens the profile).
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusBadge(status: UserStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(status.color, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = status.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatsGrid(ui: DashboardUiState) {
    val summary = ui.summary
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatCard(
                title = "Active Projects",
                icon = Icons.Outlined.ViewColumn,
                value = summary?.activeProjects,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = "Open Tasks",
                icon = Icons.Outlined.Checklist,
                value = summary?.openTasks,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatCard(
                title = "Next 7 Days",
                icon = Icons.Outlined.DateRange,
                value = summary?.nextSevenDays,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = "Unread Reading",
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                value = summary?.unreadReading,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Stat card, mirroring the iOS StatCard: display-only, shows "—" until the value loads. */
@Composable
private fun StatCard(
    title: String,
    icon: ImageVector,
    value: Int?,
    modifier: Modifier = Modifier,
) {
    DashboardCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = value?.toString() ?: "—",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickAccessRow(section: AppSection, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = section.icon,
            contentDescription = null,
            modifier = Modifier.width(26.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = section.title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** White card with a hairline border, same look as the profile GroupCard. */
@Composable
private fun DashboardCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
            border = border,
            elevation = elevation,
            content = content,
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
            border = border,
            elevation = elevation,
            content = content,
        )
    }
}
