package dev.dogetime.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dogetime.data.local.AppDatabase
import dev.dogetime.data.local.PreferenceEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PreferencesRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    private val preferences = PreferencesRepository(db.preferenceDao(), CoroutineScope(SupervisorJob() + Dispatchers.IO))

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun torrentBufferIsOffUntilSet() = runBlocking {
        assertEquals(0, preferences.observeTorrentBufferMb().first())
        preferences.setTorrentBufferMb(25)
        // Saved on the app scope, so wait for it to land.
        withTimeout(5_000) { preferences.observeTorrentBufferMb().first { it == 25 } }
        Unit
    }

    @Test
    fun unknownSavedBufferIsOff() = runBlocking {
        db.preferenceDao().upsert(PreferenceEntity("torrent_buffer_mb", "33"))
        assertEquals(0, preferences.observeTorrentBufferMb().first())
        db.preferenceDao().upsert(PreferenceEntity("torrent_buffer_mb", "lots"))
        assertEquals(0, preferences.observeTorrentBufferMb().first())
    }
}
