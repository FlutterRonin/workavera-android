package com.workavera.android.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.workavera.android.data.AuthState
import com.workavera.android.data.model.AvatarUpload
import com.workavera.android.data.model.ProfileUpdate
import com.workavera.android.data.model.UserStatus
import com.workavera.android.ui.AuthViewModel
import com.workavera.android.ui.components.AvatarView
import com.workavera.android.ui.components.StatusSelect

/**
 * Trigger for the Edit action in the home top bar (the iOS toolbar's "Edit"
 * button). The button is rendered by HomeScaffold; the dialog stays here.
 */
class ProfileTopBarState {
    var showEditProfile by mutableStateOf(false)
}

/**
 * Profile screen, styled after the grouped list of the iOS ProfileView: small
 * gray titles outside the groups + white hairline-border cards (hairline row
 * dividers), avoiding large gray blocks. Edit profile / change password use
 * full-screen dialogs per Android's Material 3 form conventions.
 */
@Composable
fun ProfileScreen(
    state: AuthState.LoggedIn,
    viewModel: AuthViewModel,
    topBarState: ProfileTopBarState = remember { ProfileTopBarState() },
) {
    val user = state.user
    val ui = viewModel.profileUiState
    var showChangePassword by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showAvatarPreview by remember { mutableStateOf(false) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    var avatarUploading by remember { mutableStateOf(false) }
    var avatarError by remember { mutableStateOf<String?>(null) }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) cropUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AvatarView(
                url = viewModel.avatarUrl(state),
                name = user.displayName,
                size = 96.dp,
                // shadow's default clip=true would crop the status dot that
                // sticks out of the circle, so it must be turned off.
                modifier = Modifier
                    .shadow(8.dp, CircleShape, clip = false)
                    .clickable { showAvatarPreview = true },
                status = user.userStatus,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = user.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            if (user.title.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = user.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))

            GroupTitle("Status")
            GroupCard {
                StatusSelect(
                    current = user.userStatus,
                    updating = ui.updatingStatus != null,
                    onSelect = viewModel::updateStatus,
                )
                if (ui.error != null) {
                    Text(
                        text = ui.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            GroupTitle("Details")
            GroupCard {
                LabeledRow("Phone", user.phone)
                GroupDivider()
                LabeledRow("Title", user.title)
                GroupDivider()
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = "Bio",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = user.bio.ifEmpty { "Not set" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            GroupTitle("Account")
            GroupCard {
                LabeledRow("User ID", user.id)
                GroupDivider()
                LabeledRow("Joined", user.created.take(10).ifEmpty { "Not set" })
                GroupDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showChangePassword = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Change Password",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))

            OutlinedButton(
                onClick = { showSignOutConfirm = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Sign Out",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (topBarState.showEditProfile) {
        EditProfileDialog(
            state = state,
            viewModel = viewModel,
            onDismiss = { topBarState.showEditProfile = false },
        )
    }
    if (showChangePassword) {
        ChangePasswordDialog(
            viewModel = viewModel,
            onDismiss = { showChangePassword = false },
        )
    }
    if (showAvatarPreview) {
        AvatarPreviewDialog(
            url = viewModel.avatarUrl(state),
            name = user.displayName,
            status = user.userStatus,
            isUploading = avatarUploading,
            error = avatarError,
            onChangePhoto = {
                avatarPicker.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ),
                )
            },
            onDismiss = {
                showAvatarPreview = false
                avatarError = null
            },
        )
    }
    cropUri?.let { uri ->
        AvatarCropDialog(
            uri = uri,
            onConfirm = { bytes ->
                cropUri = null
                avatarUploading = true
                avatarError = null
                val profile = ProfileUpdate(
                    name = user.name,
                    phone = user.phone,
                    title = user.title,
                    bio = user.bio,
                    status = user.userStatus,
                )
                viewModel.updateProfile(
                    profile,
                    AvatarUpload(bytes, "avatar.jpg", "image/jpeg"),
                ) { error ->
                    avatarUploading = false
                    avatarError = error
                }
            },
            onDismiss = { cropUri = null },
        )
    }
    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign Out") },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirm = false
                        viewModel.logout()
                    },
                ) {
                    Text(
                        text = "Sign Out",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

/** Small title outside an iOS-style group (gray, small, uppercase). */
@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, bottom = 6.dp),
    )
}

/** iOS-style group card: pure white (dark surface in dark mode) + hairline border, no tonal gray. */
@Composable
private fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}

/** Row divider, inset on the left by one row padding per iOS convention. */
@Composable
private fun GroupDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = 16.dp),
    )
}

/** iOS LabeledContent style: label on the left, value right-aligned and dimmed. */
@Composable
private fun LabeledRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value.ifEmpty { "Not set" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
