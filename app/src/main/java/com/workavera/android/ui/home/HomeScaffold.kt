package com.workavera.android.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.workavera.android.data.AuthState
import com.workavera.android.ui.AppSection
import com.workavera.android.ui.AuthViewModel
import com.workavera.android.ui.board.BoardScreen
import com.workavera.android.ui.calendar.CalendarScreen
import com.workavera.android.ui.calendar.CalendarTopBarState
import com.workavera.android.ui.chat.ChatScreen
import com.workavera.android.ui.chat.ChatTopBarState
import com.workavera.android.ui.chat.ChatViewModel
import com.workavera.android.ui.components.AvatarView
import com.workavera.android.ui.contacts.ContactsScreen
import com.workavera.android.ui.contacts.ContactsViewModel
import com.workavera.android.ui.docs.DocsScreen
import com.workavera.android.ui.notifications.NotificationViewModel
import com.workavera.android.ui.notifications.NotificationsScreen
import com.workavera.android.ui.profile.ProfileScreen
import com.workavera.android.ui.profile.ProfileTopBarState
import com.workavera.android.ui.reading.ReadingScreen
import com.workavera.android.ui.reading.ReadingTopBarState
import com.workavera.android.ui.reading.ReadingViewModel
import kotlinx.coroutines.launch

/**
 * Main screen after sign-in. Tablets (width >= 840dp, the Material 3 Expanded
 * breakpoint) get a permanent sidebar; phones get a drawer with a top-bar menu
 * button, mirroring the iOS NavigationSplitView.
 */
