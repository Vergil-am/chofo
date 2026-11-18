package dev.dogetime.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpisodeNamesTest {
    private fun pick(names: List<String>, season: Int?, episode: Int) = pickEpisode(names, season, episode) { it }

    @Test
    fun episodeOneDoesNotMatchTenOrTwelve() {
        val pack = listOf("Show.S01E10.1080p.mkv", "Show.S01E12.1080p.mkv", "Show.S01E01.1080p.mkv")
        assertEquals("Show.S01E01.1080p.mkv", pick(pack, 1, 1))
    }

    @Test
    fun episodeNumberInsideAWordDoesNotMatch() {
        // The old "e0*1" pattern matched the "e1" in "release1".
        assertNull(pick(listOf("release1.srt", "Movie.Name.2019.srt"), 1, 1))
    }

    @Test
    fun underscoresCountAsSeparators() {
        assertEquals("Show_S01E05_eng.srt", pick(listOf("Show_S01E04_eng.srt", "Show_S01E05_eng.srt"), 1, 5))
    }

    @Test
    fun commonSeasonEpisodeFormats() {
        assertEquals("show.s02e05.mkv", pick(listOf("show.s02e05.mkv"), 2, 5))
        assertEquals("Show.S02.E05.mkv", pick(listOf("Show.S02.E05.mkv"), 2, 5))
        assertEquals("Show 2x05.mkv", pick(listOf("Show 2x05.mkv"), 2, 5))
        assertEquals("Show 02x05.mkv", pick(listOf("Show 02x05.mkv"), 2, 5))
    }

    @Test
    fun wrongSeasonIsNotPickedOverTheRightOne() {
        val names = listOf("Show.S02E05.mkv", "Show.S01E05.mkv")
        assertEquals("Show.S01E05.mkv", pick(names, 1, 5))
    }

    @Test
    fun episodeOnlyNamesAreAFallback() {
        assertEquals("[Group] Anime - Ep 07 [1080p].mkv", pick(listOf("[Group] Anime - Ep 06 [1080p].mkv", "[Group] Anime - Ep 07 [1080p].mkv"), null, 7))
        assertEquals("Anime.E07.mkv", pick(listOf("Anime.E07.mkv"), null, 7))
        // A full season+episode tag wins over an episode-only one.
        assertEquals("Show.S01E07.mkv", pick(listOf("Extras.E07.mkv", "Show.S01E07.mkv"), 1, 7))
    }

    @Test
    fun codecAndResolutionNumbersAreIgnored() {
        assertNull(pick(listOf("Movie.1920x1080.x264.mkv", "Movie.H265.mkv"), 1, 264))
    }
}
