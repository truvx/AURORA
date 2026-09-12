package dev.aurora.player.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An append-only record of what was played, skipped, or completed.
 *
 * History is private by default, bounded by retention policy, and never required for
 * playback - deleting all of it must leave the player fully functional. No foreign key to
 * media items: history is retained as the user's own record even if an item is purged.
 */
@Entity(
    tableName = "listening_events",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["mediaId"]),
        Index(value = ["sessionId"])
    ]
)
data class ListeningEventEntity(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0,
    val mediaId: String,
    val provider: String,
    val timestamp: Long,
    val sessionId: String,
    /** One of PLAY, SKIP, COMPLETE. */
    val kind: String,
    val progressMs: Long
)

/**
 * Where to resume a track. Keyed by provider and media identity rather than title/artist so
 * two different recordings of the same song never share a position.
 */
@Entity(
    tableName = "resume_positions",
    primaryKeys = ["provider", "mediaId"],
    indices = [Index(value = ["updatedAt"])]
)
data class ResumePositionEntity(
    val provider: String,
    val mediaId: String,
    val positionMs: Long,
    val durationMs: Long?,
    val updatedAt: Long
)
