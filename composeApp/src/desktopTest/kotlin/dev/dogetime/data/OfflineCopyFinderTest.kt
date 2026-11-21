package dev.dogetime.data

import dev.dogetime.domain.DownloadItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.WatchTarget
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineCopyFinderTest {
    private fun download(
        title: String,
        mediaId: Int?,
        type: MediaType?,
        season: Int? = null,
        episode: Int? = null,
    ) = DownloadItem(
        id = title,
        title = title,
        mediaType = type,
        mediaId = mediaId,
        season = season,
        episode = episode,
        magnet = "",
        infoHash = "",
    )

    @Test
    fun movieMatchesById() {
        assertTrue(download("Fight Club", 550, MediaType.Movie).isCopyOf(WatchTarget.Movie(550), "Fight Club", MediaType.Movie))
    }

    @Test
    fun movieWithoutIdMatchesByTitle() {
        assertTrue(download("fight club", null, null).isCopyOf(WatchTarget.Movie(550), "Fight Club", MediaType.Movie))
    }

    @Test
    fun sameIdOfAnotherTypeIsNotACopy() {
        assertFalse(download("Naruto", 550, MediaType.Anime).isCopyOf(WatchTarget.Movie(550), "Fight Club", MediaType.Movie))
    }

    @Test
    fun episodeMustBeTheSameEpisode() {
        val target = WatchTarget.Episode(showId = 1399, season = 2, episode = 5)
        assertTrue(download("GoT", 1399, MediaType.Tv, 2, 5).isCopyOf(target, "Game of Thrones", MediaType.Tv))
        assertFalse(download("GoT", 1399, MediaType.Tv, 2, 6).isCopyOf(target, "Game of Thrones", MediaType.Tv))
    }
}
