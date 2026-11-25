package dev.dogetime.data.sync

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dogetime.data.WatchHistoryRepository
import dev.dogetime.data.local.AppDatabase
import dev.dogetime.data.local.WatchHistoryEntity
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.WatchTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Row conversion: what the API would reject never leaves the device. */
class WatchSyncMappingTest {
    private fun row(type: String, season: Int?, episode: Int?) = WatchHistoryEntity(
        watchKey = "k", id = 1, type = type, title = "T", overview = "", posterUrl = null, backdropUrl = null,
        rating = 85.0, year = "2025", season = season, episode = episode, positionMs = 5, durationMs = 10, lastWatchedAt = 1,
    )

    @Test
    fun validRowsMapToTheApiShape() {
        val tv = row("Tv", 1, 2).toWrite()!!
        assertEquals("tv", tv.type)
        assertEquals(1 to 2, tv.season to tv.episode)
        assertEquals(10.0, tv.card?.rating) // clamped to the API's 0..10
        assertEquals("movie", row("Movie", null, null).toWrite()?.type)
        assertEquals("anime", row("Anime", 1, 7).toWrite()?.type)
        assertEquals("anime", row("Anime", null, null).toWrite()?.type) // an anime movie
    }

    @Test
    fun rowsTheApiWouldRejectAreSkipped() {
        assertNull(row("Tv", null, null).toWrite())
        assertNull(row("Movie", 1, 1).toWrite())
        assertNull(row("Anime", 1, null).toWrite())
        assertNull(row("Book", null, null).toWrite())
    }
}

/**
 * Two devices syncing through a real Chofo API. Runs only when CHOFO_API_TEST_URL is set, e.g.
 * start chofo-api with `DATABASE_URL= bun src/index.ts` (in-memory) and set it to
 * http://localhost:3000.
 */
class WatchSyncIntegrationTest {
    private val baseUrl = System.getenv("CHOFO_API_TEST_URL").orEmpty()
    private val scopes = mutableListOf<CoroutineScope>()
    private val dbs = mutableListOf<AppDatabase>()

    private val show = MediaItem(247718, MediaType.Tv, "MobLand", "Crime drama", "https://img/p.jpg", null, 8.1, "2025")
    private val movie = MediaItem(550, MediaType.Movie, "Fight Club", "", null, null, 8.4, "1999")

    private inner class Device {
        val db = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val api = ChofoApi(baseUrl)
        val history = WatchHistoryRepository(db.watchHistoryDao(), scope)
        val accounts = AccountRepository(api, db.preferenceDao(), scope)
        // Long delays: the test runs every sync itself.
        val sync = WatchSync(api, accounts, db.watchHistoryDao(), db.preferenceDao(), null, scope, pushDelayMs = 3_600_000, urgentDelayMs = 3_600_000)
            .also { history.listener = it }

        init {
            dbs += db
            scopes += scope
        }

        /** Runs one sync, waiting out the one sign-in starts by itself. */
        suspend fun syncNow() = withTimeout(20_000) {
            while (!sync.sync()) delay(50)
            assertNull(sync.status.value.error, "sync failed: ${sync.status.value.error}")
        }

        suspend fun row(type: MediaType, id: Int, season: Int? = null, episode: Int? = null) = history.getHistory(type, id, season, episode)
    }

    @AfterTest
    fun tearDown() = runBlocking {
        // Wait for background work to stop before closing: a query on a closed database crashes the JVM.
        scopes.forEach { it.coroutineContext.job.cancelAndJoin() }
        delay(200)
        dbs.forEach { it.close() }
    }

    private fun live(block: suspend () -> Unit) = runBlocking {
        if (baseUrl.isBlank()) {
            println("CHOFO_API_TEST_URL is not set; skipping.")
            return@runBlocking
        }
        block()
    }

    @Test
    fun progressFollowsTheUserBetweenDevices() = live {
        val username = "app_${Random.nextInt(1_000_000)}"
        val phone = Device()
        val laptop = Device()
        phone.accounts.awaitLoaded()
        laptop.accounts.awaitLoaded()

        // Watched on the phone before signing in: it goes up at the first sync.
        phone.history.saveProgress(WatchTarget.Episode(247718, 1, 1), show, 600_000, 3_600_000)
        phone.accounts.signUp(username, "$username@chofo.test", "password123")
        phone.syncNow()

        laptop.accounts.signIn(username, "password123")
        laptop.syncNow()
        val onLaptop = assertNotNull(laptop.row(MediaType.Tv, 247718, 1, 1))
        assertEquals(600_000, onLaptop.positionMs)
        assertEquals("MobLand", onLaptop.mediaItem.title) // the card came with it
        assertEquals("https://img/p.jpg", onLaptop.mediaItem.posterUrl)

        // Keeps watching on the laptop; the phone picks up where it stopped.
        delay(5)
        laptop.history.saveProgress(WatchTarget.Episode(247718, 1, 1), show, 1_800_000, 3_600_000)
        laptop.history.saveProgress(WatchTarget.Movie(550), movie, 60_000, 8_000_000)
        laptop.syncNow()
        phone.syncNow()
        assertEquals(1_800_000, phone.row(MediaType.Tv, 247718, 1, 1)?.positionMs)
        assertEquals(60_000, phone.row(MediaType.Movie, 550)?.positionMs)

        // A delete on one device removes it on the other, and stays removed.
        phone.history.delete(MediaType.Movie, 550)
        phone.syncNow()
        laptop.syncNow()
        assertNull(laptop.row(MediaType.Movie, 550))
        laptop.syncNow()
        assertNull(laptop.row(MediaType.Movie, 550))

        // Signing out keeps the local history; changes made while signed out stay local.
        phone.accounts.signOut()
        phone.history.saveProgress(WatchTarget.Episode(247718, 1, 2), show, 5_000, 3_600_000)
        assertNotNull(phone.row(MediaType.Tv, 247718, 1, 1))
        laptop.syncNow()
        assertNull(laptop.row(MediaType.Tv, 247718, 1, 2))
    }

    @Test
    fun aRejectedSessionSignsOutButKeepsHistory() = live {
        val username = "app_${Random.nextInt(1_000_000)}"
        val device = Device()
        device.accounts.awaitLoaded()
        device.accounts.signUp(username, "$username@chofo.test", "password123")
        device.syncNow()
        device.history.saveProgress(WatchTarget.Movie(550), movie, 1_000, 8_000_000)

        // Another device signs out this session.
        device.api.signOut(device.accounts.account.value!!.token)
        withTimeout(20_000) { while (!device.sync.sync()) delay(50) }

        assertNull(device.accounts.account.value)
        assertTrue(device.sync.status.value.error!!.contains("sign in again", ignoreCase = true))
        assertNotNull(device.row(MediaType.Movie, 550))
    }
}
