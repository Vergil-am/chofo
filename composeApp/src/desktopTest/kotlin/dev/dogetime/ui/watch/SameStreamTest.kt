package dev.dogetime.ui.watch

import dev.dogetime.domain.TorrentOption
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SameStreamTest {
    private fun option(magnet: String = "", fileIndex: Int? = null, fileName: String? = null, directUrl: String? = null) =
        TorrentOption("1080p", fileName ?: "Release", 10, null, null, magnet, fileIndex, fileName, directUrl = directUrl)

    private val pack = "magnet:?xt=urn:btih:ABCDEF&dn=pack"

    @Test
    fun sameTorrentAndFileIsTheSameStream() {
        val pick = TorrentPick(pack, "S01E02.mkv", 2)
        assertTrue(pick.isSameStream(option("magnet:?xt=urn:btih:abcdef", fileIndex = 2)))
        assertFalse(pick.isSameStream(option(pack, fileIndex = 3)))
        assertFalse(pick.isSameStream(option("magnet:?xt=urn:btih:OTHER", fileIndex = 2)))
    }

    @Test
    fun directLinksMatchByUrlOrFileName() {
        val pick = TorrentPick("", "Movie.mkv", null, directUrl = "https://a/1")
        assertTrue(pick.isSameStream(option(directUrl = "https://a/1")))
        // A freshly fetched link for the same file.
        assertTrue(pick.isSameStream(option(directUrl = "https://a/2", fileName = "Movie.mkv")))
        assertFalse(pick.isSameStream(option(directUrl = "https://a/3", fileName = "Other.mkv")))
        assertFalse(pick.isSameStream(option(pack, fileIndex = null, fileName = "Movie.mkv")))
    }
}
