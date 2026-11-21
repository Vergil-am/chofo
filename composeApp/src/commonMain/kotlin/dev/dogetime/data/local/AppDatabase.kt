package dev.dogetime.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [FavoriteEntity::class, WatchHistoryEntity::class, DownloadEntity::class, PreferenceEntity::class],
    version = 5,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun preferenceDao(): PreferenceDao
}

// Room's compiler generates the actual implementation for each platform.
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

internal const val DATABASE_NAME = "dogetime.db"

/** Platform-specific builder that decides where the database file lives. */
internal expect fun databaseBuilder(): RoomDatabase.Builder<AppDatabase>

internal fun createDatabase(): AppDatabase =
    databaseBuilder()
        .addMigrations(*ALL_MIGRATIONS)
        // Only wipe when an older app build opens a newer database; upgrades must migrate.
        .fallbackToDestructiveMigrationOnDowngrade(true)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
