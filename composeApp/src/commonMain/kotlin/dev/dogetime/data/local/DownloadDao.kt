package dev.dogetime.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt DESC")
    suspend fun getByStatus(status: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE infoHash = :infoHash")
    suspend fun getByInfoHash(infoHash: String): List<DownloadEntity>

    @Upsert
    suspend fun upsert(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE downloads SET status = :status, downloadRate = 0 WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("DELETE FROM downloads")
    suspend fun clearAll()
}
