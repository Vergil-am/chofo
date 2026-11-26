package dev.dogetime.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist WHERE deleted = 0 ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist WHERE id = :id AND type = :type AND deleted = 0 LIMIT 1")
    fun observe(id: Int, type: String): Flow<WatchlistEntity?>

    /** Including a removed title's tombstone. */
    @Query("SELECT * FROM watchlist WHERE id = :id AND type = :type LIMIT 1")
    suspend fun get(id: Int, type: String): WatchlistEntity?

    @Upsert
    suspend fun upsert(entry: WatchlistEntity)

    /** Changes made here that the server hasn't been sent, oldest first, removals included. */
    @Query("SELECT * FROM watchlist WHERE pending = 1 ORDER BY updatedAt ASC")
    suspend fun pending(): List<WatchlistEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE pending = 1)")
    suspend fun hasPending(): Boolean

    /** The server has the change dated [updatedAt]; one made since stays pending. A sent removal goes. */
    @Query("UPDATE watchlist SET pending = 0 WHERE id = :id AND type = :type AND updatedAt = :updatedAt")
    suspend fun markSent(id: Int, type: String, updatedAt: Long)

    @Query("DELETE FROM watchlist WHERE id = :id AND type = :type AND deleted = 1 AND updatedAt = :updatedAt")
    suspend fun purgeTombstone(id: Int, type: String, updatedAt: Long)

    /** For signing in to a different account: everything here goes up to it. */
    @Query("UPDATE watchlist SET pending = 1")
    suspend fun markAllPending()

    @Query("DELETE FROM watchlist WHERE id = :id AND type = :type")
    suspend fun delete(id: Int, type: String)
}
