package dev.dogetime.data.sync

import dev.dogetime.data.MediaRepository
import dev.dogetime.data.WatchHistoryListener
import dev.dogetime.data.local.PreferenceDao
import dev.dogetime.data.local.PreferenceEntity
import dev.dogetime.data.local.WatchHistoryDao
import dev.dogetime.data.local.WatchHistoryEntity
import dev.dogetime.domain.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/** What Settings shows about sync. */
data class SyncStatus(
    val isSyncing: Boolean = false,
    /** When the last full sync finished (epoch ms), or null if none has this session. */
    val lastSyncedAt: Long? = null,
    /** Why the last sync failed, or null. */
    val error: String? = null,
)

/**
 * Keeps the local watch history (positions, durations, when each was watched) in step with the
 * Chofo API, for the signed-in account. Local-first: the app never waits on it, and without an
 * account it does nothing.
 *
 * - **Push:** rows changed since the last push go up in batches of 500. The server keeps the
 *   newest version of each row; when it already had a newer one, that one is applied here.
 * - **Pull:** rows changed on the server since the saved cursor come down and replace older
 *   local rows (or delete them, for tombstones).
 * - **Deletes** made here are queued and sent, so another device's older copy can't bring them back.
 *
 * Runs at sign-in (everything, both ways), at app start, when Home reloads (at most once a
 * minute), a few seconds after play, pause, the end of a video or closing the player, and when
 * the app goes to the background with unsent changes. The player's periodic position saves stay
 * on the device unless [periodicPushMs] is set (off for now, to keep requests low on free hosting).
 */
