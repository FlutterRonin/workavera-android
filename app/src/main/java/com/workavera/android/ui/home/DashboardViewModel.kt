package com.workavera.android.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workavera.android.WorkaveraApplication
import com.workavera.android.data.DashboardRepository
import com.workavera.android.data.model.DashboardSummary
import com.workavera.android.data.network.ApiException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

data class DashboardUiState(
    val summary: DashboardSummary? = null,
    val isLoading: Boolean = false,
    /** Set true after the first successful load; later refreshes skip the full-screen loading. */
    val hasLoaded: Boolean = false,
    val error: String? = null,
)

/** Loads and refreshes the dashboard stats, mirroring the iOS DashboardStore (without SSE realtime). */
class DashboardViewModel(
    private val repository: DashboardRepository,
) : ViewModel() {

    var uiState by mutableStateOf(DashboardUiState())
        private set

    /**
     * Shared load-on-enter strategy (matching the iOS CachedPageLoader):
     * render the disk cache first (silently skipped when absent), then do one
     * silent network refresh.
     */
    fun loadIfNeeded() {
        if (uiState.hasLoaded || uiState.isLoading) return
        viewModelScope.launch {
            repository.cachedSummary()?.let { cached ->
                uiState = uiState.copy(summary = cached, hasLoaded = true)
            }
            refresh()
        }
    }

    fun refresh() {
        if (uiState.isLoading) return
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            uiState = try {
                val summary = repository.loadSummary()
                DashboardUiState(summary = summary, hasLoaded = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                uiState.copy(isLoading = false, error = e.userMessage)
            } catch (e: Exception) {
                uiState.copy(
                    isLoading = false,
                    error = e.message ?: "Could not load the dashboard.",
                )
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as WorkaveraApplication
                DashboardViewModel(app.container.dashboardRepository)
            }
        }
    }
}
