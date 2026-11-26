package dev.dogetime.domain

/**
 * Watched this far (of the duration), a movie or episode counts as finished: it leaves Continue
 * Watching, plays again from the start, and the next episode comes up. The API uses the same value.
 */
const val FINISHED_FRACTION = 0.9f

/** Whether [positionMs] of a [durationMs]-long video counts as watched. False when the length is unknown. */
fun isFinished(positionMs: Long, durationMs: Long): Boolean =
    durationMs > 0 && positionMs >= durationMs * FINISHED_FRACTION

/**
 * Domain model representing a watched movie or episode.
 */
data class WatchHistoryItem(
    val mediaItem: MediaItem,
    val season: Int?,
    val episode: Int?,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: Long,
) {
    /** Watch progress as a fraction between 0.0 and 1.0 (if duration is known). */
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    /** Watched to [FINISHED_FRACTION] or further. */
    val isFinished: Boolean get() = isFinished(positionMs, durationMs)

    /** Cache/lookup key matching WatchHistoryEntity.key. */
    val watchKey: String
        get() = if (season != null && episode != null) {
            "${mediaItem.type.name}:${mediaItem.id}:$season:$episode"
        } else {
            "${mediaItem.type.name}:${mediaItem.id}"
        }
}
