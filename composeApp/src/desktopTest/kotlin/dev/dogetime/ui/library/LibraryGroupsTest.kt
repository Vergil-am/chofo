package dev.dogetime.ui.library

import dev.dogetime.domain.DownloadItem
import dev.dogetime.domain.DownloadStatus
import dev.dogetime.domain.MediaType
import dev.dogetime.ui.downloads.DownloadEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryGroupsTest {
    private fun dl(
        id: String, title: String, mediaId: Int?, type: MediaType?, season: Int? = null, episode: Int? = null,
        at: Long = 0, status: DownloadStatus = DownloadStatus.COMPLETED, progress: Float = 1f, bytes: Long = 1L shl 30,
    ) = DownloadEntry(
        DownloadItem(id = id, title = title, mediaType = type, mediaId = mediaId, season = season, episode = episode, magnet = "", infoHash = "",
            createdAt = at, status = status, progress = progress, totalBytes = bytes),
        null,
    )

    @Test
    fun episodesGatherUnderTheirShowInOrder() {
        val groups = groupDownloads(
            listOf(
                dl("a", "MobLand S1E3", 247718, MediaType.Tv, 1, 3, at = 3),
                dl("m", "Dune: Part Two", 693134, MediaType.Movie, at = 2),
                dl("b", "MobLand S1E1", 247718, MediaType.Tv, 1, 1, at = 1, status = DownloadStatus.DOWNLOADING, progress = 0.5f),
                dl("c", "MobLand S2E1", 247718, MediaType.Tv, 2, 1, at = 4),
            ),
        )
        assertEquals(listOf("MobLand", "Dune: Part Two"), groups.map { it.title }) // most recently added first
        val show = groups.first()
        assertTrue(show.isShow)
        assertEquals(listOf("b", "a", "c"), show.entries.map { it.item.id })
        assertEquals(3L shl 30, show.totalBytes)
        assertEquals(0.5f, show.unfinishedProgress)
        assertFalse(groups.last().isShow)
        assertTrue(groups.last().unfinished.isEmpty())
    }

    @Test
    fun theSameIdOfAnotherTypeIsAnotherTitle() {
        // Anime ids are Kitsu ids and can match a TMDB id.
        val groups = groupDownloads(listOf(dl("x", "Frieren S1E1", 46474, MediaType.Anime, 1, 1), dl("y", "Other", 46474, MediaType.Movie)))
        assertEquals(2, groups.size)
    }

    @Test
    fun withoutAnIdTitlesAreGroupedByName() {
        val groups = groupDownloads(listOf(dl("x", "Some Show S1E1", null, null, 1, 1), dl("y", "Some Show S1E2", null, null, 1, 2)))
        assertEquals(listOf("Some Show"), groups.map { it.title })
        assertEquals("Show", showTitleOf("Show s01e02"))
        assertEquals("Movie", showTitleOf("Movie"))
    }
}
