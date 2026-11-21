package dev.dogetime.ui.downloads

import dev.dogetime.domain.DownloadItem
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.WatchHistoryItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DownloadsViewModelTest {
    private fun download(id: String, mediaId: Int?, type: MediaType?, season: Int? = null, episode: Int? = null) =
        DownloadItem(id = id, title = id, mediaType = type, mediaId = mediaId, season = season, episode = episode, magnet = "", infoHash = "")

    private fun history(id: Int, type: MediaType, season: Int? = null, episode: Int? = null) = WatchHistoryItem(
        mediaItem = MediaItem(id, type, "", "", null, null, 0.0, null),
        season = season,
        episode = episode,
        positionMs = 1_000,
        durationMs = 2_000,
        lastWatchedAt = 0,
    )

    @Test
    fun matchesEpisodeProgressByTypeIdAndEpisode() {
        val entries = withWatchProgress(
            downloads = listOf(download("ep", 5, MediaType.Tv, season = 1, episode = 2)),
            history = listOf(history(5, MediaType.Tv, 1, 1), history(5, MediaType.Tv, 1, 2)),
        )
        assertEquals(2, entries.single().watchProgress?.episode)
    }

    @Test
    fun sameIdOfAnotherTypeIsNotAMatch() {
        val entries = withWatchProgress(
            downloads = listOf(download("anime", 5, MediaType.Anime)),
            history = listOf(history(5, MediaType.Movie)),
        )
        assertNull(entries.single().watchProgress)
    }

    @Test
    fun downloadWithoutMediaIdHasNoProgress() {
        val entries = withWatchProgress(listOf(download("magnet", null, null)), listOf(history(5, MediaType.Movie)))
        assertNull(entries.single().watchProgress)
    }
}
