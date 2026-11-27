package dev.dogetime.data

import dev.dogetime.data.local.PreferenceDao
import dev.dogetime.data.local.PreferenceEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** App preferences that are kept between launches. */
class PreferencesRepository(
    private val dao: PreferenceDao,
    private val appScope: CoroutineScope,
) {
    /** The id of the selected theme, or null if none was ever picked. */
    fun observeThemeId(): Flow<String?> = dao.observe(KEY_THEME)

    /** Saves the selected theme. */
    fun setThemeId(id: String) = save(KEY_THEME, id, "the theme")

    /** How many MB of a torrent to download before playback starts; 0 starts straight away. */
    fun observeTorrentBufferMb(): Flow<Int> =
        dao.observe(KEY_TORRENT_BUFFER).map { it?.toIntOrNull()?.takeIf { mb -> mb in TORRENT_BUFFER_OPTIONS_MB } ?: 0 }

    fun setTorrentBufferMb(mb: Int) = save(KEY_TORRENT_BUFFER, mb.toString(), "the torrent buffer")

    /** Runs on the app scope, so it finishes even if the screen closes. */
    private fun save(key: String, value: String, what: String) {
        appScope.launch {
            try {
                dao.upsert(PreferenceEntity(key, value))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Saving $what failed: ${e.message}")
            }
        }
    }

    companion object {
        private const val KEY_THEME = "theme"
        private const val KEY_TORRENT_BUFFER = "torrent_buffer_mb"

        /** The buffer sizes offered in Settings, in MB; 0 is off. */
        val TORRENT_BUFFER_OPTIONS_MB = listOf(0, 10, 25, 50, 100)
    }
}
