package dev.dogetime.data

import dev.dogetime.domain.MediaType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/** The episode after another one, as season and episode numbers. */
data class EpisodeNumber(val season: Int, val episode: Int)

/** What [NextEpisodeFinder] found: the next episode, or that there is none out yet. */
sealed interface NextEpisode {
    data class Available(val episode: EpisodeNumber) : NextEpisode
    /** The last episode out: the show is over, or the next one hasn't aired. */
    data object None : NextEpisode
}

/**
 * Finds the episode that follows one, from TMDB (TV) or Kitsu (anime). An episode counts as out
 * once its air date has passed; TMDB lists announced episodes, which aren't. Kitsu often has no
 * date for old episodes, so an anime episode without one counts as out.
 *
 * Answers are kept for [ttlMs], so Home can ask on every change without hitting the network.
 */
class NextEpisodeFinder(
    private val media: MediaRepository,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /** Today as `YYYY-MM-DD` (UTC): ISO dates compare correctly as text. */
    private val today: () -> String = { isoDate(Clock.System.now().toEpochMilliseconds()) },
    private val ttlMs: Long = 6 * 60 * 60 * 1000L,
) {
    private data class Key(val type: MediaType, val id: Int, val season: Int, val episode: Int)
    private class Cached(val value: NextEpisode, val at: Long)

    private val cache = mutableMapOf<Key, Cached>()
    private val mutex = Mutex()

    /** A remembered answer, without asking the network. */
    suspend fun cached(type: MediaType, id: Int, season: Int, episode: Int): NextEpisode? = mutex.withLock {
        cache[Key(type, id, season, episode)]?.takeIf { now() - it.at < ttlMs }?.value
    }

    /** The episode after [season]x[episode]. Throws when it can't be looked up (e.g. offline). */
    suspend fun after(type: MediaType, id: Int, season: Int, episode: Int): NextEpisode {
        cached(type, id, season, episode)?.let { return it }
        val found = lookUp(type, id, season, episode)
        mutex.withLock { cache[Key(type, id, season, episode)] = Cached(found, now()) }
        return found
    }

    private suspend fun lookUp(type: MediaType, id: Int, season: Int, episode: Int): NextEpisode {
        if (type == MediaType.Movie) return NextEpisode.None
        val sameSeason = media.episodes(id, season, type)
        sameSeason.firstOrNull { it.number > episode && isOut(it.airDate, type) }
            ?.let { return NextEpisode.Available(EpisodeNumber(season, it.number)) }
        // Anime is numbered as one long season.
        if (type == MediaType.Anime) return NextEpisode.None
        val later = media.details(type, id).seasons
            .map { it.number }
            .filter { it > season } // also skips Specials (season 0)
            .sorted()
        for (number in later) {
            val first = media.episodes(id, number, type).filter { isOut(it.airDate, type) }.minByOrNull { it.number }
            if (first != null) return NextEpisode.Available(EpisodeNumber(number, first.number))
        }
        return NextEpisode.None
    }

    private fun isOut(airDate: String?, type: MediaType): Boolean {
        val date = airDate?.take(10)?.takeIf { ISO_DATE.matches(it) } ?: return type == MediaType.Anime
        return date <= today()
    }

    private companion object {
        val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    }
}

/** The UTC calendar date of [epochMs] as `YYYY-MM-DD` (Howard Hinnant's civil-from-days). */
internal fun isoDate(epochMs: Long): String {
    val z = epochMs.floorDiv(86_400_000L) + 719_468
    val era = z.floorDiv(146_097L)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    val year = yoe + era * 400 + if (month <= 2) 1 else 0
    return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}
