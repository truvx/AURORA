package dev.aurora.player.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A restorable snapshot of the canonical queue, so a cold start can offer the user their
 * queue back rather than an empty player.
 */
@Entity(tableName = "queue_snapshots")
data class QueueSnapshotEntity(
    @PrimaryKey val snapshotId: String,
    val createdAt: Long,
    val currentIndex: Int,
    val positionMs: Long,
    val repeatMode: String,
    val shuffleMode: String
)

/**
 * One ordered item within a snapshot. Position is explicit rather than implied by row order.
 */
@Entity(
    tableName = "queue_snapshot_items",
    foreignKeys = [
        ForeignKey(
            entity = QueueSnapshotEntity::class,
            parentColumns = ["snapshotId"],
            childColumns = ["snapshotId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    primaryKeys = ["snapshotId", "position"],
    indices = [Index(value = ["snapshotId", "position"])]
)
data class QueueSnapshotItemEntity(
    val snapshotId: String,
    val position: Int,
    val mediaId: String,
    val provider: String
)
