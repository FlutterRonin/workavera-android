package com.workavera.android.ui.reading

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workavera.android.WorkaveraApplication
import com.workavera.android.data.ReadingRepository
import com.workavera.android.data.model.CandidateSummary
import com.workavera.android.data.model.DiscoverCandidate
import com.workavera.android.data.model.DiscoverFailure
import com.workavera.android.data.model.ReadingItemInput
import com.workavera.android.data.model.ReadingSource
import com.workavera.android.data.model.ReadingSourceInput
import com.workavera.android.data.model.SummaryLanguage
import com.workavera.android.data.network.ApiException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/**
 * Drives the Discover sheet: subscriptions the user owns, the candidates a
 * fetch brings back, and the summaries requested before anything is kept
 * (mirrors the iOS ReadingDiscoverStore). The summary language is persisted
 * across launches, like the iOS UserDefaults key.
 */
class ReadingDiscoverViewModel(
    private val repository: ReadingRepository,
    application: Application,
) : ViewModel() {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)

    var sources by mutableStateOf<List<ReadingSource>>(emptyList())
        private set
    var candidates by mutableStateOf<List<DiscoverCandidate>>(emptyList())
        private set
    var failures by mutableStateOf<List<DiscoverFailure>>(emptyList())
        private set
    /** Summaries keyed by candidate URL, matching the web panel. */
    var summaries by mutableStateOf<Map<String, CandidateSummary>>(emptyMap())
        private set
    var isLoadingSources by mutableStateOf(false)
        private set
    var isFetching by mutableStateOf(false)
        private set
    var hasFetched by mutableStateOf(false)
        private set
    var isSavingSource by mutableStateOf(false)
        private set
    var summarizingUrl by mutableStateOf<String?>(null)
        private set
    var savingUrl by mutableStateOf<String?>(null)
        private set
    var savedCount by mutableStateOf(0)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** The language candidates are summarized into; persisted across launches. */
    var language by mutableStateOf(
        SummaryLanguage.fromRaw(prefs.getString(KEY_LANGUAGE, null)),
    )
        private set

    val enabledSources: List<ReadingSource> get() = sources.filter { it.enabled }

    fun selectLanguage(newLanguage: SummaryLanguage) {
        language = newLanguage
        prefs.edit().putString(KEY_LANGUAGE, newLanguage.raw).apply()
    }

    fun loadSources() {
        if (isLoadingSources) return
        isLoadingSources = true
        viewModelScope.launch {
            try {
                sources = repository.loadSources()
                errorMessage = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = message(e)
            } finally {
                isLoadingSources = false
            }
        }
    }

    /** Suspends so the editor sheet can keep its own error local and close only on success. */
    suspend fun createSource(input: ReadingSourceInput) {
        if (isSavingSource) return
        isSavingSource = true
        try {
            repository.createSource(input)
            errorMessage = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = message(e)
            throw e
        } finally {
            isSavingSource = false
        }
        reloadSources()
    }

    suspend fun updateSource(id: String, input: ReadingSourceInput) {
        if (isSavingSource) return
        isSavingSource = true
        try {
            repository.updateSource(id, input)
            errorMessage = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = message(e)
            throw e
        } finally {
            isSavingSource = false
        }
        reloadSources()
    }

    /** Callback-based save for the editor sheet; null error means success. */
    fun saveSource(sourceId: String?, input: ReadingSourceInput, onComplete: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                if (sourceId == null) {
                    createSource(input)
                } else {
                    updateSource(sourceId, input)
                }
                onComplete(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onComplete(message(e))
            }
        }
    }

    fun setSourceEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.setSourceEnabled(id, enabled)
                errorMessage = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = message(e)
                return@launch
            }
            reloadSources()
        }
    }

    fun deleteSource(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteSource(id)
                candidates = candidates.filterNot { it.sourceId == id }
                errorMessage = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = message(e)
                return@launch
            }
            reloadSources()
        }
    }

    /**
     * Reads every enabled subscription and returns candidates that are not
     * stored yet, so each one can be weighed before anything is kept. The
     * enable toggle on a subscription is the selection: there is no second
     * switch for the same choice.
     */
    fun discover() {
        if (isFetching || enabledSources.isEmpty()) return
        isFetching = true
        viewModelScope.launch {
            try {
                val result = repository.discover(enabledSources.map { it.id })
                candidates = result.items
                failures = result.failures
                summaries = emptyMap()
                hasFetched = true
                errorMessage = null
                // The sources carry a fresh fetch time and error now.
                reloadSources()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = message(e)
            } finally {
                isFetching = false
            }
        }
    }

    fun summarize(candidate: DiscoverCandidate) {
        if (summarizingUrl != null) return
        summarizingUrl = candidate.url
        viewModelScope.launch {
            try {
                val summary = repository.summarizeCandidate(
                    candidate.url,
                    candidate.title,
                    language.raw,
                )
                summaries = summaries + (candidate.url to summary)
                errorMessage = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = message(e)
            } finally {
                summarizingUrl = null
            }
        }
    }

    /** Keeps one candidate as an ordinary reading item, carrying the summary the user asked for. */
    fun save(candidate: DiscoverCandidate) {
        if (savingUrl != null || candidate.alreadySaved) return
        savingUrl = candidate.url
        viewModelScope.launch {
            try {
                val summary = summaries[candidate.url]
                repository.create(
                    ReadingItemInput(
                        url = candidate.url,
                        title = candidate.title.take(240),
                        description = candidate.description,
                        tags = if (candidate.language.isEmpty()) {
                            emptyList()
                        } else {
                            listOf(candidate.language)
                        },
                        summary = summary?.summary.orEmpty(),
                        keyPoints = summary?.keyPoints.orEmpty(),
                        summaryLanguage = summary?.language.orEmpty(),
                    ),
                )
                candidates = candidates.map {
                    if (it.url == candidate.url) it.copy(alreadySaved = true) else it
                }
                savedCount += 1
                errorMessage = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = message(e)
            } finally {
                savingUrl = null
            }
        }
    }

    /** Reports whether the reading list needs a refresh once the sheet closes, and resets the count. */
    fun consumeSavedCount(): Int {
        val count = savedCount
        savedCount = 0
        return count
    }

    fun clearError() {
        errorMessage = null
    }

    private suspend fun reloadSources() {
        try {
            sources = repository.loadSources()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A reload after a successful write should not mask it with an error.
        }
    }

    private fun message(e: Exception): String =
        (e as? ApiException)?.userMessage ?: e.message ?: "Something went wrong."

    companion object {
        private const val PREFS_NAME = "workavera.reading.discover"
        private const val KEY_LANGUAGE = "language"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as WorkaveraApplication
                ReadingDiscoverViewModel(app.container.readingRepository, app)
            }
        }
    }
}
