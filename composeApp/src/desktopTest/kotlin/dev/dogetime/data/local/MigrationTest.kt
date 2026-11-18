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

        helper.runMigrationsAndValidate(4, ALL_MIGRATIONS.toList()).use { db ->
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

    private fun SQLiteConnection.strings(sql: String): List<String> = prepare(sql).use { stmt ->
        buildList { while (stmt.step()) add(stmt.getText(0)) }
    }
}
