package dev.dogetime.data

import dev.dogetime.data.local.WatchlistDao
import dev.dogetime.data.local.WatchlistEntity
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.WatchStatus
import dev.dogetime.domain.WatchlistEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/** Told about local watchlist changes, so they can be synced. Remote changes don't go through it. */
fun interface WatchlistListener {
    fun onWatchlistChanged()
}

/**
 * The watchlist, in the local database: each title has one [WatchStatus]. Removing a title
 * leaves a tombstone until sync has sent it.
 */
class WatchlistRepository(
    private val dao: WatchlistDao,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    /** Set once at startup by the sync engine. */
    var listener: WatchlistListener? = null

    /** Every title on the list, most recently changed first. */
    fun observeAll(): Flow<List<WatchlistEntry>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toEntry() } }

    /** The title's status, or null when it isn't on the list. */
    fun observeStatus(type: MediaType, id: Int): Flow<WatchStatus?> =
        dao.observe(id, type.name).map { row -> row?.let { WatchStatus.entries.firstOrNull { s -> s.name == it.status } } }

    /** Puts [item] on the list with [status], or moves it there. */
    suspend fun set(item: MediaItem, status: WatchStatus) {
        val current = dao.get(item.id, item.type.name)
        if (current != null && !current.deleted && current.status == status.name) return
        dao.upsert(item.toEntity(status, updatedAt = later(current)))
        listener?.onWatchlistChanged()
    }

    suspend fun remove(type: MediaType, id: Int) {
        val current = dao.get(id, type.name) ?: return
        if (current.deleted) return
        dao.upsert(current.copy(deleted = true, pending = true, updatedAt = later(current)))
        listener?.onWatchlistChanged()
    }

    /**
     * Playback started: a title planned or on hold moves to Watching, and so does a completed show
     * when an episode plays (a new one came out, or a rewatch). A completed movie stays completed.
     */
    suspend fun startedWatching(item: MediaItem, isEpisode: Boolean) {
        val current = dao.get(item.id, item.type.name) ?: return
        if (current.deleted) return
        val status = WatchStatus.entries.firstOrNull { it.name == current.status }
        val moves = status == WatchStatus.PlanToWatch || status == WatchStatus.OnHold ||
            (status == WatchStatus.Completed && isEpisode)
        if (moves) set(item, WatchStatus.Watching)
    }

    /** Newer than [current], even when the clock hasn't moved on, so newest-wins always sees a change. */
    private fun later(current: WatchlistEntity?): Long = maxOf(now(), (current?.updatedAt ?: 0) + 1)
}

private fun MediaItem.toEntity(status: WatchStatus, updatedAt: Long) = WatchlistEntity(
    id = id,
    type = type.name,
    status = status.name,
    title = title,
    overview = overview,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    rating = rating,
    year = year,
    updatedAt = updatedAt,
)

internal fun WatchlistEntity.toMediaItem(): MediaItem? {
    val mediaType = runCatching { MediaType.valueOf(type) }.getOrNull() ?: return null
    return MediaItem(id, mediaType, title, overview, posterUrl, backdropUrl, rating, year)
}

private fun WatchlistEntity.toEntry(): WatchlistEntry? {
    val item = toMediaItem() ?: return null
    val status = WatchStatus.entries.firstOrNull { it.name == status } ?: return null
    return WatchlistEntry(item, status, updatedAt)
}
