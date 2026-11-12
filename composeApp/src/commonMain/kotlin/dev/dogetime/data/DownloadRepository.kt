package dev.dogetime.data

import dev.dogetime.data.local.DownloadDao
import dev.dogetime.data.local.DownloadEntity
import dev.dogetime.domain.DownloadItem
import dev.dogetime.domain.DownloadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DownloadRepository(private val dao: DownloadDao) {

    fun observeAll(): Flow<List<DownloadItem>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeById(id: String): Flow<DownloadItem?> =
        dao.observeById(id).map { it?.toDomain() }

    suspend fun getById(id: String): DownloadItem? =
        dao.getById(id)?.toDomain()

    suspend fun getByInfoHash(infoHash: String): List<DownloadItem> =
        dao.getByInfoHash(infoHash).map { it.toDomain() }

    suspend fun upsert(item: DownloadItem) {
        dao.upsert(DownloadEntity.fromDomain(item))
    }

    suspend fun updateStatus(id: String, status: DownloadStatus) {
        dao.updateStatus(id, status.name)
    }

    suspend fun deleteById(id: String) {
        dao.deleteById(id)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}
