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
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val database: AuroraDatabase by lazy {
        androidx.room.Room.databaseBuilder(
            context,
            AuroraDatabase::class.java,
            "aurora-database"
        ).addMigrations(AuroraDatabase.MIGRATION_1_2)
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
}
