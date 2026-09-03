package dev.aurora.player.app

import android.content.Context
import dev.aurora.player.data.db.AuroraDatabase
import dev.aurora.player.data.db.LocalLibraryDao
import dev.aurora.player.data.player.Media3PlayerAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

interface AppContainer {
    val database: AuroraDatabase
    val localLibraryDao: LocalLibraryDao
    val trackResolver: TrackResolver
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

    override val playerAdapter: PlayerAdapter by lazy {
        Media3PlayerAdapter(context, applicationScope)
    }

    override val playerCoordinator: PlayerCoordinator by lazy {
        PlayerCoordinator(playerAdapter, trackResolver, applicationScope)
    }
}
