package com.workavera.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workavera.android.data.AuthState
import com.workavera.android.ui.home.HomeScaffold
import com.workavera.android.ui.login.LoginScreen
import com.workavera.android.ui.login.SessionRecoveryScreen
import kotlinx.coroutines.flow.MutableStateFlow

/** Root router: switches between restoring / recovery-failed / sign-in / home by auth state. */
@Composable
fun WorkaveraApp(
    requestedSection: MutableStateFlow<AppSection?>,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory),
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (val state = authState) {
            AuthState.Restoring -> RestoringScreen()
            is AuthState.RecoveryFailed -> SessionRecoveryScreen(
                message = state.message,
                onRetry = viewModel::retryRestore,
                onSignInAgain = viewModel::abandonRecovery,
            )
            AuthState.LoggedOut -> LoginScreen(viewModel)
            is AuthState.LoggedIn -> HomeScaffold(state, viewModel, requestedSection)
        }
    }
}

@Composable
private fun RestoringScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
