package dev.dogetime.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class ImageUrlsTest {
    private val tmdb = "https://image.tmdb.org/t/p/w780/abc.jpg"

    @Test
    fun picksATmdbSizeForTheWidth() {
        assertEquals(tmdb, sharpImageUrl(tmdb, 700))
        assertEquals("https://image.tmdb.org/t/p/w1280/abc.jpg", sharpImageUrl(tmdb, 1200))
        assertEquals("https://image.tmdb.org/t/p/original/abc.jpg", sharpImageUrl(tmdb, 2560))
    }

    @Test
    fun leavesOtherHostsAlone() {
        val kitsu = "https://media.kitsu.app/anime/cover_images/1/large.jpg"
        assertEquals(kitsu, sharpImageUrl(kitsu, 2560))
    }
}
