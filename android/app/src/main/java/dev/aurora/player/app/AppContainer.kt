package dev.aurora.player.app

import android.content.Context
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dev.aurora.player.data.api.YouTubeDataApi
import dev.aurora.player.data.db.AuroraDatabase
import dev.aurora.player.data.db.LocalLibraryDao
import dev.aurora.player.data.player.CompositePlayerAdapter
import dev.aurora.player.data.player.Media3PlayerAdapter
import dev.aurora.player.data.player.YouTubePlayerAdapter
import dev.aurora.player.data.providers.YouTubeMusicProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

interface AppContainer {
    val database: AuroraDatabase
    val localLibraryDao: LocalLibraryDao
    val trackResolver: TrackResolver
    val youtubeMusicProvider: YouTubeMusicProvider
    val youtubePlayerAdapter: YouTubePlayerAdapter
    val media3PlayerAdapter: PlayerAdapter
    val playerAdapter: PlayerAdapter
    val playerCoordinator: PlayerCoordinator
    val localLibraryProvider: dev.aurora.player.data.providers.LocalLibraryProvider
    val libraryOrganizationRepository: dev.aurora.player.domain.library.LibraryOrganizationRepository

    /**
     * Starts observing playback to record history, resume positions, and queue snapshots,
     * and restores the previous queue. Safe to call once from Application.onCreate.
     */
    fun startPlaybackPersistence()
    val gatewayTokenStore: dev.aurora.player.data.security.GatewayTokenStore
    val youTubeDataApi: YouTubeDataApi
    val mediaResolver: dev.aurora.player.data.discovery.MediaResolver
    val latestReleasesRepository: dev.aurora.player.data.discovery.LatestReleasesRepository
    val trendingRepository: dev.aurora.player.data.discovery.TrendingRepository
    val whatsNewStore: dev.aurora.player.data.discovery.WhatsNewStore
    val profileStore: dev.aurora.player.data.discovery.ProfileStore
    val aiProvider: dev.aurora.player.domain.ai.AiProvider
    val recommendationEngine: dev.aurora.player.domain.recommendations.RecommendationEngine
    val aiToolExecutor: AiToolExecutor
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val database: AuroraDatabase by lazy {
        androidx.room.Room.databaseBuilder(
            context,
            AuroraDatabase::class.java,
            "aurora-database"
        ).addMigrations(
            AuroraDatabase.MIGRATION_1_2,
            AuroraDatabase.MIGRATION_2_3,
            AuroraDatabase.MIGRATION_3_4,
            AuroraDatabase.MIGRATION_4_5
        )
         .build()
    }

    override val localLibraryDao: LocalLibraryDao by lazy {
        database.localLibraryDao()
    }

    override val trackResolver: TrackResolver by lazy {
        AuroraTrackResolver(localLibraryDao)
    }

    override val youTubeDataApi: YouTubeDataApi by lazy {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val contentType = okhttp3.MediaType.get("application/json")
        retrofit2.Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/youtube/v3/")
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(YouTubeDataApi::class.java)
    }

    override val youtubeMusicProvider: YouTubeMusicProvider by lazy {
        YouTubeMusicProvider(youTubeDataApi)
    }

    /*
     * Discovery: the Home feed, Explore and the release notifications.
     *
     * All of it shares one cache, because all of it spends the same YouTube quota - 10,000
     * units a day, of which a single search is 100.
     */
    private val discoveryCache by lazy {
        dev.aurora.player.data.discovery.DiscoveryCache(
            dev.aurora.player.data.discovery.SharedPreferencesStore(
                context.getSharedPreferences("aurora_discovery", Context.MODE_PRIVATE)
            )
        )
    }

    override val mediaResolver: dev.aurora.player.data.discovery.MediaResolver by lazy {
        dev.aurora.player.data.discovery.MediaResolver(
            local = localLibraryProvider,
            api = youTubeDataApi,
            apiKey = dev.aurora.player.BuildConfig.YOUTUBE_API_KEY,
            cache = discoveryCache
        )
    }

    override val latestReleasesRepository: dev.aurora.player.data.discovery.LatestReleasesRepository by lazy {
        dev.aurora.player.data.discovery.LatestReleasesRepository(
            api = youTubeDataApi,
            apiKey = dev.aurora.player.BuildConfig.YOUTUBE_API_KEY,
            cache = discoveryCache
        )
    }

