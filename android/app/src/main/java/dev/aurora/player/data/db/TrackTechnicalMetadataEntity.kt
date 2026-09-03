package dev.aurora.player.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "track_metadata",
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["mediaId"], unique = true)]
)
data class TrackTechnicalMetadataEntity(
    @PrimaryKey(autoGenerate = true) val metadataId: Long = 0,
    val mediaId: String,
    val codec: String?,
    val container: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val channels: Int?,
    val bitDepth: Int?,
    val durationMs: Long?,
    val isLossless: Boolean?,
    val extractionVersion: Int,
    val sourceVersion: Int = 1 // Required by DATABASE_MODEL.md
)
