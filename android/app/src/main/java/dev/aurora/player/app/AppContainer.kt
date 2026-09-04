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
        ).addMigrations(AuroraDatabase.MIGRATION_1_2, AuroraDatabase.MIGRATION_2_3)
         .build()
    }

    override val localLibraryDao: LocalLibraryDao by lazy {
        database.localLibraryDao()
    }

    override val trackResolver: TrackResolver by lazy {
        AuroraTrackResolver(localLibraryDao)
    }

    override val youtubeMusicProvider: YouTubeMusicProvider by lazy {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val contentType = okhttp3.MediaType.get("application/json")
        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/youtube/v3/")
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
        
        YouTubeMusicProvider(retrofit.create(YouTubeDataApi::class.java))
    }

    override val media3PlayerAdapter: PlayerAdapter by lazy {
        Media3PlayerAdapter(context, applicationScope)
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

    override val aiProvider: dev.aurora.player.domain.ai.AiProvider by lazy {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val contentType = okhttp3.MediaType.get("application/json")
        // AI gateway calls go through Gemini which can take 10-30s;
        // default OkHttp timeout is 10s, so use 60s for AI requests.
        val aiClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
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
}