    override val trendingRepository: dev.aurora.player.data.discovery.TrendingRepository by lazy {
        dev.aurora.player.data.discovery.TrendingRepository(
            api = youTubeDataApi,
            apiKey = dev.aurora.player.BuildConfig.YOUTUBE_API_KEY,
            cache = discoveryCache
        )
    }

    override val whatsNewStore: dev.aurora.player.data.discovery.WhatsNewStore by lazy {
        dev.aurora.player.data.discovery.WhatsNewStore(
            dev.aurora.player.data.discovery.SharedPreferencesStore(
                context.getSharedPreferences("aurora_discovery", Context.MODE_PRIVATE)
            )
        )
    }

    override val profileStore: dev.aurora.player.data.discovery.ProfileStore by lazy {
        dev.aurora.player.data.discovery.ProfileStore(context)
    }

    override val media3PlayerAdapter: PlayerAdapter by lazy {
        dev.aurora.player.data.player.CrossfadeMedia3Adapter(context, applicationScope)
    }

    override val youtubePlayerAdapter: YouTubePlayerAdapter by lazy {
        YouTubePlayerAdapter().apply {
            // Must be initialized on main thread, but lazy block should run when first accessed, which is typically in UI
            initializeWebView(context.applicationContext)
        }
    }

    override val playerAdapter: PlayerAdapter by lazy {
        CompositePlayerAdapter(media3PlayerAdapter, youtubePlayerAdapter)
    }

    override val playerCoordinator: PlayerCoordinator by lazy {
        PlayerCoordinator(playerAdapter, trackResolver, applicationScope)
    }

    override val localLibraryProvider: dev.aurora.player.data.providers.LocalLibraryProvider by lazy {
        dev.aurora.player.data.providers.LocalLibraryProvider(
            localLibraryDao, 
            dev.aurora.player.data.scanner.LocalMusicScanner(context)
        )
    }

    override val libraryOrganizationRepository: dev.aurora.player.domain.library.LibraryOrganizationRepository by lazy {
        dev.aurora.player.data.library.RoomLibraryOrganizationRepository(
            database.libraryOrganizationDao(),
            localLibraryDao
        )
    }

    override val gatewayTokenStore: dev.aurora.player.data.security.GatewayTokenStore by lazy {
        dev.aurora.player.data.security.KeystoreGatewayTokenStore(context)
    }

    override val aiProvider: dev.aurora.player.domain.ai.AiProvider by lazy {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val contentType = okhttp3.MediaType.get("application/json")
        // AI gateway calls go through Gemini which can take 10-30s;
        // default OkHttp timeout is 10s, so use 60s for AI requests.
        val aiClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(
                dev.aurora.player.data.api.GatewayAuthInterceptor { gatewayTokenStore.get() }
            )
            .build()
        // Use 10.0.2.2 for Android emulator -> localhost
        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl("http://10.0.2.2:3000/")
            .client(aiClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            
        val api = retrofit.create(dev.aurora.player.data.api.AiGatewayApi::class.java)
        dev.aurora.player.data.providers.NetworkAiProvider(api)
    }

    override val recommendationEngine: dev.aurora.player.domain.recommendations.RecommendationEngine by lazy {
        dev.aurora.player.domain.recommendations.RecommendationEngine(
            providers = listOf(localLibraryProvider, youtubeMusicProvider)
        )
    }

    override val aiToolExecutor: AiToolExecutor by lazy {
        AiToolExecutor(
            playerCoordinator = playerCoordinator,
            recommendationEngine = recommendationEngine,
            providers = listOf(localLibraryProvider, youtubeMusicProvider)
        )
    }

    private var persistenceStarted = false

    override fun startPlaybackPersistence() {
        if (persistenceStarted) return
        persistenceStarted = true

        applicationScope.launch {
            // Restore first, so the recorder does not immediately snapshot an empty queue
            // over the one we are about to put back.
            runCatching {
                QueueRestorer(
                    libraryOrganizationRepository,
                    listOf(localLibraryProvider, youtubeMusicProvider)
                ).restore(playerCoordinator)
            }

            // History must never be able to break playback, so failures here are contained.
            runCatching {
                PlaybackHistoryRecorder(libraryOrganizationRepository)
                    .observe(playerCoordinator.state)
            }
        }
    }
}
