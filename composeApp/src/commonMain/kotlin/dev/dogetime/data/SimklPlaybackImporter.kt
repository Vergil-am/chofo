package dev.dogetime.data

import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.WatchHistoryItem
import dev.dogetime.domain.WatchTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

/**
 * Brings what the user left unfinished elsewhere (another device or app on Simkl) into the local
 * watch history, which is what Continue Watching, resume and episode progress read. A Simkl
 * session only replaces a local entry that is older than it.
 *
 * Shows and anime on the Simkl Watching list come in as their next unwatched episode, at 0%,
 * unless that episode is already in the local history.
 *
 * Runs when the account is loaded or signed in, and when [refresh] is called (Home refresh).
 * [SimklRepository.changesSinceLastCheck] keeps that to one cheap request unless something changed.
 */
class SimklPlaybackImporter(
    private val simkl: SimklRepository,
    private val watchHistory: WatchHistoryRepository,
    private val media: MediaRepository,
    private val appScope: CoroutineScope,
) {
    private val mutex = Mutex()

    init {
        appScope.launch {
            simkl.account.map { it?.username }.distinctUntilChanged().filterNotNull().collect { refresh() }
        }
    }

    fun refresh() {
        if (!simkl.isConfigured) return
        appScope.launch {
            if (mutex.isLocked) return@launch
            mutex.withLock {
                val changes = simkl.changesSinceLastCheck() ?: return@withLock
                // Paused sessions first: they carry a position, which the list entries don't.
                changes.playback?.sortedBy { it.pausedAt }?.forEach { item -> guarded { import(item) } }
                changes.watchingShows?.forEach { entry -> guarded { importNext(entry, MediaType.Tv) } }
                changes.watchingAnime?.forEach { entry -> guarded { importNext(entry, MediaType.Anime) } }
            }
        }
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Importing from Simkl failed: ${e.message}")
        }
    }

    /**
     * Adds the next episode of a title on the Watching list, at 0%, dated when it was last watched
     * on Simkl (or added to the list). Skipped when that episode is already in the local history,
     * or when everything here is newer, unless the newest local episode is finished: then the
     * next one is what Continue Watching should offer.
     */
    private suspend fun importNext(entry: SimklListEntry, type: MediaType) {
        val target = entry.nextTarget(type) ?: return
        val id = target.showId
        if (watchHistory.getHistory(type, id, target.season, target.episode) != null) return
        val simklAt = (entry.lastWatchedAt ?: entry.addedAt)?.toEpochMs() ?: return
        val latest = watchHistory.getLatestHistory(type, id)
        val watchedAt = when {
            latest == null || latest.lastWatchedAt < simklAt -> simklAt
            latest.progress >= FINISHED_FRACTION && latest.isBefore(target) -> latest.lastWatchedAt + 1
            else -> return
        }
        val (card, runtimeMinutes) = cardFor(type, id, latest?.mediaItem)
        val durationMs = runtimeMinutes?.let { it * 60_000L } ?: DEFAULT_EPISODE_MS
        watchHistory.saveProgress(target, card, 0, durationMs, watchedAt = watchedAt)
    }

    /** The title's card from the local history, or looked up when it's new to this device. */
    private suspend fun cardFor(type: MediaType, id: Int, known: MediaItem?): Pair<MediaItem, Int?> {
        if (known != null) return known to null
        val details = media.details(type, id)
        return details.item to details.runtime?.substringBefore(' ')?.toIntOrNull()
    }

    private fun WatchHistoryItem.isBefore(target: WatchTarget.Episode): Boolean {
        val s = season ?: return false
        val e = episode ?: return false
        return s < target.season || (s == target.season && e < target.episode)
    }

    private fun SimklListEntry.nextTarget(type: MediaType): WatchTarget.Episode? {
        val media = show ?: return null
        val next = nextToWatch?.let(::parseEpisodeCode) ?: return null
        return when (type) {
            MediaType.Tv -> {
                val id = media.intId("tmdb") ?: return null
                val season = next.first ?: return null
                WatchTarget.Episode(id, season, next.second, imdbId = media.imdbId())
            }
            MediaType.Anime -> {
                val kitsu = media.intId("kitsu") ?: return null
                WatchTarget.Episode(kitsu, ANIME_SEASON, next.second, kitsuId = kitsu)
            }
            MediaType.Movie -> null
        }
    }

    private suspend fun import(item: SimklPlaybackItem) {
        val ref = item.toRef() ?: return
        val pausedAt = item.pausedAt?.toEpochMs() ?: return
        if (item.progress <= 0.0) return // Nothing to resume.
        val local = watchHistory.getHistory(ref.type, ref.id, ref.season, ref.episode)
        if (local != null && local.lastWatchedAt >= pausedAt) return

        // Reuse the title's card from any local entry; look it up only for titles new to this device.
        val card: MediaItem
        var runtimeMinutes: Int? = null
        val known = watchHistory.getLatestHistory(ref.type, ref.id)?.mediaItem
        if (known != null) {
            card = known
        } else {
            val details = media.details(ref.type, ref.id)
            card = details.item
            runtimeMinutes = details.runtime?.substringBefore(' ')?.toIntOrNull()
        }
        // Simkl only knows a percentage. The player scales it to the real length on resume.
        val durationMs = local?.durationMs?.takeIf { it > 0 }
            ?: runtimeMinutes?.let { it * 60_000L }
            ?: if (ref.target is WatchTarget.Movie) DEFAULT_MOVIE_MS else DEFAULT_EPISODE_MS
        val positionMs = (durationMs * item.progress / 100).toLong()
        watchHistory.saveProgress(ref.target, card, positionMs, durationMs, watchedAt = pausedAt)
    }

    private class Ref(val type: MediaType, val id: Int, val target: WatchTarget) {
        val season: Int? get() = (target as? WatchTarget.Episode)?.season
        val episode: Int? get() = (target as? WatchTarget.Episode)?.episode
    }

    /** Maps a Simkl session to this app's ids: TMDB for movies and TV, Kitsu for anime. */
    private fun SimklPlaybackItem.toRef(): Ref? {
        movie?.let { m ->
            val id = m.intId("tmdb") ?: return null
            return Ref(MediaType.Movie, id, WatchTarget.Movie(id = id, imdbId = m.imdbId()))
        }
        show?.let { s ->
            val id = s.intId("tmdb") ?: return null
            val season = episode?.season ?: return null
            val number = episode.number ?: return null
            return Ref(MediaType.Tv, id, WatchTarget.Episode(id, season, number, imdbId = s.imdbId()))
        }
        anime?.let { a ->
            val kitsu = a.intId("kitsu") ?: return null
            val number = episode?.number
                ?: return Ref(MediaType.Anime, kitsu, WatchTarget.Movie(id = kitsu, kitsuId = kitsu))
            // Kitsu episodes are numbered from 1 within the entry, and the app saves them as season 1.
            return Ref(MediaType.Anime, kitsu, WatchTarget.Episode(kitsu, ANIME_SEASON, number, kitsuId = kitsu))
        }
        return null
    }

    private fun SimklPlaybackMedia.imdbId(): String? =
        (ids["imdb"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.startsWith("tt") }

    private fun String.toEpochMs(): Long? = runCatching { Instant.parse(this) }.getOrNull()?.toEpochMilliseconds()

    private companion object {
        /** A local episode at least this far in counts as finished, like Simkl's 80%. */
        const val FINISHED_FRACTION = 0.8f
        const val ANIME_SEASON = 1
        const val DEFAULT_MOVIE_MS = 110 * 60_000L
        const val DEFAULT_EPISODE_MS = 45 * 60_000L
    }
}

/** `S01E05` to (1, 5) and `E10` to (null, 10). Null for anything else. */
internal fun parseEpisodeCode(code: String): Pair<Int?, Int>? {
    val match = Regex("""^(?:S(\d+))?E(\d+)$""", RegexOption.IGNORE_CASE).find(code.trim()) ?: return null
    val episode = match.groupValues[2].toIntOrNull() ?: return null
    return match.groupValues[1].toIntOrNull() to episode
}