@Composable
fun HomeScaffold(state: AuthState.LoggedIn, viewModel: AuthViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentSection =
        AppSection.fromRoute(backStackEntry?.destination?.route) ?: AppSection.DASHBOARD
    val isExpandedWidth = LocalConfiguration.current.screenWidthDp >= 840

    val openSection: (AppSection) -> Unit = { section ->
        navController.navigate(section.route) {
            popUpTo(AppSection.DASHBOARD.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    if (isExpandedWidth) {
        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet(modifier = Modifier.width(300.dp)) {
                    DrawerContent(
                        state = state,
                        avatarUrl = viewModel.avatarUrl(state),
                        currentSection = currentSection,
                        onOpenSection = openSection,
                    )
                }
            },
        ) {
            HomeContent(
                navController = navController,
                currentSection = currentSection,
                showMenuButton = false,
                onMenuClick = {},
                state = state,
                viewModel = viewModel,
                onOpenSection = openSection,
            )
        }
    } else {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    DrawerContent(
                        state = state,
                        avatarUrl = viewModel.avatarUrl(state),
                        currentSection = currentSection,
                        onOpenSection = { section ->
                            scope.launch { drawerState.close() }
                            openSection(section)
                        },
                    )
                }
            },
        ) {
            HomeContent(
                navController = navController,
                currentSection = currentSection,
                showMenuButton = true,
                onMenuClick = { scope.launch { drawerState.open() } },
                state = state,
                viewModel = viewModel,
                onOpenSection = openSection,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    navController: NavHostController,
    currentSection: AppSection,
    showMenuButton: Boolean,
    onMenuClick: () -> Unit,
    state: AuthState.LoggedIn,
    viewModel: AuthViewModel,
    onOpenSection: (AppSection) -> Unit,
) {
    // The notification ViewModel is hoisted to the Home level so the top-bar
    // badge and the notifications screen share one state; keyed by user id so
    // switching accounts rebuilds it.
    val notificationViewModel: NotificationViewModel =
        viewModel(key = state.user.id, factory = NotificationViewModel.Factory)
    val unreadCount = notificationViewModel.uiState.unreadCount
    // Calendar top-bar actions (iOS toolbar list.bullet / plus); the sheets they
    // trigger are rendered inside CalendarScreen.
    val calendarTopBar = remember { CalendarTopBarState() }
    val profileTopBar = remember { ProfileTopBarState() }
    // Chat top-bar actions (iOS toolbar archivebox / square.and.pencil); the
    // archived sheet and create flow live in ChatScreen. Keyed by user id so
    // switching accounts rebuilds the chat state.
    val chatTopBar = remember { ChatTopBarState() }
    val chatViewModel: ChatViewModel =
        viewModel(key = "chat:${state.user.id}", factory = ChatViewModel.Factory)
    // Reading top-bar actions (iOS toolbar checkmark.circle / plus); hoisted
    // like the notification ViewModel so the top bar can reflect unreadCount
    // and the mark-all-read spinner. Keyed by user id for per-account reset.
    val readingTopBar = remember { ReadingTopBarState() }
    val readingViewModel: ReadingViewModel =
        viewModel(key = "reading:${state.user.id}", factory = ReadingViewModel.Factory)
    // Contacts has no top-bar actions (iOS has none); hoisted and keyed by
    // user id like Reading so switching accounts rebuilds its state.
    val contactsViewModel: ContactsViewModel =
        viewModel(key = "contacts:${state.user.id}", factory = ContactsViewModel.Factory)
    // App-wide realtime subscription: the LaunchedEffect follows the signed-in
    // state, so signing out (leaving composition) disconnects the SSE stream.
    LaunchedEffect(state.user.id) {
        notificationViewModel.loadIfNeeded()
        notificationViewModel.listenForRealtimeEvents()
    }

    // The chat detail renders its own header (back + title + actions), so the
    // scaffold top bar is hidden while it is open.
    val hideTopBar = currentSection == AppSection.CHAT && chatTopBar.detailOpen

    Scaffold(
        topBar = {
            if (!hideTopBar) {
                TopAppBar(
                title = { Text(currentSection.title) },
                navigationIcon = {
                    if (showMenuButton) {
                        IconButton(onClick = onMenuClick) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open navigation")
                        }
                    }
                },
                actions = {
                    if (currentSection == AppSection.CHAT && !chatTopBar.detailOpen) {
                        IconButton(onClick = { chatTopBar.showArchived = true }) {
                            Icon(
                                Icons.Outlined.Archive,
                                contentDescription = "Archived conversations",
                            )
                        }
                        IconButton(
                            onClick = { chatTopBar.requestCreate() },
                            enabled = !chatViewModel.uiState.isCreating,
                        ) {
                            if (chatViewModel.uiState.isCreating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    Icons.Outlined.Create,
                                    contentDescription = "New conversation",
                                )
                            }
                        }
                    } else if (currentSection == AppSection.CALENDAR) {
                        IconButton(onClick = { calendarTopBar.showAllEvents = true }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.List,
                                contentDescription = "All events",
                            )
                        }
                        IconButton(onClick = { calendarTopBar.requestNewEvent() }) {
                            Icon(Icons.Filled.Add, contentDescription = "New event")
                        }
                    } else if (currentSection == AppSection.PROFILE) {
                        IconButton(onClick = { profileTopBar.showEditProfile = true }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit profile")
                        }
                    } else if (currentSection == AppSection.READING) {
                        if (readingViewModel.uiState.unreadCount > 0) {
                            IconButton(
                                onClick = { readingViewModel.markAllRead() },
                                enabled = !readingViewModel.uiState.isMarkingAllRead,
                            ) {
                                if (readingViewModel.uiState.isMarkingAllRead) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        Icons.Outlined.CheckCircle,
                                        contentDescription = "Mark all as read",
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { readingTopBar.requestAdd() }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add reading item")
                        }
                    } else if (currentSection == AppSection.DASHBOARD) {
                        IconButton(onClick = { onOpenSection(AppSection.NOTIFICATIONS) }) {
                            BadgedBox(
                                badge = {
                                    if (unreadCount > 0) {
                                        Badge { Text("$unreadCount") }
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Outlined.Notifications,
                                    contentDescription = "Notifications",
                                )
                            }
                        }
                    }
                },
            )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppSection.DASHBOARD.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AppSection.entries.forEach { section ->
                composable(section.route) {
                    when (section) {
                        AppSection.DASHBOARD -> DashboardScreen(state, viewModel, onOpenSection)
                        AppSection.CHAT -> ChatScreen(chatViewModel, chatTopBar)
                        AppSection.PROFILE -> ProfileScreen(state, viewModel, profileTopBar)
                        AppSection.SETTINGS -> SettingsScreen(state, viewModel)
                        AppSection.NOTIFICATIONS -> NotificationsScreen(notificationViewModel)
                        AppSection.BOARD -> BoardScreen(state)
                        AppSection.CALENDAR -> CalendarScreen(calendarTopBar)
                        AppSection.DOCS -> DocsScreen()
                        AppSection.READING -> ReadingScreen(readingViewModel, readingTopBar)
                        AppSection.CONTACTS -> ContactsScreen(contactsViewModel, state.user.id)
                        else -> PlaceholderScreen(section)
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerContent(
    state: AuthState.LoggedIn,
    avatarUrl: String?,
    currentSection: AppSection,
    onOpenSection: (AppSection) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        Text(
            text = "Workavera",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 28.dp),
        )
        Spacer(Modifier.height(16.dp))

        UserHeader(
            state = state,
            avatarUrl = avatarUrl,
            selected = currentSection == AppSection.PROFILE,
            onClick = { onOpenSection(AppSection.PROFILE) },
        )
        Spacer(Modifier.height(8.dp))

        DrawerGroup("Overview", AppSection.overview, currentSection, onOpenSection)
        DrawerGroup("Workflow", AppSection.workflow, currentSection, onOpenSection)
        DrawerGroup("System", AppSection.system, currentSection, onOpenSection)
    }
}

@Composable
private fun DrawerGroup(
    label: String,
    sections: List<AppSection>,
    currentSection: AppSection,
    onOpenSection: (AppSection) -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
    )
    sections.forEach { section ->
        NavigationDrawerItem(
            label = { Text(section.title) },
            icon = { Icon(section.icon, contentDescription = null) },
            selected = section == currentSection,
            onClick = { onOpenSection(section) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
    }
}

@Composable
private fun UserHeader(
    state: AuthState.LoggedIn,
    avatarUrl: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val user = state.user
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarView(
                url = avatarUrl,
                name = user.displayName,
                size = 44.dp,
                status = user.userStatus,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
                Text(
                    text = user.userStatus.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
