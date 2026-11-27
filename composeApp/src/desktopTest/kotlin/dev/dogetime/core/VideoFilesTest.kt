package dev.dogetime.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VideoFilesTest {
    private fun files(vararg entries: Pair<String, Long>) = entries.map { TorrentFileEntry(it.first, it.second) }

    @Test
    fun noVideoFilesGivesNull() {
        assertNull(pickVideoFile(files("Movie/readme.txt" to 1, "Movie/cover.jpg" to 2), null, null))
    }

    @Test
    fun movieFallsBackToTheLargestVideo() {
        val torrent = files(
            "Movie/Sample.mkv" to 50,
            "Movie/Movie.2019.1080p.mkv" to 4_000,
            "Movie/Movie.srt" to 1,
        )
        assertEquals(1, pickVideoFile(torrent, null, null))
    }

    @Test
    fun extensionsAreCaseInsensitive() {
        assertEquals(1, pickVideoFile(files("a.nfo" to 9_999, "Movie.MP4" to 10), null, null))
    }

    @Test
    fun episodeIsMatchedByName() {
        val pack = files(
            "Show/Show.S01E10.mkv" to 900,
            "Show/Show.S01E01.mkv" to 500,
            "Show/Show.S01E02.mkv" to 600,
        )
        assertEquals(1, pickVideoFile(pack, null, null, season = 1, episode = 1))
        assertEquals(0, pickVideoFile(pack, null, null, season = 1, episode = 10))
    }

    @Test
    fun episodeNameWinsOverFileNameAndIndex() {
        val pack = files("Show.S01E01.mkv" to 1, "Show.S01E02.mkv" to 1)
        assertEquals(1, pickVideoFile(pack, "Show.S01E01.mkv", 0, season = 1, episode = 2))
    }

    @Test
    fun fileNameMatchesTheBaseNameIgnoringCase() {
        val torrent = files("Pack/a.mkv" to 10, "Pack/Sub/B.mkv" to 5)
        assertEquals(1, pickVideoFile(torrent, "folder\\b.MKV", null))
    }

    @Test
    fun fileIndexIsUsedOnlyWhenItIsAVideo() {
        val torrent = files("a.mkv" to 10, "b.mkv" to 20, "c.txt" to 30)
        assertEquals(0, pickVideoFile(torrent, null, 0))
        // Index 2 is not a video, so the largest video is played.
        assertEquals(1, pickVideoFile(torrent, null, 2))
        assertEquals(1, pickVideoFile(torrent, null, 99))
    }

    @Test
    fun unrecognisedEpisodeNamesUseTheNthVideoByName() {
        val pack = files("Show/03.mkv" to 1, "Show/01.mkv" to 1, "Show/notes.txt" to 1, "Show/02.mkv" to 1)
        assertEquals(3, pickVideoFile(pack, null, null, season = 1, episode = 2))
    }

    @Test
    fun episodeOutOfRangeFallsBackToTheLargestVideo() {
        val pack = files("Show/a.mkv" to 1, "Show/b.mkv" to 9)
        assertEquals(1, pickVideoFile(pack, null, null, season = 1, episode = 5))
    }
}
