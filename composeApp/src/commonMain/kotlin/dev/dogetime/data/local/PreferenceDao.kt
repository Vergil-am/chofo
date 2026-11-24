package dev.dogetime.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PreferenceDao {
    /** The value saved under [key], or null when nothing is. */
    @Query("SELECT value FROM preferences WHERE `key` = :key LIMIT 1")
    fun observe(key: String): Flow<String?>

    @Upsert
    suspend fun upsert(preference: PreferenceEntity)

    @Query("DELETE FROM preferences WHERE `key` = :key")
    suspend fun delete(key: String)
}
