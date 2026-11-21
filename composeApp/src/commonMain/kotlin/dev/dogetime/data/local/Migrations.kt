package dev.dogetime.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/*
 * Schema migrations. Every version bump of [AppDatabase] needs one here, plus the exported
 * schema JSON in composeApp/schemas, so users keep favorites, history and downloads on update.
 * The CREATE statements must match the exported schema of the target version exactly.
 */

/** v2 added the watch_history table (keyed by id + type). */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `watch_history` (`id` INTEGER NOT NULL, `type` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `overview` TEXT NOT NULL, `posterUrl` TEXT, `backdropUrl` TEXT, " +
                "`rating` REAL NOT NULL, `year` TEXT, `season` INTEGER, `episode` INTEGER, " +
                "`positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `lastWatchedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`, `type`))",
        )
    }
}

/** v3 re-keyed watch_history by watchKey so each episode gets its own row. */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `watch_history_new` (`watchKey` TEXT NOT NULL, `id` INTEGER NOT NULL, " +
                "`type` TEXT NOT NULL, `title` TEXT NOT NULL, `overview` TEXT NOT NULL, `posterUrl` TEXT, " +
                "`backdropUrl` TEXT, `rating` REAL NOT NULL, `year` TEXT, `season` INTEGER, `episode` INTEGER, " +
                "`positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `lastWatchedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`watchKey`))",
        )
        // Same format as WatchHistoryEntity.key().
        connection.execSQL(
            "INSERT INTO `watch_history_new` SELECT " +
                "CASE WHEN `season` IS NOT NULL AND `episode` IS NOT NULL " +
                "THEN `type` || ':' || `id` || ':' || `season` || ':' || `episode` " +
                "ELSE `type` || ':' || `id` END, " +
                "`id`, `type`, `title`, `overview`, `posterUrl`, `backdropUrl`, `rating`, `year`, " +
                "`season`, `episode`, `positionMs`, `durationMs`, `lastWatchedAt` FROM `watch_history`",
        )
        connection.execSQL("DROP TABLE `watch_history`")
        connection.execSQL("ALTER TABLE `watch_history_new` RENAME TO `watch_history`")
    }
}

/** v4 added the downloads table. */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `downloads` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`mediaType` TEXT, `mediaId` INTEGER, `season` INTEGER, `episode` INTEGER, `posterUrl` TEXT, " +
                "`backdropUrl` TEXT, `magnet` TEXT NOT NULL, `infoHash` TEXT NOT NULL, `fileName` TEXT, " +
                "`fileIndex` INTEGER, `quality` TEXT NOT NULL, `totalBytes` INTEGER NOT NULL, " +
                "`downloadedBytes` INTEGER NOT NULL, `progress` REAL NOT NULL, `downloadRate` INTEGER NOT NULL, " +
                "`peers` INTEGER NOT NULL, `status` TEXT NOT NULL, `errorMessage` TEXT, `videoFilePath` TEXT, " +
                "`subtitleFilePath` TEXT, `subtitleLanguage` TEXT, `createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
    }
}

/** v5 added the preferences table, for the selected theme. */
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `preferences` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))",
        )
    }
}

internal val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
