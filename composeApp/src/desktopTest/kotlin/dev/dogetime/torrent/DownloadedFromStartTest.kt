package dev.dogetime.torrent

import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadedFromStartTest {
    private val piece = 100L

    private fun count(start: Long, size: Long, limit: Long, have: Set<Int>) =
        downloadedFromStart(start, size, piece, limit, have::contains)

    @Test
    fun nothingDownloaded() {
        assertEquals(0, count(start = 0, size = 1_000, limit = 10_000, have = emptySet()))
    }

    @Test
    fun countsUpToTheFirstMissingPiece() {
        assertEquals(300, count(start = 0, size = 1_000, limit = 10_000, have = setOf(0, 1, 2, 5, 6)))
    }

    @Test
    fun wholeFile() {
        assertEquals(950, count(start = 0, size = 950, limit = 10_000, have = (0..9).toSet()))
    }

    @Test
    fun stopsAtTheLimit() {
        assertEquals(250, count(start = 0, size = 1_000, limit = 250, have = (0..9).toSet()))
    }

    @Test
    fun fileStartingMidPiece() {
        // The file starts 40 bytes into piece 2, so piece 2 holds its first 60 bytes.
        assertEquals(60, count(start = 240, size = 1_000, limit = 10_000, have = setOf(2)))
        assertEquals(160, count(start = 240, size = 1_000, limit = 10_000, have = setOf(2, 3, 5)))
        assertEquals(0, count(start = 240, size = 1_000, limit = 10_000, have = setOf(3, 4)))
    }

    @Test
    fun smallFileInsideOnePiece() {
        assertEquals(30, count(start = 510, size = 30, limit = 10_000, have = setOf(5)))
    }

    @Test
    fun emptyFile() {
        assertEquals(0, count(start = 0, size = 0, limit = 10_000, have = setOf(0)))
    }
}