class WatchSync(
    private val api: ChofoApi,
    private val accounts: AccountRepository,
    private val dao: WatchHistoryDao,
    private val prefs: PreferenceDao,
    private val media: MediaRepository?,
    private val appScope: CoroutineScope,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /** Also push this long after a periodic position save. Null: only the events above push. */
    private val periodicPushMs: Long? = null,
    private val urgentDelayMs: Long = 3_000,
    private val minPullIntervalMs: Long = 60_000,
) : WatchHistoryListener {

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val syncMutex = Mutex()
    private val queueMutex = Mutex()
    private var scheduled: Job? = null
    private var scheduledFor = Long.MAX_VALUE
    private var lastPullAt = 0L

    init {
        appScope.launch {
            accounts.awaitLoaded()
            accounts.account.map { it?.userId }.distinctUntilChanged().collect { userId ->
                if (userId == null) {
                    clearSyncState()
                } else {
                    prepareFor(userId)
                    sync()
                }
            }
        }
    }

    /** Syncs now, unless a sync is already running. */
    fun syncNow() {
        appScope.launch { sync() }
    }

    /** For screen refreshes: syncs unless the last pull was under [minPullIntervalMs] ago. */
    fun refresh() {
        if (now() - lastPullAt < minPullIntervalMs) return
        syncNow()
    }

    override fun onSaved(urgent: Boolean) {
        if (accounts.account.value == null) return
        if (urgent) schedule(urgentDelayMs) else periodicPushMs?.let { schedule(it) }
    }

    /** The app went to the background: send what this device has that the server doesn't. */
    fun onBackground() {
        appScope.launch { flush() }
    }

    /** Syncs now if anything local is unsent. For the app closing; returns when done. */
    suspend fun flush() {
        if (accounts.account.value == null || !hasUnsentChanges()) return
        scheduled?.cancel()
        while (!sync()) delay(100)
    }

    private suspend fun hasUnsentChanges(): Boolean =
        queueMutex.withLock { readDeletes().isNotEmpty() } || dao.changedSince(readLong(KEY_PUSHED_UP_TO)).isNotEmpty()

    override suspend fun onDeleted(type: MediaType, id: Int, season: Int?, episode: Int?, deletedAt: Long) {
        if (accounts.account.value == null) return
        queueMutex.withLock {
            val queue = readDeletes() + PendingDelete(type.apiName, id, season, episode, deletedAt)
            writeDeletes(queue)
        }
        schedule(urgentDelayMs)
    }

    /** Runs one sync after [delayMs], or sooner if one is already due earlier. */
    private fun schedule(delayMs: Long) {
        val due = now() + delayMs
        if (scheduled?.isActive == true && scheduledFor <= due) return
        scheduled?.cancel()
        scheduledFor = due
        scheduled = appScope.launch {
            delay(delayMs)
            scheduledFor = Long.MAX_VALUE
            sync()
        }
    }

    /**
     * One full round: queued deletes, then local changes, then remote changes. False when it
     * didn't run because another round already was.
     */
    suspend fun sync(): Boolean {
        if (!syncMutex.tryLock()) return false
        try {
            val account = accounts.account.value ?: return true
            _status.update { it.copy(isSyncing = true, error = null) }
            pushDeletes(account.token)
            pushChanges(account.token)
            pull(account.token)
            lastPullAt = now()
            _status.value = SyncStatus(lastSyncedAt = now())
        } catch (e: CancellationException) {
            throw e
        } catch (e: ChofoApiException) {
            if (e.status == 401) {
                accounts.onSessionRejected()
                _status.value = SyncStatus(error = "Signed out: sign in again to keep syncing.")
            } else {
                _status.update { it.copy(isSyncing = false, error = e.message ?: "Sync failed (${e.status}).") }
            }
        } catch (e: Exception) {
            _status.update { it.copy(isSyncing = false, error = "Couldn't reach the server. Will try again later.") }
        } finally {
            syncMutex.unlock()
        }
        return true
    }

    // ---------------------------------------------------------------- Push

    private suspend fun pushDeletes(token: String) {
        val queue = queueMutex.withLock { readDeletes() }
        if (queue.isEmpty()) return
        for (item in queue) {
            api.deleteProgress(token, item.type, item.id, item.season, item.episode, item.deletedAt)
            // Removed one at a time, so a failure part-way keeps only what wasn't sent.
            queueMutex.withLock { writeDeletes(readDeletes() - item) }
        }
    }

    private suspend fun pushChanges(token: String) {
        val pushedUpTo = readLong(KEY_PUSHED_UP_TO)
        val changed = dao.changedSince(pushedUpTo)
        if (changed.isEmpty()) return
        for (chunk in changed.chunked(MAX_BATCH)) {
            val sendable = chunk.mapNotNull { row -> row.toWrite()?.let { row to it } }
            if (sendable.isNotEmpty()) {
                val stored = api.putProgress(token, sendable.map { it.second })
                // Where the server already had something newer, adopt it.
                stored.zip(sendable).forEach { (remote, local) ->
                    if (remote.deleted || remote.watchedAt > local.first.lastWatchedAt) applyRemote(remote)
                }
            }
            writeLong(KEY_PUSHED_UP_TO, chunk.maxOf { it.lastWatchedAt })
        }
    }

    // ---------------------------------------------------------------- Pull

    private suspend fun pull(token: String) {
        var cursor = readLong(KEY_CURSOR)
        var restarted = false
        while (true) {
            val page = try {
                api.progress(token, cursor)
            } catch (e: ChofoApiException) {
                // The server purged tombstones older than our cursor: download everything again.
                if (e.status == 410 && !restarted) {
                    restarted = true
                    cursor = 0
                    continue
                }
                throw e
            }
            page.items.forEach { applyRemote(it) }
            cursor = page.nextCursor
            writeLong(KEY_CURSOR, cursor)
            if (!page.hasMore) break
        }
    }

    /** Applies one server row when it's newer than the local one. Doesn't count as a local change. */
    private suspend fun applyRemote(row: ApiProgressRow) {
        val type = mediaTypeOf(row.type) ?: return
        val key = WatchHistoryEntity.key(type.name, row.id, row.season, row.episode)
        val local = dao.getByKey(key)
        if (row.deleted) {
            if (local != null && local.lastWatchedAt <= row.watchedAt) dao.deleteByKey(key)
            return
        }
        if (local != null && local.lastWatchedAt >= row.watchedAt) return
        val card = cardFor(type, row.id, row.card, local)
        dao.upsert(
            WatchHistoryEntity(
                watchKey = key,
                id = row.id,
                type = type.name,
                title = card.title,
                overview = card.overview,
                posterUrl = card.posterUrl,
                backdropUrl = card.backdropUrl,
                rating = card.rating,
                year = card.year,
                season = row.season,
                episode = row.episode,
                positionMs = row.positionMs,
                durationMs = row.durationMs,
                lastWatchedAt = row.watchedAt,
            ),
        )
    }

    private class LocalCard(
        val title: String,
        val overview: String,
        val posterUrl: String?,
        val backdropUrl: String?,
        val rating: Double,
        val year: String?,
    )

    /** The server's card, else what this device already shows for the title, else a lookup. */
    private suspend fun cardFor(type: MediaType, id: Int, remote: ApiCard?, local: WatchHistoryEntity?): LocalCard {
        val known = local ?: dao.getLatestByMedia(id, type.name)
        if (remote?.title != null || known == null) {
            val looked = if (remote?.title == null) lookUp(type, id) else null
            return LocalCard(
                title = remote?.title ?: looked?.title ?: "",
                overview = remote?.overview ?: looked?.overview ?: "",
                posterUrl = remote?.posterUrl ?: looked?.posterUrl,
                backdropUrl = remote?.backdropUrl ?: looked?.backdropUrl,
                rating = remote?.rating ?: looked?.rating ?: 0.0,
                year = remote?.year ?: looked?.year,
            )
        }
        return LocalCard(known.title, known.overview, known.posterUrl, known.backdropUrl, known.rating, known.year)
    }

    private suspend fun lookUp(type: MediaType, id: Int) = try {
        media?.details(type, id)?.item
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    // ---------------------------------------------------------------- State

    /** A different account than the one last synced here: start from scratch, both ways. */
    private suspend fun prepareFor(userId: String) {
        if (read(KEY_SYNC_USER) == userId) return
        clearSyncState()
        prefs.upsert(PreferenceEntity(KEY_SYNC_USER, userId))
    }

    private suspend fun clearSyncState() {
        scheduled?.cancel()
        listOf(KEY_SYNC_USER, KEY_CURSOR, KEY_PUSHED_UP_TO, KEY_PENDING_DELETES).forEach { prefs.delete(it) }
        _status.value = SyncStatus()
        lastPullAt = 0
    }

    @Serializable
    private data class PendingDelete(val type: String, val id: Int, val season: Int?, val episode: Int?, val deletedAt: Long)

    private suspend fun readDeletes(): List<PendingDelete> =
        read(KEY_PENDING_DELETES)?.let { runCatching { json.decodeFromString<List<PendingDelete>>(it) }.getOrNull() } ?: emptyList()

    private suspend fun writeDeletes(queue: List<PendingDelete>) {
        if (queue.isEmpty()) prefs.delete(KEY_PENDING_DELETES)
        else prefs.upsert(PreferenceEntity(KEY_PENDING_DELETES, json.encodeToString(queue)))
    }

    private suspend fun read(key: String): String? = prefs.observe(key).first()
    private suspend fun readLong(key: String): Long = read(key)?.toLongOrNull() ?: 0L
    private suspend fun writeLong(key: String, value: Long) = prefs.upsert(PreferenceEntity(key, value.toString()))

    private companion object {
        const val MAX_BATCH = 500
        const val KEY_SYNC_USER = "sync_user"
        const val KEY_CURSOR = "sync_cursor"
        const val KEY_PUSHED_UP_TO = "sync_pushed_up_to"
        const val KEY_PENDING_DELETES = "sync_pending_deletes"
        val json = Json { ignoreUnknownKeys = true }
    }
}

// ---------------------------------------------------------------- Mapping

internal val MediaType.apiName: String
    get() = when (this) {
        MediaType.Movie -> "movie"
        MediaType.Tv -> "tv"
        MediaType.Anime -> "anime"
    }

internal fun mediaTypeOf(apiName: String): MediaType? = when (apiName) {
    "movie" -> MediaType.Movie
    "tv" -> MediaType.Tv
    "anime" -> MediaType.Anime
    else -> null
}

/**
 * The row as the API takes it, or null when the server would reject it: TV needs a season and
 * an episode, movies have neither, and the two are always set together.
 */
internal fun WatchHistoryEntity.toWrite(): ApiProgressWrite? {
    val mediaType = runCatching { MediaType.valueOf(type) }.getOrNull() ?: return null
    if ((season == null) != (episode == null)) return null
    if (mediaType == MediaType.Tv && season == null) return null
    if (mediaType == MediaType.Movie && season != null) return null
    if (id < 1 || (season ?: 0) < 0 || (episode ?: 0) < 0) return null
    return ApiProgressWrite(
        type = mediaType.apiName,
        id = id,
        season = season,
        episode = episode,
        positionMs = positionMs.coerceAtLeast(0),
        durationMs = durationMs.coerceAtLeast(0),
        watchedAt = lastWatchedAt.coerceAtLeast(0),
        card = ApiCard(
            title = title.takeIf { it.isNotBlank() },
            overview = overview.takeIf { it.isNotBlank() }?.take(5000),
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            rating = rating.takeIf { it > 0 }?.coerceAtMost(10.0),
            year = year?.take(10),
        ),
    )
}
