package dev.aurora.player.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "track_loudness",
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["mediaId"])
    ]
)
data class TrackLoudnessEntity(
    @PrimaryKey val mediaId: String,
    val lufsIntegrated: Float?,
    val truePeak: Float?,
    val albumLufs: Float?,
    val albumPeak: Float?,
    val analysisVersion: Int
)
