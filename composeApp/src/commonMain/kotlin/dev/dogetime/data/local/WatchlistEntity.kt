package dev.dogetime.data.local

import androidx.room.Entity

/**
 * A title on the watchlist, with its card so the list shows without a network call. [status] is
 * a [dev.dogetime.domain.WatchStatus] name. A removed title stays as a [deleted] row until sync
 * has told the server, so another device's older copy can't bring it back. [pending] marks a
 * change made here that the server hasn't been sent yet.
 */
@Entity(tableName = "watchlist", primaryKeys = ["id", "type"])
data class WatchlistEntity(
    val id: Int,
    val type: String,
    val status: String,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: Double,
    val year: String?,
    /** When it was added, moved or removed here or on another device (epoch ms). */
    val updatedAt: Long,
    val deleted: Boolean = false,
    val pending: Boolean = true,
)
