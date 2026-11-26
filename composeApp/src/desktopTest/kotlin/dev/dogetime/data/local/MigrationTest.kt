package dev.dogetime.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs every migration against the exported schemas in composeApp/schemas. */
class MigrationTest {
    private val tempDir: Path = Files.createTempDirectory("dogetime-migration-test")
    private val dbFile: Path = tempDir.resolve("test.db")

    private val helper = MigrationTestHelper(
        schemaDirectoryPath = Paths.get("schemas"),
        databasePath = dbFile,
        driver = BundledSQLiteDriver(),
        databaseClass = AppDatabase::class,
    )

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun migrateFromV1KeepsFavoritesAndReachesLatestSchema() {
        helper.createDatabase(1).use { db ->
            db.execSQL(
                "INSERT INTO favorites (id, type, title, overview, posterUrl, backdropUrl, rating, year, addedAt) " +
                    "VALUES (550, 'Movie', 'Fight Club', '', NULL, NULL, 8.4, '1999', 1000)",
            )
        }

        helper.runMigrationsAndValidate(6, ALL_MIGRATIONS.toList()).use { db ->
            assertEquals(listOf("Fight Club"), db.strings("SELECT title FROM favorites"))
        }
    }

    @Test
    fun migrate2To3BuildsWatchKeys() {
        helper.createDatabase(2).use { db ->
            db.execSQL(
                "INSERT INTO watch_history VALUES " +
                    "(550, 'Movie', 'Fight Club', '', NULL, NULL, 8.4, '1999', NULL, NULL, 60000, 8000000, 1000)",
            )
            db.execSQL(
                "INSERT INTO watch_history VALUES " +
                    "(1399, 'Tv', 'Game of Thrones', '', NULL, NULL, 8.5, '2011', 2, 5, 120000, 3000000, 2000)",
            )
        }

        helper.runMigrationsAndValidate(3, listOf(MIGRATION_2_3)).use { db ->
            val keys = db.strings("SELECT watchKey FROM watch_history ORDER BY lastWatchedAt")
            assertEquals(
                listOf(
                    WatchHistoryEntity.key("Movie", 550),
                    WatchHistoryEntity.key("Tv", 1399, 2, 5),
                ),
                keys,
            )
        }
    }

    @Test
    fun migrate3To4AddsEmptyDownloadsTable() {
        helper.createDatabase(3).close()

        helper.runMigrationsAndValidate(4, listOf(MIGRATION_3_4)).use { db ->
            assertTrue(db.strings("SELECT id FROM downloads").isEmpty())
        }
    }

    @Test
    fun migrate4To5AddsPreferencesTableAndKeepsDownloads() {
        helper.createDatabase(4).use { db ->
            db.execSQL(
                "INSERT INTO downloads (id, title, magnet, infoHash, quality, totalBytes, downloadedBytes, " +
                    "progress, downloadRate, peers, status, createdAt) " +
                    "VALUES ('abc_movie', 'Fight Club', 'magnet:', 'abc', '1080p', 0, 0, 0, 0, 0, 'COMPLETED', 1)",
            )
        }

        helper.runMigrationsAndValidate(5, listOf(MIGRATION_4_5)).use { db ->
            assertEquals(listOf("Fight Club"), db.strings("SELECT title FROM downloads"))
            db.execSQL("INSERT INTO preferences (`key`, value) VALUES ('theme', 'mocha')")
            assertEquals(listOf("mocha"), db.strings("SELECT value FROM preferences"))
        }
    }

    @Test
    fun migrate5To6KeepsHistoryWithoutAStreamAndAddsTheWatchlist() {
        helper.createDatabase(5).use { db ->
            db.execSQL(
                "INSERT INTO watch_history VALUES " +
                    "('Movie:550', 550, 'Movie', 'Fight Club', '', NULL, NULL, 8.4, '1999', NULL, NULL, 60000, 8000000, 1000)",
            )
        }

        helper.runMigrationsAndValidate(6, listOf(MIGRATION_5_6)).use { db ->
            assertEquals(listOf("Fight Club"), db.strings("SELECT title FROM watch_history WHERE stream IS NULL"))
            db.execSQL(
                "INSERT INTO watchlist VALUES (550, 'Movie', 'PlanToWatch', 'Fight Club', '', NULL, NULL, 8.4, '1999', 2000, 0, 1)",
            )
            assertEquals(listOf("PlanToWatch"), db.strings("SELECT status FROM watchlist"))
        }
    }

    private fun SQLiteConnection.strings(sql: String): List<String> = prepare(sql).use { stmt ->
        buildList { while (stmt.step()) add(stmt.getText(0)) }
    }
}
