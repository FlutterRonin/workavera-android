package com.workavera.android.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mr0xf00.easycrop.CropError
import com.mr0xf00.easycrop.CropResult
import com.mr0xf00.easycrop.crop
import com.mr0xf00.easycrop.rememberImageCropper
import com.workavera.android.data.AuthState
import com.workavera.android.data.model.AvatarUpload
import com.workavera.android.data.model.ProfileUpdate
import com.workavera.android.ui.AuthViewModel
import com.workavera.android.ui.components.AvatarView
import com.workavera.android.ui.components.StatusSelect
import com.workavera.android.ui.settings.FormCard
import com.workavera.android.ui.settings.FormSectionTitle
import com.workavera.android.ui.settings.FormTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Edit profile: a full-screen dialog (Material 3 form convention), mirroring the iOS EditProfileSheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileDialog(
    state: AuthState.LoggedIn,
    viewModel: AuthViewModel,
    onDismiss: () -> Unit,
) {
    val user = state.user

    var name by remember { mutableStateOf(user.name) }
    var phone by remember { mutableStateOf(user.phone) }
    var title by remember { mutableStateOf(user.title) }
    var bio by remember { mutableStateOf(user.bio) }
    var status by remember { mutableStateOf(user.userStatus) }
    var avatarBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageCropper = rememberImageCropper()

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                when (val result = imageCropper.crop(uri, context)) {
                    is CropResult.Success -> {
                        avatarBytes = withContext(Dispatchers.Default) {
                            AvatarImageProcessor.compressAvatar(
                                result.bitmap.asAndroidBitmap(),
                            )
                        }
                        errorMessage = null
                    }
                    is CropError -> errorMessage = "Could not load the photo."
                    else -> Unit // Cancelled
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Edit Profile") },
                        navigationIcon = {
                            IconButton(
                                onClick = onDismiss,
                                enabled = !isSaving,
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Close")
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = {
                                    isSaving = true
                                    errorMessage = null
                                    val profile = ProfileUpdate(
                                        name = name.trim(),
                                        phone = phone.trim(),
                                        title = title.trim(),
                                        bio = bio.trim(),
                                        status = status,
                                    )
                                    val avatar = avatarBytes?.let {
                                        AvatarUpload(it, "avatar.jpg", "image/jpeg")
                                    }
                                    viewModel.updateProfile(profile, avatar) { error ->
                                        isSaving = false
                                        if (error == null) onDismiss() else errorMessage = error
                                    }
                                },
                                enabled = name.isNotBlank() && !isSaving,
                            ) {
                                Text(if (isSaving) "Saving…" else "Save")
                            }
                        },
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val preview = avatarBytes
                        val previewBitmap = remember(preview) {
                            preview?.let {
                                android.graphics.BitmapFactory
                                    .decodeByteArray(it, 0, it.size)
                                    ?.asImageBitmap()
                            }
                        }
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap,
                                contentDescription = "New avatar",
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            AvatarView(
                                url = viewModel.avatarUrl(state),
                                name = name.ifBlank { user.displayName },
                                size = 96.dp,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                            enabled = !isSaving,
                        ) {
                            Text("Change Photo")
                        }
                        Spacer(Modifier.height(16.dp))

                        FormSectionTitle("Details")
                        FormCard {
                            FormTextField(
                                value = name,
                                onValueChange = { name = it },
                                placeholder = "Name",
                                enabled = !isSaving,
                            )
                            ProfileFormDivider()
                            FormTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                placeholder = "Phone",
                                enabled = !isSaving,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            )
                            ProfileFormDivider()
                            FormTextField(
                                value = title,
                                onValueChange = { title = it },
                                placeholder = "Title",
                                enabled = !isSaving,
                            )
                            ProfileFormDivider()
                            TextField(
                                value = bio,
                                onValueChange = { bio = it },
                                placeholder = { Text("Bio") },
                                enabled = !isSaving,
                                minLines = 3,
                                maxLines = 8,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(16.dp))

                        FormSectionTitle("Status")
                        FormCard {
                            StatusSelect(
                                current = status,
                                updating = false,
                                enabled = !isSaving,
                                onSelect = { status = it },
                            )
                        }

                        if (errorMessage != null) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    imageCropper.cropState?.let { cropState ->
        AvatarCropperDialog(state = cropState)
    }
}

@Composable
private fun ProfileFormDivider() {
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}
