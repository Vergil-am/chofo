package dev.dogetime.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dogetime.data.local.AppDatabase
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.RememberedStream
import dev.dogetime.domain.TorrentOption
import dev.dogetime.domain.VideoSource
import dev.dogetime.domain.WatchStatus
import dev.dogetime.domain.WatchTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchlistRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    private var clock = 1_000L
    private val changes = mutableListOf<Unit>()
    private val watchlist = WatchlistRepository(db.watchlistDao()) { clock }.apply { listener = WatchlistListener { changes += Unit } }
    private val show = MediaItem(1399, MediaType.Tv, "Game of Thrones", "", null, null, 8.5, "2011")

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun settingMovingAndRemovingATitle() = runBlocking {
        watchlist.set(show, WatchStatus.PlanToWatch)
        assertEquals(WatchStatus.PlanToWatch, watchlist.observeStatus(MediaType.Tv, 1399).first())

        // The same status again is no change; a move is newer even within the same millisecond.
        watchlist.set(show, WatchStatus.PlanToWatch)
        watchlist.set(show, WatchStatus.Watching)
        assertEquals(2, changes.size)
        assertEquals(1_001, db.watchlistDao().get(1399, "Tv")!!.updatedAt)

        // Removing something already synced still has to be sent.
        db.watchlistDao().markSent(1399, "Tv", 1_001)
        watchlist.remove(MediaType.Tv, 1399)
        assertNull(watchlist.observeStatus(MediaType.Tv, 1399).first())
        assertTrue(watchlist.observeAll().first().isEmpty())
        // A tombstone waits for sync.
        assertTrue(db.watchlistDao().get(1399, "Tv")!!.let { it.deleted && it.pending })
    }

    @Test
    fun playingMovesPlannedTitlesToWatchingOnly() = runBlocking {
        watchlist.startedWatching(show, isEpisode = true) // not listed: stays off the list
        assertNull(watchlist.observeStatus(MediaType.Tv, 1399).first())

        watchlist.set(show, WatchStatus.PlanToWatch)
        watchlist.startedWatching(show, isEpisode = true)
        assertEquals(WatchStatus.Watching, watchlist.observeStatus(MediaType.Tv, 1399).first())

        watchlist.set(show, WatchStatus.Dropped)
        watchlist.startedWatching(show, isEpisode = true) // trying it again doesn't undo Dropped
        assertEquals(WatchStatus.Dropped, watchlist.observeStatus(MediaType.Tv, 1399).first())
    }
}

class RememberedStreamTest {
    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    private val history = WatchHistoryRepository(db.watchHistoryDao(), CoroutineScope(SupervisorJob()))
    private val movie = MediaItem(550, MediaType.Movie, "Fight Club", "", null, null, 8.4, "1999")

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun streamsAreBuiltFromWhatWasPicked() {
        val torrent = RememberedStream.of(
            TorrentOption("1080p", "Fight.Club.1080p", 42, "2 GB", null, "magnet:?xt=urn:btih:aaaa", 0, "Fight.Club.mkv", "Torrentio"),
            addonId = "torrentio",
        )
        assertEquals(RememberedStream.Kind.Torrent, torrent.kind)
        assertEquals("1080p · Torrentio", torrent.label)
        assertEquals(torrent, RememberedStream.decode(torrent.encode()))

        val direct = RememberedStream.of(
            TorrentOption("4k", "Fight Club 4K", null, null, null, "", null, null, "Debrid", directUrl = "https://cdn/x.mkv", headers = mapOf("A" to "b")),
            addonId = "debrid",
        )
        assertEquals(RememberedStream.Kind.Direct, direct.kind)
        assertEquals("Fight Club 4K", direct.fileName) // used to find the same file again
        assertEquals(VideoSource.VidLink, RememberedStream.decode(RememberedStream.of(VideoSource.VidLink).encode())?.videoSource)

        // What can't be opened again isn't offered.
        assertNull(RememberedStream.decode("""{"kind":"web","source":"GoneSite"}"""))
        assertNull(RememberedStream.decode("""{"kind":"torrent"}"""))
        assertNull(RememberedStream.decode("garbage"))
    }

    @Test
    fun progressKeepsTheStreamUntilAnotherIsPlayed() = runBlocking {
        val first = RememberedStream.of(VideoSource.VidSrc)
        history.saveProgress(WatchTarget.Movie(550), movie, 1_000, 8_000_000, stream = first)
        history.saveProgress(WatchTarget.Movie(550), movie, 2_000, 8_000_000) // e.g. the offline copy
        assertEquals(first, history.rememberedStream(MediaType.Movie, 550))
        val second = RememberedStream.of(VideoSource.VidCore)
        history.saveProgress(WatchTarget.Movie(550), movie, 3_000, 8_000_000, stream = second)
        assertEquals(second, history.rememberedStream(MediaType.Movie, 550))
    }
}
