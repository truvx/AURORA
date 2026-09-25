package dev.aurora.player.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The measured shape of a local file, so it is decoded once rather than once a screen.
 *
 * [analysisVersion] is what lets a later change to the analysis invalidate these rows
 * instead of leaving a library full of shapes measured by older code - the same guard
 * `track_loudness` carries, and for the same reason.
 */
@Entity(
    tableName = "track_waveform",
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["mediaId"])]
)
data class TrackWaveformEntity(
    @PrimaryKey val mediaId: String,
    /** One byte per peak, two hex characters each. See WaveformCodec. */
    val peaks: String,
    val analysisVersion: Int
)
