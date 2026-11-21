package dev.dogetime.ui.watch

import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.TorrentOption
import dev.dogetime.domain.WatchTarget
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadItemForTest {
    private val option = TorrentOption(
        quality = "1080p",
        release = "Show.S01.1080p",
        seeders = 10,
        size = "1 GB",
        site = null,
        magnet = "magnet:?xt=urn:btih:abc123&dn=Show",
        fileIndex = 4,
        fileName = "Show.S01E05.mkv",
    )

    @Test
    fun movieIdIsTheHashAlone() {
        val item = downloadItemFor(WatchTarget.Movie(id = 7), "Movie", null, null, option, now = 0)
        assertEquals("abc123_movie", item.id)
        assertEquals(MediaType.Movie, item.mediaType)
        assertEquals(7, item.mediaId)
    }

    @Test
    fun episodeIdHasTheEpisodeAndFile() {
        val target = WatchTarget.Episode(showId = 9, season = 1, episode = 5)
        val item = downloadItemFor(target, "Show", null, null, option, now = 0)
        assertEquals("abc123_s1e5_4", item.id)
        assertEquals(MediaType.Tv, item.mediaType)
        assertEquals(1, item.season)
        assertEquals(5, item.episode)
    }

    @Test
    fun animeKeepsItsOwnType() {
        val anime = MediaItem(9, MediaType.Anime, "Show", "", null, null, 0.0, null)
        val target = WatchTarget.Episode(showId = 9, season = 1, episode = 5)
        assertEquals(MediaType.Anime, downloadItemFor(target, "Show", anime, null, option, now = 0).mediaType)
    }
}
