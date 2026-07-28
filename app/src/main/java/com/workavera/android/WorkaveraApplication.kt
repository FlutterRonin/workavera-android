package com.workavera.android

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.workavera.android.data.AiConsentRepository
import com.workavera.android.data.ApiKeysRepository
import com.workavera.android.data.AuthRepository
import com.workavera.android.data.BoardRepository
import com.workavera.android.data.CalendarRepository
import com.workavera.android.data.ChatRepository
import com.workavera.android.data.ContactsRepository
import com.workavera.android.data.DashboardRepository
import com.workavera.android.data.DocsRepository
import com.workavera.android.data.LlmModelsRepository
import com.workavera.android.data.McpServersRepository
import com.workavera.android.data.NotificationRepository
import com.workavera.android.data.PreferencesRepository
import com.workavera.android.data.ReadingRepository
import com.workavera.android.data.ResponseCache
import com.workavera.android.data.SavedMemoriesRepository
import com.workavera.android.data.SettingsRepository
import com.workavera.android.data.network.PocketBaseApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Manual dependency injection container; the app is small enough to skip Hilt. */
class AppContainer(context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val httpClient = HttpClient(OkHttp) {
        expectSuccess = false
        // Installed only to enable per-request timeout configuration (needed by
        // long-lived SSE connections); no global timeout is set.
        install(HttpTimeout)
        install(ContentNegotiation) {
            json(this@AppContainer.json)
        }
    }

    val settings = SettingsRepository(context)
    val api = PocketBaseApi(httpClient, json)
    val authRepository = AuthRepository(api, settings)
    val preferencesRepository = PreferencesRepository(api, authRepository, settings)
    val responseCache = ResponseCache(context, json)
    val apiKeysRepository = ApiKeysRepository(api, authRepository, responseCache)
    val mcpServersRepository = McpServersRepository(api, authRepository, responseCache)
    val savedMemoriesRepository = SavedMemoriesRepository(api, authRepository, responseCache)
    val dashboardRepository = DashboardRepository(api, authRepository, responseCache)
    val notificationRepository = NotificationRepository(api, authRepository, responseCache)
    val boardRepository = BoardRepository(api, authRepository, responseCache)
    val llmModelsRepository = LlmModelsRepository(api, authRepository, responseCache)
    val calendarRepository = CalendarRepository(api, authRepository, responseCache)
    val chatRepository = ChatRepository(api, authRepository, responseCache)
    val docsRepository = DocsRepository(api, authRepository, responseCache)
    val readingRepository = ReadingRepository(api, authRepository, responseCache)
    val contactsRepository = ContactsRepository(api, authRepository, responseCache)
    val aiConsentRepository = AiConsentRepository(context)
}

class WorkaveraApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /** Coil 3 needs its network fetcher registered explicitly (avatars come from the PocketBase file API). */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory())
                // Workavera's preset avatars are DiceBear-generated SVGs, so the
                // SVG decoder must be registered explicitly.
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .build()
}
