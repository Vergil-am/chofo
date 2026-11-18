package dev.dogetime.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dogetime.data.local.AppDatabase
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.WatchTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WatchHistoryRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repo = WatchHistoryRepository(db.watchHistoryDao(), appScope)

    private val movie = MediaItem(
        id = 550, type = MediaType.Movie, title = "Fight Club", overview = "",
        posterUrl = null, backdropUrl = null, rating = 8.4, year = "1999",
    )
    private val target = WatchTarget.Movie(id = 550)

    @AfterTest
    fun tearDown() {
        appScope.cancel()
        db.close()
    }

    /**
     * What the player used to do in onDispose: launch on the composition scope, which is cancelled
     * right after. Like Compose's UI dispatcher, runBlocking's single thread is busy until cancel().
     */
    @Test
    fun launchOnScopeCancelledRightAfterNeverSaves() = runBlocking {
        val screenScope = CoroutineScope(coroutineContext.minusKey(Job) + Job())
        screenScope.launch { repo.saveProgress(target, movie, positionMs = 60_000, durationMs = 8_000_000) }
        screenScope.cancel()
        screenScope.coroutineContext.job.join()

        assertNull(repo.getHistory(MediaType.Movie, 550))
    }

    @Test
    fun detachedSaveFinishesAfterCallerIsCancelled() = runBlocking {
        val screenScope = CoroutineScope(Job() + Dispatchers.Default)
        repo.saveProgressDetached(target, movie, positionMs = 60_000, durationMs = 8_000_000)
        screenScope.cancel()
        appScope.coroutineContext.job.children.toList().forEach { it.join() }

        assertEquals(60_000, repo.getHistory(MediaType.Movie, 550)?.positionMs)
    }
}
