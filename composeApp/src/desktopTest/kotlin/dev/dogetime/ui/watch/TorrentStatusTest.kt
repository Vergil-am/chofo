package dev.dogetime.ui.watch

import dev.dogetime.core.TorrentProgress
import kotlin.test.Test
import kotlin.test.assertEquals

class TorrentStatusTest {
    private val mb = 1024L * 1024L

    @Test
    fun bufferIsTheSettingOrTheWholeSmallerFile() {
        assertEquals(25 * mb, bufferTarget(25, TorrentProgress(size = 4_000 * mb)))
        assertEquals(10 * mb, bufferTarget(25, TorrentProgress(size = 10 * mb)))
        assertEquals(25 * mb, bufferTarget(25, TorrentProgress(size = 0)))
    }

    @Test
    fun stats() {
        assertEquals("1 peer · 512 KB/s · 37%", torrentStats(TorrentProgress(peers = 1, downloadRate = 512 * 1024, progress = 0.374f)))
        assertEquals("12 peers · 2.5 MB/s · 100%", torrentStats(TorrentProgress(peers = 12, downloadRate = (2.5 * mb).toLong(), progress = 1f)))
        assertEquals("0 peers · 0 KB/s · 0%", torrentStats(TorrentProgress()))
    }
}
