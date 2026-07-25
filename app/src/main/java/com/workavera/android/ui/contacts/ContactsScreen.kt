package com.workavera.android.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workavera.android.data.model.Contact
import com.workavera.android.data.model.UserStatus
import com.workavera.android.ui.components.AvatarView
import com.workavera.android.ui.components.ErrorBanner
import com.workavera.android.ui.components.ExpressivePullToRefreshBox
import com.workavera.android.ui.components.LoadingIndicator
import com.workavera.android.ui.components.SearchTextField
import com.workavera.android.ui.theme.color

/** Amber of the iOS favorite star (Color(hex: "#f59e0b")). */
private val FavoriteStar = Color(0xFFF59E0B)

/**
 * Contacts directory, mirroring the iOS ContactsView: live client-side search,
 * status filter chips, Favorites / Everyone sections of contact rows, and a
 * detail sheet. Read-only — the only mutation is favorite/unfavorite.
 */
@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel,
    currentUserId: String,
) {
    val ui = viewModel.uiState
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<UserStatus?>(null) }
    var selectedContactId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    val normalizedQuery = query.trim().lowercase()
    val filtered = ui.contacts
        .filter { contact ->
            if (statusFilter != null && contact.userStatus != statusFilter) return@filter false
            if (normalizedQuery.isEmpty()) return@filter true
            contact.displayName.lowercase().contains(normalizedQuery) ||
                contact.email.lowercase().contains(normalizedQuery) ||
                (contact.titleOrNull?.lowercase()?.contains(normalizedQuery) == true)
        }
        .sortedBy { it.displayName.lowercase() }
    val favorites = filtered.filter { it.isFavorite }
    val everyone = filtered.filter { !it.isFavorite }

    ExpressivePullToRefreshBox(
        isRefreshing = ui.isLoading,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!ui.hasLoaded && ui.isLoading) {
            LoadingIndicator("Loading contacts…")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (ui.error != null) {
                    item {
                        ErrorBanner(
                            message = ui.error,
                            onRetry = viewModel::refresh,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
                item {
                    // iOS filters live as you type; there is no server-side search.
                    SearchTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search name, email or title",
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                item {
                    StatusFilterRow(selected = statusFilter, onSelect = { statusFilter = it })
                }

                if (filtered.isEmpty()) {
                    item { EmptyContacts(hasContacts = ui.contacts.isNotEmpty()) }
                } else {
                    if (favorites.isNotEmpty()) {
                        item {
                            ContactSection(
                                title = "Favorites",
                                contacts = favorites,
                                currentUserId = currentUserId,
                                mutatingContactIds = ui.mutatingContactIds,
                                onSelect = { selectedContactId = it.id },
                            )
                        }
                    }
                    if (everyone.isNotEmpty()) {
                        item {
                            ContactSection(
                                title = "Everyone",
                                contacts = everyone,
                                currentUserId = currentUserId,
                                mutatingContactIds = ui.mutatingContactIds,
                                onSelect = { selectedContactId = it.id },
                            )
                        }
                    }
                }
            }
        }
    }

    selectedContactId?.let { contactId ->
        ContactDetailSheet(
            contactId = contactId,
            viewModel = viewModel,
            currentUserId = currentUserId,
            onDismiss = { selectedContactId = null },
        )
    }
}

/** Horizontally scrolling status chips (iOS statusFilterPicker / WorkspaceFilterChip). */
@Composable
private fun StatusFilterRow(
    selected: UserStatus?,
    onSelect: (UserStatus?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusFilterChip(
            title = "All",
            dotColor = null,
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        UserStatus.entries.forEach { status ->
            StatusFilterChip(
                title = status.label,
                dotColor = status.color,
                selected = selected == status,
                onClick = { onSelect(status) },
            )
        }
    }
}

@Composable
private fun StatusFilterChip(
    title: String,
    dotColor: Color?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun EmptyContacts(hasContacts: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.Group,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "No Contacts Found",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (hasContacts) "Try another search or status." else "No other users are available.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One rounded section card with inset dividers between rows (iOS contactSection). */
@Composable
private fun ContactSection(
    title: String,
    contacts: List<Contact>,
    currentUserId: String,
    mutatingContactIds: Set<String>,
    onSelect: (Contact) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            contacts.forEachIndexed { index, contact ->
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
                ContactRow(
                    contact = contact,
                    isSelf = contact.id == currentUserId,
                    isBusy = contact.id in mutatingContactIds,
                    onClick = { onSelect(contact) },
                )
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: Contact,
    isSelf: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarView(
            url = contact.avatarUrl,
            name = contact.displayName,
            size = 44.dp,
            status = contact.userStatus,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = contact.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isSelf) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "YOU",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (contact.isFavorite) {
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Favorite",
                        modifier = Modifier.size(10.dp),
                        tint = FavoriteStar,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = contact.userStatus.label +
                    (contact.titleOrNull?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(4.dp))
        if (isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

/**
 * Detail sheet (iOS ContactDetailSheet): centered header, favorite toggle
 * (hidden for self), an email/phone info card, and an About section for bios.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactDetailSheet(
    contactId: String,
    viewModel: ContactsViewModel,
    currentUserId: String,
    onDismiss: () -> Unit,
) {
    val ui = viewModel.uiState
    val contact = ui.contacts.firstOrNull { it.id == contactId }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (contact == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Contact Unavailable",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AvatarView(
                        url = contact.avatarUrl,
                        name = contact.displayName,
                        size = 72.dp,
                        status = contact.userStatus,
                    )
                    Text(
                        text = contact.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(contact.userStatus.color),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = contact.userStatus.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    contact.titleOrNull?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (contact.id != currentUserId) {
                        OutlinedButton(
                            onClick = { viewModel.toggleFavorite(contact.id) },
                            enabled = contact.id !in ui.mutatingContactIds,
                        ) {
                            if (contact.id in ui.mutatingContactIds) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    if (contact.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(if (contact.isFavorite) "Favorited" else "Add to Favorites")
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    ContactInfoRow(icon = Icons.Outlined.Email, title = "Email", value = contact.email)
                    contact.phoneOrNull?.let { phone ->
                        HorizontalDivider(modifier = Modifier.padding(start = 54.dp))
                        ContactInfoRow(icon = Icons.Outlined.Phone, title = "Phone", value = phone)
                    }
                }

                contact.bioOrNull?.let { bio ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "About",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactInfoRow(
    icon: ImageVector,
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(26.dp)) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
