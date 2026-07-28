package com.workavera.android.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workavera.android.WorkaveraApplication
import com.workavera.android.data.McpServersRepository
import com.workavera.android.data.model.McpRefreshResult
import com.workavera.android.data.model.McpServer
import com.workavera.android.data.model.McpTool
import com.workavera.android.data.network.ApiException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

data class McpServersUiState(
    val servers: List<McpServer> = emptyList(),
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
    /** Ids of servers with an inline operation in flight (toggle / delete). */
    val busyIds: Set<String> = emptySet(),
)

/**
 * Backs the MCP Servers screen: list with enable toggles, add / edit / delete,
 * refresh (locked-definition diff), and the tool review selection.
 */
class McpServersViewModel(
    private val repository: McpServersRepository,
) : ViewModel() {

    var uiState by mutableStateOf(McpServersUiState())
        private set

    /** Cache-first: render the disk snapshot, then refresh over the network. */
    fun loadIfNeeded() {
        if (uiState.hasLoaded || uiState.isLoading) return
        repository.cachedServers()?.let { cached ->
            uiState = uiState.copy(servers = cached, hasLoaded = true)
        }
        refresh()
    }

    fun refresh() {
        if (uiState.isLoading) return
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            uiState = try {
                val servers = repository.load()
                uiState.copy(servers = servers, isLoading = false, hasLoaded = true, error = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState.copy(
                    isLoading = false,
                    hasLoaded = true,
                    error = message(e, "Could not load your MCP servers."),
                )
            }
        }
    }

    fun create(
        name: String,
        slug: String,
        transport: String,
        url: String,
        headers: Map<String, String>,
        approvalPolicy: String,
        onComplete: (McpServer?, String?) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val server = repository.create(
                    name = name.trim(),
                    slug = slug.trim().lowercase(),
                    transport = transport,
                    url = url.trim(),
                    headers = headers,
                    approvalPolicy = approvalPolicy,
                )
                refresh()
                onComplete(server, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onComplete(null, message(e, "Could not save the server."))
            }
        }
    }

    /** [headers] of null means the field was left blank, so stored credentials stay untouched. */
    fun update(
        id: String,
        name: String,
        url: String,
        transport: String,
        headers: Map<String, String>?,
        approvalPolicy: String,
        onComplete: (McpServer?, String?) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val server = repository.update(
                    id,
                    name = name.trim(),
                    url = url.trim(),
                    transport = transport,
                    headers = headers,
                    approvalPolicy = approvalPolicy,
                )
                refresh()
                onComplete(server, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onComplete(null, message(e, "Could not update the server."))
            }
        }
    }

    /**
     * Toggles whether the server's tools are offered to Chat. A failed
     * connection never auto-disables a server: that would overwrite the user's
     * choice over what may be a temporary outage.
     */
    fun setEnabled(server: McpServer, enabled: Boolean) {
        if (server.id in uiState.busyIds) return
        uiState = uiState.copy(busyIds = uiState.busyIds + server.id)
        viewModelScope.launch {
            try {
                val updated = repository.setEnabled(server.id, enabled)
                uiState = uiState.copy(
                    servers = uiState.servers.map { if (it.id == server.id) updated else it },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(error = message(e, "Could not update the server."))
            } finally {
                uiState = uiState.copy(busyIds = uiState.busyIds - server.id)
            }
        }
    }

    /**
     * Refreshes the locked definitions from upstream. onComplete receives the
     * result (ok=false when the server could not be reached) or an error
     * message when the request itself failed. The list is reloaded either way:
     * a failed refresh still writes last_error to the record.
     */
    fun refreshServer(server: McpServer, onComplete: (McpRefreshResult?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val result = repository.refresh(server.id)
                refresh()
                onComplete(result, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                refresh()
                onComplete(null, message(e, "Could not refresh the server."))
            }
        }
    }

    /** Applies the review decisions; the only path that makes a tool available to Chat. */
    fun saveToolSelection(
        server: McpServer,
        tools: List<McpTool>,
        onComplete: (McpServer?, String?) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val updated = repository.saveToolSelection(server.id, tools)
                refresh()
                onComplete(updated, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onComplete(null, message(e, "Could not save the tool selection."))
            }
        }
    }

    fun delete(server: McpServer) {
        if (server.id in uiState.busyIds) return
        uiState = uiState.copy(busyIds = uiState.busyIds + server.id)
        viewModelScope.launch {
            try {
                repository.delete(server.id)
                uiState = uiState.copy(servers = uiState.servers.filterNot { it.id == server.id })
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(error = message(e, "Could not remove the server."))
            } finally {
                uiState = uiState.copy(busyIds = uiState.busyIds - server.id)
            }
        }
    }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }

    private fun message(e: Exception, fallback: String): String =
        (e as? ApiException)?.userMessage ?: e.message ?: fallback

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as WorkaveraApplication
                McpServersViewModel(app.container.mcpServersRepository)
            }
        }
    }
}
