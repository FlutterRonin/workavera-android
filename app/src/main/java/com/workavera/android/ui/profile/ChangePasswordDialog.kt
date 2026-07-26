package com.workavera.android.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.workavera.android.ui.AuthViewModel
import com.workavera.android.ui.settings.FormCard
import com.workavera.android.ui.settings.FormFooter
import com.workavera.android.ui.settings.FormSectionTitle
import com.workavera.android.ui.settings.FormTextField

/** Change password: a full-screen dialog, mirroring the iOS ChangePasswordSheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordDialog(
    viewModel: AuthViewModel,
    onDismiss: () -> Unit,
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isValid = currentPassword.isNotEmpty() &&
        newPassword.length >= 8 &&
        newPassword == confirmation

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Change Password") },
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
                                    viewModel.changePassword(currentPassword, newPassword) { error ->
                                        isSaving = false
                                        if (error == null) onDismiss() else errorMessage = error
                                    }
                                },
                                enabled = isValid && !isSaving,
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
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth(),
                    ) {
                        FormSectionTitle("Password")
                        FormCard {
                            PasswordField(
                                value = currentPassword,
                                onValueChange = { currentPassword = it },
                                placeholder = "Current Password",
                                enabled = !isSaving,
                            )
                            PasswordFormDivider()
                            PasswordField(
                                value = newPassword,
                                onValueChange = { newPassword = it },
                                placeholder = "New Password",
                                enabled = !isSaving,
                            )
                            PasswordFormDivider()
                            PasswordField(
                                value = confirmation,
                                onValueChange = { confirmation = it },
                                placeholder = "Confirm New Password",
                                enabled = !isSaving,
                            )
                        }
                        FormFooter("Use at least 8 characters.")

                        if (errorMessage != null) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
) {
    var visible by remember { mutableStateOf(false) }
    FormTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        enabled = enabled,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password",
                )
            }
        },
    )
}

@Composable
private fun PasswordFormDivider() {
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}
