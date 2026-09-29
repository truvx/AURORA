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
    val gatewayTokenStore: dev.aurora.player.data.security.SecretStore
    val youTubeKeyStore: dev.aurora.player.data.security.SecretStore
    val followedArtists: dev.aurora.player.data.discovery.FollowedArtistsStore
    val youTubeDataApi: YouTubeDataApi
    val coverArtRepository: dev.aurora.player.data.discovery.CoverArtRepository
    val lyricsRepository: dev.aurora.player.data.lyrics.LyricsRepository
    val googleAuth: dev.aurora.player.data.auth.GoogleAuthRepository
    val audioQualityStore: dev.aurora.player.data.discovery.AudioQualityStore
    val headphones: dev.aurora.player.data.headphones.HeadphoneMonitor
    val waveformRepository: dev.aurora.player.data.audio.WaveformRepository
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
            AuroraDatabase.MIGRATION_4_5,
            AuroraDatabase.MIGRATION_5_6
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
        val client = okhttp3.OkHttpClient.Builder()
            /*
             * A ceiling on the whole call, not just on the socket.
             *
             * connect/read/write bound socket operations only. The interceptor below blocks
             * on a token refresh *before* chain.proceed, where none of them apply, so a
             * refresh that never came back hung the request forever and repeats starved
             * OkHttp's dispatcher until every YouTube call stopped returning, with nothing
             * to show the user. callTimeout is the only one that bounds the chain.
             */
            .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            /*
             * The bearer token is attached here rather than passed down through the call
             * sites, because freshness belongs to the moment a request leaves rather than
             * to the moment someone decided to make one. Blocking is safe: interceptors
             * run on OkHttp's own I/O threads, and the refresh behind this is either
             * instant from memory or the one call the request already depended on.
             */
            .addInterceptor(
                dev.aurora.player.data.api.YouTubeAuthInterceptor {
                    kotlinx.coroutines.runBlocking { googleAuth.accessToken() }
                }
            )
            .build()
        retrofit2.Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/youtube/v3/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(YouTubeDataApi::class.java)
    }

    /**
     * MusicBrainz, for the cover of the record behind a YouTube video.
     *
     * The service asks every client to identify itself with an application, a version and
     * a contact, and blocks the ones that do not. It is a condition of using open data for
     * free, so it is sent on every request rather than left to chance.
     */
    override val coverArtRepository: dev.aurora.player.data.discovery.CoverArtRepository by lazy {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val contentType = okhttp3.MediaType.get("application/json")
        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header(
                            "User-Agent",
                            "AURORA/${dev.aurora.player.BuildConfig.VERSION_NAME} " +
                                "( https://github.com/t6ux/AURORA )"
                        )
                        .build()
                )
            }
            .build()
        val api = retrofit2.Retrofit.Builder()
            .baseUrl("https://musicbrainz.org/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(dev.aurora.player.data.api.MusicBrainzApi::class.java)

        dev.aurora.player.data.discovery.CoverArtRepository(
            musicBrainz = api,
            cache = discoveryCache,
            // A HEAD to the archive, which has no rate limit of its own. Asking costs one
            // small request and saves handing the image loader a URL that does not exist.
            coverExists = { url ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        val request = okhttp3.Request.Builder().url(url).head().build()
                        client.newCall(request).execute().use { it.isSuccessful }
                    }.getOrDefault(false)
                }
            },
        )
    }

    /**
     * LRCLIB, for the words of what is playing.
     *
     * Its own Retrofit because it is a different service with different manners: no key,
     * no rate limit published, and no User-Agent demanded - but one is sent anyway, since
     * an anonymous client is the first thing an open service blocks when it needs to.
     */
    override val lyricsRepository: dev.aurora.player.data.lyrics.LyricsRepository by lazy {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val contentType = okhttp3.MediaType.get("application/json")
        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header(
                            "User-Agent",
                            "AURORA/${dev.aurora.player.BuildConfig.VERSION_NAME} " +
                                "( https://github.com/t6ux/AURORA )"
                        )
                        .build()
                )
            }
            .build()
        val api = retrofit2.Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(dev.aurora.player.data.api.LrcLibApi::class.java)

        dev.aurora.player.data.lyrics.LyricsRepository(api = api, cache = discoveryCache)
    }

    /**
     * The measured shape of local files.
     *
     * Nothing is measured until a scrubber asks for a track it can measure, so a library
     * scan stays a scan rather than turning into a decode of everything the user owns.
     */
    override val waveformRepository: dev.aurora.player.data.audio.WaveformRepository by lazy {
        dev.aurora.player.data.audio.WaveformRepository(
            dao = localLibraryDao,
            analyzer = dev.aurora.player.data.audio.WaveformAnalyzer(context),
        )
    }

    /**
     * What authorises a YouTube call, resolved fresh each time it is asked.
     *
     * A signed-in session wins over a pasted key: it is the newer, revocable thing, and
     * someone who has signed in has said which they want. The key stays as the fallback
     * for anyone who has not - and for the case where a session has expired and cannot be
     * refreshed, where falling back beats going dark.
     */
    private suspend fun youTubeCredential(): dev.aurora.player.domain.auth.YouTubeCredential =
        dev.aurora.player.domain.auth.YouTubeCredential.of(
            accessToken = googleAuth.accessToken(),
            apiKey = youTubeKeyStore.get(),
        )

    override val googleAuth: dev.aurora.player.data.auth.GoogleAuthRepository by lazy {
        dev.aurora.player.data.auth.GoogleAuthRepository(
            context = context,
            store = dev.aurora.player.data.security.googleAuthStore(context),
            clientId = dev.aurora.player.BuildConfig.GOOGLE_OAUTH_CLIENT_ID,
            redirectUri = dev.aurora.player.BuildConfig.GOOGLE_OAUTH_CLIENT_ID
                .substringBefore(".apps.googleusercontent.com")
                .let { "com.googleusercontent.apps.$it:/oauth2redirect" },
        ).also { it.restore() }
    }

    override val youtubeMusicProvider: YouTubeMusicProvider by lazy {
        YouTubeMusicProvider(youTubeDataApi) { youTubeCredential() }
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
            credentials = { youTubeCredential() },
            cache = discoveryCache
        )
    }

    override val latestReleasesRepository: dev.aurora.player.data.discovery.LatestReleasesRepository by lazy {
        dev.aurora.player.data.discovery.LatestReleasesRepository(
            api = youTubeDataApi,
            credentials = { youTubeCredential() },
            cache = discoveryCache
        )
    }

    override val trendingRepository: dev.aurora.player.data.discovery.TrendingRepository by lazy {
        dev.aurora.player.data.discovery.TrendingRepository(
            api = youTubeDataApi,
            credentials = { youTubeCredential() },
            cache = discoveryCache
        )
    }

    override val headphones: dev.aurora.player.data.headphones.HeadphoneMonitor by lazy {
        dev.aurora.player.data.headphones.HeadphoneMonitor(context, applicationScope)
    }

    override val audioQualityStore: dev.aurora.player.data.discovery.AudioQualityStore by lazy {
        dev.aurora.player.data.discovery.AudioQualityStore(
            dev.aurora.player.data.discovery.SharedPreferencesStore(
                context.getSharedPreferences("aurora_settings", Context.MODE_PRIVATE)
            )
        )
    }

    override val followedArtists: dev.aurora.player.data.discovery.FollowedArtistsStore by lazy {
        dev.aurora.player.data.discovery.FollowedArtistsStore(
            dev.aurora.player.data.discovery.SharedPreferencesStore(
                context.getSharedPreferences("aurora_discovery", Context.MODE_PRIVATE)
            )
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

    override val gatewayTokenStore: dev.aurora.player.data.security.SecretStore by lazy {
        dev.aurora.player.data.security.gatewayTokenStore(context)
    }

    override val youTubeKeyStore: dev.aurora.player.data.security.SecretStore by lazy {
        dev.aurora.player.data.security.youTubeKeyStore(context)
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
            // The interceptor blocks on a token refresh before the socket exists, which the
            // three above cannot bound. This one bounds the whole chain.
            .callTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(
                /*
                 * The signed-in identity first, the shared token second.
                 *
                 * The gateway tells a JWT from a secret by its shape and checks whichever
                 * it was given, so sending the ID token when there is one needs nothing
                 * else agreed between the two. The pasted token stays as the way in for
                 * anyone who has not signed in - or whose gateway has no Google client
                 * configured, where an ID token would be refused.
                 */
                dev.aurora.player.data.api.GatewayAuthInterceptor(
                    idToken = { kotlinx.coroutines.runBlocking { googleAuth.idToken() } },
                    sharedToken = { gatewayTokenStore.get() },
                )
            )
            .build()
        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl(aiGatewayUrl)
            .client(aiClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            
        val api = retrofit.create(dev.aurora.player.data.api.AiGatewayApi::class.java)
        dev.aurora.player.data.providers.NetworkAiProvider(api)
    }

    /**
     * The gateway's address, from the build rather than from a literal.
     *
     * A debug build with nothing configured falls back to the emulator's host alias, which
     * is the one host the debug network-security config permits cleartext to. A release
     * build with nothing configured gets a URL that cannot resolve, and the AI reports
     * itself unconfigured - which is the truth, and better than a socket error.
     */
    private val aiGatewayUrl: String
        get() {
            val configured = dev.aurora.player.BuildConfig.AI_GATEWAY_URL
            if (configured.isNotBlank()) {
                return if (configured.endsWith("/")) configured else "$configured/"
            }
            return if (dev.aurora.player.BuildConfig.DEBUG) {
                "http://10.0.2.2:3000/"
            } else {
                "https://gateway.invalid/"
            }
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
